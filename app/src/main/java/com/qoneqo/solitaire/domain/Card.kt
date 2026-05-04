package com.qoneqo.solitaire.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

enum class CardColor { RED, BLACK }

enum class Suit(val color: CardColor, val letter: Char) {
    CLUBS(CardColor.BLACK, 'c'),
    DIAMONDS(CardColor.RED, 'd'),
    HEARTS(CardColor.RED, 'h'),
    SPADES(CardColor.BLACK, 's')
}

enum class Rank(val value: Int, val letter: String) {
    ACE(1, "a"),
    TWO(2, "2"),
    THREE(3, "3"),
    FOUR(4, "4"),
    FIVE(5, "5"),
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    NINE(9, "9"),
    TEN(10, "10"),
    JACK(11, "j"),
    QUEEN(12, "q"),
    KING(13, "k")
}

@Serializable
data class Card(val suit: Suit, val rank: Rank, var isFaceUp: Boolean) {
    // Rendering properties (transient, not serialized)
    @Transient var renderX: Float = 0f
    @Transient var renderY: Float = 0f
    @Transient var originalX: Float = 0f
    @Transient var originalY: Float = 0f
    @Transient var isSnappingBack: Boolean = false
    
    // For calculating the relative touch position
    @Transient var touchOffsetX: Float = 0f
    @Transient var touchOffsetY: Float = 0f
}
