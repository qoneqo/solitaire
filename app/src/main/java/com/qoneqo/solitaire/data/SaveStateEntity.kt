package com.qoneqo.solitaire.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "save_state")
data class SaveStateEntity(
    @PrimaryKey val id: Int = 1,
    val stateJson: String
)
