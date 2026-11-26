package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.benchmark.DeviceTelemetry
import com.example.data.BenchmarkEntity
import com.example.data.InstalledModelStatus
import com.example.data.ModelManifest
import com.example.data.SubjectiveRatingEntity
import com.example.data.TtsModelId
import com.example.tts.EngineState
import com.example.ui.components.MetricTile
import com.example.ui.components.StatusPill
import com.example.ui.components.formatBytesHuman
import com.example.ui.components.formatMs
import java.util.Locale

private data class ModelDashboardStats(
    val modelId: TtsModelId,
    val displayName: String,
    val shortName: String,
    val runCount: Int,
    val avgRtf: Double,
    val minRtf: Double,
    val avgInitMs: Long,
    val avgSynthMs: Long,
    val totalAudioMs: Long,
    val avgRssDeltaMb: Double?,
    val maxRssMb: Double?,
)

private data class SubjectiveDashboardStats(
    val modelId: TtsModelId,
    val displayName: String,
    val count: Int,
    val avgOverall: Double,
    val avgNaturalness: Double,
    val avgExpressiveness: Double,
    val avgCharacter: Double,
    val avgPronunciation: Double,
)

@Composable
fun DashboardScreen(
    telemetry: DeviceTelemetry,
    storageSummary: StorageSummary,
    modelStatuses: Map<TtsModelId, InstalledModelStatus>,
    engineState: EngineState,
    threadCount: Int,
    benchmarks: List<BenchmarkEntity>,
    ratings: List<SubjectiveRatingEntity>,
    onRefreshTelemetry: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToSpeech: () -> Unit,
    onNavigateToCompare: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modelStats = remember(benchmarks) {
        ModelManifest.allModels.mapNotNull { spec ->
            val rows = benchmarks.filter { it.modelId == spec.id.name }
            if (rows.isEmpty()) return@mapNotNull null
            val rssDeltas = rows.mapNotNull { it.rssDeltaMb }
            val rssAfters = rows.mapNotNull { it.rssAfterMb }
            val shortLabel = when (spec.id) {
                TtsModelId.KITTEN_NANO_V08 -> "Kitten"
                TtsModelId.KOKORO_EN_V019 -> "Kokoro"
                TtsModelId.SUPERTONIC_3_INT8 -> "Supertonic"
            }
            ModelDashboardStats(
                modelId = spec.id,
                displayName = spec.displayName,
                shortName = shortLabel,
                runCount = rows.size,
                avgRtf = rows.map { it.realTimeFactor }.average(),
                minRtf = rows.minOf { it.realTimeFactor },
                avgInitMs = rows.map { it.initTimeMs }.average().toLong(),
                avgSynthMs = rows.map { it.synthesisTimeMs }.average().toLong(),
                totalAudioMs = rows.sumOf { it.audioDurationMs },
                avgRssDeltaMb = if (rssDeltas.isNotEmpty()) rssDeltas.average() else null,
                maxRssMb = rssAfters.maxOrNull(),
            )
        }
    }

    val subjectiveStats = remember(ratings) {
        ModelManifest.allModels.mapNotNull { spec ->
            val rows = ratings.filter { it.modelId == spec.id.name }
            if (rows.isEmpty()) return@mapNotNull null
            SubjectiveDashboardStats(
                modelId = spec.id,
                displayName = spec.displayName,
                count = rows.size,
                avgOverall = rows.map { it.overallPreference }.average(),
                avgNaturalness = rows.map { it.naturalness }.average(),
                avgExpressiveness = rows.map { it.expressiveness }.average(),
                avgCharacter = rows.map { it.characterIdentity }.average(),
                avgPronunciation = rows.map { it.pronunciation }.average(),
            )
        }.sortedByDescending { it.avgOverall }
    }

    val installedCount = modelStatuses.values.count { it.isInstalled }
    val fastestModel = modelStats.minByOrNull { it.avgRtf }
    val lowestInitModel = modelStats.minByOrNull { it.avgInitMs }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            SystemRamGaugeCard(
                telemetry = telemetry,
                storageSummary = storageSummary,
                engineState = engineState,
                threadCount = threadCount,
                onRefresh = onRefreshTelemetry,
                onOpenSettings = onNavigateToSettings,
                modifier = Modifier.widthIn(max = 720.dp),
            )
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
                    Text("Benchmark KPIs", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(
                            label = "RECORDED RUNS",
                            value = "${benchmarks.size}",
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            label = "MODELS READY",
                            value = "$installedCount / ${ModelManifest.allModels.size}",
                            accent = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(
                            label = "FASTEST AVG RTF",
                            value = fastestModel?.let {
                                "${String.format(Locale.US, "%.2fx", it.avgRtf)} ${it.shortName}"
                            } ?: "No runs yet",
                            accent = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            label = "FASTEST INIT",
                            value = lowestInitModel?.let {
                                "${formatMs(it.avgInitMs)} ${it.shortName}"
                            } ?: "No runs yet",
                            accent = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToModels,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("dash_open_models_button"),
                        ) {
                            Icon(Icons.Filled.Storage, contentDescription = "Model Manager", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Models")
                        }
                        OutlinedButton(
                            onClick = onNavigateToSpeech,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("dash_open_speech_button"),
                        ) {
                            Icon(Icons.Filled.GraphicEq, contentDescription = "Speech Test", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Speak")
                        }
                        Button(
                            onClick = onNavigateToCompare,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("dash_open_compare_button"),
                        ) {
                            Icon(Icons.Filled.CompareArrows, contentDescription = "Compare Models", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Compare")
                        }
                    }
                }
            }
        }

        if (modelStats.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Analytics,
                            contentDescription = "No benchmark charts yet",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                        Text("No Telemetry Charts Yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Download a model in Model Manager and run a Speech Test or Sequential Comparison to populate RTF, latency, memory, and quality charts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            item {
                RtfBarChartCard(
                    stats = modelStats,
                    modifier = Modifier.widthIn(max = 720.dp),
                )
            }

            item {
                LatencyBreakdownCard(
                    stats = modelStats,
                    modifier = Modifier.widthIn(max = 720.dp),
                )
            }
        }

        if (subjectiveStats.isNotEmpty()) {
            item {
                SubjectiveLeaderboardCard(
                    stats = subjectiveStats,
                    modifier = Modifier.widthIn(max = 720.dp),
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun SystemRamGaugeCard(
    telemetry: DeviceTelemetry,
    storageSummary: StorageSummary,
    engineState: EngineState,
    threadCount: Int,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalMb = telemetry.totalRamMb.coerceAtLeast(1L)
    val usedMb = (totalMb - telemetry.availableRamMb).coerceAtLeast(0L)
    val usedFraction = (usedMb.toFloat() / totalMb.toFloat()).coerceIn(0f, 1f)

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val gaugeColor = when {
        telemetry.lowMemoryFlag || usedFraction > 0.88f -> MaterialTheme.colorScheme.error
        usedFraction > 0.72f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

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
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "Android ${telemetry.androidVersion} • ${telemetry.supportedAbis.firstOrNull() ?: "ARM64"} • ${telemetry.cpuCores} cores (${threadCount}T TTS)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("dashboard_refresh_button"),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh dashboard telemetry")
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("dashboard_settings_button"),
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Open Settings & Phoneme Lab")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(96.dp),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokePx = 10.dp.toPx()
                        val arcSize = Size(size.width - strokePx, size.height - strokePx)
                        val topLeft = Offset(strokePx / 2f, strokePx / 2f)
                        drawArc(
                            color = trackColor,
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = gaugeColor,
                            startAngle = 135f,
                            sweepAngle = 270f * usedFraction,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(usedFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = gaugeColor,
                        )
                        Text(
                            text = "RAM USED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Free RAM: ${telemetry.availableRamMb} / ${telemetry.totalRamMb} MB",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Process RSS: ${telemetry.processRssMb?.let { String.format(Locale.US, "%.1f MB", it) } ?: "N/A"} • Peak HWM: ${telemetry.processHwmMb?.let { String.format(Locale.US, "%.1f MB", it) } ?: "N/A"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Models Disk: ${formatBytesHuman(storageSummary.modelsBytes)} • Free: ${formatBytesHuman(storageSummary.availableBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val engineLabel = when (engineState) {
                        is EngineState.Unloaded -> "Engine: Unloaded (0 models in RAM)"
                        is EngineState.Loading -> "Engine: Loading ${ModelManifest.byId(engineState.modelId).displayName}"
                        is EngineState.Loaded -> "Engine: ${ModelManifest.byId(engineState.modelId).displayName} Ready"
                        is EngineState.Synthesizing -> "Engine: Synthesizing..."
                        is EngineState.Error -> "Engine Error"
                    }
                    StatusPill(
                        text = engineLabel,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RtfBarChartCard(
    stats: List<ModelDashboardStats>,
    modifier: Modifier = Modifier,
) {
    val maxRtf = maxOf(1.25, stats.maxOf { it.avgRtf } * 1.15)
    val fastBarColor = MaterialTheme.colorScheme.secondary
    val slowBarColor = MaterialTheme.colorScheme.tertiary
    val thresholdColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth(),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Speed, contentDescription = "RTF Chart", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Real-Time Factor (RTF) by Model", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "< 1.0x = faster than real-time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            ) {
                val barSlotWidth = size.width / stats.size.coerceAtLeast(1)
                val barWidth = (barSlotWidth * 0.48f).coerceAtMost(72.dp.toPx())
                val chartHeight = size.height - 8.dp.toPx()

                // Baseline
                drawLine(
                    color = gridColor,
                    start = Offset(0f, chartHeight),
                    end = Offset(size.width, chartHeight),
                    strokeWidth = 1.dp.toPx(),
                )

                // Dashed 1.0x real-time line
                val thresholdY = chartHeight - ((1.0 / maxRtf).toFloat() * chartHeight)
                drawLine(
                    color = thresholdColor,
                    start = Offset(0f, thresholdY),
                    end = Offset(size.width, thresholdY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                )

                stats.forEachIndexed { idx, item ->
                    val frac = (item.avgRtf / maxRtf).toFloat().coerceIn(0.04f, 1f)
                    val barHeight = frac * chartHeight
                    val left = idx * barSlotWidth + (barSlotWidth - barWidth) / 2f
                    val top = chartHeight - barHeight
                    drawRoundRect(
                        color = if (item.avgRtf <= 1.0) fastBarColor else slowBarColor,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                stats.forEach { item ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(item.shortName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = String.format(Locale.US, "avg %.2fx", item.avgRtf),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.avgRtf <= 1.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = "best ${String.format(Locale.US, "%.2fx", item.minRtf)} (n=${item.runCount})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LatencyBreakdownCard(
    stats: List<ModelDashboardStats>,
    modifier: Modifier = Modifier,
) {
    val maxLatencyMs = stats.maxOf { maxOf(it.avgInitMs, it.avgSynthMs) }.coerceAtLeast(100L).toFloat()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Latency & Memory Footprint Breakdown", style = MaterialTheme.typography.titleMedium)

            stats.forEachIndexed { idx, item ->
                if (idx > 0) HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(item.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Audio generated: ${formatMs(item.totalAudioMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Avg Init", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
                        LinearProgressIndicator(
                            progress = { (item.avgInitMs / maxLatencyMs).coerceIn(0.04f, 1f) },
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatMs(item.avgInitMs), style = MaterialTheme.typography.labelMedium)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Avg Synth", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
                        LinearProgressIndicator(
                            progress = { (item.avgSynthMs / maxLatencyMs).coerceIn(0.04f, 1f) },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatMs(item.avgSynthMs), style = MaterialTheme.typography.labelMedium)
                    }

                    val memText = buildString {
                        item.avgRssDeltaMb?.let { append("Avg RSS Delta: ${String.format(Locale.US, "%+.1f MB", it)}") }
                        item.maxRssMb?.let {
                            if (isNotEmpty()) append(" • ")
                            append("Max RSS: ${String.format(Locale.US, "%.0f MB", it)}")
                        }
                    }
                    if (memText.isNotEmpty()) {
                        Text(
                            text = memText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectiveLeaderboardCard(
    stats: List<SubjectiveDashboardStats>,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Subjective quality leaderboard",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Subjective Quality Leaderboard (1–5)", style = MaterialTheme.typography.titleMedium)
            }

            stats.forEach { item ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(item.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = String.format(Locale.US, "%.1f / 5.0 (n=%d)", item.avgOverall, item.count),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Text(
                            text = String.format(
                                Locale.US,
                                "Nat %.1f • Exp %.1f • Char %.1f • Pron %.1f",
                                item.avgNaturalness,
                                item.avgExpressiveness,
                                item.avgCharacter,
                                item.avgPronunciation,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
