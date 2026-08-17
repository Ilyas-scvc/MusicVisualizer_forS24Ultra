package com.musicedge.visualizer.core

/**
 * Engine state machine.
 *
 * DISABLED           master switch off
 * IDLE               enabled, but a required permission is missing / listener not bound
 * WAITING_FOR_MEDIA  armed and listening; nothing is drawn, nothing is computed
 * ACTIVE             whitelisted app is playing, overlay visible and rendering
 * PAUSED_FADE        playback stopped, overlay fading out
 * SCREEN_OFF         display is off; rendering torn down, state remembered
 */
enum class VisualizerState {
    DISABLED,
    IDLE,
    WAITING_FOR_MEDIA,
    ACTIVE,
    PAUSED_FADE,
    SCREEN_OFF,
}
