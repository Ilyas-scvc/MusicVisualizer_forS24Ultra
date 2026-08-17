package com.musicedge.visualizer.audio

/**
 * One analysis result, normalised to 0..1. Effects consume this and nothing else,
 * so the audio backend can be replaced without touching the rendering code.
 *
 * Values are transient by design: nothing here is written to disk, and no raw audio
 * ever leaves the analysis step.
 */
data class AudioFrame(
    val amplitude: Float,
    val bass: Float,
    val mid: Float,
    val treble: Float,
) {
    companion object {
        val SILENT = AudioFrame(amplitude = 0f, bass = 0f, mid = 0f, treble = 0f)
    }
}
