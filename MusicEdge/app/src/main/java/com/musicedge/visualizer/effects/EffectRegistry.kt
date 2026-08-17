package com.musicedge.visualizer.effects

/**
 * The one place that knows which effects exist. Stage 2+ effects (Bass Pulse,
 * Rainbow, Spectrum, Wave, Breathing, Gradient, Album Flow) are added here and
 * become selectable without any change to the overlay or the engine.
 */
object EffectRegistry {

    val availableIds: List<String> = listOf(StaticEffect.ID)

    fun create(effectId: String): VisualizationEffect = when (effectId) {
        StaticEffect.ID -> StaticEffect()
        else -> StaticEffect()
    }
}
