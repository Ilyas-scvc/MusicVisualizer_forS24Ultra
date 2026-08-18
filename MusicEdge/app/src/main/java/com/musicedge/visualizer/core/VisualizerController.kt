package com.musicedge.visualizer.core

import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import com.musicedge.visualizer.audio.AudioEngine
import com.musicedge.visualizer.audio.VisualizerAudioSource
import com.musicedge.visualizer.effects.EffectRegistry
import com.musicedge.visualizer.media.PlaybackDetector
import com.musicedge.visualizer.media.PlaybackSnapshot
import com.musicedge.visualizer.overlay.EdgeStyle
import com.musicedge.visualizer.overlay.OverlayManager
import com.musicedge.visualizer.settings.AppSettings
import com.musicedge.visualizer.settings.SettingsRepository
import com.musicedge.visualizer.system.PermissionManager
import com.musicedge.visualizer.system.ScreenStateObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The state machine that ties playback detection, screen state, settings, audio and
 * the overlay together.
 *
 * Everything runs on the main thread: the inputs (media controller callbacks,
 * notification events, screen broadcasts, the settings flow) are all delivered
 * there, and the only consumer is the view hierarchy, which is main-thread bound
 * anyway. That removes the need for locking entirely; the class is not safe to touch
 * from another thread.
 *
 * Lifetime is owned by [com.musicedge.visualizer.media.MediaEdgeListenerService].
 */
class VisualizerController(private val context: Context) {

    private val settingsRepository = SettingsRepository.get(context)
    private val audioEngine = AudioEngine()
    private val overlayManager = OverlayManager(context, audioEngine)
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val screenObserver = ScreenStateObserver(context) { interactive ->
        screenInteractive = interactive
        evaluate()
    }

    private val playbackDetector = PlaybackDetector(
        context = context,
        listenerComponent = PermissionManager.listenerComponent(context),
        isAllowed = { packageName -> packageName in settingsRepository.current.allowedPackages },
        onPlaybackChanged = { snapshot ->
            playback = snapshot
            evaluate()
        },
        onPackageDiscovered = settingsRepository::rememberDiscoveredPackage,
    )

    private var state = VisualizerState.DISABLED
    private var playback = PlaybackSnapshot.NONE
    private var screenInteractive = true
    private var listenerConnected = false
    private var detectorStarted = false
    private var appliedEffectId: String? = null
    private var audioSourceAttached = false
    private var evaluating = false
    private var reevaluateRequested = false

    init {
        audioEngine.onAvailabilityChanged = { available ->
            VisualizerStatus.update { it.copy(audioAvailable = available) }
        }
    }

    fun onListenerConnected() {
        if (listenerConnected) return
        listenerConnected = true

        screenObserver.start()
        screenInteractive = screenObserver.isInteractive

        scope.launch {
            settingsRepository.settings.collectLatest { settings ->
                applyLiveSettings(settings)
                evaluate()
            }
        }
        evaluate()
    }

    fun onListenerDisconnected() {
        if (!listenerConnected) return
        listenerConnected = false
        stopDetector()
        screenObserver.stop()
        teardownOverlay()
        state = VisualizerState.IDLE
        publishStatus()
    }

    /**
     * Supplies the notifications that already exist when detection starts. Set by
     * the listener service, because only a NotificationListenerService can read them.
     */
    var activeNotificationsProvider: (() -> Array<StatusBarNotification>?)? = null

    /** Media notifications are the fallback discovery path; see [PlaybackDetector]. */
    fun onNotificationPosted(notification: StatusBarNotification) =
        playbackDetector.onNotificationPosted(notification)

    fun onNotificationRemoved(notification: StatusBarNotification) =
        playbackDetector.onNotificationRemoved(notification)

    fun destroy() {
        onListenerDisconnected()
        activeNotificationsProvider = null
        scope.cancel()
        VisualizerStatus.reset()
    }

    /**
     * Recomputes the target state from every input and performs the transition.
     *
     * Transitions can synchronously produce new input - starting the detector
     * immediately reports the current session, for example - so the call is
     * re-entrancy safe: a nested request re-runs the pass instead of interleaving
     * with it.
     */
    private fun evaluate() {
        if (evaluating) {
            reevaluateRequested = true
            return
        }
        evaluating = true
        try {
            do {
                reevaluateRequested = false
                evaluateOnce()
            } while (reevaluateRequested)
        } finally {
            evaluating = false
        }
    }

    private fun evaluateOnce() {
        val settings = settingsRepository.current
        val target = resolveTargetState(settings)
        if (target == state) {
            publishStatus()
            return
        }

        Log.d(TAG, "state $state -> $target")
        val previous = state
        state = target

        when (target) {
            VisualizerState.DISABLED, VisualizerState.IDLE -> {
                stopDetector()
                teardownOverlay()
            }

            VisualizerState.WAITING_FOR_MEDIA -> {
                // Reached either from a fade that finished or from a cold start;
                // in both cases nothing should be on screen.
                if (previous != VisualizerState.PAUSED_FADE) teardownOverlay()
                startDetector()
            }

            VisualizerState.ACTIVE -> {
                startDetector()
                activate(settings)
            }

            VisualizerState.PAUSED_FADE -> beginFadeOut()

            VisualizerState.SCREEN_OFF -> {
                // Nothing is visible, so tear down immediately instead of fading.
                teardownOverlay()
            }
        }
        publishStatus()
    }

    private fun resolveTargetState(settings: AppSettings): VisualizerState = when {
        !settings.enabled -> VisualizerState.DISABLED
        !listenerConnected || !PermissionManager.canDrawOverlay(context) -> VisualizerState.IDLE
        !screenInteractive -> VisualizerState.SCREEN_OFF
        isWhitelistedPlayback(settings) -> VisualizerState.ACTIVE
        state == VisualizerState.ACTIVE -> VisualizerState.PAUSED_FADE
        state == VisualizerState.PAUSED_FADE -> VisualizerState.PAUSED_FADE
        else -> VisualizerState.WAITING_FOR_MEDIA
    }

    private fun isWhitelistedPlayback(settings: AppSettings): Boolean {
        val packageName = playback.packageName ?: return false
        return playback.isPlaying && packageName in settings.allowedPackages
    }

    private fun activate(settings: AppSettings) {
        val style = buildStyle(settings)
        if (!overlayManager.isShowing) {
            overlayManager.show(
                style = style,
                effect = EffectRegistry.create(settings.effectId),
                targetFps = settings.performanceMode.targetFps,
            )
            appliedEffectId = settings.effectId
        } else {
            overlayManager.updateStyle(style)
        }
        startAudio(settings)
        overlayManager.fadeIn(AppSettings.FADE_IN_MILLIS)
    }

    /**
     * Audio capture only runs for effects that need it, and only while the overlay is
     * actually on screen. Without the RECORD_AUDIO permission the source refuses to
     * start and the effect simply renders its idle look.
     */
    private fun startAudio(settings: AppSettings) {
        if (!EffectRegistry.requiresAudio(settings.effectId)) {
            stopAudio()
            return
        }
        if (!audioSourceAttached) {
            audioEngine.attachSource(VisualizerAudioSource(context))
            audioSourceAttached = true
        }
        audioEngine.start(settings.performanceMode)
    }

    private fun stopAudio() {
        audioEngine.stop()
        if (audioSourceAttached) {
            audioEngine.attachSource(null)
            audioSourceAttached = false
        }
    }

    private fun beginFadeOut() {
        overlayManager.fadeOut(AppSettings.FADE_OUT_MILLIS) {
            // The fade may have been superseded by playback resuming.
            if (state != VisualizerState.PAUSED_FADE) return@fadeOut
            teardownOverlay()
            state = VisualizerState.WAITING_FOR_MEDIA
            publishStatus()
        }
    }

    private fun teardownOverlay() {
        stopAudio()
        overlayManager.hide()
        appliedEffectId = null
    }

    /** Applies settings that can take effect without a state transition. */
    private fun applyLiveSettings(settings: AppSettings) {
        // The whitelist may have changed while an app was already playing.
        if (detectorStarted) playbackDetector.refresh()

        if (!overlayManager.isShowing) return
        overlayManager.updateStyle(buildStyle(settings))
        overlayManager.updateTargetFps(settings.performanceMode.targetFps)
        if (settings.effectId != appliedEffectId) {
            overlayManager.updateEffect(EffectRegistry.create(settings.effectId))
            appliedEffectId = settings.effectId
            startAudio(settings)
        }
    }

    private fun buildStyle(settings: AppSettings) = EdgeStyle(
        gradientColors = settings.gradientColors,
        thicknessPx = settings.thicknessDp * context.resources.displayMetrics.density,
        brightness = settings.brightness,
        glowIntensity = settings.glowIntensity,
        animationSpeed = settings.animationSpeed,
    )

    private fun startDetector() {
        if (detectorStarted) return
        playbackDetector.start()
        detectorStarted = true
        // Music may already be playing when detection starts - for example when the
        // user flips the switch mid-track - so the fallback needs the notifications
        // that are already on screen, not just the ones posted from now on.
        playbackDetector.seedNotifications(activeNotificationsProvider?.invoke())
    }

    private fun stopDetector() {
        if (!detectorStarted) return
        playbackDetector.stop()
        detectorStarted = false
        playback = PlaybackSnapshot.NONE
    }

    private fun publishStatus() {
        VisualizerStatus.update {
            it.copy(
                state = state,
                listenerConnected = listenerConnected,
                appLabel = playback.appLabel,
                title = playback.title,
                artist = playback.artist,
            )
        }
    }

    private companion object {
        const val TAG = "VisualizerController"
    }
}
