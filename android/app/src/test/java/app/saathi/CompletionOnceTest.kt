package app.saathi

import app.saathi.voice.completionOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class CompletionOnceTest {
    @Test fun `simultaneous speech completion stop and error callbacks resume only once`() = runTest {
        val failures = ConcurrentLinkedQueue<Throwable>()
        val pool = Executors.newFixedThreadPool(4)
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val done = completionOnce(continuation, Unit)
                val ready = CountDownLatch(4)
                val start = CountDownLatch(1)
                repeat(4) {
                    pool.submit {
                        ready.countDown()
                        start.await()
                        repeat(1000) { runCatching { done() }.onFailure { failures.add(it) } }
                    }
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
            }
            pool.shutdown()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
            assertTrue(failures.toString(), failures.isEmpty())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun `late repeated callbacks after cancellation are harmless`() = runTest {
        lateinit var done: () -> Unit
        val job = launch { suspendCancellableCoroutine<Unit> { done = completionOnce(it, Unit) } }
        runCurrent()
        job.cancel()
        runCurrent()
        repeat(10) { done() }
        assertTrue(job.isCancelled)
    }
}
