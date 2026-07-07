# TapMemory 🧠🎵

A sound-and-light memory game built natively for the **RayNeo X3 Pro** AR
glasses. Watch a sequence light up and sing, then play it back with your right
temple pad — **swipe up / down / left / right, click for center**. Every level
presents the sequence through a *different combination of sight and sound*, so
your memory has to work a new way each time.

Built from the FABLE_X3_STARTER_GUIDE recipe: 640×480 logical canvas, binocular
side-by-side rendering, pure-black waveguide-friendly background, zero vendor
AARs, zero permissions, zero binary assets — every tone is synthesized at first
launch.

## Controls (right temple pad)

| Gesture | Action |
|---|---|
| **Swipe ↑ ↓ ← →** | The four directional tokens (and menu navigation) |
| **Click** (temple tap) | The center token · confirm · advance |
| **Double-tap** | Open / close **settings** — any time |
| Left temple pad | System volume (ignored by the game) |

Also plays on a plain touchscreen (phone/emulator): same gestures. If a swipe
direction feels backwards on your hardware, flip it in settings (**Flip
Vertical / Flip Horizontal**) — cheap insurance for the temple pad's
natural-mode axis quirk.

### A note on the controls

The RayNeo X3 Pro's temple pad is a small trackpad, and swipe/click recognition
on it is **not inherently perfect** — a swipe can occasionally register as the
wrong direction, land as a stray click, or get missed entirely. That's the
hardware, not your memory. **TapMemory is designed so that's OK.** The goal is
to *practice audio-visual memory* — training your ear and eye to hold a growing
pattern — and that practice is valuable even when an input slips and costs you a
run. Treat a miscontrol like a dropped note in music practice: shrug, restart,
keep training. Tuning that helps: raise **Swipe Sensitivity** or set **Watch
Speed** to Relaxed/Slow in settings, and lean on **Safe Tap** so a forming
double-tap never fires a stray token.

## The five tokens

The whole game is five symbols laid out as a compass cross — the natural shape
of temple-pad input:

| Token | Gesture | Color | Pitch |
|---|---|---|---|
| **UP** | swipe up | cyan | high |
| **DOWN** | swipe down | magenta | low |
| **LEFT** | swipe left | green | mid (pans left) |
| **RIGHT** | swipe right | amber | mid (pans right) |
| **TAP** | click | violet | root |

Pitches are a major pentatonic scale, so any random sequence sounds musical,
and left/right cues literally pan to your left/right ear.

## The 10 levels — every one a different sense-mix

1. **First Signal** — cross of light + tones (classic Simon)
2. **Compass** — orbiting nodes + **panned directional whooshes**
3. **Blindfold** — screen goes dark, **ears only**
4. **Silent Light** — sound goes mute, **eyes only**
5. **Equalizer** — drum-kit hits on **bouncing bars**
6. **Waveform** — a scrolling **ribbon of light + melody** to read
7. **Shapeshifter** — distinct **shapes**; the colors reshuffle to lie to you
8. **Backwards** — watch it forward, play it back **reversed**
9. **Rhythm** — same order, **syncopated timing** with a swung groove
10. **Chaos** — fast, reversed, lying colors, and a **ticking clock**

Each round the sequence grows by one; reproduce it to the target length to
clear the level. Longer runs earn more of the 3 stars. Progress, best runs, and
stars are saved per level.

## Settings (double-tap)

Sound volume · swipe sensitivity · flip vertical / horizontal · watch speed
(accessibility: slow the playback) · safe tap · arrow hints · particles ·
frame cap (30/60 fps) · binocular SBS · replay sequence · level select ·
restart · quit · reset progress (with confirm). The menu uses the on-device
proven **latched one-step-per-swipe** navigation.

## Build & install

```bash
cd ~/Projects/TapMemory
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Toolchain: gradle 8.9 wrapper · AGP 8.7.3 · Kotlin 2.0.21 · JDK 17 ·
compileSdk 35 / minSdk 29 — the stack proven on-device by TapInsight.

## X3 specifics honored

- Black is transparency: the board floats as neon light on the world
- No `ar_mode` meta-data (it would halve the display to one lens)
- Temple click read as a KEY event (`KEYCODE_BUTTON_A`/`DPAD_CENTER`)
- Double-tap window 40–320 ms; first pad delta dropped; swipes classified
  by net displacement on finger-up
- `cyttsp6` (left arm volume pad) filtered out by device *name*
- RayNeo hardware detected by manufacturer/brand/product, not `Build.MODEL`
  (the X3 Pro reports `ARGF20`); binocular SBS auto-defaults on with a
  one-time migration (starter-guide gotcha #24)
- Settings swipe uses the latched stepper with `endSwipe` re-arm (gotcha #25)
- Sleep button (`onPause` while worn) auto-pauses into the settings overlay
