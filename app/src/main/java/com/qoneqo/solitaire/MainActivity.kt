package com.qoneqo.solitaire

import android.os.Bundle
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

class MainActivity : AppCompatActivity(), GameEventListener {

    private val viewModel: GameViewModel by viewModels()
    private lateinit var gameSurfaceView: GameSurfaceView
    private lateinit var scoreText: TextView
    private lateinit var movesText: TextView
    private lateinit var timerText: TextView
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        soundManager = SoundManager(this)

        gameSurfaceView = findViewById(R.id.gameSurfaceView)
        scoreText = findViewById(R.id.scoreText)
        movesText = findViewById(R.id.movesText)
        timerText = findViewById(R.id.timerText)

        gameSurfaceView.gameEventListener = this
        
        // Settings Button replaced New Game Button
        findViewById<android.view.View>(R.id.settingsButton).setOnClickListener {
            showSettingsMenu()
        }

        findViewById<android.view.View>(R.id.undoButton).setOnClickListener {
            gameSurfaceView.undo()
        }

        // Observe ViewModel states
        lifecycleScope.launch {
            viewModel.score.collect {
                scoreText.text = "Score: $it"
            }
        }
        lifecycleScope.launch {
            viewModel.moves.collect {
                movesText.text = "Moves: $it"
            }
        }
        lifecycleScope.launch {
            viewModel.timeSeconds.collect {
                timerText.text = "Time: ${it}s"
            }
        }

        viewModel.startTimer()
        
        // Try to load game
        lifecycleScope.launch {
            val savedState = viewModel.loadGame()
            if (savedState != null) {
                gameSurfaceView.loadGameState(savedState)
                viewModel.updateScore(savedState.score)
                viewModel.updateMoves(savedState.moves)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        synchronized(gameSurfaceView.gameStateLock) {
            val stateToSave = gameSurfaceView.gameState
            // Update state with current score and moves before saving
            stateToSave.score = viewModel.score.value
            stateToSave.moves = viewModel.moves.value
            viewModel.saveGame(stateToSave)
        }
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

    override fun onGameWon() {
        runOnUiThread {
            viewModel.onGameWon()
            soundManager.playSound(R.raw.win_sound)
            AlertDialog.Builder(this)
                .setTitle("You Won!")
                .setMessage("Congratulations! You won the game in ${viewModel.timeSeconds.value} seconds with ${viewModel.moves.value} moves.")
                .setPositiveButton("New Game") { _, _ ->
                    viewModel.resetGame()
                    gameSurfaceView.setupNewGame()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun showSettingsMenu() {
        val options = arrayOf(
            "New Game",
            if (soundManager.isSoundEnabled()) "Disable Sound" else "Enable Sound",
            "Check High Score",
            "Exit"
        )

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // New Game
                        viewModel.resetGame()
                        gameSurfaceView.setupNewGame()
                    }
                    1 -> { // Toggle Sound
                        soundManager.setSoundEnabled(!soundManager.isSoundEnabled())
                    }
                    2 -> { // High Score
                        lifecycleScope.launch {
                            val best = viewModel.loadHighScore()
                            runOnUiThread {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("High Score")
                                    .setMessage("Your best score is: ${best ?: 0}")
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                        }
                    }
                    3 -> { // Exit
                        finish()
                    }
                }
            }
            .show()
    }

    override fun playSound(soundResId: Int) {
        runOnUiThread {
            soundManager.playSound(soundResId)
        }
    }

    override fun onDestroy() {
        soundManager.release()
        super.onDestroy()
    }
}