package app.saathi.brain

import app.saathi.net.Guide
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

interface CacheStore {
    fun read(): String?
    fun write(text: String)
}

/** Writes to a temp file first, so a crash mid-write leaves the old cache, not half a file. */
class FileCacheStore(private val file: File) : CacheStore {
    override fun read(): String? = if (file.exists()) file.readText() else null
    override fun write(text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}

/**
 * Answers the model already gave on this phone, keyed by [ScreenKey.turnKey]. Seniors repeat the
 * same few tasks (the daily call to a son, the weekly milk order), so most of those steps come
 * from here. Least-recently-used entries go first; old entries are ignored unless the network is
 * down, when a slightly stale step beats no step at all.
 */
class TurnCache(
    private val store: CacheStore,
    private val capacity: Int = 400,
    private val ttlMs: Long = 14L * 24 * 60 * 60 * 1000,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    @Serializable
    private class Entry(val key: String, val guide: Guide, val at: Long)

    private val json = Json { ignoreUnknownKeys = true }
    private val map = LinkedHashMap<String, Entry>(64, 0.75f, true)
    private val mutex = Mutex()
    private var loaded = false

    suspend fun get(key: String, allowStale: Boolean = false): Guide? = mutex.withLock {
        load()
        val e = map[key] ?: return null
        if (!allowStale && now() - e.at > ttlMs) return null
        e.guide
    }

    suspend fun put(key: String, guide: Guide) = mutex.withLock {
        load()
        map[key] = Entry(key, guide, now())
        val it = map.entries.iterator()
        while (map.size > capacity && it.hasNext()) {
            it.next()
            it.remove()
        }
        val text = json.encodeToString(map.values.toList())
        withContext(io) { runCatching { store.write(text) } }
        Unit
    }

    suspend fun size(): Int = mutex.withLock { load(); map.size }

    private suspend fun load() {
        if (loaded) return
        loaded = true
        val text = withContext(io) { runCatching { store.read() }.getOrNull() } ?: return
        // A corrupt or old-format file just means an empty cache.
        val entries = runCatching { json.decodeFromString<List<Entry>>(text) }.getOrDefault(emptyList())
        entries.sortedBy { it.at }.forEach { map[it.key] = it }
    }
}
