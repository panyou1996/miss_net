@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.service

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.panyou.missnet.data.media.MediaDownloadManager

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        
        val dataSourceFactory = MediaDownloadManager.getReadOnlyDataSourceFactory(this)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
