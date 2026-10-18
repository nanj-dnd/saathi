package app.saathi.design

import android.graphics.Typeface

/**
 * The phone's own sans: Roboto for Latin, with Android's Noto faces shaping Devanagari, Tamil,
 * Telugu, Kannada and Malayalam, so a mixed line like "'Display & touch' पर दबाइए" sets cleanly.
 */
object SaathiType {
    private val cache = HashMap<Int, Typeface>()

    /** For the overlay's Views, at any weight from 100 to 900. */
    fun typeface(weight: Int): Typeface = cache.getOrPut(weight) { Typeface.create(Typeface.DEFAULT, weight, false) }
}
