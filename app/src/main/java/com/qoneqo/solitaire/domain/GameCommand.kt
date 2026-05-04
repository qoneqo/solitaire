package com.qoneqo.solitaire.domain

import kotlinx.serialization.Serializable

@Serializable
sealed class GameCommand {
    abstract fun undo(gameState: GameState)
    
    @Serializable
    data class MoveCards(
        val cardIndices: List<Int>, // Indices of cards in the source pile
        val fromType: Int, // 0: Waste, 1: Foundation, 2: Tableau
        val fromIndex: Int,
        val toType: Int,
        val toIndex: Int,
        val wasFaceDown: Boolean = false // If the card below was flipped face up
    ) : GameCommand() {
        override fun undo(gameState: GameState) {
            val toPile = when (toType) {
                1 -> gameState.foundations[toIndex]
                2 -> gameState.tableaus[toIndex]
                else -> mutableListOf()
            }
            
            val fromPile = when (fromType) {
                0 -> gameState.waste
                1 -> gameState.foundations[fromIndex]
                2 -> gameState.tableaus[fromIndex]
                else -> mutableListOf()
            }
            
            // Move cards back
            val movedCards = mutableListOf<Card>()
            repeat(cardIndices.size) {
                if (toPile.isNotEmpty()) {
                    movedCards.add(0, toPile.removeAt(toPile.size - 1))
                }
            }
            
            if (wasFaceDown && fromPile.isNotEmpty()) {
                fromPile.last().isFaceUp = false
            }
            
            fromPile.addAll(movedCards)
            gameState.moves--
        }
    }

    @Serializable
    data class DealStock(val count: Int) : GameCommand() {
        override fun undo(gameState: GameState) {
            repeat(count) {
                if (gameState.waste.isNotEmpty()) {
                    val card = gameState.waste.removeAt(gameState.waste.size - 1)
                    card.isFaceUp = false
                    gameState.stock.add(card)
                }
            }
            gameState.moves--
        }
    }

    @Serializable
    data class RecycleWaste(val wasteCount: Int) : GameCommand() {
        override fun undo(gameState: GameState) {
            // Move all cards from stock back to waste
            while (gameState.stock.isNotEmpty()) {
                val card = gameState.stock.removeAt(gameState.stock.size - 1)
                card.isFaceUp = true
                gameState.waste.add(card)
            }
            gameState.moves--
        }
    }
}
