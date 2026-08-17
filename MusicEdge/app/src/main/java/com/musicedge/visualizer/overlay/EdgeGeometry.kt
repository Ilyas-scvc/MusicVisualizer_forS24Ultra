package com.musicedge.visualizer.overlay

import android.graphics.Path
import android.graphics.RectF
import android.view.RoundedCorner
import android.view.WindowInsets

/**
 * Builds the path the visualizer is drawn along: the actual outline of the panel,
 * not a plain rectangle.
 *
 * The per-corner radii come from [WindowInsets.getRoundedCorner] (API 31+), which
 * reports the real display corner radius - on the S24 Ultra the four corners are
 * equal, but the API is queried per corner so the code stays correct on foldables
 * and on devices with asymmetric corners.
 */
object EdgeGeometry {

    /** Used only if the platform reports no rounded corners (emulators, flat panels). */
    private const val FALLBACK_CORNER_RADIUS_DP = 40f

    data class CornerRadii(
        val topLeft: Float,
        val topRight: Float,
        val bottomRight: Float,
        val bottomLeft: Float,
    )

    class Perimeter(
        val path: Path,
        val bounds: RectF,
        val radii: CornerRadii,
    )

    fun readCornerRadii(insets: WindowInsets?, density: Float): CornerRadii {
        val fallback = FALLBACK_CORNER_RADIUS_DP * density
        fun radiusOf(position: Int): Float =
            insets?.getRoundedCorner(position)?.radius?.toFloat() ?: fallback

        return CornerRadii(
            topLeft = radiusOf(RoundedCorner.POSITION_TOP_LEFT),
            topRight = radiusOf(RoundedCorner.POSITION_TOP_RIGHT),
            bottomRight = radiusOf(RoundedCorner.POSITION_BOTTOM_RIGHT),
            bottomLeft = radiusOf(RoundedCorner.POSITION_BOTTOM_LEFT),
        )
    }

    /**
     * @param strokeWidthPx the widest stroke that will be drawn along the path; the
     *   outline is inset by half of it so the line is never clipped by the display
     *   edge and stays symmetric around the panel border.
     */
    fun buildPerimeter(
        widthPx: Int,
        heightPx: Int,
        corners: CornerRadii,
        strokeWidthPx: Float,
    ): Perimeter {
        val inset = strokeWidthPx / 2f
        val bounds = RectF(
            inset,
            inset,
            widthPx - inset,
            heightPx - inset,
        )
        val radii = CornerRadii(
            topLeft = (corners.topLeft - inset).coerceAtLeast(0f),
            topRight = (corners.topRight - inset).coerceAtLeast(0f),
            bottomRight = (corners.bottomRight - inset).coerceAtLeast(0f),
            bottomLeft = (corners.bottomLeft - inset).coerceAtLeast(0f),
        )
        val path = Path().apply {
            addRoundRect(
                bounds,
                floatArrayOf(
                    radii.topLeft, radii.topLeft,
                    radii.topRight, radii.topRight,
                    radii.bottomRight, radii.bottomRight,
                    radii.bottomLeft, radii.bottomLeft,
                ),
                Path.Direction.CW,
            )
        }
        return Perimeter(path, bounds, radii)
    }
}
