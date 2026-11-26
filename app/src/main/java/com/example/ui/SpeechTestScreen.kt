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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.PlaybackState
import com.example.benchmark.BenchmarkPresets
import com.example.data.InstalledModelStatus
import com.example.data.ModelManifest
import com.example.data.TtsModelId
import com.example.tts.EngineState
import com.example.ui.components.AudioPlaybackDeck
import com.example.ui.components.MetricTile
import com.example.ui.components.formatMs
import java.util.Locale

@Composable
fun SpeechTestScreen(
    uiState: SpeechTestUiState,
    modelStatuses: Map<TtsModelId, InstalledModelStatus>,
    engineState: EngineState,
    playbackState: PlaybackState,
    supertonicSteps: Int,
    onSelectModel: (TtsModelId) -> Unit,
    onSelectSpeaker: (TtsModelId, Int) -> Unit,
    onTextChange: (String) -> Unit,
    onApplyPreset: (String) -> Unit,
    onAppendInlineTag: (String) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSynthesize: () -> Unit,
    onStop: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onReplayAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onSeekAudio: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedSpec = ModelManifest.byId(uiState.selectedModelId)
    val selectedStatus = modelStatuses[uiState.selectedModelId]
    val isInstalled = selectedStatus?.isInstalled == true

    val speakers = if (engineState is EngineState.Loaded && engineState.modelId == uiState.selectedModelId) {
        engineState.speakers
    } else {
        selectedSpec.defaultSpeakers
    }
    val selectedSpeakerId = uiState.selectedSpeakers[uiState.selectedModelId] ?: 0
    val selectedSpeaker = speakers.firstOrNull { it.id == selectedSpeakerId } ?: speakers.first()
    var speakerMenuExpanded by remember { mutableStateOf(false) }

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
                    Text("Model & Voice Selection", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModelManifest.allModels.forEach { spec ->
                            val installed = modelStatuses[spec.id]?.isInstalled == true
                            FilterChip(
                                selected = uiState.selectedModelId == spec.id,
                                onClick = { onSelectModel(spec.id) },
                                label = {
                                    Text(if (installed) spec.displayName else "${spec.displayName} (Not installed)")
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("speech_model_chip_${spec.id.storageFolderName}"),
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { speakerMenuExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("speech_speaker_selector"),
                        ) {
                            Text(
                                text = "Speaker #${selectedSpeaker.id}: ${selectedSpeaker.name} (${selectedSpeaker.subtitle})",
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose speaker")
                        }
                        DropdownMenu(
                            expanded = speakerMenuExpanded,
                            onDismissRequest = { speakerMenuExpanded = false },
                        ) {
                            speakers.forEach { sp ->
                                DropdownMenuItem(
                                    text = { Text("#${sp.id} ${sp.name} — ${sp.subtitle}") },
                                    onClick = {
                                        onSelectSpeaker(uiState.selectedModelId, sp.id)
                                        speakerMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Speed: ${String.format(Locale.US, "%.2fx", uiState.speed)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (uiState.selectedModelId == TtsModelId.SUPERTONIC_3_INT8) {
                            Text(
                                text = "Flow steps: $supertonicSteps (in Settings)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Slider(
                        value = uiState.speed,
                        onValueChange = onSpeedChange,
                        valueRange = 0.6f..1.5f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("speech_speed_slider"),
                    )
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
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Built-in Test Sentences", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${BenchmarkPresets.countWords(uiState.inputText)} w • ${uiState.inputText.length} chars",
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
                        BenchmarkPresets.speechTestSentences.forEach { preset ->
                            FilterChip(
                                selected = uiState.selectedSentenceId == preset.id,
                                onClick = { onApplyPreset(preset.id) },
                                label = { Text(preset.label) },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("sentence_preset_${preset.id}"),
                            )
                        }
                    }

                    Text(
                        text = "Note: Preset phrasing & punctuation test natural prosody only. None of these engines support an emotion-ID parameter.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (uiState.selectedModelId == TtsModelId.SUPERTONIC_3_INT8) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Cue tags:", style = MaterialTheme.typography.labelMedium)
                            listOf("<laugh>", "<breath>", "<sigh>").forEach { tag ->
                                SuggestionChip(
                                    onClick = { onAppendInlineTag(tag) },
                                    label = { Text(tag) },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("inline_tag_${tag.trim('<', '>')}"),
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = onTextChange,
                        label = { Text("Text to synthesize offline") },
                        minLines = 3,
                        maxLines = 7,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("speech_text_input"),
                    )

                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onSynthesize,
                            enabled = isInstalled && !uiState.isSynthesizing && uiState.inputText.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("generate_speech_button"),
                        ) {
                            if (uiState.isSynthesizing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Synthesizing...")
                            } else {
                                Icon(Icons.Filled.GraphicEq, contentDescription = "Generate speech", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isInstalled) "Generate & Play" else "Model Not Installed")
                            }
                        }

                        if (uiState.isSynthesizing) {
                            Button(
                                onClick = onStop,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("stop_synthesis_button"),
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop synthesis", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop")
                            }
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

        uiState.latestResult?.let { res ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Measured Telemetry • ${res.modelDisplayName}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile(
                                label = if (res.isColdInit) "COLD INIT" else "WARM INIT",
                                value = formatMs(res.initTimeMs),
                                modifier = Modifier.weight(1f),
                            )
                            MetricTile(
                                label = "SYNTH / TTFA",
                                value = formatMs(res.synthesisTimeMs),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile(
                                label = "AUDIO LEN",
                                value = formatMs(res.audioDurationMs),
                                modifier = Modifier.weight(1f),
                            )
                            MetricTile(
                                label = "RTF (synth/dur)",
                                value = String.format(Locale.US, "%.2fx", res.realTimeFactor),
                                accent = if (res.realTimeFactor <= 1.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            text = "${res.backendInfo} • ${res.sampleRate} Hz • Batch synthesis (TTFA = total synth time)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}
