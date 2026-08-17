package com.musicedge.visualizer.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicedge.visualizer.core.VisualizerStatus
import com.musicedge.visualizer.settings.SettingsRepository
import com.musicedge.visualizer.system.PermissionManager
import com.musicedge.visualizer.ui.screens.HomeScreen
import com.musicedge.visualizer.ui.screens.PermissionState
import com.musicedge.visualizer.ui.theme.MusicEdgeTheme

/**
 * The only Activity: settings and permission flow. It never talks to the overlay
 * directly - it writes settings, and the engine inside the listener service reacts.
 */
class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository

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

        HomeScreen(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            settings = settings,
            status = status,
            permissions = permissions,
            onEnabledChange = ::setVisualizerEnabled,
            onColorChange = settingsRepository::setColor,
            onThicknessChange = settingsRepository::setThicknessDp,
            onBrightnessChange = settingsRepository::setBrightness,
            onPerformanceModeChange = settingsRepository::setPerformanceMode,
            onGrantOverlay = { startSettingsIntent(PermissionManager.overlaySettingsIntent(this)) },
            onGrantNotificationAccess = {
                startSettingsIntent(PermissionManager.notificationAccessSettingsIntent())
            },
        )
    }

    private fun readPermissions() = PermissionState(
        overlay = PermissionManager.canDrawOverlay(this),
        notificationAccess = PermissionManager.hasNotificationAccess(this),
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
}
