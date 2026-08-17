package com.musicedge.visualizer.system

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.musicedge.visualizer.media.MediaEdgeListenerService

/**
 * The two special-access permissions the app needs, and the system screens that
 * grant them. Neither can be requested with the runtime permission dialog.
 */
object PermissionManager {

    fun canDrawOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasNotificationAccess(context: Context): Boolean {
        val notificationManager =
            requireNotNull(context.getSystemService(NotificationManager::class.java))
        return notificationManager.isNotificationListenerAccessGranted(listenerComponent(context))
    }

    fun overlaySettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.fromParts("package", context.packageName, null),
    )

    fun notificationAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun listenerComponent(context: Context): ComponentName =
        ComponentName(context, MediaEdgeListenerService::class.java)
}
