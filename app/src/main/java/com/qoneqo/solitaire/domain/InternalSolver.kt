package com.qoneqo.solitaire.domain

/**
 * A lightweight greedy solver for Klondike Solitaire.
 */
object InternalSolver {

    fun getNextMove(state: GameState): LogbookMove? {
        // Priority 1: To Foundation
        // From Waste
        if (state.waste.isNotEmpty()) {
            val card = state.waste.last()
            for (i in 0 until 4) {
                if (SolitaireRules.canMoveToFoundation(card, state.foundations[i])) {
                    return LogbookMove("TO_FOUNDATION", 0, -1, i)
                }
            }
        }
        // From Tableaus
        for (i in 0 until 7) {
            if (state.tableaus[i].isNotEmpty()) {
                val card = state.tableaus[i].last()
                for (j in 0 until 4) {
                    if (SolitaireRules.canMoveToFoundation(card, state.foundations[j])) {
                        return LogbookMove("TO_FOUNDATION", 2, i, j)
                    }
                }
            }
        }

        // Priority 2: To Tableau (to reveal face-down cards)
        for (i in 0 until 7) {
            val toPile = state.tableaus[i]
            // From other Tableaus
            for (j in 0 until 7) {
                if (i == j) continue
                val fromPile = state.tableaus[j]
                val firstFaceUp = fromPile.indexOfFirst { it.isFaceUp }
                if (firstFaceUp == -1) continue
                
                // If moving this stack reveals a face-down card, it's high priority
                val card = fromPile[firstFaceUp]
                if (SolitaireRules.canMoveToTableau(card, toPile)) {
                    if (firstFaceUp > 0) {
                        return LogbookMove("TO_TABLEAU", 2, j, i, fromPile.size - firstFaceUp)
                    }
                }
            }
        }

        // Priority 3: Waste to Tableau
        if (state.waste.isNotEmpty()) {
            val card = state.waste.last()
            for (i in 0 until 7) {
                if (SolitaireRules.canMoveToTableau(card, state.tableaus[i])) {
                    return LogbookMove("TO_TABLEAU", 0, -1, i, 1)
                }
            }
        }

        // Priority 4: Deal Stock
        if (state.stock.isNotEmpty()) {
            return LogbookMove("DEAL_STOCK")
        } else if (state.waste.isNotEmpty()) {
            return LogbookMove("RECYCLE_WASTE")
        }

        return null
    }
}
