package com.musicedge.visualizer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicedge.visualizer.R
import com.musicedge.visualizer.core.VisualizerState
import com.musicedge.visualizer.core.VisualizerStatus
import com.musicedge.visualizer.settings.AppSettings
import com.musicedge.visualizer.settings.PerformanceMode
import com.musicedge.visualizer.ui.components.CardDivider
import com.musicedge.visualizer.ui.components.ChoiceRow
import com.musicedge.visualizer.ui.components.ColorSwatchRow
import com.musicedge.visualizer.ui.components.LabeledValueRow
import com.musicedge.visualizer.ui.components.OutlinedInfoBox
import com.musicedge.visualizer.ui.components.SectionTitle
import com.musicedge.visualizer.ui.components.SettingsCard
import com.musicedge.visualizer.ui.components.SliderRow
import com.musicedge.visualizer.ui.components.ToggleRow

/** Which special-access permissions are currently granted. */
data class PermissionState(
    val overlay: Boolean,
    val notificationAccess: Boolean,
) {
    val allGranted: Boolean get() = overlay && notificationAccess
}

/** Preset colors offered on the home screen; a full picker comes with the Colors screen. */
private val PRESET_COLORS = listOf(
    0xFF4C7DFF.toInt(),
    0xFF9F6BFF.toInt(),
    0xFF00D0C0.toInt(),
    0xFFFF5C8A.toInt(),
    0xFFFFB020.toInt(),
    0xFFFFFFFF.toInt(),
)

@Composable
fun HomeScreen(
    settings: AppSettings,
    status: VisualizerStatus.Snapshot,
    permissions: PermissionState,
    modifier: Modifier = Modifier,
    onEnabledChange: (Boolean) -> Unit,
    onColorChange: (Int) -> Unit,
    onThicknessChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onPerformanceModeChange: (PerformanceMode) -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Header()

        Column {
            SectionTitle(stringResource(R.string.visualizer))
            SettingsCard {
                ToggleRow(
                    title = stringResource(R.string.visualizer),
                    subtitle = stringResource(R.string.visualizer_summary),
                    checked = settings.enabled,
                    onCheckedChange = onEnabledChange,
                )
                CardDivider()
                LabeledValueRow(
                    title = stringResource(R.string.status),
                    value = statusLabel(settings, status, permissions),
                )
                val track = trackLabel(status)
                if (track != null) {
                    Text(
                        text = track,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                EdgePreview(
                    colorArgb = settings.colorArgb,
                    thicknessDp = settings.thicknessDp,
                    brightness = settings.brightness,
                )
            }
        }

        if (!permissions.allGranted) {
            Column {
                SectionTitle(stringResource(R.string.permissions))
                SettingsCard {
                    PermissionRow(
                        title = stringResource(R.string.permission_overlay_title),
                        reason = stringResource(R.string.permission_overlay_reason),
                        granted = permissions.overlay,
                        onGrant = onGrantOverlay,
                    )
                    CardDivider()
                    PermissionRow(
                        title = stringResource(R.string.permission_notifications_title),
                        reason = stringResource(R.string.permission_notifications_reason),
                        granted = permissions.notificationAccess,
                        onGrant = onGrantNotificationAccess,
                    )
                }
            }
        }

        Column {
            SectionTitle(stringResource(R.string.appearance))
            SettingsCard {
                Text(
                    text = stringResource(R.string.color),
                    style = MaterialTheme.typography.bodyLarge,
                )
                ColorSwatchRow(
                    colors = PRESET_COLORS,
                    selectedColor = settings.colorArgb,
                    onColorSelected = onColorChange,
                )
                CardDivider()
                SliderRow(
                    title = stringResource(R.string.thickness),
                    valueLabel = stringResource(R.string.thickness_value, settings.thicknessDp),
                    value = settings.thicknessDp,
                    valueRange = AppSettings.MIN_THICKNESS_DP..AppSettings.MAX_THICKNESS_DP,
                    onValueChange = onThicknessChange,
                )
                SliderRow(
                    title = stringResource(R.string.brightness),
                    valueLabel = stringResource(
                        R.string.percent_value,
                        (settings.brightness * 100).toInt(),
                    ),
                    value = settings.brightness,
                    valueRange = 0.1f..1f,
                    onValueChange = onBrightnessChange,
                )
            }
        }

        Column {
            SectionTitle(stringResource(R.string.performance))
            SettingsCard {
                PerformanceMode.entries.forEach { mode ->
                    ChoiceRow(
                        title = performanceLabel(mode),
                        subtitle = stringResource(R.string.performance_fps, mode.targetFps),
                        selected = settings.performanceMode == mode,
                        onClick = { onPerformanceModeChange(mode) },
                    )
                }
                if (settings.performanceMode == PerformanceMode.ULTRA_SMOOTH) {
                    OutlinedInfoBox(stringResource(R.string.performance_120_warning))
                }
            }
        }

        Column {
            SectionTitle(stringResource(R.string.music_apps))
            SettingsCard {
                LabeledValueRow(
                    title = stringResource(R.string.music_apps),
                    value = settings.allowedPackages.size.toString(),
                )
                Text(
                    text = stringResource(R.string.music_apps_mvp_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = stringResource(R.string.stage_note),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Header() {
    Column(modifier = Modifier.padding(top = 28.dp, bottom = 4.dp, start = 8.dp)) {
        Text(
            text = stringResource(R.string.home_title),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    reason: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    if (granted) R.string.permission_granted else R.string.permission_missing
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        if (!granted) {
            TextButton(onClick = onGrant) { Text(stringResource(R.string.grant)) }
        }
    }
}

/**
 * Static preview of the line as it will look on the panel. It is a plain Canvas
 * drawing, so it costs one frame when a setting changes and nothing afterwards.
 */
@Composable
private fun EdgePreview(colorArgb: Int, thicknessDp: Float, brightness: Float) {
    val color = Color(colorArgb)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val stroke = thicknessDp.dp.toPx()
        val corner = CornerRadius(28.dp.toPx(), 28.dp.toPx())
        val inset = stroke / 2f
        val topLeft = Offset(inset, inset)
        val size = Size(this.size.width - stroke, this.size.height - stroke)

        // Same layered-glow approach as StaticEffect, at preview scale.
        listOf(2.8f to 0.10f, 1.7f to 0.18f, 1f to 1f).forEach { (widthScale, alphaScale) ->
            drawRoundRect(
                color = color.copy(alpha = brightness * alphaScale),
                topLeft = topLeft,
                size = size,
                cornerRadius = corner,
                style = Stroke(width = stroke * widthScale),
            )
        }
    }
}

@Composable
private fun performanceLabel(mode: PerformanceMode): String = stringResource(
    when (mode) {
        PerformanceMode.BATTERY_SAVER -> R.string.performance_battery_saver
        PerformanceMode.BALANCED -> R.string.performance_balanced
        PerformanceMode.ULTRA_SMOOTH -> R.string.performance_ultra_smooth
    }
)

@Composable
private fun statusLabel(
    settings: AppSettings,
    status: VisualizerStatus.Snapshot,
    permissions: PermissionState,
): String {
    val appLabel = status.appLabel
    return when {
        !settings.enabled -> stringResource(R.string.status_disabled)
        !permissions.allGranted -> stringResource(R.string.status_needs_permissions)
        !status.listenerConnected -> stringResource(R.string.status_service_disconnected)
        status.state == VisualizerState.ACTIVE && appLabel != null ->
            stringResource(R.string.status_playing, appLabel)

        status.state == VisualizerState.PAUSED_FADE && appLabel != null ->
            stringResource(R.string.status_fading, appLabel)

        status.state == VisualizerState.SCREEN_OFF -> stringResource(R.string.status_screen_off)
        else -> stringResource(R.string.status_waiting)
    }
}

private fun trackLabel(status: VisualizerStatus.Snapshot): String? {
    val title = status.title ?: return null
    val artist = status.artist
    return if (artist.isNullOrBlank()) title else "$title — $artist"
}
