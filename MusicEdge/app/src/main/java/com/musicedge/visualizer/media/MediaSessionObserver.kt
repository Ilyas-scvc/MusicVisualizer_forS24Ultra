package com.musicedge.visualizer.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.util.Log

/**
 * Primary discovery path: the sessions the system itself considers active.
 *
 * [MediaSessionManager.getActiveSessions] and the active-session listener are only
 * callable by an app whose NotificationListenerService has been enabled by the user,
 * which is what [listenerComponent] proves.
 *
 * This class only answers "which controllers exist"; playback state is evaluated by
 * [PlaybackDetector], which also merges in the notification fallback.
 *
 * Main thread only: the two-argument listener overload delivers callbacks on the
 * caller's Looper.
 */
class MediaSessionObserver(
    private val context: Context,
    private val listenerComponent: ComponentName,
    private val onControllersChanged: (List<MediaController>) -> Unit,
) {

    private val sessionManager: MediaSessionManager? =
        context.getSystemService(MediaSessionManager::class.java)

    private var started = false

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            onControllersChanged(controllers ?: emptyList())
        }

    fun start() {
        if (started) return
        val manager = sessionManager ?: return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            started = true
            onControllersChanged(manager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            // Notification access was revoked between the permission check and here.
            Log.w(TAG, "No notification access for media sessions", e)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        onControllersChanged(emptyList())
    }

    /** Re-reads the active sessions, e.g. after the whitelist changed. */
    fun refresh() {
        if (!started) return
        val manager = sessionManager ?: return
        try {
            onControllersChanged(manager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            Log.w(TAG, "Lost notification access", e)
        }
    }

    private companion object {
        const val TAG = "MediaSessionObserver"
    }
}
