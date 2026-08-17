package com.musicedge.visualizer.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.musicedge.visualizer.core.VisualizerController

/**
 * Host process for the whole engine.
 *
 * Why a NotificationListenerService and not a foreground service:
 *  - notification access is required anyway, because it is the only way a
 *    third-party app may call [android.media.session.MediaSessionManager.getActiveSessions];
 *  - the system binds this service as long as access is granted and rebinds it
 *    after a reboot, so the app survives without a permanent notification, without
 *    a wakelock and without any background-start workaround;
 *  - when the user revokes access the system unbinds us, which is exactly when the
 *    engine should stop.
 *
 * Privacy: [onNotificationPosted] and [onNotificationRemoved] are deliberately not
 * overridden. The app never reads notification content - the permission is used
 * purely as the token for media session access.
 */
class MediaEdgeListenerService : NotificationListenerService() {

    private var controller: VisualizerController? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        val controller = controller ?: VisualizerController(this).also { this.controller = it }
        controller.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        controller?.onListenerDisconnected()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        controller?.destroy()
        controller = null
        super.onDestroy()
    }

    // Explicitly ignored: see the class comment.
    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit
}
