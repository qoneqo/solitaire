package com.qoneqo.solitaire.presentation.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.qoneqo.solitaire.domain.Card
import com.qoneqo.solitaire.domain.GameState

class WorldRenderer(private val am: CardAssetManager) {
    private val renderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(GameConfig.TABLEAU_BORDER_COLOR)
        style = Paint.Style.STROKE
        strokeWidth = GameConfig.UI_BORDER_WIDTH
    }
    
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 6f
        alpha = 150
    }

    fun render(
        canvas: Canvas, 
        gameState: GameState, 
        layout: GameLayout, 
        activeCardStack: List<Card>?,
        selectedStack: List<Card>? = null,
        hintedCard: Card? = null,
        hintTimer: Float = 0f,
        hintedSourceX: Float = 0f,
        hintedSourceY: Float = 0f,
        hintedTargetX: Float = 0f,
        hintedTargetY: Float = 0f,
        particles: List<Particle> = emptyList(),
        cascadingCards: List<CascadingCard> = emptyList(),
        tableColor: Int? = null,
        scrollOffsetY: Float = 0f
    ) {
        canvas.drawColor(tableColor ?: Color.parseColor(GameConfig.BACKGROUND_COLOR))

        // --- 1. Tableau Section (Scrollable & Clipped under HUD) ---
        canvas.save()
        // Clip to area below the HUD area (with a small top buffer for card rank visibility)
        val hudHeight = layout.tableauY - 20f
        canvas.clipRect(0f, hudHeight, canvas.width.toFloat(), canvas.height.toFloat())
        canvas.translate(0f, -scrollOffsetY)
        
        for (i in 0 until 7) {
            val tx = Math.round(layout.tableauX[i]).toFloat()
            val ty = Math.round(layout.tableauY).toFloat()
            val tw = Math.round(am.cardWidth).toFloat()
            
            // Draw tableau border
            val tableauRect = RectF(
                tx - 4f,
                ty - 4f,
                tx + tw + 4f,
                canvas.height.toFloat() + scrollOffsetY + 400f // Extend border
            )
            canvas.drawRoundRect(tableauRect, 16f, 16f, borderPaint)
            canvas.drawBitmap(am.emptySlotBitmap, tx, ty, renderPaint)
            
            // Draw tableau stacks
            drawStack(canvas, gameState.tableaus[i], activeCardStack, selectedStack, hintedCard)
        }
        canvas.restore()

        // --- 2. HUD Section (Sticky Top) ---
        // HUD Area: Stock, Waste, Foundations
        canvas.drawBitmap(am.emptySlotBitmap, Math.round(layout.stockX).toFloat(), Math.round(layout.stockY).toFloat(), renderPaint)
        canvas.drawBitmap(am.emptySlotBitmap, Math.round(layout.wasteX).toFloat(), Math.round(layout.wasteY).toFloat(), renderPaint)
        for (i in 0 until 4) {
            canvas.drawBitmap(am.emptySlotBitmap, Math.round(layout.foundationX[i]).toFloat(), Math.round(layout.foundationY).toFloat(), renderPaint)
        }
        
        // Draw HUD Stacks (Fixed positions)
        drawStack(canvas, gameState.stock, activeCardStack, selectedStack, hintedCard)
        drawStack(canvas, gameState.waste, activeCardStack, selectedStack, hintedCard)
        gameState.foundations.forEach { drawStack(canvas, it, activeCardStack, selectedStack, hintedCard) }
        
        // Draw a subtle shadow at the bottom of HUD
        borderPaint.color = Color.BLACK
        borderPaint.alpha = 40
        canvas.drawRect(0f, hudHeight, canvas.width.toFloat(), hudHeight + 10f, borderPaint)
        borderPaint.alpha = 255 
        borderPaint.color = Color.WHITE

        // 3. Draw active cards on top (Screen Space)
        activeCardStack?.forEach { card ->
            drawCard(canvas, card, card.renderX, card.renderY)
        }

        // 4. Draw Particles
        particles.forEach { p ->
            renderPaint.color = p.color
            renderPaint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, 6f * (p.life / p.maxLife), renderPaint)
        }
        renderPaint.alpha = 255 // reset

        // 5. Draw Cascading Cards
        cascadingCards.forEach { c ->
            val bitmap = am.getCardBitmap(c.suit, c.rank)
            canvas.drawBitmap(bitmap, c.x, c.y, renderPaint)
        }

        // 6. Draw Hint
        if (hintedCard != null && hintTimer > 0) {
            val alpha = (Math.min(1.0f, hintTimer) * 150).toInt()
            highlightPaint.alpha = alpha
            
            // Adjust hint Y if it's in the tableau area
            val sy = if (hintedSourceY >= hudHeight) hintedSourceY - scrollOffsetY else hintedSourceY
            val ty = if (hintedTargetY >= hudHeight) hintedTargetY - scrollOffsetY else hintedTargetY
            
            // Only draw hint if not clipped away
            if (sy >= hudHeight - am.cardHeight || ty >= hudHeight - am.cardHeight) {
                // Draw highlight on source
                canvas.drawRoundRect(
                    hintedSourceX - 5f, sy - 5f, 
                    hintedSourceX + am.cardWidth + 5f, sy + am.cardHeight + 5f, 
                    16f, 16f, highlightPaint
                )
                
                // Draw highlight on target
                canvas.drawRoundRect(
                    hintedTargetX - 5f, ty - 5f, 
                    hintedTargetX + am.cardWidth + 5f, ty + am.cardHeight + 5f, 
                    16f, 16f, highlightPaint
                )
                
                // Draw connecting line
                highlightPaint.strokeWidth = 4f
                canvas.drawLine(
                    hintedSourceX + am.cardWidth / 2f, sy + am.cardHeight / 2f,
                    hintedTargetX + am.cardWidth / 2f, ty + am.cardHeight / 2f,
                    highlightPaint
                )
                highlightPaint.strokeWidth = 6f
            }
        }
    }

    private fun drawStack(canvas: Canvas, stack: List<Card>, activeCardStack: List<Card>?, selectedStack: List<Card>?, hintedCard: Card?) {
        for (card in stack) {
            if (activeCardStack?.contains(card) != true) {
                drawCard(canvas, card, card.renderX, card.renderY)
                
                if (selectedStack?.contains(card) == true) {
                    val rect = RectF(card.renderX, card.renderY, card.renderX + am.cardWidth, card.renderY + am.cardHeight)
                    highlightPaint.alpha = 200
                    highlightPaint.color = Color.CYAN
                    canvas.drawRoundRect(rect, 10f, 10f, highlightPaint)
                    // Reset paint
                    highlightPaint.alpha = 150
                    highlightPaint.color = Color.YELLOW
                }

                if (card == hintedCard) {
                    val rect = RectF(card.renderX, card.renderY, card.renderX + am.cardWidth, card.renderY + am.cardHeight)
                    canvas.drawRoundRect(rect, 10f, 10f, highlightPaint)
                }
            }
        }
    }

    private fun drawCard(canvas: Canvas, card: Card, x: Float, y: Float) {
        val bitmap = am.getCardBitmap(card)
        if (card.scale != 1.0f) {
            canvas.save()
            canvas.translate(x + am.cardWidth / 2f, y + am.cardHeight / 2f)
            canvas.scale(card.scale, card.scale)
            canvas.drawBitmap(bitmap, -am.cardWidth / 2f, -am.cardHeight / 2f, renderPaint)
            canvas.restore()
        } else {
            canvas.drawBitmap(bitmap, x, y, renderPaint)
        }
    }
}

data class GameLayout(
    val stockX: Float, val stockY: Float,
    val wasteX: Float, val wasteY: Float,
    val foundationX: FloatArray, val foundationY: Float,
    val tableauX: FloatArray, val tableauY: Float
) {
    /**
     * Calculates the vertical offset for cards in a tableau pile based on its size
     * to ensure the entire pile fits on the screen.
     */
    fun getTableauOffset(pileSize: Int, screenHeight: Float, cardHeight: Float, defaultOffset: Float): Float {
        if (pileSize <= 1) return defaultOffset
        
        // We want to keep the cards neat, so we use defaultOffset as much as possible.
        // We only compress if the pile is truly enormous (e.g. 20+ cards) and we want to 
        // keep it within a reasonable scrollable range.
        // For standard games, defaultOffset is perfect.
        
        val maxTotalHeight = screenHeight * 2.0f // Allow up to 2 screens of content
        val availableSpace = maxTotalHeight - tableauY - cardHeight
        
        val calculatedOffset = availableSpace / (pileSize - 1)
        
        // Return defaultOffset but don't let it exceed defaultOffset (keep it neat)
        // and don't let it go below 40% of default (keep it readable)
        return Math.min(defaultOffset, Math.max(defaultOffset * 0.4f, calculatedOffset))
    }
}
