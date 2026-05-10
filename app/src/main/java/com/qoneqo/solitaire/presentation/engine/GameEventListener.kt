package com.qoneqo.solitaire.presentation.engine

interface GameEventListener {
    fun onScoreChanged(score: Int)
    fun onMovesChanged(moves: Int)
    fun onGameWon()
    fun onWinAnimationStarted()
    fun onBotStuck(message: String)
    fun playSound(soundResId: Int)
    fun onEmitParticles(x: Float, y: Float, color: Int)
    fun onAutoFinishAvailable()
}
