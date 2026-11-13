package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import com.example.data.model.AudioQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class RecorderStatus {
    IDLE, RECORDING, PAUSED, REVIEW
}

data class RecorderState(
    val status: RecorderStatus = RecorderStatus.IDLE,
    val elapsedMs: Long = 0L,
    val amplitudes: List<Float> = List(36) { 0.08f },
    val recordedFile: File? = null,
    val isPreviewPlaying: Boolean = false,
    val previewPositionMs: Long = 0L,
    val errorMessage: String? = null
)

class VoiceRecorderManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var previewPlayer: MediaPlayer? = null
    private var meterJob: Job? = null
    private var startEpochMs: Long = 0L
    private var accumulatedMs: Long = 0L

    fun startRecording(quality: AudioQuality) {
        stopPreview()
        releaseRecorder()
        val outDir = File(context.cacheDir, "recordings").apply { mkdirs() }
        val outFile = File(outDir, "take_${System.currentTimeMillis()}.m4a")
        try {
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(quality.bitRate)
            mr.setAudioSamplingRate(quality.sampleRate)
            mr.setOutputFile(outFile.absolutePath)
            mr.prepare()
            mr.start()

            recorder = mr
            accumulatedMs = 0L
            startEpochMs = System.currentTimeMillis()
            _state.value = RecorderState(
                status = RecorderStatus.RECORDING,
                elapsedMs = 0L,
                amplitudes = List(36) { 0.08f },
                recordedFile = outFile,
                errorMessage = null
            )
            startMeterLoop()
        } catch (e: Exception) {
            releaseRecorder()
            _state.value = RecorderState(
                status = RecorderStatus.IDLE,
                errorMessage = "Microphone unavailable: ${e.localizedMessage ?: "Permission or hardware error"}"
            )
        }
    }

    fun pauseRecording() {
        val mr = recorder ?: return
        if (_state.value.status != RecorderStatus.RECORDING) return
        try {
            mr.pause()
            accumulatedMs += System.currentTimeMillis() - startEpochMs
            meterJob?.cancel()
            _state.value = _state.value.copy(
                status = RecorderStatus.PAUSED,
                elapsedMs = accumulatedMs
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.localizedMessage)
        }
    }

    fun resumeRecording() {
        val mr = recorder ?: return
        if (_state.value.status != RecorderStatus.PAUSED) return
        try {
            mr.resume()
            startEpochMs = System.currentTimeMillis()
            _state.value = _state.value.copy(status = RecorderStatus.RECORDING)
            startMeterLoop()
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.localizedMessage)
        }
    }

    fun stopRecording() {
        val current = _state.value
        if (current.status == RecorderStatus.RECORDING) {
            accumulatedMs += System.currentTimeMillis() - startEpochMs
        }
        meterJob?.cancel()
        try {
            recorder?.stop()
        } catch (_: Exception) {
        } finally {
            releaseRecorder()
        }
        _state.value = _state.value.copy(
            status = RecorderStatus.REVIEW,
            elapsedMs = accumulatedMs.coerceAtLeast(500L)
        )
    }

    fun togglePreviewPlayback() {
        val file = _state.value.recordedFile ?: return
        if (!file.exists()) return
        if (_state.value.isPreviewPlaying) {
            stopPreview()
            return
        }
        scope.launch {
            try {
                val mp = withContext(Dispatchers.IO) {
                    MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                    }
                }
                mp.setOnCompletionListener {
                    stopPreview()
                }
                mp.start()
                previewPlayer = mp
                _state.value = _state.value.copy(isPreviewPlaying = true)
                while (_state.value.isPreviewPlaying && previewPlayer != null) {
                    val pos = try { previewPlayer?.currentPosition?.toLong() ?: 0L } catch (_: Exception) { 0L }
                    _state.value = _state.value.copy(previewPositionMs = pos)
                    delay(100)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = e.localizedMessage, isPreviewPlaying = false)
            }
        }
    }

    fun stopPreview() {
        try {
            previewPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            previewPlayer?.release()
        } catch (_: Exception) {
        }
        previewPlayer = null
        _state.value = _state.value.copy(isPreviewPlaying = false, previewPositionMs = 0L)
    }

    fun discard() {
        stopPreview()
        meterJob?.cancel()
        releaseRecorder()
        _state.value.recordedFile?.delete()
        _state.value = RecorderState()
    }

    private fun startMeterLoop() {
        meterJob?.cancel()
        meterJob = scope.launch(Dispatchers.Main) {
            while (_state.value.status == RecorderStatus.RECORDING) {
                val amp = try {
                    val maxAmp = recorder?.maxAmplitude ?: 0
                    (maxAmp / 32767f).coerceIn(0.06f, 1.0f)
                } catch (_: Exception) {
                    0.08f
                }
                val elapsed = accumulatedMs + (System.currentTimeMillis() - startEpochMs)
                val updatedBars = (_state.value.amplitudes.drop(1) + amp)
                _state.value = _state.value.copy(
                    elapsedMs = elapsed,
                    amplitudes = updatedBars
                )
                delay(75)
            }
        }
    }

    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }

    fun release() {
        stopPreview()
        meterJob?.cancel()
        releaseRecorder()
    }
}
