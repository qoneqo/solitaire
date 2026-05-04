package com.qoneqo.solitaire.presentation.engine

import com.qoneqo.solitaire.domain.Card
import com.qoneqo.solitaire.domain.GameState
import com.qoneqo.solitaire.domain.SolitaireRules
import kotlin.math.abs

class PhysicsEngine(private val eventListener: GameEventListener?) {
    private var autoCompleteTimer = 0f

    fun update(dt: Float, gameState: GameState, layout: GameLayout, am: CardAssetManager) {
        val allCards = sequence {
            yieldAll(gameState.stock)
            yieldAll(gameState.waste)
            gameState.foundations.forEach { yieldAll(it) }
            gameState.tableaus.forEach { yieldAll(it) }
        }

        var isAnimating = false
        for (card in allCards) {
            if (card.isSnappingBack) {
                isAnimating = true
                card.renderX += (card.originalX - card.renderX) * GameConfig.SNAP_SPEED * dt
                card.renderY += (card.originalY - card.renderY) * GameConfig.SNAP_SPEED * dt
                if (abs(card.renderX - card.originalX) < 1f && abs(card.renderY - card.originalY) < 1f) {
                    card.renderX = card.originalX
                    card.renderY = card.originalY
                    card.isSnappingBack = false
                }
            }
        }

        // Auto-complete logic
        if (!isAnimating && SolitaireRules.canAutoComplete(gameState) && !gameState.foundations.all { it.size == 13 }) {
            autoCompleteTimer += dt
            if (autoCompleteTimer >= GameConfig.AUTO_COMPLETE_DELAY) {
                autoCompleteTimer = 0f
                performAutoCompleteStep(gameState, layout)
            }
        }
    }

    private fun performAutoCompleteStep(gameState: GameState, layout: GameLayout) {
        for (i in 0 until 7) {
            val tableau = gameState.tableaus[i]
            if (tableau.isNotEmpty()) {
                val card = tableau.last()
                for (j in 0 until 4) {
                    if (SolitaireRules.canMoveToFoundation(card, gameState.foundations[j])) {
                        tableau.removeAt(tableau.size - 1)
                        gameState.foundations[j].add(card)
                        
                        card.originalX = layout.foundationX[j]
                        card.originalY = layout.foundationY
                        card.isSnappingBack = true
                        
                        eventListener?.playSound(com.qoneqo.solitaire.R.raw.card_place)
                        gameState.score += GameConfig.SCORE_FOUNDATION
                        gameState.moves++
                        eventListener?.onScoreChanged(gameState.score)
                        eventListener?.onMovesChanged(gameState.moves)
                        return
                    }
                }
            }
        }
        
        // Also check waste
        if (gameState.waste.isNotEmpty()) {
            val card = gameState.waste.last()
            for (j in 0 until 4) {
                if (SolitaireRules.canMoveToFoundation(card, gameState.foundations[j])) {
                    gameState.waste.removeAt(gameState.waste.size - 1)
                    gameState.foundations[j].add(card)
                    card.originalX = layout.foundationX[j]
                    card.originalY = layout.foundationY
                    card.isSnappingBack = true
                    eventListener?.playSound(com.qoneqo.solitaire.R.raw.card_place)
                    gameState.score += GameConfig.SCORE_FOUNDATION
                    gameState.moves++
                    eventListener?.onScoreChanged(gameState.score)
                    eventListener?.onMovesChanged(gameState.moves)
                    return
                }
            }
        }
    }
}
