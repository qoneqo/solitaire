package com.qoneqo.solitaire

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.media.AudioManager

class SoundManager(private val context: Context) {
    private var soundEnabled = true
    private val soundPool = mutableMapOf<Int, MediaPlayer>()

    fun playSound(soundResId: Int) {
        if (!soundEnabled) return

        try {
            MediaPlayer.create(context, soundResId).apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .build()
                )
                setOnCompletionListener { mp ->
                    mp.release()
                }
                start()
                soundPool[soundResId] = this
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        soundPool.values.forEach { it.release() }
        soundPool.clear()
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }

    fun isSoundEnabled(): Boolean = soundEnabled
}