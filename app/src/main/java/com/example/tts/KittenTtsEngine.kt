package com.example.tts

import com.example.data.ModelManifest
import com.example.data.SpeakerProfile
import com.example.data.TtsModelSpec
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class KittenTtsEngine : TtsEngine {
    override val modelSpec: TtsModelSpec = ModelManifest.kittenNano
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

        val modelFile = findOnnx(modelDir)
            ?: throw IllegalStateException("Missing ONNX model file in ${modelDir.absolutePath}")
        val voicesFile = File(modelDir, "voices.bin")
        val tokensFile = File(modelDir, "tokens.txt")
        val espeakDir = File(modelDir, "espeak-ng-data")
        val lexiconFile = File(modelDir, "lexicon.txt").takeIf { it.exists() && it.length() > 0L }
            ?: File(modelDir, "lexicon-en-us.txt").takeIf { it.exists() && it.length() > 0L }
            ?: sharedLexiconFile?.takeIf { it.exists() && it.length() > 0L }

        require(voicesFile.exists()) { "Missing voices.bin in ${modelDir.absolutePath}" }
        require(tokensFile.exists()) { "Missing tokens.txt in ${modelDir.absolutePath}" }

        val startNs = System.nanoTime()
        val kittenConfig = OfflineTtsKittenModelConfig(
            model = modelFile.absolutePath,
            voices = voicesFile.absolutePath,
            tokens = tokensFile.absolutePath,
            dataDir = if (espeakDir.exists() && espeakDir.isDirectory) espeakDir.absolutePath else "",
            lengthScale = 1.0f,
        )
        val modelConfig = OfflineTtsModelConfig(
            kitten = kittenConfig,
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
                ?: SpeakerProfile(idx, "Speaker $idx", "Kitten Voice #$idx")
        }

        val backendNote = buildString {
            append("OfflineTtsKittenModelConfig • ${modelFile.name} • ${numThreads}T CPU")
            if (espeakDir.exists()) append(" • espeak-ng-data")
            if (lexiconFile != null) append(" • ${lexiconFile.name}")
        }

        TtsInitMetrics(
            initDurationMs = elapsedMs,
            sampleRate = sampleRate,
            numSpeakers = count,
            backendInfo = backendNote,
        )
    }

    override suspend fun synthesize(
        text: String,
        speakerId: Int,
        speed: Float,
        extraParams: Map<String, String>,
    ): TtsSynthesisOutput = withContext(Dispatchers.IO) {
        val engine = tts ?: throw IllegalStateException("Kitten Nano engine is not initialized.")
        cancelled = false
        val safeText = text.trim()
        require(safeText.isNotEmpty()) { "Input text cannot be empty." }

        val startNs = System.nanoTime()
        val audio = engine.generate(
            text = safeText,
            sid = speakerId.coerceIn(0, (speakers.size - 1).coerceAtLeast(0)),
            speed = speed.coerceIn(0.5f, 2.0f),
        )
        val synthMs = (System.nanoTime() - startNs) / 1_000_000L

        if (cancelled) {
            throw InterruptedException("Synthesis stopped by user.")
        }

        val sr = audio.sampleRate.takeIf { it > 0 } ?: sampleRate
        val durationMs = ((audio.samples.size.toDouble() / sr.toDouble()) * 1000.0).toLong()
        val speakerName = speakers.firstOrNull { it.id == speakerId }?.name ?: "Speaker $speakerId"

        TtsSynthesisOutput(
            samples = audio.samples,
            sampleRate = sr,
            synthesisTimeMs = synthMs,
            timeToFirstAudioMs = synthMs, // Batch synthesis: first completed audio == total synthesis time
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

    private fun findOnnx(dir: File): File? {
        val preferred = listOf("model.int8.onnx", "model.onnx", "kitten_tts_nano_v0_8.onnx")
        for (name in preferred) {
            val f = File(dir, name)
            if (f.exists() && f.length() > 0L) return f
        }
        return dir.listFiles()?.firstOrNull { it.isFile && it.extension.equals("onnx", ignoreCase = true) }
    }
}
