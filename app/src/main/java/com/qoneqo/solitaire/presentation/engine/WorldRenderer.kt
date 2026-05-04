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
        strokeWidth = 2f
    }

    fun render(
        canvas: Canvas, 
        gameState: GameState, 
        layout: GameLayout, 
        activeCardStack: List<Card>?
    ) {
        canvas.drawColor(Color.parseColor(GameConfig.BACKGROUND_COLOR))

        // 1. Draw empty slots and tableau borders
        canvas.drawBitmap(am.emptySlotBitmap, layout.stockX, layout.stockY, renderPaint)
        canvas.drawBitmap(am.emptySlotBitmap, layout.wasteX, layout.wasteY, renderPaint)
        
        for (i in 0 until 4) {
            canvas.drawBitmap(am.emptySlotBitmap, layout.foundationX[i], layout.foundationY, renderPaint)
        }
        
        for (i in 0 until 7) {
            // Draw tableau column area border
            val tableauRect = RectF(
                layout.tableauX[i] - 4f,
                layout.tableauY - 4f,
                layout.tableauX[i] + am.cardWidth + 4f,
                canvas.height.toFloat() - 40f
            )
            canvas.drawRoundRect(tableauRect, 16f, 16f, borderPaint)
            canvas.drawBitmap(am.emptySlotBitmap, layout.tableauX[i], layout.tableauY, renderPaint)
        }

        // 2. Draw card stacks (except active cards)
        drawStack(canvas, gameState.stock, activeCardStack)
        drawStack(canvas, gameState.waste, activeCardStack)
        gameState.foundations.forEach { drawStack(canvas, it, activeCardStack) }
        gameState.tableaus.forEach { drawStack(canvas, it, activeCardStack) }

        // 3. Draw active cards on top
        activeCardStack?.forEach { card ->
            canvas.drawBitmap(am.getCardBitmap(card), card.renderX, card.renderY, renderPaint)
        }
    }

    private fun drawStack(canvas: Canvas, stack: List<Card>, activeCardStack: List<Card>?) {
        for (card in stack) {
            if (activeCardStack?.contains(card) != true) {
                canvas.drawBitmap(am.getCardBitmap(card), card.renderX, card.renderY, renderPaint)
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
