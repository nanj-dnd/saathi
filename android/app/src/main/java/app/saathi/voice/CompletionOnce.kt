package app.saathi.voice

import kotlinx.coroutines.CancellableContinuation
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** TTS callbacks and a manual stop can arrive on different threads for the same utterance. */
internal fun <T> completionOnce(continuation: CancellableContinuation<T>, value: T): () -> Unit {
    val completed = AtomicBoolean(false)
    return {
        if (completed.compareAndSet(false, true) && continuation.isActive) continuation.resume(value)
    }
}
