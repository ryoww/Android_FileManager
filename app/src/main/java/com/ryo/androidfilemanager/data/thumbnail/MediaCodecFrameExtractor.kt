package com.ryo.androidfilemanager.data.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * MediaMetadataRetriever のソフトウェアデコーダでは扱えない動画(4K VP9 等)向けの
 * フォールバック。MediaExtractor + MediaCodec を直接使い、HW デコーダでフレームを
 * 1 枚だけ取り出す。
 *
 * MediaMetadataRetriever に比べて実装コストが高いため、あくまで pickThumbnailFrame
 * が失敗した場合の最終手段として使う。
 */
internal object MediaCodecFrameExtractor {
    private const val PERF_TAG = "ThumbPerf"

    // dequeueOutputBuffer の1回あたりタイムアウト
    private const val DEQUEUE_TIMEOUT_US = 10_000L

    // 全体でのループ上限(概算)。壊れたストリームでデコーダが応答し続けず
    // ハングするのを防ぐ
    private const val OVERALL_TIMEOUT_US = 30_000_000L

    /** 失敗時は null。例外は投げない(内部で catch して PERF_TAG でログ) */
    fun extractFrame(dataSource: MediaDataSource, maxSize: Int): Bitmap? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(dataSource)

            val trackIndex = selectVideoTrack(extractor) ?: run {
                Log.d(PERF_TAG, "mediacodec fallback failed: no video track")
                return null
            }
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: run {
                Log.d(PERF_TAG, "mediacodec fallback failed: no mime")
                return null
            }
            extractor.selectTrack(trackIndex)

            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            val seekTargetUs = durationUs / 10L
            extractor.seekTo(seekTargetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bitmap = decodeFirstFrame(extractor, codec, format, maxSize)
            if (bitmap == null) {
                Log.d(PERF_TAG, "mediacodec fallback failed: no frame decoded")
            }
            bitmap
        } catch (throwable: Throwable) {
            Log.d(PERF_TAG, "mediacodec fallback failed: ${throwable.message}", throwable)
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) {
                return index
            }
        }
        return null
    }

    private fun decodeFirstFrame(
        extractor: MediaExtractor,
        codec: MediaCodec,
        trackFormat: MediaFormat,
        maxSize: Int,
    ): Bitmap? {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputFormat = trackFormat
        val deadlineNs = System.nanoTime() + OVERALL_TIMEOUT_US * 1_000L

        while (System.nanoTime() < deadlineNs) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    val sampleSize = if (inputBuffer != null) {
                        extractor.readSampleData(inputBuffer, 0)
                    } else {
                        -1
                    }
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // 次のループで再試行
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outputFormat = codec.outputFormat
                }
                outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    // 非推奨 API。ByteBuffer 経路では特に何もしなくてよい
                }
                outputIndex >= 0 -> {
                    if (bufferInfo.size > 0) {
                        val bitmap = runCatching {
                            imageToBitmap(codec, outputIndex, outputFormat, maxSize)
                        }.getOrNull()
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (bitmap != null) {
                            return bitmap
                        }
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            return null
                        }
                    } else {
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            return null
                        }
                    }
                }
            }

            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                return null
            }
        }
        return null
    }

    private fun imageToBitmap(
        codec: MediaCodec,
        outputIndex: Int,
        format: MediaFormat,
        maxSize: Int,
    ): Bitmap? {
        val image = codec.getOutputImage(outputIndex) ?: return null
        val bitmap = try {
            val cropLeft = format.getIntOrDefault(MediaFormat.KEY_CROP_LEFT, 0)
            val cropTop = format.getIntOrDefault(MediaFormat.KEY_CROP_TOP, 0)
            val cropRight = format.getIntOrDefault(MediaFormat.KEY_CROP_RIGHT, image.width - 1)
            val cropBottom = format.getIntOrDefault(MediaFormat.KEY_CROP_BOTTOM, image.height - 1)
            val width = (cropRight - cropLeft + 1).coerceIn(1, image.width)
            val height = (cropBottom - cropTop + 1).coerceIn(1, image.height)

            val nv21 = imageToNv21(image, cropLeft, cropTop, width, height) ?: return null
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val jpegBytes = ByteArrayOutputStream().use { output ->
                yuvImage.compressToJpeg(Rect(0, 0, width, height), 85, output)
                output.toByteArray()
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSize)
            }
            val decoded = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
                ?: return null
            val scaled = decoded.scaleDownTo(maxSize)

            val rotation = format.getIntOrDefault(MediaFormat.KEY_ROTATION, 0)
            scaled.rotatedBy(rotation)
        } finally {
            image.close()
        }
        return bitmap
    }

    /** YUV_420_888 の Image を NV21 バイト列へ詰め替える(pixelStride/rowStride を考慮) */
    private fun imageToNv21(
        image: Image,
        cropLeft: Int,
        cropTop: Int,
        width: Int,
        height: Int,
    ): ByteArray? {
        if (image.format != ImageFormat.YUV_420_888) {
            return null
        }
        val planes = image.planes
        if (planes.size < 3) {
            return null
        }

        val nv21 = ByteArray(width * height + 2 * ((width + 1) / 2) * ((height + 1) / 2))

        val yPlane = planes[0]
        var outputOffset = 0
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        for (row in 0 until height) {
            val rowStart = (row + cropTop) * yRowStride + cropLeft * yPixelStride
            for (col in 0 until width) {
                nv21[outputOffset++] = yBuffer.get(rowStart + col * yPixelStride)
            }
        }

        // VU の順で詰める(NV21 は V,U の順)
        val uPlane = planes[1]
        val vPlane = planes[2]
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        val chromaWidth = (width + 1) / 2
        val chromaHeight = (height + 1) / 2
        val chromaCropLeft = cropLeft / 2
        val chromaCropTop = cropTop / 2

        for (row in 0 until chromaHeight) {
            val vRowStart = (row + chromaCropTop) * vRowStride + chromaCropLeft * vPixelStride
            val uRowStart = (row + chromaCropTop) * uRowStride + chromaCropLeft * uPixelStride
            for (col in 0 until chromaWidth) {
                nv21[outputOffset++] = vBuffer.get(vRowStart + col * vPixelStride)
                nv21[outputOffset++] = uBuffer.get(uRowStart + col * uPixelStride)
            }
        }

        return nv21
    }

    private fun MediaFormat.getIntOrDefault(key: String, default: Int): Int =
        if (containsKey(key)) getInteger(key) else default

    private fun sampleSizeFor(width: Int, height: Int, maxSize: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= maxSize && height / (sampleSize * 2) >= maxSize) {
            sampleSize *= 2
        }
        return sampleSize
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

    private fun Bitmap.rotatedBy(rotationDegrees: Int): Bitmap {
        if (rotationDegrees != 90 && rotationDegrees != 180 && rotationDegrees != 270) {
            return this
        }
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (rotated !== this) {
            recycle()
        }
        return rotated
    }
}
