package com.musicedge.visualizer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.musicedge.visualizer.effects.FlowEffect
import com.musicedge.visualizer.effects.GlowProfile

/**
 * Live preview of the edge inside the settings screen: the same closed six-colour
 * sweep gradient, the same [GlowProfile] halo and the same time-based rotation as
 * the overlay, at card size. Per-colour alpha comes through unchanged, so a
 * translucent colour looks translucent here too.
 *
 * It never starts the real overlay or the audio engine. The clock comes from
 * [withFrameNanos], so it only advances while this screen is actually being drawn -
 * leaving the app stops it.
 */
@Composable
fun GradientEdgePreview(
    colors: List<Int>,
    thicknessDp: Float,
    brightness: Float,
    glowIntensity: Float,
    animationSpeed: Float,
    modifier: Modifier = Modifier,
) {
    var rotation by remember { mutableFloatStateOf(0f) }
    val speed by rememberUpdatedState(animationSpeed)

    LaunchedEffect(Unit) {
        var previousNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (previousNanos != 0L) {
                    val deltaSeconds = (now - previousNanos) / NANOS_PER_SECOND
                    rotation = (rotation + deltaSeconds * FlowEffect.BASE_DEGREES_PER_SECOND * speed) % 360f
                }
                previousNanos = now
            }
        }
    }

    // The first colour is repeated so the sweep closes without a seam; the stops are
    // evenly spaced, which is exactly what the List overload of sweepGradient does.
    val sweepColors = remember(colors) {
        (colors + colors.first()).map { Color(it) }
    }
    val glowAlphas = remember { FloatArray(GlowProfile.PASS_COUNT) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT_DP.dp),
    ) {
        val brush = Brush.sweepGradient(colors = sweepColors, center = center)
        val stroke = thicknessDp.dp.toPx()
        val corner = CornerRadius(CORNER_RADIUS_DP.dp.toPx(), CORNER_RADIUS_DP.dp.toPx())
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        val boxSize = Size(size.width - stroke, size.height - stroke)

        rotate(degrees = rotation) {
            val glow = glowIntensity.coerceIn(0f, 1f)
            if (glow > 0f) {
                val haloRadius = stroke * GlowProfile.MAX_SPREAD * glow
                GlowProfile.computePassAlphas(brightness * GlowProfile.PEAK_ALPHA, glowAlphas)
                for (index in 0 until GlowProfile.PASS_COUNT) {
                    val passAlpha = glowAlphas[index]
                    if (passAlpha < MIN_VISIBLE_ALPHA) continue
                    edgePass(
                        brush = brush,
                        topLeft = topLeft,
                        size = boxSize,
                        corner = corner,
                        strokeWidth = GlowProfile.strokeWidth(index, stroke, haloRadius),
                        alpha = passAlpha,
                    )
                }
            }
            edgePass(brush, topLeft, boxSize, corner, stroke, brightness)
        }
    }
}

private fun DrawScope.edgePass(
    brush: Brush,
    topLeft: Offset,
    size: Size,
    corner: CornerRadius,
    strokeWidth: Float,
    alpha: Float,
) {
    drawRoundRect(
        brush = brush,
        topLeft = topLeft,
        size = size,
        cornerRadius = corner,
        style = Stroke(width = strokeWidth),
        alpha = alpha.coerceIn(0f, 1f),
    )
}

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val PREVIEW_HEIGHT_DP = 132
private const val CORNER_RADIUS_DP = 26

/** Below one 8-bit alpha step the pass would draw nothing. */
private const val MIN_VISIBLE_ALPHA = 1f / 255f
