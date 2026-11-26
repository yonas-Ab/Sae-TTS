package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.PlaybackState
import com.example.data.BenchmarkEntity
import com.example.data.ModelManifest
import com.example.data.SubjectiveRatingEntity
import com.example.data.TtsModelId
import com.example.ui.components.AudioPlaybackDeck
import com.example.ui.components.StatusPill
import com.example.ui.components.formatMs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    benchmarks: List<BenchmarkEntity>,
    ratings: List<SubjectiveRatingEntity>,
    playbackState: PlaybackState,
    onPlayEntry: (BenchmarkEntity) -> Unit,
    onDeleteEntry: (BenchmarkEntity) -> Unit,
    onClearAll: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onReplayAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onSeekAudio: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf<TtsModelId?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val ratingMap = remember(ratings) {
        ratings.associateBy { "${it.batchId}|${it.modelId}" }
    }
    val filtered = remember(benchmarks, selectedFilter) {
        if (selectedFilter == null) benchmarks else benchmarks.filter { it.modelId == selectedFilter!!.name }
    }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Benchmark History?") },
            text = { Text("This deletes all saved benchmark metrics, subjective ratings, and rendered WAV files from app storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearAll()
                    },
                    modifier = Modifier.testTag("confirm_clear_history_button"),
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Benchmark History", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${benchmarks.size} saved runs • ${ratings.size} subjective ratings",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { showClearConfirm = true },
                            enabled = benchmarks.isNotEmpty(),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("clear_all_history_button"),
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all history", tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onExportCsv,
                            enabled = benchmarks.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("export_csv_button"),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Export CSV", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export CSV")
                        }
                        OutlinedButton(
                            onClick = onExportJson,
                            enabled = benchmarks.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("export_json_button"),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Export JSON", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedFilter == null,
                            onClick = { selectedFilter = null },
                            label = { Text("All") },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("history_filter_all"),
                        )
                        ModelManifest.allModels.forEach { spec ->
                            FilterChip(
                                selected = selectedFilter == spec.id,
                                onClick = { selectedFilter = spec.id },
                                label = { Text(spec.displayName) },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("history_filter_${spec.id.storageFolderName}"),
                            )
                        }
                    }
                }
            }
        }

        item {
            AudioPlaybackDeck(
                playbackState = playbackState,
                onTogglePlayPause = onTogglePlayPause,
                onStop = onStopAudio,
                onReplay = onReplayAudio,
                onSeek = onSeekAudio,
                onVolumeChange = onVolumeChange,
                modifier = Modifier.widthIn(max = 720.dp),
            )
        }

        if (filtered.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                ) {
                    Text(
                        text = "No benchmark runs recorded yet. Synthesize speech or run a sequential comparison to populate history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                val rating = if (item.batchId.isNotEmpty()) ratingMap["${item.batchId}|${item.modelId}"] else null
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.modelDisplayName} • ${item.speakerName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${dateFormatter.format(Date(item.createdAtMs))} • ${item.runType} (${item.presetName})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusPill(
                                text = String.format(Locale.US, "RTF %.2fx", item.realTimeFactor),
                                containerColor = if (item.realTimeFactor <= 1.0) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                },
                                contentColor = if (item.realTimeFactor <= 1.0) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                },
                            )
                        }

                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Text(
                            text = buildString {
                                append("Init: ${formatMs(item.initTimeMs)} (${if (item.isColdInit) "cold" else "warm"})")
                                append(" • Synth: ${formatMs(item.synthesisTimeMs)}")
                                append(" • Audio: ${formatMs(item.audioDurationMs)}")
                                item.rssAfterMb?.let { append(" • RSS: ${String.format(Locale.US, "%.0f MB", it)}") }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        if (rating != null) {
                            Text(
                                text = "Subjective: Overall ${rating.overallPreference}/5 (Nat ${rating.naturalness}, Exp ${rating.expressiveness}, Char ${rating.characterIdentity}, Pron ${rating.pronunciation})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { onPlayEntry(item) },
                                enabled = !item.wavFilePath.isNullOrBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("history_play_${item.id}"),
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Replay WAV", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Replay WAV")
                            }
                            IconButton(
                                onClick = { onDeleteEntry(item) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("history_delete_${item.id}"),
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete benchmark entry", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}
