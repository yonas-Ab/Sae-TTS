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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.EngineSettings
import com.example.phoneme.PhonemeInvestigationReport
import com.example.ui.components.StatusPill
import com.example.ui.components.formatBytesHuman
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: EngineSettings,
    availableStorageBytes: Long,
    audioCacheBytes: Long,
    lexiconStatus: String?,
    phonemeInputText: String,
    phonemeReport: PhonemeInvestigationReport,
    onThreadCountChange: (Int) -> Unit,
    onSupertonicStepsChange: (Int) -> Unit,
    onThemeToggle: (Boolean) -> Unit,
    onClearAudioCache: () -> Unit,
    onDownloadSharedLexicon: () -> Unit,
    onPhonemeTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Tune, contentDescription = "Inference settings", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Engine & Runtime Settings", style = MaterialTheme.typography.titleMedium)
                    }

                    Text(
                        text = "CPU Inference Threads: ${settings.threadCount} (default 2 for Unisoc T606)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(1, 2, 3, 4).forEach { count ->
                            FilterChip(
                                selected = settings.threadCount == count,
                                onClick = { onThreadCountChange(count) },
                                label = { Text("${count}T") },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("thread_chip_$count"),
                            )
                        }
                    }

                    Text(
                        text = "Supertonic-3 Flow-Matching Steps: ${settings.supertonicSteps}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = settings.supertonicSteps.toFloat(),
                        onValueChange = { onSupertonicStepsChange(it.roundToInt()) },
                        valueRange = 2f..10f,
                        steps = 7,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("supertonic_steps_slider"),
                    )
                    Text(
                        text = "Lower steps (2–4) run faster on Unisoc T606; higher steps (6–10) refine clarity.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dark Telemetry Theme", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "High-contrast slate lab palette",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.preferDarkTheme,
                            onCheckedChange = onThemeToggle,
                            modifier = Modifier.testTag("dark_theme_switch"),
                        )
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Storage & Optional Lexicon", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Free app storage: ${formatBytesHuman(availableStorageBytes)} • Saved WAV outputs: ${formatBytesHuman(audioCacheBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (lexiconStatus != null) {
                        Text(
                            text = "Lexicon status: $lexiconStatus",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDownloadSharedLexicon,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("download_lexicon_button"),
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = "Download lexicon", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lexicon EN-US")
                        }
                        Button(
                            onClick = onClearAudioCache,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("clear_wav_cache_button"),
                        ) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = "Clear WAV cache", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear WAVs")
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.FaceRetouchingNatural, contentDescription = "Phoneme & VRM Lab", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Phoneme & VRM Lip-Sync Lab", style = MaterialTheme.typography.titleMedium)
                        }
                        StatusPill(
                            text = if (phonemeReport.supportsRealModelTimestamps) "TIMESTAMPS YES" else "NO MODEL TIMESTAMPS",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }

                    Text(
                        text = phonemeReport.capabilitySummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = phonemeReport.lipSyncRecommendation,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Loaded lexicon entries: ${phonemeReport.lexiconEntriesLoaded} • tokens.txt entries: ${phonemeReport.tokensTxtCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    OutlinedTextField(
                        value = phonemeInputText,
                        onValueChange = onPhonemeTextChange,
                        label = { Text("Preview text-level G2P -> VRM visemes (heuristic/lexicon, untimed)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("phoneme_preview_input"),
                    )

                    phonemeReport.wordTokens.take(12).forEach { token ->
                        Text(
                            text = "${token.word}  ->  /${token.ipaPreview}/  ->  [${token.vrmSequence.joinToString(", ") { it.blendshapeKey }}] (${token.source})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}
