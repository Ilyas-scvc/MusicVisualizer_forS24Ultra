package com.musicedge.visualizer.effects

/**
 * The one place that knows which effects exist. Adding Wave, Spectrum, Breathing or
 * an album-artwork gradient later means adding a class and one line here - the
 * overlay, the engine and the UI need no change.
 *
 * Every effect paints the user's six-colour gradient; there is no single-colour
 * option by design.
 */
object EffectRegistry {

    const val DEFAULT_ID = FlowEffect.ID

    val availableIds: List<String> = listOf(FlowEffect.ID, BassPulseEffect.ID)

    fun create(effectId: String): VisualizationEffect = when (effectId) {
        BassPulseEffect.ID -> BassPulseEffect()
        else -> FlowEffect()
    }

    /** True when the effect needs audio data to look the way it is meant to. */
    fun requiresAudio(effectId: String): Boolean = effectId == BassPulseEffect.ID
}
