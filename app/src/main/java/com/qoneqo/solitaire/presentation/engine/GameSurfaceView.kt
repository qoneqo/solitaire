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
            gameState.tableaus[i].forEachIndexed { index, card ->
                if (!card.isSnappingBack && inputHandler.activeCardStack?.contains(card) != true) {
                    card.renderX = l.tableauX[i]
                    card.renderY = l.tableauY + index * am.verticalOffset
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

    private fun initEngine() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        
        assetManager = CardAssetManager(context, w.toInt(), h.toInt())
        val am = assetManager!!
        
        // Calculate Layout
        val marginX = w * GameConfig.MARGIN_X_RATIO
        val marginY = h * GameConfig.MARGIN_Y_RATIO
        val spacingX = (w - (2 * marginX) - (7 * am.cardWidth)) / 6f
        val stockY = marginY + (h * GameConfig.TOP_UI_OFFSET_RATIO)
        
        val fX = FloatArray(4) { i -> marginX + (i + 3) * (am.cardWidth + spacingX) }
        val tX = FloatArray(7) { i -> marginX + i * (am.cardWidth + spacingX) }
        
        layout = GameLayout(
            stockX = marginX, stockY = stockY,
            wasteX = marginX + am.cardWidth + spacingX, wasteY = stockY,
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
