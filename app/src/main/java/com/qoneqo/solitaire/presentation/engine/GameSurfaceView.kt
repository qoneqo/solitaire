package com.qoneqo.solitaire.presentation.engine

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.qoneqo.solitaire.domain.*
import java.util.*

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

    // Components
    private lateinit var renderer: WorldRenderer
    private lateinit var physics: PhysicsEngine
    private lateinit var inputHandler: InputHandler
    private var layout: GameLayout? = null
    var uiHeaderHeight: Float = 0f

    // Bot Properties
    var isAutoSolving: Boolean = false
    private var solverTimer: Float = 0f
    private var isUsingLogbook: Boolean = false
    private var currentLogbookEntry: LogbookEntry? = null
    private var logbookStepIndex: Int = 0
    private var isGameFinished: Boolean = false

    // Loop Detection
    private val moveHistory = LinkedList<LogbookMove>()
    private var recycleCount: Int = 0
    private var movesSinceLastProgress: Int = 0

    fun getCurrentLogbookId(): Int = currentLogbookEntry?.id ?: -1

    init {
        holder.addCallback(this)
        isFocusable = true
        LogbookManager.load(context)
        setupNewGame()
    }

    fun setupNewGame() {
        synchronized(gameStateLock) {
            gameState = GameState()
            val entry = LogbookManager.getRandomEntry()
            
            if (entry != null) {
                currentLogbookEntry = entry
                isUsingLogbook = true
                logbookStepIndex = 0
                
                val deck = entry.deck.map { LogbookManager.stringToCard(it) }.toMutableList()
                
                // Distribute to Tableaus
                for (i in 0 until 7) {
                    for (j in 0..i) {
                        val card = deck.removeAt(0)
                        card.isFaceUp = (j == i)
                        gameState.tableaus[i].add(card)
                    }
                }
                gameState.stock.addAll(deck)
                android.util.Log.i("GameSurfaceView", "New Game started using Logbook ID: ${entry.id}")
            } else {
                android.util.Log.e("GameSurfaceView", "CRITICAL ERROR: Logbook is empty! Cannot start game.")
                // Optionally show a message to user here
            }
            
            moveHistory.clear()
            recycleCount = 0
            movesSinceLastProgress = 0
            isGameFinished = false
            isAutoSolving = false // Stop bot on new game
            updateCardPositions()
        }
    }

    fun loadGameState(state: GameState) {
        synchronized(gameStateLock) {
            gameState = state
            isUsingLogbook = false // Manual load breaks logbook
            updateCardPositions()
        }
    }

    fun undo() {
        runOnGameThread {
            if (gameState.undoStack.isNotEmpty()) {
                val command = gameState.undoStack.removeAt(gameState.undoStack.size - 1)
                command.undo(gameState)
                isUsingLogbook = false // Undo breaks logbook
                updateCardPositions()
                gameEventListener?.onScoreChanged(gameState.score)
                gameEventListener?.onMovesChanged(gameState.moves)
            }
        }
    }

    fun runOnGameThread(action: () -> Unit) {
        actionQueue.add(action)
    }

    private fun updateCardPositions() {
        val am = assetManager ?: return
        val l = layout ?: return
        
        gameState.stock.forEach { card ->
            if (!card.isSnappingBack && inputHandler.activeCardStack?.contains(card) != true) {
                card.renderX = l.stockX
                card.renderY = l.stockY
            }
        }
        gameState.waste.forEach { card ->
            if (!card.isSnappingBack && inputHandler.activeCardStack?.contains(card) != true) {
                card.renderX = l.wasteX
                card.renderY = l.wasteY
            }
        }
        for (i in 0 until 4) {
            gameState.foundations[i].forEach { card ->
                if (!card.isSnappingBack && inputHandler.activeCardStack?.contains(card) != true) {
                    card.renderX = l.foundationX[i]
                    card.renderY = l.foundationY
                }
            }
        }
        for (i in 0 until 7) {
            val pile = gameState.tableaus[i]
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
        
        val marginY = h * GameConfig.MARGIN_Y_RATIO
        val stockY = if (uiHeaderHeight > 0) uiHeaderHeight + marginY else marginY + (h * GameConfig.TOP_UI_OFFSET_RATIO)
        
        val minSpacing = 2f * context.resources.displayMetrics.density
        val totalBoardWidth = (7 * am.cardWidth) + (6 * minSpacing)
        
        val dynamicMarginX = if (w > totalBoardWidth + (w * 0.04f)) (w - totalBoardWidth) / 2f else w * 0.02f
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
            while (actionQueue.isNotEmpty()) actionQueue.poll()?.invoke()

            val l = layout ?: return
            val am = assetManager ?: return
            physics.update(dt, gameState, l, am)
            
            if (isAutoSolving) {
                solverTimer += dt
                if (solverTimer >= 0.3f) {
                    solverTimer = 0f
                    performSolverMove()
                }
            }
            
            checkWinCondition()
        }
    }

    private fun performSolverMove() {
        var nextMove: LogbookMove? = null
        
        if (isUsingLogbook) {
            val entry = currentLogbookEntry
            if (entry != null && logbookStepIndex < entry.moves.size) {
                nextMove = entry.moves[logbookStepIndex]
                android.util.Log.d("GameBot", "Executing logbook step $logbookStepIndex: ${nextMove.type}")
                logbookStepIndex++
            } else {
                android.util.Log.d("GameBot", "Logbook finished or unavailable. Switching to dynamic.")
                isUsingLogbook = false
            }
        }
        
        if (nextMove == null) {
            nextMove = InternalSolver.getNextMove(gameState)
            if (nextMove != null) {
                android.util.Log.d("GameBot", "Executing dynamic step: ${nextMove.type}")
            }
        }

        if (nextMove == null) {
            android.util.Log.d("GameBot", "No moves found. Stopping.")
            isAutoSolving = false
            return
        }

        if (validateAndExecuteMove(nextMove)) {
            detectLoop(nextMove)
        } else {
            android.util.Log.w("GameBot", "Move ${nextMove.type} was invalid according to rules!")
            // If logbook move failed, we must switch to dynamic
            if (isUsingLogbook) {
                isUsingLogbook = false
                performSolverMove() // Try again with dynamic solver immediately
            }
        }
    }

    private fun validateAndExecuteMove(move: LogbookMove): Boolean {

        when (move.type) {
            "DEAL_STOCK" -> {
                if (gameState.stock.isNotEmpty()) {
                    executeMove(move)
                    return true
                }
            }
            "RECYCLE_WASTE" -> {
                if (gameState.waste.isNotEmpty() && gameState.stock.isEmpty()) {
                    executeMove(move)
                    return true
                }
            }
            "TO_FOUNDATION" -> {
                val fromPile = if (move.fromType == 0) gameState.waste else gameState.tableaus[move.fromIdx]
                if (fromPile.isNotEmpty()) {
                    val card = fromPile.last()
                    if (SolitaireRules.canMoveToFoundation(card, gameState.foundations[move.toIdx])) {
                        executeMove(move)
                        return true
                    }
                }
            }
            "TO_TABLEAU" -> {
                val fromPile = if (move.fromType == 0) gameState.waste else gameState.tableaus[move.fromIdx]
                if (fromPile.size >= move.cardCount) {
                    val card = fromPile[fromPile.size - move.cardCount]
                    if (SolitaireRules.canMoveToTableau(card, gameState.tableaus[move.toIdx])) {
                        executeMove(move)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun executeMove(move: LogbookMove) {
        val l = layout ?: return
        val am = assetManager ?: return

        when (move.type) {
            "DEAL_STOCK" -> {
                if (gameState.stock.isNotEmpty()) {
                    val card = gameState.stock.removeAt(gameState.stock.size - 1)
                    card.isFaceUp = true
                    gameState.waste.add(card)
                    gameState.undoStack.add(GameCommand.DealStock(1))
                    gameState.moves++
                    gameEventListener?.playSound(com.qoneqo.solitaire.R.raw.card_deal)
                    gameEventListener?.onMovesChanged(gameState.moves)
                }
            }
            "RECYCLE_WASTE" -> {
                val count = gameState.waste.size
                while (gameState.waste.isNotEmpty()) {
                    val card = gameState.waste.removeAt(gameState.waste.size - 1)
                    card.isFaceUp = false
                    gameState.stock.add(card)
                }
                gameState.undoStack.add(GameCommand.RecycleWaste(count))
                gameState.moves++
                gameEventListener?.onMovesChanged(gameState.moves)
            }
            "TO_FOUNDATION" -> {
                val fromPile = if (move.fromType == 0) gameState.waste else gameState.tableaus[move.fromIdx]
                if (fromPile.isEmpty()) return
                val card = fromPile.removeAt(fromPile.size - 1)
                if (move.fromType == 2 && fromPile.isNotEmpty() && !fromPile.last().isFaceUp) fromPile.last().isFaceUp = true
                
                gameState.foundations[move.toIdx].add(card)
                card.originalX = l.foundationX[move.toIdx]
                card.originalY = l.foundationY
                card.isSnappingBack = true
                
                gameState.undoStack.add(GameCommand.MoveCards(listOf(0), move.fromType, move.fromIdx, 1, move.toIdx, false))
                gameState.score += GameConfig.SCORE_FOUNDATION
                gameState.moves++
                gameEventListener?.playSound(com.qoneqo.solitaire.R.raw.card_place)
                gameEventListener?.onScoreChanged(gameState.score)
                gameEventListener?.onMovesChanged(gameState.moves)
            }
            "TO_TABLEAU" -> {
                val fromPile = if (move.fromType == 0) gameState.waste else gameState.tableaus[move.fromIdx]
                if (fromPile.size < move.cardCount) return
                val stack = fromPile.subList(fromPile.size - move.cardCount, fromPile.size).toList()
                val wasFaceDown = move.fromType == 2 && fromPile.size > stack.size && !fromPile[fromPile.size - stack.size - 1].isFaceUp
                
                repeat(stack.size) { fromPile.removeAt(fromPile.size - 1) }
                if (move.fromType == 2 && fromPile.isNotEmpty() && !fromPile.last().isFaceUp) fromPile.last().isFaceUp = true
                
                val toPile = gameState.tableaus[move.toIdx]
                toPile.addAll(stack)
                stack.forEachIndexed { index, c ->
                    c.originalX = l.tableauX[move.toIdx]
                    c.originalY = l.tableauY + (toPile.size - stack.size + index) * am.verticalOffset
                    c.isSnappingBack = true
                }
                
                gameState.undoStack.add(GameCommand.MoveCards(List(stack.size) { it }, move.fromType, move.fromIdx, 2, move.toIdx, wasFaceDown))
                gameState.moves++
                gameEventListener?.playSound(com.qoneqo.solitaire.R.raw.card_place)
                gameEventListener?.onMovesChanged(gameState.moves)
            }
        }
        updateCardPositions()
    }

    private fun detectLoop(move: LogbookMove) {
        if (isUsingLogbook) return // Logbook path is pre-verified

        // Pattern A: Ping-Pong (Back and forth)
        moveHistory.add(move)
        if (moveHistory.size > 6) moveHistory.removeFirst()
        
        if (moveHistory.size >= 4) {
            val m1 = moveHistory[moveHistory.size - 4]
            val m2 = moveHistory[moveHistory.size - 3]
            val m3 = moveHistory[moveHistory.size - 2]
            val m4 = moveHistory[moveHistory.size - 1]
            
            // Check if m1 == m3 and m2 == m4 AND m1 is reverse of m2
            if (m1 == m3 && m2 == m4) {
                // Simple reverse detection: same piles, opposite direction
                if (m1.type == "TO_TABLEAU" && m2.type == "TO_TABLEAU" &&
                    m1.fromIdx == m2.toIdx && m1.toIdx == m2.fromIdx) {
                    stuck("Bot Terjebak! Win tidak bisa dipastikan 100% Silakan gerakkan kartu manual atau mulai game baru.")
                }
            }
        }

        // Pattern B: Stock Loop
        if (move.type == "RECYCLE_WASTE") {
            recycleCount++
            if (recycleCount >= 3) {
                stuck("Bot Terjebak! Win tidak bisa dipastikan 100% Silakan gerakkan kartu manual atau mulai game baru.")
            }
        } else if (move.type != "DEAL_STOCK") {
            // Any move other than dealing reset the recycle count
            recycleCount = 0
        }
    }

    private fun stuck(message: String) {
        isAutoSolving = false
        gameEventListener?.onBotStuck(message)
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
                if (handled) isUsingLogbook = false // Manual move breaks logbook
                updateCardPositions()
            }
            return handled
        }
    }

    private fun checkWinCondition() {
        if (!isGameFinished && gameState.foundations.all { it.size == 13 }) {
            isGameFinished = true
            isAutoSolving = false
            android.util.Log.d("GameBot", "Game Won! Stopping Bot.")
            gameEventListener?.onGameWon()
        }
    }
}
