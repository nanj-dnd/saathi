package app.saathi.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import app.saathi.session.Mouth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Plays the Worker's Sarvam audio when it sent some, otherwise falls back to the phone's own
 * TextToSpeech (offline, lower quality, but always there).
 */
class Speaker(private val context: Context, private val language: () -> String) : Mouth {
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady: CompletableDeferred<Boolean>? = null
    private var pending: ((Unit) -> Unit)? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    override suspend fun say(text: String, audioB64: String?) {
        if (text.isBlank()) return
        if (audioB64 != null && playWav(audioB64)) return
        speakLocally(text)
    }

    override fun stop() {
        player?.run { runCatching { stop() }; release() }
        player = null
        tts?.stop()
        pending?.invoke(Unit)
        pending = null
    }

    private suspend fun playWav(b64: String): Boolean {
        val file = File(context.cacheDir, "say.wav")
        runCatching { file.writeBytes(Base64.decode(b64, Base64.DEFAULT)) }.getOrElse { return false }
        val mp = MediaPlayer()
        player = mp
        return suspendCancellableCoroutine { cont ->
            fun finish(ok: Boolean) {
                if (player === mp) {
                    mp.release()
                    player = null
                }
                pending = null
                if (cont.isActive) cont.resume(ok)
            }
            pending = { finish(true) }
            try {
                mp.setAudioAttributes(attributes)
                mp.setDataSource(file.path)
                mp.setOnCompletionListener { finish(true) }
                mp.setOnErrorListener { _, _, _ -> finish(false); true }
                mp.prepare()
                mp.start()
            } catch (e: Exception) {
                finish(false)
            }
            cont.invokeOnCancellation { if (player === mp) { runCatching { mp.stop() }; mp.release(); player = null } }
        }
    }

    private suspend fun speakLocally(text: String) {
        val engine = engine() ?: return
        engine.language = Locale.forLanguageTag(language())
        engine.setSpeechRate(0.85f)
        engine.setAudioAttributes(attributes)
        val id = "saathi-${System.nanoTime()}"
        // Some engines never report completion (missing voice data, no audio route). Don't hang the session.
        withTimeoutOrNull(4_000L + text.length * 150L) { awaitUtterance(engine, text, id) } ?: engine.stop()
    }

    private suspend fun awaitUtterance(engine: TextToSpeech, text: String, id: String) {
        suspendCancellableCoroutine { cont ->
            val done: (Unit) -> Unit = { if (cont.isActive) cont.resume(Unit) }
            pending = done
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (utteranceId == id) done(Unit) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { if (utteranceId == id) done(Unit) }
                override fun onStop(utteranceId: String?, interrupted: Boolean) { if (utteranceId == id) done(Unit) }
            })
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) done(Unit)
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
