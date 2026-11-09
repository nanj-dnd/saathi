package app.saathi.design

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import app.saathi.R

/**
 * Two voices. Board lettering (Help, Repeat, Close, titles, step counts) is Paytone One, a heavy
 * rounded sign face; letters it lacks (every non-Latin script) fall back to the phone's Noto faces.
 * Captions and body copy use the phone's own sans throughout, so a mixed line like
 * "'Display & touch' पर दबाइए" sets as one sentence.
 */
object SaathiType {
    private val cache = HashMap<Int, Typeface>()
    private var signFace: Typeface? = null

    /** For the overlay's Views, at any weight from 100 to 900. */
    fun typeface(weight: Int): Typeface = cache.getOrPut(weight) { Typeface.create(Typeface.DEFAULT, weight, false) }

    /** The signboard face for short labels. */
    fun sign(context: Context): Typeface =
        signFace ?: (ResourcesCompat.getFont(context, R.font.paytone_one) ?: typeface(800)).also { signFace = it }

    /**
     * Board lettering for [text]: Paytone One when the text is Latin. Any other script would fall
     * back to the phone's face at its regular weight, so it gets that face at extra-bold instead.
     */
    fun signFor(context: Context, text: String): Typeface = if (isLatin(text)) sign(context) else typeface(800)

    /** True when every letter is Latin (basic, accented or extended), so Paytone One can set it. */
    fun isLatin(text: String) = text.none { it.isLetter() && it.code > 0x24F }
}
