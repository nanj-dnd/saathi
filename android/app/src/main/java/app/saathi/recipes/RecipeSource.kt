package app.saathi.recipes

import android.content.res.AssetManager
import android.util.Log
import java.io.File

/**
 * The recipes in use: the ones shipped in assets/recipes (copied from shared/recipes at build
 * time), overlaid with any newer ones downloaded from the Worker into [downloaded]. A recipe can
 * be fixed for a redesigned app the same day, without waiting for an app update.
 */
class RecipeSource(private val assets: AssetManager, private val downloaded: File) {
    private val bundled: RecipeBundle by lazy { readBundled() }

    @Volatile
    var book: RecipeBook = RecipeBook.EMPTY
        private set

    init {
        book = load()
    }

    /** Saves a bundle fetched from the Worker if it parses; the merge drops anything invalid. */
    fun install(json: String): Boolean {
        val bundle = runCatching { RecipeBook.parseBundle(json) }.getOrElse {
            Log.w(TAG, "ignoring bad recipe bundle", it)
            return false
        }
        downloaded.parentFile?.mkdirs()
        val tmp = File(downloaded.parentFile, downloaded.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(downloaded)) {
            downloaded.delete()
            tmp.renameTo(downloaded)
        }
        book = RecipeBook.merge(bundled, bundle)
        return true
    }

    private fun load(): RecipeBook {
        val fetched = runCatching { if (downloaded.exists()) RecipeBook.parseBundle(downloaded.readText()) else null }.getOrNull()
        return if (fetched != null) RecipeBook.merge(bundled, fetched) else RecipeBook.merge(bundled)
    }

    private fun readBundled(): RecipeBundle {
        val names = runCatching { assets.list(DIR).orEmpty().filter { it.endsWith(".json") } }.getOrDefault(emptyList())
        fun read(name: String) = assets.open("$DIR/$name").bufferedReader().use { it.readText() }
        val common = runCatching { RecipeBook.parseCommon(read(COMMON)) }.getOrElse { Common() }
        val recipes = names.filter { it != COMMON }.mapNotNull { name ->
            runCatching { RecipeBook.parseRecipe(read(name)) }
                .onFailure { Log.w(TAG, "bad bundled recipe $name", it) }
                .getOrNull()
        }
        return RecipeBundle(common, recipes)
    }

    private companion object {
        const val TAG = "Saathi"
        const val DIR = "recipes"
        const val COMMON = "_common.json"
    }
}
