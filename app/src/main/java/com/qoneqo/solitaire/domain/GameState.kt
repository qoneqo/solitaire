package com.qoneqo.solitaire.domain

import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    var stock: ArrayList<Card> = ArrayList(),
    var waste: ArrayList<Card> = ArrayList(),
    var foundations: ArrayList<ArrayList<Card>> = ArrayList(List(4) { ArrayList<Card>() }),
    var tableaus: ArrayList<ArrayList<Card>> = ArrayList(List(7) { ArrayList<Card>() }),
    var score: Int = 0,
    var moves: Int = 0,
    var undoStack: MutableList<GameCommand> = mutableListOf()
)

