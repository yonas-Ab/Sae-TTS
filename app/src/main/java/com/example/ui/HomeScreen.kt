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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.benchmark.DeviceTelemetry
import com.example.data.DownloadPhase
import com.example.data.InstalledModelStatus
import com.example.data.ModelManifest
import com.example.data.TtsModelId
import com.example.data.TtsModelSpec
import com.example.tts.EngineState
import com.example.ui.components.MetricTile
import com.example.ui.components.StatusPill
import com.example.ui.components.formatBytesHuman
import com.example.ui.components.formatSecondsShort
import java.util.Locale

@Composable
fun HomeScreen(
    deviceTelemetry: DeviceTelemetry,
    availableStorageBytes: Long,
    modelStatuses: Map<TtsModelId, InstalledModelStatus>,
    downloadStates: Map<TtsModelId, DownloadPhase>,
    engineState: EngineState,
    threadCount: Int,
    onDownloadModel: (TtsModelId) -> Unit,
    onCancelDownload: (TtsModelId) -> Unit,
    onDiscardPartial: (TtsModelId) -> Unit,
    onDeleteModel: (TtsModelId) -> Unit,
    onLoadModel: (TtsModelId) -> Unit,
    onUnloadModel: () -> Unit,
    onOpenModelDetails: (TtsModelId) -> Unit,
    onRefreshTelemetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            DeviceTelemetryHeaderCard(
                telemetry = deviceTelemetry,
                availableStorageBytes = availableStorageBytes,
                engineState = engineState,
                threadCount = threadCount,
                onRefresh = onRefreshTelemetry,
                onUnload = onUnloadModel,
                modifier = Modifier.widthIn(max = 720.dp),
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Offline Neural TTS Models",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Single-model residency",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(ModelManifest.allModels, key = { it.id.name }) { spec ->
            val status = modelStatuses[spec.id]
            val downloadPhase = downloadStates[spec.id] ?: DownloadPhase.Idle
            ModelBenchmarkCard(
                spec = spec,
                status = status,
                downloadPhase = downloadPhase,
                engineState = engineState,
                onDownload = { onDownloadModel(spec.id) },
                onCancelDownload = { onCancelDownload(spec.id) },
                onDiscardPartial = { onDiscardPartial(spec.id) },
                onDelete = { onDeleteModel(spec.id) },
                onLoad = { onLoadModel(spec.id) },
                onUnload = onUnloadModel,
                onOpenDetails = { onOpenModelDetails(spec.id) },
                modifier = Modifier.widthIn(max = 720.dp),
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DeviceTelemetryHeaderCard(
    telemetry: DeviceTelemetry,
    availableStorageBytes: Long,
    engineState: EngineState,
    threadCount: Int,
    onRefresh: () -> Unit,
    onUnload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${telemetry.manufacturer.uppercase(Locale.US)} ${telemetry.deviceModel}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Android ${telemetry.androidVersion} (API ${telemetry.apiLevel}) • ${telemetry.supportedAbis.firstOrNull() ?: "unknown"} • ${telemetry.cpuCores} cores",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("refresh_telemetry_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh device telemetry",
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricTile(
                    label = "FREE RAM",
                    value = "${telemetry.availableRamMb} / ${telemetry.totalRamMb} MB",
                    accent = if (telemetry.lowMemoryFlag) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "APP RSS",
                    value = telemetry.processRssMb?.let { String.format(Locale.US, "%.0f MB", it) } ?: "N/A",
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricTile(
                    label = "FREE STORAGE",
                    value = formatBytesHuman(availableStorageBytes),
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "THREADS",
                    value = "$threadCount CPU",
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            ActiveEngineStrip(engineState = engineState, onUnload = onUnload)
        }
    }
}

@Composable
private fun ActiveEngineStrip(
    engineState: EngineState,
    onUnload: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = "Active TTS Engine",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                val text = when (engineState) {
                    is EngineState.Unloaded -> "No model loaded in RAM"
                    is EngineState.Loading -> "Loading ${ModelManifest.byId(engineState.modelId).displayName}..."
                    is EngineState.Loaded -> "Loaded: ${ModelManifest.byId(engineState.modelId).displayName} (${engineState.initTimeMs} ms)"
                    is EngineState.Synthesizing -> "Synthesizing on ${ModelManifest.byId(engineState.modelId).displayName}..."
                    is EngineState.Error -> "Engine error: ${engineState.message}"
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (engineState is EngineState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (engineState is EngineState.Loaded) {
                OutlinedButton(
                    onClick = onUnload,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("unload_active_engine_button"),
                ) {
                    Text("Unload")
                }
            }
        }
    }
}

@Composable
private fun ModelBenchmarkCard(
    spec: TtsModelSpec,
    status: InstalledModelStatus?,
    downloadPhase: DownloadPhase,
    engineState: EngineState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDiscardPartial: () -> Unit,
    onDelete: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isInstalled = status?.isInstalled == true
    val isLoaded = engineState is EngineState.Loaded && engineState.modelId == spec.id
    val isLoadingThis = engineState is EngineState.Loading && engineState.modelId == spec.id
    val isDownloading = downloadPhase is DownloadPhase.Checking ||
        downloadPhase is DownloadPhase.Downloading ||
        downloadPhase is DownloadPhase.VerifyingArchive ||
        downloadPhase is DownloadPhase.Extracting

    val partialBytes = status?.partialDownloadBytes ?: 0L

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isLoaded) 1.5.dp else 1.dp,
            color = if (isLoaded) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .testTag("model_card_${spec.id.storageFolderName}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spec.displayName,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "${spec.roleTag} • ${spec.nominalSampleRate / 1000.0} kHz • ${spec.defaultSpeakers.size} speakers",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                when {
                    isLoaded -> StatusPill(
                        text = "LOADED",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    isInstalled -> StatusPill(
                        text = "INSTALLED",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    partialBytes > 0L -> StatusPill(
                        text = "PARTIAL",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    else -> StatusPill(
                        text = "~${spec.approxDownloadMb} MB",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = spec.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.SdStorage,
                        contentDescription = "Disk usage",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isInstalled) {
                            "Disk: ${formatBytesHuman(status?.installedSizeBytes ?: 0L)}"
                        } else {
                            "Est. disk: ~${spec.approxDiskMb} MB"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "License: ${spec.licenseName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Download / Verification / Extraction Progress UI
            when (downloadPhase) {
                is DownloadPhase.Checking -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Checking storage and HTTP range support...",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                is DownloadPhase.Downloading -> {
                    val progress = if (downloadPhase.totalBytes > 0L) {
                        (downloadPhase.bytesDownloaded.toFloat() / downloadPhase.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else null

                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    val pct = if (progress != null) "${(progress * 100).toInt()}%" else "--%"
                    val totalTxt = if (downloadPhase.totalBytes > 0L) formatBytesHuman(downloadPhase.totalBytes) else "?"
                    val speedTxt = if (downloadPhase.speedBytesPerSec > 0) {
                        "${formatBytesHuman(downloadPhase.speedBytesPerSec.toLong())}/s"
                    } else "measuring..."
                    val etaTxt = downloadPhase.etaSeconds?.let { "ETA ${formatSecondsShort(it)}" } ?: ""
                    val resumeTxt = if (downloadPhase.resumedFromBytes > 0L) " • Resumed" else ""

                    Text(
                        text = "${formatBytesHuman(downloadPhase.bytesDownloaded)} / $totalTxt ($pct) • $speedTxt $etaTxt$resumeTxt",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is DownloadPhase.VerifyingArchive -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Verifying archive (${formatBytesHuman(downloadPhase.bytesTotal)})...",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                is DownloadPhase.Extracting -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Extracting (${downloadPhase.entriesExtracted} files): ${downloadPhase.currentEntry}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                is DownloadPhase.Cancelled -> {
                    Text(
                        text = "Download paused/cancelled. ${formatBytesHuman(downloadPhase.partialBytesSaved)} saved for resume.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                is DownloadPhase.Failed -> {
                    Text(
                        text = downloadPhase.message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> Unit
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    isDownloading -> {
                        Button(
                            onClick = onCancelDownload,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("cancel_download_${spec.id.storageFolderName}"),
                        ) {
                            Icon(Icons.Filled.Cancel, contentDescription = "Cancel download", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancel")
                        }
                    }

                    isInstalled -> {
                        if (isLoaded) {
                            FilledTonalButton(
                                onClick = onUnload,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("unload_model_${spec.id.storageFolderName}"),
                            ) {
                                Icon(Icons.Filled.StopCircle, contentDescription = "Unload model", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Unload")
                            }
                        } else {
                            Button(
                                onClick = onLoad,
                                enabled = !isLoadingThis && engineState !is EngineState.Synthesizing,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("load_model_${spec.id.storageFolderName}"),
                            ) {
                                if (isLoadingThis) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Loading...")
                                } else {
                                    Icon(Icons.Filled.PlayCircleOutline, contentDescription = "Load model", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Load Model")
                                }
                            }
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("delete_model_${spec.id.storageFolderName}"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete ${spec.displayName}",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    else -> {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("download_model_${spec.id.storageFolderName}"),
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = "Download model", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (partialBytes > 0L) "Resume (${formatBytesHuman(partialBytes)})" else "Download")
                        }
                        if (partialBytes > 0L) {
                            IconButton(
                                onClick = onDiscardPartial,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("discard_partial_${spec.id.storageFolderName}"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Discard partial download",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onOpenDetails,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("inspect_model_${spec.id.storageFolderName}"),
                ) {
                    Icon(Icons.Filled.Info, contentDescription = "Model details", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Details")
                }
            }
        }
    }
}
