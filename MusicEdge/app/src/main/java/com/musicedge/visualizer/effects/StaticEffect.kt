package com.musicedge.visualizer.effects

import android.graphics.Canvas
import android.graphics.Paint
import com.musicedge.visualizer.overlay.EdgeGeometry
import com.musicedge.visualizer.overlay.EdgeStyle

/**
 * A steady line of the chosen color around the panel, with a soft halo.
 *
 * The halo is drawn as two wider, dimmer strokes rather than with a BlurMaskFilter:
 * mask filters fall back to software rendering, which would defeat the point of a
 * cheap always-on overlay. Three stroked paths cost one display-list replay.
 */
class StaticEffect : VisualizationEffect {

    override val id: String get() = ID

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Nothing moves, so once the fade has settled the renderer can go to sleep. */
    override fun needsContinuousFrames(hasLiveAudio: Boolean): Boolean = false

    override fun draw(
        canvas: Canvas,
        perimeter: EdgeGeometry.Perimeter,
        frame: RenderFrame,
        style: EdgeStyle,
    ) {
        val baseAlpha = (style.brightness * frame.fade * 255f).toInt().coerceIn(0, 255)
        if (baseAlpha == 0) return

        paint.color = style.colorArgb

        if (style.glow > 0f) {
            for (pass in GLOW_PASSES downTo 1) {
                paint.strokeWidth = style.thicknessPx * (1f + pass * GLOW_SPREAD * style.glow)
                paint.alpha = (baseAlpha * GLOW_ALPHA * style.glow / pass).toInt().coerceIn(0, 255)
                canvas.drawPath(perimeter.path, paint)
            }
        }

        paint.strokeWidth = style.thicknessPx
        paint.alpha = baseAlpha
        canvas.drawPath(perimeter.path, paint)
    }

    companion object {
        const val ID = "static"

        private const val GLOW_PASSES = 2
        private const val GLOW_SPREAD = 1.4f
        private const val GLOW_ALPHA = 0.22f
    }
}
