package com.example.videoeditor

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * Live preview of the timeline with ExoPlayer.
 *
 * - Trims use MediaItem.ClippingConfiguration, so nothing is re-encoded.
 * - Speed and volume are applied each time a new clip starts.
 * - Not previewed: fades and rotation (they only appear in the exported file).
 * - ExoPlayer caps volume at 1.0, so volumes above 1 preview as 1.0.
 */
class PreviewPlayer(context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var clips: List<Clip> = emptyList()

    init {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyClipSettings()
            }
        })
    }

    /** Rebuilds the playlist. Keeps the current clip and position when possible. */
    fun setClips(newClips: List<Clip>) {
        clips = newClips.toList()
        val items = clips.map { c ->
            MediaItem.Builder()
                .setUri(Uri.fromFile(File(c.path)))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs((c.startSec * 1000).toLong())
                        .setEndPositionMs((c.endSec * 1000).toLong())
                        .build()
                )
                .build()
        }
        player.setMediaItems(items, /* resetPosition = */ false)
        player.prepare()
        applyClipSettings()
    }

    /** Starts playback at the given clip, from its trimmed start. */
    fun playFrom(index: Int) {
        if (clips.isEmpty()) return
        player.seekTo(index.coerceIn(0, clips.lastIndex), 0)
        player.play()
    }

    private fun applyClipSettings() {
        val c = clips.getOrNull(player.currentMediaItemIndex) ?: return
        player.playbackParameters = PlaybackParameters(c.speed.toFloat())
        player.volume = c.volume.coerceIn(0.0, 1.0).toFloat()
    }

    fun release() = player.release()
}
