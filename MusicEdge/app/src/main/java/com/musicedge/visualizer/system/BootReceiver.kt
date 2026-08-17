package com.musicedge.visualizer.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService

/**
 * The engine lives inside a NotificationListenerService, which the system binds on
 * its own after a reboot. This receiver only nudges that binding along after boot
 * or an app update - [NotificationListenerService.requestRebind] is the documented
 * way to do it, and there is no background start or wakelock involved.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (PermissionManager.hasNotificationAccess(context)) {
                    NotificationListenerService.requestRebind(
                        PermissionManager.listenerComponent(context)
                    )
                }
            }
        }
    }
}
