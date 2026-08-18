package com.musicedge.visualizer.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.service.notification.StatusBarNotification

/**
 * Decides which app is actually playing.
 *
 *     PlaybackDetector
 *         |- MediaSessionObserver      sessions the system reports as active
 *         |- MediaNotificationObserver sessions found on media notifications
 *
 * Both paths only produce candidate controllers; the playback state always comes
 * from [MediaController], never from what is on screen. Merging is by session token,
 * so a player discovered twice is tracked once.
 *
 * The fallback matters for players that publish their session in a non-standard way:
 * they still post a media notification with a session token, and that is enough.
 *
 * Main thread only.
 */
class PlaybackDetector(
    context: Context,
    listenerComponent: ComponentName,
    private val isAllowed: (String) -> Boolean,
    private val onPlaybackChanged: (PlaybackSnapshot) -> Unit,
    private val onPackageDiscovered: (String) -> Unit,
) {

    private class Tracked(val controller: MediaController, val callback: MediaController.Callback)

    private val appDetector = MusicAppDetector(context)
    private val tracked = LinkedHashMap<MediaSession.Token, Tracked>()

    private var sessionControllers: List<MediaController> = emptyList()
    private var notificationControllers: List<MediaController> = emptyList()
    private var lastKnownPackage: String? = null
    private var started = false

    private val sessionObserver = MediaSessionObserver(context, listenerComponent) { controllers ->
        sessionControllers = controllers
        onSourcesChanged()
    }

    private val notificationObserver = MediaNotificationObserver(context) { controllers ->
        notificationControllers = controllers
        onSourcesChanged()
    }

    fun start() {
        if (started) return
        started = true
        sessionObserver.start()
    }

    fun stop() {
        if (!started) return
        started = false
        sessionObserver.stop()
        notificationObserver.clear()
        sessionControllers = emptyList()
        notificationControllers = emptyList()
        onSourcesChanged()
        lastKnownPackage = null
    }

    /** Re-evaluates the current state, e.g. after the whitelist changed. */
    fun refresh() {
        if (!started) return
        sessionObserver.refresh()
        publish()
    }

    fun onNotificationPosted(notification: StatusBarNotification) {
        if (!started) return
        notificationObserver.onNotificationPosted(notification)
    }

    fun onNotificationRemoved(notification: StatusBarNotification) {
        if (!started) return
        notificationObserver.onNotificationRemoved(notification)
    }

    /** Seeds the fallback with notifications that already exist when we connect. */
    fun seedNotifications(active: Array<StatusBarNotification>?) {
        if (!started) return
        notificationObserver.reset(active)
    }

    private fun onSourcesChanged() {
        val merged = LinkedHashMap<MediaSession.Token, MediaController>()
        sessionControllers.forEach { merged.putIfAbsent(it.sessionToken, it) }
        notificationControllers.forEach { merged.putIfAbsent(it.sessionToken, it) }

        tracked.keys.toList()
            .filterNot { it in merged }
            .forEach { token ->
                tracked.remove(token)?.let { it.controller.unregisterCallback(it.callback) }
            }

        merged.forEach { (token, controller) ->
            if (tracked.containsKey(token)) return@forEach
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                override fun onSessionDestroyed() {
                    tracked.remove(token)?.let { it.controller.unregisterCallback(it.callback) }
                    publish()
                }
            }
            controller.registerCallback(callback)
            tracked[token] = Tracked(controller, callback)
            onPackageDiscovered(controller.packageName)
        }

        publish()
    }

    private fun publish() = onPlaybackChanged(currentSnapshot())

    private fun currentSnapshot(): PlaybackSnapshot {
        val controllers = tracked.values.map { it.controller }
        val playing = controllers.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }

        // Prefer an allowed app: a non-whitelisted session (a video, a game) must not
        // mask music that is playing at the same time.
        val selected = playing.firstOrNull { isAllowed(it.packageName) } ?: playing.firstOrNull()
        if (selected != null) {
            lastKnownPackage = selected.packageName
            return snapshotOf(selected, isPlaying = true)
        }

        val lastSession = lastKnownPackage?.let { pkg ->
            controllers.firstOrNull { it.packageName == pkg }
        }
        return when {
            lastSession != null -> snapshotOf(lastSession, isPlaying = false)
            lastKnownPackage != null -> PlaybackSnapshot(
                packageName = lastKnownPackage,
                appLabel = lastKnownPackage?.let(appDetector::labelOf),
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
            appLabel = appDetector.labelOf(controller.packageName),
            isPlaying = isPlaying,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
        )
    }
}
