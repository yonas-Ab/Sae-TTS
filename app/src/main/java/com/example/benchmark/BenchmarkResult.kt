package com.example.benchmark

import com.example.data.TtsModelId

data class TestSentencePreset(
    val id: String,
    val label: String,
    val toneNote: String,
    val text: String,
)

data class BenchmarkLengthPreset(
    val id: String,
    val label: String,
    val wordTarget: String,
    val text: String,
)

data class MeasuredSynthesisResult(
    val dbRowId: Long,
    val batchId: String,
    val modelId: TtsModelId,
    val modelDisplayName: String,
    val speakerId: Int,
    val speakerName: String,
    val text: String,
    val wordCount: Int,
    val charCount: Int,
    val isColdInit: Boolean,
    val initTimeMs: Long,
    val timeToFirstAudioMs: Long,
    val synthesisTimeMs: Long,
    val audioDurationMs: Long,
    val realTimeFactor: Double,
    val sampleRate: Int,
    val rssBeforeMb: Double?,
    val rssAfterMb: Double?,
    val rssDeltaMb: Double?,
    val wavFilePath: String,
    val samples: FloatArray,
    val backendInfo: String,
)

sealed class ComparisonRunState {
    data object Idle : ComparisonRunState()
    data class Running(
        val currentModelId: TtsModelId,
        val stepIndex: Int,
        val totalSteps: Int,
        val phaseLabel: String,
    ) : ComparisonRunState()

    data class Completed(
        val batchId: String,
        val results: List<MeasuredSynthesisResult>,
        val skippedMessages: Map<TtsModelId, String>,
    ) : ComparisonRunState()

    data class Failed(val message: String) : ComparisonRunState()
}

object BenchmarkPresets {

    val speechTestSentences: List<TestSentencePreset> = listOf(
        TestSentencePreset(
            id = "normal",
            label = "Normal",
            toneNote = "Baseline conversational cadence",
            text = "Good morning. All offline neural speech systems are initialized and ready for local testing.",
        ),
        TestSentencePreset(
            id = "question",
            label = "Question",
            toneNote = "Interrogative rising inflection",
            text = "Wait, did you really check how much free memory is left before loading the next voice model?",
        ),
        TestSentencePreset(
            id = "sarcastic",
            label = "Sarcastic",
            toneNote = "Punctuation & pacing contrast (no emotion API)",
            text = "Oh, fantastic. Another benchmark run on four gigabytes of RAM. What could possibly go wrong?",
        ),
        TestSentencePreset(
            id = "excited",
            label = "Excited",
            toneNote = "High punctuation energy + optional Supertonic cue",
            text = "Incredible! The audio rendered offline in less than a second, with zero cloud calls!",
        ),
        TestSentencePreset(
            id = "calm",
            label = "Calm",
            toneNote = "Gentle paced companion voice",
            text = "Take a slow breath. Everything is saved safely on your device, and there is no rush at all.",
        ),
        TestSentencePreset(
            id = "paragraph",
            label = "Paragraph",
            toneNote = "Multi-sentence breath & prosody stability",
            text = "When building a personal voice companion for entry-level smartphones, latency tells only half the story. Listeners notice harsh metallic artifacts, clipped endings, and robotic pacing long before they look at a real-time factor chart. Testing complete paragraphs reveals how each model handles pauses between clauses.",
        ),
    )

    val comparisonPresets: List<BenchmarkLengthPreset> = listOf(
        BenchmarkLengthPreset(
            id = "short",
            label = "Short",
            wordTarget = "8 words",
            text = "Hello Sae, let us test offline speech speed.",
        ),
        BenchmarkLengthPreset(
            id = "medium",
            label = "Medium",
            wordTarget = "38 words",
            text = "On a compact Android Go device with four gigabytes of RAM, running neural speech synthesis locally means keeping thread counts balanced, releasing unused weights promptly, and listening closely for clear pronunciation across everyday conversational sentences.",
        ),
        BenchmarkLengthPreset(
            id = "long",
            label = "Long",
            wordTarget = "116 words",
            text = "Designing an offline AI companion requires careful engineering across every millisecond of the voice pipeline. First, the runtime must initialize acoustic weights cleanly from app-specific storage without exhausting system memory. Second, phonemization needs to handle abbreviations, numbers, and punctuation gracefully so phrases sound natural rather than stitched together. Third, listeners judge character identity and warmth across longer monologues, where weak vocoders often reveal buzzing artifacts or unnatural breathing rhythms. By running identical passages through Kitten Nano, Kokoro, and Supertonic sequentially on the exact same hardware, we can compare real-time factor, memory footprint, and subjective vocal personality side by side before locking in a production voice architecture.",
        ),
    )

    fun countWords(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split(Regex("\\s+")).size
    }
}
