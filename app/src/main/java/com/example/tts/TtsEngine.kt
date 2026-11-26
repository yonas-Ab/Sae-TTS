package com.example.tts

import com.example.data.SpeakerProfile
import com.example.data.TtsModelSpec
import java.io.File

data class TtsInitMetrics(
    val initDurationMs: Long,
    val sampleRate: Int,
    val numSpeakers: Int,
    val backendInfo: String,
)

data class TtsSynthesisOutput(
    val samples: FloatArray,
    val sampleRate: Int,
    val synthesisTimeMs: Long,
    val timeToFirstAudioMs: Long,
    val audioDurationMs: Long,
    val speakerId: Int,
    val speakerName: String,
    val isStreamingOutput: Boolean = false,
) {
    val realTimeFactor: Double
        get() = if (audioDurationMs > 0L) synthesisTimeMs.toDouble() / audioDurationMs.toDouble() else 0.0
}

interface TtsEngine {
    val modelSpec: TtsModelSpec
    val isStreaming: Boolean

    suspend fun initialize(modelDir: File, sharedLexiconFile: File?, numThreads: Int): TtsInitMetrics
    suspend fun synthesize(
        text: String,
        speakerId: Int,
        speed: Float,
        extraParams: Map<String, String> = emptyMap(),
    ): TtsSynthesisOutput

    fun stop()
    fun release()
    fun getSampleRate(): Int
    fun getAvailableSpeakers(): List<SpeakerProfile>
    fun isInitialized(): Boolean
}
