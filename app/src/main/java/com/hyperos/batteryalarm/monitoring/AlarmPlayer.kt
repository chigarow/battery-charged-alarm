package com.hyperos.batteryalarm.monitoring

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

class AlarmPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    fun play(uri: Uri) {
        stop()
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(context, uri)
            isLooping = false
            setOnCompletionListener {
                stop()
            }
            setOnErrorListener { mp, _, _ ->
                mp.reset()
                stop()
                true
            }
            prepare()
            start()
        }
        mediaPlayer = player
    }

    fun stop() {
        mediaPlayer?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        mediaPlayer = null
    }
}
