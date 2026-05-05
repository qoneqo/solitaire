package com.qoneqo.solitaire

import android.content.Intent
import android.net.Uri
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
        
        // Measure UI to avoid overlap with game surface
        val statsRow = findViewById<android.view.View>(R.id.statsRow)
        statsRow.post {
            gameSurfaceView.uiHeaderHeight = statsRow.bottom.toFloat()
            // Force engine re-init with new height
            gameSurfaceView.initEngine()
        }

        // Settings Button replaced New Game Button
        findViewById<android.view.View>(R.id.settingsButton).setOnClickListener {
            playSound(R.raw.card_place)
            showSettingsMenu()
        }

        findViewById<android.view.View>(R.id.undoButton).setOnClickListener {
            playSound(R.raw.card_place)
            gameSurfaceView.undo()
        }

        findViewById<android.view.View>(R.id.hintButton).setOnClickListener {
            playSound(R.raw.card_place)
            gameSurfaceView.showHint()
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
        
        viewModel.startTimer()
        
        // Forced New Game from Logbook on every start to ensure winnability
        gameSurfaceView.setupNewGame()
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
            AlertDialog.Builder(this)
                .setTitle("Bot Terjebak!")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
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
            "Auto Solve",
            if (soundManager.isSoundEnabled()) "Disable Sound" else "Enable Sound",
            "Check High Score",
            "Donate",
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
                    1 -> { // Auto Solve
                        gameSurfaceView.isAutoSolving = !gameSurfaceView.isAutoSolving
                    }
                    2 -> { // Toggle Sound
                        soundManager.setSoundEnabled(!soundManager.isSoundEnabled())
                    }
                    3 -> { // High Score
                        lifecycleScope.launch {
                            val topScores = viewModel.loadTopScores()
                            runOnUiThread {
                                val message = if (topScores.isEmpty()) {
                                    "No scores yet!"
                                } else {
                                    topScores.joinToString("\n") { 
                                        "Score: ${it.score} | Moves: ${it.moves} | Time: ${it.timeElapsedSeconds}s"
                                    }
                                }
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Top 10 High Scores")
                                    .setMessage(message)
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                        }
                    }
                    4 -> { // Donate
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://qoneqo.id/support"))
                        startActivity(intent)
                    }
                    5 -> { // Exit
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

    override fun onEmitParticles(x: Float, y: Float, color: Int) {
        runOnUiThread {
            gameSurfaceView.emitParticles(x, y, color)
        }
    }

    override fun onDestroy() {
        soundManager.release()
        super.onDestroy()
    }
}