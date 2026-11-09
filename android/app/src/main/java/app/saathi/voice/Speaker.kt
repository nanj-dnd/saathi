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
    /** The natural voice, tried first; the phone's own voice speaks whatever it can't. */
    private val cloud: CloudVoice? = null,
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
        val speed = if (slow) rate() * SLOW_FACTOR else rate()
        cloud?.clip(text, language(), naturalPace(speed))?.let { clip ->
            val restoreVolume = liftVolume()
            val gotFocus = audio?.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            try {
                if (cloud.play(clip, attributes)) return
            } finally {
                if (gotFocus) audio?.abandonAudioFocusRequest(focus)
                restoreVolume()
            }
        }
        val engine = engine() ?: return
        val locale = Locale.forLanguageTag(app.saathi.Languages.speechTag(language()))
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Speaking Tamil text with an English voice is noise. The caption still shows it, and
            // setup offers to install the voice (see voiceStatus).
            Log.w(TAG, "no TTS voice for $locale")
            return
        }
        VoicePicker.apply(context, engine, locale)
        engine.setSpeechRate(speed)
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

    /** Fetches a task's coming lines in the natural voice, at the speed they'll be spoken. */
    suspend fun prefetch(lines: List<String>) {
        cloud?.prefetch(lines, language(), naturalPace(rate()))
    }

    /**
     * Starts the phone's speech engine ahead of time. Some phones take seconds to bring it up, and
     * the first step of a task (or the fallback when the natural voice can't be had) shouldn't wait.
     */
    suspend fun warmUp() {
        val engine = engine() ?: return
        val locale = Locale.forLanguageTag(app.saathi.Languages.speechTag(language()))
        if (engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) VoicePicker.apply(context, engine, locale)
    }

    override fun stop() {
        val complete = pending
        pending = null
        cloud?.stop()
        tts?.stop()
        complete?.invoke()
    }

    /** Whether this phone can speak [tag]; setup uses it to offer the voice download. */
    suspend fun voiceStatus(tag: String): VoiceStatus {
        val engine = engine() ?: return VoiceStatus.NO_ENGINE
        return when (engine.isLanguageAvailable(Locale.forLanguageTag(app.saathi.Languages.speechTag(tag)))) {
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
            val done = completionOnce(cont, Unit)
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
        // Stretching speech far below 1.0 makes even a good voice sound mechanical, so "normal" is
        // only a touch slow; seniors who want more pick "slow" in setup.
        const val DEFAULT_RATE = 0.95f
        const val SLOW_RATE = 0.8f
        /** A repeat is spoken this much slower than normal. */
        private const val SLOW_FACTOR = 0.88f
        /** Media volume is lifted to at least this share of maximum while Saathi speaks. */
        private const val MIN_VOLUME = 0.5f

        /** The natural voice already speaks at a comfortable speed, so "normal" plays it as is. */
        fun naturalPace(rate: Float) = rate / DEFAULT_RATE
    }
}
