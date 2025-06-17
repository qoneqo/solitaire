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
                checkLoseCondition()
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
                checkLoseCondition()
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
    private fun checkLoseCondition() {
        if (game.isGameWon()) return // Don't check if already won

        val gameState = game.getGameState()

        // 1. Check if any card can be moved to foundations
        if (canAnyCardMoveToFoundation(gameState)) return

        // 2. Check if any tableau card can be moved to another tableau column
        if (canAnyTableauCardMove(gameState)) return

        // 3. Check if any waste card can be moved (if waste isn't empty)
        if (gameState.waste.isNotEmpty() && canWasteCardMove(gameState)) return

        // 4. Check if any foundation card can be moved back to tableau
        if (canAnyFoundationCardMoveToTableau(gameState)) return

        // 5. Check if drawing new cards could help (only if stock isn't empty)
        if (gameState.deck.isNotEmpty()) {
            // Simulate drawing a card to see if it would enable any moves
            val simulatedWasteCard = gameState.deck.last()
            if (wouldCardEnableMoves(simulatedWasteCard, gameState)) return
        }

        // If we get here, no moves are possible
        showLoseMessage()
    }

// Helper functions for the lose condition check:

    private fun canAnyCardMoveToFoundation(gameState: GameState): Boolean {
        // Check waste card
        if (gameState.waste.isNotEmpty()) {
            val wasteCard = gameState.waste.last()
            for (i in 0..3) {
                val foundation = gameState.foundations[i]
                val topFoundationCard = foundation.lastOrNull()
                if (wasteCard.canPlaceInFoundation(topFoundationCard)) {
                    return true
                }
            }
        }

        // Check tableau cards
        for (col in 0..6) {
            val pile = gameState.tableau[col]
            if (pile.isEmpty()) continue

            val topTableauCard = pile.last()
            if (!topTableauCard.isFaceUp) continue

            for (i in 0..3) {
                val foundation = gameState.foundations[i]
                val topFoundationCard = foundation.lastOrNull()
                if (topTableauCard.canPlaceInFoundation(topFoundationCard)) {
                    return true
                }
            }
        }

        return false
    }

    private fun canAnyTableauCardMove(gameState: GameState): Boolean {
        for (fromCol in 0..6) {
            val fromPile = gameState.tableau[fromCol]
            if (fromPile.isEmpty()) continue

            // Find the first face-up card (bottom of movable sequence)
            val firstFaceUpIndex = fromPile.indexOfFirst { it.isFaceUp }
            if (firstFaceUpIndex == -1) continue // No face-up cards in this column

            val movingCard = fromPile[firstFaceUpIndex]

            for (toCol in 0..6) {
                if (fromCol == toCol) continue

                val toPile = gameState.tableau[toCol]
                if (toPile.isEmpty()) {
                    if (movingCard.rank == Card.Rank.KING) return true
                } else {
                    if (movingCard.canPlaceOn(toPile.last())) return true
                }
            }
        }
        return false
    }

    private fun canWasteCardMove(gameState: GameState): Boolean {
        if (gameState.waste.isEmpty()) return false

        val wasteCard = gameState.waste.last()

        // Check waste to foundation
        for (i in 0..3) {
            val foundation = gameState.foundations[i]
            val topFoundationCard = foundation.lastOrNull()
            if (wasteCard.canPlaceInFoundation(topFoundationCard)) {
                return true
            }
        }

        // Check waste to tableau
        for (col in 0..6) {
            val pile = gameState.tableau[col]
            if (pile.isEmpty()) {
                if (wasteCard.rank == Card.Rank.KING) return true
            } else {
                if (wasteCard.canPlaceOn(pile.last())) return true
            }
        }

        return false
    }

    private fun canAnyFoundationCardMoveToTableau(gameState: GameState): Boolean {
        for (foundationIndex in 0..3) {
            val foundation = gameState.foundations[foundationIndex]
            if (foundation.isEmpty()) continue

            val foundationCard = foundation.last()

            for (tableauIndex in 0..6) {
                val tableauPile = gameState.tableau[tableauIndex]
                if (tableauPile.isEmpty()) {
                    if (foundationCard.rank == Card.Rank.KING) return true
                } else {
                    if (foundationCard.canPlaceOn(tableauPile.last())) return true
                }
            }
        }
        return false
    }

    private fun wouldCardEnableMoves(card: Card, gameState: GameState): Boolean {
        // Check if this card could be placed on any foundation
        for (i in 0..3) {
            val foundation = gameState.foundations[i]
            val topFoundationCard = foundation.lastOrNull()
            if (card.canPlaceInFoundation(topFoundationCard)) {
                return true
            }
        }

        // Check if this card could be placed on any tableau
        for (col in 0..6) {
            val pile = gameState.tableau[col]
            if (pile.isEmpty()) {
                if (card.rank == Card.Rank.KING) return true
            } else {
                if (card.canPlaceOn(pile.last())) return true
            }
        }

        return false
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
    // Add this function to show the lose message
    private fun showLoseMessage() {
        soundManager.playSound(R.raw.lose_sound)
        val toast = Toast.makeText(
            this,
            "Game Over! No more moves available",
            Toast.LENGTH_LONG
        ).apply {
            setGravity(Gravity.CENTER, 0, 0)
            view?.setBackgroundColor(Color.parseColor("#BB000000"))
            view?.findViewById<TextView>(android.R.id.message)?.apply {
                setTextColor(Color.WHITE)
                textSize = 18f
                setPadding(40, 40, 40, 40)
            }
        }
        toast.show()
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
            Toast.makeText(this, "Congratulations! You won!", Toast.LENGTH_LONG).show()
        }
    }
}