package com.qoneqo.solitaire.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qoneqo.solitaire.data.AppDatabase
import com.qoneqo.solitaire.data.SaveStateEntity
import com.qoneqo.solitaire.data.StatEntity
import com.qoneqo.solitaire.domain.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val saveStateDao = db.saveStateDao()
    private val statDao = db.statDao()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _moves = MutableStateFlow(0)
    val moves: StateFlow<Int> = _moves.asStateFlow()

    private val _timeSeconds = MutableStateFlow(0L)
    val timeSeconds: StateFlow<Long> = _timeSeconds.asStateFlow()

    var timerJob: kotlinx.coroutines.Job? = null

    fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                _timeSeconds.value += 1
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun updateScore(newScore: Int) {
        _score.value = newScore
    }

    fun updateMoves(newMoves: Int) {
        _moves.value = newMoves
    }

    fun saveGame(gameState: GameState) {
        viewModelScope.launch {
            val json = Json.encodeToString(gameState)
            saveStateDao.saveGame(SaveStateEntity(id = 1, stateJson = json))
        }
    }

    suspend fun loadGame(): GameState? {
        return withContext(Dispatchers.IO) {
            val entity = saveStateDao.loadGame()
            if (entity != null) {
                try {
                    Json.decodeFromString<GameState>(entity.stateJson)
                } catch (e: Exception) {
                    null
                }
            } else null
        }
    }

    fun onGameWon() {
        stopTimer()
        viewModelScope.launch {
            statDao.insertStat(
                StatEntity(
                    score = _score.value,
                    moves = _moves.value,
                    timeElapsedSeconds = _timeSeconds.value,
                    isWin = true
                )
            )
        }
    }

    fun resetGame() {
        _score.value = 0
        _moves.value = 0
        _timeSeconds.value = 0L
        stopTimer()
        startTimer()
    }
}
