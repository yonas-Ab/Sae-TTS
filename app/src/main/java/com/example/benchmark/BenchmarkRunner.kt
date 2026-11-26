package com.example.benchmark

import com.example.audio.AudioFileManager
import com.example.data.BenchmarkEntity
import com.example.data.BenchmarkRepository
import com.example.data.ModelManifest
import com.example.data.ModelRepository
import com.example.data.TtsModelId
import com.example.tts.TtsEngineManager
import kotlinx.coroutines.CancellationException
import java.util.UUID

class BenchmarkRunner(
    private val modelRepository: ModelRepository,
    private val engineManager: TtsEngineManager,
    private val audioFileManager: AudioFileManager,
    private val benchmarkRepository: BenchmarkRepository,
    private val performanceMonitor: PerformanceMonitor,
) {

    suspend fun runSingleSpeechTest(
        modelId: TtsModelId,
        speakerId: Int,
        text: String,
        speed: Float,
        threadCount: Int,
        supertonicSteps: Int,
        presetName: String,
    ): Result<MeasuredSynthesisResult> {
        return runAndPersist(
            batchId = "",
            runType = "SPEECH_TEST",
            presetName = presetName,
            modelId = modelId,
            speakerId = speakerId,
            text = text,
            speed = speed,
            threadCount = threadCount,
            supertonicSteps = supertonicSteps,
            releaseAfter = false,
        )
    }

    suspend fun runSequentialComparison(
        modelsAndSpeakers: List<Pair<TtsModelId, Int>>,
        text: String,
        speed: Float,
        threadCount: Int,
        supertonicSteps: Int,
        presetName: String,
        onProgress: (ComparisonRunState.Running) -> Unit,
    ): ComparisonRunState {
        val batchId = UUID.randomUUID().toString()
        val results = mutableListOf<MeasuredSynthesisResult>()
        val skipped = linkedMapOf<TtsModelId, String>()

        val installedQueue = mutableListOf<Pair<TtsModelId, Int>>()
        for ((modelId, speakerId) in modelsAndSpeakers) {
            val spec = ModelManifest.byId(modelId)
            val status = modelRepository.inspectModel(spec)
            if (status.isInstalled) {
                installedQueue += modelId to speakerId
            } else {
                skipped[modelId] = "Not installed"
            }
        }

        if (installedQueue.isEmpty()) {
            return ComparisonRunState.Failed("No selected models are installed. Download at least one model on the Models tab first.")
        }

        // Release any active engine before starting sequential comparison so every model begins cleanly
        engineManager.unloadCurrentModel()

        val total = installedQueue.size
        for ((index, pair) in installedQueue.withIndex()) {
            val (modelId, speakerId) = pair
            val spec = ModelManifest.byId(modelId)
            onProgress(
                ComparisonRunState.Running(
                    currentModelId = modelId,
                    stepIndex = index + 1,
                    totalSteps = total,
                    phaseLabel = "Loading & synthesizing ${spec.displayName} (${index + 1}/$total)...",
                ),
            )

            val res = try {
                runAndPersist(
                    batchId = batchId,
                    runType = "COMPARISON",
                    presetName = presetName,
                    modelId = modelId,
                    speakerId = speakerId,
                    text = text,
                    speed = speed,
                    threadCount = threadCount,
                    supertonicSteps = supertonicSteps,
                    releaseAfter = true,
                )
            } catch (ce: CancellationException) {
                engineManager.unloadCurrentModel()
                throw ce
            }

            res.onSuccess { measured ->
                results += measured
            }.onFailure { err ->
                skipped[modelId] = err.message ?: "Synthesis failed"
            }
        }

        return if (results.isEmpty()) {
            ComparisonRunState.Failed(
                skipped.entries.joinToString("\n") { "${ModelManifest.byId(it.key).displayName}: ${it.value}" },
            )
        } else {
            ComparisonRunState.Completed(
                batchId = batchId,
                results = results,
                skippedMessages = skipped,
            )
        }
    }

    private suspend fun runAndPersist(
        batchId: String,
        runType: String,
        presetName: String,
        modelId: TtsModelId,
        speakerId: Int,
        text: String,
        speed: Float,
        threadCount: Int,
        supertonicSteps: Int,
        releaseAfter: Boolean,
    ): Result<MeasuredSynthesisResult> {
        val spec = ModelManifest.byId(modelId)
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter text to synthesize."))
        }

        val rssBefore = performanceMonitor.readCurrentProcessRssMb()
        val extraParams = if (modelId == TtsModelId.SUPERTONIC_3_INT8) {
            mapOf("num_steps" to supertonicSteps.toString(), "lang" to "en")
        } else {
            emptyMap()
        }

        val synthResult = engineManager.synthesize(
            modelId = modelId,
            text = trimmed,
            speakerId = speakerId,
            speed = speed,
            numThreads = threadCount,
            extraParams = extraParams,
        )

        val result = synthResult.fold(
            onSuccess = { (loadedInfo, output) ->
                val rssAfter = performanceMonitor.readCurrentProcessRssMb()
                val rssDelta = if (rssBefore != null && rssAfter != null) rssAfter - rssBefore else null

                val wavName = "${modelId.storageFolderName}_${System.currentTimeMillis()}"
                val wavFile = audioFileManager.savePcm16Wav(
                    samples = output.samples,
                    sampleRate = output.sampleRate,
                    fileName = wavName,
                )

                val wordCount = BenchmarkPresets.countWords(trimmed)
                val entity = BenchmarkEntity(
                    batchId = batchId,
                    runType = runType,
                    presetName = presetName,
                    modelId = modelId.name,
                    modelDisplayName = spec.displayName,
                    speakerId = output.speakerId,
                    speakerName = output.speakerName,
                    text = trimmed,
                    wordCount = wordCount,
                    charCount = trimmed.length,
                    threadCount = threadCount,
                    speed = speed,
                    supertonicSteps = if (modelId == TtsModelId.SUPERTONIC_3_INT8) supertonicSteps else null,
                    isColdInit = loadedInfo.isColdInit,
                    initTimeMs = loadedInfo.initTimeMs,
                    timeToFirstAudioMs = output.timeToFirstAudioMs,
                    synthesisTimeMs = output.synthesisTimeMs,
                    audioDurationMs = output.audioDurationMs,
                    realTimeFactor = output.realTimeFactor,
                    sampleRate = output.sampleRate,
                    rssBeforeMb = rssBefore,
                    rssAfterMb = rssAfter,
                    rssDeltaMb = rssDelta,
                    wavFilePath = wavFile.absolutePath,
                    createdAtMs = System.currentTimeMillis(),
                )
                val rowId = benchmarkRepository.insertBenchmark(entity)

                Result.success(
                    MeasuredSynthesisResult(
                        dbRowId = rowId,
                        batchId = batchId,
                        modelId = modelId,
                        modelDisplayName = spec.displayName,
                        speakerId = output.speakerId,
                        speakerName = output.speakerName,
                        text = trimmed,
                        wordCount = wordCount,
                        charCount = trimmed.length,
                        isColdInit = loadedInfo.isColdInit,
                        initTimeMs = loadedInfo.initTimeMs,
                        timeToFirstAudioMs = output.timeToFirstAudioMs,
                        synthesisTimeMs = output.synthesisTimeMs,
                        audioDurationMs = output.audioDurationMs,
                        realTimeFactor = output.realTimeFactor,
                        sampleRate = output.sampleRate,
                        rssBeforeMb = rssBefore,
                        rssAfterMb = rssAfter,
                        rssDeltaMb = rssDelta,
                        wavFilePath = wavFile.absolutePath,
                        samples = output.samples,
                        backendInfo = loadedInfo.backendInfo,
                    ),
                )
            },
            onFailure = { err ->
                Result.failure(err)
            },
        )

        if (releaseAfter) {
            engineManager.unloadCurrentModel()
        }
        return result
    }
}
