package app.saathi.net

import app.saathi.Prefs
import app.saathi.session.Brain
import app.saathi.voice.VoiceUnavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class WorkerClient(
    private val baseUrl: String,
    private val prefs: Prefs,
    /** Debug builds save each request here so it can be replayed with `npm run fixtures`. */
    private val fixtureDir: File? = null,
) : Brain {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun turn(request: TurnRequest): TurnResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(request)
        saveFixture(request)
        var token = prefs.token ?: register()
        var res = post("/turn", body, token)
        if (res.code == 401) {
            res.close()
            token = register()
            res = post("/turn", body, token)
        }
        res.use {
            when {
                it.code == 429 -> throw TurnFailure(TurnFailure.Kind.LIMIT, "daily limit")
                it.code >= 500 -> throw TurnFailure(TurnFailure.Kind.BUSY, "worker ${it.code}")
                !it.isSuccessful -> throw IOException("worker ${it.code}")
            }
            json.decodeFromString<TurnResponse>(it.body!!.string())
        }
    }

    /**
     * One line in the natural voice, as MP3 bytes; null if the Worker couldn't make it (busy, limit,
     * upstream error). Throws [VoiceUnavailable] when no voice is configured at all.
     */
    suspend fun speak(text: String, language: String, pace: Float): ByteArray? = withContext(Dispatchers.IO) {
        val body = json.encodeToString(SpeakRequest(text, language, pace))
        var token = prefs.token ?: register()
        var res = post("/speak", body, token)
        if (res.code == 401) {
            res.close()
            token = register()
            res = post("/speak", body, token)
        }
        res.use {
            when {
                it.code == 501 -> throw VoiceUnavailable()
                !it.isSuccessful -> null
                else -> it.body?.bytes()
            }
        }
    }

    /** Opens (or keeps open) the connection to the Worker, so the next request skips the handshake. */
    suspend fun warm() = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(baseUrl.trimEnd('/') + "/ping").get().build()
            http.newCall(req).await().close()
        }
        Unit
    }

    /** The Worker's current recipe bundle (shared/recipes), as JSON. */
    suspend fun fetchRecipes(): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl.trimEnd('/') + "/recipes").get().build()
        http.newCall(req).await().use {
            if (!it.isSuccessful) throw IOException("recipes ${it.code}")
            it.body!!.string()
        }
    }

    private suspend fun register(): String {
        val res = post("/register", "{}", null)
        val token = res.use {
            if (!it.isSuccessful) throw IOException("register ${it.code}")
            json.decodeFromString<RegisterResponse>(it.body!!.string()).token
        }
        prefs.token = token
        return token
    }

    private suspend fun post(path: String, body: String, token: String?): Response {
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .post(body.toRequestBody(jsonType))
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        return http.newCall(req).await()
    }

    private fun saveFixture(request: TurnRequest) {
        val dir = fixtureDir ?: return
        runCatching {
            dir.mkdirs()
            val name = "turn-" + System.currentTimeMillis()
            request.screenshotB64?.let {
                File(dir, "$name.jpg").writeBytes(android.util.Base64.decode(it, android.util.Base64.DEFAULT))
            }
            File(dir, "$name.json").writeText(json.encodeToString(request.copy(screenshotB64 = null)))
        }
    }
}

@kotlinx.serialization.Serializable
private data class SpeakRequest(val text: String, val language: String, val pace: Float)
