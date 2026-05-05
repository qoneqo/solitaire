package com.qoneqo.solitaire.presentation.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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

    // Visual Effects
    private val moveHistory = LinkedList<LogbookMove>()
    private var recycleCount: Int = 0
    private var movesSinceLastProgress: Int = 0
    private var hintedCard: Card? = null
    private var hintTimer: Float = 0f
    
    private val particles = mutableListOf<Particle>()
    private val cascadingCards = mutableListOf<CascadingCard>()
    private var isWinAnimationActive = false
    private var cascadeTimer = 0f

    fun getCurrentLogbookId(): Int = currentLogbookEntry?.id ?: -1

    init {
        holder.addCallback(this)
        isFocusable = true
        LogbookManager.load(context)
        setupNewGame()
    }

    fun setupNewGame(specificId: Int = -1) {
        synchronized(gameStateLock) {
            gameState = GameState()
            val entry = if (specificId != -1) LogbookManager.getEntry(specificId) else LogbookManager.getRandomEntry()
            
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
                gameState.stock.addAll(deck.reversed())
                android.util.Log.i("GameSurfaceView", "New Game started using Logbook ID: ${entry.id}")
            } else {
                android.util.Log.e("GameSurfaceView", "CRITICAL ERROR: Logbook is empty! Cannot start game.")
            }
            
            moveHistory.clear()
            recycleCount = 0
            movesSinceLastProgress = 0
            isGameFinished = false
            isAutoSolving = false
            hintedCard = null
            particles.clear()
            cascadingCards.clear()
            isWinAnimationActive = false
            updateCardPositions()
        }
    }

    fun undo() {
        runOnGameThread {
            if (gameState.undoStack.isNotEmpty()) {
                val command = gameState.undoStack.removeAt(gameState.undoStack.size - 1)
                command.undo(gameState)
                isUsingLogbook = false
                updateCardPositions()
                gameEventListener?.onScoreChanged(gameState.score)
                gameEventListener?.onMovesChanged(gameState.moves)
            }
        }
    }

    fun showHint() {
        runOnGameThread {
            val move = InternalSolver.getNextMove(gameState)
            if (move != null) {
                hintedCard = when (move.type) {
                    "DEAL_STOCK" -> if (gameState.stock.isNotEmpty()) gameState.stock.last() else null
                    "RECYCLE_WASTE" -> if (gameState.waste.isNotEmpty()) gameState.waste.first() else null
                    "TO_FOUNDATION", "TO_TABLEAU" -> {
                        val fromPile = if (move.fromType == 0) gameState.waste else gameState.tableaus[move.fromIdx]
                        if (fromPile.size >= move.cardCount) fromPile[fromPile.size - move.cardCount] else null
                    }
                    else -> null
                }
                hintTimer = 3.0f
            }
        }
    }

    fun emitParticles(x: Float, y: Float, color: Int = Color.YELLOW, count: Int = 15) {
        val random = Random()
        repeat(count) {
            val angle = random.nextDouble() * 2.0 * Math.PI
            val speed = 100f + random.nextFloat() * 200f
            particles.add(Particle(
                x = x,
                y = y,
                vx = (speed * Math.cos(angle)).toFloat(),
                vy = (speed * Math.sin(angle)).toFloat(),
                color = color,
                life = 0.5f + random.nextFloat() * 0.5f
            ))
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
            
            updateParticles(dt)
            if (isWinAnimationActive) updateWinAnimation(dt)

            if (hintTimer > 0) {
                hintTimer -= dt
                if (hintTimer <= 0) hintedCard = null
            }

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

    private fun updateParticles(dt: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.life -= dt
            if (p.life <= 0) {
                iterator.remove()
            } else {
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.alpha = (255 * (p.life / p.maxLife)).toInt()
            }
        }
    }

    private fun updateWinAnimation(dt: Float) {
        val am = assetManager ?: return
        val w = width.toFloat()
        val h = height.toFloat()

        // Update existing cascading cards
        val iterator = cascadingCards.iterator()
        while (iterator.hasNext()) {
            val c = iterator.next()
            c.x += c.vx * dt
            c.y += c.vy * dt
            c.vy += 1200f * dt // Gravity

            if (c.y + am.cardHeight > h) {
                c.y = h - am.cardHeight
                c.vy = -c.vy * 0.7f // Bounce
                if (Math.abs(c.vy) > 100f) {
                    gameEventListener?.playSound(com.qoneqo.solitaire.R.raw.card_deal)
                }
            }

            if (c.x > w || c.x + am.cardWidth < 0) {
                iterator.remove()
            }
        }

        // Spawn next card in sequence
        cascadeTimer += dt
        if (cascadeTimer >= 0.1f) {
            cascadeTimer = 0f
            spawnNextCascadingCard()
        }
        
        // Final condition: if all cards are gone and sequence is done
        if (cascadingCards.isEmpty() && isAllCardsSpawned()) {
            isWinAnimationActive = false
            gameEventListener?.onGameWon() // Finally show dialog
        }
    }

    private var nextCardToSpawnIdx = 12 // K down to A
    private var nextFoundationToSpawnIdx = 0

    private fun spawnNextCascadingCard() {
        val l = layout ?: return
        val random = Random()
        
        // Search for a foundation that still has cards
        var found = false
        for (fIdx in 0 until 4) {
            val pile = gameState.foundations[fIdx]
            if (pile.isNotEmpty()) {
                val card = pile.removeAt(pile.size - 1)
                val vx = if (random.nextBoolean()) 150f + random.nextFloat() * 300f else -150f - random.nextFloat() * 300f
                cascadingCards.add(CascadingCard(
                    cardIndex = pile.size,
                    foundationIndex = fIdx,
                    x = l.foundationX[fIdx],
                    y = l.foundationY,
                    vx = vx,
                    vy = -200f - random.nextFloat() * 400f,
                    suit = card.suit,
                    rank = card.rank
                ))
                gameEventListener?.playSound(com.qoneqo.solitaire.R.raw.card_place)
                found = true
                break
            }
        }
        
        if (!found) nextCardToSpawnIdx = -1 // Marker for all spawned
    }

    private fun isAllCardsSpawned(): Boolean = gameState.foundations.all { it.isEmpty() }

    private fun performSolverMove() {
        var nextMove: LogbookMove? = null

        // Try logbook moves first; if invalid, fall through to solver
        if (isUsingLogbook) {
            val entry = currentLogbookEntry
            if (entry != null && logbookStepIndex < entry.moves.size) {
                val logbookMove = entry.moves[logbookStepIndex]
                logbookStepIndex++
                if (validateAndExecuteMove(logbookMove)) {
                    detectLoop(logbookMove)
                    return
                } else {
                    // Logbook move invalid — abandon logbook, fall through to solver
                    android.util.Log.w("GameBot", "Logbook move #$logbookStepIndex invalid, switching to solver.")
                    isUsingLogbook = false
                }
            } else {
                isUsingLogbook = false
            }
        }

        nextMove = InternalSolver.getNextMove(gameState)

        if (nextMove == null) {
            android.util.Log.w("GameBot", "Solver returned null — no moves available. Stopping.")
            isAutoSolving = false
            return
        }

        if (validateAndExecuteMove(nextMove)) {
            detectLoop(nextMove)
        } else {
            android.util.Log.w("GameBot", "Solver move invalid: $nextMove")
            isAutoSolving = false
        }
    }

    private fun validateAndExecuteMove(move: LogbookMove): Boolean {
        when (move.type) {
            "DEAL_STOCK" -> if (gameState.stock.isNotEmpty()) { executeMove(move); return true }
            "RECYCLE_WASTE" -> if (gameState.waste.isNotEmpty() && gameState.stock.isEmpty()) { executeMove(move); return true }
            "TO_FOUNDATION" -> {
                val fromPile = if (move.fromType == 0) gameState.waste else gameState.tableaus[move.fromIdx]
                if (fromPile.isNotEmpty()) {
                    val card = fromPile.last()
                    // If logbook specifies an index, try it first, otherwise find any valid
                    var targetIdx = move.toIdx
                    if (targetIdx == -1 || !SolitaireRules.canMoveToFoundation(card, gameState.foundations[targetIdx])) {
                        targetIdx = -1
                        for (i in 0 until 4) {
                            if (SolitaireRules.canMoveToFoundation(card, gameState.foundations[i])) {
                                targetIdx = i
                                break
                            }
                        }
                    }
                    
                    if (targetIdx != -1) {
                        // Update move with correct index for execution
                        val correctedMove = move.copy(toIdx = targetIdx)
                        executeMove(correctedMove); return true
                    }
                }
            }
            "TO_TABLEAU" -> {
                val fromPile = if (move.fromType == 0) gameState.waste else gameState.tableaus[move.fromIdx]
                if (fromPile.size >= move.cardCount && SolitaireRules.canMoveToTableau(fromPile[fromPile.size - move.cardCount], gameState.tableaus[move.toIdx])) {
                    executeMove(move); return true
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
                val card = gameState.stock.removeAt(gameState.stock.size - 1)
                card.isFaceUp = true
                gameState.waste.add(card)
                gameState.undoStack.add(GameCommand.DealStock(1))
                gameState.moves++
                gameEventListener?.playSound(com.qoneqo.solitaire.R.raw.card_deal)
                gameEventListener?.onMovesChanged(gameState.moves)
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
                emitParticles(l.foundationX[move.toIdx] + am.cardWidth / 2f, l.foundationY + am.cardHeight / 2f, Color.YELLOW)
            }
            "TO_TABLEAU" -> {
                val fromPile = if (move.fromType == 0) gameState.waste else gameState.tableaus[move.fromIdx]
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
                emitParticles(l.tableauX[move.toIdx] + am.cardWidth / 2f, l.tableauY + (toPile.size - 1) * am.verticalOffset + am.cardHeight / 2f, Color.WHITE)
            }
        }
        updateCardPositions()
    }

    private fun detectLoop(move: LogbookMove) {
        if (isUsingLogbook) return

        moveHistory.add(move)
        if (moveHistory.size > 8) moveHistory.removeFirst()

        // Detect A->B->A->B tableau loop
        if (moveHistory.size >= 4) {
            val m1 = moveHistory[moveHistory.size - 4]; val m2 = moveHistory[moveHistory.size - 3]
            val m3 = moveHistory[moveHistory.size - 2]; val m4 = moveHistory[moveHistory.size - 1]
            if (m1 == m3 && m2 == m4
                && m1.type == "TO_TABLEAU" && m2.type == "TO_TABLEAU"
                && m1.fromIdx == m2.toIdx && m1.toIdx == m2.fromIdx) {
                stuck("Bot Terjebak! Loop terdeteksi. Silakan gerakkan kartu manual atau mulai game baru.")
                return
            }
        }

        // Track recycle cycles — only reset after a Foundation move (real progress)
        when (move.type) {
            "RECYCLE_WASTE" -> {
                recycleCount++
                if (recycleCount >= 3) stuck("Bot Terjebak! Tidak ada kartu berguna di deck. Silakan gerakkan kartu manual atau mulai game baru.")
            }
            "TO_FOUNDATION" -> recycleCount = 0 // Real progress, reset cycle counter
        }
    }

    private fun stuck(message: String) {
        isAutoSolving = false
        gameEventListener?.onBotStuck(message)
    }

    fun render(canvas: Canvas) {
        val l = layout ?: return
        val am = assetManager ?: return
        renderer.render(canvas, gameState, l, inputHandler.activeCardStack, hintedCard, particles, cascadingCards)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isWinAnimationActive) return false
        synchronized(gameStateLock) {
            val l = layout ?: return false
            val handled = inputHandler.onTouchEvent(event, gameState, l)
            if (event.action == MotionEvent.ACTION_UP) {
                if (handled) { isUsingLogbook = false; hintedCard = null }
                updateCardPositions()
            }
            return handled
        }
    }

    private fun checkWinCondition() {
        if (!isGameFinished && !isWinAnimationActive && gameState.foundations.all { it.size == 13 }) {
            isGameFinished = true
            isAutoSolving = false
            isWinAnimationActive = true // Start cascade instead of showing dialog immediately
            android.util.Log.d("GameBot", "Starting Win Cascade Animation!")
        }
    }
}
