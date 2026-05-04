package com.qoneqo.solitaire.domain

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

@Serializable
data class LogbookMove(
    val type: String, // "DEAL_STOCK", "RECYCLE_WASTE", "TO_FOUNDATION", "TO_TABLEAU"
    val fromType: Int = -1, // 0: Waste, 2: Tableau
    val fromIdx: Int = -1,
    val toIdx: Int = -1,
    val cardCount: Int = 1
)

@Serializable
data class LogbookEntry(
    val id: Int,
    val deck: List<String>, // e.g. "c1", "s13"
    val moves: List<LogbookMove>
)

object LogbookManager {
    private var entries: List<LogbookEntry> = emptyList()

    fun load(context: Context) {
        try {
            val inputStream = context.assets.open("solitaire/logbook.json")
            val reader = InputStreamReader(inputStream)
            val jsonString = reader.readText()
            entries = Json { ignoreUnknownKeys = true }.decodeFromString(jsonString)
            android.util.Log.d("LogbookManager", "Successfully loaded ${entries.size} games from logbook.")
        } catch (e: Exception) {
            android.util.Log.e("LogbookManager", "Failed to load logbook: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getRandomEntry(): LogbookEntry? {
        if (entries.isEmpty()) {
            android.util.Log.w("LogbookManager", "No entries available in logbook.")
            return null
        }
        val entry = entries.random()
        android.util.Log.d("LogbookManager", "Selected game ID: ${entry.id}")
        return entry
    }

    fun stringToCard(s: String): Card {
        val suitChar = s[0]
        val rankVal = s.substring(1).toInt()
        
        val suit = when (suitChar) {
            'c' -> Suit.CLUBS
            'd' -> Suit.DIAMONDS
            'h' -> Suit.HEARTS
            's' -> Suit.SPADES
            else -> Suit.CLUBS
        }
        
        val rank = Rank.values().find { it.value == rankVal } ?: Rank.ACE
        return Card(suit, rank, false)
    }
}
