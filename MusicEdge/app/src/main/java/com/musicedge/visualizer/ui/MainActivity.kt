package com.musicedge.visualizer.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicedge.visualizer.core.VisualizerStatus
import com.musicedge.visualizer.media.MusicAppDetector
import com.musicedge.visualizer.settings.AppSettings
import com.musicedge.visualizer.settings.SettingsRepository
import com.musicedge.visualizer.system.PermissionManager
import com.musicedge.visualizer.ui.screens.AppRowItem
import com.musicedge.visualizer.ui.screens.HomeActions
import com.musicedge.visualizer.ui.screens.HomeScreen
import com.musicedge.visualizer.ui.screens.MusicAppsScreen
import com.musicedge.visualizer.ui.screens.PermissionState
import com.musicedge.visualizer.ui.theme.MusicEdgeTheme
import com.musicedge.visualizer.ui.util.toImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The only Activity: settings, the app picker and the permission flow. It never
 * talks to the overlay directly - it writes settings, and the engine inside the
 * listener service reacts, which is what makes every slider apply in real time.
 */
class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private val musicAppDetector by lazy { MusicAppDetector(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsRepository = SettingsRepository.get(this)

        setContent {
            MusicEdgeTheme {
                MainScreen()
            }
        }
    }

    @Composable
    private fun MainScreen() {
        val settings by settingsRepository.settings.collectAsStateWithLifecycle()
        val status by VisualizerStatus.snapshot.collectAsStateWithLifecycle()

        // Special-access permissions can only change outside the app, so they are
        // re-read every time the screen comes back to the foreground.
        var permissions by remember { mutableStateOf(readPermissions()) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            permissions = readPermissions()
        }

        val audioPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { permissions = readPermissions() }

        var showMusicApps by rememberSaveable { mutableStateOf(false) }
        BackHandler(enabled = showMusicApps) { showMusicApps = false }

        val screenModifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)

        if (showMusicApps) {
            val apps by produceState<List<AppRowItem>?>(initialValue = null) {
                value = withContext(Dispatchers.Default) { loadInstalledApps() }
            }
            MusicAppsScreen(
                apps = apps,
                allowedPackages = settings.allowedPackages,
                onToggle = settingsRepository::setPackageAllowed,
                onBack = { showMusicApps = false },
                modifier = screenModifier,
            )
        } else {
            HomeScreen(
                settings = settings,
                status = status,
                permissions = permissions,
                actions = HomeActions(
                    onEnabledChange = ::setVisualizerEnabled,
                    onGradientColorChange = settingsRepository::setGradientColor,
                    onResetPalette = {
                        settingsRepository.setGradientColors(AppSettings.DEFAULT_GRADIENT_COLORS)
                    },
                    onAnimationSpeedChange = settingsRepository::setAnimationSpeed,
                    onThicknessChange = settingsRepository::setThicknessDp,
                    onGlowChange = settingsRepository::setGlowIntensity,
                    onBrightnessChange = settingsRepository::setBrightness,
                    onEffectChange = settingsRepository::setEffectId,
                    onPerformanceModeChange = settingsRepository::setPerformanceMode,
                    onGrantOverlay = {
                        startSettingsIntent(PermissionManager.overlaySettingsIntent(this))
                    },
                    onGrantNotificationAccess = {
                        startSettingsIntent(PermissionManager.notificationAccessSettingsIntent())
                    },
                    onGrantAudio = {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onOpenMusicApps = { showMusicApps = true },
                ),
                modifier = screenModifier,
            )
        }
    }

    /** Package manager queries and icon rasterisation; never call on the main thread. */
    private fun loadInstalledApps(): List<AppRowItem> {
        val settings = settingsRepository.current
        val iconSizePx = (ICON_SIZE_DP * resources.displayMetrics.density).toInt()
        return musicAppDetector.listSelectableApps(
            discoveredPackages = settings.discoveredPackages,
            allowedPackages = settings.allowedPackages,
        ).map { app ->
            AppRowItem(
                packageName = app.packageName,
                label = app.label,
                icon = app.icon?.toImageBitmap(iconSizePx),
                isKnownMediaApp = app.isKnownMediaApp,
            )
        }
    }

    private fun readPermissions() = PermissionState(
        overlay = PermissionManager.canDrawOverlay(this),
        notificationAccess = PermissionManager.hasNotificationAccess(this),
        audio = PermissionManager.hasAudioPermission(this),
    )

    private fun setVisualizerEnabled(enabled: Boolean) {
        settingsRepository.setEnabled(enabled)
        if (enabled && PermissionManager.hasNotificationAccess(this)) {
            // The system usually keeps the listener bound, but after a process death
            // this is the documented way to ask for it back.
            NotificationListenerService.requestRebind(PermissionManager.listenerComponent(this))
        }
    }

    private fun startSettingsIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, e.localizedMessage ?: "Settings unavailable", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private companion object {
        const val ICON_SIZE_DP = 48
    }
}
