package com.ryo.androidfilemanager.viewer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ryo.androidfilemanager.data.model.OpenedFile
import java.io.File

@Composable
fun UnsupportedViewerScreen(
    openedFile: OpenedFile,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uri = openedFile.localUriOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Unsupported File",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "This format is not supported by the in-app viewer. Try opening it with another app.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            enabled = uri != null,
            onClick = {
                if (uri != null) {
                    val externalUri = uri.toExternalShareUri(context)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(externalUri, "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open with"))
                }
            },
        ) {
            Text(text = "Open externally")
        }
    }
}

private fun Uri.toExternalShareUri(context: android.content.Context): Uri {
    if (scheme != "file") {
        return this
    }

    val path = path ?: return this
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        File(path),
    )
}
