package app.saathi

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("saathi", Context.MODE_PRIVATE)

    var language: String
        get() = sp.getString("language", null) ?: Languages.DEFAULT
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
        get() = sp.getFloat("speech_rate", 0.85f)
        set(v) = sp.edit().putFloat("speech_rate", v).apply()

    var bubbleX: Int
        get() = sp.getInt("bubble_x", -1)
        set(v) = sp.edit().putInt("bubble_x", v).apply()

    var bubbleY: Int
        get() = sp.getInt("bubble_y", -1)
        set(v) = sp.edit().putInt("bubble_y", v).apply()
}

object Languages {
    const val DEFAULT = "hi-IN"

    /** BCP-47 tag to the language's own name, as a senior would recognise it. */
    val ALL = linkedMapOf(
        "hi-IN" to "हिन्दी",
        "en-IN" to "English",
        "ta-IN" to "தமிழ்",
        "te-IN" to "తెలుగు",
        "kn-IN" to "ಕನ್ನಡ",
        "ml-IN" to "മലയാളം",
    )

    /** A Context whose resources resolve in [tag], independent of the phone's system language. */
    fun context(base: Context, tag: String): Context {
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }
}
