package com.example.videoeditor

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever

object MediaProbe {
    fun durationSec(path: String): Double {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(path)
            (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
        } finally {
            mmr.release()
        }
    }

    fun hasAudio(path: String): Boolean {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(path)
            (0 until ex.trackCount).any {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
        } finally {
            ex.release()
        }
    }
}
