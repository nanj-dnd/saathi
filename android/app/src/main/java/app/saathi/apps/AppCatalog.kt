package app.saathi.apps

import app.saathi.capture.ScreenText
import app.saathi.net.AppInfo
import app.saathi.net.TurnRequest
import app.saathi.recipes.RecipeEngine

/** Match app names locally. Only matches needed for this task may leave the phone. */
class AppCatalog(apps: List<AppInfo>) {
    private val apps = apps.filter { it.packageName.isNotBlank() && it.label.isNotBlank() }
        .distinctBy { it.packageName }.sortedBy { it.packageName }

    fun mentioned(text: String): List<AppInfo> {
        val normalized = ScreenText.norm(text)
        return apps.filter {
            RecipeEngine.wholeWords(normalized, ScreenText.norm(it.label)) ||
                RecipeEngine.wholeWords(normalized, it.packageName.lowercase(java.util.Locale.ROOT))
        }
    }

    fun contextualize(request: TurnRequest): TurnRequest {
        val current = apps.firstOrNull { it.packageName == request.app }
        val named = mentioned(listOfNotNull(request.utterance, request.goal).joinToString(" "))
        return request.copy(
            appName = current?.label.orEmpty(),
            installedApps = (listOfNotNull(current) + named).distinctBy { it.packageName }.take(MAX_RELEVANT),
        )
    }

    /** A label can repair a wrong package only when it identifies exactly one installed app. */
    fun resolve(label: String): String? = apps.filter {
        ScreenText.norm(it.label) == ScreenText.norm(label)
    }.singleOrNull()?.packageName

    /** Never reuse a prior answer that chose between indistinguishable launcher labels. */
    fun ambiguousSelection(text: String, foreground: String): Boolean {
        val normalized = ScreenText.norm(text)
        return mentioned(text).groupBy { ScreenText.norm(it.label) }.values.any { group ->
            group.size > 1 && group.none { it.packageName == foreground ||
                RecipeEngine.wholeWords(normalized, it.packageName.lowercase(java.util.Locale.ROOT)) }
        }
    }

    companion object {
        const val MAX_RELEVANT = 12
        val EMPTY = AppCatalog(emptyList())
    }
}
