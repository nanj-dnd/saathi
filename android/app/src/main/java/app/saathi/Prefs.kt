package app.saathi

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("saathi", Context.MODE_PRIVATE)

    var language: String
        get() = sp.getString("language", null) ?: Languages.forPhone()
        set(v) = sp.edit().putString("language", v).apply()

    var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit().putString("token", v).apply()

    /** When recipes were last fetched from the Worker, in epoch ms. */
    var recipesCheckedAt: Long
        get() = sp.getLong("recipes_checked_at", 0L)
        set(v) = sp.edit().putLong("recipes_checked_at", v).apply()

    /** TextToSpeech rate; seniors who find the default too fast pick "slow" in setup. */
    var speechRate: Float
        get() = when (val r = sp.getFloat("speech_rate", app.saathi.voice.Speaker.DEFAULT_RATE)) {
            // Rates saved before the speeds were raised map onto the new normal and slow.
            0.85f -> app.saathi.voice.Speaker.DEFAULT_RATE
            0.7f -> app.saathi.voice.Speaker.SLOW_RATE
            else -> r
        }
        set(v) = sp.edit().putFloat("speech_rate", v).apply()

    var bubbleX: Int
        get() = sp.getInt("bubble_x", -1)
        set(v) = sp.edit().putInt("bubble_x", v).apply()

    var bubbleY: Int
        get() = sp.getInt("bubble_y", -1)
        set(v) = sp.edit().putInt("bubble_y", v).apply()
}

object Languages {
    /** Where nothing better is known. */
    const val DEFAULT = "en-US"

    /** BCP-47 tag to the language's own name, as a speaker would recognise it, in picker order. */
    val ALL: Map<String, String> = LanguageList.ALL.associate { it.tag to it.native }

    fun of(tag: String): Language? = LanguageList.ALL.firstOrNull { it.tag == tag }

    fun isRtl(tag: String) = of(tag)?.rtl == true

    /** Recipes are written in these (English anywhere uses the English ones); every other language is guided by the model. */
    val RECIPES = setOf("hi-IN", "en-IN", "en-US", "ta-IN", "te-IN", "kn-IN", "ml-IN")

    /**
     * The language Bob offers that matches the phone's own: the exact tag, else the same language
     * (English on an Indian phone is English (India)), else null.
     */
    fun matching(locale: Locale): String? {
        val tags = LanguageList.ALL.map { it.tag }
        tags.firstOrNull { it.equals(locale.toLanguageTag(), ignoreCase = true) }?.let { return it }
        val lang = when (locale.language) { "iw" -> "he"; "in" -> "id"; "tl" -> "fil"; "no", "nn" -> "nb"; else -> locale.language }
        if (lang == "en") return if (locale.country == "IN") "en-IN" else "en-US"
        return tags.firstOrNull { it.substringBefore('-') == lang }
    }

    /** First run: the phone's language if Bob offers it. */
    fun forPhone(): String = matching(Locale.getDefault()) ?: DEFAULT

    /**
     * The locale to ask the recogniser and the phone's voice for: the phone's own regional variant
     * when it speaks the same language (Spanish in Spain, not Mexico), otherwise Bob's default region.
     */
    fun speechTag(tag: String): String {
        val phone = Locale.getDefault()
        return if (matching(phone) == tag && phone.country.isNotEmpty()) phone.toLanguageTag() else tag
    }

    /** A Context whose resources and layout direction follow [tag], independent of the phone's system language. */
    fun context(base: Context, tag: String): Context {
        val config = Configuration(base.resources.configuration).apply {
            val locale = Locale.forLanguageTag(tag)
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }
}
