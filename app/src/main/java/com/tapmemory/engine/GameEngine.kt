package com.tapmemory.engine

import com.tapmemory.SettingsStore
import com.tapmemory.audio.Audio
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class GameState { MENU, INTRO, WATCH, INPUT, ROUND_OK, LEVEL_CLEAR, GAME_OVER, WON }

/** Activity-side services the engine drives (audio + harness settings). */
interface GameHost {
    fun applySettings()
    fun playToken(token: Int, audio: AudioMode)
    fun ui(sound: Int, pitch: Float = 1f, vol: Float = 1f)
}

object Ui {
    const val CORRECT = 0
    const val WRONG = 1
    const val LEVELUP = 2
    const val TICK = 3
    const val SELECT = 4
    const val START = 5
    const val COUNTDOWN = 6
}

/**
 * The heart of TapMemory: a Simon-style grow-and-repeat loop, but every level
 * presents the sequence through a different sound/light combination and some
 * levels twist the recall (reverse, lying colors, rhythm, a clock).
 */
class GameEngine(val store: SettingsStore, val host: GameHost) {

    var state = GameState.MENU
        private set
    var settingsOpen = false
        private set
    val settingsMenu = SettingsMenu(this, store)

    val particles = ParticleSystem()
    val texts = ArrayList<FloatText>()

    var levelIdx = 1
        private set
    var level: LevelConfig = LEVELS[0]
        private set

    val sequence = ArrayList<Int>()
    var inputIndex = 0
        private set

    // Per-node glow (0..1), decays; renderer reads this for every visual mode.
    val glow = FloatArray(Tok.COUNT)
    // Token -> color slot mapping. Identity unless the level shuffles colors.
    val colorPerm = IntArray(Tok.COUNT) { it }

    var time = 0f
    var menuLevel = 1
    var streak = 0            // current sequence length reproduced this run
    var bestRun = 0
    var score = 0L
    var flash = 0f           // full-screen feedback flash (green/red via flashGood)
    var flashGood = true
    var revealToken = -1     // the correct answer shown on a miss
    var shake = 0f
    var clearStars = 0
    var inputTimeLeft = 0f

    // WATCH playback cursor.
    private var watchStep = 0
    private var watchOn = false
    private var watchT = 0f
    private var onDur = 0.5f
    private var gapDur = 0.28f
    private var stateT = 0f
    private var rng = Random(System.nanoTime())

    val isPlayingBack get() = state == GameState.WATCH
    val watchCursor get() = if (watchOn) watchStep else -1

    fun boot() {
        menuLevel = store.unlockedLevel
        toMenu()
    }

    // ------------------------------------------------------------- update

    fun update(dtReal: Float) {
        time += dtReal
        flash = max(0f, flash - dtReal * 2.2f)
        shake = max(0f, shake - dtReal * 30f)
        for (i in glow.indices) glow[i] = max(0f, glow[i] - dtReal * 3.4f)
        particles.update(dtReal)
        updateTexts(dtReal)
        if (settingsOpen) return

        stateT += dtReal
        when (state) {
            GameState.WATCH -> updateWatch(dtReal)
            GameState.INPUT -> updateInput(dtReal)
            GameState.ROUND_OK -> if (stateT > 0.7f) growAndWatch()
            GameState.LEVEL_CLEAR -> {
                spawnConfetti()
                if (stateT > 0.4f && stateT % 0.5f < dtReal) host.ui(Ui.TICK, 1.5f, 0.5f)
            }
            GameState.WON -> spawnConfetti()
            GameState.GAME_OVER -> {}
            else -> {}
        }
    }

    private fun updateWatch(dt: Float) {
        watchT += dt
        if (watchOn) {
            if (watchT >= onDur) {
                watchOn = false
                watchT = 0f
            }
        } else {
            val g = gapDur * (1f + (if (level.jitter > 0f) (rng.nextFloat() - 0.5f) * level.jitter else 0f))
            if (watchT >= g) {
                watchStep++
                if (watchStep >= sequence.size) {
                    beginInput()
                } else {
                    beginWatchToken()
                }
            }
        }
    }

    private fun beginWatchToken() {
        watchOn = true
        watchT = 0f
        val tk = sequence[watchStep]
        glow[tk] = 1f
        host.playToken(tk, level.audio)
        val px = Tok.PX[tk]
        val py = Tok.PY[tk]
        particles.ring(px, py, Tok.COLOR[colorPerm[tk]], 40f)
    }

    private fun updateInput(dt: Float) {
        if (level.inputLimit > 0f) {
            inputTimeLeft -= dt
            if (inputTimeLeft <= 1f && time % 0.5f < dt) host.ui(Ui.COUNTDOWN, 1.2f, 0.6f)
            if (inputTimeLeft <= 0f) fail(expectedToken())
        }
    }

    // -------------------------------------------------------------- input

    /** Called by the Activity with a resolved token (0..4). */
    fun onToken(tk: Int) {
        if (settingsOpen || state != GameState.INPUT) {
            // Outside INPUT a click/swipe just advances menus/screens.
            if (!settingsOpen) advanceScreen()
            return
        }
        glow[tk] = 1f
        host.playToken(tk, level.audio)
        val expected = expectedToken()
        if (tk == expected) {
            particles.burst(Tok.PX[tk], Tok.PY[tk], Tok.COLOR[colorPerm[tk]], 0.6f)
            inputIndex++
            score += 25L * levelIdx
            if (inputIndex >= sequence.size) roundComplete()
            else if (level.inputLimit > 0f) inputTimeLeft = level.inputLimit
        } else {
            fail(expected)
        }
    }

    private fun expectedToken(): Int {
        val i = if (level.reverse) sequence.size - 1 - inputIndex else inputIndex
        return sequence[i]
    }

    private fun roundComplete() {
        streak = sequence.size
        bestRun = max(bestRun, streak)
        score += 100L * levelIdx
        host.ui(Ui.CORRECT, 1f + min(streak, 8) * 0.03f)
        flash = 0.7f
        flashGood = true
        if (sequence.size >= level.targetLen) levelClear()
        else {
            state = GameState.ROUND_OK
            stateT = 0f
        }
    }

    private fun fail(correct: Int) {
        revealToken = correct
        glow[correct] = 1f
        host.ui(Ui.WRONG)
        flash = 0.9f
        flashGood = false
        shake = 12f
        state = GameState.GAME_OVER
        stateT = 0f
        if (bestRun > store.bestRun(levelIdx)) store.setBestRun(levelIdx, bestRun)
        particles.burst(Tok.PX[correct], Tok.PY[correct], 0xFFFF5050.toInt(), 1.2f)
    }

    private fun levelClear() {
        clearStars = starsFor(bestRun, level.targetLen)
        if (clearStars > store.bestStars(levelIdx)) store.setBestStars(levelIdx, clearStars)
        if (levelIdx < LEVELS.size) store.unlockedLevel = max(store.unlockedLevel, levelIdx + 1)
        host.ui(Ui.LEVELUP)
        flash = 0.8f
        flashGood = true
        state = GameState.LEVEL_CLEAR
        stateT = 0f
    }

    fun starsFor(reached: Int, target: Int): Int = when {
        reached >= target + 3 -> 3
        reached >= target + 1 -> 2
        else -> 1
    }

    // ---------------------------------------------------------- sequence

    private fun beginInput() {
        state = GameState.INPUT
        stateT = 0f
        inputIndex = 0
        if (level.inputLimit > 0f) inputTimeLeft = level.inputLimit + 1.2f
    }

    private fun growAndWatch() {
        sequence.add(rng.nextInt(Tok.COUNT))
        startWatch()
    }

    private fun startWatch() {
        if (level.shuffleColors) shufflePerm()
        state = GameState.WATCH
        stateT = 0f
        watchStep = 0
        watchT = 0f
        // Playback quickens slightly as the sequence lengthens (pressure).
        val lenFactor = 1f - min(0.3f, (sequence.size - 2) * 0.03f)
        // Accessibility: Watch Speed stretches on/gap durations (0/1/2 -> 1x/1.35x/1.8x).
        val slow = 1f + store.watchSlow * 0.4f
        onDur = 0.52f * lenFactor * slow / level.speed
        gapDur = 0.30f * lenFactor * slow / level.speed
        beginWatchToken()
    }

    private fun shufflePerm() {
        val idx = (0 until Tok.COUNT).toMutableList()
        idx.shuffle(rng)
        for (i in 0 until Tok.COUNT) colorPerm[i] = idx[i]
    }

    // ---------------------------------------------------------- screens

    /** A tap/click/swipe outside INPUT just moves through menus and banners. */
    private fun advanceScreen() {
        when (state) {
            GameState.MENU -> startLevel(menuLevel)
            GameState.INTRO -> startWatch()
            GameState.LEVEL_CLEAR -> {
                if (levelIdx >= LEVELS.size) {
                    state = GameState.WON
                    stateT = 0f
                } else startLevel(levelIdx + 1)
            }
            GameState.GAME_OVER -> startLevel(levelIdx)
            GameState.WON -> toMenu()
            else -> {}
        }
    }

    fun startLevel(n: Int) {
        levelIdx = n.coerceIn(1, LEVELS.size)
        level = LEVELS[levelIdx - 1]
        sequence.clear()
        for (i in colorPerm.indices) colorPerm[i] = i
        repeat(level.startLen) { sequence.add(rng.nextInt(Tok.COUNT)) }
        inputIndex = 0
        streak = 0
        bestRun = 0
        revealToken = -1
        clearStars = 0
        rng = Random(System.nanoTime())
        particles.clear()
        texts.clear()
        settingsOpen = false
        host.ui(Ui.START)
        state = GameState.INTRO
        stateT = 0f
    }

    fun toMenu() {
        state = GameState.MENU
        settingsOpen = false
        sequence.clear()
        particles.clear()
        texts.clear()
        menuLevel = menuLevel.coerceIn(1, store.unlockedLevel)
        stateT = 0f
    }

    fun jumpToLevel(n: Int) = startLevel(n)
    fun restartLevel() = startLevel(levelIdx)
    fun quitToMenu() = toMenu()

    // Replay the current sequence from the start (accessibility / "watch again").
    fun replay() {
        if (state == GameState.INPUT) startWatch()
    }

    // --------------------------------------------------- settings + nav

    fun doubleTap() {
        settingsOpen = !settingsOpen
        if (settingsOpen) settingsMenu.onOpen()
        host.ui(if (settingsOpen) Ui.SELECT else Ui.TICK)
    }

    /** The temple click: in settings it confirms, during INPUT it's the center
     *  TAP token, on any banner it advances the screen. */
    fun click() {
        when {
            settingsOpen -> settingsMenu.activate()
            state == GameState.INPUT -> onToken(Tok.TAP)
            else -> advanceScreen()
        }
    }

    fun onBack(): Boolean {
        if (settingsOpen) { doubleTap(); return true }
        return when (state) {
            GameState.WATCH, GameState.INPUT, GameState.INTRO -> { doubleTap(); true }
            GameState.LEVEL_CLEAR, GameState.GAME_OVER, GameState.WON -> { toMenu(); true }
            else -> false
        }
    }

    /** Menu navigation swipes (settings overlay or the title level-picker). */
    fun navSwipe(dx: Float, dy: Float) {
        if (settingsOpen) { settingsMenu.swipe(dx, dy); return }
        if (state == GameState.MENU) {
            menuSwipeAcc += dx
            while (menuSwipeAcc > 60f) { menuSwipeAcc -= 60f; menuStep(1) }
            while (menuSwipeAcc < -60f) { menuSwipeAcc += 60f; menuStep(-1) }
        }
    }

    fun endSwipe() {
        if (settingsOpen) settingsMenu.endSwipe() else menuSwipeAcc = 0f
    }

    private var menuSwipeAcc = 0f
    private fun menuStep(d: Int) {
        val nl = (menuLevel + d).coerceIn(1, store.unlockedLevel)
        if (nl != menuLevel) { menuLevel = nl; host.ui(Ui.TICK) }
    }

    fun onAppPause() {
        if ((state == GameState.WATCH || state == GameState.INPUT) && !settingsOpen) {
            settingsOpen = true
            settingsMenu.onOpen()
        }
    }

    // ------------------------------------------------------------- misc

    private fun spawnConfetti() {
        if (particles.list.size < 260) {
            particles.confetti(640f, Tok.COLOR[rng.nextInt(Tok.COUNT)])
        }
    }

    private fun addText(t: String, x: Float, y: Float, color: Int, big: Boolean) {
        if (texts.size > 20) texts.removeAt(0)
        val life = if (big) 1.4f else 0.9f
        texts.add(FloatText(t, x, y, life, life, color, big))
    }

    private fun updateTexts(dt: Float) {
        var i = texts.size - 1
        while (i >= 0) {
            val t = texts[i]
            t.life -= dt
            t.y -= 24f * dt
            if (t.life <= 0f) texts.removeAt(i)
            i--
        }
    }
}
