package com.musicedge.visualizer.media

import android.app.Notification
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Fallback discovery path for players whose sessions never show up in
 * [android.media.session.MediaSessionManager.getActiveSessions] - either because the
 * session is not marked active or because the app publishes it late.
 *
 * A media notification carries its session token in
 * [Notification.EXTRA_MEDIA_SESSION]; a controller built from that token exposes the
 * same playback state as a normally discovered session. Nothing else is read from
 * the notification: no title, no text, no icons. The app looks at exactly one extra.
 *
 * Fully event-driven - it reacts to posted and removed notifications, it never polls.
 */
class MediaNotificationObserver(
    private val context: Context,
    private val onControllersChanged: (List<MediaController>) -> Unit,
) {

    private val controllersByKey = LinkedHashMap<String, MediaController>()

    fun onNotificationPosted(notification: StatusBarNotification) {
        val token = tokenOf(notification) ?: return
        val existing = controllersByKey[notification.key]
        if (existing != null && existing.sessionToken == token) return

        val controller = runCatching { MediaController(context, token) }
            .onFailure { Log.w(TAG, "Media notification token rejected", it) }
            .getOrNull() ?: return

        controllersByKey[notification.key] = controller
        publish()
    }

    fun onNotificationRemoved(notification: StatusBarNotification) {
        if (controllersByKey.remove(notification.key) != null) publish()
    }

    /** Seeds the state from the notifications that already exist when we connect. */
    fun reset(active: Array<StatusBarNotification>?) {
        controllersByKey.clear()
        active?.forEach { notification ->
            val token = tokenOf(notification) ?: return@forEach
            runCatching { MediaController(context, token) }.getOrNull()?.let {
                controllersByKey[notification.key] = it
            }
        }
        publish()
    }

    fun clear() {
        if (controllersByKey.isEmpty()) return
        controllersByKey.clear()
        publish()
    }

    private fun publish() = onControllersChanged(controllersByKey.values.toList())

    private fun tokenOf(notification: StatusBarNotification): MediaSession.Token? =
        notification.notification.extras.getParcelable(
            Notification.EXTRA_MEDIA_SESSION,
            MediaSession.Token::class.java,
        )

    private companion object {
        const val TAG = "MediaNotificationObserver"
    }
}
