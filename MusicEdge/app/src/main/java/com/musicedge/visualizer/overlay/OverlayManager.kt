package com.musicedge.visualizer.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import com.musicedge.visualizer.audio.AudioEngine
import com.musicedge.visualizer.effects.VisualizationEffect

/**
 * Owns the overlay window: one [EdgeVisualizerView] added to [WindowManager] with
 * TYPE_APPLICATION_OVERLAY.
 *
 * Window contract:
 *  - FLAG_NOT_TOUCHABLE + FLAG_NOT_FOCUSABLE: every touch and every key event goes
 *    to the app underneath, and the overlay never takes IME focus;
 *  - FLAG_LAYOUT_NO_LIMITS + LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS: the window spans
 *    the whole panel including the area behind the status bar, the navigation bar
 *    and the camera cutout, which is exactly where the edge line has to be drawn;
 *  - PixelFormat.TRANSLUCENT and no background: only the stroked path is ever
 *    rasterised, the rest of the window stays fully transparent.
 *
 * The view is built from a window context ([Context.createWindowContext]) so it
 * resolves configuration, density and insets against the display it is shown on
 * instead of against the service's context.
 */
class OverlayManager(context: Context, private val audioEngine: AudioEngine) {

    // createWindowContext() requires a display-associated context, which a Service
    // context is not guaranteed to be - so go through the default display explicitly.
    private val windowContext: Context = run {
        val displayManager = requireNotNull(context.getSystemService(DisplayManager::class.java))
        val defaultDisplay = requireNotNull(displayManager.getDisplay(Display.DEFAULT_DISPLAY))
        context.createDisplayContext(defaultDisplay)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }
    private val windowManager: WindowManager =
        requireNotNull(windowContext.getSystemService(WindowManager::class.java))

    private var view: EdgeVisualizerView? = null

    val isShowing: Boolean get() = view != null

    fun show(style: EdgeStyle, effect: VisualizationEffect, targetFps: Int) {
        if (view != null) return
        val overlayView = EdgeVisualizerView(windowContext, style, effect, audioEngine).apply {
            setTargetFps(targetFps)
        }
        try {
            windowManager.addView(overlayView, buildLayoutParams())
        } catch (e: WindowManager.BadTokenException) {
            // Overlay permission was revoked while we were running.
            Log.w(TAG, "Overlay window rejected", e)
            return
        }
        view = overlayView
    }

    fun fadeIn(durationMillis: Long) {
        view?.fadeTo(1f, durationMillis)
    }

    fun fadeOut(durationMillis: Long, onComplete: () -> Unit) {
        val overlayView = view ?: run {
            onComplete()
            return
        }
        overlayView.fadeTo(0f, durationMillis, onComplete)
    }

    fun updateStyle(style: EdgeStyle) {
        view?.setStyle(style)
    }

    fun updateEffect(effect: VisualizationEffect) {
        view?.setEffect(effect)
    }

    fun updateTargetFps(fps: Int) {
        view?.setTargetFps(fps)
    }

    /** Removes the window immediately; safe to call when nothing is shown. */
    fun hide() {
        val overlayView = view ?: return
        view = null
        runCatching { windowManager.removeViewImmediate(overlayView) }
            .onFailure { Log.w(TAG, "Overlay already detached", it) }
    }

    private fun buildLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        title = OVERLAY_WINDOW_TITLE
    }

    private companion object {
        const val TAG = "OverlayManager"
        const val OVERLAY_WINDOW_TITLE = "MusicEdgeOverlay"
    }
}
