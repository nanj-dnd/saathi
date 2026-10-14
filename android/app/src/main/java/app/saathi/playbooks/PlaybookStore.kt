package app.saathi.playbooks

import android.content.res.AssetManager

/** Short per-app hints shipped in assets/playbooks/<package>.md, sent with each turn for that app. */
class PlaybookStore(private val assets: AssetManager) {
    private val cache = HashMap<String, String>()
    private val available: Set<String> by lazy {
        assets.list("playbooks").orEmpty().map { it.removeSuffix(".md") }.toSet()
    }

    fun forApp(pkg: String): String = cache.getOrPut(pkg) {
        val name = when {
            pkg in available -> pkg
            pkg in BROWSERS -> "_browser"
            else -> return@getOrPut ""
        }
        assets.open("playbooks/$name.md").bufferedReader().use { it.readText() }.trim()
    }

    private companion object {
        val BROWSERS = setOf(
            "com.android.chrome",
            "com.sec.android.app.sbrowser",
            "com.mi.globalbrowser",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
        )
    }
}
