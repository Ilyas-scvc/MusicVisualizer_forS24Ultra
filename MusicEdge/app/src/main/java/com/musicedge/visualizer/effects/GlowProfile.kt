package com.musicedge.visualizer.effects

/**
 * Shape of the halo around the line, shared by the overlay renderer and the preview
 * in the settings screen so the two cannot drift apart.
 *
 * The halo is painted as several stroked passes of the same path, from the widest
 * and faintest inwards. Two passes - the previous approach - read as a translucent
 * band with a visible edge, because each pass has constant alpha across its whole
 * width. Here the passes sample a Gaussian falloff instead: the accumulated opacity
 * follows exp(-4.6 x^2), reaching about 1% of the peak at the outer radius, so the
 * outermost step is well under one 8-bit alpha level and the boundary disappears
 * while the light stays.
 *
 * Everything is still plain stroked paths - no BlurMaskFilter, no offscreen layer,
 * so the whole thing stays on the hardware pipeline.
 */
object GlowProfile {

    /** Halo radius at full intensity, as a multiple of the line thickness. */
    const val MAX_SPREAD = 3.5f

    /** Opacity the halo reaches next to the line, relative to the line itself. */
    const val PEAK_ALPHA = 0.75f

    /** Pass radii as a fraction of the halo radius, widest first. */
    val RADIUS_FRACTIONS = floatArrayOf(1f, 0.84f, 0.68f, 0.52f, 0.36f, 0.22f, 0.1f)

    /** exp(-4.6 x^2) sampled at [RADIUS_FRACTIONS]: the target accumulated opacity. */
    private val LEVELS = floatArrayOf(0.010f, 0.039f, 0.119f, 0.288f, 0.551f, 0.800f, 0.955f)

    val PASS_COUNT: Int get() = RADIUS_FRACTIONS.size

    /**
     * Per-pass alpha that makes the *accumulated* opacity follow the profile.
     *
     * Drawing pass i over coverage A(i-1) yields A(i) = A(i-1) + a(i) * (1 - A(i-1)),
     * so the alpha each pass needs is derived from the two neighbouring levels rather
     * than used directly - otherwise the overlap would pile up into visible rings.
     *
     * @param peak opacity of the halo next to the line (line alpha x [PEAK_ALPHA]).
     * @param out receives [PASS_COUNT] alphas, widest pass first.
     */
    fun computePassAlphas(peak: Float, out: FloatArray) {
        var covered = 0f
        for (index in LEVELS.indices) {
            val target = (peak * LEVELS[index]).coerceIn(0f, 1f)
            out[index] = if (covered >= 1f) 0f else ((target - covered) / (1f - covered)).coerceAtLeast(0f)
            covered = target
        }
    }

    /** Stroke width of pass [index] for a line of [thicknessPx] and halo [radiusPx]. */
    fun strokeWidth(index: Int, thicknessPx: Float, radiusPx: Float): Float =
        thicknessPx + 2f * radiusPx * RADIUS_FRACTIONS[index]
}
