package com.qoneqo.solitaire.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_stats")
data class StatEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val score: Int,
    val moves: Int,
    val timeElapsedSeconds: Long,
    val isWin: Boolean
)
