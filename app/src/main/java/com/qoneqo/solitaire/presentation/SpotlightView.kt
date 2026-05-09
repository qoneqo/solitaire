package com.qoneqo.solitaire.presentation

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class SpotlightView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eraser = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var spotlightX = 0f
    private var spotlightY = 0f
    private var spotlightRadius = 0f
    private var spotlightRect: RectF? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setSpotlight(x: Float, y: Float, radius: Float) {
        spotlightX = x
        spotlightY = y
        spotlightRadius = radius
        spotlightRect = null
        invalidate()
    }

    fun setSpotlightRect(rect: RectF) {
        spotlightRect = rect
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw the dim background
        paint.color = Color.parseColor("#CC000000")
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Erase the spotlight area
        val rect = spotlightRect
        if (rect != null) {
            canvas.drawRoundRect(rect, 24f, 24f, eraser)
        } else if (spotlightRadius > 0) {
            canvas.drawCircle(spotlightX, spotlightY, spotlightRadius, eraser)
        }
    }
}
