package com.musicedge.visualizer.effects

import android.graphics.Canvas
import com.musicedge.visualizer.overlay.EdgeGeometry
import com.musicedge.visualizer.overlay.EdgeStyle
import kotlin.math.exp

/**
 * The flowing gradient plus a reaction to the low end: on a bass hit the line gets
 * thicker, brighter and its halo wider, then settles back to the values the user
 * configured.
 *
 * The envelope has a fast attack and a slow release (roughly 40 ms / 240 ms), and
 * both are expressed as time constants rather than per-frame steps, so the pulse
 * feels identical at 30, 60 and 120 FPS. Everything is a multiplier on the user's
 * own settings: glow set to Off stays off, and alpha never exceeds full opacity.
 */
class BassPulseEffect : VisualizationEffect {

    override val id: String get() = ID

    private val renderer = GradientEdgeRenderer()
    private var envelope = 0f

    override fun needsContinuousFrames(hasLiveAudio: Boolean): Boolean = true

    override fun draw(
        canvas: Canvas,
        perimeter: EdgeGeometry.Perimeter,
        frame: RenderFrame,
        style: EdgeStyle,
    ) {
        envelope = advanceEnvelope(envelope, frame.audio.bass, frame.deltaSeconds)

        val rotation = FlowEffect.flowRotationDegrees(frame.elapsedSeconds, style.animationSpeed)
        val thickness = style.thicknessPx * (1f + THICKNESS_GAIN * envelope)
        val glow = (style.glowIntensity * (1f + GLOW_GAIN * envelope)).coerceAtMost(1f)
        val alpha = (style.brightness * (1f + BRIGHTNESS_GAIN * envelope)).coerceAtMost(1f)

        renderer.draw(
            canvas = canvas,
            perimeter = perimeter,
            colors = style.gradientColors,
            rotationDegrees = rotation,
            thicknessPx = thickness,
            alpha = alpha * frame.fade,
            glowIntensity = glow,
        )
    }

    override fun release() = renderer.release()

    private fun advanceEnvelope(current: Float, target: Float, deltaSeconds: Float): Float {
        if (deltaSeconds <= 0f) return current
        val tau = if (target > current) ATTACK_SECONDS else RELEASE_SECONDS
        val coefficient = 1f - exp(-deltaSeconds / tau)
        return current + (target.coerceIn(0f, 1f) - current) * coefficient
    }

    companion object {
        const val ID = "bass_pulse"

        private const val ATTACK_SECONDS = 0.04f
        private const val RELEASE_SECONDS = 0.24f

        /** 3 dp at rest becomes about 5.7 dp on a full-scale hit. */
        private const val THICKNESS_GAIN = 0.9f
        private const val GLOW_GAIN = 0.8f
        private const val BRIGHTNESS_GAIN = 0.25f
    }
}
