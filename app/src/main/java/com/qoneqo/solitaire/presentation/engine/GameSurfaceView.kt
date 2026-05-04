package com.qoneqo.solitaire.presentation.engine

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.qoneqo.solitaire.domain.Card
import com.qoneqo.solitaire.domain.GameState
import com.qoneqo.solitaire.domain.Rank
import com.qoneqo.solitaire.domain.Suit

class GameSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private var gameThread: GameThread? = null
    var gameState: GameState = GameState()
    val gameStateLock = Any()
    private val actionQueue = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()
    private var assetManager: CardAssetManager? = null
    var gameEventListener: GameEventListener? = null

    // Refactored Components
    private lateinit var renderer: WorldRenderer
    private lateinit var physics: PhysicsEngine
    private lateinit var inputHandler: InputHandler
    private var layout: GameLayout? = null
    var uiHeaderHeight: Float = 0f

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
                    val card = deck.removeAt(deck.size - 1)
                    if (j == i) card.isFaceUp = true
                    gameState.tableaus[i].add(card)
                }
            }
            gameState.stock.addAll(deck)
            updateCardPositions()
        }
    }

    fun loadGameState(state: GameState) {
        synchronized(gameStateLock) {
            gameState = state
            updateCardPositions()
        }
    }

    fun undo() {
        runOnGameThread {
            if (gameState.undoStack.isNotEmpty()) {
                val command = gameState.undoStack.removeAt(gameState.undoStack.size - 1)
                command.undo(gameState)
                updateCardPositions()
                gameEventListener?.onScoreChanged(gameState.score)
                gameEventListener?.onMovesChanged(gameState.moves)
            }
        }
    }

    /**
     * Queues a block of code to be executed safely on the Game Thread
     * before the next physics update and render call.
     */
    fun runOnGameThread(action: () -> Unit) {
        actionQueue.add(action)
    }

    private fun updateCardPositions() {
        val am = assetManager ?: return
        val l = layout ?: return
        
        // This keeps cards in sync with their logical piles
        // Stock
        gameState.stock.forEach { card ->
            if (!card.isSnappingBack && inputHandler.activeCardStack?.contains(card) != true) {
                card.renderX = l.stockX
                card.renderY = l.stockY
            }
        }
        // Waste
        gameState.waste.forEach { card ->
            if (!card.isSnappingBack && inputHandler.activeCardStack?.contains(card) != true) {
                card.renderX = l.wasteX
                card.renderY = l.wasteY
            }
        }
        // Foundations
        for (i in 0 until 4) {
            gameState.foundations[i].forEach { card ->
                if (!card.isSnappingBack && inputHandler.activeCardStack?.contains(card) != true) {
                    card.renderX = l.foundationX[i]
                    card.renderY = l.foundationY
                }
            }
        }
        // Tableaus
        for (i in 0 until 7) {
            val pile = gameState.tableaus[i]
            // Adaptive vertical offset: if pile is too long, squish it
            val maxTableauHeight = height.toFloat() - l.tableauY - am.cardHeight - (height * 0.05f)
            val adaptiveOffset = if (pile.size > 1) {
                kotlin.math.min(am.verticalOffset, maxTableauHeight / (pile.size - 1))
            } else {
                am.verticalOffset
            }

            pile.forEachIndexed { index, card ->
                if (!card.isSnappingBack && inputHandler.activeCardStack?.contains(card) != true) {
                    card.renderX = l.tableauX[i]
                    card.renderY = l.tableauY + index * adaptiveOffset
                }
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        initEngine()
        gameThread = GameThread(holder, this)
        gameThread?.isRunning = true
        gameThread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        initEngine()
    }

    fun initEngine() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        
        assetManager = CardAssetManager(context, w.toInt(), h.toInt())
        val am = assetManager!!
        
        // Calculate Layout
        val marginY = h * GameConfig.MARGIN_Y_RATIO
        val stockY = if (uiHeaderHeight > 0) {
            uiHeaderHeight + marginY
        } else {
            marginY + (h * GameConfig.TOP_UI_OFFSET_RATIO)
        }
        
        // Ensure spacing between columns is at least 2dp
        val minSpacing = 2f * context.resources.displayMetrics.density
        val totalBoardWidth = (7 * am.cardWidth) + (6 * minSpacing)
        
        // Center the board if screen is wider than board + default margins
        val dynamicMarginX = if (w > totalBoardWidth + (w * 0.04f)) {
            (w - totalBoardWidth) / 2f
        } else {
            w * 0.02f
        }
        val spacingX = (w - (2 * dynamicMarginX) - (7 * am.cardWidth)) / 6f
        
        val fX = FloatArray(4) { i -> dynamicMarginX + (i + 3) * (am.cardWidth + spacingX) }
        val tX = FloatArray(7) { i -> dynamicMarginX + i * (am.cardWidth + spacingX) }
        
        layout = GameLayout(
            stockX = dynamicMarginX, stockY = stockY,
            wasteX = dynamicMarginX + am.cardWidth + spacingX, wasteY = stockY,
            foundationX = fX, foundationY = stockY,
            tableauX = tX, tableauY = stockY + am.cardHeight + marginY
        )

        renderer = WorldRenderer(am)
        physics = PhysicsEngine(gameEventListener)
        inputHandler = InputHandler(gameEventListener, am) { action -> runOnGameThread(action) }
        
        updateCardPositions()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        gameThread?.isRunning = false
        gameThread?.join()
    }

    fun updatePhysics(dt: Float) {
        synchronized(gameStateLock) {
            // Fase 4: Process actions from UI thread safely on Game Thread
            while (actionQueue.isNotEmpty()) {
                actionQueue.poll()?.invoke()
            }

            val l = layout ?: return
            val am = assetManager ?: return
            physics.update(dt, gameState, l, am)
            checkWinCondition()
        }
    }

    fun render(canvas: Canvas) {
        val l = layout ?: return
        val am = assetManager ?: return
        renderer.render(canvas, gameState, l, inputHandler.activeCardStack)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        synchronized(gameStateLock) {
            val l = layout ?: return false
            val handled = inputHandler.onTouchEvent(event, gameState, l)
            if (event.action == MotionEvent.ACTION_UP) {
                updateCardPositions()
            }
            return handled
        }
    }

    private fun checkWinCondition() {
        if (gameState.foundations.all { it.size == 13 }) {
            gameEventListener?.onGameWon()
        }
    }
}
