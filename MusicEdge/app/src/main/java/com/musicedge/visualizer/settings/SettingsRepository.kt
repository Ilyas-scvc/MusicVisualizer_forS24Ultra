package com.musicedge.visualizer.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SharedPreferences-backed settings store, shared by the UI and the listener
 * service (same process). No DataStore, no Room: a handful of scalars does not
 * justify the dependency.
 *
 * Reads are served from an in-memory [StateFlow] that is refreshed by the
 * preference change listener, so both the Activity and the service observe the same
 * value without polling. Everything written here survives process death and reboot.
 */
class SettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Held as a field on purpose: SharedPreferences keeps only a weak reference.
    private val changeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _settings.value = read() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(changeListener)
    }

    val current: AppSettings get() = _settings.value

    fun setEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun setEffectId(effectId: String) = prefs.edit().putString(KEY_EFFECT, effectId).apply()

    /** Replaces the whole palette; the list is normalised to six colours first. */
    fun setGradientColors(colors: List<Int>) = prefs.edit()
        .putString(KEY_GRADIENT_COLORS, encodeColors(AppSettings.normalizeColors(colors)))
        .apply()

    /** Replaces one slot of the palette, keeping the other five untouched. */
    fun setGradientColor(index: Int, colorArgb: Int) {
        if (index !in 0 until AppSettings.GRADIENT_COLOR_COUNT) return
        val updated = current.gradientColors.toMutableList()
        updated[index] = colorArgb
        setGradientColors(updated)
    }

    fun setThicknessDp(thicknessDp: Float) = prefs.edit()
        .putFloat(
            KEY_THICKNESS,
            thicknessDp.coerceIn(AppSettings.MIN_THICKNESS_DP, AppSettings.MAX_THICKNESS_DP),
        )
        .apply()

    fun setBrightness(brightness: Float) = prefs.edit()
        .putFloat(
            KEY_BRIGHTNESS,
            brightness.coerceIn(AppSettings.MIN_BRIGHTNESS, AppSettings.MAX_BRIGHTNESS),
        )
        .apply()

    fun setGlowIntensity(glow: Float) = prefs.edit()
        .putFloat(
            KEY_GLOW,
            glow.coerceIn(AppSettings.MIN_GLOW_INTENSITY, AppSettings.MAX_GLOW_INTENSITY),
        )
        .apply()

    fun setAnimationSpeed(speed: Float) = prefs.edit()
        .putFloat(
            KEY_ANIMATION_SPEED,
            speed.coerceIn(AppSettings.MIN_ANIMATION_SPEED, AppSettings.MAX_ANIMATION_SPEED),
        )
        .apply()

    fun setPerformanceMode(mode: PerformanceMode) =
        prefs.edit().putString(KEY_PERFORMANCE, mode.name).apply()

    fun setAllowedPackages(packages: Set<String>) =
        prefs.edit().putStringSet(KEY_ALLOWED_PACKAGES, packages).apply()

    fun setPackageAllowed(packageName: String, allowed: Boolean) {
        val updated = current.allowedPackages.toMutableSet()
        if (allowed) updated += packageName else updated -= packageName
        setAllowedPackages(updated)
    }

    /**
     * Remembers a package that really owned a media session. Players that publish
     * neither a MediaBrowserService nor an APP_MUSIC launcher entry become visible
     * in the Music Apps list this way.
     */
    fun rememberDiscoveredPackage(packageName: String) {
        val known = current.discoveredPackages
        if (packageName in known) return
        prefs.edit().putStringSet(KEY_DISCOVERED_PACKAGES, known + packageName).apply()
    }

    private fun read(): AppSettings = AppSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        effectId = prefs.getString(KEY_EFFECT, null) ?: AppSettings.DEFAULT_EFFECT_ID,
        gradientColors = decodeColors(prefs.getString(KEY_GRADIENT_COLORS, null)),
        thicknessDp = prefs.getFloat(KEY_THICKNESS, AppSettings.DEFAULT_THICKNESS_DP),
        brightness = prefs.getFloat(KEY_BRIGHTNESS, AppSettings.DEFAULT_BRIGHTNESS),
        glowIntensity = prefs.getFloat(KEY_GLOW, AppSettings.DEFAULT_GLOW_INTENSITY),
        animationSpeed = prefs.getFloat(KEY_ANIMATION_SPEED, AppSettings.DEFAULT_ANIMATION_SPEED),
        performanceMode = readPerformanceMode(),
        allowedPackages = prefs.getStringSet(KEY_ALLOWED_PACKAGES, null)
            ?: AppSettings.DEFAULT_ALLOWED_PACKAGES,
        discoveredPackages = prefs.getStringSet(KEY_DISCOVERED_PACKAGES, null) ?: emptySet(),
    )

    private fun readPerformanceMode(): PerformanceMode {
        val stored = prefs.getString(KEY_PERFORMANCE, null) ?: return PerformanceMode.BALANCED
        return runCatching { PerformanceMode.valueOf(stored) }.getOrDefault(PerformanceMode.BALANCED)
    }

    private fun encodeColors(colors: List<Int>): String = colors.joinToString(separator = ",")

    private fun decodeColors(stored: String?): List<Int> {
        if (stored.isNullOrBlank()) return AppSettings.DEFAULT_GRADIENT_COLORS
        val parsed = stored.split(',').mapNotNull { it.trim().toIntOrNull() }
        return AppSettings.normalizeColors(parsed)
    }

    companion object {
        private const val FILE_NAME = "music_edge_settings"

        private const val KEY_ENABLED = "enabled"
        private const val KEY_EFFECT = "effect_id"
        private const val KEY_GRADIENT_COLORS = "gradient_colors"
        private const val KEY_THICKNESS = "thickness_dp"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_GLOW = "glow_intensity"
        private const val KEY_ANIMATION_SPEED = "animation_speed"
        private const val KEY_PERFORMANCE = "performance_mode"
        private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
        private const val KEY_DISCOVERED_PACKAGES = "discovered_packages"

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
    }
}
