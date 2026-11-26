package com.example.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

data class LoadedWavAudio(
    val sampleRate: Int,
    val samples: FloatArray,
)

class AudioFileManager(private val context: Context) {

    fun getAudioOutputsDir(): File {
        val base = context.getExternalFilesDir(null)
            ?: File(context.filesDir, "external_fallback")
        return File(base, "audio_outputs").apply { mkdirs() }
    }

    suspend fun savePcm16Wav(
        samples: FloatArray,
        sampleRate: Int,
        fileName: String,
    ): File = withContext(Dispatchers.IO) {
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val outFile = File(getAudioOutputsDir(), if (safeName.endsWith(".wav")) safeName else "$safeName.wav")
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
        val blockAlign = numChannels * (bitsPerSample / 8)
        val dataSize = samples.size * 2

        DataOutputStream(BufferedOutputStream(FileOutputStream(outFile), 32 * 1024)).use { dos ->
            // RIFF chunk descriptor
            dos.writeBytes("RIFF")
            dos.writeIntLE(36 + dataSize)
            dos.writeBytes("WAVE")
            // fmt sub-chunk
            dos.writeBytes("fmt ")
            dos.writeIntLE(16) // PCM subchunk size
            dos.writeShortLE(1) // PCM format
            dos.writeShortLE(numChannels)
            dos.writeIntLE(sampleRate)
            dos.writeIntLE(byteRate)
            dos.writeShortLE(blockAlign)
            dos.writeShortLE(bitsPerSample)
            // data sub-chunk
            dos.writeBytes("data")
            dos.writeIntLE(dataSize)

            val pcmBuffer = ByteArray(4096)
            var bufIdx = 0
            for (sample in samples) {
                val clamped = sample.coerceIn(-1.0f, 1.0f)
                val s16 = (clamped * 32767.0f).roundToInt().coerceIn(-32768, 32767)
                pcmBuffer[bufIdx++] = (s16 and 0xFF).toByte()
                pcmBuffer[bufIdx++] = ((s16 ushr 8) and 0xFF).toByte()
                if (bufIdx >= pcmBuffer.size) {
                    dos.write(pcmBuffer, 0, bufIdx)
                    bufIdx = 0
                }
            }
            if (bufIdx > 0) {
                dos.write(pcmBuffer, 0, bufIdx)
            }
        }
        outFile
    }

    suspend fun readWavFile(file: File): LoadedWavAudio = withContext(Dispatchers.IO) {
        require(file.exists() && file.length() > 44L) { "WAV file is missing or invalid: ${file.name}" }
        val bytes = BufferedInputStream(FileInputStream(file)).use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riff = String(bytes, 0, 4)
        val wave = String(bytes, 8, 4)
        require(riff == "RIFF" && wave == "WAVE") { "Unsupported WAV container in ${file.name}" }

        var offset = 12
        var sampleRate = 24000
        var channels = 1
        var bitsPerSample = 16
        var dataOffset = -1
        var dataLength = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = bb.getInt(offset + 4)
            val payloadStart = offset + 8
            if (chunkId == "fmt " && payloadStart + 16 <= bytes.size) {
                channels = bb.getShort(payloadStart + 2).toInt() and 0xFFFF
                sampleRate = bb.getInt(payloadStart + 4)
                bitsPerSample = bb.getShort(payloadStart + 14).toInt() and 0xFFFF
            } else if (chunkId == "data") {
                dataOffset = payloadStart
                dataLength = chunkSize.coerceAtMost(bytes.size - payloadStart)
                break
            }
            offset = payloadStart + chunkSize + (chunkSize and 1)
        }

        require(dataOffset > 0 && dataLength > 0) { "Missing PCM data chunk in ${file.name}" }
        require(bitsPerSample == 16) { "Only 16-bit PCM WAV files are supported ($bitsPerSample-bit found)." }

        val frameCount = dataLength / (2 * channels.coerceAtLeast(1))
        val out = FloatArray(frameCount)
        var readPos = dataOffset
        for (i in 0 until frameCount) {
            val low = bytes[readPos].toInt() and 0xFF
            val high = bytes[readPos + 1].toInt()
            val sample16 = (high shl 8) or low
            out[i] = sample16 / 32768.0f
            readPos += 2 * channels
        }
        LoadedWavAudio(sampleRate = sampleRate, samples = out)
    }

    fun deleteAudioFile(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (file.exists()) file.delete()
    }

    fun clearAllAudioOutputs() {
        getAudioOutputsDir().listFiles()?.forEach { it.delete() }
    }

    fun getAudioOutputsSizeBytes(): Long {
        return getAudioOutputsDir().listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun DataOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }
}
