package com.ryo.androidfilemanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A back-navigation action shown at the trailing edge of a [BrowserHeader]. */
internal data class NavigateUpAction(
    val label: String,
    val contentDescription: String,
    val onClick: () -> Unit,
)

/**
 * Shared "title + subtitle (+ optional back button)" header used by the
 * Explorer and SMB browser screens.
 */
@Composable
internal fun BrowserHeader(
    title: String,
    subtitle: String,
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    navigateUp: NavigateUpAction? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (navigateUp != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrowseLabelButton(
                    label = navigateUp.label,
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = navigateUp.contentDescription,
                    onClick = navigateUp.onClick,
                )
            }
        }
    }
}
