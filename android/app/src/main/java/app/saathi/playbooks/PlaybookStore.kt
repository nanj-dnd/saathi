package app.saathi.playbooks

import android.content.res.AssetManager

/** Short per-app hints shipped in assets/playbooks/<package>.md, sent with each turn for that app. */
class PlaybookStore(private val available: Set<String>, private val read: (String) -> String) {
    constructor(assets: AssetManager) : this(
        assets.list("playbooks").orEmpty().filter { it.endsWith(".md") }.map { it.removeSuffix(".md") }.toSet(),
        { name -> assets.open("playbooks/$name.md").bufferedReader().use { it.readText() } },
    )

    private val cache = HashMap<String, String>()

    fun forApp(pkg: String): String = cache.getOrPut(pkg) {
        val name = when {
            pkg in available -> pkg
            pkg in BROWSERS -> "_browser"
            else -> null
        }
        listOfNotNull("_general".takeIf { it in available }, name)
            .mapNotNull { runCatching { read(it).trim() }.getOrNull()?.takeIf(String::isNotEmpty) }
            .joinToString("\n\n")
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
