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
    private val displayManager: DisplayManager =
        requireNotNull(context.getSystemService(DisplayManager::class.java))
    private val defaultDisplay: Display =
        requireNotNull(displayManager.getDisplay(Display.DEFAULT_DISPLAY))
    private val windowContext: Context = context.createDisplayContext(defaultDisplay)
        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    private val windowManager: WindowManager =
        requireNotNull(windowContext.getSystemService(WindowManager::class.java))

    private var view: EdgeVisualizerView? = null

    /**
     * The window is sized in real pixels (see [buildLayoutParams]), so a rotation
     * would leave it at the stale size; re-apply the layout params whenever the
     * default display changes configuration.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val overlayView = view ?: return
            runCatching { windowManager.updateViewLayout(overlayView, buildLayoutParams()) }
                .onFailure { Log.w(TAG, "Overlay relayout failed", it) }
        }
    }

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
        displayManager.registerDisplayListener(displayListener, null)
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
        displayManager.unregisterDisplayListener(displayListener)
        runCatching { windowManager.removeViewImmediate(overlayView) }
            .onFailure { Log.w(TAG, "Overlay already detached", it) }
    }

    /**
     * The window is sized to the full panel in pixels instead of MATCH_PARENT.
     *
     * For TYPE_APPLICATION_OVERLAY the parent frame MATCH_PARENT resolves against
     * stops at the navigation bar, while FLAG_LAYOUT_IN_SCREEN only re-anchors the
     * top edge - which left the bottom of the panel uncovered. The maximum window
     * metrics report the real display bounds (system bars included), and
     * FLAG_LAYOUT_NO_LIMITS lets the window actually occupy them, so the perimeter
     * path now runs along the true physical edge on all four sides.
     */
    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val bounds = windowManager.maximumWindowMetrics.bounds
        return WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
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
    }

    private companion object {
        const val TAG = "OverlayManager"
        const val OVERLAY_WINDOW_TITLE = "MusicEdgeOverlay"
    }
}