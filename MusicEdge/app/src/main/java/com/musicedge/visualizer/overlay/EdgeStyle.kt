package com.musicedge.visualizer.overlay

/**
 * Resolved drawing parameters in device pixels. Built by the controller from
 * [com.musicedge.visualizer.settings.AppSettings], so effects never read settings
 * or convert density themselves.
 *
 * [gradientColors] always holds the six colours the user picked; effects must not
 * introduce colours of their own.
 */
data class EdgeStyle(
    val gradientColors: List<Int>,
    val thicknessPx: Float,
    val brightness: Float,
    val glowIntensity: Float,
    val animationSpeed: Float,
)
