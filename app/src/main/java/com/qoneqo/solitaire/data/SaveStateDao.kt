package com.qoneqo.solitaire.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SaveStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGame(state: SaveStateEntity)

    @Query("SELECT * FROM save_state WHERE id = 1")
    suspend fun loadGame(): SaveStateEntity?
}
