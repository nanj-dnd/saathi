package app.saathi.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.saathi.session.Ears
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Transcribes one utterance with the phone's own speech service (Google's on most phones), which
 * covers all five languages for free. Must be used from the main thread.
 */
class SpeechListener(
    private val context: Context,
    private val language: () -> String,
    /** Held while listening so the mic is allowed while another app is in front. */
    private val onStart: () -> Unit,
    private val onEnd: () -> Unit,
) : Ears {
    private var recognizer: SpeechRecognizer? = null

    override fun finish() {
        recognizer?.stopListening()
    }

    override suspend fun listen(): String? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
        onStart()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        try {
            return suspendCancellableCoroutine { cont ->
                fun done(text: String?) {
                    if (cont.isActive) cont.resume(text?.trim()?.takeIf { it.isNotEmpty() })
                }
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle) =
                        done(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
                    override fun onError(error: Int) = done(null)
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                sr.startListening(intent())
                cont.invokeOnCancellation { sr.cancel() }
            }
        } finally {
            sr.destroy()
            recognizer = null
            onEnd()
        }
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        // Seniors pause mid-sentence; ask for a generous end-of-speech silence (engines may cap it).
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
    }
}
