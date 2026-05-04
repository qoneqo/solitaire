package com.qoneqo.solitaire

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.content.SharedPreferences

class SoundManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("solitaire_prefs", Context.MODE_PRIVATE)
    private var soundEnabled = prefs.getBoolean("sound_enabled", true)
    
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(5)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundMap = mutableMapOf<Int, Int>()

    fun playSound(soundResId: Int) {
        if (!soundEnabled) return

        if (soundMap.containsKey(soundResId)) {
            soundPool.play(soundMap[soundResId]!!, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            val id = soundPool.load(context, soundResId, 1)
            soundMap[soundResId] = id
            soundPool.setOnLoadCompleteListener { sp, loadedId, status ->
                if (status == 0 && loadedId == id) {
                    sp.play(loadedId, 1.0f, 1.0f, 1, 0, 1.0f)
                }
            }
        }
    }

    fun release() {
        soundPool.release()
        soundMap.clear()
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }

    fun isSoundEnabled(): Boolean = soundEnabled
}