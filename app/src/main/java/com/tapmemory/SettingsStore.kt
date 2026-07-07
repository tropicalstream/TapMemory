package com.tapmemory

import android.content.Context
import android.os.Build

/** Persistent settings + progress. RayNeo hardware detected by identity, not model. */
class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("tapmemory", Context.MODE_PRIVATE)

    // FABLE_X3_STARTER_GUIDE gotcha #24: the X3 Pro reports Build.MODEL=ARGF20,
    // not "X3-Pro" — detect by manufacturer/brand/product instead.
    private val deviceText = listOf(
        Build.MODEL, Build.MANUFACTURER, Build.BRAND, Build.DEVICE, Build.PRODUCT
    ).joinToString(" ").lowercase()

    private val isRayNeoX3 =
        "rayneo" in deviceText || "leiniao" in deviceText || "ffalcon" in deviceText ||
            ("x3" in deviceText && ("tcl" in deviceText || "falcon" in deviceText))

    init {
        // One-time migration: force SBS on for RayNeo hardware even if an old
        // saved value (from a model-string default) left it off.
        if (isRayNeoX3 && !p.getBoolean("rayneoSbsV1", false)) {
            p.edit().putBoolean("sbs", true).putBoolean("rayneoSbsV1", true).apply()
        }
    }

    var soundVolume: Int
        get() = p.getInt("sndVol", 9)
        set(v) { p.edit().putInt("sndVol", v.coerceIn(0, 10)).apply() }

    var swipeSens: Float
        get() = p.getFloat("swipeSens", 1.0f)
        set(v) { p.edit().putFloat("swipeSens", v.coerceIn(0.4f, 2.5f)).apply() }

    var flipVertical: Boolean
        get() = p.getBoolean("flipV", false)
        set(v) { p.edit().putBoolean("flipV", v).apply() }

    var flipHorizontal: Boolean
        get() = p.getBoolean("flipH", false)
        set(v) { p.edit().putBoolean("flipH", v).apply() }

    /** Delay a single click by the double-tap window so double-tap = settings never fires a stray token. */
    var safeTap: Boolean
        get() = p.getBoolean("safeTap", true)
        set(v) { p.edit().putBoolean("safeTap", v).apply() }

    /** Slow the sequence playback for accessibility (multiplier 1.0..2.0 of the on/gap durations). */
    var watchSlow: Int
        get() = p.getInt("watchSlow", 0) // 0 = normal, 1 = relaxed, 2 = slow
        set(v) { p.edit().putInt("watchSlow", v.coerceIn(0, 2)).apply() }

    var arrowHints: Boolean
        get() = p.getBoolean("arrows", true)
        set(v) { p.edit().putBoolean("arrows", v).apply() }

    var particlesLevel: Int
        get() = p.getInt("particles", 1)
        set(v) { p.edit().putInt("particles", v.coerceIn(0, 2)).apply() }

    var frameCap30: Boolean
        get() = p.getBoolean("cap30", false)
        set(v) { p.edit().putBoolean("cap30", v).apply() }

    var sbs: Boolean
        get() = p.getBoolean("sbs", isRayNeoX3)
        set(v) { p.edit().putBoolean("sbs", v).apply() }

    var unlockedLevel: Int
        get() = p.getInt("unlocked", 1).coerceIn(1, 10)
        set(v) { p.edit().putInt("unlocked", v.coerceIn(1, 10)).apply() }

    fun bestRun(level: Int): Int = p.getInt("run$level", 0)
    fun setBestRun(level: Int, v: Int) { p.edit().putInt("run$level", v).apply() }

    fun bestStars(level: Int): Int = p.getInt("stars$level", 0)
    fun setBestStars(level: Int, v: Int) { p.edit().putInt("stars$level", v.coerceIn(0, 3)).apply() }

    fun resetProgress() {
        val e = p.edit()
        e.putInt("unlocked", 1)
        for (i in 1..10) { e.remove("run$i"); e.remove("stars$i") }
        e.apply()
    }
}
