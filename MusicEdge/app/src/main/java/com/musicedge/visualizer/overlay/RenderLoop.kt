package com.musicedge.visualizer.overlay

import android.view.Choreographer

/**
 * Frame pacing built on [Choreographer], i.e. on the display's vsync signal. There
 * is no drawing thread and no busy loop: while [stop] is in effect the app posts no
 * callbacks at all and costs exactly nothing.
 *
 * On a 120 Hz panel the callback still arrives at panel rate; the target frame rate
 * is honoured by skipping callbacks whose time budget has not elapsed, which is far
 * cheaper than driving the whole window at a lower refresh rate (that would also
 * slow down whatever app is underneath).
 *
 * Must be created and used on a thread with a Looper - in practice the main thread.
 */
class RenderLoop(private val onFrame: (deltaSeconds: Float) -> Unit) {

    private val choreographer = Choreographer.getInstance()

    private var running = false
    private var lastFrameNanos = 0L
    private var minFrameIntervalNanos = intervalFor(DEFAULT_FPS)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            choreographer.postFrameCallback(this)

            val elapsed = frameTimeNanos - lastFrameNanos
            if (lastFrameNanos != 0L && elapsed < minFrameIntervalNanos) return

            val deltaSeconds = if (lastFrameNanos == 0L) {
                0f
            } else {
                (elapsed / NANOS_PER_SECOND).toFloat().coerceAtMost(MAX_DELTA_SECONDS)
            }
            lastFrameNanos = frameTimeNanos
            onFrame(deltaSeconds)
        }
    }

    val isRunning: Boolean get() = running

    fun setTargetFps(fps: Int) {
        minFrameIntervalNanos = intervalFor(fps)
    }

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        choreographer.postFrameCallback(frameCallback)
    }

    fun stop() {
        if (!running) return
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    private companion object {
        const val DEFAULT_FPS = 60
        const val NANOS_PER_SECOND = 1_000_000_000.0

        /** Guards animations against a long stall (e.g. right after the screen wakes). */
        const val MAX_DELTA_SECONDS = 0.1f

        /**
         * The threshold sits at 85% of the target frame period. It has to be below
         * one target period (so the right vsync is accepted) and above the previous
         * panel vsync (so the frame before it is rejected): on a 120 Hz panel that
         * yields exactly 30 / 60 / 120 for the three performance modes.
         */
        fun intervalFor(fps: Int): Long =
            if (fps <= 0) 0L else (NANOS_PER_SECOND / fps * 0.85).toLong()
    }
}
