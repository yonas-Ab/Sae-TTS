package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.TtsModelId
import com.example.ui.ComparisonScreen
import com.example.ui.HistoryScreen
import com.example.ui.HomeScreen
import com.example.ui.ModelScreen
import com.example.ui.SaeBenchmarkViewModel
import com.example.ui.SettingsScreen
import com.example.ui.SpeechTestScreen
import com.example.ui.theme.SaeTtsBenchmarkTheme
import kotlinx.serialization.Serializable

@Serializable data object ModelsRoute
@Serializable data object SpeechRoute
@Serializable data object CompareRoute
@Serializable data object HistoryRoute
@Serializable data object SettingsRoute
@Serializable data class ModelDetailRoute(val modelIdName: String)

private data class TopLevelDestination(
    val label: String,
    val icon: ImageVector,
    val route: Any,
    val testTag: String,
)

private val topLevelDestinations = listOf(
    TopLevelDestination("Models", Icons.Filled.Storage, ModelsRoute, "nav_models"),
    TopLevelDestination("Speech", Icons.Filled.GraphicEq, SpeechRoute, "nav_speech"),
    TopLevelDestination("Compare", Icons.Filled.CompareArrows, CompareRoute, "nav_compare"),
    TopLevelDestination("History", Icons.Filled.History, HistoryRoute, "nav_history"),
    TopLevelDestination("Settings", Icons.Filled.Settings, SettingsRoute, "nav_settings"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SaeBenchmarkViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            SaeTtsBenchmarkTheme(darkTheme = settings.preferDarkTheme) {
                SaeBenchmarkRoot(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaeBenchmarkRoot(
    viewModel: SaeBenchmarkViewModel,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val modelStatuses by viewModel.modelStatuses.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val lexiconStatus by viewModel.lexiconStatus.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val telemetry by viewModel.deviceTelemetry.collectAsStateWithLifecycle()
    val availableStorageBytes by viewModel.availableStorageBytes.collectAsStateWithLifecycle()
    val audioCacheBytes by viewModel.audioCacheBytes.collectAsStateWithLifecycle()
    val speechState by viewModel.speechTestState.collectAsStateWithLifecycle()
    val comparisonState by viewModel.comparisonState.collectAsStateWithLifecycle()
    val benchmarks by viewModel.benchmarks.collectAsStateWithLifecycle()
    val ratings by viewModel.ratings.collectAsStateWithLifecycle()
    val phonemeText by viewModel.phonemeInputText.collectAsStateWithLifecycle()
    val phonemeReport by viewModel.phonemeReport.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shareIntentEvents.collect { shareIntent ->
            runCatching { context.startActivity(shareIntent) }
        }
    }

    val isDetailRoute = currentDest?.hasRoute<ModelDetailRoute>() == true
    val activeTitle = when {
        currentDest?.hasRoute<ModelsRoute>() == true -> "Sae TTS Benchmark"
        currentDest?.hasRoute<SpeechRoute>() == true -> "Speech Test"
        currentDest?.hasRoute<CompareRoute>() == true -> "Model Comparison"
        currentDest?.hasRoute<HistoryRoute>() == true -> "Benchmark History"
        currentDest?.hasRoute<SettingsRoute>() == true -> "Settings & Phoneme Lab"
        else -> "Sae TTS Benchmark"
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                if (!isDetailRoute) {
                    TopAppBar(title = { Text(activeTitle) })
                }
            },
            bottomBar = {
                if (!useRail && !isDetailRoute) {
                    NavigationBar {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentDest?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail && !isDetailRoute) {
                    NavigationRail {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentDest?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ModelsRoute,
                    modifier = Modifier.weight(1f),
                ) {
                    composable<ModelsRoute> {
                        HomeScreen(
                            deviceTelemetry = telemetry,
                            availableStorageBytes = availableStorageBytes,
                            modelStatuses = modelStatuses,
                            downloadStates = downloadStates,
                            engineState = engineState,
                            threadCount = settings.threadCount,
                            onDownloadModel = viewModel::downloadModel,
                            onCancelDownload = viewModel::cancelDownload,
                            onDiscardPartial = viewModel::discardPartialDownload,
                            onDeleteModel = viewModel::deleteModel,
                            onLoadModel = viewModel::loadModel,
                            onUnloadModel = viewModel::unloadCurrentModel,
                            onOpenModelDetails = { id ->
                                navController.navigate(ModelDetailRoute(id.name))
                            },
                            onRefreshTelemetry = viewModel::refreshDeviceTelemetry,
                        )
                    }

                    composable<ModelDetailRoute> { entry ->
                        val route = entry.toRoute<ModelDetailRoute>()
                        val modelId = runCatching { TtsModelId.valueOf(route.modelIdName) }
                            .getOrDefault(TtsModelId.KITTEN_NANO_V08)
                        ModelScreen(
                            modelId = modelId,
                            status = modelStatuses[modelId],
                            onBack = { navController.popBackStack() },
                            onDownload = { viewModel.downloadModel(modelId) },
                            onLoad = { viewModel.loadModel(modelId) },
                            onDelete = { viewModel.deleteModel(modelId) },
                        )
                    }

                    composable<SpeechRoute> {
                        SpeechTestScreen(
                            uiState = speechState,
                            modelStatuses = modelStatuses,
                            engineState = engineState,
                            playbackState = playbackState,
                            supertonicSteps = settings.supertonicSteps,
                            onSelectModel = viewModel::selectSpeechTestModel,
                            onSelectSpeaker = viewModel::selectSpeechTestSpeaker,
                            onTextChange = viewModel::updateSpeechTestText,
                            onApplyPreset = viewModel::applySpeechTestPreset,
                            onAppendInlineTag = viewModel::appendInlineTagToSpeechText,
                            onSpeedChange = viewModel::updateSpeechSpeed,
                            onSynthesize = viewModel::synthesizeSpeechTest,
                            onStop = viewModel::stopSpeechSynthesis,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onReplayAudio = viewModel::replayAudio,
                            onStopAudio = viewModel::stopAudio,
                            onSeekAudio = viewModel::seekAudio,
                            onVolumeChange = viewModel::setAudioVolume,
                        )
                    }

                    composable<CompareRoute> {
                        ComparisonScreen(
                            uiState = comparisonState,
                            modelStatuses = modelStatuses,
                            playbackState = playbackState,
                            savedRatings = ratings,
                            onTextChange = viewModel::updateComparisonText,
                            onApplyPreset = viewModel::applyComparisonPreset,
                            onToggleModel = viewModel::toggleComparisonModel,
                            onSelectSpeaker = viewModel::selectComparisonSpeaker,
                            onSpeedChange = viewModel::updateComparisonSpeed,
                            onRunComparison = viewModel::runSequentialComparison,
                            onCancelComparison = viewModel::cancelComparison,
                            onPlayResult = viewModel::playMeasuredResult,
                            onSaveSubjectiveRating = viewModel::saveSubjectiveEvaluation,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onReplayAudio = viewModel::replayAudio,
                            onStopAudio = viewModel::stopAudio,
                            onSeekAudio = viewModel::seekAudio,
                            onVolumeChange = viewModel::setAudioVolume,
                        )
                    }

                    composable<HistoryRoute> {
                        HistoryScreen(
                            benchmarks = benchmarks,
                            ratings = ratings,
                            playbackState = playbackState,
                            onPlayEntry = viewModel::playHistoryEntity,
                            onDeleteEntry = viewModel::deleteHistoryEntry,
                            onClearAll = viewModel::clearAllHistory,
                            onExportCsv = viewModel::exportBenchmarksCsv,
                            onExportJson = viewModel::exportBenchmarksJson,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onReplayAudio = viewModel::replayAudio,
                            onStopAudio = viewModel::stopAudio,
                            onSeekAudio = viewModel::seekAudio,
                            onVolumeChange = viewModel::setAudioVolume,
                        )
                    }

                    composable<SettingsRoute> {
                        SettingsScreen(
                            settings = settings,
                            availableStorageBytes = availableStorageBytes,
                            audioCacheBytes = audioCacheBytes,
                            lexiconStatus = lexiconStatus,
                            phonemeInputText = phonemeText,
                            phonemeReport = phonemeReport,
                            onThreadCountChange = viewModel::setThreadCount,
                            onSupertonicStepsChange = viewModel::setSupertonicSteps,
                            onThemeToggle = viewModel::setPreferDarkTheme,
                            onClearAudioCache = viewModel::clearAudioOutputsCache,
                            onDownloadSharedLexicon = viewModel::downloadSharedLexicon,
                            onPhonemeTextChange = viewModel::runPhonemePreview,
                        )
                    }
                }
            }
        }
    }
}
