package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioFileManager
import com.example.data.ModelRepository
import com.example.data.TtsModelId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun appNameMatchesSaeTtsBenchmark() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Sae TTS Benchmark", appName)
    }

    @Test
    fun modelStorageUsesAppSpecificExternalFilesModelsDir() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ModelRepository(context)
        val kittenDir = repo.getModelDir(TtsModelId.KITTEN_NANO_V08)
        val kokoroDir = repo.getModelDir(TtsModelId.KOKORO_EN_V019)
        val supertonicDir = repo.getModelDir(TtsModelId.SUPERTONIC_3_INT8)

        assertTrue(kittenDir.absolutePath.endsWith("models${File.separator}kitten-nano-en-v0_8-int8"))
        assertTrue(kokoroDir.absolutePath.endsWith("models${File.separator}kokoro-int8-en-v0_19"))
        assertTrue(supertonicDir.absolutePath.endsWith("models${File.separator}supertonic-3-tts-int8"))
    }

    @Test
    fun wavRoundTripWritesAndReadsPcm16() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AudioFileManager(context)
        val samples = floatArrayOf(0f, 0.5f, -0.5f, 0.25f)
        val wav = manager.savePcm16Wav(samples, 24000, "unit_test_clip")
        assertTrue(wav.exists())
        val loaded = manager.readWavFile(wav)
        assertEquals(24000, loaded.sampleRate)
        assertEquals(4, loaded.samples.size)
        assertEquals(0.5f, loaded.samples[1], 0.01f)
        wav.delete()
    }
}
