package com.tapmemory

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.tapmemory.engine.AudioMode
import com.tapmemory.engine.GameEngine
import com.tapmemory.engine.GameState
import com.tapmemory.engine.LEVELS
import com.tapmemory.engine.LevelConfig
import com.tapmemory.engine.Tok
import com.tapmemory.engine.VisualMode
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * All drawing in 640x480 logical space on a pure-black canvas — on the
 * waveguide, black is transparency, so the board floats as neon light.
 */
class Renderer(private val engine: GameEngine, private val store: SettingsStore) {

    private val W = 640f
    private val H = 480f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val rf = RectF()
    private val shakeRng = Random(3)
    private val glowCache = arrayOfNulls<Paint>(Tok.COUNT)
    private var titleShader: Shader? = null

    fun draw(c: Canvas, w: Int, h: Int) {
        c.drawColor(Color.BLACK)
        if (w <= 0 || h <= 0) return
        val s = min(w / W, h / H)
        c.save()
        c.translate((w - W * s) / 2f, (h - H * s) / 2f)
        c.scale(s, s)
        if (engine.shake > 0.1f) {
            c.translate((shakeRng.nextFloat() - 0.5f) * engine.shake, (shakeRng.nextFloat() - 0.5f) * engine.shake)
        }

        when (engine.state) {
            GameState.MENU -> drawMenu(c)
            GameState.WON -> drawWon(c)
            else -> drawGame(c)
        }
        drawParticles(c)
        drawTexts(c)
        drawFlash(c)
        if (engine.settingsOpen) drawSettings(c)
        c.restore()
    }

    // ------------------------------------------------------------- game

    private fun drawGame(c: Canvas) {
        val lv = engine.level
        drawBoard(c, lv)
        drawHud(c, lv)
        when (engine.state) {
            GameState.INTRO -> drawIntro(c, lv)
            GameState.WATCH -> banner(c, "WATCH", 0xFF8FD0FF.toInt())
            GameState.INPUT -> {
                if (lv.reverse) banner(c, "YOUR TURN · REVERSED", 0xFFFF8FD0.toInt())
                else banner(c, "YOUR TURN", 0xFF9CFFB0.toInt())
                drawProgress(c)
            }
            GameState.ROUND_OK -> banner(c, "GOOD!", 0xFF9CFFB0.toInt())
            GameState.LEVEL_CLEAR -> drawLevelClear(c, lv)
            GameState.GAME_OVER -> drawGameOver(c)
            else -> {}
        }
    }

    private fun tokenColor(t: Int) = Tok.COLOR[engine.colorPerm[t]]

    private fun drawBoard(c: Canvas, lv: LevelConfig) {
        when (lv.visual) {
            VisualMode.CROSS -> drawCross(c)
            VisualMode.ORBIT -> drawOrbit(c)
            VisualMode.BARS -> drawBars(c)
            VisualMode.WAVE -> drawWave(c)
            VisualMode.SHAPES -> drawShapes(c)
            VisualMode.NONE -> drawBlackout(c)
        }
    }

    private fun glowFor(t: Int): Paint {
        val p = glowCache[t] ?: Paint(Paint.ANTI_ALIAS_FLAG).also { glowCache[t] = it }
        return p
    }

    private fun nodeGlow(c: Canvas, x: Float, y: Float, color: Int, g: Float, radius: Float) {
        if (g <= 0.02f) return
        val a = (g * 150).toInt().coerceIn(0, 200)
        val p = glowFor(0)
        p.shader = RadialGradient(
            x, y, radius * 2.2f,
            intArrayOf(Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, radius * 2.2f, p)
        p.shader = null
    }

    private fun drawCross(c: Canvas) {
        for (t in 0 until Tok.COUNT) {
            val x = Tok.PX[t]; val y = Tok.PY[t]
            val g = engine.glow[t]
            val col = tokenColor(t)
            nodeGlow(c, x, y, col, g, 46f)
            // Panel
            rf.set(x - 42f, y - 42f, x + 42f, y + 42f)
            fill.shader = null
            fill.color = Tok.mix(0xFF0E1A2E.toInt(), col, 0.10f + g * 0.7f)
            c.drawRoundRect(rf, 16f, 16f, fill)
            stroke.strokeWidth = 2f + g * 3f
            stroke.color = Tok.mix(Color.argb(120, Color.red(col), Color.green(col), Color.blue(col)), Color.WHITE, g * 0.6f)
            c.drawRoundRect(rf, 16f, 16f, stroke)
            if (store.arrowHints) drawArrow(c, x, y, t, Color.argb((90 + g * 140).toInt().coerceIn(0, 255), 255, 255, 255))
        }
    }

    private fun drawOrbit(c: Canvas) {
        // Connecting ring.
        stroke.strokeWidth = 1.5f
        stroke.color = Color.argb(50, 120, 170, 230)
        c.drawCircle(320f, 250f, 120f, stroke)
        for (t in 0 until Tok.COUNT) {
            val x = Tok.PX[t]; val y = Tok.PY[t]
            val g = engine.glow[t]
            val col = tokenColor(t)
            nodeGlow(c, x, y, col, g, 40f)
            fill.shader = null
            fill.color = Tok.mix(0xFF0C1626.toInt(), col, 0.15f + g * 0.7f)
            c.drawCircle(x, y, 30f + g * 8f, fill)
            stroke.strokeWidth = 2f + g * 3f
            stroke.color = Tok.mix(Color.argb(140, Color.red(col), Color.green(col), Color.blue(col)), Color.WHITE, g * 0.6f)
            c.drawCircle(x, y, 30f + g * 8f, stroke)
            if (store.arrowHints) drawArrow(c, x, y, t, Color.argb((80 + g * 150).toInt().coerceIn(0, 255), 255, 255, 255))
        }
    }

    private fun drawBars(c: Canvas) {
        // Five bars along the bottom, ordered LEFT DOWN TAP UP RIGHT.
        val order = intArrayOf(Tok.LEFT, Tok.DOWN, Tok.TAP, Tok.UP, Tok.RIGHT)
        val baseY = 380f
        val bw = 62f
        for ((i, t) in order.withIndex()) {
            val cx = 130f + i * 95f
            val g = engine.glow[t]
            val col = tokenColor(t)
            val hgt = 40f + g * 210f
            nodeGlow(c, cx, baseY - hgt, col, g, 30f)
            rf.set(cx - bw / 2, baseY - hgt, cx + bw / 2, baseY)
            fill.shader = null
            fill.color = Tok.mix(0xFF10203A.toInt(), col, 0.25f + g * 0.65f)
            c.drawRoundRect(rf, 10f, 10f, fill)
            stroke.strokeWidth = 2f
            stroke.color = Color.argb(150, Color.red(col), Color.green(col), Color.blue(col))
            c.drawRoundRect(rf, 10f, 10f, stroke)
            if (store.arrowHints) drawArrow(c, cx, baseY + 22f, t, Color.argb(200, 255, 255, 255))
        }
        stroke.strokeWidth = 2f
        stroke.color = Color.argb(70, 120, 160, 210)
        c.drawLine(90f, baseY, 550f, baseY, stroke)
    }

    private fun drawWave(c: Canvas) {
        // Flowing ribbon tinted by whatever token is glowing most; humps per token.
        val midY = 250f
        var domT = 0; var domG = 0f
        for (t in 0 until Tok.COUNT) if (engine.glow[t] > domG) { domG = engine.glow[t]; domT = t }
        val col = tokenColor(domT)
        stroke.strokeWidth = 3f
        val path = Path()
        var first = true
        var x = 90f
        while (x <= 550f) {
            val phase = (x - 90f) / 460f
            val amp = 18f + 60f * domG
            val y = midY + sin(phase * 12f + engine.time * 5f) * amp * (0.4f + 0.6f * sin(phase * 3.14f))
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            x += 8f
        }
        stroke.color = Tok.mix(Color.argb(120, Color.red(col), Color.green(col), Color.blue(col)), Color.WHITE, domG * 0.5f)
        c.drawPath(path, stroke)
        // Token markers along the lane.
        for (t in 0 until Tok.COUNT) {
            val cx = 130f + t * 95f
            val g = engine.glow[t]
            val tc = tokenColor(t)
            nodeGlow(c, cx, 130f, tc, g, 26f)
            fill.shader = null
            fill.color = Tok.mix(0xFF0C1626.toInt(), tc, 0.2f + g * 0.7f)
            c.drawCircle(cx, 130f, 18f + g * 8f, fill)
            if (store.arrowHints) drawArrow(c, cx, 130f, t, Color.argb((80 + g * 150).toInt().coerceIn(0, 255), 255, 255, 255))
        }
    }

    private fun drawShapes(c: Canvas) {
        // The active token as a big glyph center; reference glyphs around it.
        var domT = -1; var domG = 0.15f
        for (t in 0 until Tok.COUNT) if (engine.glow[t] > domG) { domG = engine.glow[t]; domT = t }
        if (domT >= 0) {
            val col = tokenColor(domT)
            nodeGlow(c, 320f, 250f, col, domG, 80f)
            drawGlyph(c, 320f, 250f, domT, 60f + domG * 14f, Tok.mix(col, Color.WHITE, domG * 0.5f))
        } else {
            fill.shader = null
            fill.color = Color.argb(30, 120, 150, 200)
            c.drawCircle(320f, 250f, 10f, fill)
        }
        // Reference row of the five shapes at the bottom.
        if (store.arrowHints) {
            for (t in 0 until Tok.COUNT) {
                val cx = 200f + t * 60f
                drawGlyph(c, cx, 410f, t, 16f, Color.argb(150, 200, 220, 245))
            }
        }
    }

    private fun drawBlackout(c: Canvas) {
        // Ears-only: no per-node info. A neutral breathing pulse; a white ring
        // flash on each step so the player feels the rhythm.
        var maxG = 0f
        for (t in 0 until Tok.COUNT) if (engine.glow[t] > maxG) maxG = engine.glow[t]
        val breathe = 0.5f + 0.5f * sin(engine.time * 2f)
        fill.shader = null
        fill.color = Color.argb((20 + breathe * 20).toInt(), 90, 120, 170)
        c.drawCircle(320f, 250f, 30f, fill)
        if (maxG > 0.05f) {
            stroke.strokeWidth = 3f + maxG * 4f
            stroke.color = Color.argb((maxG * 180).toInt().coerceIn(0, 255), 200, 225, 255)
            c.drawCircle(320f, 250f, 40f + (1f - maxG) * 60f, stroke)
        }
        // During INPUT, fade a dim reference cross in so the player can orient.
        if (engine.state == GameState.INPUT && store.arrowHints) {
            for (t in 0 until Tok.COUNT) {
                drawArrow(c, Tok.PX[t], Tok.PY[t], t, Color.argb(60, 200, 220, 245))
            }
        }
    }

    // ------------------------------------------------------------ glyphs

    private fun drawArrow(c: Canvas, x: Float, y: Float, t: Int, color: Int) {
        fill.shader = null
        fill.color = color
        stroke.color = color
        stroke.strokeWidth = 3f
        val p = Path()
        when (t) {
            Tok.UP -> { p.moveTo(x, y - 12f); p.lineTo(x - 9f, y + 4f); p.lineTo(x + 9f, y + 4f); p.close(); c.drawPath(p, fill) }
            Tok.DOWN -> { p.moveTo(x, y + 12f); p.lineTo(x - 9f, y - 4f); p.lineTo(x + 9f, y - 4f); p.close(); c.drawPath(p, fill) }
            Tok.LEFT -> { p.moveTo(x - 12f, y); p.lineTo(x + 4f, y - 9f); p.lineTo(x + 4f, y + 9f); p.close(); c.drawPath(p, fill) }
            Tok.RIGHT -> { p.moveTo(x + 12f, y); p.lineTo(x - 4f, y - 9f); p.lineTo(x - 4f, y + 9f); p.close(); c.drawPath(p, fill) }
            else -> c.drawCircle(x, y, 8f, fill) // TAP = center dot
        }
    }

    private fun drawGlyph(c: Canvas, x: Float, y: Float, t: Int, r: Float, color: Int) {
        fill.shader = null
        fill.color = color
        stroke.color = color
        stroke.strokeWidth = r * 0.18f
        val p = Path()
        when (t) {
            Tok.UP -> { p.moveTo(x, y - r); p.lineTo(x - r * 0.85f, y + r * 0.6f); p.lineTo(x + r * 0.85f, y + r * 0.6f); p.close(); c.drawPath(p, fill) }
            Tok.DOWN -> { p.moveTo(x, y + r); p.lineTo(x - r * 0.85f, y - r * 0.6f); p.lineTo(x + r * 0.85f, y - r * 0.6f); p.close(); c.drawPath(p, fill) }
            Tok.LEFT -> { // chevron
                p.moveTo(x + r * 0.5f, y - r); p.lineTo(x - r * 0.6f, y); p.lineTo(x + r * 0.5f, y + r)
                stroke.strokeWidth = r * 0.34f; stroke.strokeCap = Paint.Cap.ROUND; c.drawPath(p, stroke)
            }
            Tok.RIGHT -> {
                p.moveTo(x - r * 0.5f, y - r); p.lineTo(x + r * 0.6f, y); p.lineTo(x - r * 0.5f, y + r)
                stroke.strokeWidth = r * 0.34f; stroke.strokeCap = Paint.Cap.ROUND; c.drawPath(p, stroke)
            }
            else -> { // TAP = diamond
                p.moveTo(x, y - r); p.lineTo(x + r, y); p.lineTo(x, y + r); p.lineTo(x - r, y); p.close(); c.drawPath(p, fill)
            }
        }
    }

    // -------------------------------------------------------------- HUD

    private fun drawHud(c: Canvas, lv: LevelConfig) {
        text(c, "LVL ${lv.number}", 16f, 26f, 13f, Color.argb(255, 150, 200, 245), Paint.Align.LEFT)
        text(c, lv.name.uppercase(), 16f, 44f, 11f, Color.argb(220, 200, 224, 245), Paint.Align.LEFT)

        // Sensory badges: which senses this level gives you.
        val seeOn = lv.visual != VisualMode.NONE
        val hearOn = lv.audio != AudioMode.NONE
        text(c, "SEE", 548f, 26f, 11f, if (seeOn) Color.argb(255, 120, 230, 255) else Color.argb(70, 120, 140, 170), Paint.Align.LEFT)
        text(c, "HEAR", 584f, 26f, 11f, if (hearOn) Color.argb(255, 255, 210, 90) else Color.argb(70, 120, 140, 170), Paint.Align.LEFT)

        // Length progress.
        val len = engine.sequence.size
        text(c, "$len / ${lv.targetLen}", 624f, 46f, 13f, Color.WHITE, Paint.Align.RIGHT)

        // Input timer ring (levels with a clock).
        if (engine.state == GameState.INPUT && lv.inputLimit > 0f) {
            val frac = (engine.inputTimeLeft / lv.inputLimit).coerceIn(0f, 1f)
            stroke.strokeWidth = 4f
            stroke.color = if (frac > 0.3f) Color.argb(150, 140, 200, 255)
            else Color.argb((150 + 105 * sin(engine.time * 8f)).toInt().coerceIn(60, 255), 255, 80, 80)
            rf.set(300f, 20f, 340f, 60f)
            c.drawArc(rf, -90f, 360f * frac, false, stroke)
        }
    }

    private fun drawProgress(c: Canvas) {
        val n = engine.sequence.size
        val done = engine.inputIndex
        val totalW = (n * 16f).coerceAtMost(260f)
        val step = if (n > 0) totalW / n else 0f
        val startX = 320f - totalW / 2f
        for (i in 0 until n) {
            val x = startX + step * i + step / 2f
            fill.shader = null
            fill.color = if (i < done) Color.argb(255, 156, 255, 176) else Color.argb(110, 120, 150, 190)
            c.drawCircle(x, 452f, if (i < done) 5f else 3.5f, fill)
        }
    }

    // ---------------------------------------------------------- banners

    private fun banner(c: Canvas, s: String, color: Int) {
        text(c, s, 320f, 96f, 20f, color, glow = Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)))
    }

    private fun pulse(speed: Float) = (170 + 85 * sin(engine.time * speed)).toInt().coerceIn(60, 255)

    private fun dim(c: Canvas, a: Int) {
        fill.shader = null
        fill.color = Color.argb(a, 0, 0, 0)
        c.drawRect(0f, 0f, W, H, fill)
    }

    private fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        rf.set(l, t, r, b)
        fill.shader = null
        fill.color = Color.argb(236, 12, 22, 42)
        c.drawRoundRect(rf, 16f, 16f, fill)
        stroke.strokeWidth = 2f
        stroke.color = Color.argb(200, 95, 134, 200)
        c.drawRoundRect(rf, 16f, 16f, stroke)
    }

    private fun drawIntro(c: Canvas, lv: LevelConfig) {
        dim(c, 150)
        panel(c, 96f, 108f, 544f, 372f)
        text(c, "LEVEL ${lv.number}", 320f, 146f, 15f, Color.argb(255, 143, 208, 255))
        text(c, lv.name, 320f, 182f, 30f, Color.WHITE, glow = Color.argb(160, 60, 140, 255))
        text(c, lv.tagline, 320f, 208f, 13f, Color.argb(230, 255, 214, 120))
        lv.intro.forEachIndexed { i, line ->
            text(c, line, 320f, 246f + i * 24f, 12.5f, Color.argb(255, 200, 220, 242))
        }
        text(c, "TAP TO START", 320f, 352f, 15f, Color.argb(pulse(4f), 255, 224, 112))
    }

    private fun drawLevelClear(c: Canvas, lv: LevelConfig) {
        dim(c, 130)
        panel(c, 130f, 96f, 510f, 388f)
        text(c, "LEVEL ${lv.number} CLEAR!", 320f, 146f, 26f, Color.argb(255, 156, 255, 176), glow = Color.argb(180, 30, 160, 60))
        for (i in 1..3) {
            val sx = 320f + (i - 2) * 48f
            val on = i <= engine.clearStars
            text(c, "★", sx, 208f, if (on) 34f else 26f,
                if (on) Color.argb(255, 255, 214, 64) else Color.argb(90, 120, 140, 170),
                glow = if (on) Color.argb(150, 255, 160, 0) else 0)
        }
        text(c, "Longest sequence: ${engine.bestRun}", 320f, 258f, 14f, Color.argb(255, 200, 224, 245))
        text(c, "Score: ${engine.score}", 320f, 284f, 14f, Color.argb(255, 255, 224, 112))
        val nxt = if (lv.number >= LEVELS.size) "TAP FOR THE CROWN" else "TAP FOR NEXT LEVEL"
        text(c, nxt, 320f, 350f, 15f, Color.argb(pulse(4f), 200, 230, 255))
    }

    private fun drawGameOver(c: Canvas) {
        dim(c, 150)
        fill.shader = null
        fill.color = Color.argb(30, 255, 40, 40)
        c.drawRect(0f, 0f, W, H, fill)
        text(c, "MISSED IT", 320f, 150f, 34f, Color.argb(255, 255, 96, 96), glow = Color.argb(200, 200, 0, 0))
        val rt = engine.revealToken
        if (rt in 0 until Tok.COUNT) {
            text(c, "it was  ${Tok.NAMES[rt]}  (${Tok.GESTURE[rt]})", 320f, 190f, 15f, Color.argb(255, 255, 220, 140))
        }
        text(c, "Longest sequence: ${engine.bestRun}", 320f, 236f, 14f, Color.argb(255, 200, 220, 242))
        text(c, "TAP TO RETRY", 320f, 300f, 16f, Color.argb(pulse(4f), 255, 224, 112))
        text(c, "double-tap for settings", 320f, 326f, 11f, Color.argb(170, 170, 190, 220))
    }

    // --------------------------------------------------------- menu/won

    private fun drawMenu(c: Canvas) {
        // Ambient drifting nodes.
        for (t in 0 until Tok.COUNT) {
            val x = 320f + cos(engine.time * 0.4f + t * 1.256f) * 200f
            val y = 250f + sin(engine.time * 0.33f + t * 1.256f) * 150f
            fill.shader = null
            fill.color = Color.argb(50, Color.red(Tok.COLOR[t]), Color.green(Tok.COLOR[t]), Color.blue(Tok.COLOR[t]))
            c.drawCircle(x, y, 14f, fill)
        }
        val sh = titleShader ?: android.graphics.LinearGradient(
            0f, 120f, 0f, 172f, 0xFFEAF7FF.toInt(), 0xFF4FA8FF.toInt(), Shader.TileMode.CLAMP
        ).also { titleShader = it }
        textP.textSize = 52f
        textP.textAlign = Paint.Align.CENTER
        textP.shader = sh
        textP.setShadowLayer(18f, 0f, 0f, Color.argb(190, 60, 150, 255))
        c.drawText("TAP MEMORY", 320f, 162f, textP)
        textP.shader = null
        textP.clearShadowLayer()
        text(c, "the sound & light memory game", 320f, 190f, 12f, Color.argb(220, 150, 190, 235))

        val lv = engine.menuLevel
        val cfg = LEVELS[lv - 1]
        text(c, "‹  LEVEL $lv  ›", 320f, 262f, 24f, Color.WHITE, glow = Color.argb(140, 80, 150, 255))
        text(c, cfg.name, 320f, 290f, 15f, Color.argb(255, 255, 224, 112))
        text(c, cfg.tagline, 320f, 312f, 12f, Color.argb(210, 180, 210, 240))
        val stars = store.bestStars(lv)
        if (stars > 0) text(c, "★".repeat(stars) + "☆".repeat(3 - stars), 320f, 338f, 15f, Color.argb(240, 255, 214, 64), glow = Color.argb(110, 255, 160, 0))
        val best = store.bestRun(lv)
        if (best > 0) text(c, "BEST RUN  $best", 320f, 360f, 12f, Color.argb(220, 156, 255, 176))

        text(c, "swipe ↔ choose   •   tap to play", 320f, 402f, 13f, Color.argb(pulse(3f), 200, 230, 255))
        text(c, "double-tap for settings", 320f, 424f, 11f, Color.argb(160, 150, 175, 210))
    }

    private fun drawWon(c: Canvas) {
        text(c, "PERFECT RECALL", 320f, 180f, 34f, Color.argb(255, 156, 255, 176), glow = Color.argb(200, 30, 180, 80))
        text(c, "You cleared every combination of sound and light.", 320f, 218f, 12.5f, Color.argb(230, 200, 220, 242))
        text(c, "FINAL SCORE  ${engine.score}", 320f, 274f, 22f, Color.argb(255, 255, 224, 112), glow = Color.argb(150, 255, 160, 0))
        text(c, "TAP FOR TITLE", 320f, 350f, 15f, Color.argb(pulse(4f), 200, 230, 255))
    }

    // -------------------------------------------------------- settings

    private fun drawSettings(c: Canvas) {
        dim(c, 188)
        panel(c, 140f, 40f, 500f, 440f)
        text(c, "SETTINGS", 320f, 72f, 20f, Color.WHITE, glow = Color.argb(160, 80, 150, 255))
        val menu = engine.settingsMenu
        val visible = 9
        val start = (menu.selected - visible / 2).coerceIn(0, (menu.items.size - visible).coerceAtLeast(0))
        var y = 106f
        for (i in start until min(start + visible, menu.items.size)) {
            val item = menu.items[i]
            val sel = i == menu.selected
            if (sel) {
                fill.shader = null
                fill.color = Color.argb(210, 36, 64, 106)
                rf.set(152f, y - 16f, 488f, y + 9f)
                c.drawRoundRect(rf, 8f, 8f, fill)
            }
            text(c, item.label, 168f, y, 13.5f, if (sel) Color.WHITE else Color.argb(255, 159, 180, 208), Paint.Align.LEFT)
            val v = item.value()
            if (v.isNotEmpty()) {
                val shown = if (sel && item.adjust != null) "‹ $v ›" else v
                text(c, shown, 472f, y, 13.5f, if (sel) Color.argb(255, 255, 224, 128) else Color.argb(255, 120, 144, 176), Paint.Align.RIGHT)
            }
            y += 33f
        }
        if (start > 0) text(c, "▲", 320f, 96f, 10f, Color.argb(180, 150, 180, 220))
        if (start + visible < menu.items.size) text(c, "▼", 320f, 420f, 10f, Color.argb(180, 150, 180, 220))
        text(c, "swipe ↕ select   ↔ adjust   tap OK   double-tap close", 320f, 456f, 10.5f, Color.argb(200, 150, 175, 210))
    }

    // ------------------------------------------------------ fx & text

    private fun drawFlash(c: Canvas) {
        if (engine.flash <= 0.01f) return
        val a = (engine.flash * 90f).toInt().coerceIn(0, 120)
        fill.shader = null
        fill.color = if (engine.flashGood) Color.argb(a, 60, 220, 120) else Color.argb(a, 220, 50, 50)
        c.drawRect(0f, 0f, W, H, fill)
    }

    private fun drawParticles(c: Canvas) {
        for (p in engine.particles.list) {
            val k = (p.life / p.maxLife).coerceIn(0f, 1f)
            val alpha = (k * 255).toInt()
            if (p.ring) {
                stroke.strokeWidth = 1.5f + 3f * k
                stroke.color = p.color
                stroke.alpha = alpha
                c.drawCircle(p.x, p.y, p.size * (1f + (1f - k) * 2f), stroke)
            } else {
                fill.shader = null
                fill.color = p.color
                fill.alpha = alpha
                c.drawCircle(p.x, p.y, p.size * (0.4f + 0.6f * k), fill)
            }
        }
        stroke.alpha = 255
        fill.alpha = 255
    }

    private fun drawTexts(c: Canvas) {
        for (t in engine.texts) {
            val k = (t.life / t.maxLife).coerceIn(0f, 1f)
            val alpha = (255f * min(1f, k * 3f)).toInt()
            textP.textSize = if (t.big) 22f else 13f
            textP.textAlign = Paint.Align.CENTER
            textP.color = t.color
            textP.alpha = alpha
            c.drawText(t.text, t.x, t.y, textP)
        }
        textP.alpha = 255
    }

    private fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.CENTER, glow: Int = 0,
    ) {
        textP.textSize = size
        textP.textAlign = align
        textP.color = color
        if (glow != 0) textP.setShadowLayer(size * 0.4f, 0f, 0f, glow) else textP.clearShadowLayer()
        c.drawText(s, x, y, textP)
        textP.clearShadowLayer()
    }
}
