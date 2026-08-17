package com.musicedge.visualizer.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.musicedge.visualizer.audio.AudioEngine
import com.musicedge.visualizer.effects.RenderFrame
import com.musicedge.visualizer.effects.VisualizationEffect

/**
 * The overlay surface. It owns the frame clock, the fade, and the perimeter
 * geometry; the picture itself is delegated to a [VisualizationEffect].
 *
 * Rendering rules:
 *  - frames are requested only while something actually changes (fade in progress,
 *    or an effect that says it animates). A settled static line draws once and the
 *    loop is stopped, so the overlay costs no CPU while it is on screen;
 *  - the view is created and driven on the main thread only.
 */
@SuppressLint("ViewConstructor")
class EdgeVisualizerView(
    context: Context,
    style: EdgeStyle,
    effect: VisualizationEffect,
    private val audioEngine: AudioEngine,
) : View(context) {

    private var currentStyle: EdgeStyle = style
    private var currentEffect: VisualizationEffect = effect

    private val renderLoop = RenderLoop(::onFrame)

    private var perimeter: EdgeGeometry.Perimeter? = null
    private var cornerRadii: EdgeGeometry.CornerRadii? = null

    private var fade = 0f
    private var fadeTarget = 0f
    private var fadeSpeedPerSecond = 0f
    private var fadeCompletion: (() -> Unit)? = null

    private var elapsedSeconds = 0f
    private var lastDeltaSeconds = 0f

    init {
        // Nothing here is interactive; the window is already NOT_TOUCHABLE.
        isClickable = false
        isFocusable = false
    }

    fun setTargetFps(fps: Int) = renderLoop.setTargetFps(fps)

    fun setStyle(style: EdgeStyle) {
        val thicknessChanged = style.thicknessPx != currentStyle.thicknessPx ||
            style.glow != currentStyle.glow
        currentStyle = style
        if (thicknessChanged) rebuildPerimeter()
        invalidate()
    }

    fun setEffect(effect: VisualizationEffect) {
        if (effect === currentEffect) return
        currentEffect.release()
        currentEffect = effect
        elapsedSeconds = 0f
        if (effect.needsContinuousFrames(audioEngine.hasLiveAudio)) renderLoop.start()
        invalidate()
    }

    /**
     * @param onComplete invoked on the main thread once the target is reached; a
     *   pending completion from an earlier call is dropped, not invoked.
     */
    fun fadeTo(target: Float, durationMillis: Long, onComplete: (() -> Unit)? = null) {
        fadeTarget = target.coerceIn(0f, 1f)
        fadeSpeedPerSecond = if (durationMillis <= 0L) Float.MAX_VALUE else 1000f / durationMillis
        fadeCompletion = onComplete

        if (fade == fadeTarget) {
            finishFade()
            return
        }
        renderLoop.start()
    }

    private fun onFrame(deltaSeconds: Float) {
        elapsedSeconds += deltaSeconds
        lastDeltaSeconds = deltaSeconds

        if (fade != fadeTarget) {
            val step = fadeSpeedPerSecond * deltaSeconds
            fade = if (fadeTarget > fade) {
                (fade + step).coerceAtMost(fadeTarget)
            } else {
                (fade - step).coerceAtLeast(fadeTarget)
            }
            invalidate()
            if (fade == fadeTarget) finishFade()
            return
        }

        if (currentEffect.needsContinuousFrames(audioEngine.hasLiveAudio)) {
            invalidate()
        } else {
            // Picture is settled: stop asking for frames until something changes.
            renderLoop.stop()
        }
    }

    private fun finishFade() {
        val completion = fadeCompletion
        fadeCompletion = null
        if (fade == 0f || !currentEffect.needsContinuousFrames(audioEngine.hasLiveAudio)) {
            renderLoop.stop()
        }
        completion?.invoke()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        cornerRadii = EdgeGeometry.readCornerRadii(rootWindowInsets, resources.displayMetrics.density)
        rebuildPerimeter()
    }

    private fun rebuildPerimeter() {
        val corners = cornerRadii ?: return
        if (width == 0 || height == 0) return
        // Inset by half the core stroke only: the wider glow passes are meant to be
        // clipped by the display edge, which is what makes the halo fall inwards.
        perimeter = EdgeGeometry.buildPerimeter(
            widthPx = width,
            heightPx = height,
            corners = corners,
            strokeWidthPx = currentStyle.thicknessPx,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val geometry = perimeter ?: return
        if (fade <= 0f) return
        currentEffect.draw(
            canvas = canvas,
            perimeter = geometry,
            frame = RenderFrame(
                elapsedSeconds = elapsedSeconds,
                deltaSeconds = lastDeltaSeconds,
                fade = fade,
                audio = audioEngine.currentFrame(),
            ),
            style = currentStyle,
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderLoop.stop()
        fadeCompletion = null
        currentEffect.release()
    }
}
