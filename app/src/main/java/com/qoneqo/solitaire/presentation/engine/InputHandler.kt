package com.qoneqo.solitaire.presentation.engine

import android.view.MotionEvent
import com.qoneqo.solitaire.domain.Card
import com.qoneqo.solitaire.domain.GameCommand
import com.qoneqo.solitaire.domain.GameState
import com.qoneqo.solitaire.domain.SolitaireRules

class InputHandler(
    private val eventListener: GameEventListener?,
    private val am: CardAssetManager,
    private val queueAction: (() -> Unit) -> Unit
) {
    var activeCardStack: List<Card>? = null
    private var sourcePileType = -1 // 0: Waste, 1: Foundation, 2: Tableau
    private var sourcePileIndex = -1
    
    // Double Tap
    private var lastTapTime: Long = 0
    private var lastTapCard: Card? = null
    private val DOUBLE_TAP_TIMEOUT = 300L

    fun onTouchEvent(event: MotionEvent, gameState: GameState, layout: GameLayout): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                return handleActionDown(x, y, gameState, layout)
            }
            MotionEvent.ACTION_MOVE -> {
                activeCardStack?.forEachIndexed { index, card ->
                    card.renderX = x - card.touchOffsetX
                    card.renderY = y - card.touchOffsetY + (index * am.verticalOffset)
                }
                return activeCardStack != null
            }
            MotionEvent.ACTION_UP -> {
                return handleActionUp(x, y, gameState, layout)
            }
        }
        return false
    }

    private fun handleActionDown(x: Float, y: Float, gameState: GameState, layout: GameLayout): Boolean {
        // 1. Check stock tap
        if (x >= layout.stockX && x <= layout.stockX + am.cardWidth && y >= layout.stockY && y <= layout.stockY + am.cardHeight) {
            queueAction {
                if (gameState.stock.isNotEmpty()) {
                    val card = gameState.stock.removeAt(gameState.stock.size - 1)
                    card.isFaceUp = true
                    gameState.waste.add(card)
                    gameState.undoStack.add(GameCommand.DealStock(1))
                } else if (gameState.waste.isNotEmpty()) {
                    val count = gameState.waste.size
                    while (gameState.waste.isNotEmpty()) {
                        val card = gameState.waste.removeAt(gameState.waste.size - 1)
                        card.isFaceUp = false
                        gameState.stock.add(card)
                    }
                    gameState.undoStack.add(GameCommand.RecycleWaste(count))
                }
                gameState.moves++
                eventListener?.playSound(com.qoneqo.solitaire.R.raw.card_deal)
                eventListener?.onMovesChanged(gameState.moves)
            }
            return true
        }

        // 2. Tableaus
        for (i in 6 downTo 0) {
            val tableau = gameState.tableaus[i]
            for (j in tableau.indices.reversed()) {
                val card = tableau[j]
                val cardY = layout.tableauY + j * am.verticalOffset
                val cardBottomY = if (j == tableau.lastIndex) cardY + am.cardHeight else cardY + am.verticalOffset
                
                if (card.isFaceUp && x >= layout.tableauX[i] && x <= layout.tableauX[i] + am.cardWidth &&
                    y >= cardY && y <= cardBottomY) {
                    
                    // Double Tap Check
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT && lastTapCard == card && j == tableau.lastIndex) {
                        if (tryAutoMoveToFoundation(card, 2, i, gameState, layout)) {
                            lastTapTime = 0
                            return true
                        }
                    }
                    lastTapTime = currentTime
                    lastTapCard = card

                    activeCardStack = tableau.subList(j, tableau.size).toList()
                    sourcePileType = 2
                    sourcePileIndex = i
                    
                    activeCardStack?.forEachIndexed { index, c ->
                        c.originalX = c.renderX
                        c.originalY = c.renderY
                        c.touchOffsetX = x - c.renderX
                        c.touchOffsetY = y - c.renderY
                    }
                    return true
                }
            }
        }

        // 3. Waste
        if (gameState.waste.isNotEmpty()) {
            val card = gameState.waste.last()
            if (x >= layout.wasteX && x <= layout.wasteX + am.cardWidth && y >= layout.wasteY && y <= layout.wasteY + am.cardHeight) {
                // Double Tap Check
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT && lastTapCard == card) {
                    if (tryAutoMoveToFoundation(card, 0, -1, gameState, layout)) {
                        lastTapTime = 0
                        return true
                    }
                }
                lastTapTime = currentTime
                lastTapCard = card

                activeCardStack = listOf(card)
                sourcePileType = 0
                sourcePileIndex = -1
                card.originalX = card.renderX
                card.originalY = card.renderY
                card.touchOffsetX = x - card.renderX
                card.touchOffsetY = y - card.renderY
                return true
            }
        }
        
        return false
    }

    private fun handleActionUp(x: Float, y: Float, gameState: GameState, layout: GameLayout): Boolean {
        val stack = activeCardStack ?: return false
        val card = stack.first()
        var moved = false

        // Try drop on foundations
        if (stack.size == 1) {
            for (i in 0 until 4) {
                if (x >= layout.foundationX[i] && x <= layout.foundationX[i] + am.cardWidth &&
                    y >= layout.foundationY && y <= layout.foundationY + am.cardHeight) {
                    if (SolitaireRules.canMoveToFoundation(card, gameState.foundations[i])) {
                        executeMove(stack, sourcePileType, sourcePileIndex, 1, i, gameState, layout)
                        moved = true
                        break
                    }
                }
            }
        }

        // Try drop on tableaus
        if (!moved) {
            for (i in 0 until 7) {
                if (x >= layout.tableauX[i] && x <= layout.tableauX[i] + am.cardWidth) {
                    val tableau = gameState.tableaus[i]
                    if (SolitaireRules.canMoveToTableau(card, tableau)) {
                        executeMove(stack, sourcePileType, sourcePileIndex, 2, i, gameState, layout)
                        moved = true
                        break
                    }
                }
            }
        }

        if (!moved) {
            stack.forEach { it.isSnappingBack = true }
        }

        activeCardStack = null
        return true
    }

    private fun tryAutoMoveToFoundation(card: Card, fromType: Int, fromIdx: Int, gameState: GameState, layout: GameLayout): Boolean {
        for (i in 0 until 4) {
            if (SolitaireRules.canMoveToFoundation(card, gameState.foundations[i])) {
                executeMove(listOf(card), fromType, fromIdx, 1, i, gameState, layout)
                return true
            }
        }
        return false
    }

    private fun executeMove(
        stack: List<Card>,
        fromType: Int, fromIdx: Int,
        toType: Int, toIdx: Int,
        gameState: GameState, layout: GameLayout
    ) {
        queueAction {
            val fromPile = when (fromType) {
                0 -> gameState.waste
                1 -> gameState.foundations[fromIdx]
                2 -> gameState.tableaus[fromIdx]
                else -> mutableListOf()
            }
            
            val toPile = when (toType) {
                1 -> gameState.foundations[toIdx]
                2 -> gameState.tableaus[toIdx]
                else -> mutableListOf()
            }

            // Record for undo
            val wasFaceDown = fromType == 2 && fromPile.size > stack.size && !fromPile[fromPile.size - stack.size - 1].isFaceUp
            
            // Remove from source
            repeat(stack.size) { fromPile.removeAt(fromPile.size - 1) }
            
            // Auto flip card in tableau
            if (fromType == 2 && fromPile.isNotEmpty() && !fromPile.last().isFaceUp) {
                fromPile.last().isFaceUp = true
            }

            // Add to destination
            toPile.addAll(stack)
            
            // Setup snap back animation targets
            stack.forEachIndexed { index, c ->
                c.originalX = when (toType) {
                    1 -> layout.foundationX[toIdx]
                    2 -> layout.tableauX[toIdx]
                    else -> 0f
                }
                c.originalY = when (toType) {
                    1 -> layout.foundationY
                    2 -> layout.tableauY + (toPile.size - stack.size + index) * am.verticalOffset
                    else -> 0f
                }
                c.isSnappingBack = true
            }

            // Record undo command
            gameState.undoStack.add(
                GameCommand.MoveCards(
                    cardIndices = List(stack.size) { it },
                    fromType = fromType, fromIndex = fromIdx,
                    toType = toType, toIndex = toIdx,
                    wasFaceDown = wasFaceDown
                )
            )

            gameState.moves++
            if (toType == 1) gameState.score += GameConfig.SCORE_FOUNDATION
            else if (fromType != 2 && toType == 2) gameState.score += GameConfig.SCORE_TABLEAU

            eventListener?.playSound(com.qoneqo.solitaire.R.raw.card_place)
            eventListener?.onMovesChanged(gameState.moves)
            eventListener?.onScoreChanged(gameState.score)
        }
    }
}
