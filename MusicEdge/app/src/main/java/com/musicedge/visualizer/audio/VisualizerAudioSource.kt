package com.musicedge.visualizer.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Audio analysis through [android.media.audiofx.Visualizer] attached to session 0,
 * the global output mix.
 *
 * Documented behaviour this relies on:
 *  - session 0 means "output mix"; the platform requires RECORD_AUDIO for it, which
 *    is why [start] returns false instead of throwing when the permission is missing;
 *  - [Visualizer.getCaptureSizeRange] and [Visualizer.getMaxCaptureRate] bound the
 *    capture size and the callback rate (the rate is in milliHertz);
 *  - the FFT byte array is packed as: [0] = DC real, [1] = Nyquist real, then
 *    (real, imaginary) pairs per bin;
 *  - the samplingRate reported to the callbacks is also in milliHertz.
 *
 * Not guaranteed, and the reason [isActive] exists: since Android 10 the platform
 * restricts third-party access to other apps' audio. On some devices and firmware
 * the object is created successfully but only ever delivers silence. This class does
 * not pretend otherwise - it reports whether real signal has been seen, and the UI
 * says so instead of animating on invented data.
 *
 * Callbacks arrive on the thread that created the Visualizer (the main thread here);
 * the published frame is still volatile because the renderer reads it per frame.
 */
class VisualizerAudioSource(private val context: Context) : AudioSource {

    @Volatile
    private var latest = AudioFrame.SILENT

    @Volatile
    private var signalSeen = false

    private var visualizer: Visualizer? = null

    private val bassNormalizer = BandNormalizer()
    private val midNormalizer = BandNormalizer()
    private val trebleNormalizer = BandNormalizer()

    override val isActive: Boolean get() = visualizer != null && signalSeen

    private val captureListener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(
            visualizer: Visualizer?,
            waveform: ByteArray?,
            samplingRate: Int,
        ) {
            if (waveform == null || waveform.isEmpty()) return
            latest = latest.copy(amplitude = amplitudeOf(waveform))
            if (latest.amplitude > SIGNAL_THRESHOLD) signalSeen = true
        }

        override fun onFftDataCapture(
            visualizer: Visualizer?,
            fft: ByteArray?,
            samplingRate: Int,
        ) {
            if (fft == null || fft.size < 4) return
            val sampleRateHz = samplingRate / 1000f
            if (sampleRateHz <= 0f) return
            val binWidthHz = sampleRateHz / fft.size

            val bass = bandMagnitude(fft, binWidthHz, BASS_LOW_HZ, BASS_HIGH_HZ)
            val mid = bandMagnitude(fft, binWidthHz, MID_LOW_HZ, MID_HIGH_HZ)
            val treble = bandMagnitude(fft, binWidthHz, TREBLE_LOW_HZ, TREBLE_HIGH_HZ)
            if (max(bass, max(mid, treble)) > RAW_SIGNAL_THRESHOLD) signalSeen = true

            latest = latest.copy(
                bass = bassNormalizer.normalize(bass),
                mid = midNormalizer.normalize(mid),
                treble = trebleNormalizer.normalize(treble),
            )
        }
    }

    override fun start(updatesPerSecond: Int): Boolean {
        if (visualizer != null) return true
        if (!hasRecordAudioPermission()) {
            Log.i(TAG, "RECORD_AUDIO not granted, audio analysis stays off")
            return false
        }
        return try {
            val created = Visualizer(GLOBAL_OUTPUT_MIX_SESSION)
            val sizeRange = Visualizer.getCaptureSizeRange()
            created.setCaptureSize(PREFERRED_CAPTURE_SIZE.coerceIn(sizeRange[0], sizeRange[1]))
            val rateMilliHz = (updatesPerSecond * 1000).coerceIn(1, Visualizer.getMaxCaptureRate())
            created.setDataCaptureListener(captureListener, rateMilliHz, true, true)
            created.setEnabled(true)
            visualizer = created
            true
        } catch (e: RuntimeException) {
            // UnsupportedOperationException / IllegalStateException / RuntimeException
            // are all documented outcomes when the effect cannot be attached.
            Log.w(TAG, "Visualizer on the output mix is unavailable", e)
            releaseQuietly()
            false
        }
    }

    override fun stop() {
        releaseQuietly()
        latest = AudioFrame.SILENT
        signalSeen = false
        bassNormalizer.reset()
        midNormalizer.reset()
        trebleNormalizer.reset()
    }

    override fun latestFrame(): AudioFrame = latest

    private fun releaseQuietly() {
        val current = visualizer ?: return
        visualizer = null
        runCatching {
            current.setEnabled(false)
            current.setDataCaptureListener(null, 0, false, false)
        }
        runCatching { current.release() }
    }

    private fun hasRecordAudioPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** RMS of the 8-bit unsigned PCM waveform, centred at 128. */
    private fun amplitudeOf(waveform: ByteArray): Float {
        var sumOfSquares = 0f
        for (sample in waveform) {
            val centred = ((sample.toInt() and 0xFF) - 128) / 128f
            sumOfSquares += centred * centred
        }
        return sqrt(sumOfSquares / waveform.size).coerceIn(0f, 1f)
    }

    /** Mean magnitude of the FFT bins that fall inside the given frequency band. */
    private fun bandMagnitude(fft: ByteArray, binWidthHz: Float, lowHz: Float, highHz: Float): Float {
        val firstBin = max(1, (lowHz / binWidthHz).toInt())
        val lastBin = (highHz / binWidthHz).toInt().coerceAtMost(fft.size / 2 - 1)
        if (lastBin < firstBin) return 0f

        var sum = 0f
        for (bin in firstBin..lastBin) {
            val real = fft[bin * 2].toFloat()
            val imaginary = fft[bin * 2 + 1].toFloat()
            sum += hypot(real, imaginary)
        }
        return sum / (lastBin - firstBin + 1)
    }

    /**
     * Turns raw magnitudes into a 0..1 level. The reference peak follows the loudest
     * recent value and decays slowly, so quiet tracks still fill the range and a
     * sudden hit still reads as a hit.
     */
    private class BandNormalizer {
        private var peak = FLOOR

        fun normalize(raw: Float): Float {
            peak = max(raw, peak * PEAK_DECAY).coerceAtLeast(FLOOR)
            val level = (raw / peak).coerceIn(0f, 1f)
            return level.pow(RESPONSE_CURVE)
        }

        fun reset() {
            peak = FLOOR
        }

        private companion object {
            const val FLOOR = 2f
            const val PEAK_DECAY = 0.995f

            /** < 1 lifts quiet passages without clipping loud ones. */
            const val RESPONSE_CURVE = 0.7f
        }
    }

    private companion object {
        const val TAG = "VisualizerAudioSource"

        /** Session 0 is the global output mix. */
        const val GLOBAL_OUTPUT_MIX_SESSION = 0

        const val PREFERRED_CAPTURE_SIZE = 1024

        const val SIGNAL_THRESHOLD = 0.01f
        const val RAW_SIGNAL_THRESHOLD = 1.5f

        const val BASS_LOW_HZ = 20f
        const val BASS_HIGH_HZ = 160f
        const val MID_LOW_HZ = 160f
        const val MID_HIGH_HZ = 2000f
        const val TREBLE_LOW_HZ = 2000f
        const val TREBLE_HIGH_HZ = 8000f
    }
}
