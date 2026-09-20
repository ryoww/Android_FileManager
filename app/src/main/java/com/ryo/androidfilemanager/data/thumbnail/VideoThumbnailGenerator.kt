package com.ryo.androidfilemanager.data.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 動画からサムネイル画像をレンダリングする。
 * MediaMetadataRetriever はメモリ・CPU 負荷が高いため、プロセス内で並列数を制限する。
 * (SMB 経由ではフェッチとデコードがこの中で一体になるため、1 だとワーカーを
 * 増やしてもパイプライン化されない)
 *
 * フレームの選び方:
 * 1. コンテナ埋め込みのカバーアートがあればそれを使う(フレームデコード不要)
 * 2. 品質優先では先頭・10%・30% 地点のキーフレームを順に取得し、最初に十分明るい
 *    フレームを採用する。SMB の速度優先では追加シークを避けるため先頭だけを見る
 * 3. 全候補が暗い場合は、その中で最も明るいフレームを使う
 */
object VideoThumbnailGenerator {
    private const val PERF_TAG = "ThumbPerf"

    // 平均輝度(0-255)がこの値未満のフレームは「暗すぎる」とみなして次の候補を試す
    private const val DARK_LUMA_THRESHOLD = 40f

    // 輝度計算で何ピクセルおきにサンプリングするか(全ピクセル走査は不要)
    private const val LUMA_SAMPLE_GRID = 24

    private val renderSemaphore = Semaphore(4)

    suspend fun renderFrame(
        dataSource: MediaDataSource,
        output: File,
        maxSize: Int,
        jpegQuality: Int,
        options: VideoThumbnailRenderOptions = VideoThumbnailRenderPresets.Quality,
    ): Boolean = renderSemaphore.withPermit {
        val retriever = MediaMetadataRetriever()
        val frame = try {
            retriever.setDataSource(dataSource)
            pickThumbnailFrame(retriever, maxSize, options.frameSelection)
        } finally {
            // release は冪等なので、フォールバック側で使い回す前にここで確実に手放す
            runCatching { retriever.release() }
        }

        val resolvedFrame = frame ?: run {
            if (!options.allowCodecFallback) {
                Log.d(PERF_TAG, "video frame: mediacodec fallback skipped")
                return@withPermit false
            }
            // MediaMetadataRetriever はソフトウェアデコーダのみで、4K VP9 等では
            // 全フレームが取得できないことがある。MediaCodec(HW デコーダ優先)で
            // 1 フレームだけ取り直すフォールバックを試す
            val fallbackFrame = MediaCodecFrameExtractor.extractFrame(dataSource, maxSize)
            Log.d(
                PERF_TAG,
                "video frame: mediacodec fallback ${if (fallbackFrame != null) "succeeded" else "failed"}",
            )
            fallbackFrame ?: return@withPermit false
        }

        output.parentFile?.mkdirs()
        FileOutputStream(output).use { stream ->
            resolvedFrame.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
        }
        resolvedFrame.recycle()
        true
    }

    /**
     * setDataSource 済みの retriever からサムネイルに使うフレームを選ぶ。
     * ローカル・SMB 双方の生成経路から共用される。
     */
    fun pickThumbnailFrame(
        retriever: MediaMetadataRetriever,
        maxSize: Int,
        frameSelection: VideoThumbnailFrameSelection = VideoThumbnailFrameSelection.Representative,
    ): Bitmap? = retriever.embeddedPictureBitmap(maxSize)
        ?: retriever.representativeFrame(maxSize, frameSelection)

    private fun MediaMetadataRetriever.embeddedPictureBitmap(maxSize: Int): Bitmap? {
        val bytes = runCatching { embeddedPicture }.getOrNull() ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSize)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return null
        Log.d(PERF_TAG, "video frame: embedded picture used")
        return decoded.scaleDownTo(maxSize)
    }

    private fun MediaMetadataRetriever.representativeFrame(
        maxSize: Int,
        frameSelection: VideoThumbnailFrameSelection,
    ): Bitmap? {
        val durationUs = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.times(1000L)
            ?: 0L
        val candidateTimesUs = videoThumbnailCandidateTimesUs(durationUs, frameSelection)

        var brightestFrame: Bitmap? = null
        var brightestLuma = -1f
        for ((index, timeUs) in candidateTimesUs.withIndex()) {
            val frame = frameAt(timeUs, maxSize)
            if (frame == null) {
                // 最初の候補(t=0)がデコードすらできない場合、ソフトウェアデコーダが
                // このコーデック/解像度を扱えないと判断できる(例: 4K VP9)。
                // 以降の候補も同じ理由で確実に失敗するため、SMB で候補ごとに
                // 発生するシーク(WebM の Cues はファイル末尾にあり、そこへの
                // シークだけで数十MBを浪費する)を避けるため即座に諦める。
                // 「デコードはできたが暗い」場合は brightestFrame に積んで続行する。
                if (index == 0) {
                    Log.d(PERF_TAG, "video frame: first candidate failed to decode, aborting")
                    return null
                }
                continue
            }
            val luma = frame.averageLuma()
            if (luma >= DARK_LUMA_THRESHOLD) {
                Log.d(
                    PERF_TAG,
                    "video frame: t=${timeUs / 1000}ms luma=${luma.roundToInt()}",
                )
                brightestFrame?.recycle()
                return frame
            }
            if (luma > brightestLuma) {
                brightestFrame?.recycle()
                brightestFrame = frame
                brightestLuma = luma
            } else {
                frame.recycle()
            }
        }

        Log.d(
            PERF_TAG,
            "video frame: all candidates dark, fallback luma=${brightestLuma.roundToInt()}",
        )
        return brightestFrame
    }

    private fun MediaMetadataRetriever.frameAt(timeUs: Long, maxSize: Int): Bitmap? {
        val targetSize = videoTargetSize(maxSize)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && targetSize != null) {
            return getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                targetSize.first,
                targetSize.second,
            )
        }

        val sourceBitmap = getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: return null
        return sourceBitmap.scaleDownTo(maxSize)
    }

    private fun MediaMetadataRetriever.videoTargetSize(maxSize: Int): Pair<Int, Int>? {
        val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
            ?: return null
        val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
            ?: return null
        val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?: 0

        val sourceWidth = if (rotation == 90 || rotation == 270) height else width
        val sourceHeight = if (rotation == 90 || rotation == 270) width else height
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return null
        }

        val scale = min(
            min(
                maxSize.toFloat() / sourceWidth.toFloat(),
                maxSize.toFloat() / sourceHeight.toFloat(),
            ),
            1f,
        )
        return (sourceWidth * scale).roundToInt().coerceAtLeast(1) to
            (sourceHeight * scale).roundToInt().coerceAtLeast(1)
    }

    private fun Bitmap.scaleDownTo(maxSize: Int): Bitmap {
        val scale = min(
            min(
                maxSize.toFloat() / width.toFloat(),
                maxSize.toFloat() / height.toFloat(),
            ),
            1f,
        )
        if (scale >= 1f) {
            return this
        }

        val scaledBitmap = Bitmap.createScaledBitmap(
            this,
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaledBitmap !== this) {
            recycle()
        }
        return scaledBitmap
    }

    private fun sampleSizeFor(width: Int, height: Int, maxSize: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= maxSize && height / (sampleSize * 2) >= maxSize) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun Bitmap.averageLuma(): Float {
        val stepX = (width / LUMA_SAMPLE_GRID).coerceAtLeast(1)
        val stepY = (height / LUMA_SAMPLE_GRID).coerceAtLeast(1)
        var total = 0f
        var count = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = getPixel(x, y)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                total += 0.299f * red + 0.587f * green + 0.114f * blue
                count++
                x += stepX
            }
            y += stepY
        }
        return if (count == 0) 0f else total / count
    }
}
