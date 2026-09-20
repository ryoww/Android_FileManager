package com.ryo.androidfilemanager.smb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SmbConnectionFormCard(
    uiState: SmbExplorerUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onShareNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onDomainChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTest: () -> Unit,
    onConnect: () -> Unit,
    onClearSaved: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (uiState.hasSavedConnection) "Saved connection" else "Connect to a share",
                style = MaterialTheme.typography.titleMedium,
            )
            if (uiState.connected) {
                OutlinedButton(onClick = onDone) {
                    Text(text = "Done")
                }
            }
        }
        ConnectionFields(
            uiState = uiState,
            onHostChange = onHostChange,
            onPortChange = onPortChange,
            onShareNameChange = onShareNameChange,
            onUsernameChange = onUsernameChange,
            onDomainChange = onDomainChange,
            onPasswordChange = onPasswordChange,
        )
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onTest) {
            Text(text = "Test")
        }
        Button(onClick = onConnect) {
            Text(text = if (uiState.connected) "Reconnect" else "Connect")
        }
        if (uiState.hasSavedConnection && !uiState.connected) {
            OutlinedButton(onClick = onClearSaved) {
                Text(text = "Clear saved")
            }
        }
        if (uiState.isLoading) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ConnectionFields(
    uiState: SmbExplorerUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onShareNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onDomainChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = uiState.form.host,
                onValueChange = onHostChange,
                label = { Text(text = "Host") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = uiState.form.port,
                onValueChange = onPortChange,
                label = { Text(text = "Port") },
                modifier = Modifier.weight(0.45f),
            )
        }
        OutlinedTextField(
            value = uiState.form.shareName,
            onValueChange = onShareNameChange,
            label = { Text(text = "Share name") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = uiState.form.username,
                onValueChange = onUsernameChange,
                label = { Text(text = "Username") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = uiState.form.domain,
                onValueChange = onDomainChange,
                label = { Text(text = "Domain") },
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = uiState.form.password,
            onValueChange = onPasswordChange,
            label = { Text(text = "Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
