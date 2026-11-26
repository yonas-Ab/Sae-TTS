package com.example.data

enum class TtsModelId(val storageFolderName: String) {
    KITTEN_NANO_V08("kitten-nano-en-v0_8-int8"),
    KOKORO_EN_V019("kokoro-int8-en-v0_19"),
    SUPERTONIC_3_INT8("supertonic-3-tts-int8"),
}

data class SpeakerProfile(
    val id: Int,
    val name: String,
    val subtitle: String,
)

data class TtsModelSpec(
    val id: TtsModelId,
    val identifier: String,
    val version: String,
    val displayName: String,
    val roleTag: String,
    val family: String,
    val description: String,
    val licenseName: String,
    val licenseDetails: String,
    val archiveUrl: String,
    val docsUrl: String,
    val approxDownloadMb: Int,
    val approxDiskMb: Int,
    val minExpectedArchiveBytes: Long,
    val nominalSampleRate: Int,
    val isStreamingSupported: Boolean,
    val supportsSpeedControl: Boolean,
    val supportsEmotionConditioning: Boolean,
    val expressivenessNote: String,
    val defaultSpeakers: List<SpeakerProfile>,
    val expectedArchiveTopDir: String,
    val requiredRelativeFiles: List<String>,
)

object ModelManifest {
    const val ENGLISH_LEXICON_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/lexicon-en-us.txt"
    const val ENGLISH_LEXICON_FILENAME = "lexicon-en-us.txt"

    val kittenNano = TtsModelSpec(
        id = TtsModelId.KITTEN_NANO_V08,
        identifier = "kitten-nano-en-v0_8-int8",
        version = "v0.8-int8",
        displayName = "Kitten Nano v0.8 INT8",
        roleTag = "Baseline • Ultra-Light",
        family = "KittenTTS (15M INT8)",
        description = "Ultra-lightweight English neural TTS quantized to INT8. Built as a fast low-RAM baseline for Unisoc T606 / 4 GB devices.",
        licenseName = "Apache-2.0",
        licenseDetails = "KittenML/kitten-tts-nano-0.8-int8 & sherpa-onnx wrapper under Apache 2.0.",
        archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_8-int8.tar.bz2",
        docsUrl = "https://k2-fsa.github.io/sherpa/onnx/tts/all/English/kitten-nano-en-v0_8-int8.html",
        approxDownloadMb = 24,
        approxDiskMb = 38,
        minExpectedArchiveBytes = 5_000_000L,
        nominalSampleRate = 24000,
        isStreamingSupported = false,
        supportsSpeedControl = true,
        supportsEmotionConditioning = false,
        expressivenessNote = "Multi-speaker prosody via speaker embeddings only. No categorical emotion conditioning API.",
        defaultSpeakers = listOf(
            SpeakerProfile(0, "Bella", "Female • Warm conversational"),
            SpeakerProfile(1, "Jasper", "Male • Clear neutral"),
            SpeakerProfile(2, "Luna", "Female • Bright & soft"),
            SpeakerProfile(3, "Bruno", "Male • Deep & steady"),
            SpeakerProfile(4, "Rosie", "Female • Energetic"),
            SpeakerProfile(5, "Hugo", "Male • Crisp narrator"),
            SpeakerProfile(6, "Kiki", "Female • Youthful"),
            SpeakerProfile(7, "Leo", "Male • Relaxed"),
        ),
        expectedArchiveTopDir = "kitten-nano-en-v0_8-int8",
        requiredRelativeFiles = listOf("voices.bin", "tokens.txt"),
    )

    val kokoroEn = TtsModelSpec(
        id = TtsModelId.KOKORO_EN_V019,
        identifier = "kokoro-int8-en-v0_19",
        version = "v0.19-int8",
        displayName = "Kokoro English v0.19 INT8",
        roleTag = "High Quality • 82M",
        family = "Kokoro-82M (StyleTTS 2 INT8)",
        description = "82M-parameter StyleTTS 2 English model quantized to INT8 with 11 American & British English speakers.",
        licenseName = "Apache-2.0",
        licenseDetails = "hexgrad/Kokoro-82M weights & sherpa-onnx distribution under Apache 2.0.",
        archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2",
        docsUrl = "https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html",
        approxDownloadMb = 103,
        approxDiskMb = 140,
        minExpectedArchiveBytes = 40_000_000L,
        nominalSampleRate = 24000,
        isStreamingSupported = false,
        supportsSpeedControl = true,
        supportsEmotionConditioning = false,
        expressivenessNote = "Natural StyleTTS 2 prosody from punctuation & speaker style vector; no categorical emotion label input.",
        defaultSpeakers = listOf(
            SpeakerProfile(0, "af (Heart/Default)", "American Female • Signature"),
            SpeakerProfile(1, "af_bella", "American Female • Expressive"),
            SpeakerProfile(2, "af_nicole", "American Female • Gentle"),
            SpeakerProfile(3, "af_sarah", "American Female • Clear"),
            SpeakerProfile(4, "af_sky", "American Female • Crisp"),
            SpeakerProfile(5, "am_adam", "American Male • Balanced"),
            SpeakerProfile(6, "am_michael", "American Male • Resonant"),
            SpeakerProfile(7, "bf_emma", "British Female • Polished"),
            SpeakerProfile(8, "bf_isabella", "British Female • Warm"),
            SpeakerProfile(9, "bm_george", "British Male • Classic"),
            SpeakerProfile(10, "bm_lewis", "British Male • Narrative"),
        ),
        expectedArchiveTopDir = "kokoro-int8-en-v0_19",
        requiredRelativeFiles = listOf("voices.bin", "tokens.txt"),
    )

    val supertonic3 = TtsModelSpec(
        id = TtsModelId.SUPERTONIC_3_INT8,
        identifier = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11",
        version = "v3-int8",
        displayName = "Supertonic-3 INT8",
        roleTag = "Expressive Character • 44.1 kHz",
        family = "Supertonic-3 (66M Flow-Matching)",
        description = "Reference-audio-independent expressive character TTS using flow-matching + ConvNeXt vocoder at 44.1 kHz with 10 character voices & inline cue tags (<laugh>, <breath>, <sigh>).",
        licenseName = "OpenRAIL-M (Weights) / MIT (Code)",
        licenseDetails = "Supertone Supertonic-3 weights under BigScience OpenRAIL-M (disclose synthetic speech publicly; no unauthorized impersonation).",
        archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2",
        docsUrl = "https://k2-fsa.github.io/sherpa/onnx/tts/index.html",
        approxDownloadMb = 129,
        approxDiskMb = 155,
        minExpectedArchiveBytes = 50_000_000L,
        nominalSampleRate = 44100,
        isStreamingSupported = false,
        supportsSpeedControl = true,
        supportsEmotionConditioning = false,
        expressivenessNote = "Uses 10 consistent character style vectors (F1–F5, M1–M5), flow-matching step control, and inline cue tags (<laugh>, <breath>, <sigh>) rather than a separate emotion-ID API.",
        defaultSpeakers = listOf(
            SpeakerProfile(0, "F1", "Female Character 1 • Bright"),
            SpeakerProfile(1, "F2", "Female Character 2 • Warm"),
            SpeakerProfile(2, "F3", "Female Character 3 • Expressive"),
            SpeakerProfile(3, "F4", "Female Character 4 • Calm"),
            SpeakerProfile(4, "F5", "Female Character 5 • Energetic"),
            SpeakerProfile(5, "M1", "Male Character 1 • Steady"),
            SpeakerProfile(6, "M2", "Male Character 2 • Conversational"),
            SpeakerProfile(7, "M3", "Male Character 3 • Deep"),
            SpeakerProfile(8, "M4", "Male Character 4 • Crisp"),
            SpeakerProfile(9, "M5", "Male Character 5 • Expressive"),
        ),
        expectedArchiveTopDir = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11",
        requiredRelativeFiles = listOf(
            "duration_predictor.int8.onnx",
            "text_encoder.int8.onnx",
            "vector_estimator.int8.onnx",
            "vocoder.int8.onnx",
            "tts.json",
            "unicode_indexer.bin",
            "voice.bin",
        ),
    )

    val allModels: List<TtsModelSpec> = listOf(kittenNano, kokoroEn, supertonic3)

    fun byId(id: TtsModelId): TtsModelSpec = allModels.first { it.id == id }
    fun byIdentifier(identifier: String): TtsModelSpec? = allModels.firstOrNull { it.identifier == identifier }
}
