package app.saathi.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume

/** The Worker has no natural voice configured (no Sarvam key). */
class VoiceUnavailable : Exception("no natural voice configured")

/**
 * The natural voice: each line is fetched once from the Worker's /speak (Sarvam), kept on the
 * phone, and played from there every time after. Returns nothing when the line can't be had
 * quickly (offline, slow network, no voice configured), and the caller uses the phone's own voice.
 */
class CloudVoice(
    context: Context,
    /** Fetches MP3 bytes for (text, language, pace), or null. */
    private val fetch: suspend (text: String, language: String, pace: Float) -> ByteArray?,
) {
    private val dir = File(context.cacheDir, "speech").apply { mkdirs() }
    private var player: MediaPlayer? = null
    /** Set when the Worker says it has no voice, so the phone stops asking for a while. */
    private var offUntil = 0L

    /** The line as a playable file: from this phone's cache, or fetched within [FETCH_BUDGET_MS]. */
    suspend fun clip(text: String, language: String, pace: Float): File? = withContext(Dispatchers.IO) {
        val file = File(dir, name(text, language, pace))
        if (file.length() > 0) return@withContext file.also { it.setLastModified(System.currentTimeMillis()) }
        if (System.currentTimeMillis() < offUntil) return@withContext null
        val bytes = withTimeoutOrNull(FETCH_BUDGET_MS) {
            runCatching { fetch(text, language, pace) }.onFailure {
                if (it is VoiceUnavailable) pause()
                Log.w(TAG, "natural voice unavailable: ${it.message}")
            }.getOrNull()
        }
        if (bytes == null || bytes.isEmpty()) return@withContext null
        val tmp = File(dir, file.name + ".part")
        tmp.writeBytes(bytes)
        tmp.renameTo(file)
        prune()
        file
    }

    /** The Worker has no voice configured: stop asking for ten minutes. */
    fun pause() {
        offUntil = System.currentTimeMillis() + 10 * 60 * 1000L
    }

    /** Plays [file] to the end. False if it couldn't be played, so the caller can speak it instead. */
    suspend fun play(file: File, attributes: AudioAttributes): Boolean = suspendCancellableCoroutine { cont ->
        val mp = MediaPlayer()
        player = mp
        fun finish(ok: Boolean) {
            runCatching { mp.release() }
            if (player === mp) player = null
            if (cont.isActive) cont.resume(ok)
        }
        try {
            mp.setAudioAttributes(attributes)
            mp.setDataSource(file.path)
            mp.setOnCompletionListener { finish(true) }
            mp.setOnErrorListener { _, _, _ ->
                file.delete() // a broken clip would fail every time
                finish(false)
                true
            }
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            Log.w(TAG, "clip failed to play", e)
            file.delete()
            finish(false)
        }
        cont.invokeOnCancellation { runCatching { mp.stop(); mp.release() } }
    }

    fun stop() {
        player?.let { runCatching { it.stop() } }
    }

    private fun name(text: String, language: String, pace: Float): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$language\n${"%.2f".format(pace)}\n$text".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(40) + ".mp3"
    }

    /** Keeps the cache under [MAX_BYTES] by dropping the lines heard least recently. */
    private fun prune() {
        val files = dir.listFiles { f -> f.extension == "mp3" }?.toMutableList() ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        files.sortBy { it.lastModified() }
        for (f in files) {
            if (total <= MAX_BYTES * 3 / 4) break
            total -= f.length()
            f.delete()
        }
    }

    private companion object {
        const val TAG = "Saathi"
        /** Longer than this and the phone's own voice speaks instead: silence is worse than a plainer voice. */
        const val FETCH_BUDGET_MS = 3_500L
        const val MAX_BYTES = 40L * 1024 * 1024
    }
}
