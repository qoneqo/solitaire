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
        hintedCard: Card? = null,
        hintTimer: Float = 0f,
        hintedSourceX: Float = 0f,
        hintedSourceY: Float = 0f,
        hintedTargetX: Float = 0f,
        hintedTargetY: Float = 0f,
        particles: List<Particle> = emptyList(),
        cascadingCards: List<CascadingCard> = emptyList()
    ) {
        canvas.drawColor(Color.parseColor(GameConfig.BACKGROUND_COLOR))

        // 1. Draw empty slots and tableau borders
        canvas.drawBitmap(am.emptySlotBitmap, Math.round(layout.stockX).toFloat(), Math.round(layout.stockY).toFloat(), renderPaint)
        canvas.drawBitmap(am.emptySlotBitmap, Math.round(layout.wasteX).toFloat(), Math.round(layout.wasteY).toFloat(), renderPaint)
        
        for (i in 0 until 4) {
            canvas.drawBitmap(am.emptySlotBitmap, Math.round(layout.foundationX[i]).toFloat(), Math.round(layout.foundationY).toFloat(), renderPaint)
        }
        
        for (i in 0 until 7) {
            // Precise integer-aligned rect for the tableau column
            val tx = Math.round(layout.tableauX[i]).toFloat()
            val ty = Math.round(layout.tableauY).toFloat()
            val tw = Math.round(am.cardWidth).toFloat()
            
            val tableauRect = RectF(
                tx - 4f,
                ty - 4f,
                tx + tw + 4f,
                canvas.height.toFloat() - 40f
            )
            canvas.drawRoundRect(tableauRect, 16f, 16f, borderPaint)
            canvas.drawBitmap(am.emptySlotBitmap, tx, ty, renderPaint)
        }

        // 2. Draw card stacks (except active cards)
        drawStack(canvas, gameState.stock, activeCardStack, hintedCard)
        drawStack(canvas, gameState.waste, activeCardStack, hintedCard)
        gameState.foundations.forEach { drawStack(canvas, it, activeCardStack, hintedCard) }
        gameState.tableaus.forEach { drawStack(canvas, it, activeCardStack, hintedCard) }

        // 3. Draw active cards on top
        activeCardStack?.forEach { card ->
            canvas.drawBitmap(am.getCardBitmap(card), card.renderX, card.renderY, renderPaint)
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
            
            // Draw highlight on source
            canvas.drawRoundRect(
                hintedSourceX - 5f, hintedSourceY - 5f, 
                hintedSourceX + am.cardWidth + 5f, hintedSourceY + am.cardHeight + 5f, 
                16f, 16f, highlightPaint
            )
            
            // Draw highlight on target
            canvas.drawRoundRect(
                hintedTargetX - 5f, hintedTargetY - 5f, 
                hintedTargetX + am.cardWidth + 5f, hintedTargetY + am.cardHeight + 5f, 
                16f, 16f, highlightPaint
            )
            
            // Draw connecting line/arrow
            highlightPaint.strokeWidth = 4f
            canvas.drawLine(
                hintedSourceX + am.cardWidth / 2f, hintedSourceY + am.cardHeight / 2f,
                hintedTargetX + am.cardWidth / 2f, hintedTargetY + am.cardHeight / 2f,
                highlightPaint
            )
            highlightPaint.strokeWidth = 6f // reset
        }
    }

    private fun drawStack(canvas: Canvas, stack: List<Card>, activeCardStack: List<Card>?, hintedCard: Card?) {
        for (card in stack) {
            if (activeCardStack?.contains(card) != true) {
                canvas.drawBitmap(am.getCardBitmap(card), card.renderX, card.renderY, renderPaint)
                
                if (card == hintedCard) {
                    val rect = RectF(card.renderX, card.renderY, card.renderX + am.cardWidth, card.renderY + am.cardHeight)
                    canvas.drawRoundRect(rect, 10f, 10f, highlightPaint)
                }
            }
        }
    }
}

data class GameLayout(
    val stockX: Float, val stockY: Float,
    val wasteX: Float, val wasteY: Float,
    val foundationX: FloatArray, val foundationY: Float,
    val tableauX: FloatArray, val tableauY: Float
)
