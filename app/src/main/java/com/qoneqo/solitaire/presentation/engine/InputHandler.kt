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
    
    // Tap-to-Move State
    var selectedStack: List<Card>? = null
    private var selectedPileType = -1
    private var selectedPileIndex = -1
    private var touchDownX = 0f
    private var touchDownY = 0f


    fun onTouchEvent(event: MotionEvent, gameState: GameState, layout: GameLayout, screenHeight: Float, scrollOffsetY: Float): Boolean {
        val x = event.x
        val y = event.y
        val hudHeight = layout.tableauY - 20f

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                return handleActionDown(x, y, gameState, layout, screenHeight, scrollOffsetY)
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeCardStack == null) return false
                val offset = layout.getTableauOffset(activeCardStack!!.size, screenHeight, am.cardHeight, am.verticalOffset)
                activeCardStack?.forEachIndexed { index, card ->
                    card.renderX = x - card.touchOffsetX
                    
                    // Dragged cards follow screen space
                    card.renderY = y - card.touchOffsetY + (index * offset)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                return handleActionUp(x, y, gameState, layout, screenHeight, scrollOffsetY)
            }
        }
        return false
    }

    private fun handleActionDown(x: Float, y: Float, gameState: GameState, layout: GameLayout, screenHeight: Float, scrollOffsetY: Float): Boolean {
        touchDownX = x
        touchDownY = y
        val hudHeight = layout.tableauY - 20f
        val effectiveY = y + scrollOffsetY

        // Check if we are tapping a valid destination for the currently selected stack
        if (selectedStack != null) {
            var destinationFound = false
            
            // Check foundation drop
            if (selectedStack!!.size == 1) {
                for (i in 0 until 4) {
                    if (x >= layout.foundationX[i] && x <= layout.foundationX[i] + am.cardWidth &&
                        y >= layout.foundationY && y <= layout.foundationY + am.cardHeight) {
                        if (SolitaireRules.canMoveToFoundation(selectedStack!!.first(), gameState.foundations[i])) {
                            executeMove(selectedStack!!, selectedPileType, selectedPileIndex, 1, i, gameState, layout, screenHeight)
                            destinationFound = true
                            break
                        }
                    }
                }
            }
            
            // Check tableau drop
            if (!destinationFound) {
                for (i in 0 until 7) {
                    if (x >= layout.tableauX[i] && x <= layout.tableauX[i] + am.cardWidth && effectiveY >= layout.tableauY) {
                        val tableau = gameState.tableaus[i]
                        if (SolitaireRules.canMoveToTableau(selectedStack!!.first(), tableau)) {
                            executeMove(selectedStack!!, selectedPileType, selectedPileIndex, 2, i, gameState, layout, screenHeight)
                            destinationFound = true
                            break
                        }
                    }
                }
            }

            if (destinationFound) {
                selectedStack = null
                return true
            }
        }

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
                eventListener?.playSound(com.qoneqo.solitaire.R.raw.paper_slide)
                eventListener?.onMovesChanged(gameState.moves)
            }
            return true
        }

        // 2. Tableaus (Effective Y)
        for (i in 6 downTo 0) {
            val tableau = gameState.tableaus[i]
            for (j in tableau.indices.reversed()) {
                val card = tableau[j]
                val offset = layout.getTableauOffset(tableau.size, screenHeight, am.cardHeight, am.verticalOffset)
                val cardY = layout.tableauY + j * offset
                val cardBottomY = if (j == tableau.lastIndex) cardY + am.cardHeight else cardY + offset
                
                if (card.isFaceUp && x >= layout.tableauX[i] && x <= layout.tableauX[i] + am.cardWidth &&
                    effectiveY >= cardY && effectiveY <= cardBottomY && y >= hudHeight) {
                    
                    activeCardStack = tableau.subList(j, tableau.size).toList()
                    sourcePileType = 2
                    sourcePileIndex = i
                    
                    activeCardStack?.forEachIndexed { _, c ->
                        c.originalX = c.renderX
                        c.originalY = c.renderY
                        c.touchOffsetX = x - c.renderX
                        c.touchOffsetY = y - (c.renderY - scrollOffsetY) 
                    }
                    return true
                }
            }
        }

        // 3. Foundation
        for (i in 0 until 4) {
            val pile = gameState.foundations[i]
            if (pile.isNotEmpty()) {
                val card = pile.last()
                if (x >= layout.foundationX[i] && x <= layout.foundationX[i] + am.cardWidth &&
                    y >= layout.foundationY && y <= layout.foundationY + am.cardHeight) {
                    
                    activeCardStack = listOf(card)
                    sourcePileType = 1
                    sourcePileIndex = i
                    
                    card.originalX = card.renderX
                    card.originalY = card.renderY
                    card.touchOffsetX = x - card.renderX
                    card.touchOffsetY = y - card.renderY
                    return true
                }
            }
        }

        // 4. Waste
        if (gameState.waste.isNotEmpty()) {
            val card = gameState.waste.last()
            if (x >= layout.wasteX && x <= layout.wasteX + am.cardWidth && y >= layout.wasteY && y <= layout.wasteY + am.cardHeight) {
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

    private fun handleActionUp(x: Float, y: Float, gameState: GameState, layout: GameLayout, screenHeight: Float, scrollOffsetY: Float): Boolean {
        val stack = activeCardStack ?: return false
        val effectiveY = y + scrollOffsetY
        val card = stack.first()
        var moved = false
 
        // Try drop on foundations
        if (stack.size == 1) {
            for (i in 0 until 4) {
                if (x >= layout.foundationX[i] && x <= layout.foundationX[i] + am.cardWidth &&
                    y >= layout.foundationY && y <= layout.foundationY + am.cardHeight) {
                    if (SolitaireRules.canMoveToFoundation(card, gameState.foundations[i])) {
                        executeMove(stack, sourcePileType, sourcePileIndex, 1, i, gameState, layout, screenHeight)
                        moved = true
                        break
                    }
                }
            }
        }
 
        // Try drop on tableaus
        if (!moved) {
            for (i in 0 until 7) {
                if (x >= layout.tableauX[i] && x <= layout.tableauX[i] + am.cardWidth && effectiveY >= layout.tableauY) {
                    val tableau = gameState.tableaus[i]
                    if (SolitaireRules.canMoveToTableau(card, tableau)) {
                        executeMove(stack, sourcePileType, sourcePileIndex, 2, i, gameState, layout, screenHeight)
                        moved = true
                        break
                    }
                }
            }
        }
 
        if (!moved) {
            val dx = x - touchDownX
            val dy = y - touchDownY
            val isTap = dx * dx + dy * dy < 400 // Allow slight movement for tap
 
            if (isTap) {
                // Try auto-move
                var autoMoved = false
                
                // 1. Try Foundation
                if (stack.size == 1) {
                    autoMoved = tryAutoMoveToFoundation(card, sourcePileType, sourcePileIndex, gameState, layout, screenHeight)
                }
                
                // 2. Try Tableau
                if (!autoMoved) {
                    for (i in 0 until 7) {
                        if (i != sourcePileIndex || sourcePileType != 2) {
                            val tableau = gameState.tableaus[i]
                            if (SolitaireRules.canMoveToTableau(card, tableau)) {
                                executeMove(stack, sourcePileType, sourcePileIndex, 2, i, gameState, layout, screenHeight)
                                autoMoved = true
                                break
                            }
                        }
                    }
                }

                if (autoMoved) {
                    selectedStack = null
                } else {
                    // If auto-move fails, make it the selected stack
                    selectedStack = stack
                    selectedPileType = sourcePileType
                    selectedPileIndex = sourcePileIndex
                    stack.forEach { it.isSnappingBack = true }
                }
            } else {
                // Was a drag that failed
                selectedStack = null
                stack.forEach { it.isSnappingBack = true }
            }
        } else {
            // Was a successful drag
            selectedStack = null
        }

        activeCardStack = null
        return true
    }

    private fun tryAutoMoveToFoundation(card: Card, fromType: Int, fromIdx: Int, gameState: GameState, layout: GameLayout, screenHeight: Float): Boolean {
        for (i in 0 until 4) {
            if (SolitaireRules.canMoveToFoundation(card, gameState.foundations[i])) {
                executeMove(listOf(card), fromType, fromIdx, 1, i, gameState, layout, screenHeight)
                return true
            }
        }
        return false
    }
 
    private fun executeMove(
        stack: List<Card>,
        fromType: Int, fromIdx: Int,
        toType: Int, toIdx: Int,
        gameState: GameState, layout: GameLayout,
        screenHeight: Float
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
            
            if (fromType == 2 && fromPile.isNotEmpty() && !fromPile.last().isFaceUp) {
                fromPile.last().isFaceUp = true
                eventListener?.playSound(com.qoneqo.solitaire.R.raw.pop)
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
                    2 -> {
                        val offset = layout.getTableauOffset(toPile.size, screenHeight, am.cardHeight, am.verticalOffset)
                        layout.tableauY + (toPile.size - stack.size + index) * offset
                    }
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
            else if (fromType != 2 && toType == 2 && fromType != 1) gameState.score += GameConfig.SCORE_TABLEAU
            else if (fromType == 1 && toType == 2) {
                gameState.score += GameConfig.PENALTY_FOUNDATION_TO_TABLEAU
                if (gameState.score < 0) gameState.score = 0
            }

            val sfx = if (toType == 1) com.qoneqo.solitaire.R.raw.sparkle else com.qoneqo.solitaire.R.raw.pop
            eventListener?.playSound(sfx)
            eventListener?.onMovesChanged(gameState.moves)
            eventListener?.onScoreChanged(gameState.score)
            
            // Trigger Particles
            val targetX = stack.first().originalX + am.cardWidth / 2f
            val targetY = stack.first().originalY + am.cardHeight / 2f
            val pColor = if (toType == 1) android.graphics.Color.YELLOW else android.graphics.Color.WHITE
            eventListener?.onEmitParticles(targetX, targetY, pColor)
        }
    }
}
