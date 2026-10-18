package app.saathi.design

import android.content.Context
import android.content.res.Configuration

/**
 * Saathi's visual system: a cursor buddy. One blue pointer flies to the button to press, and a
 * quiet near-black caption says the step. Blue appears only where to press or on what can be
 * pressed; state is carried by a badge's shape (tick, lock, warning), never colour alone.
 */
class Buddy(
    val dark: Boolean,
    /** The light dim over everything except the target. */
    val veil: Int,
    /** The floating caption card and its type. */
    val card: Int,
    /** A hairline around the card, only at night, so it separates from dark apps. */
    val cardEdge: Int,
    val cardInk: Int,
    val cardInkSoft: Int,
    /** Repeat: a white pill. Close: a translucent pill on the card. */
    val pill: Int,
    val pillInk: Int,
    val pillQuiet: Int,
    /** Saathi blue as a fill under white ([onAccent]); [accentLine] where blue is a line or text on the ground. */
    val accent: Int,
    val accentLine: Int,
    val onAccent: Int,
    /** The edge that keeps the cursor and Help pill visible on any app colour. */
    val edge: Int,
    /** Badge fills and the glyph colour on each. */
    val done: Int,
    val onDone: Int,
    val caution: Int,
    val onCaution: Int,
    val danger: Int,
    val onDanger: Int,
) {
    companion object {
        private const val BLUE = 0xFF1F5FE0.toInt()
        private const val NEAR_BLACK = 0xFF15171C.toInt()

        val LIGHT = Buddy(
            dark = false,
            veil = 0x38000000,
            card = NEAR_BLACK,
            cardEdge = 0,
            cardInk = 0xFFFFFFFF.toInt(),
            cardInkSoft = 0xB3FFFFFF.toInt(),
            pill = 0xFFFFFFFF.toInt(),
            pillInk = NEAR_BLACK,
            pillQuiet = 0x29FFFFFF,
            accent = BLUE,
            accentLine = BLUE,
            onAccent = 0xFFFFFFFF.toInt(),
            edge = 0xFFFFFFFF.toInt(),
            done = 0xFF1E9E5A.toInt(),
            onDone = 0xFFFFFFFF.toInt(),
            caution = 0xFFF5A524.toInt(),
            onCaution = NEAR_BLACK,
            danger = 0xFFE5484D.toInt(),
            onDanger = 0xFFFFFFFF.toInt(),
        )

        val DARK = Buddy(
            dark = true,
            veil = 0x52000000,
            card = 0xFF22252B.toInt(),
            cardEdge = 0x1FFFFFFF,
            cardInk = 0xFFFFFFFF.toInt(),
            cardInkSoft = 0xB3FFFFFF.toInt(),
            pill = 0xFFFFFFFF.toInt(),
            pillInk = NEAR_BLACK,
            pillQuiet = 0x29FFFFFF,
            accent = BLUE,
            accentLine = 0xFF4D8BFF.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            edge = 0xFFFFFFFF.toInt(),
            done = 0xFF1E9E5A.toInt(),
            onDone = 0xFFFFFFFF.toInt(),
            caution = 0xFFF5A524.toInt(),
            onCaution = NEAR_BLACK,
            danger = 0xFFE5484D.toInt(),
            onDanger = 0xFFFFFFFF.toInt(),
        )

        fun of(context: Context): Buddy =
            if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) DARK
            else LIGHT
    }
}
