package com.qoneqo.solitaire

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.qoneqo.solitaire.domain.GameState
import com.qoneqo.solitaire.presentation.GameViewModel
import com.qoneqo.solitaire.presentation.engine.GameEventListener
import com.qoneqo.solitaire.presentation.engine.GameSurfaceView
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import com.qoneqo.solitaire.presentation.HighScoreAdapter
import com.qoneqo.solitaire.presentation.TableColorAdapter
import com.qoneqo.solitaire.presentation.engine.GameConfig
import androidx.recyclerview.widget.GridLayoutManager
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import android.view.View

class MainActivity : AppCompatActivity(), GameEventListener {

    private val viewModel: GameViewModel by viewModels()
    private lateinit var gameSurfaceView: GameSurfaceView
    private lateinit var scoreText: TextView
    private lateinit var movesText: TextView
    private lateinit var timerText: TextView
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val mainLayout = findViewById<View>(R.id.mainLayout)
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply padding to avoid status bar and side notches
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            
            val density = resources.displayMetrics.density
            val margin16 = (16 * density).toInt()

            // Bottom margin for FABs to avoid navigation bar
            val fabIds = listOf(R.id.newGameButton, R.id.undoButton, R.id.hintButton, R.id.settingsButton)
            fabIds.forEach { id ->
                findViewById<View>(id).updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = systemBars.bottom + margin16
                }
            }
            
            // Adjust auto-finish button which is above the main row
            findViewById<View>(R.id.autoFinishButton).updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = margin16
            }
            
            insets
        }

        soundManager = SoundManager(this)

        gameSurfaceView = findViewById(R.id.gameSurfaceView)
        val savedBgColor = getSharedPreferences("settings", MODE_PRIVATE).getString("bg_color", GameConfig.BACKGROUND_COLOR)
        val colorInt = android.graphics.Color.parseColor(savedBgColor)
        gameSurfaceView.tableColor = colorInt
        findViewById<android.view.View>(R.id.mainLayout).setBackgroundColor(colorInt)
        scoreText = findViewById(R.id.scoreText)
        movesText = findViewById(R.id.movesText)
        timerText = findViewById(R.id.timerText)

        // Setup Intro Screen
        val introLayout = findViewById<android.view.View>(R.id.introLayout)
        val btnStartGame = findViewById<android.view.View>(R.id.btnStartGame)
        val btnHowToPlay = findViewById<android.view.View>(R.id.btnHowToPlay)
        val instructionsCard = findViewById<android.view.View>(R.id.instructionsCard)
        introLayout.setBackgroundColor(colorInt)

        btnHowToPlay.setOnClickListener {
            playSound(R.raw.bubble_pop)
            if (instructionsCard.visibility == android.view.View.VISIBLE) {
                instructionsCard.visibility = android.view.View.GONE
                (it as android.widget.Button).text = "How to Play?"
            } else {
                instructionsCard.visibility = android.view.View.VISIBLE
                (it as android.widget.Button).text = "Got it!"
            }
        }
        val btnInteractiveTutorial = findViewById<android.view.View>(R.id.btnInteractiveTutorial)
        val tutorialOverlay = findViewById<android.view.View>(R.id.tutorialOverlay)
        val btnNextTutorial = findViewById<android.view.View>(R.id.btnNextTutorial)
        val btnSkipTutorial = findViewById<android.view.View>(R.id.btnSkipTutorial)
        val spotlightView = findViewById<com.qoneqo.solitaire.presentation.SpotlightView>(R.id.spotlightView)
        val tutorialText = findViewById<android.widget.TextView>(R.id.tutorialText)
        val tutorialCard = findViewById<android.view.View>(R.id.tutorialCard)

        var currentStep = 0
        val steps = listOf(
            Triple("The Deck", "Tap here to draw new cards from the stock pile.", android.graphics.RectF(0.05f, 0.08f, 0.25f, 0.22f)),
            Triple("Foundations", "Move cards here from Ace to King to win the game!", android.graphics.RectF(0.45f, 0.08f, 0.95f, 0.22f)),
            Triple("Tableau", "Build sequences here in alternating colors and descending order.", android.graphics.RectF(0.05f, 0.35f, 0.95f, 0.75f)),
            Triple("Helpful Tools", "Use Hint if you're stuck, or Undo to fix a mistake!", android.graphics.RectF(0.15f, 0.85f, 0.85f, 0.98f))
        )

        fun updateTutorialStep() {
            if (currentStep >= steps.size) {
                tutorialOverlay.visibility = android.view.View.GONE
                introLayout.visibility = android.view.View.VISIBLE
                return
            }
            val step = steps[currentStep]
            tutorialText.text = "${step.first}\n\n${step.second}"
            
            spotlightView.post {
                val w = spotlightView.width.toFloat()
                val h = spotlightView.height.toFloat()
                val rect = android.graphics.RectF(
                    step.third.left * w,
                    step.third.top * h,
                    step.third.right * w,
                    step.third.bottom * h
                )
                spotlightView.setSpotlightRect(rect)

                // Move card to avoid spotlight
                val params = tutorialCard.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                params.verticalBias = if (step.third.centerY() > 0.5f) 0.2f else 0.7f
                tutorialCard.layoutParams = params
            }
        }

        btnInteractiveTutorial.setOnClickListener {
            playSound(R.raw.bubble_pop)
            introLayout.visibility = android.view.View.GONE
            tutorialOverlay.visibility = android.view.View.VISIBLE
            currentStep = 0
            updateTutorialStep()
        }

        btnNextTutorial.setOnClickListener {
            playSound(R.raw.pop)
            currentStep++
            updateTutorialStep()
        }

        btnSkipTutorial.setOnClickListener {
            playSound(R.raw.bubble_pop)
            tutorialOverlay.visibility = android.view.View.GONE
            introLayout.visibility = android.view.View.VISIBLE
        }

        btnStartGame.setOnClickListener {
            playSound(R.raw.pop)
            introLayout.animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction {
                    introLayout.visibility = android.view.View.GONE
                    viewModel.startTimer()
                }
        }

        gameSurfaceView.gameEventListener = this
        
        // Measure UI to avoid overlap with game surface
        val statsRow = findViewById<android.view.View>(R.id.statsRow)
        statsRow.post {
            gameSurfaceView.uiHeaderHeight = statsRow.bottom.toFloat()
            // Force engine re-init with new height
            gameSurfaceView.initEngine()
        }

        // New Game Button
        findViewById<android.view.View>(R.id.newGameButton).setOnClickListener {
            playSound(R.raw.pop)
            viewModel.resetGame()
            resetButtons()
            gameSurfaceView.setupNewGame()
        }

        // Settings Button replaced New Game Button
        findViewById<android.view.View>(R.id.settingsButton).setOnClickListener {
            playSound(R.raw.tapping_glass)
            showSettingsMenu()
        }

        findViewById<android.view.View>(R.id.undoButton).setOnClickListener {
            playSound(R.raw.bubble_pop)
            resetButtons()
            gameSurfaceView.undo()
        }

        val hintButton = findViewById<android.view.View>(R.id.hintButton)
        val autoFinishButton = findViewById<android.view.View>(R.id.autoFinishButton)

        hintButton.setOnClickListener {
            playSound(R.raw.bubble_pop)
            gameSurfaceView.showHint()
        }

        autoFinishButton.setOnClickListener {
            playSound(R.raw.bubble_pop)
            gameSurfaceView.startFastForward()
            autoFinishButton.visibility = android.view.View.GONE
        }

        // Observe ViewModel states
        lifecycleScope.launch {
            viewModel.score.collect {
                scoreText.text = "$it"
            }
        }
        lifecycleScope.launch {
            viewModel.moves.collect {
                movesText.text = "$it"
            }
        }
        lifecycleScope.launch {
            viewModel.timeSeconds.collect {
                timerText.text = "${it}s"
            }
        }

        // Timer now starts when btnStartGame is clicked
        
        // Forced New Game from Logbook on every start to ensure winnability
        resetButtons()
        gameSurfaceView.setupNewGame()
    }

    private fun resetButtons() {
        runOnUiThread {
            findViewById<android.view.View>(R.id.hintButton).visibility = android.view.View.VISIBLE
            findViewById<android.view.View>(R.id.autoFinishButton).visibility = android.view.View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        // We no longer save random states to avoid overwriting winnable games
    }

    override fun onScoreChanged(score: Int) {
        runOnUiThread {
            viewModel.updateScore(score)
        }
    }

    override fun onMovesChanged(moves: Int) {
        runOnUiThread {
            viewModel.updateMoves(moves)
        }
    }

    override fun onBotStuck(message: String) {
        runOnUiThread {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bot_stuck, null)
            val messageView = dialogView.findViewById<TextView>(R.id.stuckMessage)
            val okButton = dialogView.findViewById<Button>(R.id.okButton)
            
            messageView.text = message
            
            val dialog = AlertDialog.Builder(this, R.style.CozyDialogTheme)
                .setView(dialogView)
                .create()
                
            okButton.setOnClickListener { dialog.dismiss() }
            dialog.show()
            
            val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
            dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onGameWon() {
        runOnUiThread {
            viewModel.onGameWon()
            soundManager.playSound(R.raw.win_sound)
            
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_won, null)
            val messageView = dialogView.findViewById<TextView>(R.id.winMessage)
            val scoreView = dialogView.findViewById<TextView>(R.id.finalScore)
            val timeView = dialogView.findViewById<TextView>(R.id.finalTime)
            val newGameButton = dialogView.findViewById<Button>(R.id.newGameButton)
            
            scoreView.text = "${viewModel.score.value}"
            timeView.text = "${viewModel.timeSeconds.value}s"
            messageView.text = "Congratulations! You won the game in ${viewModel.moves.value} moves."
            
            val dialog = AlertDialog.Builder(this, R.style.CozyDialogTheme)
                .setView(dialogView)
                .setCancelable(false)
                .create()
                
            newGameButton.setOnClickListener {
                dialog.dismiss()
                viewModel.resetGame()
                resetButtons()
                gameSurfaceView.setupNewGame()
            }
            dialog.show()
            
            val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
            dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun showSettingsMenu() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val btnNewGame = dialogView.findViewById<Button>(R.id.btnNewGame)
        val btnAutoSolve = dialogView.findViewById<Button>(R.id.btnAutoSolve)
        val btnSound = dialogView.findViewById<Button>(R.id.btnSound)
        val btnMusic = dialogView.findViewById<Button>(R.id.btnMusic)
        val btnHighScore = dialogView.findViewById<Button>(R.id.btnHighScore)
        val btnDonate = dialogView.findViewById<Button>(R.id.btnDonate)
        val btnExit = dialogView.findViewById<Button>(R.id.btnExit)

        btnAutoSolve.text = "Auto Solve: ${if (gameSurfaceView.isAutoSolving) "ON" else "OFF"}"
        btnSound.text = "Sound FX: ${if (soundManager.isSoundEnabled()) "ON" else "OFF"}"
        btnMusic.text = "Ambient Music: ${if (soundManager.isMusicEnabled()) "ON" else "OFF"}"

        val dialog = AlertDialog.Builder(this, R.style.CozyDialogTheme)
            .setView(dialogView)
            .create()
        
        viewModel.stopTimer()
        dialog.setOnDismissListener {
            viewModel.startTimer()
        }
        
        val currentColorStr = getSharedPreferences("settings", MODE_PRIVATE).getString("bg_color", GameConfig.BACKGROUND_COLOR) ?: GameConfig.BACKGROUND_COLOR
        val alphaColor = if (currentColorStr.length == 7 && currentColorStr.startsWith("#")) {
            android.graphics.Color.parseColor("#BF" + currentColorStr.substring(1))
        } else gameSurfaceView.tableColor
        
        dialogView.setBackgroundColor(alphaColor)

        btnNewGame.setOnClickListener {
            dialog.dismiss()
            viewModel.resetGame()
            resetButtons()
            gameSurfaceView.setupNewGame()
        }

        btnAutoSolve.setOnClickListener {
            gameSurfaceView.isAutoSolving = !gameSurfaceView.isAutoSolving
            btnAutoSolve.text = "Auto Solve: ${if (gameSurfaceView.isAutoSolving) "ON" else "OFF"}"
            dialog.dismiss()
        }

        btnSound.setOnClickListener {
            soundManager.setSoundEnabled(!soundManager.isSoundEnabled())
            btnSound.text = "Sound FX: ${if (soundManager.isSoundEnabled()) "ON" else "OFF"}"
        }

        btnMusic.setOnClickListener {
            soundManager.setMusicEnabled(!soundManager.isMusicEnabled())
            btnMusic.text = "Ambient Music: ${if (soundManager.isMusicEnabled()) "ON" else "OFF"}"
        }

        btnHighScore.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                val topScores = viewModel.loadTopScores()
                runOnUiThread {
                    val hsView = LayoutInflater.from(this@MainActivity).inflate(R.layout.dialog_high_score, null)
                    val recyclerView = hsView.findViewById<RecyclerView>(R.id.scoresRecyclerView)
                    val closeButton = hsView.findViewById<Button>(R.id.closeButton)
                    val titleView = hsView.findViewById<TextView>(R.id.dialogTitle)

                    if (topScores.isEmpty()) {
                        titleView.text = "No scores yet!"
                    } else {
                        recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                        recyclerView.adapter = HighScoreAdapter(topScores)
                    }

                    val hsDialog = AlertDialog.Builder(this@MainActivity, R.style.CozyDialogTheme)
                        .setView(hsView)
                        .create()
                    
                    hsView.setBackgroundColor(alphaColor)
                    
                    viewModel.stopTimer()
                    closeButton.setOnClickListener { hsDialog.dismiss() }
                    hsDialog.setOnDismissListener {
                        viewModel.startTimer()
                    }
                    hsDialog.show()
                    
                    val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
                    hsDialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
            }
        }

        // Background Color Selection
        dialogView.findViewById<Button>(R.id.btnChangeColor).setOnClickListener {
            showColorPickerModal(dialogView)
        }

        btnDonate.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://qoneqo.id/support"))
            startActivity(intent)
        }

        btnExit.setOnClickListener {
            finish()
        }

        dialog.show()
        
        val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun updateTableColor(colorStr: String, viewToUpdate: android.view.View? = null) {
        val color = android.graphics.Color.parseColor(colorStr)
        gameSurfaceView.tableColor = color
        
        // Use 75% alpha for dialog backgrounds
        val alphaColor = if (colorStr.length == 7 && colorStr.startsWith("#")) {
            android.graphics.Color.parseColor("#BF" + colorStr.substring(1))
        } else color
        
        viewToUpdate?.setBackgroundColor(alphaColor)
        findViewById<android.view.View>(R.id.mainLayout).setBackgroundColor(color)
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString("bg_color", colorStr).apply()
    }

    private fun showColorPickerModal(settingsDialogView: android.view.View) {
        val colorPickerView = LayoutInflater.from(this).inflate(R.layout.dialog_color_picker, null)
        val recyclerView = colorPickerView.findViewById<RecyclerView>(R.id.colorRecyclerView)
        val btnCancel = colorPickerView.findViewById<Button>(R.id.btnCancel)
        val root = colorPickerView.findViewById<android.view.View>(R.id.colorPickerRoot)

        val colors = listOf(
            "#9fb5b0", "#94a6c2", "#b09fb5", "#b5a89f",
            "#2d3436", "#27ae60", "#2c3e50", "#c0392b",
            "#e67e22", "#fab1a0", "#55efc4", "#a29bfe",
            "#74b9ff", "#ffeaa7", "#556b2f", "#483d8b"
        )

        val cpDialog = AlertDialog.Builder(this, R.style.CozyDialogTheme)
            .setView(colorPickerView)
            .create()

        val currentColorStr = getSharedPreferences("settings", MODE_PRIVATE).getString("bg_color", GameConfig.BACKGROUND_COLOR) ?: GameConfig.BACKGROUND_COLOR
        val alphaColor = if (currentColorStr.length == 7 && currentColorStr.startsWith("#")) {
            android.graphics.Color.parseColor("#BF" + currentColorStr.substring(1))
        } else gameSurfaceView.tableColor

        root.setBackgroundColor(alphaColor)

        recyclerView.layoutManager = GridLayoutManager(this, 4)
        recyclerView.adapter = TableColorAdapter(colors) { selectedColor ->
            updateTableColor(selectedColor, settingsDialogView)
            val newAlphaColor = if (selectedColor.length == 7 && selectedColor.startsWith("#")) {
                android.graphics.Color.parseColor("#BF" + selectedColor.substring(1))
            } else android.graphics.Color.parseColor(selectedColor)
            root.setBackgroundColor(newAlphaColor)
            cpDialog.dismiss()
        }

        btnCancel.setOnClickListener { cpDialog.dismiss() }
        cpDialog.show()

        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        cpDialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun playSound(soundResId: Int) {
        runOnUiThread {
            soundManager.playSound(soundResId)
        }
    }

    override fun onEmitParticles(x: Float, y: Float, color: Int) {
        runOnUiThread {
            gameSurfaceView.emitParticles(x, y, color)
        }
    }

    override fun onAutoFinishAvailable() {
        runOnUiThread {
            val autoFinishButton = findViewById<android.view.View>(R.id.autoFinishButton)
            if (autoFinishButton.visibility != android.view.View.VISIBLE) {
                autoFinishButton.visibility = android.view.View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        soundManager.release()
        super.onDestroy()
    }
}