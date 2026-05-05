package com.qoneqo.solitaire.presentation.engine

import android.graphics.Color

import com.qoneqo.solitaire.domain.Suit
import com.qoneqo.solitaire.domain.Rank

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Int = 255,
    var color: Int = Color.YELLOW,
    var life: Float = 1.0f, // seconds
    val maxLife: Float = 1.0f
)

data class CascadingCard(
    val cardIndex: Int, // Index of the card in the foundation (0..12)
    val foundationIndex: Int, // 0..3
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val suit: Suit,
    val rank: Rank
)
