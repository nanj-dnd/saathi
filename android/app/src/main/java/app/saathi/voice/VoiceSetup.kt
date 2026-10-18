package app.saathi.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Setup-time checks for the family member installing Saathi: can this phone speak and listen in
 * the senior's language? A missing voice is the most common reason Saathi would stay silent on a
 * cheap phone, so setup plays a sample and offers the download.
 */
class VoiceSetup(
    private val context: Context,
    /** The natural voice, played for the test when it can be had; the phone's voice otherwise. */
    private val cloud: CloudVoice? = null,
) {
    private val scope = MainScope()
    private var playing: Job? = null
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            pending?.invoke()
            pending = null
        }
    }

    /**
     * Plays [line] in [tag] at [rate], in the natural voice when it can be had, and reports whether
     * the phone's own voice exists (it speaks whenever the natural one can't). Main thread.
     */
    fun test(tag: String, rate: Float, line: String, onResult: (Speaker.VoiceStatus) -> Unit) {
        playing?.cancel()
        playing = scope.launch {
            val clip = cloud?.clip(line, tag, Speaker.naturalPace(rate))
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            if (clip != null && cloud.play(clip, attributes)) {
                onResult(Speaker.VoiceStatus.OK)
            } else {
                speakOnPhone(tag, rate, line, onResult)
            }
        }
    }

    private fun speakOnPhone(tag: String, rate: Float, line: String, onResult: (Speaker.VoiceStatus) -> Unit) {
        val run = run@{
            val engine = tts
            if (!ready || engine == null) {
                onResult(Speaker.VoiceStatus.NO_ENGINE)
                return@run
            }
            val locale = Locale.forLanguageTag(tag)
            when (engine.setLanguage(locale)) {
                TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> onResult(Speaker.VoiceStatus.MISSING)
                else -> {
                    VoicePicker.apply(context, engine, locale)
                    engine.setSpeechRate(rate)
                    engine.speak(line, TextToSpeech.QUEUE_FLUSH, null, "saathi-setup")
                    onResult(Speaker.VoiceStatus.OK)
                }
            }
        }
        if (tts != null && !ready && pending == null) pending = run else run()
    }

    /** Opens the phone's own screen for downloading voice data. */
    fun installVoiceIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Android 13+ can fetch an on-device speech model, so listening works with no internet. */
    val canDownloadOfflineSpeech: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /** Starts the download in the background; the system shows its own progress. Main thread. */
    fun downloadOfflineSpeech(tag: String): Boolean {
        if (!canDownloadOfflineSpeech || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return runCatching {
            val sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            sr.triggerModelDownload(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag))
            sr.destroy()
            true
        }.getOrElse {
            Log.w("Saathi", "offline speech download not started", it)
            false
        }
    }

    fun shutdown() {
        scope.cancel()
        cloud?.stop()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
