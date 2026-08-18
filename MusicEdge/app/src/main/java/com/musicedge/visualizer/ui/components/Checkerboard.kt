package com.musicedge.visualizer.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Default size of one checkerboard square. */
internal const val CHECKER_CELL_DP = 6

/** Colours of the transparency checkerboard, picked from the current theme. */
data class CheckerboardColors(val light: Color, val dark: Color)

@Composable
@ReadOnlyComposable
fun rememberCheckerboardColors(): CheckerboardColors = CheckerboardColors(
    light = MaterialTheme.colorScheme.surfaceVariant,
    dark = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
)

/**
 * The usual chequered backdrop, drawn behind translucent colours so the alpha the
 * user picked is actually visible instead of blending into the card.
 */
fun DrawScope.drawCheckerboard(colors: CheckerboardColors, cellSizePx: Float) {
    drawRect(color = colors.light)
    val cell = cellSizePx.coerceAtLeast(1f)
    var row = 0
    var y = 0f
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) {
            drawRect(
                color = colors.dark,
                topLeft = Offset(x, y),
                size = Size(
                    width = minOf(cell, size.width - x),
                    height = minOf(cell, size.height - y),
                ),
            )
            x += cell * 2f
        }
        y += cell
        row++
    }
}
