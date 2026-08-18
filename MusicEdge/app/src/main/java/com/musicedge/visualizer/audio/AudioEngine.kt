package com.musicedge.visualizer.audio

import com.musicedge.visualizer.settings.PerformanceMode
import kotlin.math.exp

/**
 * The seam between "where audio data comes from" and "what draws it".
 *
 * The backend is asked for data at the performance mode's analysis rate (20-30 Hz),
 * never per rendered frame. [sample] is called once per frame and interpolates
 * towards the newest analysis result, which is what keeps a 60 or 120 FPS animation
 * smooth without running an FFT 120 times a second.
 */
class AudioEngine {

    private var source: AudioSource? = null
    private var running = false
    private var smoothed = AudioFrame.SILENT
    private var lastReportedAvailability = false

    /** Called when the backend starts or stops delivering real signal. */
    var onAvailabilityChanged: ((available: Boolean) -> Unit)? = null

    val isRunning: Boolean get() = running

    /** True only when a source is attached and actually producing data. */
    val hasLiveAudio: Boolean get() = source?.isActive == true

    fun attachSource(source: AudioSource?) {
        if (running) stop()
        this.source = source
    }

    fun start(mode: PerformanceMode) {
        if (running) return
        running = source?.start(mode.audioUpdatesPerSecond) ?: false
        if (!running) notifyAvailability(false)
    }

    fun stop() {
        if (!running) return
        running = false
        source?.stop()
        smoothed = AudioFrame.SILENT
        notifyAvailability(false)
    }

    /** Advances the interpolated frame; call once per rendered frame. */
    fun sample(deltaSeconds: Float): AudioFrame {
        val target = source?.latestFrame() ?: AudioFrame.SILENT
        smoothed = if (deltaSeconds <= 0f) {
            target
        } else {
            val coefficient = 1f - exp(-deltaSeconds / SMOOTHING_SECONDS)
            AudioFrame(
                amplitude = approach(smoothed.amplitude, target.amplitude, coefficient),
                bass = approach(smoothed.bass, target.bass, coefficient),
                mid = approach(smoothed.mid, target.mid, coefficient),
                treble = approach(smoothed.treble, target.treble, coefficient),
            )
        }
        notifyAvailability(hasLiveAudio)
        return smoothed
    }

    fun currentFrame(): AudioFrame = smoothed

    private fun approach(current: Float, target: Float, coefficient: Float): Float =
        current + (target - current) * coefficient

    private fun notifyAvailability(available: Boolean) {
        if (available == lastReportedAvailability) return
        lastReportedAvailability = available
        onAvailabilityChanged?.invoke(available)
    }

    private companion object {
        /** Interpolation time constant between analysis updates. */
        const val SMOOTHING_SECONDS = 0.03f
    }
}
