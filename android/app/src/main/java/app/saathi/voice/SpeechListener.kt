package app.saathi.voice

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.saathi.session.Ears
import app.saathi.session.Heard
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Transcribes one utterance with the phone's own speech service (Google's on most phones), which
 * covers all five languages for free. Offline, it uses the on-device recogniser when the language
 * pack is downloaded, so scripted recipes keep working with no network at all. Main thread only.
 */
class SpeechListener(
    private val context: Context,
    private val language: () -> String,
    /** Held while listening so the mic is allowed while another app is in front. */
    private val onStart: () -> Unit,
    private val onEnd: () -> Unit,
    /** Called once the mic is open, so the senior gets a cue to start talking. */
    private val onReady: () -> Unit = {},
) : Ears {
    private var recognizer: SpeechRecognizer? = null

    override fun finish() {
        recognizer?.stopListening()
    }

    override suspend fun listen(): Heard {
        val online = isOnline(context)
        val onDevice = !online && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(context)) return Heard.Unavailable
        onStart()
        val sr = if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = sr
        try {
            return suspendCancellableCoroutine { cont ->
                fun done(heard: Heard) {
                    if (cont.isActive) cont.resume(heard)
                }
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle) {
                        val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                        done(if (text.isNullOrEmpty()) Heard.Silence else Heard.Words(text))
                    }
                    override fun onError(error: Int) = done(outcome(error, online))
                    override fun onReadyForSpeech(params: Bundle?) = onReady()
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                sr.startListening(intent(preferOffline = !online))
                cont.invokeOnCancellation { sr.cancel() }
            }
        } finally {
            sr.destroy()
            recognizer = null
            onEnd()
        }
    }

    private fun intent(preferOffline: Boolean) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        // Seniors pause mid-sentence; ask for a generous end-of-speech silence (engines may cap it).
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
    }

    companion object {
        /** Maps SpeechRecognizer error codes to what the senior should be told. */
        fun outcome(error: Int, online: Boolean): Heard = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_CLIENT -> Heard.Silence
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Heard.MicBusy
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> Heard.Offline
            // Language not supported or its pack not downloaded: offline that means "get online".
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> if (online) Heard.Unavailable else Heard.Offline
            else -> if (online) Heard.Silence else Heard.Offline
        }

        fun isOnline(context: Context): Boolean {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
