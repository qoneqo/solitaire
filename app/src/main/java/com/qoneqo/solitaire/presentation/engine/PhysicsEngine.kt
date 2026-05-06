package com.qoneqo.solitaire.presentation.engine

import com.qoneqo.solitaire.domain.Card
import com.qoneqo.solitaire.domain.GameState
import com.qoneqo.solitaire.domain.SolitaireRules
import kotlin.math.abs

class PhysicsEngine(private val eventListener: GameEventListener?) {

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
    }
}
