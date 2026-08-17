package com.musicedge.visualizer.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SharedPreferences-backed settings store, shared by the UI process components and
 * the listener service (same process). No DataStore, no Room: a handful of scalars
 * does not justify the dependency.
 *
 * Reads are served from an in-memory [StateFlow] that is refreshed by the
 * preference change listener, so both the Activity and the service observe the same
 * value without polling.
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

    fun setColor(colorArgb: Int) = prefs.edit().putInt(KEY_COLOR, colorArgb).apply()

    fun setThicknessDp(thicknessDp: Float) = prefs.edit()
        .putFloat(KEY_THICKNESS, thicknessDp.coerceIn(AppSettings.MIN_THICKNESS_DP, AppSettings.MAX_THICKNESS_DP))
        .apply()

    fun setBrightness(brightness: Float) =
        prefs.edit().putFloat(KEY_BRIGHTNESS, brightness.coerceIn(0.1f, 1f)).apply()

    fun setPerformanceMode(mode: PerformanceMode) =
        prefs.edit().putString(KEY_PERFORMANCE, mode.name).apply()

    fun setAllowedPackages(packages: Set<String>) =
        prefs.edit().putStringSet(KEY_ALLOWED_PACKAGES, packages).apply()

    private fun read(): AppSettings = AppSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        effectId = prefs.getString(KEY_EFFECT, null) ?: AppSettings.DEFAULT_EFFECT_ID,
        colorArgb = prefs.getInt(KEY_COLOR, AppSettings.DEFAULT_COLOR),
        thicknessDp = prefs.getFloat(KEY_THICKNESS, AppSettings.DEFAULT_THICKNESS_DP),
        brightness = prefs.getFloat(KEY_BRIGHTNESS, AppSettings.DEFAULT_BRIGHTNESS),
        performanceMode = readPerformanceMode(),
        allowedPackages = prefs.getStringSet(KEY_ALLOWED_PACKAGES, null)
            ?: AppSettings.DEFAULT_ALLOWED_PACKAGES,
    )

    private fun readPerformanceMode(): PerformanceMode {
        val stored = prefs.getString(KEY_PERFORMANCE, null) ?: return PerformanceMode.BALANCED
        return runCatching { PerformanceMode.valueOf(stored) }.getOrDefault(PerformanceMode.BALANCED)
    }

    companion object {
        private const val FILE_NAME = "music_edge_settings"

        private const val KEY_ENABLED = "enabled"
        private const val KEY_EFFECT = "effect_id"
        private const val KEY_COLOR = "color_argb"
        private const val KEY_THICKNESS = "thickness_dp"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_PERFORMANCE = "performance_mode"
        private const val KEY_ALLOWED_PACKAGES = "allowed_packages"

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
    }
}
