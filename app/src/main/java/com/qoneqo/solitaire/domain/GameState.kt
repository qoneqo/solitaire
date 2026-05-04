package com.qoneqo.solitaire.domain

import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    var stock: ArrayList<Card> = ArrayList(),
    var waste: ArrayList<Card> = ArrayList(),
    var foundations: Array<ArrayList<Card>> = Array(4) { ArrayList() },
    var tableaus: Array<ArrayList<Card>> = Array(7) { ArrayList() },
    var score: Int = 0,
    var moves: Int = 0,
    var undoStack: ArrayList<String> = ArrayList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GameState

        if (stock != other.stock) return false
        if (waste != other.waste) return false
        if (!foundations.contentEquals(other.foundations)) return false
        if (!tableaus.contentEquals(other.tableaus)) return false
        if (score != other.score) return false
        if (moves != other.moves) return false
        if (undoStack != other.undoStack) return false

        return true
    }

    override fun hashCode(): Int {
        var result = stock.hashCode()
        result = 31 * result + waste.hashCode()
        result = 31 * result + foundations.contentHashCode()
        result = 31 * result + tableaus.contentHashCode()
        result = 31 * result + score
        result = 31 * result + moves
        result = 31 * result + undoStack.hashCode()
        return result
    }
}
