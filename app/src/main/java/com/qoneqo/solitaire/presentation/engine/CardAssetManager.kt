package com.qoneqo.solitaire.presentation.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.qoneqo.solitaire.domain.Card
import kotlin.math.min

class CardAssetManager(private val context: Context, screenWidth: Int, screenHeight: Int) {

    val cardWidth: Float
    val cardHeight: Float
    val verticalOffset: Float

    val cardBitmaps = mutableMapOf<String, Bitmap>()
    lateinit var cardBackBitmap: Bitmap
    lateinit var emptySlotBitmap: Bitmap

    init {
        // Dynamic card width: Fit 7 columns + margins
        // (screenWidth - (2 * margin_ratio * screenWidth)) / 7
        val maxPossibleWidth = (screenWidth * 0.95f) / 7.2f 
        val heightConstraint = screenHeight * 0.18f // Max 18% of screen height
        
        cardWidth = min(maxPossibleWidth, heightConstraint / 1.4f)
        cardHeight = cardWidth * 1.4f
        verticalOffset = cardHeight / 3.5f
 
        loadAssets()
    }

    private fun loadAssets() {
        val packageName = context.packageName
        val res = context.resources

        // Load 52 cards
        val suits = listOf('c', 'd', 'h', 's')
        val ranks = listOf("a", "2", "3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k")

        for (suit in suits) {
            for (rank in ranks) {
                val cardName = "card_$rank$suit"
                val resId = res.getIdentifier(cardName, "drawable", packageName)
                cardBitmaps[cardName] = loadOrFallback(resId, cardName)
            }
        }

        val backId = res.getIdentifier("card_back", "drawable", packageName)
        cardBackBitmap = loadOrFallback(backId, "card_back", isBack = true)

        createEmptySlotBitmap()
    }

    private fun loadOrFallback(resId: Int, name: String, isBack: Boolean = false): Bitmap {
        if (resId != 0) {
            try {
                val options = BitmapFactory.Options()
                options.inScaled = false
                val original = BitmapFactory.decodeResource(context.resources, resId, options)
                if (original != null) {
                    val scaled = Bitmap.createScaledBitmap(original, cardWidth.toInt(), cardHeight.toInt(), true)
                    original.recycle()
                    return scaled
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback procedural generation
        val bitmap = Bitmap.createBitmap(cardWidth.toInt(), cardHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw card background
        paint.color = if (isBack) Color.BLUE else Color.WHITE
        val rectF = RectF(0f, 0f, cardWidth, cardHeight)
        canvas.drawRoundRect(rectF, 12f, 12f, paint)

        // Draw border
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(rectF, 12f, 12f, paint)

        if (!isBack) {
            paint.style = Paint.Style.FILL
            val isRed = name.endsWith("h") || name.endsWith("d")
            paint.color = if (isRed) Color.RED else Color.BLACK
            paint.textSize = cardWidth / 2f
            paint.textAlign = Paint.Align.CENTER

            val text = name.replace("card_", "").uppercase()
            // simple center text
            val textY = cardHeight / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(text, cardWidth / 2f, textY, paint)
        }

        return bitmap
    }

    private fun createEmptySlotBitmap() {
        emptySlotBitmap = Bitmap.createBitmap(cardWidth.toInt(), cardHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(emptySlotBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Fill background with #c2d0cd
        paint.color = Color.parseColor("#c2d0cd")
        paint.style = Paint.Style.FILL
        val rectF = RectF(0f, 0f, cardWidth, cardHeight)
        canvas.drawRoundRect(rectF, 16f, 16f, paint)
        
        // Draw border
        paint.color = Color.argb(60, 0, 0, 0)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(rectF, 16f, 16f, paint)
    }

    fun getCardBitmap(card: Card): Bitmap {
        if (!card.isFaceUp) return cardBackBitmap
        val name = "card_${card.rank.letter}${card.suit.letter}"
        return cardBitmaps[name] ?: cardBackBitmap
    }
}
