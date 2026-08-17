package com.musicedge.visualizer.audio

/**
 * A producer of [AudioFrame]s.
 *
 * Planned implementations:
 *  - `VisualizerAudioSource`      android.media.audiofx.Visualizer on session 0
 *                                 (Stage 2; needs verification on One UI, see docs)
 *  - `PlaybackCaptureAudioSource` AudioPlaybackCapture + MediaProjection fallback,
 *                                 only usable for apps that allow capture
 *
 * Implementations deliver frames on their own worker thread; the consumer reads the
 * most recent value, so implementations must publish frames atomically.
 */
interface AudioSource {

    /** True once the source is delivering non-trivial data. */
    val isActive: Boolean

    /**
     * @param updatesPerSecond target analysis rate; implementations should not
     *   analyse faster than this even if the backend can.
     * @return false if the source cannot start (missing permission, unsupported).
     */
    fun start(updatesPerSecond: Int): Boolean

    /** Must release every native resource; safe to call repeatedly. */
    fun stop()

    /** The most recent frame, or [AudioFrame.SILENT] before the first one arrives. */
    fun latestFrame(): AudioFrame
}
