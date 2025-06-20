package com.qoneqo.solitaire

import kotlin.collections.mutableSetOf

/**
 * Advanced unwinnable game detection system for Solitaire
 * This system performs deep analysis including face-down cards to determine if a game is unwinnable
 */
class GameStateAnalyzer {

    companion object {
        private const val MAX_ANALYSIS_DEPTH = 20
        private const val MAX_STATES_TO_ANALYZE = 10000
    }

    /**
     * Main function to check if the current game state is unwinnable
     */
    fun isGameUnwinnable(gameState: GameState): Boolean {
        // Quick checks first
        if (isObviouslyWinnable(gameState)) return false
        if (isObviouslyUnwinnable(gameState)) return true

        // Deep analysis
        return performDeepAnalysis(gameState)
    }

    /**
     * Quick check for obviously winnable states
     */
    private fun isObviouslyWinnable(gameState: GameState): Boolean {
        // If all cards are face-up and there are moves available, it's likely winnable
        val allFaceUp = gameState.tableau.all { pile ->
            pile.all { it.isFaceUp }
        } && gameState.deck.isEmpty()

        if (allFaceUp) {
            return hasImmediateMoves(gameState)
        }

        return false
    }

    /**
     * Quick check for obviously unwinnable states
     */
    private fun isObviouslyUnwinnable(gameState: GameState): Boolean {
        // Check for impossible foundation sequences
        if (hasImpossibleFoundationSequences(gameState)) return true

        // Check for deadlocked tableau with no escape routes
        if (isTableauDeadlocked(gameState)) return true

        // Check for critical cards buried impossibly deep
        if (areCriticalCardsBuriedImpossibly(gameState)) return true

        return false
    }

    /**
     * Check if there are immediate moves available
     */
    private fun hasImmediateMoves(gameState: GameState): Boolean {
        // Check waste to foundation/tableau
        if (gameState.waste.isNotEmpty()) {
            val wasteCard = gameState.waste.last()
            for (i in 0..3) {
                if (canMoveToFoundation(wasteCard, gameState.foundations[i])) return true
            }
            for (i in 0..6) {
                if (canMoveToTableauPile(wasteCard, gameState.tableau[i])) return true
            }
        }

        // Check tableau to foundation/tableau
        for (i in 0..6) {
            if (gameState.tableau[i].isEmpty()) continue
            val topCard = gameState.tableau[i].last()

            // Can move to foundation?
            for (j in 0..3) {
                if (canMoveToFoundation(topCard, gameState.foundations[j])) return true
            }

            // Can move to another tableau pile?
            for (j in 0..6) {
                if (i != j && canMoveToTableauPile(topCard, gameState.tableau[j])) return true
            }
        }

        // Check foundation to tableau (only if needed)
        for (i in 0..3) {
            if (gameState.foundations[i].isEmpty()) continue
            val topFoundationCard = gameState.foundations[i].last()
            for (j in 0..6) {
                if (canMoveToTableauPile(topFoundationCard, gameState.tableau[j])) return true
            }
        }

        return false
    }

    /**
     * Check for impossible foundation sequences
     */
    private fun hasImpossibleFoundationSequences(gameState: GameState): Boolean {
        for (foundationIndex in 0..3) {
            val foundation = gameState.foundations[foundationIndex]
            if (foundation.isEmpty()) continue

            val suit = foundation.first().suit
            val currentRank = foundation.last().rank.value

            // Check if required cards for this foundation are blocked
            for (nextRank in (currentRank + 1)..13) {
                val requiredCard = Card(suit, Card.Rank.values()[nextRank - 1])
                if (isCardImpossiblyBlocked(requiredCard, gameState)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Check if tableau is deadlocked
     */
    private fun isTableauDeadlocked(gameState: GameState): Boolean {
        // Check if all face-up cards in tableau are in wrong order and can't be moved
        val moveableCards = mutableListOf<Card>()

        for (pile in gameState.tableau) {
            if (pile.isNotEmpty() && pile.last().isFaceUp) {
                moveableCards.add(pile.last())
            }
        }

        // Add waste card if available
        if (gameState.waste.isNotEmpty()) {
            moveableCards.add(gameState.waste.last())
        }

        // Check if any of these cards can be moved anywhere
        for (card in moveableCards) {
            // Can move to foundation?
            for (foundation in gameState.foundations) {
                if (canMoveToFoundation(card, foundation)) {
                    return false
                }
            }

            // Can move to tableau?
            for (pile in gameState.tableau) {
                if (canMoveToTableauPile(card, pile)) {
                    return false
                }
            }
        }

        // If deck is empty and no moves available, it's deadlocked
        return gameState.deck.isEmpty()
    }

    /**
     * Check if critical cards are buried impossibly deep
     */
    private fun areCriticalCardsBuriedImpossibly(gameState: GameState): Boolean {
        // Find cards that are essential for progress but are buried
        val essentialCards = findEssentialCards(gameState)

        for (card in essentialCards) {
            if (isCardBuriedTooDeep(card, gameState)) {
                return true
            }
        }

        return false
    }

    /**
     * Find cards that are essential for game progress
     */
    private fun findEssentialCards(gameState: GameState): List<Card> {
        val essential = mutableListOf<Card>()

        // Aces are always essential
        for (suit in Card.Suit.values()) {
            val ace = Card(suit, Card.Rank.ACE)
            if (!isCardInFoundation(ace, gameState)) {
                essential.add(ace)
            }
        }

        // Cards needed for current foundation progress
        for (foundationIndex in 0..3) {
            val foundation = gameState.foundations[foundationIndex]
            if (foundation.isNotEmpty()) {
                val nextRank = foundation.last().rank.value + 1
                if (nextRank <= 13) {
                    val nextCard = Card(foundation.first().suit, Card.Rank.values()[nextRank - 1])
                    essential.add(nextCard)
                }
            }
        }

        return essential
    }

    /**
     * Check if a card is buried too deep to be accessible
     */
    private fun isCardBuriedTooDeep(targetCard: Card, gameState: GameState): Boolean {
        // Find the card's location
        for (pileIndex in 0..6) {
            val pile = gameState.tableau[pileIndex]
            val cardIndex = pile.indexOfFirst { it.suit == targetCard.suit && it.rank == targetCard.rank }

            if (cardIndex != -1) {
                // Check if cards above it can be moved away
                val cardsAbove = pile.subList(cardIndex + 1, pile.size)
                return !canClearCardsAbove(cardsAbove, gameState, pileIndex)
            }
        }

        // Check if card is in deck/waste
        val inDeck = gameState.deck.any { it.suit == targetCard.suit && it.rank == targetCard.rank }
        val inWaste = gameState.waste.any { it.suit == targetCard.suit && it.rank == targetCard.rank }

        if (inDeck) {
            // Card is in deck, check if it can be accessed through cycling
            return !canAccessCardInDeck(targetCard, gameState)
        }

        return false
    }

    /**
     * Check if cards above a target can be cleared
     */
    private fun canClearCardsAbove(cardsAbove: List<Card>, gameState: GameState, pileIndex: Int): Boolean {
        if (cardsAbove.isEmpty()) return true

        // Check if the sequence can be moved as a whole
        if (isValidDescendingSequence(cardsAbove)) {
            // Check if there's a place to move this sequence
            for (i in 0..6) {
                if (i != pileIndex) {
                    val targetPile = gameState.tableau[i]
                    if (canMoveSequenceToTableau(cardsAbove, targetPile)) {
                        return true
                    }
                }
            }
        }

        // Check if cards can be moved individually to foundations
        for (card in cardsAbove.reversed()) {
            for (foundation in gameState.foundations) {
                if (canMoveToFoundation(card, foundation)) {
                    // Recursively check if remaining cards can be cleared
                    val remaining = cardsAbove.filter { it != card }
                    if (canClearCardsAbove(remaining, gameState, pileIndex)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * Perform deep analysis using state space exploration
     */
    private fun performDeepAnalysis(gameState: GameState): Boolean {
        val visitedStates = mutableSetOf<String>()
        val statesToAnalyze = mutableListOf<GameState>()

        statesToAnalyze.add(gameState)
        var statesAnalyzed = 0

        while (statesToAnalyze.isNotEmpty() && statesAnalyzed < MAX_STATES_TO_ANALYZE) {
            val currentState = statesToAnalyze.removeAt(0)
            val stateHash = generateStateHash(currentState)

            if (stateHash in visitedStates) continue
            visitedStates.add(stateHash)
            statesAnalyzed++

            // Check if this state has any possible moves
            val possibleMoves = generateAllPossibleMoves(currentState)

            if (possibleMoves.isEmpty()) {
                // No moves available and game not won = unwinnable
                if (!isGameWon(currentState)) {
                    return true
                }
            } else {
                // Add resulting states to analysis queue
                for (move in possibleMoves.take(5)) { // Limit to prevent explosion
                    val newState = applyMove(currentState, move)
                    if (newState != null) {
                        statesToAnalyze.add(newState)
                    }
                }
            }
        }

        // If we've analyzed many states and found no winning path, likely unwinnable
        return statesAnalyzed >= MAX_STATES_TO_ANALYZE
    }

    /**
     * Generate all possible moves from current state
     */
    private fun generateAllPossibleMoves(gameState: GameState): List<GameMove> {
        val moves = mutableListOf<GameMove>()

        // Draw card moves
        if (gameState.deck.isNotEmpty() || gameState.waste.isNotEmpty()) {
            moves.add(GameMove.DrawCard)
        }

        // Waste to foundation
        if (gameState.waste.isNotEmpty()) {
            for (i in 0..3) {
                if (canMoveToFoundation(gameState.waste.last(), gameState.foundations[i])) {
                    moves.add(GameMove.WasteToFoundation(i))
                }
            }
        }

        // Waste to tableau
        if (gameState.waste.isNotEmpty()) {
            for (i in 0..6) {
                if (canMoveWasteToTableau(gameState, i)) {
                    moves.add(GameMove.WasteToTableau(i))
                }
            }
        }

        // Tableau to foundation
        for (i in 0..6) {
            if (gameState.tableau[i].isNotEmpty()) {
                for (j in 0..3) {
                    if (canMoveToFoundation(gameState.tableau[i].last(), gameState.foundations[j])) {
                        moves.add(GameMove.TableauToFoundation(i, j))
                    }
                }
            }
        }

        // Tableau to tableau
        for (i in 0..6) {
            for (j in 0..6) {
                if (i != j && canMoveTableauToTableau(gameState, i, j)) {
                    val maxCards = getMaxMoveableCards(gameState.tableau[i])
                    for (cardCount in 1..maxCards) {
                        moves.add(GameMove.TableauToTableau(i, j, cardCount))
                    }
                }
            }
        }

        return moves
    }

    // Helper functions

    private fun canMoveToFoundation(card: Card, foundation: List<Card>): Boolean {
        return card.canPlaceInFoundation(foundation.lastOrNull())
    }

    private fun canMoveToTableauPile(card: Card, pile: List<Card>): Boolean {
        return if (pile.isEmpty()) {
            card.rank == Card.Rank.KING
        } else {
            card.canPlaceOn(pile.last())
        }
    }

    private fun canMoveWasteToTableau(gameState: GameState, tableauIndex: Int): Boolean {
        return canMoveToTableauPile(gameState.waste.last(), gameState.tableau[tableauIndex])
    }

    private fun canMoveTableauToTableau(gameState: GameState, fromIndex: Int, toIndex: Int): Boolean {
        val fromPile = gameState.tableau[fromIndex]
        val toPile = gameState.tableau[toIndex]

        if (fromPile.isEmpty()) return false

        return canMoveToTableauPile(fromPile.last(), toPile)
    }

    private fun isValidDescendingSequence(cards: List<Card>): Boolean {
        if (cards.size <= 1) return true

        for (i in 0 until cards.size - 1) {
            val current = cards[i]
            val next = cards[i + 1]

            if (current.rank.value != next.rank.value + 1 ||
                current.suit.color == next.suit.color) {
                return false
            }
        }
        return true
    }

    private fun canMoveSequenceToTableau(sequence: List<Card>, targetPile: List<Card>): Boolean {
        if (sequence.isEmpty()) return false
        return canMoveToTableauPile(sequence.first(), targetPile)
    }

    private fun isCardInFoundation(card: Card, gameState: GameState): Boolean {
        return gameState.foundations.any { foundation ->
            foundation.any { it.suit == card.suit && it.rank == card.rank }
        }
    }

    private fun canAccessCardInDeck(card: Card, gameState: GameState): Boolean {
        // Simple heuristic: if deck is small enough, card is accessible
        return gameState.deck.size <= 20
    }

    private fun isCardImpossiblyBlocked(card: Card, gameState: GameState): Boolean {
        // Check if the card is blocked by cards that cannot be moved
        for (pileIndex in 0..6) {
            val pile = gameState.tableau[pileIndex]
            val cardIndex = pile.indexOfFirst { it.suit == card.suit && it.rank == card.rank }

            if (cardIndex != -1 && cardIndex < pile.size - 1) {
                // Card is not on top, check if cards above can be moved
                val cardsAbove = pile.subList(cardIndex + 1, pile.size)
                return !canClearCardsAbove(cardsAbove, gameState, pileIndex)
            }
        }
        return false
    }

    private fun getMaxMoveableCards(pile: List<Card>): Int {
        if (pile.isEmpty()) return 0

        var count = 1
        for (i in pile.size - 2 downTo 0) {
            val current = pile[i]
            val below = pile[i + 1]

            if (current.isFaceUp &&
                current.rank.value == below.rank.value + 1 &&
                current.suit.color != below.suit.color) {
                count++
            } else {
                break
            }
        }
        return count
    }

    private fun generateStateHash(gameState: GameState): String {
        // Create a hash of the game state for duplicate detection
        val sb = StringBuilder()

        // Hash deck
        sb.append("D:${gameState.deck.size}")

        // Hash waste
        sb.append("W:${gameState.waste.map { "${it.suit.ordinal}${it.rank.value}" }.joinToString(",")}")

        // Hash foundations
        gameState.foundations.forEachIndexed { index, foundation ->
            sb.append("F$index:${foundation.map { "${it.suit.ordinal}${it.rank.value}" }.joinToString(",")}")
        }

        // Hash tableau
        gameState.tableau.forEachIndexed { index, pile ->
            sb.append("T$index:${pile.map { "${it.suit.ordinal}${it.rank.value}${if(it.isFaceUp) "U" else "D"}" }.joinToString(",")}")
        }

        return sb.toString()
    }

    private fun applyMove(gameState: GameState, move: GameMove): GameState? {
        // Create a copy of the game state and apply the move
        // This is a simplified implementation - you'd need to implement the full move logic
        return null // Implementation depends on your game logic
    }

    private fun isGameWon(gameState: GameState): Boolean {
        return gameState.foundations.all { it.size == 13 }
    }

    // Add this to GameStateAnalyzer.kt
    fun isGameLost(gameState: GameState): Boolean {
        // 1. If the game is already won, it's not lost
        if (isGameWon(gameState)) return false

        // 2. If there are still cards in the deck, the game isn't lost yet
        if (gameState.deck.isNotEmpty()) return false

        // 3. If there are immediate moves available, the game isn't lost
        if (hasImmediateMoves(gameState)) return false

        // 4. Check if there are face-down cards that can still be revealed
        if (hasHiddenCardsThatCanBeRevealed(gameState)) return false

        // 5. If all cards are face-up and no moves left, check if foundations can still progress
        if (isAllCardsFaceUp(gameState) && !hasFoundationPotential(gameState)) return true

        // 6. If none of the above, perform deep analysis (but limit recursion depth)
        return performDeepAnalysis(gameState)
    }
    private fun hasHiddenCardsThatCanBeRevealed(gameState: GameState): Boolean {
        for (pile in gameState.tableau) {
            if (pile.size > 1 && !pile[pile.size - 2].isFaceUp) {
                // There's a face-down card that can be revealed by moving the top card
                return true
            }
        }
        return false
    }
    private fun isAllCardsFaceUp(gameState: GameState): Boolean {
        return gameState.tableau.all { pile -> pile.all { it.isFaceUp } } &&
                gameState.waste.all { it.isFaceUp } &&
                gameState.deck.isEmpty()
    }

    private fun canCardBeRevealedThroughMoves(card: Card, gameState: GameState): Boolean {
        // 1. Find which pile and position the card is in
        for (pileIndex in 0..6) {
            val pile = gameState.tableau[pileIndex]
            val cardPosition = pile.indexOfFirst { it == card }

            if (cardPosition != -1) {
                // 2. Check if it's already face-up (no need to reveal)
                if (card.isFaceUp) return true

                // 3. Check if cards above it can be moved away
                val cardsAbove = pile.subList(cardPosition + 1, pile.size)

                // 4. If no cards above, it's already the top card (should be face-up)
                if (cardsAbove.isEmpty()) return false

                // 5. Check if the sequence above can be moved elsewhere
                return canSequenceBeMoved(cardsAbove, pileIndex, gameState)
            }
        }

        // Card not found in tableau (might be in deck/waste)
        return true
    }

    private fun canSequenceBeMoved(sequence: List<Card>, fromPileIndex: Int, gameState: GameState): Boolean {
        // 1. First check if the sequence is valid and movable
        if (!isValidDescendingSequence(sequence)) return false

        val bottomCard = sequence.first()

        // 2. Check all possible destination piles
        for (destPileIndex in 0..6) {
            if (destPileIndex == fromPileIndex) continue

            val destPile = gameState.tableau[destPileIndex]

            // 3. Can this sequence be placed on the destination pile?
            if (destPile.isEmpty()) {
                if (bottomCard.rank == Card.Rank.KING) return true
            } else {
                if (bottomCard.canPlaceOn(destPile.last())) return true
            }
        }

        // 4. Check if individual cards can be moved to foundations
        for (i in sequence.indices.reversed()) {
            val card = sequence[i]
            for (foundationIndex in 0..3) {
                if (canMoveToFoundation(card, gameState.foundations[foundationIndex])) {
                    // If we can move this card, check if remaining sequence can be moved
                    val remainingSequence = sequence.subList(0, i)
                    if (remainingSequence.isEmpty() || canSequenceBeMoved(remainingSequence, fromPileIndex, gameState)) {
                        return true
                    }
                }
            }
        }

        return false
    }
    private fun areCriticalCardsPermanentlyBuried(gameState: GameState): Boolean {
        // Check if essential cards (like Aces or sequence starters) are buried under unmovable cards
        val essentialCards = findEssentialCards(gameState)

        return essentialCards.any { card ->
            isCardBuriedTooDeep(card, gameState) &&
                    !canCardBeRevealedThroughMoves(card, gameState)
        }
    }

    private fun areFoundationsPermanentlyBlocked(gameState: GameState): Boolean {
        // Check if foundations need cards that are unavailable
        for (i in 0..3) {
            val foundation = gameState.foundations[i]
            if (foundation.isEmpty()) continue

            val nextRank = foundation.last().rank.value + 1
            if (nextRank > 13) continue

            val requiredCard = Card(foundation.first().suit, Card.Rank.values()[nextRank - 1])
            if (isCardUnavailable(requiredCard, gameState)) {
                return true
            }
        }
        return false
    }

    private fun isGameInDeadEndState(gameState: GameState): Boolean {
        // Quick checks first
        if (hasImmediateMoves(gameState)) return false
        if (gameState.deck.isNotEmpty()) return false // Still have cards to draw

        // 1. Check for foundation progress potential
        if (hasFoundationPotential(gameState)) return false

        // 2. Check for tableau unlocking potential
        if (hasTableauUnlockingPotential(gameState)) return false

        // 3. Check for critical card accessibility
        if (hasAccessibleCriticalCards(gameState)) return false

        // 4. Limited lookahead analysis (depth-limited)
        return !hasPotentialMoveSequences(gameState, maxDepth = 3)
    }

// Helper functions for dead-end detection:

    private fun hasFoundationPotential(gameState: GameState): Boolean {
        // Check if any foundations can still progress
        for (i in 0..3) {
            val foundation = gameState.foundations[i]
            val nextRank = if (foundation.isEmpty()) 1 else foundation.last().rank.value + 1
            if (nextRank > 13) continue

            val requiredSuit = if (foundation.isEmpty()) Card.Suit.values()[i] else foundation.first().suit
            val requiredCard = Card(requiredSuit, Card.Rank.values()[nextRank - 1])

            // Check if required card is available in playable position
            if (isCardReachable(requiredCard, gameState)) {
                return true
            }
        }
        return false
    }

    private fun hasTableauUnlockingPotential(gameState: GameState): Boolean {
        // Check for face-down cards that could be unlocked
        for (i in 0..6) {
            val pile = gameState.tableau[i]
            if (pile.size > 1 && !pile[pile.size - 2].isFaceUp) {
                // There's a face-down card that could be revealed
                val topCard = pile.last()

                // Check if we can move the top card somewhere
                for (j in 0..6) {
                    if (i == j) continue
                    if (gameState.tableau[j].isEmpty() && topCard.rank == Card.Rank.KING) {
                        return true
                    }
                    if (gameState.tableau[j].isNotEmpty() && topCard.canPlaceOn(gameState.tableau[j].last())) {
                        return true
                    }
                }

                // Check foundation moves
                for (j in 0..3) {
                    if (topCard.canPlaceInFoundation(gameState.foundations[j].lastOrNull())) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun hasAccessibleCriticalCards(gameState: GameState): Boolean {
        // Check accessibility of Aces and foundation sequence cards
        val criticalCards = findCriticalCards(gameState)

        return criticalCards.any { card ->
            isCardReachable(card, gameState) &&
                    !isCardBlockedByUnmovableSequence(card, gameState)
        }
    }

    private fun simulateMove(gameState: GameState, move: GameMove): GameState {
        // Create deep copies of all game state components
        val newDeck = gameState.deck.toMutableList()
        val newWaste = gameState.waste.toMutableList()
        val newFoundations = gameState.foundations.map { it.toMutableList() }.toTypedArray()
        val newTableau = gameState.tableau.map { it.toMutableList() }.toTypedArray()
        var newScore = gameState.score
        var newMoves = gameState.moves

        when (move) {
            is GameMove.DrawCard -> {
                if (newDeck.isNotEmpty()) {
                    val card = newDeck.removeAt(newDeck.size - 1)
                    card.isFaceUp = true
                    newWaste.add(card)
                    newMoves++
                    newScore = maxOf(0, newScore - 1)
                } else if (newWaste.isNotEmpty()) {
                    // Reset deck from waste
                    while (newWaste.isNotEmpty()) {
                        val card = newWaste.removeAt(newWaste.size - 1)
                        card.isFaceUp = false
                        newDeck.add(card)
                    }
                    newMoves++
                    newScore = maxOf(0, newScore - 1)
                }
            }

            is GameMove.WasteToFoundation -> {
                if (newWaste.isNotEmpty()) {
                    val card = newWaste.last()
                    val foundation = newFoundations[move.foundationIndex]
                    val topCard = foundation.lastOrNull()

                    if (card.canPlaceInFoundation(topCard)) {
                        newWaste.removeAt(newWaste.size - 1)
                        foundation.add(card)
                        newMoves++
                        newScore = maxOf(0, newScore - 1 + 10) // -1 move penalty + 10 foundation bonus
                    }
                }
            }

            is GameMove.WasteToTableau -> {
                if (newWaste.isNotEmpty()) {
                    val card = newWaste.last()
                    val targetPile = newTableau[move.tableauIndex]

                    val canMove = if (targetPile.isEmpty()) {
                        card.rank == Card.Rank.KING
                    } else {
                        card.canPlaceOn(targetPile.last())
                    }

                    if (canMove) {
                        newWaste.removeAt(newWaste.size - 1)
                        targetPile.add(card)
                        newMoves++
                        newScore = maxOf(0, newScore - 1 + 5) // -1 move penalty + 5 waste to tableau bonus
                    }
                }
            }

            is GameMove.TableauToFoundation -> {
                val pile = newTableau[move.tableauIndex]
                if (pile.isNotEmpty()) {
                    val card = pile.last()
                    val foundation = newFoundations[move.foundationIndex]
                    val topCard = foundation.lastOrNull()

                    if (card.canPlaceInFoundation(topCard)) {
                        pile.removeAt(pile.size - 1)
                        foundation.add(card)

                        // Flip the next card if needed
                        if (pile.isNotEmpty() && !pile.last().isFaceUp) {
                            pile.last().isFaceUp = true
                            newScore += 5
                        }

                        newMoves++
                        newScore = maxOf(0, newScore - 1 + 10) // -1 move penalty + 10 foundation bonus
                    }
                }
            }

            is GameMove.TableauToTableau -> {
                val fromPile = newTableau[move.fromIndex]
                val toPile = newTableau[move.toIndex]

                if (fromPile.size >= move.cardCount) {
                    val cardsToMove = fromPile.takeLast(move.cardCount)
                    if (cardsToMove.all { it.isFaceUp }) {
                        val bottomCard = cardsToMove.first()
                        val canMove = if (toPile.isEmpty()) {
                            bottomCard.rank == Card.Rank.KING
                        } else {
                            bottomCard.canPlaceOn(toPile.last())
                        }

                        if (canMove) {
                            repeat(move.cardCount) { fromPile.removeAt(fromPile.size - 1) }
                            toPile.addAll(cardsToMove)

                            // Flip the next card if needed
                            if (fromPile.isNotEmpty() && !fromPile.last().isFaceUp) {
                                fromPile.last().isFaceUp = true
                                newScore += 5
                            }

                            newMoves++
                        }
                    }
                }
            }
        }

        return GameState(
            deck = newDeck,
            waste = newWaste,
            foundations = newFoundations.map { it.toList() }.toTypedArray(),
            tableau = newTableau.map { it.toList() }.toTypedArray(),
            score = newScore,
            moves = newMoves
        )
    }
    private fun hasPotentialMoveSequences(gameState: GameState, maxDepth: Int): Boolean {
        if (maxDepth <= 0) return false

        // Generate all possible immediate moves
        val moves = generateAllPossibleMoves(gameState)
        if (moves.isEmpty()) return false

        // Try each possible move
        for (move in moves) {
            // Simulate the move
            val simulatedState = simulateMove(gameState, move)

            // Check if this move immediately creates new opportunities
            if (hasImmediateMoves(simulatedState)) {
                return true
            }

            // If we still have depth remaining, check recursively
            if (maxDepth > 1) {
                if (hasPotentialMoveSequences(simulatedState, maxDepth - 1)) {
                    return true
                }
            }

            // Special case: Drawing cards can reveal new options
            if (move is GameMove.DrawCard && gameState.deck.isNotEmpty()) {
                // After drawing, we might have new waste card options
                if (simulatedState.waste.isNotEmpty()) {
                    val topWasteCard = simulatedState.waste.last()
                    // Check if this newly revealed card can be played
                    for (i in 0..3) {
                        if (canMoveToFoundation(topWasteCard, simulatedState.foundations[i])) {
                            return true
                        }
                    }
                    for (i in 0..6) {
                        if (canMoveToTableauPile(topWasteCard, simulatedState.tableau[i])) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }


// New helper functions:

    private fun findCriticalCards(gameState: GameState): List<Card> {
        val criticalCards = mutableListOf<Card>()

        // All Aces not yet in foundations
        for (suit in Card.Suit.values()) {
            val ace = Card(suit, Card.Rank.ACE)
            if (!gameState.foundations.any { it.contains(ace) }) {
                criticalCards.add(ace)
            }
        }

        // Next cards needed for each foundation
        for (i in 0..3) {
            val foundation = gameState.foundations[i]
            if (foundation.isNotEmpty()) {
                val nextRank = foundation.last().rank.value + 1
                if (nextRank <= 13) {
                    criticalCards.add(Card(foundation.first().suit, Card.Rank.values()[nextRank - 1]))
                }
            }
        }

        return criticalCards
    }

    private fun isCardReachable(card: Card, gameState: GameState): Boolean {
        // 1. Check if card is in waste (immediately playable)
        if (gameState.waste.lastOrNull()?.suit == card.suit &&
            gameState.waste.lastOrNull()?.rank == card.rank) {
            return true
        }

        // 2. Check if card is on top of any tableau pile
        for (pile in gameState.tableau) {
            if (pile.lastOrNull()?.suit == card.suit &&
                pile.lastOrNull()?.rank == card.rank) {
                return true
            }
        }

        // 3. Check if card is in the deck (can be drawn eventually)
        if (gameState.deck.any { it.suit == card.suit && it.rank == card.rank }) {
            return true
        }

        // 4. Check if card is buried in tableau but can be uncovered
        for (pileIndex in gameState.tableau.indices) {
            val pile = gameState.tableau[pileIndex]
            val cardPosition = pile.indexOfFirst { it.suit == card.suit && it.rank == card.rank }

            if (cardPosition != -1 && cardPosition < pile.size - 1) {
                // Card is buried - check if sequence above can be moved
                val sequenceAbove = pile.subList(cardPosition + 1, pile.size)
                if (canSequenceBeMoved(sequenceAbove, pileIndex, gameState)) {
                    return true
                }
            }
        }

        return false
    }

    private fun isCardBlockedByUnmovableSequence(card: Card, gameState: GameState): Boolean {
        for (pileIndex in gameState.tableau.indices) {
            val pile = gameState.tableau[pileIndex]
            val cardPosition = pile.indexOfFirst { it.suit == card.suit && it.rank == card.rank }

            if (cardPosition != -1 && cardPosition < pile.size - 1) {
                // Card is buried under other cards
                val sequenceAbove = pile.subList(cardPosition + 1, pile.size)

                // If the sequence above can't be moved, the card is blocked
                if (!canSequenceBeMoved(sequenceAbove, pileIndex, gameState)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isCardUnavailable(card: Card, gameState: GameState): Boolean {
        // Check if card is in a position where it can never be played
        for (pile in gameState.tableau) {
            val index = pile.indexOfFirst { it.suit == card.suit && it.rank == card.rank }
            if (index != -1) {
                // Card is in tableau - check if it's accessible
                if (index == pile.size - 1) return false // Top card is accessible
                if (pile.subList(index + 1, pile.size).all { it.isFaceUp }) return false // Sequence can be moved
                return true // Buried under face-down cards
            }
        }

        // Card is in waste or not yet drawn
        return !gameState.waste.contains(card) && !gameState.deck.contains(card)
    }

    // Data classes for moves
    sealed class GameMove {
        object DrawCard : GameMove()
        data class WasteToFoundation(val foundationIndex: Int) : GameMove()
        data class WasteToTableau(val tableauIndex: Int) : GameMove()
        data class TableauToFoundation(val tableauIndex: Int, val foundationIndex: Int) : GameMove()
        data class TableauToTableau(val fromIndex: Int, val toIndex: Int, val cardCount: Int) : GameMove()
    }
}

// Extension function for SolitaireGame to check if unwinnable
fun SolitaireGame.isUnwinnable(): Boolean {
    val analyzer = GameStateAnalyzer()
    return analyzer.isGameUnwinnable(this.getGameState())
}