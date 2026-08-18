package com.musicedge.visualizer.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.musicedge.visualizer.R
import kotlin.math.roundToInt

/**
 * A plain HSV picker with an opacity bar: hue, saturation/value square, alpha, and a
 * live preview on a checkerboard.
 *
 * Written with Canvas and pointer input rather than pulling in a colour-picker
 * library - it is a few dozen lines and keeps the APK small. Conversion between
 * ARGB and HSV uses the platform [android.graphics.Color] helpers, and the alpha the
 * user picks is stored in the colour itself, so every palette slot can have its own
 * transparency.
 */
@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor, it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember { mutableFloatStateOf(AndroidColor.alpha(initialColor) / 255f) }

    val selected = Color.hsv(hue, saturation, value).copy(alpha = alpha)
    val checkerColors = rememberCheckerboardColors()
    val checkerCellPx = with(LocalDensity.current) { CHECKER_CELL_DP.dp.toPx() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(36.dp)
                            .clip(MaterialTheme.shapes.small)
                            .drawBehind { drawCheckerboard(checkerColors, checkerCellPx) }
                            .background(selected),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = hexOf(selected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(
                                R.string.opacity_value,
                                (alpha * 100).roundToInt(),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SaturationValueArea(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { newSaturation, newValue ->
                        saturation = newSaturation
                        value = newValue
                    },
                )

                HueBar(hue = hue, onHueChange = { hue = it })

                AlphaBar(
                    opaqueColor = Color.hsv(hue, saturation, value),
                    alpha = alpha,
                    checkerColors = checkerColors,
                    checkerCellPx = checkerCellPx,
                    onAlphaChange = { alpha = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onColorSelected(
                        AndroidColor.HSVToColor(
                            (alpha * 255f).roundToInt().coerceIn(0, 255),
                            floatArrayOf(hue, saturation, value),
                        )
                    )
                },
            ) {
                Text(stringResource(R.string.select))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun SaturationValueArea(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(MaterialTheme.shapes.small)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onChange(
                        (offset.x / size.width).coerceIn(0f, 1f),
                        1f - (offset.y / size.height).coerceIn(0f, 1f),
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onChange(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        1f - (change.position.y / size.height).coerceIn(0f, 1f),
                    )
                }
            },
    ) {
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f)))
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
        )
        drawCircle(
            color = Color.White,
            radius = SELECTOR_RADIUS_DP.dp.toPx(),
            center = Offset(saturation * size.width, (1f - value) * size.height),
            style = Stroke(width = SELECTOR_STROKE_DP.dp.toPx()),
        )
    }
}

@Composable
private fun HueBar(hue: Float, onHueChange: (Float) -> Unit) {
    val hueColors = remember {
        List(HUE_STOPS) { index -> Color.hsv(index * 360f / (HUE_STOPS - 1), 1f, 1f) }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT_DP.dp)
            .clip(MaterialTheme.shapes.small)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onHueChange((offset.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onHueChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            },
    ) {
        drawRect(brush = Brush.horizontalGradient(hueColors))
        drawMarker(hue / 360f)
    }
}

@Composable
private fun AlphaBar(
    opaqueColor: Color,
    alpha: Float,
    checkerColors: CheckerboardColors,
    checkerCellPx: Float,
    onAlphaChange: (Float) -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT_DP.dp)
            .clip(MaterialTheme.shapes.small)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onAlphaChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onAlphaChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        drawCheckerboard(checkerColors, checkerCellPx)
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(opaqueColor.copy(alpha = 0f), opaqueColor.copy(alpha = 1f))
            )
        )
        drawMarker(alpha)
    }
}

private fun DrawScope.drawMarker(fraction: Float) {
    val x = fraction.coerceIn(0f, 1f) * size.width
    drawLine(
        color = Color.White,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = SELECTOR_STROKE_DP.dp.toPx(),
    )
}

private fun hexOf(color: Color): String {
    val argb = color.toArgb()
    return if (AndroidColor.alpha(argb) == 255) {
        "#%06X".format(argb and 0xFFFFFF)
    } else {
        "#%08X".format(argb)
    }
}

private const val HUE_STOPS = 13
private const val SELECTOR_RADIUS_DP = 8
private const val SELECTOR_STROKE_DP = 2
private const val BAR_HEIGHT_DP = 36
