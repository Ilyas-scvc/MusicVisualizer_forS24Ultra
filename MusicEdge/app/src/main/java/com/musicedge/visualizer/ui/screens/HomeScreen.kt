package com.musicedge.visualizer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicedge.visualizer.R
import com.musicedge.visualizer.core.VisualizerState
import com.musicedge.visualizer.core.VisualizerStatus
import com.musicedge.visualizer.effects.BassPulseEffect
import com.musicedge.visualizer.effects.FlowEffect
import com.musicedge.visualizer.settings.AppSettings
import com.musicedge.visualizer.settings.PerformanceMode
import com.musicedge.visualizer.ui.components.CardDivider
import com.musicedge.visualizer.ui.components.ChoiceRow
import com.musicedge.visualizer.ui.components.ColorPickerDialog
import com.musicedge.visualizer.ui.components.GradientEdgePreview
import com.musicedge.visualizer.ui.components.GradientSwatchRow
import com.musicedge.visualizer.ui.components.LabeledValueRow
import com.musicedge.visualizer.ui.components.NavigationRow
import com.musicedge.visualizer.ui.components.OutlinedInfoBox
import com.musicedge.visualizer.ui.components.SectionTitle
import com.musicedge.visualizer.ui.components.SettingsCard
import com.musicedge.visualizer.ui.components.SliderRow
import com.musicedge.visualizer.ui.components.ToggleRow

/** Which permissions are currently granted. */
data class PermissionState(
    val overlay: Boolean,
    val notificationAccess: Boolean,
    val audio: Boolean,
) {
    /** Audio is optional: it is only needed by effects that react to sound. */
    val essentialsGranted: Boolean get() = overlay && notificationAccess
}

/** Every settings change the home screen can produce. */
data class HomeActions(
    val onEnabledChange: (Boolean) -> Unit,
    val onGradientColorChange: (index: Int, color: Int) -> Unit,
    val onResetPalette: () -> Unit,
    val onAnimationSpeedChange: (Float) -> Unit,
    val onThicknessChange: (Float) -> Unit,
    val onGlowChange: (Float) -> Unit,
    val onBrightnessChange: (Float) -> Unit,
    val onEffectChange: (String) -> Unit,
    val onPerformanceModeChange: (PerformanceMode) -> Unit,
    val onGrantOverlay: () -> Unit,
    val onGrantNotificationAccess: () -> Unit,
    val onGrantAudio: () -> Unit,
    val onOpenMusicApps: () -> Unit,
)

@Composable
fun HomeScreen(
    settings: AppSettings,
    status: VisualizerStatus.Snapshot,
    permissions: PermissionState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    var editingColorIndex by remember { mutableStateOf<Int?>(null) }

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
                    onCheckedChange = actions.onEnabledChange,
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
                GradientEdgePreview(
                    colors = settings.gradientColors,
                    thicknessDp = settings.thicknessDp,
                    brightness = settings.brightness,
                    glowIntensity = settings.glowIntensity,
                    animationSpeed = settings.animationSpeed,
                )
            }
        }

        if (!permissions.essentialsGranted) {
            Column {
                SectionTitle(stringResource(R.string.permissions))
                SettingsCard {
                    PermissionRow(
                        title = stringResource(R.string.permission_overlay_title),
                        reason = stringResource(R.string.permission_overlay_reason),
                        granted = permissions.overlay,
                        onGrant = actions.onGrantOverlay,
                    )
                    CardDivider()
                    PermissionRow(
                        title = stringResource(R.string.permission_notifications_title),
                        reason = stringResource(R.string.permission_notifications_reason),
                        granted = permissions.notificationAccess,
                        onGrant = actions.onGrantNotificationAccess,
                    )
                }
            }
        }

        Column {
            SectionTitle(stringResource(R.string.gradient_colors))
            SettingsCard {
                Text(
                    text = stringResource(R.string.gradient_colors_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GradientSwatchRow(
                    colors = settings.gradientColors,
                    onSlotClick = { index -> editingColorIndex = index },
                )
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = actions.onResetPalette) {
                        Text(stringResource(R.string.reset_palette))
                    }
                }
            }
        }

        Column {
            SectionTitle(stringResource(R.string.effect))
            SettingsCard {
                ChoiceRow(
                    title = stringResource(R.string.effect_flow),
                    subtitle = stringResource(R.string.effect_flow_summary),
                    selected = settings.effectId == FlowEffect.ID,
                    onClick = { actions.onEffectChange(FlowEffect.ID) },
                )
                ChoiceRow(
                    title = stringResource(R.string.effect_bass_pulse),
                    subtitle = stringResource(R.string.effect_bass_pulse_summary),
                    selected = settings.effectId == BassPulseEffect.ID,
                    onClick = { actions.onEffectChange(BassPulseEffect.ID) },
                )
                if (settings.effectId == BassPulseEffect.ID) {
                    if (!permissions.audio) {
                        OutlinedInfoBox(stringResource(R.string.effect_audio_hint))
                        Row {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = actions.onGrantAudio) {
                                Text(stringResource(R.string.grant))
                            }
                        }
                    } else if (status.state == VisualizerState.ACTIVE && !status.audioAvailable) {
                        OutlinedInfoBox(stringResource(R.string.effect_audio_silent))
                    }
                }
            }
        }

        Column {
            SectionTitle(stringResource(R.string.preview))
            SettingsCard {
                SliderRow(
                    title = stringResource(R.string.animation_speed),
                    valueLabel = stringResource(R.string.speed_value, settings.animationSpeed),
                    value = settings.animationSpeed,
                    valueRange = AppSettings.MIN_ANIMATION_SPEED..AppSettings.MAX_ANIMATION_SPEED,
                    onValueChange = actions.onAnimationSpeedChange,
                    startLabel = stringResource(R.string.slow),
                    endLabel = stringResource(R.string.fast),
                )
                SliderRow(
                    title = stringResource(R.string.thickness),
                    valueLabel = stringResource(R.string.thickness_value, settings.thicknessDp),
                    value = settings.thicknessDp,
                    valueRange = AppSettings.MIN_THICKNESS_DP..AppSettings.MAX_THICKNESS_DP,
                    onValueChange = actions.onThicknessChange,
                )
                SliderRow(
                    title = stringResource(R.string.glow),
                    valueLabel = stringResource(
                        R.string.percent_value,
                        (settings.glowIntensity * 100).toInt(),
                    ),
                    value = settings.glowIntensity,
                    valueRange = AppSettings.MIN_GLOW_INTENSITY..AppSettings.MAX_GLOW_INTENSITY,
                    onValueChange = actions.onGlowChange,
                    startLabel = stringResource(R.string.glow_off),
                    endLabel = stringResource(R.string.glow_max),
                )
                SliderRow(
                    title = stringResource(R.string.brightness),
                    valueLabel = stringResource(
                        R.string.percent_value,
                        (settings.brightness * 100).toInt(),
                    ),
                    value = settings.brightness,
                    valueRange = AppSettings.MIN_BRIGHTNESS..AppSettings.MAX_BRIGHTNESS,
                    onValueChange = actions.onBrightnessChange,
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
                        onClick = { actions.onPerformanceModeChange(mode) },
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
                NavigationRow(
                    title = stringResource(R.string.music_apps),
                    subtitle = stringResource(
                        R.string.music_apps_summary,
                        settings.allowedPackages.size,
                    ),
                    onClick = actions.onOpenMusicApps,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    val editedIndex = editingColorIndex
    if (editedIndex != null) {
        ColorPickerDialog(
            initialColor = settings.gradientColors[editedIndex],
            onDismiss = { editingColorIndex = null },
            onColorSelected = { color ->
                actions.onGradientColorChange(editedIndex, color)
                editingColorIndex = null
            },
        )
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
        !permissions.essentialsGranted -> stringResource(R.string.status_needs_permissions)
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
