package com.musicedge.visualizer.effects

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SweepGradient
import com.musicedge.visualizer.overlay.EdgeGeometry

/**
 * Shared painting core for every gradient effect: one seamless sweep gradient
 * rotating around the panel, drawn as a soft halo plus a crisp core line.
 *
 * Why a [SweepGradient] with a local matrix:
 *  - the shader is created once per palette and rotated per frame with
 *    [Shader.setLocalMatrix], which is a cheap, hardware-accelerated transform -
 *    no per-frame allocation, no pixel work on the CPU;
 *  - every pass shares the same shader instance, so the halo is always exactly the
 *    colour of the line under it and moves with it. A blurred copy or a fixed glow
 *    colour would break that;
 *  - [android.graphics.BlurMaskFilter] is deliberately avoided: it forces parts of
 *    the draw off the hardware pipeline, which an always-on overlay cannot afford.
 *    The halo is shaped by [GlowProfile] instead.
 *
 * The palette is closed: the first colour is repeated as the last stop, so the
 * moving gradient has no visible seam. Per-colour alpha is preserved - a translucent
 * colour stays translucent in the line and in its halo.
 */
class GradientEdgeRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        // Cheap insurance against banding in the faint halo levels on AMOLED.
        isDither = true
    }

    private val localMatrix = Matrix()
    private val glowAlphas = FloatArray(GlowProfile.PASS_COUNT)

    private var shader: SweepGradient? = null
    private var shaderColors: List<Int>? = null
    private var shaderCenterX = Float.NaN
    private var shaderCenterY = Float.NaN

    /**
     * @param alpha overall opacity 0..1 (brightness x fade x effect gain).
     * @param rotationDegrees position of the gradient along the perimeter; callers
     *   derive it from elapsed time so the motion is frame-rate independent.
     */
    fun draw(
        canvas: Canvas,
        perimeter: EdgeGeometry.Perimeter,
        colors: List<Int>,
        rotationDegrees: Float,
        thicknessPx: Float,
        alpha: Float,
        glowIntensity: Float,
    ) {
        val effectiveAlpha = alpha.coerceIn(0f, 1f)
        if (effectiveAlpha <= 0f || thicknessPx <= 0f || colors.isEmpty()) return

        val centerX = perimeter.bounds.centerX()
        val centerY = perimeter.bounds.centerY()
        val gradient = obtainShader(colors, centerX, centerY)

        localMatrix.setRotate(rotationDegrees, centerX, centerY)
        gradient.setLocalMatrix(localMatrix)
        paint.shader = gradient

        val glow = glowIntensity.coerceIn(0f, 1f)
        if (glow > 0f) {
            val haloRadius = thicknessPx * GlowProfile.MAX_SPREAD * glow
            GlowProfile.computePassAlphas(effectiveAlpha * GlowProfile.PEAK_ALPHA, glowAlphas)
            for (index in glowAlphas.indices) {
                val passAlpha = (glowAlphas[index] * 255f).toInt()
                // A pass that rounds to nothing is skipped rather than drawn blank.
                if (passAlpha < 1) continue
                paint.strokeWidth = GlowProfile.strokeWidth(index, thicknessPx, haloRadius)
                paint.alpha = passAlpha
                canvas.drawPath(perimeter.path, paint)
            }
        }

        paint.strokeWidth = thicknessPx
        paint.alpha = (effectiveAlpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawPath(perimeter.path, paint)
    }

    fun release() {
        paint.shader = null
        shader = null
        shaderColors = null
    }

    private fun obtainShader(colors: List<Int>, centerX: Float, centerY: Float): SweepGradient {
        val cached = shader
        if (cached != null && colors == shaderColors && centerX == shaderCenterX && centerY == shaderCenterY) {
            return cached
        }
        val created = SweepGradient(centerX, centerY, closedColors(colors), evenStops(colors.size + 1))
        shader = created
        shaderColors = colors
        shaderCenterX = centerX
        shaderCenterY = centerY
        return created
    }

    /** Palette with the first colour repeated at the end - the seam killer. */
    private fun closedColors(colors: List<Int>): IntArray {
        val result = IntArray(colors.size + 1)
        for (i in colors.indices) result[i] = colors[i]
        result[colors.size] = colors[0]
        return result
    }

    private fun evenStops(count: Int): FloatArray {
        val stops = FloatArray(count)
        val last = count - 1
        for (i in 0 until count) stops[i] = i.toFloat() / last
        return stops
    }
}
