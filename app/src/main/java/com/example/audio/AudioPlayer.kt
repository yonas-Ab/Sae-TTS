package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

enum class PlaybackStatus {
    IDLE,
    PLAYING,
    PAUSED,
    COMPLETED,
    ERROR,
}

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val label: String = "",
    val sampleRate: Int = 24000,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val activeClipKey: String? = null,
    val errorMessage: String? = null,
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

class AudioPlayer(private val audioFileManager: AudioFileManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var currentSamples: FloatArray = FloatArray(0)
    private var currentPcm16: ShortArray = ShortArray(0)
    private var currentSampleRate: Int = 24000
    private var currentLabel: String = ""
    private var currentClipKey: String? = null

    private val cursorFrame = AtomicInteger(0)

    @Volatile
    private var pausedFlag: Boolean = false

    @Volatile
    private var volumeLevel: Float = 1.0f

    private var playbackJob: Job? = null
    private var activeTrack: AudioTrack? = null

    fun playFloatArray(
        samples: FloatArray,
        sampleRate: Int,
        label: String,
        clipKey: String,
    ) {
        stop()
        if (samples.isEmpty() || sampleRate <= 0) {
            _state.value = PlaybackState(
                status = PlaybackStatus.ERROR,
                label = label,
                errorMessage = "Audio buffer is empty.",
            )
            return
        }
        currentSamples = samples
        currentPcm16 = ShortArray(samples.size) { i ->
            (samples[i].coerceIn(-1.0f, 1.0f) * 32767.0f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        currentSampleRate = sampleRate
        currentLabel = label
        currentClipKey = clipKey
        cursorFrame.set(0)
        pausedFlag = false
        startStreamingLoop()
    }

    fun playWavFile(
        file: File,
        label: String,
        clipKey: String,
    ) {
        stop()
        playbackJob = scope.launch {
            try {
                val loaded = audioFileManager.readWavFile(file)
                currentSamples = loaded.samples
                currentPcm16 = ShortArray(loaded.samples.size) { i ->
                    (loaded.samples[i].coerceIn(-1.0f, 1.0f) * 32767.0f).roundToInt().coerceIn(-32768, 32767).toShort()
                }
                currentSampleRate = loaded.sampleRate
                currentLabel = label
                currentClipKey = clipKey
                cursorFrame.set(0)
                pausedFlag = false
                runAudioTrackPump()
            } catch (t: Throwable) {
                _state.value = PlaybackState(
                    status = PlaybackStatus.ERROR,
                    label = label,
                    activeClipKey = clipKey,
                    errorMessage = t.message ?: "Unable to play WAV file.",
                )
            }
        }
    }

    fun pause() {
        if (_state.value.status == PlaybackStatus.PLAYING) {
            pausedFlag = true
            try {
                activeTrack?.pause()
            } catch (_: Throwable) {
            }
            val durMs = framesToMs(currentPcm16.size, currentSampleRate)
            val posMs = framesToMs(cursorFrame.get(), currentSampleRate)
            _state.value = _state.value.copy(
                status = PlaybackStatus.PAUSED,
                positionMs = posMs,
                durationMs = durMs,
            )
        }
    }

    fun resume() {
        if (_state.value.status == PlaybackStatus.PAUSED && currentPcm16.isNotEmpty()) {
            pausedFlag = false
            try {
                activeTrack?.play()
            } catch (_: Throwable) {
            }
            _state.value = _state.value.copy(status = PlaybackStatus.PLAYING)
        } else if (_state.value.status == PlaybackStatus.COMPLETED && currentPcm16.isNotEmpty()) {
            replay()
        }
    }

    fun togglePlayPause() {
        when (_state.value.status) {
            PlaybackStatus.PLAYING -> pause()
            PlaybackStatus.PAUSED -> resume()
            PlaybackStatus.COMPLETED, PlaybackStatus.IDLE -> if (currentPcm16.isNotEmpty()) replay()
            PlaybackStatus.ERROR -> if (currentPcm16.isNotEmpty()) replay()
        }
    }

    fun replay() {
        if (currentPcm16.isEmpty()) return
        stopPlaybackLoopOnly()
        cursorFrame.set(0)
        pausedFlag = false
        startStreamingLoop()
    }

    fun seekToFraction(fraction: Float) {
        if (currentPcm16.isEmpty()) return
        val clamped = fraction.coerceIn(0f, 1f)
        val targetFrame = (currentPcm16.size * clamped).roundToInt().coerceIn(0, currentPcm16.size - 1)
        cursorFrame.set(targetFrame)
        try {
            activeTrack?.flush()
        } catch (_: Throwable) {
        }
        val durMs = framesToMs(currentPcm16.size, currentSampleRate)
        val posMs = framesToMs(targetFrame, currentSampleRate)
        _state.value = _state.value.copy(
            positionMs = posMs,
            durationMs = durMs,
        )
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        volumeLevel = clamped
        try {
            activeTrack?.setVolume(clamped)
        } catch (_: Throwable) {
        }
        _state.value = _state.value.copy(volume = clamped)
    }

    fun stop() {
        stopPlaybackLoopOnly()
        cursorFrame.set(0)
        pausedFlag = false
        val durMs = if (currentPcm16.isNotEmpty()) framesToMs(currentPcm16.size, currentSampleRate) else 0L
        _state.value = PlaybackState(
            status = PlaybackStatus.IDLE,
            label = currentLabel,
            sampleRate = currentSampleRate,
            positionMs = 0L,
            durationMs = durMs,
            volume = volumeLevel,
            activeClipKey = currentClipKey,
        )
    }

    fun release() {
        stop()
        currentSamples = FloatArray(0)
        currentPcm16 = ShortArray(0)
    }

    private fun startStreamingLoop() {
        playbackJob = scope.launch {
            runAudioTrackPump()
        }
    }

    private suspend fun runAudioTrackPump() {
        val pcm = currentPcm16
        val sr = currentSampleRate
        val totalFrames = pcm.size
        val durationMs = framesToMs(totalFrames, sr)

        val minBufBytes = AudioTrack.getMinBufferSize(
            sr,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(minBufBytes * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        activeTrack = track
        track.setVolume(volumeLevel)
        track.play()

        _state.value = PlaybackState(
            status = PlaybackStatus.PLAYING,
            label = currentLabel,
            sampleRate = sr,
            positionMs = framesToMs(cursorFrame.get(), sr),
            durationMs = durationMs,
            volume = volumeLevel,
            activeClipKey = currentClipKey,
        )

        val chunkFrames = 2048
        try {
            while (coroutineContextIsActive(playbackJob)) {
                if (pausedFlag) {
                    delay(40)
                    continue
                }
                val startIdx = cursorFrame.get()
                if (startIdx >= totalFrames) {
                    break
                }
                val toWrite = minOf(chunkFrames, totalFrames - startIdx)
                val written = track.write(pcm, startIdx, toWrite, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    throw IllegalStateException("AudioTrack write error: $written")
                }
                val nextIdx = (startIdx + written).coerceAtMost(totalFrames)
                cursorFrame.set(nextIdx)

                _state.value = _state.value.copy(
                    status = if (pausedFlag) PlaybackStatus.PAUSED else PlaybackStatus.PLAYING,
                    positionMs = framesToMs(nextIdx, sr),
                    durationMs = durationMs,
                )
            }

            if (cursorFrame.get() >= totalFrames && !pausedFlag) {
                // Allow AudioTrack hardware buffer to drain cleanly
                delay(180)
                _state.value = _state.value.copy(
                    status = PlaybackStatus.COMPLETED,
                    positionMs = durationMs,
                    durationMs = durationMs,
                )
            }
        } catch (t: Throwable) {
            if (playbackJob?.isActive == true) {
                _state.value = _state.value.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = t.message ?: "Playback error",
                )
            }
        } finally {
            try {
                track.stop()
            } catch (_: Throwable) {
            }
            try {
                track.release()
            } catch (_: Throwable) {
            }
            if (activeTrack === track) {
                activeTrack = null
            }
        }
    }

    private fun stopPlaybackLoopOnly() {
        pausedFlag = false
        playbackJob?.cancel()
        playbackJob = null
        val track = activeTrack
        activeTrack = null
        if (track != null) {
            try {
                track.pause()
                track.flush()
                track.stop()
                track.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun coroutineContextIsActive(job: Job?): Boolean = job?.isActive == true

    private fun framesToMs(frames: Int, sampleRate: Int): Long {
        if (sampleRate <= 0) return 0L
        return ((frames.toDouble() / sampleRate.toDouble()) * 1000.0).toLong()
    }
}
