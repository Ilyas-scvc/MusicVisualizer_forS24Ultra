package com.musicedge.visualizer.effects

import android.graphics.Canvas
import com.musicedge.visualizer.audio.AudioFrame
import com.musicedge.visualizer.overlay.EdgeGeometry
import com.musicedge.visualizer.overlay.EdgeStyle

/** Everything an effect gets for one frame. */
data class RenderFrame(
    /** Seconds since the effect started drawing; the time base for animations. */
    val elapsedSeconds: Float,
    val deltaSeconds: Float,
    /** Global fade 0..1 applied on show/hide. */
    val fade: Float,
    val audio: AudioFrame,
)

/**
 * A way of painting the display perimeter. Implementations are created per overlay
 * session, drawn on the main thread only, and must not allocate inside [draw].
 */
interface VisualizationEffect {

    val id: String

    /**
     * Whether the effect still changes between frames. Returning false when the
     * picture is settled lets the renderer stop asking for frames entirely, which
     * is what keeps an idle static line at zero CPU.
     */
    fun needsContinuousFrames(hasLiveAudio: Boolean): Boolean = hasLiveAudio

    fun draw(
        canvas: Canvas,
        perimeter: EdgeGeometry.Perimeter,
        frame: RenderFrame,
        style: EdgeStyle,
    )

    /** Release any Paint-level resources; called when the overlay goes away. */
    fun release() {}
}
