package com.musicedge.visualizer.effects

import android.graphics.Canvas
import com.musicedge.visualizer.overlay.EdgeGeometry
import com.musicedge.visualizer.overlay.EdgeStyle

/**
 * The default look: the user's six colours as one closed gradient that keeps
 * flowing around the panel, halo included.
 *
 * The rotation is derived from elapsed time, not accumulated per frame, so 30, 60
 * and 120 FPS all move at exactly the same visual speed.
 */
class FlowEffect : VisualizationEffect {

    override val id: String get() = ID

    private val renderer = GradientEdgeRenderer()

    override fun needsContinuousFrames(hasLiveAudio: Boolean): Boolean = true

    override fun draw(
        canvas: Canvas,
        perimeter: EdgeGeometry.Perimeter,
        frame: RenderFrame,
        style: EdgeStyle,
    ) {
        val rotation = flowRotationDegrees(frame.elapsedSeconds, style.animationSpeed)
        renderer.draw(
            canvas = canvas,
            perimeter = perimeter,
            colors = style.gradientColors,
            rotationDegrees = rotation,
            thicknessPx = style.thicknessPx,
            alpha = style.brightness * frame.fade,
            glowIntensity = style.glowIntensity,
        )
    }

    override fun release() = renderer.release()

    companion object {
        const val ID = "flow"

        /** One lap in 15 s at speed 1.0. */
        const val BASE_DEGREES_PER_SECOND = 24f

        fun flowRotationDegrees(elapsedSeconds: Float, animationSpeed: Float): Float =
            (elapsedSeconds * BASE_DEGREES_PER_SECOND * animationSpeed) % 360f
    }
}
