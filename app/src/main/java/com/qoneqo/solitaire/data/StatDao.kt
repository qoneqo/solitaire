package com.qoneqo.solitaire.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface StatDao {
    @Insert
    suspend fun insertStat(stat: StatEntity)

    @Query("SELECT MAX(score) FROM game_stats")
    suspend fun getBestScore(): Int?
}
