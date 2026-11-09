package app.saathi

import app.saathi.net.await
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HttpAwaitTest {
    private class FakeCall : Call {
        lateinit var callback: Callback
        var cancelled = false
        var executed = false
        override fun request() = Request.Builder().url("https://saathi.test/turn").build()
        override fun execute(): Response = error("Only asynchronous calls expected")
        override fun enqueue(responseCallback: Callback) { executed = true; callback = responseCallback }
        override fun cancel() { cancelled = true }
        override fun isExecuted() = executed
        override fun isCanceled() = cancelled
        override fun timeout() = Timeout()
        override fun clone(): Call = FakeCall()
    }

    private class Body : ResponseBody() {
        var closed = false
        private val data = object : ForwardingSource(Buffer().writeUtf8("reply")) {
            override fun close() { closed = true; super.close() }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength() = 5L
        override fun source(): BufferedSource = data
    }

    private fun response(call: Call, body: Body) = Response.Builder()
        .request(call.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()

    @Test fun `cancellation closes responses that arrive later`() = runTest {
        val call = FakeCall()
        val body = Body()
        val job = launch { call.await().close() }
        runCurrent()
        job.cancel()
        runCurrent()
        call.callback.onResponse(call, response(call, body))
        assertTrue(call.cancelled)
        assertTrue(body.closed)
    }

    @Test fun `cancellation closes a response awaiting coroutine dispatch`() = runTest {
        val call = FakeCall()
        val body = Body()
        var delivered = false
        val job = launch { call.await().use { delivered = true } }
        runCurrent()
        call.callback.onResponse(call, response(call, body))
        job.cancel()
        runCurrent()
        assertFalse(delivered)
        assertTrue(body.closed)
    }

    @Test fun `successful responses stay open until the caller closes them`() = runTest {
        val call = FakeCall()
        val body = Body()
        val job = launch {
            call.await().use {
                assertFalse(body.closed)
                assertEquals("reply", it.body!!.string())
            }
        }
        runCurrent()
        call.callback.onResponse(call, response(call, body))
        job.join()
        assertTrue(body.closed)
    }

    @Test fun `network failures reach the caller`() = runTest {
        val call = FakeCall()
        val failure = IOException("offline")
        var caught: Throwable? = null
        val job = launch { try { call.await() } catch (e: IOException) { caught = e } }
        runCurrent()
        call.callback.onFailure(call, failure)
        job.join()
        assertTrue(caught is IOException)
        assertEquals(failure.message, caught?.message)
    }
}
