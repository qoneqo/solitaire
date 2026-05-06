package com.qoneqo.solitaire

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.content.SharedPreferences
import android.media.MediaPlayer

class SoundManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("solitaire_prefs", Context.MODE_PRIVATE)
    private var soundEnabled = prefs.getBoolean("sound_enabled", true)
    private var musicEnabled = prefs.getBoolean("music_enabled", true)
    
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
    private var mediaPlayer: MediaPlayer? = null

    init {
        if (musicEnabled) {
            startMusic()
        }
    }

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

    private fun startMusic() {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(context, R.raw.ambient_music)
                mediaPlayer?.isLooping = true
                mediaPlayer?.setVolume(0.15f, 0.15f) // Very low volume for lo-fi feel
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopMusic() {
        mediaPlayer?.pause()
    }

    fun setMusicEnabled(enabled: Boolean) {
        musicEnabled = enabled
        prefs.edit().putBoolean("music_enabled", enabled).apply()
        if (enabled) {
            startMusic()
        } else {
            stopMusic()
        }
    }

    fun isMusicEnabled(): Boolean = musicEnabled

    fun release() {
        soundPool.release()
        soundMap.clear()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }

    fun isSoundEnabled(): Boolean = soundEnabled
}