package com.tapmemory.engine

import android.graphics.Color

/**
 * The five-symbol input alphabet, drawn straight from the X3 Pro right temple
 * pad: four swipe directions plus the firm click. Everything in the game — the
 * sequences to memorize, the colors, the tones, the on-screen board — is built
 * on these five tokens laid out as a compass cross (click = center).
 */
object Tok {
    const val UP = 0
    const val DOWN = 1
    const val LEFT = 2
    const val RIGHT = 3
    const val TAP = 4   // the center click (KEYCODE_BUTTON_A / DPAD_CENTER)
    const val COUNT = 5

    val NAMES = arrayOf("UP", "DOWN", "LEFT", "RIGHT", "TAP")
    val GESTURE = arrayOf("swipe up", "swipe down", "swipe left", "swipe right", "click")

    // Neon palette, waveguide-tuned and mutually distinct (no orange — it
    // washes toward yellow on the additive display; magenta instead).
    val COLOR = intArrayOf(
        Color.rgb(48, 219, 240),   // UP    cyan
        Color.rgb(255, 70, 215),   // DOWN  magenta
        Color.rgb(70, 230, 96),    // LEFT  green
        Color.rgb(255, 222, 48),   // RIGHT amber
        Color.rgb(170, 140, 255),  // TAP   violet
    )

    // Board positions in the 640x480 logical canvas (compass cross).
    val PX = floatArrayOf(320f, 320f, 205f, 435f, 320f)
    val PY = floatArrayOf(150f, 350f, 250f, 250f, 250f)

    // Major-pentatonic pitches: up = high, down = low, tap = center root.
    // Any random sequence therefore sounds musical.
    val FREQ = floatArrayOf(
        440.00f,  // UP    A4
        261.63f,  // DOWN  C4
        329.63f,  // LEFT  E4
        392.00f,  // RIGHT G4
        329.63f,  // TAP   E4 (root, distinct timbre)
    )

    // Stereo pan per token (-1 left .. +1 right) for spatial audio cues.
    val PAN = floatArrayOf(0f, 0f, -0.85f, 0.85f, 0f)

    fun mix(a: Int, b: Int, t: Float): Int {
        val u = 1f - t
        return Color.rgb(
            (Color.red(a) * u + Color.red(b) * t).toInt(),
            (Color.green(a) * u + Color.green(b) * t).toInt(),
            (Color.blue(a) * u + Color.blue(b) * t).toInt(),
        )
    }
}
