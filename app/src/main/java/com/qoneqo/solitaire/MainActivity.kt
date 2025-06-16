package com.qoneqo.solitaire

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var game: SolitaireGame
    private lateinit var scoreText: TextView
    private lateinit var movesText: TextView
    private lateinit var stockPile: FrameLayout
    private lateinit var wastePile: FrameLayout
    private lateinit var foundations: Array<FrameLayout>
    private lateinit var tableauColumns: Array<LinearLayout>
    private lateinit var autoCompleteButton: Button

    // Selection state
    private var selectedTableauColumn: Int = -1
    private var selectedCardIndex: Int = -1
    private var selectedFromWaste: Boolean = false
    private var selectedFoundationIndex: Int = -1 // NEW: Track selected foundation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        game = SolitaireGame()
        setupClickListeners()
        updateUI()
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
                clearSelection()
                updateUI()
            }
        }

        // Waste pile click - select/deselect waste card
        wastePile.setOnClickListener {
            val gameState = game.getGameState()
            if (gameState.waste.isNotEmpty()) {
                if (selectedFromWaste) {
                    // Deselect waste
                    clearSelection()
                } else {
                    // Select waste card
                    clearSelection()
                    selectedFromWaste = true

                    // Try auto-move to foundation first
                    for (i in 0..3) {
                        if (game.moveWasteToFoundation(i)) {
                            clearSelection()
                            updateUI()
                            checkWinCondition()
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
                val gameState = game.getGameState()

                if (selectedFromWaste) {
                    // Move waste to foundation
                    if (game.moveWasteToFoundation(index)) {
                        clearSelection()
                        updateUI()
                        checkWinCondition()
                    }
                } else if (selectedTableauColumn != -1) {
                    // Move tableau to foundation
                    if (game.moveTableauToFoundation(selectedTableauColumn, index)) {
                        clearSelection()
                        updateUI()
                        checkWinCondition()
                    }
                } else if (selectedFoundationIndex != -1) {
                    // Deselect if clicking the same foundation
                    if (selectedFoundationIndex == index) {
                        clearSelection()
                        updateUI()
                    }
                } else {
                    // Select foundation if it has cards
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
                clearSelection()
                updateUI()
            }
        } else if (selectedFoundationIndex != -1) {
            // NEW: Move foundation to tableau
            if (game.moveFoundationToTableau(selectedFoundationIndex, columnIndex)) {
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

    private fun checkAutoCompleteAvailability(): Boolean {
        val gameState = game.getGameState()

        // Check if all cards are face-up
        val allFaceUp = gameState.tableau.all { pile ->
            pile.all { card -> card.isFaceUp }
        } && gameState.waste.all { it.isFaceUp } && gameState.deck.isEmpty()

        return allFaceUp && !game.isGameWon()
    }

    private fun performAutoComplete() {
        var movesMade = true

        while (movesMade && !game.isGameWon()) {
            movesMade = false

            // Try to move from waste to foundation
            if (game.getGameState().waste.isNotEmpty()) {
                for (foundationIndex in 0..3) {
                    if (game.moveWasteToFoundation(foundationIndex)) {
                        movesMade = true
                        break
                    }
                }
            }

            // Try to move from tableau to foundation
            if (!movesMade) {
                for (tableauIndex in 0..6) {
                    for (foundationIndex in 0..3) {
                        if (game.moveTableauToFoundation(tableauIndex, foundationIndex)) {
                            movesMade = true
                            break
                        }
                    }
                    if (movesMade) break
                }
            }

            // Update UI after each batch of moves
            if (movesMade) {
                updateUI()
                // Small delay to show the animation effect
                Thread.sleep(100)
            }
        }

        // Final UI update
        updateUI()
        checkWinCondition()
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
                clearSelection()
                updateUI()
            }
        } else if (selectedFoundationIndex != -1) {
            // NEW: Move foundation to tableau
            if (game.moveFoundationToTableau(selectedFoundationIndex, columnIndex)) {
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
            Toast.makeText(this, "Congratulations! You won!", Toast.LENGTH_LONG).show()
        }
    }
}