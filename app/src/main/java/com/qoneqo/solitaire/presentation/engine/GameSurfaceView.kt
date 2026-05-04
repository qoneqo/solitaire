package com.qoneqo.solitaire.presentation.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.qoneqo.solitaire.domain.Card
import com.qoneqo.solitaire.domain.GameState
import com.qoneqo.solitaire.domain.Rank
import com.qoneqo.solitaire.domain.SolitaireRules
import com.qoneqo.solitaire.domain.Suit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import android.util.AttributeSet

class GameSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private var gameThread: GameThread? = null
    var gameState: GameState = GameState()
    val gameStateLock = Any()
    private var assetManager: CardAssetManager? = null
    var gameEventListener: GameEventListener? = null

    private val renderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Layout coordinates
    private var stockX = 0f
    private var stockY = 0f
    private var wasteX = 0f
    private var wasteY = 0f
    private val foundationX = FloatArray(4)
    private var foundationY = 0f
    private val tableauX = FloatArray(7)
    private var tableauY = 0f

    // Input state
    private var activeCardStack: List<Card>? = null
    private var sourcePileType: Int = -1 // 0: waste, 1: foundation, 2: tableau
    private var sourcePileIndex: Int = -1

    init {
        holder.addCallback(this)
        isFocusable = true
        setupNewGame()
    }

    fun setupNewGame() {
        synchronized(gameStateLock) {
            gameState = GameState()
            val deck = mutableListOf<Card>()
            for (suit in Suit.values()) {
                for (rank in Rank.values()) {
                    deck.add(Card(suit, rank, false))
                }
            }
            deck.shuffle()

            for (i in 0 until 7) {
                for (j in 0..i) {
                    val card = deck.removeLast()
                    if (j == i) card.isFaceUp = true
                    gameState.tableaus[i].add(card)
                }
            }
            gameState.stock.addAll(deck)
            updateCardTargets()
        }
    }

    fun loadGameState(state: GameState) {
        synchronized(gameStateLock) {
            gameState = state
            updateCardTargets()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val width = width
        val height = height
        assetManager = CardAssetManager(context, width, height)
        calculateLayout(width.toFloat(), height.toFloat())
        
        gameThread = GameThread(holder, this)
        gameThread?.isRunning = true
        gameThread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        assetManager = CardAssetManager(context, width, height)
        calculateLayout(width.toFloat(), height.toFloat())
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        gameThread?.isRunning = false
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    private fun calculateLayout(w: Float, h: Float) {
        val am = assetManager ?: return
        val marginX = w * 0.02f
        val marginY = h * 0.05f
        val spacingX = (w - (2 * marginX) - (7 * am.cardWidth)) / 6f

        // Top row: stock, waste, gap, 4 foundations
        // Adjusted stockY down to avoid overlap with new header and stats row tiles
        stockY = marginY + (h * 0.18f) 
        stockX = marginX
        wasteY = stockY
        wasteX = stockX + am.cardWidth + spacingX

        for (i in 0 until 4) {
            foundationX[i] = marginX + (i + 3) * (am.cardWidth + spacingX)
        }
        foundationY = stockY

        // Bottom row: 7 tableaus
        tableauY = stockY + am.cardHeight + marginY
        for (i in 0 until 7) {
            tableauX[i] = marginX + i * (am.cardWidth + spacingX)
        }
        
        synchronized(gameStateLock) {
            updateCardTargets()
            // Snap them immediately to the layout positions initially
            val allCards = sequence {
                yieldAll(gameState.stock)
                yieldAll(gameState.waste)
                gameState.foundations.forEach { yieldAll(it) }
                gameState.tableaus.forEach { yieldAll(it) }
            }
            for (card in allCards) {
                card.originalX = card.renderX
                card.originalY = card.renderY
            }
        }
    }

    private fun updateCardTargets() {
        val am = assetManager ?: return
        
        // Stock
        gameState.stock.forEach { 
            if (!it.isSnappingBack && activeCardStack?.contains(it) != true) {
                it.renderX = stockX
                it.renderY = stockY
            }
        }
        // Waste
        gameState.waste.forEachIndexed { _, card ->
            if (!card.isSnappingBack && activeCardStack?.contains(card) != true) {
                card.renderX = wasteX
                card.renderY = wasteY
            }
        }
        // Foundations
        for (i in 0 until 4) {
            gameState.foundations[i].forEach { card ->
                if (!card.isSnappingBack && activeCardStack?.contains(card) != true) {
                    card.renderX = foundationX[i]
                    card.renderY = foundationY
                }
            }
        }
        // Tableaus
        for (i in 0 until 7) {
            gameState.tableaus[i].forEachIndexed { index, card ->
                if (!card.isSnappingBack && activeCardStack?.contains(card) != true) {
                    card.renderX = tableauX[i]
                    card.renderY = tableauY + index * am.verticalOffset
                }
            }
        }
    }

    private var autoCompleteTimer = 0f

    fun updatePhysics(dt: Float) {
        synchronized(gameStateLock) {
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
                    card.renderX += (card.originalX - card.renderX) * 15f * dt
                    card.renderY += (card.originalY - card.renderY) * 15f * dt
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
                if (autoCompleteTimer >= 0.1f) {
                    autoCompleteTimer = 0f
                    performAutoCompleteStep()
                }
            }
        }
    }

    private fun performAutoCompleteStep() {
        for (i in 0 until 7) {
            val tableau = gameState.tableaus[i]
            if (tableau.isNotEmpty()) {
                val card = tableau.last()
                for (j in 0 until 4) {
                    if (SolitaireRules.canMoveToFoundation(card, gameState.foundations[j])) {
                        tableau.removeLast()
                        gameState.foundations[j].add(card)
                        
                        // Setup snap back animation to fly to foundation
                        card.originalX = foundationX[j]
                        card.originalY = foundationY
                        card.isSnappingBack = true
                        
                        gameEventListener?.playSound(com.qoneqo.solitaire.R.raw.card_place)
                        gameState.score += 10
                        gameState.moves++
                        gameEventListener?.onScoreChanged(gameState.score)
                        gameEventListener?.onMovesChanged(gameState.moves)
                        checkWinCondition()
                        return
                    }
                }
            }
        }
    }

    fun render(canvas: Canvas) {
        val am = assetManager ?: return
        canvas.drawColor(Color.parseColor("#9fb5b0"))

        // Draw empty slots and tableau borders
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4D000000") // Subtle dark border
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        canvas.drawBitmap(am.emptySlotBitmap, stockX, stockY, renderPaint)
        canvas.drawBitmap(am.emptySlotBitmap, wasteX, wasteY, renderPaint)
        for (i in 0 until 4) {
            canvas.drawBitmap(am.emptySlotBitmap, foundationX[i], foundationY, renderPaint)
        }
        for (i in 0 until 7) {
            // Draw a subtle border for the tableau column area
            val tableauRect = RectF(
                tableauX[i] - 4f, 
                tableauY - 4f, 
                tableauX[i] + am.cardWidth + 4f, 
                height.toFloat() - 40f
            )
            canvas.drawRoundRect(tableauRect, 16f, 16f, borderPaint)
            
            canvas.drawBitmap(am.emptySlotBitmap, tableauX[i], tableauY, renderPaint)
        }

        // Helper to draw a stack, except active cards
        fun drawStack(stack: List<Card>) {
            for (card in stack) {
                if (activeCardStack?.contains(card) != true) {
                    canvas.drawBitmap(am.getCardBitmap(card), card.renderX, card.renderY, renderPaint)
                }
            }
        }

        // Draw stock (only top card really needs to be drawn if not empty, or a few for depth)
        drawStack(gameState.stock)
        drawStack(gameState.waste)
        for (f in gameState.foundations) drawStack(f)
        for (t in gameState.tableaus) drawStack(t)

        // Draw active cards last (on top)
        activeCardStack?.forEach { card ->
            canvas.drawBitmap(am.getCardBitmap(card), card.renderX, card.renderY, renderPaint)
        }
    }

    private fun saveUndoState() {
        val stateJson = Json.encodeToString(gameState.copy(undoStack = ArrayList()))
        gameState.undoStack.add(stateJson)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val am = assetManager ?: return false
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                synchronized(gameStateLock) {
                    // Check stock tap
                    if (x >= stockX && x <= stockX + am.cardWidth && y >= stockY && y <= stockY + am.cardHeight) {
                        saveUndoState()
                        if (gameState.stock.isNotEmpty()) {
                            val card = gameState.stock.removeLast()
                            card.isFaceUp = true
                            gameState.waste.add(card)
                        } else {
                            while (gameState.waste.isNotEmpty()) {
                                val card = gameState.waste.removeLast()
                                card.isFaceUp = false
                                gameState.stock.add(card)
                            }
                        }
                        gameEventListener?.playSound(com.qoneqo.solitaire.R.raw.card_deal)
                        updateCardTargets()
                        return true
                    }

                    // Hit testing from top to bottom (visually)
                    // 1. Tableaus (can pick up multiple face up cards)
                    for (i in 6 downTo 0) {
                        val tableau = gameState.tableaus[i]
                        for (j in tableau.indices.reversed()) {
                            val card = tableau[j]
                            val cardY = tableauY + j * am.verticalOffset
                            val cardBottomY = if (j == tableau.lastIndex) cardY + am.cardHeight else cardY + am.verticalOffset
                            
                            if (card.isFaceUp && x >= tableauX[i] && x <= tableauX[i] + am.cardWidth &&
                                y >= cardY && y <= cardBottomY) {
                                
                                activeCardStack = tableau.subList(j, tableau.size).toList()
                                sourcePileType = 2
                                sourcePileIndex = i
                                
                                activeCardStack?.forEachIndexed { index, c ->
                                    c.touchOffsetX = x - c.renderX
                                    c.touchOffsetY = y - c.renderY
                                }
                                return true
                            }
                        }
                    }

                    // 2. Waste (only top card)
                    if (gameState.waste.isNotEmpty()) {
                        val card = gameState.waste.last()
                        if (x >= wasteX && x <= wasteX + am.cardWidth && y >= wasteY && y <= wasteY + am.cardHeight) {
                            activeCardStack = listOf(card)
                            sourcePileType = 0
                            sourcePileIndex = -1
                            card.touchOffsetX = x - card.renderX
                            card.touchOffsetY = y - card.renderY
                            return true
                        }
                    }

                    // 3. Foundations (only top card, though usually not moved back)
                    for (i in 0 until 4) {
                        if (gameState.foundations[i].isNotEmpty()) {
                            val card = gameState.foundations[i].last()
                            if (x >= foundationX[i] && x <= foundationX[i] + am.cardWidth &&
                                y >= foundationY && y <= foundationY + am.cardHeight) {
                                activeCardStack = listOf(card)
                                sourcePileType = 1
                                sourcePileIndex = i
                                card.touchOffsetX = x - card.renderX
                                card.touchOffsetY = y - card.renderY
                                return true
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                synchronized(gameStateLock) {
                    activeCardStack?.forEach { card ->
                        card.renderX = x - card.touchOffsetX
                        card.renderY = y - card.touchOffsetY
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                synchronized(gameStateLock) {
                    activeCardStack?.let { stack ->
                        val topCard = stack.first()
                        val centerX = topCard.renderX + am.cardWidth / 2f
                        val centerY = topCard.renderY + am.cardHeight / 2f

                        var dropped = false

                        // Check Foundations (only if single card)
                        if (stack.size == 1) {
                            for (i in 0 until 4) {
                                if (centerX >= foundationX[i] && centerX <= foundationX[i] + am.cardWidth &&
                                    centerY >= foundationY && centerY <= foundationY + am.cardHeight) {
                                    
                                    if (SolitaireRules.canMoveToFoundation(topCard, gameState.foundations[i])) {
                                        saveUndoState()
                                        removeActiveStackFromSource()
                                        gameState.foundations[i].add(topCard)
                                        dropped = true
                                        
                                        // Score
                                        gameState.score += 10
                                        if (sourcePileType == 2) gameState.score += 5 // Extra points for moving from tableau
                                        
                                        break
                                    }
                                }
                            }
                        }

                        // Check Tableaus
                        if (!dropped) {
                            for (i in 0 until 7) {
                                // Find bottom bounds of tableau
                                val bottomY = tableauY + Math.max(0, gameState.tableaus[i].size - 1) * am.verticalOffset
                                if (centerX >= tableauX[i] && centerX <= tableauX[i] + am.cardWidth &&
                                    centerY >= tableauY && centerY <= bottomY + am.cardHeight) {
                                    
                                    if (SolitaireRules.canMoveToTableau(topCard, gameState.tableaus[i])) {
                                        saveUndoState()
                                        removeActiveStackFromSource()
                                        gameState.tableaus[i].addAll(stack)
                                        dropped = true
                                        
                                        // Score
                                        if (sourcePileType == 0) gameState.score += 5
                                        if (sourcePileType == 1) gameState.score -= 15
                                        break
                                    }
                                }
                            }
                        }

                        if (dropped) {
                            gameState.moves++
                            flipTopCardOfSource()
                            gameEventListener?.playSound(com.qoneqo.solitaire.R.raw.card_place)
                            gameEventListener?.onScoreChanged(gameState.score)
                            gameEventListener?.onMovesChanged(gameState.moves)
                            checkWinCondition()
                        } else {
                            // Snap back
                            stack.forEach { c ->
                                c.originalX = c.renderX
                                c.originalY = c.renderY
                                c.isSnappingBack = true
                                // Calculate where they should snap back to
                                val idx = activeCardStack!!.indexOf(c)
                                when (sourcePileType) {
                                    0 -> { c.originalX = wasteX; c.originalY = wasteY }
                                    1 -> { c.originalX = foundationX[sourcePileIndex]; c.originalY = foundationY }
                                    2 -> { 
                                        c.originalX = tableauX[sourcePileIndex]
                                        val baseSize = gameState.tableaus[sourcePileIndex].size - stack.size
                                        c.originalY = tableauY + (baseSize + idx) * am.verticalOffset 
                                    }
                                }
                            }
                        }

                        activeCardStack = null
                        if (dropped) updateCardTargets()
                    }
                }
            }
        }
        return true
    }

    private fun removeActiveStackFromSource() {
        val stackSize = activeCardStack!!.size
        when (sourcePileType) {
            0 -> gameState.waste.removeLast()
            1 -> gameState.foundations[sourcePileIndex].removeLast()
            2 -> {
                val tableau = gameState.tableaus[sourcePileIndex]
                for (i in 0 until stackSize) {
                    tableau.removeLast()
                }
            }
        }
    }

    private fun flipTopCardOfSource() {
        if (sourcePileType == 2) {
            val tableau = gameState.tableaus[sourcePileIndex]
            if (tableau.isNotEmpty() && !tableau.last().isFaceUp) {
                tableau.last().isFaceUp = true
            }
        }
    }

    private fun checkWinCondition() {
        val won = gameState.foundations.all { it.size == 13 }
        if (won) {
            gameEventListener?.onGameWon()
        }
    }
}
