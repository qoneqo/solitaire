package com.qoneqo.solitaire.presentation.engine

import com.qoneqo.solitaire.domain.Card
import com.qoneqo.solitaire.domain.GameState
import com.qoneqo.solitaire.domain.SolitaireRules
import kotlin.math.abs

class PhysicsEngine(private val eventListener: GameEventListener?) {

    fun update(dt: Float, gameState: GameState) {
        val allCards = sequence {
            yieldAll(gameState.stock)
            yieldAll(gameState.waste)
            gameState.foundations.forEach { yieldAll(it) }
            gameState.tableaus.forEach { yieldAll(it) }
        }

        for (card in allCards) {
            // Smoothly interpolate scale
            if (abs(card.scale - card.targetScale) > 0.01f) {
                card.scale += (card.targetScale - card.scale) * 10f * dt
            } else {
                card.scale = card.targetScale
            }

            if (card.isSnappingBack) {
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
