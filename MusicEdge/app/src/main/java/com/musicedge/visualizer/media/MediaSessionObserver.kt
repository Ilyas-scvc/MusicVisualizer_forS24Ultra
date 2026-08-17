package com.musicedge.visualizer.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log

/**
 * Watches the system's active media sessions and reports the one that should drive
 * the visualizer.
 *
 * API notes:
 *  - [MediaSessionManager.getActiveSessions] and the active-session listener are
 *    only callable by an app whose [android.service.notification.NotificationListenerService]
 *    has been enabled by the user; that component is passed in as [listenerComponent].
 *  - Session ownership is the only reliable source of "which app is playing".
 *    The foreground app is irrelevant: Spotify playing behind Telegram still owns
 *    the session.
 *
 * All methods must be called on the main thread; the listener overloads used here
 * deliver callbacks on the caller's Looper.
 */
class MediaSessionObserver(
    context: Context,
    private val listenerComponent: ComponentName,
    private val isAllowed: (String) -> Boolean,
    private val onChanged: (PlaybackSnapshot) -> Unit,
) {

    private val sessionManager: MediaSessionManager? =
        context.getSystemService(MediaSessionManager::class.java)
    private val detector = MusicAppDetector(context)

    private val trackedControllers = mutableListOf<MediaController>()
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()

    private var started = false
    private var lastKnownPackage: String? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            trackControllers(controllers ?: emptyList())
            publish()
        }

    fun start() {
        if (started) return
        val manager = sessionManager ?: return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            trackControllers(manager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            // Notification access was revoked between the permission check and here.
            Log.w(TAG, "No notification access for media sessions", e)
            return
        }
        started = true
        publish()
    }

    fun stop() {
        if (!started) return
        started = false
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        trackControllers(emptyList())
        lastKnownPackage = null
    }

    /** Re-evaluates the current sessions, e.g. after the whitelist changed. */
    fun refresh() {
        if (started) publish()
    }

    private fun trackControllers(controllers: List<MediaController>) {
        trackedControllers.forEach { controller ->
            controllerCallbacks.remove(controller)?.let(controller::unregisterCallback)
        }
        trackedControllers.clear()

        controllers.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                override fun onSessionDestroyed() {
                    controllerCallbacks.remove(controller)?.let(controller::unregisterCallback)
                    trackedControllers.remove(controller)
                    publish()
                }
            }
            controller.registerCallback(callback)
            controllerCallbacks[controller] = callback
            trackedControllers += controller
        }
    }

    private fun publish() {
        onChanged(currentSnapshot())
    }

    private fun currentSnapshot(): PlaybackSnapshot {
        val playing = trackedControllers.filter { it.playbackState.isPlaying() }
        // Prefer a whitelisted app: a non-whitelisted session (a video, a game)
        // must not mask music that is playing at the same time.
        val selected = playing.firstOrNull { isAllowed(it.packageName) }
            ?: playing.firstOrNull()

        if (selected != null) {
            lastKnownPackage = selected.packageName
            return snapshotOf(selected, isPlaying = true)
        }

        val lastSession = lastKnownPackage?.let { pkg ->
            trackedControllers.firstOrNull { it.packageName == pkg }
        }
        return when {
            lastSession != null -> snapshotOf(lastSession, isPlaying = false)
            lastKnownPackage != null -> PlaybackSnapshot(
                packageName = lastKnownPackage,
                appLabel = lastKnownPackage?.let(detector::labelOf),
                isPlaying = false,
                title = null,
                artist = null,
            )
            else -> PlaybackSnapshot.NONE
        }
    }

    private fun snapshotOf(controller: MediaController, isPlaying: Boolean): PlaybackSnapshot {
        val metadata = controller.metadata
        return PlaybackSnapshot(
            packageName = controller.packageName,
            appLabel = detector.labelOf(controller.packageName),
            isPlaying = isPlaying,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
        )
    }

    private fun PlaybackState?.isPlaying(): Boolean = this?.state == PlaybackState.STATE_PLAYING

    private companion object {
        const val TAG = "MediaSessionObserver"
    }
}
