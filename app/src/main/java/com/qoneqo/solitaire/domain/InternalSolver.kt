package com.qoneqo.solitaire.domain

/**
 * A greedy solver for Klondike Solitaire with safe-move logic.
 */
object InternalSolver {

    fun getNextMove(state: GameState): LogbookMove? {

        // Priority 1: Move to Foundation (always safe)
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

        // Priority 2: Tableau moves that REVEAL a face-down card
        for (j in 0 until 7) {
            val fromPile = state.tableaus[j]
            val firstFaceUp = fromPile.indexOfFirst { it.isFaceUp }
            // Only if there ARE face-down cards under the face-up stack
            if (firstFaceUp <= 0) continue

            val stack = fromPile.subList(firstFaceUp, fromPile.size)
            val topCard = stack.first()

            for (i in 0 until 7) {
                if (i == j) continue
                if (SolitaireRules.canMoveToTableau(topCard, state.tableaus[i])) {
                    return LogbookMove("TO_TABLEAU", 2, j, i, stack.size)
                }
            }
        }

        // Priority 3: Waste to Tableau (place waste card to open up moves)
        if (state.waste.isNotEmpty()) {
            val card = state.waste.last()
            for (i in 0 until 7) {
                if (SolitaireRules.canMoveToTableau(card, state.tableaus[i])) {
                    return LogbookMove("TO_TABLEAU", 0, -1, i, 1)
                }
            }
        }

        // Priority 4: Deal Stock (primary way to get new cards)
        if (state.stock.isNotEmpty()) {
            return LogbookMove("DEAL_STOCK")
        }

        // Priority 5: Tableau to Tableau for non-revealing moves
        // Only do this if the destination is NOT empty (avoid King ping-pong)
        // And avoid moving a single King from one non-empty pile to another just to do something
        for (j in 0 until 7) {
            val fromPile = state.tableaus[j]
            if (fromPile.isEmpty()) continue
            val firstFaceUp = fromPile.indexOfFirst { it.isFaceUp }
            if (firstFaceUp == -1) continue

            val stack = fromPile.subList(firstFaceUp, fromPile.size)
            val topCard = stack.first()

            for (i in 0 until 7) {
                if (i == j) continue
                val toPile = state.tableaus[i]
                // Only move to non-empty piles in this priority (avoid useless King shifts)
                if (toPile.isEmpty()) continue
                if (SolitaireRules.canMoveToTableau(topCard, toPile)) {
                    return LogbookMove("TO_TABLEAU", 2, j, i, stack.size)
                }
            }
        }

        // Priority 6: Move King from Waste or Tableau to an EMPTY slot (to enable progress)
        // Only do this if there's something to gain (face-down cards somewhere)
        val hasHiddenCards = state.tableaus.any { pile -> pile.any { !it.isFaceUp } }
        if (hasHiddenCards) {
            for (i in 0 until 7) {
                if (state.tableaus[i].isNotEmpty()) continue // Only target empty slots
                // Try King from waste
                if (state.waste.isNotEmpty() && state.waste.last().rank == Rank.KING) {
                    return LogbookMove("TO_TABLEAU", 0, -1, i, 1)
                }
                // Try King from a face-up pile (pick the one with most face-down cards below)
                var bestFrom = -1
                var bestHidden = 0
                for (j in 0 until 7) {
                    val fromPile = state.tableaus[j]
                    if (fromPile.isEmpty()) continue
                    val firstFaceUp = fromPile.indexOfFirst { it.isFaceUp }
                    if (firstFaceUp == -1) continue
                    if (fromPile[firstFaceUp].rank == Rank.KING) {
                        val hiddenCount = firstFaceUp
                        if (hiddenCount > bestHidden) {
                            bestHidden = hiddenCount
                            bestFrom = j
                        }
                    }
                }
                if (bestFrom != -1) {
                    val fromPile = state.tableaus[bestFrom]
                    val firstFaceUp = fromPile.indexOfFirst { it.isFaceUp }
                    return LogbookMove("TO_TABLEAU", 2, bestFrom, i, fromPile.size - firstFaceUp)
                }
            }
        }

        // Priority 7: Recycle waste (last resort before declaring stuck)
        if (state.waste.isNotEmpty() && state.stock.isEmpty()) {
            return LogbookMove("RECYCLE_WASTE")
        }

        return null
    }
}
