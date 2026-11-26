package com.example

import com.example.benchmark.BenchmarkPresets
import com.example.data.ModelManifest
import com.example.phoneme.PhonemeAnalyzer
import com.example.phoneme.VrmViseme
import com.example.tts.TtsSynthesisOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun realTimeFactorCalculationIsAccurate() {
        val output = TtsSynthesisOutput(
            samples = FloatArray(24000),
            sampleRate = 24000,
            synthesisTimeMs = 500L,
            timeToFirstAudioMs = 500L,
            audioDurationMs = 1000L,
            speakerId = 0,
            speakerName = "Bella",
        )
        assertEquals(0.5, output.realTimeFactor, 0.0001)
    }

    @Test
    fun allManifestModelsHaveValidHttpsArchiveUrlsAndSpeakers() {
        assertEquals(3, ModelManifest.allModels.size)
        ModelManifest.allModels.forEach { spec ->
            assertTrue(spec.archiveUrl.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/"))
            assertTrue(spec.defaultSpeakers.isNotEmpty())
            assertFalse(spec.isStreamingSupported)
            assertFalse(spec.supportsEmotionConditioning)
        }
    }

    @Test
    fun phonemeAnalyzerReportsNoModelTimestampsAndMapsVrmVisemes() {
        val analyzer = PhonemeAnalyzer()
        val report = analyzer.analyzeText("Hello Sae", sharedLexiconFile = null, tokensFile = null)
        assertFalse(report.supportsRealModelTimestamps)
        assertEquals(2, report.wordTokens.size)
        assertTrue(analyzer.mapIpaToVrmVisemes("ɑi").containsAll(listOf(VrmViseme.AA, VrmViseme.IH)))
    }

    @Test
    fun presetsCoverAllRequiredCategories() {
        assertEquals(6, BenchmarkPresets.speechTestSentences.size)
        assertEquals(3, BenchmarkPresets.comparisonPresets.size)
    }
}
