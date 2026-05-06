package com.qoneqo.solitaire.domain

object SolitaireRules {

    fun canMoveToFoundation(card: Card, foundation: MutableList<Card>): Boolean {
        if (foundation.isEmpty()) {
            return card.rank == Rank.ACE
        }
        val topCard = foundation.last()
        return card.suit == topCard.suit && card.rank.value == topCard.rank.value + 1
    }

    fun canMoveToTableau(card: Card, tableau: MutableList<Card>): Boolean {
        if (tableau.isEmpty()) {
            return card.rank == Rank.KING
        }
        val topCard = tableau.last()
        // Must be alternate colors and rank one less
        return card.suit.color != topCard.suit.color && card.rank.value == topCard.rank.value - 1 && topCard.isFaceUp
    }

    fun canAutoComplete(gameState: GameState): Boolean {
        // If stock and waste are empty, and all tableau cards are face up, it can auto complete
        if (gameState.stock.isNotEmpty() || gameState.waste.isNotEmpty()) {
            return false
        }
        
        for (tableau in gameState.tableaus) {
            if (tableau.any { !it.isFaceUp }) {
                return false
            }
        }
        
        // Don't auto-complete if foundations are already full
        if (gameState.foundations.all { it.size == 13 }) return false
        
        return true
    }
}
