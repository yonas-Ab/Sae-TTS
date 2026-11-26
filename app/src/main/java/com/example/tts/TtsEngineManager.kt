package com.example.tts

import com.example.data.ModelManifest
import com.example.data.ModelRepository
import com.example.data.SpeakerProfile
import com.example.data.TtsModelId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class EngineState {
    data object Unloaded : EngineState()
    data class Loading(val modelId: TtsModelId) : EngineState()
    data class Loaded(
        val modelId: TtsModelId,
        val initTimeMs: Long,
        val isColdInit: Boolean,
        val sampleRate: Int,
        val speakers: List<SpeakerProfile>,
        val backendInfo: String,
    ) : EngineState()

    data class Synthesizing(
        val modelId: TtsModelId,
        val speakerName: String,
    ) : EngineState()

    data class Error(
        val modelId: TtsModelId?,
        val message: String,
    ) : EngineState()
}

class TtsEngineManager(
    private val modelRepository: ModelRepository,
) {
    private val engineMutex = Mutex()
    private val initializedOnceInProcess = mutableSetOf<TtsModelId>()

    @Volatile
    private var activeEngine: TtsEngine? = null

    @Volatile
    private var activeLoadedInfo: EngineState.Loaded? = null

    private val _state = MutableStateFlow<EngineState>(EngineState.Unloaded)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    fun getCurrentLoadedModelId(): TtsModelId? = activeLoadedInfo?.modelId

    suspend fun ensureModelLoaded(
        modelId: TtsModelId,
        numThreads: Int,
        forceReload: Boolean = false,
    ): Result<EngineState.Loaded> = engineMutex.withLock {
        val current = activeEngine
        val existingInfo = activeLoadedInfo
        if (!forceReload && current != null && current.isInitialized() && existingInfo?.modelId == modelId) {
            _state.value = existingInfo
            return@withLock Result.success(existingInfo)
        }

        // Strict single-model residency: release any currently loaded engine first.
        releaseInternal()

        val spec = ModelManifest.byId(modelId)
        val status = modelRepository.inspectModel(spec)
        if (!status.isInstalled) {
            val err = "Model '${spec.displayName}' is not installed. Missing: ${status.missingFiles.joinToString()}"
            _state.value = EngineState.Error(modelId, err)
            return@withLock Result.failure(IllegalStateException(err))
        }

        _state.value = EngineState.Loading(modelId)
        return@withLock try {
            val engine = TtsEngineFactory.create(modelId)
            val sharedLexicon = modelRepository.getSharedLexiconFile().takeIf { it.exists() && it.length() > 0L }
            val metrics = engine.initialize(
                modelDir = status.modelDirectory,
                sharedLexiconFile = sharedLexicon,
                numThreads = numThreads,
            )
            val isCold = initializedOnceInProcess.add(modelId)
            val loadedState = EngineState.Loaded(
                modelId = modelId,
                initTimeMs = metrics.initDurationMs,
                isColdInit = isCold,
                sampleRate = metrics.sampleRate,
                speakers = engine.getAvailableSpeakers(),
                backendInfo = metrics.backendInfo,
            )
            activeEngine = engine
            activeLoadedInfo = loadedState
            _state.value = loadedState
            Result.success(loadedState)
        } catch (ce: CancellationException) {
            releaseInternal()
            _state.value = EngineState.Unloaded
            throw ce
        } catch (t: Throwable) {
            releaseInternal()
            val msg = t.message ?: "Failed to initialize ${spec.displayName}"
            _state.value = EngineState.Error(modelId, msg)
            Result.failure(t)
        }
    }

    suspend fun synthesize(
        modelId: TtsModelId,
        text: String,
        speakerId: Int,
        speed: Float,
        numThreads: Int,
        extraParams: Map<String, String> = emptyMap(),
    ): Result<Pair<EngineState.Loaded, TtsSynthesisOutput>> = engineMutex.withLock {
        val current = activeEngine
        val currentInfo = activeLoadedInfo
        val loadedInfo = if (current == null || !current.isInitialized() || currentInfo?.modelId != modelId) {
            releaseInternal()
            val spec = ModelManifest.byId(modelId)
            val status = modelRepository.inspectModel(spec)
            if (!status.isInstalled) {
                val err = "Model '${spec.displayName}' is not installed."
                _state.value = EngineState.Error(modelId, err)
                return@withLock Result.failure(IllegalStateException(err))
            }
            _state.value = EngineState.Loading(modelId)
            try {
                val engine = TtsEngineFactory.create(modelId)
                val sharedLexicon = modelRepository.getSharedLexiconFile().takeIf { it.exists() && it.length() > 0L }
                val metrics = engine.initialize(status.modelDirectory, sharedLexicon, numThreads)
                val isCold = initializedOnceInProcess.add(modelId)
                val info = EngineState.Loaded(
                    modelId = modelId,
                    initTimeMs = metrics.initDurationMs,
                    isColdInit = isCold,
                    sampleRate = metrics.sampleRate,
                    speakers = engine.getAvailableSpeakers(),
                    backendInfo = metrics.backendInfo,
                )
                activeEngine = engine
                activeLoadedInfo = info
                info
            } catch (ce: CancellationException) {
                releaseInternal()
                _state.value = EngineState.Unloaded
                throw ce
            } catch (t: Throwable) {
                releaseInternal()
                _state.value = EngineState.Error(modelId, t.message ?: "Initialization failed")
                return@withLock Result.failure(t)
            }
        } else {
            currentInfo
        }

        val engine = activeEngine ?: return@withLock Result.failure(IllegalStateException("Engine unavailable"))
        val speakerName = loadedInfo.speakers.firstOrNull { it.id == speakerId }?.name ?: "Speaker $speakerId"
        _state.value = EngineState.Synthesizing(modelId, speakerName)

        return@withLock try {
            val output = engine.synthesize(
                text = text,
                speakerId = speakerId,
                speed = speed,
                extraParams = extraParams,
            )
            _state.value = loadedInfo
            Result.success(loadedInfo to output)
        } catch (ce: CancellationException) {
            _state.value = loadedInfo
            throw ce
        } catch (t: Throwable) {
            _state.value = loadedInfo
            Result.failure(t)
        }
    }

    fun stopCurrentSynthesis() {
        activeEngine?.stop()
    }

    suspend fun unloadCurrentModel() = engineMutex.withLock {
        releaseInternal()
        _state.value = EngineState.Unloaded
    }

    suspend fun releaseForDeletion(modelId: TtsModelId) = engineMutex.withLock {
        if (activeLoadedInfo?.modelId == modelId) {
            releaseInternal()
            _state.value = EngineState.Unloaded
        }
    }

    private suspend fun releaseInternal() = withContext(Dispatchers.IO) {
        try {
            activeEngine?.release()
        } catch (_: Throwable) {
        } finally {
            activeEngine = null
            activeLoadedInfo = null
        }
    }
}
