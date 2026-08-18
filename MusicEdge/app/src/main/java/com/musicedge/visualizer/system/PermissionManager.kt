package com.musicedge.visualizer.system

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.musicedge.visualizer.media.MediaEdgeListenerService

/**
 * The permissions the app needs and the screens that grant them.
 *
 * Overlay access and notification access are special-access settings that cannot be
 * requested with the runtime dialog; RECORD_AUDIO is an ordinary runtime permission
 * and is only needed for the audio analysis behind Bass Pulse.
 */
object PermissionManager {

    fun canDrawOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasNotificationAccess(context: Context): Boolean {
        val notificationManager =
            requireNotNull(context.getSystemService(NotificationManager::class.java))
        return notificationManager.isNotificationListenerAccessGranted(listenerComponent(context))
    }

    fun hasAudioPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun overlaySettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.fromParts("package", context.packageName, null),
    )

    fun notificationAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun listenerComponent(context: Context): ComponentName =
        ComponentName(context, MediaEdgeListenerService::class.java)
}
