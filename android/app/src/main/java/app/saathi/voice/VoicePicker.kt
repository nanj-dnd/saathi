package app.saathi.voice

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Chooses the voice the phone's engine speaks a language in. Left alone, engines hand back their
 * default voice, often an older flat one, while Google's engine also carries smoother voices,
 * including free online ones. Those need internet, so offline the on-phone version is used.
 * This voice is the fallback: the natural voice (CloudVoice) speaks whenever it can.
 */
internal object VoicePicker {
    private val chosen = HashMap<String, Voice>()

    /** Sets the voice for [locale] on [engine], after setLanguage has succeeded. */
    fun apply(context: Context, engine: TextToSpeech, locale: Locale) {
        val online = isOnline(context)
        val key = "${locale.toLanguageTag()}/$online"
        val voice = chosen[key] ?: best(engine, locale, online)?.also {
            chosen[key] = it
            Log.i(TAG, "voice for $key: ${it.name} quality=${it.quality} network=${it.isNetworkConnectionRequired}")
        }
        if (voice != null && engine.voice?.name != voice.name) engine.voice = voice
    }

    private fun best(engine: TextToSpeech, locale: Locale, online: Boolean): Voice? {
        val all = usable(engine, locale, online)
        if (all.isEmpty()) Log.w(TAG, "no voice for ${locale.toLanguageTag()} (online=$online)")
        // Highest quality first; online voices win ties (they are Google's newer models); then the
        // lowest latency, and the name so the choice is the same every time.
        return all.sortedWith(
            compareByDescending<Voice> { it.quality }
                .thenByDescending { it.isNetworkConnectionRequired }
                .thenBy { it.latency }
                .thenBy { it.name },
        ).firstOrNull()
    }

    private fun usable(engine: TextToSpeech, locale: Locale, online: Boolean): List<Voice> =
        runCatching { engine.voices }.getOrNull().orEmpty().filter {
            it.locale.language == locale.language &&
                (locale.country.isEmpty() || it.locale.country == locale.country) &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features &&
                (online || !it.isNetworkConnectionRequired)
        }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private const val TAG = "Saathi"
}
