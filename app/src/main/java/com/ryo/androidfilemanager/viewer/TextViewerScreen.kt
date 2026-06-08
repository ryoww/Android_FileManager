package com.ryo.androidfilemanager.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.ryo.androidfilemanager.data.model.OpenedFile
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
        modifier = modifier,
    )
}

@Composable
internal fun TextFileContent(
    openedFile: OpenedFile,
    monospace: Boolean,
    modifier: Modifier = Modifier,
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
            .horizontalScroll(rememberScrollState())
            .padding(18.dp),
    ) {
        Text(
            text = text,
            style = if (monospace) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}

private const val MAX_TEXT_CHARS = 300_000
