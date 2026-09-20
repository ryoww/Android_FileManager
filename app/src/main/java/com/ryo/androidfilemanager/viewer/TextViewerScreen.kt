package com.ryo.androidfilemanager.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ryo.androidfilemanager.core.domain.OpenedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TextViewerScreen(
    openedFile: OpenedFile,
    modifier: Modifier = Modifier,
) {
    TextFileContent(
        openedFile = openedFile,
        monospace = false,
        wrapLines = true,
        modifier = modifier,
    )
}

/**
 * @param wrapLines true なら画面幅で折り返す（散文のテキスト向け）。false なら折り返さず
 * 横スクロールにする（コードはインデントと行の対応が崩れると読みにくいため）。
 */
@Composable
internal fun TextFileContent(
    openedFile: OpenedFile,
    monospace: Boolean,
    modifier: Modifier = Modifier,
    wrapLines: Boolean = false,
) {
    val context = LocalContext.current
    val uri = openedFile.localUriOrNull()

    if (uri == null) {
        StreamUnsupportedPlaceholder(openedFile = openedFile, modifier = modifier)
        return
    }

    val text by produceState(initialValue = "Loading...", key1 = uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.readText().take(MAX_TEXT_CHARS)
                    }
                } ?: "Text file could not be opened."
            }.getOrElse { throwable ->
                throwable.message ?: "Text file could not be decoded as UTF-8."
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(if (wrapLines) Modifier else Modifier.horizontalScroll(rememberScrollState()))
            .padding(18.dp),
    ) {
        Text(
            text = text,
            // 折り返すときは幅を画面いっぱいに固定しないと、横スクロールが無くても
            // Text が内容幅で計測されて折り返し位置が決まらない
            modifier = if (wrapLines) Modifier.fillMaxWidth() else Modifier,
            style = if (monospace) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}

private const val MAX_TEXT_CHARS = 300_000
