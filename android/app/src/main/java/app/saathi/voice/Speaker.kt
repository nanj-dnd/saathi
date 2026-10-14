package app.saathi.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.saathi.session.Mouth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Speaks with the phone's own TextToSpeech, slowed down a little for older listeners. */
class Speaker(private val context: Context, private val language: () -> String) : Mouth {
    private var tts: TextToSpeech? = null
    private var ttsReady: CompletableDeferred<Boolean>? = null
    private var pending: (() -> Unit)? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    override suspend fun say(text: String) {
        if (text.isBlank()) return
        val engine = engine() ?: return
        engine.language = Locale.forLanguageTag(language())
        engine.setSpeechRate(0.85f)
        engine.setAudioAttributes(attributes)
        val id = "saathi-${System.nanoTime()}"
        // Some engines never report completion (missing voice data, no audio route). Don't hang the session.
        withTimeoutOrNull(4_000L + text.length * 150L) { awaitUtterance(engine, text, id) } ?: engine.stop()
    }

    override fun stop() {
        tts?.stop()
        pending?.invoke()
        pending = null
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
}
