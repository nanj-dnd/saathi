package app.saathi.apps

import android.content.Context
import android.content.Intent
import app.saathi.net.AppInfo
import app.saathi.net.TurnRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Uses launcher visibility already declared in the manifest; no QUERY_ALL_PACKAGES permission. */
class InstalledApps(private val context: Context) {
    @Volatile private var catalog = AppCatalog.EMPTY
    private val mutex = Mutex()
    private var checkedAt = 0L

    suspend fun contextualize(request: TurnRequest): TurnRequest = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = android.os.SystemClock.elapsedRealtime()
            if (checkedAt == 0L || request.utterance != null || request.history.isEmpty() || now - checkedAt >= REFRESH_MS) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = runCatching {
                    @Suppress("DEPRECATION")
                    val entries = pm.queryIntentActivities(intent, 0)
                    entries.mapNotNull { info ->
                        val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                        if (pkg == context.packageName) return@mapNotNull null
                        // An app may be removed while its label is being loaded.
                        runCatching {
                            val label = info.loadLabel(pm).toString().trim().take(100)
                            if (label.isBlank()) null else AppInfo(pkg, label)
                        }.getOrNull()
                    }
                }.getOrNull()
                if (apps != null) catalog = AppCatalog(apps)
                checkedAt = now
            }
            catalog.contextualize(request)
        }
    }

    fun resolve(label: String): String? = catalog.resolve(label)

    private companion object { const val REFRESH_MS = 60_000L }
}
