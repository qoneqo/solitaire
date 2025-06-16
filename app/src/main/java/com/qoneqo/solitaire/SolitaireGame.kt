package com.qoneqo.solitaire

import kotlin.random.Random

class SolitaireGame {

    companion object {
        private const val MOVE_PENALTY = 1  // Points deducted per move
        private const val WASTE_TO_TABLEAU_BONUS = 5
        private const val TO_FOUNDATION_BONUS = 10
        private const val TABLEAU_FLIP_BONUS = 5
        private const val INITIAL_SCORE = 1000  // Starting score
    }

    // Game state
    private val deck = mutableListOf<Card>()
    private val waste = mutableListOf<Card>()
    private val foundations = Array(4) { mutableListOf<Card>() }
    private val tableau = Array(7) { mutableListOf<Card>() }

    // Game stats
    var score = INITIAL_SCORE
        private set
    var moves = 0
        private set

    init {
        initializeGame()
    }

    private fun initializeGame() {
        createDeck()
        shuffleDeck()
        dealCards()
    }

    private fun createDeck() {
        deck.clear()
        for (suit in Card.Suit.values()) {
            for (rank in Card.Rank.values()) {
                deck.add(Card(suit, rank))
            }
        }
    }

    private fun shuffleDeck() {
        deck.shuffle(Random.Default)
    }
    private fun applyMovePenalty() {
        moves++
        score = maxOf(0, score - MOVE_PENALTY)
    }
    private fun dealCards() {
        // Deal cards to tableau
        var cardIndex = 0
        for (col in 0..6) {
            for (row in 0..col) {
                val card = deck[cardIndex++]
                if (row == col) {
                    card.isFaceUp = true // Top card is face up
                }
                tableau[col].add(card)
            }
        }

        // Remaining cards go to stock (deck)
        val stock = deck.subList(cardIndex, deck.size).toMutableList()
        deck.clear()
        deck.addAll(stock)
    }

    // Draw card from deck to waste
    fun drawCard(): Boolean {
        return if (deck.isNotEmpty()) {
            val card = deck.removeAt(deck.size - 1)
            card.isFaceUp = true
            waste.add(card)
            applyMovePenalty()
            true
        } else if (waste.isNotEmpty()) {
            // Reset deck from waste
            while (waste.isNotEmpty()) {
                val card = waste.removeAt(waste.size - 1)
                card.isFaceUp = false
                deck.add(card)
            }
            applyMovePenalty()
            true
        } else {
            false
        }
    }

    // Move card from waste to tableau
    fun moveWasteToTableau(tableauIndex: Int): Boolean {
        if (waste.isEmpty() || tableauIndex !in 0..6) return false

        val card = waste.last()
        val targetPile = tableau[tableauIndex]

        val canMove = if (targetPile.isEmpty()) {
            card.rank == Card.Rank.KING
        } else {
            card.canPlaceOn(targetPile.last())
        }

        if (canMove) {
            waste.removeAt(waste.size - 1)
            targetPile.add(card)
            applyMovePenalty()
            score += WASTE_TO_TABLEAU_BONUS
            return true
        }
        return false
    }

    // Move card from waste to foundation
    fun moveWasteToFoundation(foundationIndex: Int): Boolean {
        if (waste.isEmpty() || foundationIndex !in 0..3) return false

        val card = waste.last()
        val foundation = foundations[foundationIndex]
        val topCard = foundation.lastOrNull()

        if (card.canPlaceInFoundation(topCard)) {
            waste.removeAt(waste.size - 1)
            foundation.add(card)
            applyMovePenalty()
            score += TO_FOUNDATION_BONUS
            return true
        }
        return false
    }

    // Move cards within tableau
    fun moveTableauToTableau(fromIndex: Int, toIndex: Int, cardCount: Int = 1): Boolean {
        if (fromIndex !in 0..6 || toIndex !in 0..6 || fromIndex == toIndex) return false

        val fromPile = tableau[fromIndex]
        val toPile = tableau[toIndex]

        if (fromPile.size < cardCount) return false

        val cardsToMove = fromPile.takeLast(cardCount)
        if (cardsToMove.any { !it.isFaceUp }) return false

        val bottomCard = cardsToMove.first()
        val canMove = if (toPile.isEmpty()) {
            bottomCard.rank == Card.Rank.KING
        } else {
            bottomCard.canPlaceOn(toPile.last())
        }

        if (canMove) {
            repeat(cardCount) { fromPile.removeAt(fromPile.size - 1) }
            toPile.addAll(cardsToMove)

            // Flip the next card if needed
            if (fromPile.isNotEmpty() && !fromPile.last().isFaceUp) {
                fromPile.last().isFaceUp = true
                score += 5
            }

            moves++
            return true
        }
        return false
    }

    // Move card from tableau to foundation
    fun moveTableauToFoundation(tableauIndex: Int, foundationIndex: Int): Boolean {
        if (tableauIndex !in 0..6 || foundationIndex !in 0..3) return false

        val pile = tableau[tableauIndex]
        if (pile.isEmpty()) return false

        val card = pile.last()
        val foundation = foundations[foundationIndex]
        val topCard = foundation.lastOrNull()

        if (card.canPlaceInFoundation(topCard)) {
            pile.removeAt(pile.size - 1)
            foundation.add(card)

            // Flip the next card if needed
            if (pile.isNotEmpty() && !pile.last().isFaceUp) {
                pile.last().isFaceUp = true
                score += TABLEAU_FLIP_BONUS
            }

            applyMovePenalty()
            score += TO_FOUNDATION_BONUS
            return true
        }
        return false
    }

    // NEW: Move card from foundation to tableau
    fun moveFoundationToTableau(foundationIndex: Int, tableauIndex: Int): Boolean {
        if (foundationIndex !in 0..3 || tableauIndex !in 0..6) return false

        val foundation = foundations[foundationIndex]
        if (foundation.isEmpty()) return false

        val card = foundation.last()
        val targetPile = tableau[tableauIndex]

        val canMove = if (targetPile.isEmpty()) {
            card.rank == Card.Rank.KING
        } else {
            card.canPlaceOn(targetPile.last())
        }

        if (canMove) {
            foundation.removeAt(foundation.size - 1)
            targetPile.add(card)
            moves++
            // Deduct points for moving from foundation (reverse of the foundation bonus)
            score = maxOf(0, score - 10)
            return true
        }
        return false
    }

    // Check if game is won
    fun isGameWon(): Boolean {
        return foundations.all { it.size == 13 }
    }

    // Get current game state for UI
    fun getGameState(): GameState {
        return GameState(
            deck = deck.toList(),
            waste = waste.toList(),
            foundations = foundations.map { it.toList() }.toTypedArray(),
            tableau = tableau.map { it.toList() }.toTypedArray(),
            score = score,
            moves = moves
        )
    }

    // Add these methods to SolitaireGame class:

    // Check if move is possible without actually performing it
    fun canMoveWasteToFoundation(foundationIndex: Int): Boolean {
        if (waste.isEmpty() || foundationIndex !in 0..3) return false

        val card = waste.last()
        val foundation = foundations[foundationIndex]
        val topCard = foundation.lastOrNull()

        return card.canPlaceInFoundation(topCard)
    }

    fun canMoveTableauToFoundation(tableauIndex: Int, foundationIndex: Int): Boolean {
        if (tableauIndex !in 0..6 || foundationIndex !in 0..3) return false

        val pile = tableau[tableauIndex]
        if (pile.isEmpty()) return false

        val card = pile.last()
        val foundation = foundations[foundationIndex]
        val topCard = foundation.lastOrNull()

        return card.canPlaceInFoundation(topCard)
    }
    fun canMoveTableauToTableau(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in 0..6 || toIndex !in 0..6 || fromIndex == toIndex) return false

        val fromPile = tableau[fromIndex]
        if (fromPile.isEmpty()) return false

        // Find the first face-up card (bottom of movable sequence)
        val firstFaceUpIndex = fromPile.indexOfFirst { it.isFaceUp }
        if (firstFaceUpIndex == -1) return false // No face-up cards

        val movingCard = fromPile[firstFaceUpIndex]
        val toPile = tableau[toIndex]

        return if (toPile.isEmpty()) {
            movingCard.rank == Card.Rank.KING
        } else {
            movingCard.canPlaceOn(toPile.last())
        }
    }

    fun canMoveWasteToTableau(tableauIndex: Int): Boolean {
        if (waste.isEmpty() || tableauIndex !in 0..6) return false

        val card = waste.last()
        val targetPile = tableau[tableauIndex]

        return if (targetPile.isEmpty()) {
            card.rank == Card.Rank.KING
        } else {
            card.canPlaceOn(targetPile.last())
        }
    }

    fun canMoveFoundationToTableau(foundationIndex: Int, tableauIndex: Int): Boolean {
        if (foundationIndex !in 0..3 || tableauIndex !in 0..6) return false

        val foundation = foundations[foundationIndex]
        if (foundation.isEmpty()) return false

        val card = foundation.last()
        val targetPile = tableau[tableauIndex]

        return if (targetPile.isEmpty()) {
            card.rank == Card.Rank.KING
        } else {
            card.canPlaceOn(targetPile.last())
        }
    }

    // Reset game
    fun newGame() {
        deck.clear()
        waste.clear()
        foundations.forEach { it.clear() }
        tableau.forEach { it.clear() }
        score = INITIAL_SCORE
        moves = 0
        initializeGame()
    }
}

data class GameState(
    val deck: List<Card>,
    val waste: List<Card>,
    val foundations: Array<List<Card>>,
    val tableau: Array<List<Card>>,
    val score: Int,
    val moves: Int
)