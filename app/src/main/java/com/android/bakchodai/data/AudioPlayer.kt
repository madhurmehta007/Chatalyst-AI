package com.android.bakchodai.data

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val isPlaying: Boolean = false,
    val progressMs: Int = 0,
    val durationMs: Int = 0
)

@Singleton
class AudioPlayer @Inject constructor() {
    private var player: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState = _playbackState.asStateFlow()

    fun play(url: String) {
        stop(resetState = false)

        player = MediaPlayer().apply {
            try {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                setDataSource(url)
                prepareAsync()

                setOnPreparedListener {
                    _playbackState.value = PlaybackState(
                        isPlaying = true,
                        progressMs = 0,
                        durationMs = it.duration
                    )
                    it.start()
                    startProgressLoop()
                }

                setOnCompletionListener {
                    stop(resetState = true)
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlayer", "MediaPlayer Error: what $what, extra $extra")
                    stop(resetState = true)
                    true
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Failed to set data source or prepare", e)
                stop(resetState = true)
            }
        }
    }

    fun pause() {
        player?.pause()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
        progressJob?.cancel()
    }

    fun resume() {
        player?.start()
        _playbackState.value = _playbackState.value.copy(isPlaying = true)
        startProgressLoop()
    }

    fun seekTo(ms: Int) {
        player?.seekTo(ms)
        _playbackState.value = _playbackState.value.copy(progressMs = ms)
    }

    /**
     * Stops and releases the MediaPlayer.
     * @param resetState If true (default), reset playbackStateFlow.
     * Set to false when just switching tracks.
     */
    fun stop(resetState: Boolean = true) {
        progressJob?.cancel()
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) {
            Log.w("AudioPlayer", "MediaPlayer stop/release failed, likely already released.")
        }
        player = null

        if (resetState) {
            _playbackState.value = PlaybackState()
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _playbackState.value.isPlaying) {
                player?.currentPosition?.let { currentMs ->
                    _playbackState.value = _playbackState.value.copy(
                        progressMs = currentMs
                    )
                }
                delay(100L)
            }
        }
    }
}