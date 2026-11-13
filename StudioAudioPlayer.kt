package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.example.data.model.Track
import com.example.data.repository.StudioRepository
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
import java.io.FileOutputStream

enum class RepeatMode {
    OFF, ALL, ONE
}

data class PlayerUiState(
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 0.85f,
    val speed: Float = 1.0f,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val isLoadingAudio: Boolean = false,
    val hasFinishedTrack: Boolean = false,
    val errorMessage: String? = null
)

class StudioAudioPlayer(
    private val context: Context,
    private val repository: StudioRepository,
    private val scope: CoroutineScope,
    private val onNextRequested: () -> Unit,
    private val onPrevRequested: () -> Unit
) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var tickerJob: Job? = null
    private var loadJob: Job? = null
    private var loadedTrackId: String? = null
    @Volatile
    private var mediaSession: MediaSession? = null

    init {
        scope.launch(Dispatchers.IO) {
            setupMediaSession()
        }
    }

    private fun setupMediaSession() {
        try {
            val session = MediaSession(context, "DaudioStudioSession")
            val mainHandler = Handler(Looper.getMainLooper())
            session.setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        if (!_state.value.isPlaying) togglePlayPause()
                    }

                    override fun onPause() {
                        if (_state.value.isPlaying) togglePlayPause()
                    }

                    override fun onSeekTo(pos: Long) {
                        seekTo(pos)
                    }

                    override fun onSkipToNext() {
                        onNextRequested()
                    }

                    override fun onSkipToPrevious() {
                        onPrevRequested()
                    }
                },
                mainHandler
            )
            session.isActive = true
            mediaSession = session
        } catch (_: Exception) {
            // Robolectric / headless environments may stub MediaSession
        }
    }

    fun selectTrack(track: Track, autoPlay: Boolean) {
        if (loadedTrackId == track.id && mediaPlayer != null) {
            if (autoPlay && !_state.value.isPlaying) {
                togglePlayPause()
            }
            return
        }
        loadAndPrepare(track, autoPlay)
    }

    private fun loadAndPrepare(track: Track, autoPlay: Boolean) {
        loadJob?.cancel()
        tickerJob?.cancel()
        releaseMediaPlayerAsync(mediaPlayer)
        mediaPlayer = null
        loadedTrackId = track.id
        _state.value = _state.value.copy(
            currentTrackId = track.id,
            isPlaying = false,
            positionMs = 0L,
            durationMs = track.durationMs,
            isLoadingAudio = true,
            hasFinishedTrack = false,
            errorMessage = null
        )

        loadJob = scope.launch {
            var preparedPlayer: MediaPlayer? = null
            try {
                val audioFile = resolveAudioFile(track)
                if (loadedTrackId != track.id) return@launch
                if (audioFile == null || !audioFile.exists()) {
                    _state.value = _state.value.copy(
                        isLoadingAudio = false,
                        errorMessage = "Audio data unavailable for ${track.title}"
                    )
                    return@launch
                }

                preparedPlayer = withContext(Dispatchers.IO) {
                    MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(audioFile.absolutePath)
                        prepare()
                    }
                }

                if (loadedTrackId != track.id) {
                    releaseMediaPlayerAsync(preparedPlayer)
                    return@launch
                }

                val vol = _state.value.volume
                preparedPlayer.setVolume(vol, vol)
                applySpeedInternal(preparedPlayer, _state.value.speed)
                preparedPlayer.setOnCompletionListener {
                    handleTrackCompletion()
                }
                mediaPlayer = preparedPlayer

                val realDuration = preparedPlayer.duration.toLong().coerceAtLeast(track.durationMs).coerceAtLeast(1000L)
                _state.value = _state.value.copy(
                    durationMs = realDuration,
                    positionMs = 0L,
                    isLoadingAudio = false,
                    hasFinishedTrack = false
                )

                scope.launch(Dispatchers.IO) {
                    updateMediaSessionMetadata(track, realDuration)
                    updateMediaSessionPlaybackState()
                }

                if (autoPlay) {
                    preparedPlayer.start()
                    _state.value = _state.value.copy(isPlaying = true)
                    startTicker()
                    scope.launch(Dispatchers.IO) {
                        updateMediaSessionPlaybackState()
                    }
                }
            } catch (e: Exception) {
                if (preparedPlayer != null && mediaPlayer !== preparedPlayer) {
                    releaseMediaPlayerAsync(preparedPlayer)
                }
                _state.value = _state.value.copy(
                    isLoadingAudio = false,
                    isPlaying = false,
                    errorMessage = "Playback error: ${e.localizedMessage ?: "Unable to load audio"}"
                )
            }
        }
    }

    private suspend fun resolveAudioFile(track: Track): File? {
        if (track.isDemo && track.demoKey.isNotBlank()) {
            return DemoAudioSynthesizer.getOrCreateDemoFile(context, track.demoKey)
        }
        val cachedFile = withContext(Dispatchers.IO) {
            val cacheDir = File(context.filesDir, "audio_cache").apply { mkdirs() }
            val ext = when {
                track.mimeType.contains("wav") -> "wav"
                track.mimeType.contains("ogg") -> "ogg"
                track.mimeType.contains("mp4") || track.mimeType.contains("m4a") -> "m4a"
                else -> "mp3"
            }
            File(cacheDir, "${track.id}.$ext")
        }
        if (withContext(Dispatchers.IO) { cachedFile.exists() && cachedFile.length() > 0 }) {
            return cachedFile
        }
        val bytesResult = repository.downloadMediaChunks(track.id)
        val bytes = bytesResult.getOrNull() ?: return null
        withContext(Dispatchers.IO) {
            FileOutputStream(cachedFile).use { it.write(bytes) }
        }
        return cachedFile
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                tickerJob?.cancel()
                _state.value = _state.value.copy(isPlaying = false)
            } else {
                if (_state.value.hasFinishedTrack) {
                    mp.seekTo(0)
                    _state.value = _state.value.copy(positionMs = 0L, hasFinishedTrack = false)
                }
                mp.start()
                applySpeedInternal(mp, _state.value.speed)
                _state.value = _state.value.copy(isPlaying = true, hasFinishedTrack = false)
                startTicker()
            }
            scope.launch(Dispatchers.IO) {
                updateMediaSessionPlaybackState()
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.localizedMessage)
        }
    }

    fun pause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            tickerJob?.cancel()
            _state.value = _state.value.copy(isPlaying = false)
            scope.launch(Dispatchers.IO) {
                updateMediaSessionPlaybackState()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val dur = _state.value.durationMs.coerceAtLeast(1L)
        val clamped = positionMs.coerceIn(0L, dur)
        try {
            mediaPlayer?.seekTo(clamped.toInt())
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(
            positionMs = clamped,
            hasFinishedTrack = clamped >= dur - 250L && !_state.value.isPlaying
        )
        scope.launch(Dispatchers.IO) {
            updateMediaSessionPlaybackState()
        }
    }

    fun seekByProgress(fraction: Float) {
        val dur = _state.value.durationMs.coerceAtLeast(1L)
        seekTo((fraction.coerceIn(0f, 1f) * dur).toLong())
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        try {
            mediaPlayer?.setVolume(clamped, clamped)
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(volume = clamped)
    }

    fun cycleSpeed() {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f)
        val curIdx = speeds.indexOfFirst { kotlin.math.abs(it - _state.value.speed) < 0.05f }
        val next = speeds[(curIdx + 1) % speeds.size]
        _state.value = _state.value.copy(speed = next)
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) applySpeedInternal(mp, next)
        }
        scope.launch(Dispatchers.IO) {
            updateMediaSessionPlaybackState()
        }
    }

    fun toggleShuffle() {
        _state.value = _state.value.copy(shuffleEnabled = !_state.value.shuffleEnabled)
    }

    fun cycleRepeatMode() {
        val next = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.value = _state.value.copy(repeatMode = next)
    }

    private fun applySpeedInternal(mp: MediaPlayer, speed: Float) {
        try {
            val params = mp.playbackParams
            params.speed = speed
            mp.playbackParams = params
        } catch (_: Exception) {
        }
    }

    private fun handleTrackCompletion() {
        tickerJob?.cancel()
        val dur = _state.value.durationMs
        _state.value = _state.value.copy(
            isPlaying = false,
            positionMs = dur,
            hasFinishedTrack = true
        )
        scope.launch(Dispatchers.IO) {
            updateMediaSessionPlaybackState()
        }

        when (_state.value.repeatMode) {
            RepeatMode.ONE -> {
                scope.launch {
                    delay(1200)
                    if (_state.value.hasFinishedTrack) {
                        seekTo(0L)
                        togglePlayPause()
                    }
                }
            }
            RepeatMode.ALL -> {
                scope.launch {
                    delay(1400)
                    if (_state.value.hasFinishedTrack) {
                        onNextRequested()
                    }
                }
            }
            RepeatMode.OFF -> Unit
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Main) {
            while (_state.value.isPlaying) {
                val mp = mediaPlayer
                if (mp != null) {
                    val pos = try { mp.currentPosition.toLong() } catch (_: Exception) { _state.value.positionMs }
                    _state.value = _state.value.copy(positionMs = pos.coerceAtMost(_state.value.durationMs))
                }
                delay(80)
            }
        }
    }

    private fun updateMediaSessionMetadata(track: Track, durationMs: Long) {
        try {
            val meta = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist.ifBlank { "Ensis" })
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album.ifBlank { "Studio Blueprint" })
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                .build()
            mediaSession?.setMetadata(meta)
        } catch (_: Exception) {
        }
    }

    private fun updateMediaSessionPlaybackState() {
        try {
            val stateCode = if (_state.value.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS
            val pbState = PlaybackState.Builder()
                .setActions(actions)
                .setState(stateCode, _state.value.positionMs, _state.value.speed)
                .build()
            mediaSession?.setPlaybackState(pbState)
        } catch (_: Exception) {
        }
    }

    private fun releaseMediaPlayerAsync(mp: MediaPlayer?) {
        if (mp == null) return
        scope.launch(Dispatchers.IO) {
            try { mp.stop() } catch (_: Exception) {}
            try { mp.release() } catch (_: Exception) {}
        }
    }

    fun release() {
        loadJob?.cancel()
        tickerJob?.cancel()
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {
        }
        mediaSession = null
    }
}
