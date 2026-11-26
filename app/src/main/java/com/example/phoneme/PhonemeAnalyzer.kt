package com.example.phoneme

import java.io.File

enum class VrmViseme(val blendshapeKey: String, val label: String) {
    AA("aa", "Open Jaw (aa)"),
    IH("ih", "Spread Front (ih)"),
    OU("ou", "Rounded Lip (ou)"),
    EE("ee", "Wide Smile (ee)"),
    OH("oh", "Rounded Mid (oh)"),
    SIL("neutral", "Closed / Rest"),
}

data class WordPhonemeToken(
    val word: String,
    val ipaPreview: String,
    val source: String,
    val vrmSequence: List<VrmViseme>,
)

data class PhonemeInvestigationReport(
    val supportsRealModelTimestamps: Boolean = false,
    val capabilitySummary: String,
    val lipSyncRecommendation: String,
    val lexiconEntriesLoaded: Int,
    val tokensTxtCount: Int,
    val wordTokens: List<WordPhonemeToken>,
)

/**
 * Decoupled Phoneme & VRM Lip-Sync Investigation module.
 *
 * Truthfulness contract:
 * - sherpa-onnx OfflineTts exposes ONLY waveform samples (`FloatArray`) and sample rate (`Int`).
 * - It does NOT expose phoneme-level or frame-level timestamps from Kitten Nano, Kokoro, or Supertonic-3.
 * - Therefore this analyzer NEVER fabricates millisecond phoneme timestamps.
 * - Instead, it inspects installed `lexicon-en-us.txt` / `tokens.txt` files and previews
 *   lexicon/rule-based IPA-to-VRM viseme sequences (`aa`, `ih`, `ou`, `ee`, `oh`).
 */
class PhonemeAnalyzer {

    private var cachedLexicon: Map<String, String> = emptyMap()
    private var cachedLexiconPath: String? = null

    fun analyzeText(
        text: String,
        sharedLexiconFile: File?,
        tokensFile: File?,
    ): PhonemeInvestigationReport {
        val lexicon = loadLexiconIfAvailable(sharedLexiconFile)
        val tokensCount = if (tokensFile != null && tokensFile.exists()) {
            runCatching { tokensFile.readLines().count { it.isNotBlank() } }.getOrDefault(0)
        } else {
            0
        }

        val words = text
            .replace(Regex("<[^>]+>"), " ") // strip inline tags like <laugh>
            .split(Regex("\\s+"))
            .map { it.trim { ch -> !ch.isLetterOrDigit() && ch != '\'' } }
            .filter { it.isNotEmpty() }

        val analyzed = words.map { word ->
            val lower = word.lowercase()
            val lexEntry = lexicon[lower]
            val ipa = lexEntry ?: heuristicEnglishToIpa(lower)
            val source = if (lexEntry != null) "lexicon-en-us.txt" else "Heuristic G2P"
            WordPhonemeToken(
                word = word,
                ipaPreview = ipa,
                source = source,
                vrmSequence = mapIpaToVrmVisemes(ipa),
            )
        }

        return PhonemeInvestigationReport(
            supportsRealModelTimestamps = false,
            capabilitySummary = "Not exposed by sherpa-onnx OfflineTts Kotlin/C++ API (returns GeneratedAudio(samples, sampleRate) only — no phoneme durations or alignments).",
            lipSyncRecommendation = "For Sae VRM avatars on Unisoc T606: drive primary mouth openness with real-time RMS audio amplitude envelope (~30–60 Hz) from AudioTrack playback, and optionally blend with sherpa-onnx forced-alignment / pocket-VAD viseme cues in a future phase.",
            lexiconEntriesLoaded = lexicon.size,
            tokensTxtCount = tokensCount,
            wordTokens = analyzed,
        )
    }

    fun mapIpaToVrmVisemes(ipa: String): List<VrmViseme> {
        val visemes = mutableListOf<VrmViseme>()
        var i = 0
        while (i < ipa.length) {
            val ch = ipa[i]
            val viseme = when (ch) {
                'ɑ', 'a', 'æ', 'ʌ', 'ə', 'ɐ' -> VrmViseme.AA
                'i', 'ɪ', 'j' -> VrmViseme.IH
                'u', 'ʊ', 'w' -> VrmViseme.OU
                'e', 'ɛ', 'ɜ' -> VrmViseme.EE
                'o', 'ɔ', 'ɒ' -> VrmViseme.OH
                'p', 'b', 'm' -> VrmViseme.SIL
                else -> null
            }
            if (viseme != null && visemes.lastOrNull() != viseme) {
                visemes += viseme
            }
            i++
        }
        return if (visemes.isEmpty()) listOf(VrmViseme.AA) else visemes
    }

    private fun loadLexiconIfAvailable(file: File?): Map<String, String> {
        if (file == null || !file.exists() || !file.isFile) return emptyMap()
        if (cachedLexiconPath == file.absolutePath && cachedLexicon.isNotEmpty()) {
            return cachedLexicon
        }
        val map = HashMap<String, String>(16384)
        runCatching {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val spaceIdx = trimmed.indexOf(' ')
                        if (spaceIdx > 0) {
                            val word = trimmed.substring(0, spaceIdx).lowercase()
                            val phonemes = trimmed.substring(spaceIdx + 1).trim()
                            map[word] = phonemes
                        }
                    }
                }
            }
        }
        cachedLexicon = map
        cachedLexiconPath = file.absolutePath
        return map
    }

    private fun heuristicEnglishToIpa(word: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < word.length) {
            val rest = word.substring(i)
            when {
                rest.startsWith("oo") -> { sb.append("u"); i += 2 }
                rest.startsWith("ee") || rest.startsWith("ea") -> { sb.append("i"); i += 2 }
                rest.startsWith("ou") || rest.startsWith("ow") -> { sb.append("aʊ"); i += 2 }
                rest.startsWith("oa") -> { sb.append("oʊ"); i += 2 }
                rest.startsWith("th") -> { sb.append("θ"); i += 2 }
                rest.startsWith("sh") -> { sb.append("ʃ"); i += 2 }
                rest.startsWith("ch") -> { sb.append("tʃ"); i += 2 }
                rest.startsWith("ng") -> { sb.append("ŋ"); i += 2 }
                else -> {
                    val mapped = when (word[i]) {
                        'a' -> "æ"
                        'e' -> "ɛ"
                        'i' -> "ɪ"
                        'o' -> "ɒ"
                        'u' -> "ʌ"
                        'y' -> "j"
                        else -> word[i].toString()
                    }
                    sb.append(mapped)
                    i++
                }
            }
        }
        return sb.toString()
    }
}
