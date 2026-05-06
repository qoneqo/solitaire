package com.qoneqo.solitaire

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.util.Log

class SoundManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("solitaire_prefs", Context.MODE_PRIVATE)
    private var soundEnabled = prefs.getBoolean("sound_enabled", true)
    private var musicEnabled = prefs.getBoolean("music_enabled", true)
    
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8) // Increased for overlapping card sounds
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
        preloadSounds()
        if (musicEnabled) {
            startMusic()
        }
    }

    private fun preloadSounds() {
        val soundResIds = listOf(
            R.raw.bubble_pop,
            R.raw.pop,
            R.raw.tapping_glass,
            R.raw.sparkle,
            R.raw.win_sound,
            R.raw.lose_sound
        )
        for (resId in soundResIds) {
            val id = soundPool.load(context, resId, 1)
            soundMap[resId] = id
        }
    }

    fun playSound(soundResId: Int) {
        if (!soundEnabled) return

        val poolId = soundMap[soundResId]
        if (poolId != null) {
            // Volume set to 1.0f for normal clear sound
            soundPool.play(poolId, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            // If not preloaded (e.g. newly added), load it for future use
            val id = soundPool.load(context, soundResId, 1)
            soundMap[soundResId] = id
        }
    }

    private fun startMusic() {
        if (!musicEnabled) return

        if (mediaPlayer != null) {
            if (!mediaPlayer!!.isPlaying) {
                mediaPlayer?.start()
            }
            return
        }
        
        try {
            mediaPlayer = MediaPlayer.create(context, R.raw.ambient_music)
            mediaPlayer?.apply {
                isLooping = true
                val vol = 0.5f // Set to normal volume (0.5f)
                setVolume(vol, vol)
                start()
            }
        } catch (e: Exception) {
            Log.e("SoundManager", "Error starting ambient music", e)
        }
    }

    private fun stopMusic() {
        Log.d("SoundManager", "Stopping music.")
        mediaPlayer?.pause()
    }

    fun setMusicEnabled(enabled: Boolean) {
        Log.d("SoundManager", "setMusicEnabled: $enabled")
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
        try {
            soundPool.release()
            soundMap.clear()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }

    fun isSoundEnabled(): Boolean = soundEnabled
}