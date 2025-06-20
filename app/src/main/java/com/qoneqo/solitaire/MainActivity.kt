package com.qoneqo.solitaire

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var game: SolitaireGame
    private lateinit var scoreText: TextView
    private lateinit var movesText: TextView
    private lateinit var stockPile: FrameLayout
    private lateinit var wastePile: FrameLayout
    private lateinit var foundations: Array<FrameLayout>
    private lateinit var tableauColumns: Array<LinearLayout>
    private lateinit var autoCompleteButton: Button
    private lateinit var soundManager: SoundManager
    private var isSoundEnabled = true

    // animation state
    private var isAnimating = false
    private val animationQueue = mutableListOf<() -> Unit>()

    // Selection state
    private var selectedTableauColumn: Int = -1
    private var selectedCardIndex: Int = -1
    private var selectedFromWaste: Boolean = false
    private var selectedFoundationIndex: Int = -1 // NEW: Track selected foundation

    //    analyzer
    private lateinit var gameAnalyzer: GameStateAnalyzer
    private var lastAnalysisTime = 0L
    private val ANALYSIS_COOLDOWN = 5000L // 5 seconds between analyses

    private fun createCardViewFromSource(sourceView: View): ImageView {
        val imageView = ImageView(this)

        if (sourceView is ImageView) {
            imageView.setImageDrawable(sourceView.drawable)
            imageView.scaleType = sourceView.scaleType
        } else {
            // Fallback
            imageView.setImageResource(R.drawable.card_back)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        }

        return imageView
    }
    private fun performAnimatedMove(moveAction: () -> Boolean, sourceView: View? = null, targetView: View? = null) {
        if (isAnimating) {
            // Queue the animation
            animationQueue.add { performAnimatedMove(moveAction, sourceView, targetView) }
            return
        }

        if (sourceView != null && targetView != null) {
            isAnimating = true

            // Execute the actual move FIRST before animation
            val moveSuccess = moveAction()

            if (!moveSuccess) {
                isAnimating = false
                return
            }

            // Create a temporary card view for animation
            val cardView = if (sourceView is ImageView) sourceView else createCardViewFromSource(sourceView)

            CardAnimationHelper.animateCardMove(
                sourceView = sourceView,
                targetContainer = targetView as ViewGroup,
                cardView = cardView as ImageView
            ) {
                // After animation completes
                updateUI()
                checkWinCondition()
                isAnimating = false

                // Process next animation in queue
                if (animationQueue.isNotEmpty()) {
                    val nextAnimation = animationQueue.removeAt(0)
                    nextAnimation()
                }
            }
        } else {
            // No animation, just perform the move
            if (moveAction()) {
                updateUI()
                checkWinCondition()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        soundManager = SoundManager(this)
        initializeViews()
        game = SolitaireGame()
        setupClickListeners()
        setupSoundToggleButton()
        updateUI()
        soundManager.playSound(R.raw.card_deal)
        gameAnalyzer = GameStateAnalyzer()
    }
    override fun onDestroy() {
        soundManager.release()
        super.onDestroy()
    }
    private fun initializeViews() {
        scoreText = findViewById(R.id.scoreText)
        movesText = findViewById(R.id.movesText)
        stockPile = findViewById(R.id.stockPile)
        wastePile = findViewById(R.id.wastePile)
        autoCompleteButton = findViewById(R.id.autoCompleteButton)

        foundations = arrayOf(
            findViewById(R.id.foundation0),
            findViewById(R.id.foundation1),
            findViewById(R.id.foundation2),
            findViewById(R.id.foundation3)
        )

        tableauColumns = arrayOf(
            findViewById(R.id.tableau0),
            findViewById(R.id.tableau1),
            findViewById(R.id.tableau2),
            findViewById(R.id.tableau3),
            findViewById(R.id.tableau4),
            findViewById(R.id.tableau5),
            findViewById(R.id.tableau6)
        )

        findViewById<Button>(R.id.newGameButton).setOnClickListener {
            game.newGame()
            clearSelection()
            updateUI()
        }

        autoCompleteButton.setOnClickListener {
            performAutoComplete()
        }
    }

    private fun setupClickListeners() {
        // Stock pile click - draw card
        stockPile.setOnClickListener {
            if (game.drawCard()) {
                soundManager.playSound(R.raw.card_place)
                clearSelection()
                updateUI()
            }
        }

        // Waste pile click - select/deselect waste card
        wastePile.setOnClickListener {
            if (isAnimating) return@setOnClickListener

            val gameState = game.getGameState()
            if (gameState.waste.isNotEmpty()) {
                if (selectedFromWaste) {
                    clearSelection()
                } else {
                    clearSelection()
                    selectedFromWaste = true

                    // Try auto-move to foundation first with animation
                    for (i in 0..3) {
                        if (game.canMoveWasteToFoundation(i)) {
                            val sourceView = wastePile.getChildAt(0)
                            val targetView = foundations[i]
                            soundManager.playSound(R.raw.card_place)
                            performAnimatedMove(
                                moveAction = { game.moveWasteToFoundation(i) },
                                sourceView = sourceView,
                                targetView = targetView
                            )
                            clearSelection()
                            return@setOnClickListener
                        }
                    }
                }
                updateUI()
            }
        }

        // Foundation clicks - UPDATED to handle selection and movement
        foundations.forEachIndexed { index, foundation ->
            foundation.setOnClickListener {
                if (isAnimating) return@setOnClickListener

                val gameState = game.getGameState()

                if (selectedFromWaste) {
                    val sourceView = wastePile.getChildAt(0)
                    soundManager.playSound(R.raw.card_place)
                    performAnimatedMove(
                        moveAction = { game.moveWasteToFoundation(index) },
                        sourceView = sourceView,
                        targetView = foundation
                    )
                    clearSelection()
                } else if (selectedTableauColumn != -1) {
                    val sourceColumn = tableauColumns[selectedTableauColumn]
                    val sourceView = if (sourceColumn.childCount > 0)
                        sourceColumn.getChildAt(sourceColumn.childCount - 1) else null

                    if (sourceView != null) {
                        soundManager.playSound(R.raw.card_place)
                        performAnimatedMove(
                            moveAction = { game.moveTableauToFoundation(selectedTableauColumn, index) },
                            sourceView = sourceView,
                            targetView = foundation
                        )
                        clearSelection()
                    }
                } else if (selectedFoundationIndex != -1) {
                    if (selectedFoundationIndex == index) {
                        clearSelection()
                        updateUI()
                    }
                } else {
                    if (gameState.foundations[index].isNotEmpty()) {
                        selectedFoundationIndex = index
                        updateUI()
                    }
                }
            }
        }

        // Tableau column clicks
        tableauColumns.forEachIndexed { index, column ->
            column.setOnClickListener {
                handleTableauClick(index)
            }

            // Add click listeners to individual cards in tableau
            setupTableauCardClickListeners(index)
        }
    }

    private fun setupTableauCardClickListeners(columnIndex: Int) {
        val column = tableauColumns[columnIndex]

        // We'll set up click listeners when updating UI
        // since cards are recreated each time
    }

    private fun handleTableauClick(columnIndex: Int) {
        val gameState = game.getGameState()

        if (selectedFromWaste) {
            // Move waste to tableau
            if (game.moveWasteToTableau(columnIndex)) {
                soundManager.playSound(R.raw.card_place)
                clearSelection()
                updateUI()
            }
        } else if (selectedFoundationIndex != -1) {
            // NEW: Move foundation to tableau
            if (game.moveFoundationToTableau(selectedFoundationIndex, columnIndex)) {
                soundManager.playSound(R.raw.card_place)
                clearSelection()
                updateUI()
            }
        } else if (selectedTableauColumn != -1) {
            if (selectedTableauColumn == columnIndex) {
                // Clicking same column - deselect
                clearSelection()
                updateUI()
            } else {
                // Move from selected tableau to this tableau
                val selectedPile = gameState.tableau[selectedTableauColumn]
                if (selectedPile.isNotEmpty() && selectedCardIndex != -1) {
                    val cardsToMove = selectedPile.size - selectedCardIndex
                    if (game.moveTableauToTableau(selectedTableauColumn, columnIndex, cardsToMove)) {
                        soundManager.playSound(R.raw.card_place)
                        clearSelection()
                        updateUI()
                    }
                }
            }
        } else {
            // Select cards from this tableau column
            val pile = gameState.tableau[columnIndex]
            if (pile.isNotEmpty()) {
                // Try auto-move to foundation first
                for (foundationIndex in 0..3) {
                    if (game.moveTableauToFoundation(columnIndex, foundationIndex)) {
                        soundManager.playSound(R.raw.card_place)
                        clearSelection()
                        updateUI()
                        checkWinCondition()
                        return
                    }
                }

                // Select the movable sequence starting from the last face-up card
                selectTableauCards(columnIndex)
                updateUI()
            }
        }
    }

    private fun selectTableauCards(columnIndex: Int) {
        val gameState = game.getGameState()
        val pile = gameState.tableau[columnIndex]

        if (pile.isEmpty()) return

        // Find the longest movable sequence from the bottom
        var startIndex = pile.size - 1

        // Go backwards to find the start of the movable sequence
        for (i in pile.size - 2 downTo 0) {
            val currentCard = pile[i]
            val nextCard = pile[i + 1]

            if (currentCard.isFaceUp &&
                currentCard.rank.value == nextCard.rank.value + 1 &&
                currentCard.suit.color != nextCard.suit.color) {
                startIndex = i
            } else {
                break
            }
        }

        selectedTableauColumn = columnIndex
        selectedCardIndex = startIndex
        selectedFromWaste = false
        selectedFoundationIndex = -1
    }

    private fun clearSelection() {
        selectedTableauColumn = -1
        selectedCardIndex = -1
        selectedFromWaste = false
        selectedFoundationIndex = -1 // NEW: Clear foundation selection
    }

    private fun setupSoundToggleButton() {
        val fabSoundToggle = findViewById<FloatingActionButton>(R.id.fabSoundToggle)

        // Load saved preference (optional)
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        isSoundEnabled = sharedPref.getBoolean("sound_enabled", true)
        soundManager.setSoundEnabled(isSoundEnabled)

        // Set initial icon
        fabSoundToggle.setImageResource(
            if (isSoundEnabled) R.drawable.ic_volume_on
            else R.drawable.ic_volume_off
        )

        fabSoundToggle.setOnClickListener {
            isSoundEnabled = !isSoundEnabled

            // Update icon
            fabSoundToggle.setImageResource(
                if (isSoundEnabled) R.drawable.ic_volume_on
                else R.drawable.ic_volume_off
            )

            // Save preference (optional)
            with(sharedPref.edit()) {
                putBoolean("sound_enabled", isSoundEnabled)
                apply()
            }

            // Provide feedback
            soundManager.setSoundEnabled(isSoundEnabled)
            if (isSoundEnabled) {
                soundManager.playSound(R.raw.card_place)
            }

            // Haptic feedback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fabSoundToggle.performHapticFeedback(HapticFeedbackConstants.TOGGLE_OFF)
            } else {
                fabSoundToggle.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }

            // Small bounce animation
            fabSoundToggle.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(100)
                .withEndAction {
                    fabSoundToggle.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }.start()
        }
    }

    private fun checkAutoCompleteAvailability(): Boolean {
        val gameState = game.getGameState()

        // Check if all cards are face-up
        val allFaceUp = gameState.tableau.all { pile ->
            pile.all { card -> card.isFaceUp }
        } && gameState.waste.all { it.isFaceUp } && gameState.deck.isEmpty()

        return allFaceUp && !game.isGameWon()
    }
    private fun performNextAutoMove() {
        val gameState = game.getGameState()

        // Try to move from waste to foundation with animation
        if (gameState.waste.isNotEmpty()) {
            for (foundationIndex in 0..3) {
                if (game.canMoveWasteToFoundation(foundationIndex)) {
                    val sourceView = wastePile.getChildAt(0)
                    val targetView = foundations[foundationIndex]

                    soundManager.playSound(R.raw.card_place)
                    performAnimatedMove(
                        moveAction = { game.moveWasteToFoundation(foundationIndex) },
                        sourceView = sourceView,
                        targetView = targetView
                    )

                    // Continue auto-complete after this animation
                    handler.postDelayed({
                        if (!game.isGameWon()) performNextAutoMove()
                    }, 400)
                    return
                }
            }
        }

        // Try to move from tableau to foundation with animation
        for (tableauIndex in 0..6) {
            for (foundationIndex in 0..3) {
                if (game.canMoveTableauToFoundation(tableauIndex, foundationIndex)) {
                    val sourceColumn = tableauColumns[tableauIndex]
                    val sourceView = if (sourceColumn.childCount > 0)
                        sourceColumn.getChildAt(sourceColumn.childCount - 1) else null
                    val targetView = foundations[foundationIndex]

                    if (sourceView != null) {
                        soundManager.playSound(R.raw.card_place)
                        performAnimatedMove(
                            moveAction = { game.moveTableauToFoundation(tableauIndex, foundationIndex) },
                            sourceView = sourceView,
                            targetView = targetView
                        )

                        // Continue auto-complete after this animation
                        handler.postDelayed({
                            if (!game.isGameWon()) performNextAutoMove()
                        }, 400)
                        return
                    }
                }
            }
        }

        // No more moves available
        updateUI()
        checkWinCondition()
    }

    // Add handler for delayed operations
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private fun performAutoComplete() {
        if (isAnimating) return

        performNextAutoMove()
    }
    private fun updateUI() {
        val gameState = game.getGameState()

        // Update score and moves
        scoreText.text = "Score: ${gameState.score}"
        movesText.text = "Moves: ${gameState.moves}"

        // Update auto-complete button visibility
        if (checkAutoCompleteAvailability()) {
            autoCompleteButton.visibility = View.VISIBLE
        } else {
            autoCompleteButton.visibility = View.GONE
        }

        // Update stock pile
        stockPile.removeAllViews()
        if (gameState.deck.isNotEmpty()) {
            stockPile.background = getDrawable(R.drawable.card_back)
        } else {
            // Create a properly sized refresh icon
            stockPile.background = null
            val refreshIcon = ImageView(this)
            refreshIcon.setImageResource(android.R.drawable.ic_menu_rotate)
            refreshIcon.scaleType = ImageView.ScaleType.FIT_CENTER
            refreshIcon.setPadding(16, 16, 16, 16)

            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            refreshIcon.layoutParams = params
            stockPile.addView(refreshIcon)
        }

        // Update waste pile
        wastePile.removeAllViews()
        if (gameState.waste.isNotEmpty()) {
            val topCard = gameState.waste.last()
            val cardView = createCardView(topCard)

            // Highlight if selected
            if (selectedFromWaste) {
                cardView.background = ContextCompat.getDrawable(this, android.R.drawable.btn_default)
                cardView.background.setTint(ContextCompat.getColor(this, android.R.color.holo_blue_light))
            }

            wastePile.addView(cardView)
        }

        // Update foundations - UPDATED to show selection highlight
        foundations.forEachIndexed { index, foundation ->
            foundation.removeAllViews()
            if (gameState.foundations[index].isNotEmpty()) {
                val topCard = gameState.foundations[index].last()
                val cardView = createCardView(topCard)

                // NEW: Highlight if this foundation is selected
                if (selectedFoundationIndex == index) {
                    cardView.background = ContextCompat.getDrawable(this, android.R.drawable.btn_default)
                    cardView.background?.setTint(ContextCompat.getColor(this, android.R.color.holo_blue_light))
                }

                foundation.addView(cardView)
            }
        }

        // Update tableau
        tableauColumns.forEachIndexed { columnIndex, column ->
            column.removeAllViews()
            gameState.tableau[columnIndex].forEachIndexed { cardIndex, card ->
                val cardView = createCardView(card)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.card_height)
                    // Card height in dp
                )
                if (cardIndex > 0) {
                    params.topMargin = -80 // Overlap
                }
                cardView.layoutParams = params

                // Highlight selected cards
                if (selectedTableauColumn == columnIndex &&
                    selectedCardIndex != -1 &&
                    cardIndex >= selectedCardIndex) {
                    cardView.background = ContextCompat.getDrawable(this, android.R.drawable.btn_default)
                    cardView.background?.setTint(ContextCompat.getColor(this, android.R.color.holo_blue_light))
                }

                // Add click listener to individual cards
                cardView.setOnClickListener {
                    handleCardClick(columnIndex, cardIndex)
                }

                column.addView(cardView)
            }
        }
    }

    private fun handleCardClick(columnIndex: Int, cardIndex: Int) {
        val gameState = game.getGameState()
        val pile = gameState.tableau[columnIndex]

        if (cardIndex >= pile.size || !pile[cardIndex].isFaceUp) return

        if (selectedFromWaste) {
            // Move waste to tableau
            if (game.moveWasteToTableau(columnIndex)) {
                soundManager.playSound(R.raw.card_place)
                clearSelection()
                updateUI()
            }
        } else if (selectedFoundationIndex != -1) {
            // NEW: Move foundation to tableau
            if (game.moveFoundationToTableau(selectedFoundationIndex, columnIndex)) {
                soundManager.playSound(R.raw.card_place)
                clearSelection()
                updateUI()
            }
        } else if (selectedTableauColumn != -1) {
            if (selectedTableauColumn == columnIndex) {
                // Clicking same column - update selection or try auto-move
                if (cardIndex == selectedCardIndex) {
                    clearSelection()
                } else if (cardIndex == pile.size - 1) {
                    // Clicking on the top card - try auto-move to foundation
                    for (foundationIndex in 0..3) {
                        if (game.moveTableauToFoundation(columnIndex, foundationIndex)) {
                            soundManager.playSound(R.raw.card_place)
                            clearSelection()
                            updateUI()
                            checkWinCondition()
                            return
                        }
                    }
                    selectedCardIndex = cardIndex
                } else {
                    selectedCardIndex = cardIndex
                }
                updateUI()
            } else {
                // Move cards to different column
                val selectedPile = gameState.tableau[selectedTableauColumn]
                if (selectedPile.isNotEmpty() && selectedCardIndex != -1) {
                    val cardsToMove = selectedPile.size - selectedCardIndex
                    if (game.moveTableauToTableau(selectedTableauColumn, columnIndex, cardsToMove)) {
                        soundManager.playSound(R.raw.card_place)
                        clearSelection()
                        updateUI()
                    }
                }
            }
        } else {
            // First click on a card - try auto-move to foundation if it's the top card
            if (cardIndex == pile.size - 1) {
                for (foundationIndex in 0..3) {
                    if (game.moveTableauToFoundation(columnIndex, foundationIndex)) {
                        soundManager.playSound(R.raw.card_place)
                        clearSelection()
                        updateUI()
                        checkWinCondition()
                        return
                    }
                }
            }

            // Select sequence starting from this card if auto-move failed or not applicable
            if (isValidSequenceStart(pile, cardIndex)) {
                selectedTableauColumn = columnIndex
                selectedCardIndex = cardIndex
                selectedFromWaste = false
                selectedFoundationIndex = -1
                updateUI()
            }
        }
    }

    private fun isValidSequenceStart(pile: List<Card>, startIndex: Int): Boolean {
        if (startIndex >= pile.size || !pile[startIndex].isFaceUp) return false

        // Check if this card and all cards below form a valid descending sequence
        for (i in startIndex until pile.size - 1) {
            val currentCard = pile[i]
            val nextCard = pile[i + 1]

            if (currentCard.rank.value != nextCard.rank.value + 1 ||
                currentCard.suit.color == nextCard.suit.color) {
                return false
            }
        }

        return true
    }

    private fun createCardView(card: Card): ImageView {
        val imageView = ImageView(this)

        if (card.isFaceUp) {
            // Get card image resource
            val resourceName = card.getDrawableResourceName()
            val resourceId = resources.getIdentifier(resourceName, "drawable", packageName)

            if (resourceId != 0) {
                imageView.setImageResource(resourceId)
            } else {
                // Fallback - create a simple text-based card
                imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            // Show card back
            imageView.setImageResource(R.drawable.card_back)
        }

        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.adjustViewBounds = true

        return imageView
    }

    private fun checkWinCondition() {
        if (game.isGameWon()) {
            soundManager.playSound(R.raw.win_sound)
            showGameOverDialog(true)
        } else if (gameAnalyzer.isGameLost(game.getGameState())) {
            // Only show loss if there are truly no moves left
            soundManager.playSound(R.raw.lose_sound)
            showGameOverDialog(false)
        }
    }
    private fun showGameOverDialog(isWin: Boolean) {
        val message = if (isWin) "Congratulations! You won!"
        else "Game Over - No more possible moves detected"

        AlertDialog.Builder(this)
            .setTitle(if (isWin) "You Won!" else "Game Over")
            .setMessage(message)
            .setPositiveButton("New Game") { _, _ ->
                game.newGame()
                clearSelection()
                updateUI()
                soundManager.playSound(R.raw.card_deal)
            }
            .setNegativeButton("Continue") { dialog, _ ->
                dialog.dismiss() // Let the player keep trying
            }
            .setCancelable(false)
            .show()
    }
    // Add this method to show the unwinnable dialog
    private fun showUnwinnableDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Game Analysis")
            .setMessage("This game appears to be unwinnable based on current card positions. Would you like to start a new game?")
            .setPositiveButton("New Game") { _, _ ->
                game.newGame()
                clearSelection()
                updateUI()
                soundManager.playSound(R.raw.card_deal)
            }
            .setNegativeButton("Continue Playing") { _, _ ->
                // Player chooses to continue
            }
            .setNeutralButton("Auto-Hint") { _, _ ->
                // Show a hint for the best available move
                showBestMoveHint()
            }
            .setCancelable(true)
            .create()

        dialog.show()
    }

    // Add this method to show move hints
    private fun showBestMoveHint() {
        val gameState = game.getGameState()
        val hint = findBestMoveHint(gameState)

        if (hint.isNotEmpty()) {
            Toast.makeText(this, hint, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "No obvious moves available. Try drawing cards or moving cards between columns.", Toast.LENGTH_LONG).show()
        }
    }

    // Add this helper method to find move hints
    private fun findBestMoveHint(gameState: GameState): String {
        // Check for foundation moves first (highest priority)
        if (gameState.waste.isNotEmpty()) {
            for (i in 0..3) {
                if (gameState.waste.last().canPlaceInFoundation(gameState.foundations[i].lastOrNull())) {
                    return "Move ${gameState.waste.last().rank.displayName} of ${gameState.waste.last().suit.symbol} from waste to foundation"
                }
            }
        }

        // Check tableau to foundation
        for (i in 0..6) {
            if (gameState.tableau[i].isNotEmpty()) {
                val topCard = gameState.tableau[i].last()
                for (j in 0..3) {
                    if (topCard.canPlaceInFoundation(gameState.foundations[j].lastOrNull())) {
                        return "Move ${topCard.rank.displayName} of ${topCard.suit.symbol} from column ${i + 1} to foundation"
                    }
                }
            }
        }

        // Check for tableau moves that reveal face-down cards
        for (i in 0..6) {
            val pile = gameState.tableau[i]
            if (pile.size >= 2 && !pile[pile.size - 2].isFaceUp) {
                val topCard = pile.last()
                for (j in 0..6) {
                    if (i != j) {
                        val targetPile = gameState.tableau[j]
                        if (targetPile.isEmpty() && topCard.rank == Card.Rank.KING) {
                            return "Move King from column ${i + 1} to empty column ${j + 1} to reveal hidden card"
                        } else if (targetPile.isNotEmpty() && topCard.canPlaceOn(targetPile.last())) {
                            return "Move ${topCard.rank.displayName} of ${topCard.suit.symbol} from column ${i + 1} to column ${j + 1} to reveal hidden card"
                        }
                    }
                }
            }
        }

        // Check waste to tableau moves
        if (gameState.waste.isNotEmpty()) {
            val wasteCard = gameState.waste.last()
            for (i in 0..6) {
                val pile = gameState.tableau[i]
                if (pile.isEmpty() && wasteCard.rank == Card.Rank.KING) {
                    return "Move King from waste to empty column ${i + 1}"
                } else if (pile.isNotEmpty() && wasteCard.canPlaceOn(pile.last())) {
                    return "Move ${wasteCard.rank.displayName} of ${wasteCard.suit.symbol} from waste to column ${i + 1}"
                }
            }
        }

        // Suggest drawing cards if deck available
        if (gameState.deck.isNotEmpty()) {
            return "Draw cards from the deck to see more options"
        }

        return ""
    }

    // Add this method to check game progress
    private fun hasGameProgressedRecently(): Boolean {
        val gameState = game.getGameState()
        val foundationTotal = gameState.foundations.sumOf { it.size }

        // Store previous foundation total and compare
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val previousFoundationTotal = sharedPref.getInt("prev_foundation_total", 0)
        val previousMoves = sharedPref.getInt("prev_moves", 0)

        // If 10+ moves passed without foundation progress, trigger analysis
        val noProgress = (gameState.moves - previousMoves >= 10) &&
                (foundationTotal == previousFoundationTotal)

        // Update stored values
        with(sharedPref.edit()) {
            putInt("prev_foundation_total", foundationTotal)
            putInt("prev_moves", gameState.moves)
            apply()
        }

        return !noProgress
    }


}