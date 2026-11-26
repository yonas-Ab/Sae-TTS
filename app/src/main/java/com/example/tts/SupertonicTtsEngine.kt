package com.example.tts

import com.example.data.ModelManifest
import com.example.data.SpeakerProfile
import com.example.data.TtsModelSpec
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SupertonicTtsEngine : TtsEngine {
    override val modelSpec: TtsModelSpec = ModelManifest.supertonic3
    override val isStreaming: Boolean = false

    @Volatile
    private var tts: OfflineTts? = null

    @Volatile
    private var sampleRate: Int = modelSpec.nominalSampleRate

    @Volatile
    private var speakers: List<SpeakerProfile> = modelSpec.defaultSpeakers

    @Volatile
    private var cancelled: Boolean = false

    override suspend fun initialize(
        modelDir: File,
        sharedLexiconFile: File?,
        numThreads: Int,
    ): TtsInitMetrics = withContext(Dispatchers.IO) {
        release()
        cancelled = false

        val durationPredictor = resolveComponent(modelDir, "duration_predictor.int8.onnx", "duration_predictor.onnx")
        val textEncoder = resolveComponent(modelDir, "text_encoder.int8.onnx", "text_encoder.onnx")
        val vectorEstimator = resolveComponent(modelDir, "vector_estimator.int8.onnx", "vector_estimator.onnx")
        val vocoder = resolveComponent(modelDir, "vocoder.int8.onnx", "vocoder.onnx")
        val ttsJson = File(modelDir, "tts.json").also {
            require(it.exists()) { "Missing tts.json in ${modelDir.absolutePath}" }
        }
        val unicodeIndexer = File(modelDir, "unicode_indexer.bin").also {
            require(it.exists()) { "Missing unicode_indexer.bin in ${modelDir.absolutePath}" }
        }
        val voiceStyle = File(modelDir, "voice.bin").also {
            require(it.exists()) { "Missing voice.bin in ${modelDir.absolutePath}" }
        }

        val startNs = System.nanoTime()
        val supertonicConfig = OfflineTtsSupertonicModelConfig(
            durationPredictor = durationPredictor.absolutePath,
            textEncoder = textEncoder.absolutePath,
            vectorEstimator = vectorEstimator.absolutePath,
            vocoder = vocoder.absolutePath,
            ttsJson = ttsJson.absolutePath,
            unicodeIndexer = unicodeIndexer.absolutePath,
            voiceStyle = voiceStyle.absolutePath,
        )
        val modelConfig = OfflineTtsModelConfig(
            supertonic = supertonicConfig,
            numThreads = numThreads.coerceIn(1, 4),
            debug = false,
            provider = "cpu",
        )
        val ttsConfig = OfflineTtsConfig(
            model = modelConfig,
            maxNumSentences = 1,
        )

        val instance = OfflineTts(config = ttsConfig)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L

        tts = instance
        sampleRate = instance.sampleRate().takeIf { it > 0 } ?: modelSpec.nominalSampleRate
        val count = instance.numSpeakers().coerceAtLeast(1)
        speakers = (0 until count).map { idx ->
            modelSpec.defaultSpeakers.getOrNull(idx)
                ?: SpeakerProfile(idx, "Style #$idx", "Supertonic Character #$idx")
        }

        TtsInitMetrics(
            initDurationMs = elapsedMs,
            sampleRate = sampleRate,
            numSpeakers = count,
            backendInfo = "OfflineTtsSupertonicModelConfig • ${durationPredictor.name} • ${numThreads}T CPU",
        )
    }

    override suspend fun synthesize(
        text: String,
        speakerId: Int,
        speed: Float,
        extraParams: Map<String, String>,
    ): TtsSynthesisOutput = withContext(Dispatchers.IO) {
        val engine = tts ?: throw IllegalStateException("Supertonic-3 engine is not initialized.")
        cancelled = false
        val safeText = text.trim()
        require(safeText.isNotEmpty()) { "Input text cannot be empty." }

        val numSteps = extraParams["numSteps"]?.toIntOrNull()?.coerceIn(2, 12) ?: 5
        val lang = extraParams["lang"]?.takeIf { it.isNotBlank() } ?: "en"

        val genConfig = GenerationConfig(
            sid = speakerId.coerceIn(0, (speakers.size - 1).coerceAtLeast(0)),
            speed = speed.coerceIn(0.5f, 2.0f),
            numSteps = numSteps,
            extra = mapOf("lang" to lang),
        )

        val startNs = System.nanoTime()
        val audio = engine.generateWithConfig(text = safeText, config = genConfig)
        val synthMs = (System.nanoTime() - startNs) / 1_000_000L

        if (cancelled) {
            throw InterruptedException("Synthesis stopped by user.")
        }

        val sr = audio.sampleRate.takeIf { it > 0 } ?: sampleRate
        val durationMs = ((audio.samples.size.toDouble() / sr.toDouble()) * 1000.0).toLong()
        val speakerName = speakers.firstOrNull { it.id == speakerId }?.name ?: "Style #$speakerId"

        TtsSynthesisOutput(
            samples = audio.samples,
            sampleRate = sr,
            synthesisTimeMs = synthMs,
            timeToFirstAudioMs = synthMs,
            audioDurationMs = durationMs,
            speakerId = speakerId,
            speakerName = speakerName,
            isStreamingOutput = false,
        )
    }

    override fun stop() {
        cancelled = true
    }

    override fun release() {
        cancelled = true
        try {
            tts?.release()
        } catch (_: Throwable) {
        } finally {
            tts = null
        }
    }

    override fun getSampleRate(): Int = sampleRate
    override fun getAvailableSpeakers(): List<SpeakerProfile> = speakers
    override fun isInitialized(): Boolean = tts != null

    private fun resolveComponent(dir: File, preferred: String, fallback: String): File {
        return File(dir, preferred).takeIf { it.exists() && it.length() > 0L }
            ?: File(dir, fallback).takeIf { it.exists() && it.length() > 0L }
            ?: throw IllegalStateException("Missing $preferred in ${dir.absolutePath}")
    }
}
