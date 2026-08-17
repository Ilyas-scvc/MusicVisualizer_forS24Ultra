package com.musicedge.visualizer.audio

import com.musicedge.visualizer.settings.PerformanceMode

/**
 * The single seam between "where audio data comes from" and "what draws it".
 *
 * Stage 1 ships without a source: the MVP effect is static, and starting an
 * AudioEffect session would cost battery for nothing. [source] is assigned in
 * Stage 2 and the renderer needs no change - it already reads [currentFrame].
 */
class AudioEngine {

    private var source: AudioSource? = null
    private var running = false

    val isRunning: Boolean get() = running

    /** True when a source is attached and actually producing data. */
    val hasLiveAudio: Boolean get() = source?.isActive == true

    fun attachSource(source: AudioSource?) {
        if (running) stop()
        this.source = source
    }

    fun start(mode: PerformanceMode) {
        if (running) return
        running = source?.start(mode.audioUpdatesPerSecond) ?: true
    }

    fun stop() {
        if (!running) return
        running = false
        source?.stop()
    }

    fun currentFrame(): AudioFrame = source?.latestFrame() ?: AudioFrame.SILENT
}
