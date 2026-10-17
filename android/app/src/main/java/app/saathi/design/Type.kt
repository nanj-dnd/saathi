package app.saathi.design

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Anek, Ek Type's signage-like sans, in the script of the senior's language. Each file also carries
 * Latin, so "'Display & touch' पर दबाइए" sets in one face. Variable: weight 100–800, width 75–125.
 */
object SaathiType {
    private val FILES = mapOf(
        "hi-IN" to "AnekDevanagari",
        "en-IN" to "AnekLatin",
        "ta-IN" to "AnekTamil",
        "te-IN" to "AnekTelugu",
        "kn-IN" to "AnekKannada",
        "ml-IN" to "AnekMalayalam",
    )

    private fun file(language: String) = "fonts/${FILES[language] ?: "AnekLatin"}.ttf"

    private val cache = HashMap<String, Typeface>()

    /** For the overlay's Views. Falls back to the system sans if the asset can't be read. */
    fun typeface(context: Context, language: String, weight: Int, width: Int = 100): Typeface =
        cache.getOrPut("$language/$weight/$width") {
            runCatching {
                Typeface.Builder(context.assets, file(language))
                    .setFontVariationSettings("'wght' $weight, 'wdth' $width")
                    .build()
            }.getOrNull() ?: Typeface.create(Typeface.SANS_SERIF, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }

    /** For the Compose setup screen. */
    @OptIn(ExperimentalTextApi::class)
    fun family(context: Context, language: String): FontFamily {
        val path = file(language)
        fun at(w: Int) = Font(
            path, context.assets, FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.width(100f)),
        )
        return FontFamily(at(400), at(500), at(600), at(700), at(800))
    }
}
