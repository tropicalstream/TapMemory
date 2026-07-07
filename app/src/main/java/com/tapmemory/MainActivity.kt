package com.tapmemory

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.tapmemory.audio.Audio
import com.tapmemory.engine.AudioMode
import com.tapmemory.engine.GameEngine
import com.tapmemory.engine.GameHost
import com.tapmemory.engine.GameState
import com.tapmemory.engine.Tok
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Input contract (FABLE_X3_STARTER_GUIDE Part II):
 *  - Right temple pad (cyttsp5_mt): a swipe arrives as touch deltas — we
 *    classify the finished gesture into one of four directions (the game's
 *    directional tokens), or feed continuous deltas to menus.
 *  - The firm temple click arrives as a KEY (KEYCODE_BUTTON_A / DPAD_CENTER),
 *    never a touch: it's the center TAP token, or confirms, or (doubled) opens
 *    settings. Double-tap gap 40..320 ms; the 40 ms floor filters keycode echo.
 *  - Left temple pad (cyttsp6_mt): the system volume pad — swallowed.
 *  - Plain touchscreens (phone testing) reuse the same gestures.
 */
class MainActivity : Activity(), GameHost {

    private val TAG = "TapMemory"

    private lateinit var store: SettingsStore
    private lateinit var audio: Audio
    private lateinit var engine: GameEngine
    private lateinit var renderer: Renderer
    private lateinit var gameView: GameView
    private lateinit var sbsRoot: BinocularSbsLayout

    private val handler = Handler(Looper.getMainLooper())

    // Click (KEY + touch-tap) gesture state.
    private var keyDownAt = 0L
    private var lastTapUpAt = 0L
    private var lastTapGuard = 0L
    private var pendingClick: Runnable? = null

    // Swipe gesture state.
    private var touchActive = false
    private var touchStartT = 0L
    private var sumX = 0f
    private var sumY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dropFirst = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        audio = Audio(this).also { it.loadAsync() }
        engine = GameEngine(store, this)
        renderer = Renderer(engine, store)
        gameView = GameView(this, engine, renderer)
        sbsRoot = BinocularSbsLayout(this).apply { addView(gameView) }
        setContentView(sbsRoot)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        applySettings()
        engine.boot()
    }

    // ------------------------------------------------------------ GameHost

    override fun applySettings() {
        audio.volume = store.soundVolume / 10f
        gameView.frameCap30 = store.frameCap30
        val sbs = store.sbs
        sbsRoot.sbsEnabled = sbs
        engine.particles.budget = when (store.particlesLevel) { 0 -> 0.4f; 2 -> 1.7f; else -> 1f }
        Log.i(TAG, "applySettings sbs=$sbs model=${Build.MODEL} mfr=${Build.MANUFACTURER} product=${Build.PRODUCT}")
    }

    override fun playToken(token: Int, audioMode: AudioMode) = audio.playToken(token, audioMode)

    override fun ui(sound: Int, pitch: Float, vol: Float) = audio.ui(sound, pitch, vol)

    // --------------------------------------------------------------- input

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isTapKey = event.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_ENTER
        if (isTapKey) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) keyDownAt = SystemClock.uptimeMillis()
                KeyEvent.ACTION_UP -> {
                    val now = SystemClock.uptimeMillis()
                    if (now - keyDownAt <= 400) handleClick(now)
                }
            }
            return true
        }
        // The four DPAD keys can appear on some builds — treat as swipe tokens.
        if (event.action == KeyEvent.ACTION_DOWN && engine.state == GameState.INPUT) {
            val dirTok = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> Tok.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> Tok.DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> Tok.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> Tok.RIGHT
                else -> -1
            }
            if (dirTok >= 0) { emitDirection(dirTok); return true }
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            if (engine.onBack()) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleClick(now: Long) {
        if (now - lastTapGuard < 35) return
        lastTapGuard = now
        val gap = now - lastTapUpAt
        if (gap in 40..320) {
            pendingClick?.let { handler.removeCallbacks(it) }
            pendingClick = null
            lastTapUpAt = 0
            engine.doubleTap()
            return
        }
        lastTapUpAt = now
        // Safe Tap defers the single click so a forming double-tap can cancel it.
        if (store.safeTap && couldBeSettings()) {
            val r = Runnable { pendingClick = null; engine.click() }
            pendingClick = r
            handler.postDelayed(r, 300)
        } else {
            engine.click()
        }
    }

    // Only bother deferring where a double-tap (settings) is meaningful.
    private fun couldBeSettings() = true

    private val flipH get() = store.flipHorizontal
    private val flipV get() = store.flipVertical

    private fun continuousMode() = engine.settingsOpen || engine.state == GameState.MENU

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val name = ev.device?.name ?: ""
        if (name.contains("cyttsp6", ignoreCase = true)) return true // left volume pad

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchActive = true
                touchStartT = SystemClock.uptimeMillis()
                sumX = 0f; sumY = 0f
                lastX = ev.x; lastY = ev.y
                dropFirst = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchActive) {
                    touchActive = true; touchStartT = SystemClock.uptimeMillis()
                    sumX = 0f; sumY = 0f; lastX = ev.x; lastY = ev.y; dropFirst = true
                } else {
                    var dx = ev.x - lastX
                    var dy = ev.y - lastY
                    lastX = ev.x; lastY = ev.y
                    if (dropFirst) { dropFirst = false } else {
                        if (flipH) dx = -dx
                        if (flipV) dy = -dy
                        sumX += dx; sumY += dy
                        if (continuousMode()) engine.navSwipe(dx, dy)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (touchActive) resolveGesture(SystemClock.uptimeMillis())
                touchActive = false
            }
            MotionEvent.ACTION_CANCEL -> {
                engine.endSwipe()
                touchActive = false
            }
        }
        return true
    }

    private fun resolveGesture(now: Long) {
        val dist = sqrt(sumX * sumX + sumY * sumY)
        val threshold = max(55f, 0.11f * resources.displayMetrics.widthPixels) / store.swipeSens
        if (continuousMode()) {
            engine.endSwipe()
            // A near-still touch in a menu = a confirm tap (phone testing path).
            if (dist < threshold * 0.6f && now - touchStartT <= 320) handleClick(now)
            return
        }
        if (dist >= threshold) {
            val tok = if (abs(sumX) >= abs(sumY)) {
                if (sumX > 0) Tok.RIGHT else Tok.LEFT
            } else {
                if (sumY < 0) Tok.UP else Tok.DOWN
            }
            emitDirection(tok)
        } else if (now - touchStartT <= 320) {
            // Small movement = a tap (center). On glasses this is usually a KEY
            // instead; the shared guard prevents double-registering.
            handleClick(now)
        }
    }

    private fun emitDirection(tok: Int) {
        if (engine.state == GameState.INPUT && !engine.settingsOpen) engine.onToken(tok)
    }

    // ------------------------------------------------------------ lifecycle

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        applySettings()
        gameView.start()
    }

    override fun onPause() {
        engine.onAppPause()
        gameView.stop()
        super.onPause()
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}
