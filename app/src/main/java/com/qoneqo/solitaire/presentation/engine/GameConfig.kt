package com.qoneqo.solitaire.presentation.engine

object GameConfig {
    // Physics & Animations
    const val SNAP_SPEED = 15f
    const val AUTO_COMPLETE_DELAY = 0.1f
    const val FRAME_RATE = 60L
    const val TARGET_FRAME_TIME_NS = 1_000_000_000L / FRAME_RATE

    // Layout Margins (Ratios of screen size)
    const val MARGIN_X_RATIO = 0.02f
    const val MARGIN_Y_RATIO = 0.05f
    const val TOP_UI_OFFSET_RATIO = 0.18f // Avoid header/stats overlap
    
    // Visuals
    const val EMPTY_SLOT_COLOR = "#c2d0cd"
    const val BACKGROUND_COLOR = "#9fb5b0"
    const val TABLEAU_BORDER_COLOR = "#4D000000"
    const val EMPTY_SLOT_BORDER_ALPHA = 60
    const val CARD_CORNER_RADIUS = 16f
    
    // Gameplay
    const val SCORE_FOUNDATION = 10
    const val SCORE_TABLEAU = 5
}
