package com.musicedge.visualizer.settings

/**
 * Every user-tunable value in one immutable snapshot. The engine reads it, never
 * writes it; the UI writes it through [SettingsRepository].
 */
data class AppSettings(
    val enabled: Boolean,
    val effectId: String,
    val colorArgb: Int,
    val thicknessDp: Float,
    val brightness: Float,
    val performanceMode: PerformanceMode,
    val allowedPackages: Set<String>,
) {
    companion object {
        const val DEFAULT_EFFECT_ID = "static"

        /** One UI accent-ish blue; reads well on the S24 Ultra AMOLED panel. */
        const val DEFAULT_COLOR = 0xFF4C7DFF.toInt()

        const val DEFAULT_THICKNESS_DP = 3f
        const val MIN_THICKNESS_DP = 1.5f
        const val MAX_THICKNESS_DP = 10f

        const val DEFAULT_BRIGHTNESS = 0.85f

        /** Fade duration used when playback stops. Spec: 500-1000 ms. */
        const val FADE_OUT_MILLIS = 700L
        const val FADE_IN_MILLIS = 250L

        /**
         * Music apps enabled out of the box. The Stage 4 "Music Apps" screen will let
         * the user edit this set; the engine already honours it today.
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
    }
}

/**
 * Frame budget presets. [audioUpdatesPerSecond] is unused by the MVP renderer and
 * becomes the AudioEngine polling rate in Stage 2.
 */
enum class PerformanceMode(val targetFps: Int, val audioUpdatesPerSecond: Int) {
    BATTERY_SAVER(30, 20),
    BALANCED(60, 25),
    ULTRA_SMOOTH(120, 30),
}
