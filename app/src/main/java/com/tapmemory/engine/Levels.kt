package com.tapmemory.engine

/** How a level renders the sequence. */
enum class VisualMode { CROSS, ORBIT, BARS, WAVE, SHAPES, NONE }

/** How a level sounds the sequence. */
enum class AudioMode { TONES, WHOOSH, MELODY, PERCUSSION, NONE }

/**
 * Ten levels, each a different *combination* of sound and light — the whole
 * point of the game. Difficulty ramps three ways: which senses you get, how
 * long the target sequence is, and twists (reverse input, lying colors,
 * rhythmic gaps, speed).
 */
data class LevelConfig(
    val number: Int,
    val name: String,
    val tagline: String,
    val visual: VisualMode,
    val audio: AudioMode,
    val targetLen: Int,        // reproduce a sequence this long to clear
    val startLen: Int = 2,     // first round starts here
    val speed: Float = 1f,     // playback speed multiplier (higher = faster)
    val reverse: Boolean = false,
    val shuffleColors: Boolean = false,  // token->color remaps each round (a lie)
    val jitter: Float = 0f,    // rhythmic gap variation (0..1)
    val inputLimit: Float = 0f, // seconds allowed per input (0 = untimed)
    val themeA: Int = 0xFF3CC8FF.toInt(),
    val themeB: Int = 0xFFAA5AFF.toInt(),
    val intro: List<String>,
)

val LEVELS = listOf(
    LevelConfig(
        1, "First Signal", "see it · hear it · repeat it",
        VisualMode.CROSS, AudioMode.TONES, targetLen = 5,
        themeA = 0xFF3CC8FF.toInt(), themeB = 0xFFAA5AFF.toInt(),
        intro = listOf(
            "Watch the cross light up, then echo it back.",
            "Swipe up/down/left/right · click for center.",
            "Double-tap any time for settings."
        )
    ),
    LevelConfig(
        2, "Compass", "sound has a direction",
        VisualMode.ORBIT, AudioMode.WHOOSH, targetLen = 5, speed = 1.05f,
        themeA = 0xFF40E8D0.toInt(), themeB = 0xFF4C7CFF.toInt(),
        intro = listOf(
            "Whooshes pan to where they live — left in your left ear.",
            "A comet flies to each node. Follow it."
        )
    ),
    LevelConfig(
        3, "Blindfold", "ears only",
        VisualMode.NONE, AudioMode.TONES, targetLen = 5,
        themeA = 0xFF9A6CFF.toInt(), themeB = 0xFF3C6CFF.toInt(),
        intro = listOf(
            "The lights go dark. Trust your ears.",
            "High = up, low = down, panned = sideways, root = click."
        )
    ),
    LevelConfig(
        4, "Silent Light", "eyes only",
        VisualMode.CROSS, AudioMode.NONE, targetLen = 6,
        themeA = 0xFFB8C8DC.toInt(), themeB = 0xFF6A8CB4.toInt(),
        intro = listOf(
            "Now the sound is gone. Read the light.",
            "No tones — only the glow tells the story."
        )
    ),
    LevelConfig(
        5, "Equalizer", "drums on the bars",
        VisualMode.BARS, AudioMode.PERCUSSION, targetLen = 6, speed = 1.1f,
        themeA = 0xFFFF6A3C.toInt(), themeB = 0xFFFFB43C.toInt(),
        intro = listOf(
            "Each token is a drum and a bar.",
            "Learn which bar means which swipe."
        )
    ),
    LevelConfig(
        6, "Waveform", "read the ribbon",
        VisualMode.WAVE, AudioMode.MELODY, targetLen = 6, speed = 1.1f,
        themeA = 0xFF30D8F0.toInt(), themeB = 0xFF3060FF.toInt(),
        intro = listOf(
            "The sequence scrolls by as a wave of light and melody.",
            "Catch the order as it flows."
        )
    ),
    LevelConfig(
        7, "Shapeshifter", "colors lie, shapes don't",
        VisualMode.SHAPES, AudioMode.TONES, targetLen = 7, shuffleColors = true,
        themeA = 0xFFB46AFF.toInt(), themeB = 0xFF50E8FF.toInt(),
        intro = listOf(
            "Every round the colors reshuffle to fool you.",
            "Trust the shape, not the hue."
        )
    ),
    LevelConfig(
        8, "Backwards", "memory in reverse",
        VisualMode.CROSS, AudioMode.TONES, targetLen = 6, reverse = true,
        themeA = 0xFFFF50B4.toInt(), themeB = 0xFF7050FF.toInt(),
        intro = listOf(
            "Watch it forward — play it back BACKWARDS.",
            "Last light first."
        )
    ),
    LevelConfig(
        9, "Rhythm", "the gaps are the game",
        VisualMode.CROSS, AudioMode.PERCUSSION, targetLen = 7, speed = 1.15f,
        jitter = 0.8f, themeA = 0xFFFFC850.toInt(), themeB = 0xFFFF5AA0.toInt(),
        intro = listOf(
            "The timing swings — feel the groove.",
            "Same order, syncopated."
        )
    ),
    LevelConfig(
        10, "Chaos", "everything, all at once",
        VisualMode.SHAPES, AudioMode.MELODY, targetLen = 8, speed = 1.3f,
        reverse = true, shuffleColors = true, jitter = 0.6f, inputLimit = 3.2f,
        themeA = 0xFFE8F4FF.toInt(), themeB = 0xFF6AB4FF.toInt(),
        intro = listOf(
            "Fast. Reversed. Lying colors. A ticking clock.",
            "Show them what a mind can hold."
        )
    ),
)
