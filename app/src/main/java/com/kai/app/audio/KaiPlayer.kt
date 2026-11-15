package com.kai.app.audio

import android.content.Context
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class PlaybackState(val owner: String = "", val playing: Boolean = false, val loading: Boolean = false, val position: Long = 0, val duration: Long = 0, val index: Int = 0, val count: Int = 0, val speed: Float = 1f, val error: String = "")

/** One player for the whole app, with native audio focus and no background service/microphone. */
class KaiPlayer(context: Context, private val scope: CoroutineScope) {
    private val player = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
        setHandleAudioBecomingNoisy(true)
    }
    private val mutable = MutableStateFlow(PlaybackState())
    val state = mutable.asStateFlow()
    private var ticker: Job? = null
    private var trackSpeeds: List<Float> = emptyList()
    private var defaultTrackSpeed: Float = 1f
    var foreground: Boolean = false
        private set
    init {
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                publish()
                updateTicker()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val speed = trackSpeeds.getOrNull(player.currentMediaItemIndex) ?: defaultTrackSpeed
                player.setPlaybackSpeed(speed)
                publish()
            }
            override fun onPlayerError(error: PlaybackException) { mutable.value = mutable.value.copy(playing = false, loading = false, error = "Audio tidak dapat diputar. Coba buat ulang VN.") }
        })
    }
    fun setForeground(value: Boolean) { foreground = value; if (!value) pause() }
    fun play(owner: String, files: List<File>, speeds: List<Float> = emptyList(), defaultSpeed: Float = 1f) {
        if (files.isEmpty() || files.any { !it.exists() }) {
            mutable.value = PlaybackState(owner = owner, error = "Berkas audio tidak ditemukan. Buat ulang VN."); return
        }
        if (mutable.value.owner != owner) {
            player.stop()
            trackSpeeds = speeds.map { it.coerceIn(.5f, 2f) }
            defaultTrackSpeed = defaultSpeed.coerceIn(.5f, 2f)
            val firstSpeed = trackSpeeds.firstOrNull() ?: defaultTrackSpeed
            player.setPlaybackSpeed(firstSpeed)
            player.setMediaItems(files.map { MediaItem.fromUri(android.net.Uri.fromFile(it)) })
            mutable.value = PlaybackState(owner = owner, count = files.size, speed = firstSpeed)
            player.prepare(); player.play()
        } else if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0, 0)
            player.play()
        }
        updateTicker()
    }
    fun pause() { player.pause(); ticker?.cancel(); publish() }
    fun stop() { player.stop(); player.clearMediaItems(); trackSpeeds = emptyList(); defaultTrackSpeed = 1f; ticker?.cancel(); mutable.value = PlaybackState(speed = player.playbackParameters.speed) }
    fun seek(position: Long) { player.seekTo(position.coerceIn(0, mutable.value.duration)); publish() }
    fun next() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
    fun previous() { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0) }
    fun speed(value: Float) { player.setPlaybackSpeed(value.coerceIn(.5f, 2f)); publish() }
    fun replay() { player.seekTo(0, 0); player.play(); publish() }
    fun release() { ticker?.cancel(); player.release() }
    private fun updateTicker() {
        if (!player.isPlaying && player.playbackState != Player.STATE_BUFFERING) {
            ticker?.cancel(); ticker = null
        } else if (ticker?.isActive != true) {
            ticker = scope.launch {
                while (isActive) { publish(); delay(250) }
            }
        }
    }
    private fun publish() {
        mutable.value = mutable.value.copy(playing = player.isPlaying, loading = player.playbackState == Player.STATE_BUFFERING,
            position = player.currentPosition.coerceAtLeast(0), duration = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
            index = player.currentMediaItemIndex, count = player.mediaItemCount, speed = player.playbackParameters.speed)
    }
}
