package com.qoneqo.solitaire

data class Card(
    val suit: Suit,
    val rank: Rank,
    var isFaceUp: Boolean = false,
    var isMovable: Boolean = true
) {
    enum class Suit(val symbol: String, val color: CardColor) {
        HEARTS("♥", CardColor.RED),
        DIAMONDS("♦", CardColor.RED),
        CLUBS("♣", CardColor.BLACK),
        SPADES("♠", CardColor.BLACK)
    }

    enum class Rank(val value: Int, val displayName: String) {
        ACE(1, "A"),
        TWO(2, "2"),
        THREE(3, "3"),
        FOUR(4, "4"),
        FIVE(5, "5"),
        SIX(6, "6"),
        SEVEN(7, "7"),
        EIGHT(8, "8"),
        NINE(9, "9"),
        TEN(10, "10"),
        JACK(11, "J"),
        QUEEN(12, "Q"),
        KING(13, "K")
    }

    enum class CardColor {
        RED, BLACK
    }

    // Get the drawable resource name for this card
    fun getDrawableResourceName(): String {
        val suitLetter = when (suit) {
            Suit.HEARTS -> "h"
            Suit.DIAMONDS -> "d"
            Suit.CLUBS -> "c"
            Suit.SPADES -> "s"
        }
        val rankName = when (rank) {
            Rank.ACE -> "a"
            Rank.JACK -> "j"
            Rank.QUEEN -> "q"
            Rank.KING -> "k"
            else -> rank.value.toString()
        }
        return "card_${rankName}${suitLetter}"
    }

    // Check if this card can be placed on another card in tableau
    fun canPlaceOn(otherCard: Card): Boolean {
        return this.rank.value == otherCard.rank.value - 1 &&
                this.suit.color != otherCard.suit.color
    }

    // Check if this card can be placed in foundation
    fun canPlaceInFoundation(foundationCard: Card?): Boolean {
        return if (foundationCard == null) {
            this.rank == Rank.ACE
        } else {
            this.suit == foundationCard.suit &&
                    this.rank.value == foundationCard.rank.value + 1
        }
    }
}