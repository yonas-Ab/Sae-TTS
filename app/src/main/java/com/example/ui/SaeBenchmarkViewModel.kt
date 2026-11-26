package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioFileManager
import com.example.audio.AudioPlayer
import com.example.audio.PlaybackState
import com.example.benchmark.BenchmarkPresets
import com.example.benchmark.BenchmarkRunner
import com.example.benchmark.ComparisonRunState
import com.example.benchmark.DeviceTelemetry
import com.example.benchmark.MeasuredSynthesisResult
import com.example.benchmark.PerformanceMonitor
import com.example.data.BenchmarkDatabase
import com.example.data.BenchmarkEntity
import com.example.data.BenchmarkRepository
import com.example.data.DownloadPhase
import com.example.data.EngineSettings
import com.example.data.InstalledModelStatus
import com.example.data.ModelDownloadManager
import com.example.data.ModelManifest
import com.example.data.ModelRepository
import com.example.data.SettingsStore
import com.example.data.SubjectiveRatingEntity
import com.example.data.TtsModelId
import com.example.phoneme.PhonemeAnalyzer
import com.example.phoneme.PhonemeInvestigationReport
import com.example.tts.EngineState
import com.example.tts.TtsEngineManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class SpeechTestUiState(
    val selectedModelId: TtsModelId = TtsModelId.KITTEN_NANO_V08,
    val selectedSpeakers: Map<TtsModelId, Int> = mapOf(
        TtsModelId.KITTEN_NANO_V08 to 0,
        TtsModelId.KOKORO_EN_V019 to 0,
        TtsModelId.SUPERTONIC_3_INT8 to 0,
    ),
    val inputText: String = BenchmarkPresets.speechTestSentences.first().text,
    val selectedSentenceId: String = BenchmarkPresets.speechTestSentences.first().id,
    val speed: Float = 1.0f,
    val isSynthesizing: Boolean = false,
    val latestResult: MeasuredSynthesisResult? = null,
    val errorMessage: String? = null,
)

data class ComparisonUiState(
    val sharedText: String = BenchmarkPresets.comparisonPresets.first().text,
    val selectedPresetId: String = BenchmarkPresets.comparisonPresets.first().id,
    val speed: Float = 1.0f,
    val enabledModels: Set<TtsModelId> = setOf(
        TtsModelId.KITTEN_NANO_V08,
        TtsModelId.KOKORO_EN_V019,
        TtsModelId.SUPERTONIC_3_INT8,
    ),
    val selectedSpeakers: Map<TtsModelId, Int> = mapOf(
        TtsModelId.KITTEN_NANO_V08 to 0,
        TtsModelId.KOKORO_EN_V019 to 0,
        TtsModelId.SUPERTONIC_3_INT8 to 0,
    ),
    val runState: ComparisonRunState = ComparisonRunState.Idle,
)

data class StorageSummary(
    val modelsBytes: Long = 0L,
    val partialsBytes: Long = 0L,
    val lexiconBytes: Long = 0L,
    val audioCacheBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val modelsRootPath: String = "",
)

class SaeBenchmarkViewModel(application: Application) : AndroidViewModel(application) {

    private val modelRepository = ModelRepository(application)
    private val settingsStore = SettingsStore(application)
    private val audioFileManager = AudioFileManager(application)
    private val audioPlayer = AudioPlayer(audioFileManager)
    private val engineManager = TtsEngineManager(modelRepository)
    private val benchmarkDb = BenchmarkDatabase.getInstance(application)
    private val benchmarkRepository = BenchmarkRepository(application, benchmarkDb, audioFileManager)
    private val performanceMonitor = PerformanceMonitor(application)
    private val phonemeAnalyzer = PhonemeAnalyzer()

    private val downloadManager = ModelDownloadManager(
        repository = modelRepository,
        onModelFilesChanged = { refreshStorageAndModels() },
    )

    private val benchmarkRunner = BenchmarkRunner(
        modelRepository = modelRepository,
        engineManager = engineManager,
        audioFileManager = audioFileManager,
        benchmarkRepository = benchmarkRepository,
        performanceMonitor = performanceMonitor,
    )

    val downloadStates: StateFlow<Map<TtsModelId, DownloadPhase>> = downloadManager.states
    val lexiconStatus: StateFlow<String?> = downloadManager.lexiconStatus
    val engineState: StateFlow<EngineState> = engineManager.state
    val playbackState: StateFlow<PlaybackState> = audioPlayer.state

    val settings: StateFlow<EngineSettings> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = EngineSettings(),
    )

    val benchmarks: StateFlow<List<BenchmarkEntity>> = benchmarkRepository.benchmarksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val ratings: StateFlow<List<SubjectiveRatingEntity>> = benchmarkRepository.ratingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _modelStatuses = MutableStateFlow<Map<TtsModelId, InstalledModelStatus>>(emptyMap())
    val modelStatuses: StateFlow<Map<TtsModelId, InstalledModelStatus>> = _modelStatuses.asStateFlow()

    private val _deviceTelemetry = MutableStateFlow(performanceMonitor.captureTelemetry())
    val deviceTelemetry: StateFlow<DeviceTelemetry> = _deviceTelemetry.asStateFlow()

    private val _availableStorageBytes = MutableStateFlow(0L)
    val availableStorageBytes: StateFlow<Long> = _availableStorageBytes.asStateFlow()

    private val _audioCacheBytes = MutableStateFlow(0L)
    val audioCacheBytes: StateFlow<Long> = _audioCacheBytes.asStateFlow()

    private val _storageSummary = MutableStateFlow(StorageSummary())
    val storageSummary: StateFlow<StorageSummary> = _storageSummary.asStateFlow()

    private val _speechTestState = MutableStateFlow(SpeechTestUiState())
    val speechTestState: StateFlow<SpeechTestUiState> = _speechTestState.asStateFlow()

    private val _comparisonState = MutableStateFlow(ComparisonUiState())
    val comparisonState: StateFlow<ComparisonUiState> = _comparisonState.asStateFlow()

    private val _phonemeInputText = MutableStateFlow("Hello Sae! Testing offline speech synthesis.")
    val phonemeInputText: StateFlow<String> = _phonemeInputText.asStateFlow()

    private val _phonemeReport = MutableStateFlow(
        phonemeAnalyzer.analyzeText(_phonemeInputText.value, null, null),
    )
    val phonemeReport: StateFlow<PhonemeInvestigationReport> = _phonemeReport.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val _shareIntentEvents = MutableSharedFlow<Intent>(extraBufferCapacity = 4)
    val shareIntentEvents: SharedFlow<Intent> = _shareIntentEvents.asSharedFlow()

    private var speechSynthJob: Job? = null
    private var comparisonJob: Job? = null

    init {
        refreshStorageAndModels()
        runPhonemePreview(_phonemeInputText.value)
    }

    fun refreshStorageAndModels() {
        _modelStatuses.value = modelRepository.inspectAllModels()
        val avail = modelRepository.getAvailableStorageBytes()
        val audioBytes = audioFileManager.getAudioOutputsSizeBytes()
        _availableStorageBytes.value = avail
        _audioCacheBytes.value = audioBytes
        _storageSummary.value = StorageSummary(
            modelsBytes = modelRepository.getModelsTotalSizeBytes(),
            partialsBytes = modelRepository.getPartialDownloadsTotalBytes(),
            lexiconBytes = modelRepository.getSharedLexiconSizeBytes(),
            audioCacheBytes = audioBytes,
            availableBytes = avail,
            modelsRootPath = modelRepository.getModelsRootDir().absolutePath,
        )
        _deviceTelemetry.value = performanceMonitor.captureTelemetry()
    }

    fun refreshDeviceTelemetry() {
        refreshStorageAndModels()
    }

    // --- Model Management ---

    fun downloadModel(modelId: TtsModelId, customUrl: String? = null) {
        val spec = ModelManifest.byId(modelId)
        downloadManager.startDownload(spec, viewModelScope, customUrl)
    }

    fun cancelDownload(modelId: TtsModelId) {
        downloadManager.cancelDownload(modelId)
    }

    fun discardPartialDownload(modelId: TtsModelId) {
        downloadManager.discardPartialAndReset(modelId)
        refreshStorageAndModels()
    }

    fun deleteModel(modelId: TtsModelId) {
        viewModelScope.launch {
            downloadManager.cancelDownload(modelId)
            engineManager.releaseForDeletion(modelId)
            modelRepository.deleteModel(modelId)
            downloadManager.resetState(modelId)
            refreshStorageAndModels()
            _toastEvents.tryEmit("${ModelManifest.byId(modelId).displayName} deleted.")
        }
    }

    fun loadModel(modelId: TtsModelId) {
        viewModelScope.launch {
            val threads = settings.value.threadCount
            val res = engineManager.ensureModelLoaded(modelId, threads)
            refreshDeviceTelemetry()
            res.onSuccess { loaded ->
                _speechTestState.update { it.copy(selectedModelId = modelId, errorMessage = null) }
                _toastEvents.tryEmit("Loaded ${ModelManifest.byId(modelId).displayName} (${loaded.initTimeMs} ms)")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.message ?: "Failed to load model")
            }
        }
    }

    fun unloadCurrentModel() {
        viewModelScope.launch {
            engineManager.unloadCurrentModel()
            refreshDeviceTelemetry()
            _toastEvents.tryEmit("Active TTS engine released from memory.")
        }
    }

    fun downloadSharedLexicon() {
        viewModelScope.launch {
            val res = downloadManager.downloadSharedEnglishLexicon()
            refreshStorageAndModels()
            runPhonemePreview(_phonemeInputText.value)
            res.onSuccess {
                _toastEvents.tryEmit("Shared English lexicon installed.")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.message ?: "Lexicon download failed.")
            }
        }
    }

    fun deleteSharedLexicon() {
        viewModelScope.launch {
            modelRepository.deleteSharedLexicon()
            refreshStorageAndModels()
            runPhonemePreview(_phonemeInputText.value)
            _toastEvents.tryEmit("Shared lexicon deleted.")
        }
    }

    fun verifyAllModels() {
        refreshStorageAndModels()
        val installedCount = _modelStatuses.value.values.count { it.isInstalled }
        _toastEvents.tryEmit("Verified $installedCount of ${ModelManifest.allModels.size} models on disk.")
    }

    fun clearAllPartialDownloads() {
        viewModelScope.launch {
            val deleted = modelRepository.clearAllPartialDownloads()
            ModelManifest.allModels.forEach { downloadManager.resetState(it.id) }
            refreshStorageAndModels()
            _toastEvents.tryEmit(if (deleted > 0) "Cleaned $deleted partial download file(s)." else "No partial files to clean.")
        }
    }

    // --- Speech Test ---

    fun selectSpeechTestModel(modelId: TtsModelId) {
        _speechTestState.update { it.copy(selectedModelId = modelId, errorMessage = null) }
    }

    fun selectSpeechTestSpeaker(modelId: TtsModelId, speakerId: Int) {
        _speechTestState.update { state ->
            state.copy(selectedSpeakers = state.selectedSpeakers + (modelId to speakerId))
        }
    }

    fun updateSpeechTestText(text: String) {
        _speechTestState.update { it.copy(inputText = text, selectedSentenceId = "custom", errorMessage = null) }
    }

    fun applySpeechTestPreset(presetId: String) {
        val preset = BenchmarkPresets.speechTestSentences.firstOrNull { it.id == presetId } ?: return
        _speechTestState.update {
            it.copy(
                inputText = preset.text,
                selectedSentenceId = preset.id,
                errorMessage = null,
            )
        }
    }

    fun appendInlineTagToSpeechText(tag: String) {
        _speechTestState.update { current ->
            val separator = if (current.inputText.isBlank() || current.inputText.endsWith(" ")) "" else " "
            current.copy(inputText = "${current.inputText}$separator$tag ", selectedSentenceId = "custom")
        }
    }

    fun updateSpeechSpeed(speed: Float) {
        _speechTestState.update { it.copy(speed = speed.coerceIn(0.6f, 1.6f)) }
    }

    fun synthesizeSpeechTest() {
        if (speechSynthJob?.isActive == true) return
        val current = _speechTestState.value
        val modelId = current.selectedModelId
        val speakerId = current.selectedSpeakers[modelId] ?: 0
        val currentSettings = settings.value

        speechSynthJob = viewModelScope.launch {
            _speechTestState.update { it.copy(isSynthesizing = true, errorMessage = null) }
            try {
                val presetLabel = BenchmarkPresets.speechTestSentences
                    .firstOrNull { it.id == current.selectedSentenceId }?.label ?: "Custom"

                val result = benchmarkRunner.runSingleSpeechTest(
                    modelId = modelId,
                    speakerId = speakerId,
                    text = current.inputText,
                    speed = current.speed,
                    threadCount = currentSettings.threadCount,
                    supertonicSteps = currentSettings.supertonicSteps,
                    presetName = presetLabel,
                )
                refreshStorageAndModels()
                result.onSuccess { measured ->
                    _speechTestState.update {
                        it.copy(isSynthesizing = false, latestResult = measured, errorMessage = null)
                    }
                    audioPlayer.playFloatArray(
                        samples = measured.samples,
                        sampleRate = measured.sampleRate,
                        label = "${measured.modelDisplayName} • ${measured.speakerName}",
                        clipKey = "speech_${measured.dbRowId}",
                    )
                }.onFailure { err ->
                    _speechTestState.update {
                        it.copy(isSynthesizing = false, errorMessage = err.message ?: "Synthesis failed.")
                    }
                }
            } catch (_: CancellationException) {
                _speechTestState.update {
                    it.copy(isSynthesizing = false, errorMessage = "Synthesis cancelled.")
                }
            }
        }
    }

    fun stopSpeechSynthesis() {
        engineManager.stopCurrentSynthesis()
        speechSynthJob?.cancel()
        audioPlayer.stop()
        _speechTestState.update { it.copy(isSynthesizing = false) }
    }

    // --- Comparison Benchmark ---

    fun updateComparisonText(text: String) {
        _comparisonState.update { it.copy(sharedText = text, selectedPresetId = "custom") }
    }

    fun applyComparisonPreset(presetId: String) {
        val preset = BenchmarkPresets.comparisonPresets.firstOrNull { it.id == presetId } ?: return
        _comparisonState.update { it.copy(sharedText = preset.text, selectedPresetId = preset.id) }
    }

    fun toggleComparisonModel(modelId: TtsModelId) {
        _comparisonState.update { state ->
            val next = if (modelId in state.enabledModels && state.enabledModels.size > 1) {
                state.enabledModels - modelId
            } else {
                state.enabledModels + modelId
            }
            state.copy(enabledModels = next)
        }
    }

    fun selectComparisonSpeaker(modelId: TtsModelId, speakerId: Int) {
        _comparisonState.update { state ->
            state.copy(selectedSpeakers = state.selectedSpeakers + (modelId to speakerId))
        }
    }

    fun updateComparisonSpeed(speed: Float) {
        _comparisonState.update { it.copy(speed = speed.coerceIn(0.6f, 1.6f)) }
    }

    fun runSequentialComparison() {
        if (comparisonJob?.isActive == true) return
        val state = _comparisonState.value
        val orderedQueue = ModelManifest.allModels
            .map { it.id }
            .filter { it in state.enabledModels }
            .map { it to (state.selectedSpeakers[it] ?: 0) }

        val presetLabel = BenchmarkPresets.comparisonPresets
            .firstOrNull { it.id == state.selectedPresetId }?.label ?: "Custom"
        val curSettings = settings.value

        comparisonJob = viewModelScope.launch {
            audioPlayer.stop()
            try {
                val finalState = benchmarkRunner.runSequentialComparison(
                    modelsAndSpeakers = orderedQueue,
                    text = state.sharedText,
                    speed = state.speed,
                    threadCount = curSettings.threadCount,
                    supertonicSteps = curSettings.supertonicSteps,
                    presetName = presetLabel,
                    onProgress = { progress ->
                        _comparisonState.update { it.copy(runState = progress) }
                    },
                )
                _comparisonState.update { it.copy(runState = finalState) }
                refreshStorageAndModels()
            } catch (_: CancellationException) {
                _comparisonState.update {
                    it.copy(runState = ComparisonRunState.Failed("Comparison benchmark cancelled."))
                }
                refreshStorageAndModels()
            }
        }
    }

    fun cancelComparison() {
        engineManager.stopCurrentSynthesis()
        comparisonJob?.cancel()
    }

    fun saveSubjectiveEvaluation(
        batchId: String,
        modelId: TtsModelId,
        naturalness: Int,
        expressiveness: Int,
        characterIdentity: Int,
        pronunciation: Int,
        overallPreference: Int,
        notes: String,
    ) {
        viewModelScope.launch {
            benchmarkRepository.saveSubjectiveRating(
                batchId = batchId,
                modelId = modelId.name,
                naturalness = naturalness,
                expressiveness = expressiveness,
                characterIdentity = characterIdentity,
                pronunciation = pronunciation,
                overallPreference = overallPreference,
                notes = notes,
            )
            _toastEvents.tryEmit("Saved subjective rating for ${ModelManifest.byId(modelId).displayName}.")
        }
    }

    // --- Audio Playback Controls ---

    fun playMeasuredResult(result: MeasuredSynthesisResult) {
        val wavFile = File(result.wavFilePath)
        val label = "${result.modelDisplayName} • ${result.speakerName}"
        val key = "result_${result.dbRowId}"
        if (result.samples.isNotEmpty()) {
            audioPlayer.playFloatArray(result.samples, result.sampleRate, label, key)
        } else if (wavFile.exists()) {
            audioPlayer.playWavFile(wavFile, label, key)
        } else {
            _toastEvents.tryEmit("Audio file missing for ${result.modelDisplayName}.")
        }
    }

    fun playHistoryEntity(entity: BenchmarkEntity) {
        val path = entity.wavFilePath
        if (path.isNullOrBlank()) {
            _toastEvents.tryEmit("No saved WAV file for this entry.")
            return
        }
        val file = File(path)
        if (!file.exists()) {
            _toastEvents.tryEmit("Saved WAV file no longer exists on disk.")
            return
        }
        audioPlayer.playWavFile(
            file = file,
            label = "${entity.modelDisplayName} • ${entity.speakerName}",
            clipKey = "history_${entity.id}",
        )
    }

    fun togglePlayPause() = audioPlayer.togglePlayPause()
    fun replayAudio() = audioPlayer.replay()
    fun stopAudio() = audioPlayer.stop()
    fun seekAudio(fraction: Float) = audioPlayer.seekToFraction(fraction)
    fun setAudioVolume(volume: Float) = audioPlayer.setVolume(volume)

    // --- History & Export ---

    fun deleteHistoryEntry(entity: BenchmarkEntity) {
        viewModelScope.launch {
            benchmarkRepository.deleteBenchmark(entity)
            refreshStorageAndModels()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            audioPlayer.stop()
            benchmarkRepository.clearHistory()
            refreshStorageAndModels()
            _toastEvents.tryEmit("Cleared benchmark history and saved WAV outputs.")
        }
    }

    fun clearAudioOutputsCache() {
        viewModelScope.launch {
            audioPlayer.stop()
            audioFileManager.clearAllAudioOutputs()
            refreshStorageAndModels()
            _toastEvents.tryEmit("Cleared generated WAV output files.")
        }
    }

    fun exportBenchmarksCsv() {
        viewModelScope.launch {
            runCatching { benchmarkRepository.exportCsv() }.onSuccess { (file, uri) ->
                _toastEvents.tryEmit("Exported CSV: ${file.name}")
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _shareIntentEvents.tryEmit(Intent.createChooser(share, "Share CSV Benchmark Report"))
            }.onFailure { err ->
                _toastEvents.tryEmit("CSV export failed: ${err.message}")
            }
        }
    }

    fun exportBenchmarksJson() {
        viewModelScope.launch {
            runCatching { benchmarkRepository.exportJson() }.onSuccess { (file, uri) ->
                _toastEvents.tryEmit("Exported JSON: ${file.name}")
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _shareIntentEvents.tryEmit(Intent.createChooser(share, "Share JSON Benchmark Report"))
            }.onFailure { err ->
                _toastEvents.tryEmit("JSON export failed: ${err.message}")
            }
        }
    }

    // --- Settings & Phoneme Investigation ---

    fun setThreadCount(count: Int) {
        viewModelScope.launch {
            settingsStore.setThreadCount(count)
            _toastEvents.tryEmit("Inference threads set to ${count.coerceIn(1, 4)}. Takes effect on next model load.")
        }
    }

    fun setSupertonicSteps(steps: Int) {
        viewModelScope.launch {
            settingsStore.setSupertonicSteps(steps)
        }
    }

    fun setPreferDarkTheme(dark: Boolean) {
        viewModelScope.launch {
            settingsStore.setPreferDarkTheme(dark)
        }
    }

    fun runPhonemePreview(text: String) {
        _phonemeInputText.value = text
        val sharedLex = modelRepository.getSharedLexiconFile().takeIf { it.exists() }
        val tokensTxt = File(modelRepository.getModelDir(TtsModelId.KOKORO_EN_V019), "tokens.txt")
            .takeIf { it.exists() }
            ?: File(modelRepository.getModelDir(TtsModelId.KITTEN_NANO_V08), "tokens.txt").takeIf { it.exists() }
        _phonemeReport.value = phonemeAnalyzer.analyzeText(text, sharedLex, tokensTxt)
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
        viewModelScope.launch { engineManager.unloadCurrentModel() }
    }
}
