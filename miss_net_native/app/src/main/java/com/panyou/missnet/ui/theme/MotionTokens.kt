package com.panyou.missnet.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

object MotionTokens {
    // --- Easings ---
    // Emphasized: For Hero transitions and navigation (expressive)
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f) // Incoming
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f) // Outgoing

    // Standard: For small UI elements (icons, selection)
    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)

    // --- Durations ---
    const val DurationShort1 = 50
    const val DurationShort2 = 100
    const val DurationShort3 = 150
    const val DurationShort4 = 200

    const val DurationMedium1 = 250
    const val DurationMedium2 = 300
    const val DurationMedium3 = 350
    const val DurationMedium4 = 400

    const val DurationLong1 = 450
    const val DurationLong2 = 500
    const val DurationLong3 = 550
    const val DurationLong4 = 600

    // --- Helpers ---
    fun <T> enter(duration: Int = DurationLong2) = tween<T>(
        durationMillis = duration,
        easing = EmphasizedDecelerate
    )

    fun <T> exit(duration: Int = DurationMedium4) = tween<T>( // Exits are faster
        durationMillis = duration,
        easing = EmphasizedAccelerate
    )
    
    fun <T> standard(duration: Int = DurationMedium1) = tween<T>(
        durationMillis = duration,
        easing = Standard
    )
}
