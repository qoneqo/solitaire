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
        val btnHighScore = dialogView.findViewById<Button>(R.id.btnHighScore)
        val btnDonate = dialogView.findViewById<Button>(R.id.btnDonate)
        val btnExit = dialogView.findViewById<Button>(R.id.btnExit)

        btnAutoSolve.text = "Auto Solve: ${if (gameSurfaceView.isAutoSolving) "ON" else "OFF"}"
        btnSound.text = "Sound: ${if (soundManager.isSoundEnabled()) "ON" else "OFF"}"

        val dialog = AlertDialog.Builder(this, R.style.CozyDialogTheme)
            .setView(dialogView)
            .create()

        btnNewGame.setOnClickListener {
            dialog.dismiss()
            viewModel.resetGame()
            gameSurfaceView.setupNewGame()
        }

        btnAutoSolve.setOnClickListener {
            gameSurfaceView.isAutoSolving = !gameSurfaceView.isAutoSolving
            btnAutoSolve.text = "Auto Solve: ${if (gameSurfaceView.isAutoSolving) "ON" else "OFF"}"
        }

        btnSound.setOnClickListener {
            soundManager.setSoundEnabled(!soundManager.isSoundEnabled())
            btnSound.text = "Sound: ${if (soundManager.isSoundEnabled()) "ON" else "OFF"}"
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

                    closeButton.setOnClickListener { hsDialog.dismiss() }
                    hsDialog.show()
                    val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
                    hsDialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
            }
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