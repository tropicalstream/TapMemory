package com.tapmemory.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.tapmemory.engine.AudioMode
import com.tapmemory.engine.Tok
import com.tapmemory.engine.Ui
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Every sound is synthesized to a WAV at first launch and played via SoundPool
 * — no audio binaries ship in the APK (the TapBubbles/TapInsight pattern).
 * SoundPool's per-channel volume gives us free stereo panning for directional
 * cues, and its rate arg gives pitch variation.
 */
class Audio(private val context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()

    private val toneId = IntArray(Tok.COUNT)
    private val whooshId = IntArray(Tok.COUNT)
    private val percId = IntArray(Tok.COUNT)
    private val uiId = IntArray(7)

    @Volatile private var loaded = false
    @Volatile var volume = 0.85f

    private val rng = Random(5)
    private val rate = 22050

    fun loadAsync() {
        Thread {
            runCatching {
                val dir = File(context.cacheDir, "snd").apply { mkdirs() }
                for (t in 0 until Tok.COUNT) {
                    toneId[t] = load(dir, "tone$t", synthTone(Tok.FREQ[t], t))
                    whooshId[t] = load(dir, "wh$t", synthWhoosh(t))
                    percId[t] = load(dir, "perc$t", synthPerc(t))
                }
                uiId[Ui.CORRECT] = load(dir, "ok", arpeggio(intArrayOf(523, 659, 784), 90, 0.6f))
                uiId[Ui.WRONG] = load(dir, "no", synthWrong())
                uiId[Ui.LEVELUP] = load(dir, "up", arpeggio(intArrayOf(523, 659, 784, 1046, 1318), 95, 0.7f))
                uiId[Ui.TICK] = load(dir, "tick", synthTick())
                uiId[Ui.SELECT] = load(dir, "sel", synthSelect())
                uiId[Ui.START] = load(dir, "start", arpeggio(intArrayOf(392, 523, 659), 110, 0.6f))
                uiId[Ui.COUNTDOWN] = load(dir, "cd", synthTick(1320f))
                loaded = true
            }
        }.start()
    }

    /** Play a token in the level's audio style, panned to its board position. */
    fun playToken(token: Int, mode: AudioMode) {
        if (!loaded || volume <= 0f) return
        val id = when (mode) {
            AudioMode.NONE -> return
            AudioMode.WHOOSH -> whooshId[token]
            AudioMode.PERCUSSION -> percId[token]
            AudioMode.TONES, AudioMode.MELODY -> toneId[token]
        }
        if (id == 0) return
        val pan = Tok.PAN[token]
        val l = volume * (if (pan > 0f) 1f - pan * 0.75f else 1f)
        val r = volume * (if (pan < 0f) 1f + pan * 0.75f else 1f)
        pool.play(id, l.coerceIn(0f, 1f), r.coerceIn(0f, 1f), 1, 0, 1f)
    }

    fun ui(sound: Int, pitch: Float = 1f, vol: Float = 1f) {
        if (!loaded || sound < 0 || sound >= uiId.size) return
        val id = uiId[sound]
        if (id == 0) return
        val v = (volume * vol).coerceIn(0f, 1f)
        if (v <= 0f) return
        pool.play(id, v, v, 1, 0, pitch.coerceIn(0.5f, 2f))
    }

    fun release() = runCatching { pool.release() }.let {}

    // ------------------------------------------------------------ synth

    private fun buf(ms: Int, gen: (t: Float) -> Float): ShortArray {
        val n = rate * ms / 1000
        return ShortArray(n) { i ->
            (gen(i.toFloat() / rate).coerceIn(-1f, 1f) * 30000f).toInt().toShort()
        }
    }

    private fun sine(f: Float, t: Float) = sin(2.0 * PI * f * t).toFloat()
    private fun noise() = rng.nextFloat() * 2f - 1f

    // Each token gets a slightly different harmonic recipe so it is
    // distinguishable by timbre even before pitch/pan.
    private fun synthTone(freq: Float, token: Int): ShortArray = buf(440) { t ->
        val env = min(1f, t * 60f) * exp(-t * 3.4f)
        val h2 = when (token) { Tok.UP -> 0.5f; Tok.TAP -> 0.15f; else -> 0.3f }
        val h3 = when (token) { Tok.TAP -> 0.35f; Tok.DOWN -> 0.25f; else -> 0.12f }
        val vib = 1f + 0.004f * sine(6f, t)
        (sine(freq * vib, t) + h2 * sine(freq * 2, t) + h3 * sine(freq * 3, t)) * env * 0.6f
    }

    private fun synthWhoosh(token: Int): ShortArray = buf(360) { t ->
        // Pitch sweeps up for UP, down for DOWN; sideways stay flat + panned.
        val dir = when (token) { Tok.UP -> 1f; Tok.DOWN -> -1f; else -> 0f }
        val base = 300f + dir * 500f * t
        val env = min(1f, t * 30f) * exp(-t * 5f)
        (0.55f * sine(base, t) + 0.5f * noise() * exp(-t * 7f)) * env
    }

    private fun synthPerc(token: Int): ShortArray = when (token) {
        Tok.DOWN -> buf(260) { t -> // kick
            val f = 150f * exp(-t * 16f) + 45f
            sine(f, t) * exp(-t * 9f) * 0.95f
        }
        Tok.TAP -> buf(200) { t -> // snare
            (noise() * 0.7f + sine(220f, t) * 0.4f) * exp(-t * 18f)
        }
        Tok.LEFT -> buf(220) { t -> // low tom
            sine(260f * exp(-t * 5f) + 120f, t) * exp(-t * 11f) * 0.9f
        }
        Tok.RIGHT -> buf(200) { t -> // high tom
            sine(400f * exp(-t * 5f) + 180f, t) * exp(-t * 12f) * 0.9f
        }
        else -> buf(140) { t -> // hat (UP)
            noise() * exp(-t * 45f) * 0.6f
        }
    }

    private fun synthWrong(): ShortArray = buf(500) { t ->
        val f = 200f - t * 120f
        (sine(f, t) + 0.5f * sine(f * 1.03f, t)) * exp(-t * 3f) * 0.6f
    }

    private fun synthTick(f: Float = 1150f): ShortArray = buf(34) { t ->
        sine(f, t) * exp(-t * 70f) * 0.6f
    }

    private fun synthSelect(): ShortArray = buf(130) { t ->
        sine(if (t < 0.05f) 700f else 1046f, t) * exp(-t * 11f) * 0.6f
    }

    private fun arpeggio(freqs: IntArray, noteMs: Int, amp: Float): ShortArray {
        val total = noteMs * freqs.size + 260
        return buf(total) { t ->
            var v = 0f
            for ((i, f) in freqs.withIndex()) {
                val start = i * noteMs / 1000f
                if (t >= start) {
                    val lt = t - start
                    v += (sine(f.toFloat(), lt) + 0.3f * sine(f * 2f, lt)) * exp(-lt * 5.5f) * amp * 0.45f
                }
            }
            v
        }
    }

    // ------------------------------------------------------------- wav

    private fun DataOutputStream.wInt(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF); write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
    }
    private fun DataOutputStream.wShort(v: Int) { write(v and 0xFF); write((v shr 8) and 0xFF) }

    private fun load(dir: File, name: String, pcm: ShortArray): Int {
        val f = File(dir, "$name.wav")
        val dataLen = pcm.size * 2
        DataOutputStream(BufferedOutputStream(FileOutputStream(f))).use { o ->
            o.writeBytes("RIFF"); o.wInt(36 + dataLen); o.writeBytes("WAVE")
            o.writeBytes("fmt "); o.wInt(16); o.wShort(1); o.wShort(1)
            o.wInt(rate); o.wInt(rate * 2); o.wShort(2); o.wShort(16)
            o.writeBytes("data"); o.wInt(dataLen)
            for (s in pcm) o.wShort(s.toInt())
        }
        return pool.load(f.absolutePath, 1)
    }
}
