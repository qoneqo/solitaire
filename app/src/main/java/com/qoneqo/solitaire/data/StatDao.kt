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

    @Query("SELECT * FROM game_stats WHERE isWin = 1 ORDER BY score DESC, timeElapsedSeconds ASC LIMIT 10")
    suspend fun getTopScores(): List<StatEntity>
}
