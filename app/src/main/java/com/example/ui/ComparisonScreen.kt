package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.PlaybackState
import com.example.benchmark.BenchmarkPresets
import com.example.benchmark.ComparisonRunState
import com.example.benchmark.MeasuredSynthesisResult
import com.example.data.InstalledModelStatus
import com.example.data.ModelManifest
import com.example.data.SubjectiveRatingEntity
import com.example.data.TtsModelId
import com.example.ui.components.AudioPlaybackDeck
import com.example.ui.components.MetricTile
import com.example.ui.components.RatingRow
import com.example.ui.components.StatusPill
import com.example.ui.components.formatMs
import java.util.Locale

@Composable
fun ComparisonScreen(
    uiState: ComparisonUiState,
    modelStatuses: Map<TtsModelId, InstalledModelStatus>,
    playbackState: PlaybackState,
    savedRatings: List<SubjectiveRatingEntity>,
    onTextChange: (String) -> Unit,
    onApplyPreset: (String) -> Unit,
    onToggleModel: (TtsModelId) -> Unit,
    onSelectSpeaker: (TtsModelId, Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onRunComparison: () -> Unit,
    onCancelComparison: () -> Unit,
    onPlayResult: (MeasuredSynthesisResult) -> Unit,
    onSaveSubjectiveRating: (
        batchId: String,
        modelId: TtsModelId,
        naturalness: Int,
        expressiveness: Int,
        characterIdentity: Int,
        pronunciation: Int,
        overallPreference: Int,
        notes: String,
    ) -> Unit,
    onTogglePlayPause: () -> Unit,
    onReplayAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onSeekAudio: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = uiState.runState is ComparisonRunState.Running

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
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
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Sequential Benchmark Setup", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${BenchmarkPresets.countWords(uiState.sharedText)} words",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BenchmarkPresets.comparisonPresets.forEach { preset ->
                            FilterChip(
                                selected = uiState.selectedPresetId == preset.id,
                                onClick = { onApplyPreset(preset.id) },
                                label = { Text("${preset.label} (${preset.wordTarget})") },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("comparison_preset_${preset.id}"),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = uiState.sharedText,
                        onValueChange = onTextChange,
                        label = { Text("Shared benchmark prompt") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("comparison_text_input"),
                    )

                    Text("Models & Speakers (loaded & released one by one):", style = MaterialTheme.typography.labelLarge)

                    ModelManifest.allModels.forEach { spec ->
                        val installed = modelStatuses[spec.id]?.isInstalled == true
                        val checked = spec.id in uiState.enabledModels
                        val selectedSpId = uiState.selectedSpeakers[spec.id] ?: 0
                        val speaker = spec.defaultSpeakers.firstOrNull { it.id == selectedSpId } ?: spec.defaultSpeakers.first()
                        var expanded by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilterChip(
                                selected = checked,
                                onClick = { onToggleModel(spec.id) },
                                label = { Text(if (installed) spec.displayName else "${spec.displayName} (Missing)") },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("compare_toggle_${spec.id.storageFolderName}"),
                            )

                            Box {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("compare_speaker_${spec.id.storageFolderName}"),
                                ) {
                                    Text(speaker.name, maxLines = 1)
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select speaker")
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    spec.defaultSpeakers.forEach { sp ->
                                        DropdownMenuItem(
                                            text = { Text("#${sp.id} ${sp.name} — ${sp.subtitle}") },
                                            onClick = {
                                                onSelectSpeaker(spec.id, sp.id)
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Shared Speed: ${String.format(Locale.US, "%.2fx", uiState.speed)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = uiState.speed,
                        onValueChange = onSpeedChange,
                        valueRange = 0.6f..1.5f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("comparison_speed_slider"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onRunComparison,
                            enabled = !isRunning && uiState.sharedText.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("run_comparison_button"),
                        ) {
                            Icon(Icons.Filled.CompareArrows, contentDescription = "Run benchmark", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isRunning) "Benchmarking..." else "Run Sequential Benchmark")
                        }
                        if (isRunning) {
                            Button(
                                onClick = onCancelComparison,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("cancel_comparison_button"),
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = "Cancel comparison", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }

        when (val runState = uiState.runState) {
            is ComparisonRunState.Running -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(runState.phaseLabel, style = MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(
                                progress = { runState.stepIndex.toFloat() / runState.totalSteps.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            is ComparisonRunState.Failed -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                    ) {
                        Text(
                            text = runState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            is ComparisonRunState.Completed -> {
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

                if (runState.skippedMessages.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Skipped Models:", style = MaterialTheme.typography.labelLarge)
                                runState.skippedMessages.forEach { (id, reason) ->
                                    Text(
                                        text = "• ${ModelManifest.byId(id).displayName}: $reason",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Measured Telemetry (Objective)",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                    )
                }

                items(runState.results, key = { "${it.batchId}_${it.modelId.name}" }) { result ->
                    MeasuredComparisonCard(
                        result = result,
                        isPlayingThis = playbackState.activeClipKey == "result_${result.dbRowId}",
                        onPlay = { onPlayResult(result) },
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                }

                item {
                    Text(
                        text = "Subjective Evaluation (Listening Score 1–5)",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .padding(top = 4.dp),
                    )
                }

                items(runState.results, key = { "subj_${it.batchId}_${it.modelId.name}" }) { result ->
                    val existing = savedRatings.firstOrNull {
                        it.batchId == runState.batchId && it.modelId == result.modelId.name
                    }
                    SubjectiveEvaluationCard(
                        batchId = runState.batchId,
                        result = result,
                        existing = existing,
                        onSave = onSaveSubjectiveRating,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                }
            }

            ComparisonRunState.Idle -> Unit
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun MeasuredComparisonCard(
    result: MeasuredSynthesisResult,
    isPlayingThis: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isPlayingThis) 1.5.dp else 1.dp,
            color = if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        ),
        modifier = modifier.fillMaxWidth(),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(result.modelDisplayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Speaker: ${result.speakerName} • ${result.sampleRate} Hz",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("play_compare_${result.modelId.storageFolderName}"),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${result.modelDisplayName}", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Listen")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(
                    label = if (result.isColdInit) "COLD INIT" else "WARM INIT",
                    value = formatMs(result.initTimeMs),
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "TTFA / SYNTH",
                    value = formatMs(result.synthesisTimeMs),
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "AUDIO",
                    value = formatMs(result.audioDurationMs),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(
                    label = "RTF",
                    value = String.format(Locale.US, "%.2fx", result.realTimeFactor),
                    accent = if (result.realTimeFactor <= 1.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "RSS DELTA",
                    value = result.rssDeltaMb?.let { String.format(Locale.US, "%+.1f MB", it) } ?: "N/A",
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "PEAK RSS",
                    value = result.rssAfterMb?.let { String.format(Locale.US, "%.0f MB", it) } ?: "N/A",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SubjectiveEvaluationCard(
    batchId: String,
    result: MeasuredSynthesisResult,
    existing: SubjectiveRatingEntity?,
    onSave: (String, TtsModelId, Int, Int, Int, Int, Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var naturalness by remember(batchId, result.modelId) { mutableIntStateOf(existing?.naturalness ?: 4) }
    var expressiveness by remember(batchId, result.modelId) { mutableIntStateOf(existing?.expressiveness ?: 4) }
    var characterIdentity by remember(batchId, result.modelId) { mutableIntStateOf(existing?.characterIdentity ?: 4) }
    var pronunciation by remember(batchId, result.modelId) { mutableIntStateOf(existing?.pronunciation ?: 4) }
    var overall by remember(batchId, result.modelId) { mutableIntStateOf(existing?.overallPreference ?: 4) }
    var notes by remember(batchId, result.modelId) { mutableStateOf(existing?.notes ?: "") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(result.modelDisplayName, style = MaterialTheme.typography.titleMedium)
                if (existing != null) {
                    StatusPill(
                        text = "SAVED",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            val prefix = "subj_${result.modelId.storageFolderName}"
            RatingRow("Naturalness", naturalness, { naturalness = it }, "${prefix}_nat")
            RatingRow("Expressiveness", expressiveness, { expressiveness = it }, "${prefix}_exp")
            RatingRow("Character Identity", characterIdentity, { characterIdentity = it }, "${prefix}_chr")
            RatingRow("Pronunciation", pronunciation, { pronunciation = it }, "${prefix}_pro")
            RatingRow("Overall Preference", overall, { overall = it }, "${prefix}_ovr")

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Listening notes (artifacts, pacing, warmth...)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${prefix}_notes"),
            )

            Button(
                onClick = {
                    onSave(
                        batchId,
                        result.modelId,
                        naturalness,
                        expressiveness,
                        characterIdentity,
                        pronunciation,
                        overall,
                        notes,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("save_rating_${result.modelId.storageFolderName}"),
            ) {
                Icon(Icons.Filled.Save, contentDescription = "Save subjective evaluation", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Subjective Evaluation")
            }
        }
    }
}
