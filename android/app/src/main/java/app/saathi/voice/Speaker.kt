package app.saathi.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import app.saathi.session.Mouth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Speaks with the phone's own TextToSpeech, slowed down for older listeners. While speaking it
 * ducks other audio (a YouTube video, the radio) and lifts a near-silent media volume, then puts
 * both back: an instruction nobody hears is worse than none.
 */
class Speaker(
    private val context: Context,
    private val language: () -> String,
    /** Normal speaking rate; 1.0 is the engine default. */
    private val rate: () -> Float = { DEFAULT_RATE },
) : Mouth {
    private var tts: TextToSpeech? = null
    private var ttsReady: CompletableDeferred<Boolean>? = null
    private var pending: (() -> Unit)? = null
    private val audio = context.getSystemService(AudioManager::class.java)

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    override suspend fun say(text: String, slow: Boolean) {
        if (text.isBlank()) return
        val engine = engine() ?: return
        val locale = Locale.forLanguageTag(language())
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Speaking Tamil text with an English voice is noise. The caption still shows it, and
            // setup offers to install the voice (see voiceStatus).
            Log.w(TAG, "no TTS voice for $locale")
            return
        }
        engine.setSpeechRate(if (slow) rate() * SLOW_FACTOR else rate())
        engine.setAudioAttributes(attributes)
        val id = "saathi-${System.nanoTime()}"
        val restoreVolume = liftVolume()
        val gotFocus = audio?.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        try {
            // Some engines never report completion (missing voice data, no audio route). Don't hang the session.
            val budget = (4_000L + text.length * 150L) * (if (slow) 2 else 1)
            withTimeoutOrNull(budget) { awaitUtterance(engine, text, id) } ?: engine.stop()
        } finally {
            if (gotFocus) audio?.abandonAudioFocusRequest(focus)
            restoreVolume()
        }
    }

    override fun stop() {
        tts?.stop()
        pending?.invoke()
        pending = null
    }

    /** Whether this phone can speak [tag]; setup uses it to offer the voice download. */
    suspend fun voiceStatus(tag: String): VoiceStatus {
        val engine = engine() ?: return VoiceStatus.NO_ENGINE
        return when (engine.isLanguageAvailable(Locale.forLanguageTag(tag))) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> VoiceStatus.MISSING
            else -> VoiceStatus.OK
        }
    }

    /**
     * If media volume is below [MIN_VOLUME] of maximum, raises it for this utterance. Returns the
     * undo, which leaves the volume alone if the senior changed it in the meantime.
     */
    private fun liftVolume(): () -> Unit {
        val am = audio ?: return {}
        if (am.isVolumeFixed) return {}
        val stream = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(stream)
        val before = am.getStreamVolume(stream)
        val floor = (max * MIN_VOLUME).toInt().coerceAtLeast(1)
        if (before >= floor) return {}
        runCatching { am.setStreamVolume(stream, floor, 0) }.onFailure { return {} }
        return {
            if (am.getStreamVolume(stream) == floor) runCatching { am.setStreamVolume(stream, before, 0) }
        }
    }

    private suspend fun awaitUtterance(engine: TextToSpeech, text: String, id: String) {
        suspendCancellableCoroutine { cont ->
            val done = { if (cont.isActive) cont.resume(Unit) }
            pending = done
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (utteranceId == id) done() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { if (utteranceId == id) done() }
                override fun onStop(utteranceId: String?, interrupted: Boolean) { if (utteranceId == id) done() }
            })
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) done()
            cont.invokeOnCancellation { engine.stop() }
        }
    }

    private suspend fun engine(): TextToSpeech? {
        val ready = ttsReady ?: CompletableDeferred<Boolean>().also { d ->
            ttsReady = d
            tts = TextToSpeech(context) { status -> d.complete(status == TextToSpeech.SUCCESS) }
        }
        return if (ready.await()) tts else null
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = null
    }

    enum class VoiceStatus { OK, MISSING, NO_ENGINE }

    companion object {
        private const val TAG = "Saathi"
        const val DEFAULT_RATE = 0.85f
        const val SLOW_RATE = 0.7f
        /** A repeat is spoken this much slower than normal. */
        private const val SLOW_FACTOR = 0.8f
        /** Media volume is lifted to at least this share of maximum while Saathi speaks. */
        private const val MIN_VOLUME = 0.5f
    }
}
