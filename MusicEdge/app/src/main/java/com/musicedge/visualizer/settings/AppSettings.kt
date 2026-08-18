package com.musicedge.visualizer.settings

/**
 * Every user-tunable value in one immutable snapshot. The engine reads it, never
 * writes it; the UI writes it through [SettingsRepository].
 *
 * The visual identity of the app is a moving multi-colour gradient, so there is no
 * single-colour option: [gradientColors] always holds exactly
 * [GRADIENT_COLOR_COUNT] entries and every effect paints from that palette.
 */
data class AppSettings(
    val enabled: Boolean,
    val effectId: String,
    val gradientColors: List<Int>,
    val thicknessDp: Float,
    val brightness: Float,
    val glowIntensity: Float,
    val animationSpeed: Float,
    val performanceMode: PerformanceMode,
    val allowedPackages: Set<String>,
    /** Packages that were actually seen owning a media session on this device. */
    val discoveredPackages: Set<String>,
) {
    companion object {
        const val DEFAULT_EFFECT_ID = "flow"

        /** The gradient is always built from exactly this many user-chosen colours. */
        const val GRADIENT_COLOR_COUNT = 6

        /**
         * Starting palette. These are only the initial values of a user setting -
         * effects never reference them, they read whatever the user picked.
         */
        val DEFAULT_GRADIENT_COLORS: List<Int> = listOf(
            0xFF00E5FF.toInt(),
            0xFF2F6BFF.toInt(),
            0xFF9B5CFF.toInt(),
            0xFFFF4FA3.toInt(),
            0xFFFF8A34.toInt(),
            0xFF23E08A.toInt(),
        )

        const val DEFAULT_THICKNESS_DP = 3f
        const val MIN_THICKNESS_DP = 1.5f
        const val MAX_THICKNESS_DP = 10f

        const val DEFAULT_BRIGHTNESS = 0.85f
        const val MIN_BRIGHTNESS = 0.1f
        const val MAX_BRIGHTNESS = 1f

        /** 0 = flat line, 1 = widest halo. */
        const val DEFAULT_GLOW_INTENSITY = 0.6f
        const val MIN_GLOW_INTENSITY = 0f
        const val MAX_GLOW_INTENSITY = 1f

        /** Multiplier on the base flow speed; 1.0 = one lap around the screen per 15 s. */
        const val DEFAULT_ANIMATION_SPEED = 1f
        const val MIN_ANIMATION_SPEED = 0.05f
        const val MAX_ANIMATION_SPEED = 10f

        /** Fade duration used when playback stops. Spec: 500-1000 ms. */
        const val FADE_OUT_MILLIS = 700L
        const val FADE_IN_MILLIS = 250L

        /**
         * Music apps enabled out of the box. This is only a starting point - the
         * Music Apps screen lists everything installed, including players that are
         * not in this list.
         */
        val DEFAULT_ALLOWED_PACKAGES: Set<String> = setOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.sec.android.app.music",
            "com.apple.android.music",
            "com.maxmpz.audioplayer",
            "com.aspiro.tidal",
            "deezer.android.app",
            "com.soundcloud.android",
        )

        /** Pads or trims a palette so it always has [GRADIENT_COLOR_COUNT] entries. */
        fun normalizeColors(colors: List<Int>): List<Int> = when {
            colors.size == GRADIENT_COLOR_COUNT -> colors
            colors.isEmpty() -> DEFAULT_GRADIENT_COLORS
            colors.size > GRADIENT_COLOR_COUNT -> colors.take(GRADIENT_COLOR_COUNT)
            else -> colors + DEFAULT_GRADIENT_COLORS.drop(colors.size)
        }
    }
}

/**
 * Frame budget presets. [audioUpdatesPerSecond] caps how often the audio backend is
 * asked for new data - the renderer interpolates between those updates instead of
 * running an FFT per frame.
 */
enum class PerformanceMode(val targetFps: Int, val audioUpdatesPerSecond: Int) {
    BATTERY_SAVER(30, 20),
    BALANCED(60, 25),
    ULTRA_SMOOTH(120, 30),
}
