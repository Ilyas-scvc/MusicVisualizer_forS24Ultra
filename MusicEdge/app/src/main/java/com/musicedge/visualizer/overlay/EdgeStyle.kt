package com.musicedge.visualizer.overlay

/**
 * Resolved drawing parameters in device pixels. Built by the controller from
 * [com.musicedge.visualizer.settings.AppSettings] so effects never touch settings
 * or density conversion themselves.
 */
data class EdgeStyle(
    val colorArgb: Int,
    val thicknessPx: Float,
    val brightness: Float,
    val glow: Float = DEFAULT_GLOW,
) {
    companion object {
        /** 0 = flat line, 1 = widest halo. Kept subtle for AMOLED. */
        const val DEFAULT_GLOW = 0.6f
    }
}
