package com.olerast.suflyor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.olerast.suflyor.App
import com.olerast.suflyor.R
import com.olerast.suflyor.data.OnlineLibrary
import com.olerast.suflyor.data.OnlineLibraryException
import com.olerast.suflyor.data.OnlineProblem

@Composable
fun OnlineLibrarySettings() {
    val online = App.instance.onlineLibrary
    var showDialog by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        TextButton(onClick = {
            address = online.url
            invalid = false
            showDialog = true
        }) { Text(stringResource(R.string.online_link_title)) }
        Text(online.url.ifEmpty { stringResource(R.string.online_link_hint) }, color = Palette.TextMuted)
        if (online.url.isNotEmpty()) {
            TextButton(onClick = online::sync, enabled = !online.syncing) {
                Text(stringResource(R.string.online_sync))
            }
            Text(onlineLibraryStatus(online), color = if (online.error != null) Palette.Danger else Palette.TextMuted)
        }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.online_link_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it; invalid = false },
                        label = { Text(stringResource(R.string.online_link_address)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        isError = invalid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(if (invalid) R.string.online_error_url else R.string.online_link_format))
                }
            },
            confirmButton = {
                TextButton(enabled = !online.syncing, onClick = {
                    try {
                        online.configure(address)
                        showDialog = false
                    } catch (_: OnlineLibraryException) {
                        invalid = true
                    }
                }) { Text(stringResource(R.string.online_link_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
fun onlineLibraryStatus(online: OnlineLibrary): String = when {
    online.syncing -> stringResource(R.string.online_syncing)
    online.error != null -> {
        val error = online.error!!
        when (error.problem) {
            OnlineProblem.URL -> stringResource(R.string.online_error_url)
            OnlineProblem.NETWORK -> stringResource(R.string.online_error_network)
            OnlineProblem.TIMEOUT -> stringResource(R.string.online_error_timeout)
            OnlineProblem.HTTP -> stringResource(R.string.online_error_http, error.status)
            OnlineProblem.FORMAT -> stringResource(R.string.online_error_format)
            OnlineProblem.VERSION -> stringResource(R.string.online_error_version)
            OnlineProblem.SIZE -> stringResource(R.string.online_error_size)
            OnlineProblem.COUNT -> stringResource(R.string.online_error_count)
            OnlineProblem.ENTRY -> stringResource(R.string.online_error_entry)
            OnlineProblem.STORAGE -> stringResource(R.string.online_error_storage)
        }
    }
    online.syncedCount != null -> stringResource(R.string.online_synced, online.syncedCount!!)
    else -> stringResource(R.string.online_cached_hint)
}
