package app.saathi.recipes

import app.saathi.net.Danger
import app.saathi.net.Status
import app.saathi.safety.ScreenSafety
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Scripted guides for common tasks, run on the phone with no network. The JSON lives in
// shared/recipes at the repo root; the app ships it as assets and the Worker serves newer copies
// at GET /recipes. shared/recipes/README.md explains the format for authors.

/** Matches when every group has at least one phrase in what the senior said. */
@Serializable
data class Trigger(
    val all: List<List<String>>,
    /** Only counts while the recipe's app is already in front ("video call" inside WhatsApp). */
    @SerialName("in_app") val inApp: Boolean = false,
)

/** How many times an earlier step's line must already be in the history. */
@Serializable
data class After(val step: String, val min: Int = 1, val max: Int = Int.MAX_VALUE)

@Serializable
data class Step(
    val id: String,
    /**
     * Packages this step runs in, when it is not the recipe's own app: the share sheet, or
     * WhatsApp when sharing from the gallery. Empty means the recipe's apps.
     */
    val apps: List<String> = emptyList(),
    /** Every group needs one of its phrases on screen. */
    @SerialName("screen_has") val screenHas: List<List<String>> = emptyList(),
    /** None of these may be on screen. */
    @SerialName("screen_lacks") val screenLacks: List<String> = emptyList(),
    /** Label alternatives for the clickable element to point at. Empty means point at nothing. */
    val target: List<String> = emptyList(),
    /** Whole-label matches only, for short words that also turn up inside messages ("Send", "Chats"). */
    val exact: Boolean = false,
    /** Labels of an on/off row ("Wi‑Fi", "Use Wi‑Fi"). The step needs that switch on screen, in state [switchIs]. */
    val switch: List<String> = emptyList(),
    @SerialName("switch_is") val switchIs: String = "",
    /** Point at the switch itself, so the senior flips it. */
    @SerialName("tap_switch") val tapSwitch: Boolean = false,
    val after: After? = null,
    /** Language tag to spoken line. "{label}" becomes the matched element's text. */
    val say: Map<String, String>,
    val status: String = Status.NEXT_STEP,
    val danger: String = Danger.NONE,
    /** Give up and ask the model once this line has been said this many times in a row. */
    @SerialName("max_repeats") val maxRepeats: Int = 3,
)

@Serializable
data class Recipe(
    val id: String,
    val version: Int,
    /** English goal, carried between turns. It is how the next turn knows this recipe is running. */
    val goal: String,
    /** Packages the steps run in. ["*"] means any app: spoken tips that need no particular screen. */
    val apps: List<String>,
    /** How the app is named when spoken ("WhatsApp"). */
    @SerialName("app_label") val appLabel: String,
    /** Launcher icon labels to point at when the app is not open yet. */
    @SerialName("app_names") val appNames: List<String> = emptyList(),
    val triggers: List<Trigger>,
    /** Words that mean the senior wants something else ("smaller", "cancel"): hand over to the model. */
    val unless: List<String> = emptyList(),
    /**
     * Ignore the common "unless" words. For safety warnings: "should I not tell the OTP?" still
     * deserves the OTP warning, even though it contains a negation.
     */
    @SerialName("skip_common_unless") val skipCommonUnless: Boolean = false,
    /** Phrases a senior might say, by language. Not used at run time: tests check each one picks this recipe. */
    val examples: Map<String, List<String>> = emptyMap(),
    /** Checked in order, first match wins, so list the furthest-along steps first. */
    val steps: List<Step>,
)

@Serializable
data class Common(
    val version: Int = 0,
    /** Words that hand any request to the model: negations, "why", "problem". */
    val unless: List<String> = emptyList(),
    /** "open", "drawer" and "find" lines, with "{app}" for the app's spoken name. */
    val lines: Map<String, Map<String, String>> = emptyMap(),
)

@Serializable
data class RecipeBundle(val common: Common = Common(), val recipes: List<Recipe> = emptyList())

val Recipe.anyApp get() = ANY_APP in apps
const val ANY_APP = "*"

class RecipeBook(val common: Common, val recipes: List<Recipe>) {
    fun byGoal(goal: String): Recipe? = if (goal.isBlank()) null else recipes.firstOrNull { it.goal == goal }

    companion object {
        val LANGUAGES = listOf("hi-IN", "ta-IN", "te-IN", "kn-IN", "ml-IN")
        val COMMON_LINES = listOf("open", "drawer", "find")
        val EMPTY = RecipeBook(Common(), emptyList())

        private val json = Json { ignoreUnknownKeys = true }
        private val STATUSES = setOf(Status.NEXT_STEP, Status.DONE, Status.NEED_INFO, Status.USER_MUST_DO, Status.CANNOT_HELP)
        private val DANGERS = setOf(Danger.NONE, Danger.SENSITIVE, Danger.SCAM)

        fun parseRecipe(text: String): Recipe = json.decodeFromString(text)
        fun parseCommon(text: String): Common = json.decodeFromString(text)
        fun parseBundle(text: String): RecipeBundle = json.decodeFromString(text)

        /**
         * Combines bundles: per recipe id the highest version wins, likewise for the common lines.
         * Anything that fails [validate] is dropped, so a bad download can't break guidance.
         */
        fun merge(vararg bundles: RecipeBundle): RecipeBook {
            val common = bundles.map { it.common }
                .filter { validateCommon(it).isEmpty() }
                .maxByOrNull { it.version } ?: Common()
            val recipes = LinkedHashMap<String, Recipe>()
            for (r in bundles.flatMap { it.recipes }) {
                if (validate(r).isNotEmpty()) continue
                val have = recipes[r.id]
                if (have == null || r.version > have.version) recipes[r.id] = r
            }
            // Without the shared lines no recipe can send the senior to the right app.
            if (validateCommon(common).isNotEmpty()) return EMPTY
            return RecipeBook(common, recipes.values.toList())
        }

        fun validateCommon(c: Common): List<String> = buildList {
            for (name in COMMON_LINES) {
                val line = c.lines[name]
                if (line == null) add("common: missing line '$name'") else missingLanguages(line).forEach { add("common.$name: no $it") }
            }
        }

        /** Problems that would make a recipe speak the wrong language, loop, or point somewhere unsafe. */
        fun validate(r: Recipe): List<String> = buildList {
            val where = "recipe ${r.id.ifBlank { "?" }}"
            if (r.id.isBlank()) add("$where: no id")
            if (r.goal.isBlank()) add("$where: no goal")
            if (r.apps.isEmpty() || r.apps.any { it.isBlank() }) add("$where: no apps")
            if (r.appLabel.isBlank() && ANY_APP !in r.apps) add("$where: no app_label")
            if (ANY_APP in r.apps && r.apps.size > 1) add("$where: '*' must be the only app")
            if (ANY_APP in r.apps && r.steps.any { it.target.isNotEmpty() || it.after != null }) add("$where: any-app recipes are single spoken steps")
            r.examples.keys.filter { it !in LANGUAGES }.forEach { add("$where: examples for unknown language $it") }
            if (r.triggers.isEmpty()) add("$where: no triggers")
            r.triggers.forEachIndexed { i, t ->
                if (t.all.isEmpty() || t.all.any { g -> g.isEmpty() || g.any { it.isBlank() } }) add("$where: trigger $i has an empty group")
            }
            if (r.steps.isEmpty()) add("$where: no steps")
            val ids = r.steps.map { it.id }
            if (ids.toSet().size != ids.size) add("$where: duplicate step ids")
            for (s in r.steps) {
                val at = "$where step ${s.id}"
                missingLanguages(s.say).forEach { add("$at: no $it line") }
                if (s.status !in STATUSES) add("$at: unknown status ${s.status}")
                if (s.danger !in DANGERS) add("$at: unknown danger ${s.danger}")
                if (s.maxRepeats < 1) add("$at: max_repeats must be at least 1")
                if (s.after != null && s.after.step !in ids) add("$at: after refers to unknown step ${s.after.step}")
                if (s.say.values.any { "{label}" in it } && s.target.isEmpty() && !s.tapSwitch) add("$at: uses {label} without a target")
                if (s.danger != Danger.NONE && (s.target.isNotEmpty() || s.tapSwitch)) add("$at: never point on a ${s.danger} step")
                if (s.switch.isNotEmpty() && s.switchIs !in setOf("on", "off")) add("$at: switch_is must be on or off")
                if (s.tapSwitch && s.switch.isEmpty()) add("$at: tap_switch without a switch")
                if (s.tapSwitch && s.target.isNotEmpty()) add("$at: tap_switch and target both set")
                s.target.filter(ScreenSafety::isSensitiveTarget).forEach { add("$at: target '$it' is for the senior alone") }
                if (s.screenHas.any { it.isEmpty() }) add("$at: empty screen_has group")
                if (s.apps.any { it.isBlank() || it == ANY_APP }) add("$at: step apps must be package names")
            }
        }

        private fun missingLanguages(lines: Map<String, String>) = LANGUAGES.filter { lines[it].isNullOrBlank() }
    }
}
