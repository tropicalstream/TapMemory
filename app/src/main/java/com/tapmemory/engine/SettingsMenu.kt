package com.tapmemory.engine

import com.tapmemory.SettingsStore

class SettingsItem(
    val label: String,
    val value: () -> String,
    val adjust: ((Int) -> Unit)? = null,
    val activate: (() -> Unit)? = null,
)

/**
 * Settings overlay (double-tap to enter/exit). Navigation uses the on-device
 * proven LATCHED stepper (FABLE_X3_STARTER_GUIDE gotcha #25): one swipe
 * gesture = one step; the latch re-arms only after the accumulator settles
 * back toward center or the gesture ends (endSwipe), so a single fast pad
 * flick can't skip several rows.
 */
class SettingsMenu(private val engine: GameEngine, private val store: SettingsStore) {
    var selected = 0
    var confirmingReset = false
    var levelSel = 1
    private var accX = 0f
    private var accY = 0f
    private var vLatched = false
    private var hLatched = false

    private val slowNames = arrayOf("Normal", "Relaxed", "Slow")
    private val partNames = arrayOf("Low", "Normal", "Ultra")
    private val moveThreshold = 42f
    private val moveRearm = 14f
    private val adjustThreshold = 56f
    private val adjustRearm = 18f

    fun onOpen() {
        selected = 0; accX = 0f; accY = 0f
        vLatched = false; hLatched = false
        confirmingReset = false
        levelSel = engine.levelIdx
    }

    val items: List<SettingsItem> = listOf(
        SettingsItem("Resume", { "" }, activate = { engine.doubleTap() }),
        SettingsItem("Sound Volume", { "${store.soundVolume * 10}%" }, adjust = { d ->
            store.soundVolume += d; engine.host.applySettings()
        }),
        SettingsItem("Swipe Sensitivity", { "%.1f".format(store.swipeSens) }, adjust = { d ->
            store.swipeSens += d * 0.1f
        }),
        SettingsItem("Flip Vertical", { if (store.flipVertical) "On" else "Off" }, adjust = {
            store.flipVertical = !store.flipVertical
        }),
        SettingsItem("Flip Horizontal", { if (store.flipHorizontal) "On" else "Off" }, adjust = {
            store.flipHorizontal = !store.flipHorizontal
        }),
        SettingsItem("Watch Speed", { slowNames[store.watchSlow] }, adjust = { d ->
            store.watchSlow = (store.watchSlow + d + 3) % 3
        }),
        SettingsItem("Safe Tap", { if (store.safeTap) "On" else "Off" }, adjust = {
            store.safeTap = !store.safeTap
        }),
        SettingsItem("Arrow Hints", { if (store.arrowHints) "On" else "Off" }, adjust = {
            store.arrowHints = !store.arrowHints
        }),
        SettingsItem("Particles", { partNames[store.particlesLevel] }, adjust = { d ->
            store.particlesLevel = (store.particlesLevel + d + 3) % 3; engine.host.applySettings()
        }),
        SettingsItem("Frame Cap", { if (store.frameCap30) "30 fps" else "60 fps" }, adjust = {
            store.frameCap30 = !store.frameCap30; engine.host.applySettings()
        }),
        SettingsItem("Binocular SBS", { if (store.sbs) "On" else "Off" }, adjust = {
            store.sbs = !store.sbs; engine.host.applySettings()
        }),
        SettingsItem("Replay Sequence", { "" }, activate = { engine.doubleTap(); engine.replay() }),
        SettingsItem(
            "Level Select", { "Level $levelSel" },
            adjust = { d -> levelSel = (levelSel + d).coerceIn(1, store.unlockedLevel) },
            activate = { engine.jumpToLevel(levelSel) }
        ),
        SettingsItem("Restart Level", { "" }, activate = { engine.restartLevel() }),
        SettingsItem("Quit To Title", { "" }, activate = { engine.quitToMenu() }),
        SettingsItem("Reset Progress", { if (confirmingReset) "tap again!" else "" }, activate = {
            if (confirmingReset) { store.resetProgress(); confirmingReset = false; levelSel = 1 }
            else confirmingReset = true
        }),
    )

    fun swipe(dx: Float, dy: Float) {
        accY += dy; accX += dx
        if (vLatched && kotlin.math.abs(accY) <= moveRearm) { vLatched = false; accY = 0f }
        if (!vLatched) {
            when {
                accY > moveThreshold -> { move(1); vLatched = true; accY = 0f }
                accY < -moveThreshold -> { move(-1); vLatched = true; accY = 0f }
            }
        }
        if (hLatched && kotlin.math.abs(accX) <= adjustRearm) { hLatched = false; accX = 0f }
        if (!hLatched) {
            when {
                accX > adjustThreshold -> { adjust(1); hLatched = true; accX = 0f }
                accX < -adjustThreshold -> { adjust(-1); hLatched = true; accX = 0f }
            }
        }
    }

    fun endSwipe() {
        accX = 0f; accY = 0f; vLatched = false; hLatched = false
    }

    private fun move(d: Int) {
        selected = (selected + d + items.size) % items.size
        confirmingReset = false
        accX = 0f
        engine.host.ui(Ui.TICK)
    }

    private fun adjust(d: Int) {
        items[selected].adjust?.invoke(d) ?: return
        engine.host.ui(Ui.TICK, 1.3f)
    }

    fun activate() {
        val item = items[selected]
        if (item.activate != null) { item.activate.invoke(); engine.host.ui(Ui.SELECT) }
        else { item.adjust?.invoke(1); engine.host.ui(Ui.TICK, 1.3f) }
    }
}
