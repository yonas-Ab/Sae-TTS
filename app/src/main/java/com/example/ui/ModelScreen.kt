package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.DownloadPhase
import com.example.data.InstalledModelStatus
import com.example.data.ModelManifest
import com.example.data.TtsModelId
import com.example.ui.components.MetricTile
import com.example.ui.components.formatBytesHuman

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(
    modelId: TtsModelId,
    status: InstalledModelStatus?,
    downloadPhase: DownloadPhase,
    onBack: () -> Unit,
    onDownload: (String?) -> Unit,
    onCancelDownload: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = ModelManifest.byId(modelId)
    val isInstalled = status?.isInstalled == true
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var customUrlText by remember(modelId) { mutableStateOf(spec.archiveUrl) }

    if (showCustomUrlDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            title = { Text("Download URL Override") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Leave the official sherpa-onnx URL or paste a custom HTTPS .tar.bz2 mirror:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = customUrlText,
                        onValueChange = { customUrlText = it },
                        label = { Text("HTTPS .tar.bz2 archive URL") },
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_url_input"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCustomUrlDialog = false
                        onDownload(customUrlText.trim())
                    },
                    modifier = Modifier.testTag("confirm_custom_url_download"),
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUrlDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(spec.displayName) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("model_detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to models",
                        )
                    }
                },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(spec.family, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(spec.description, style = MaterialTheme.typography.bodyLarge)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile(
                                label = "SAMPLE RATE",
                                value = "${spec.nominalSampleRate} Hz",
                                modifier = Modifier.weight(1f),
                            )
                            MetricTile(
                                label = "STREAMING",
                                value = if (spec.isStreamingSupported) "Yes" else "Batch Only",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile(
                                label = "INSTALLED SIZE",
                                value = if (isInstalled) formatBytesHuman(status?.installedSizeBytes ?: 0L) else "Not installed",
                                modifier = Modifier.weight(1f),
                            )
                            MetricTile(
                                label = "SPEAKERS",
                                value = "${spec.defaultSpeakers.size} voices",
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Text("Identifier: ${spec.identifier}", style = MaterialTheme.typography.labelMedium)
                        Text("License: ${spec.licenseDetails}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Expressiveness: ${spec.expressivenessNote}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Source URL: ${spec.archiveUrl}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = "Storage folder", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("App-Specific Storage Inspection", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = status?.modelDirectory?.absolutePath ?: "models/${spec.id.storageFolderName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val verified = status?.verifiedFiles.orEmpty()
                        val missing = status?.missingFiles.orEmpty()
                        verified.forEach { file ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Verified", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(file, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        missing.forEach { file ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ErrorOutline, contentDescription = "Missing", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Missing: $file", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.RecordVoiceOver, contentDescription = "Available speakers", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Available Speakers", style = MaterialTheme.typography.titleMedium)
                        }
                        spec.defaultSpeakers.forEach { sp ->
                            Text(
                                text = "#${sp.id}  ${sp.name} — ${sp.subtitle}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            item {
                val isDownloading = downloadPhase is DownloadPhase.Checking ||
                    downloadPhase is DownloadPhase.Downloading ||
                    downloadPhase is DownloadPhase.VerifyingArchive ||
                    downloadPhase is DownloadPhase.Extracting

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (downloadPhase is DownloadPhase.Downloading) {
                        val progress = if (downloadPhase.totalBytes > 0L) {
                            (downloadPhase.bytesDownloaded.toFloat() / downloadPhase.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else null
                        if (progress != null) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            text = "${formatBytesHuman(downloadPhase.bytesDownloaded)} downloaded",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (downloadPhase is DownloadPhase.Failed) {
                        Text(
                            text = downloadPhase.message,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when {
                            isDownloading -> {
                                Button(
                                    onClick = onCancelDownload,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("detail_cancel_download_button"),
                                ) {
                                    Text("Cancel Download")
                                }
                            }
                            isInstalled -> {
                                Button(
                                    onClick = onLoad,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("detail_load_button"),
                                ) {
                                    Text("Load Model")
                                }
                                OutlinedButton(
                                    onClick = onDelete,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("detail_delete_button"),
                                ) {
                                    Text("Delete Files")
                                }
                            }
                            else -> {
                                Button(
                                    onClick = { onDownload(null) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("detail_download_button"),
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = "Download model", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download (~${spec.approxDownloadMb} MB)")
                                }
                                OutlinedButton(
                                    onClick = { showCustomUrlDialog = true },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("detail_custom_url_button"),
                                ) {
                                    Icon(Icons.Filled.Link, contentDescription = "Custom mirror URL", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Custom URL")
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
