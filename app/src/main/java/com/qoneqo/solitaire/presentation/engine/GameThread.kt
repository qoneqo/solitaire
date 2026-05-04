package com.qoneqo.solitaire.presentation.engine

import android.graphics.Canvas
import android.view.SurfaceHolder

class GameThread(
    private val surfaceHolder: SurfaceHolder,
    private val gameView: GameSurfaceView
) : Thread() {

    @Volatile
    var isRunning = false

    override fun run() {
        var lastTime = System.nanoTime()
        val targetFrameTime = 1_000_000_000L / 60L // 60 FPS

        while (isRunning) {
            val now = System.nanoTime()
            val dt = (now - lastTime) / 1_000_000_000f
            lastTime = now

            // Update physics (snap-back animations)
            gameView.updatePhysics(dt)

            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockHardwareCanvas()
                if (canvas == null) {
                    canvas = surfaceHolder.lockCanvas()
                }
                if (canvas != null) {
                    synchronized(gameView.gameStateLock) {
                        gameView.render(canvas)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Sleep to maintain target FPS
            val timeTaken = System.nanoTime() - now
            val sleepTime = (targetFrameTime - timeTaken) / 1_000_000L
            if (sleepTime > 0) {
                try {
                    sleep(sleepTime)
                } catch (e: InterruptedException) {
                    // Ignore
                }
            }
        }
    }
}
