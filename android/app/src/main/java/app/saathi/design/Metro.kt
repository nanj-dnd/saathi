package app.saathi.design

import android.content.Context
import android.content.res.Configuration

/**
 * Saathi's visual system: metro wayfinding. White enamel sign panels, near-black signage type,
 * and metro blue as the only accent. State is always carried by a glyph's shape as well as its
 * colour (arrow, tick, lock, warning), so nothing depends on telling colours apart.
 */
class Metro(
    /** The screen-wide veil that dims everything except the target. */
    val veil: Int,
    /** Sign panel ground and its type. */
    val panel: Int,
    val ink: Int,
    val inkSoft: Int,
    /** Metro blue as a band, ring and outline; [onAccent] sits on [accentFill]. */
    val accent: Int,
    val accentFill: Int,
    val onAccent: Int,
    /** The bright edge under the ring, so it holds on dark and on blue apps. */
    val ringEdge: Int,
    /** Bands for the three signs that are not "press here". */
    val done: Int,
    val caution: Int,
    val danger: Int,
) {
    companion object {
        val LIGHT = Metro(
            veil = 0x5C000000,
            panel = 0xFFFFFFFF.toInt(),
            ink = 0xFF111214.toInt(),
            inkSoft = 0xFF3B3F45.toInt(),
            accent = 0xFF0858A8.toInt(),
            accentFill = 0xFF0858A8.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            ringEdge = 0xFFFFFFFF.toInt(),
            done = 0xFF1B7F3B.toInt(),
            caution = 0xFF9A5B00.toInt(),
            danger = 0xFFC62828.toInt(),
        )

        val DARK = Metro(
            veil = 0x80000000.toInt(),
            panel = 0xFF1C1E22.toInt(),
            ink = 0xFFF4F5F7.toInt(),
            inkSoft = 0xFFC3C7CE.toInt(),
            accent = 0xFF6AB2F2.toInt(),
            accentFill = 0xFF1E6CC0.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            ringEdge = 0xFF0B0C0E.toInt(),
            done = 0xFF4CC071.toInt(),
            caution = 0xFFF0A63A.toInt(),
            danger = 0xFFFF6B6B.toInt(),
        )

        fun of(context: Context): Metro =
            if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) DARK
            else LIGHT
    }
}
