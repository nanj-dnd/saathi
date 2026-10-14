package app.saathi.recipes

import app.saathi.capture.ScreenText
import app.saathi.capture.TreeNode
import app.saathi.net.Danger
import app.saathi.net.Guide
import app.saathi.net.PastStep
import app.saathi.net.Status
import app.saathi.net.TurnRequest
import app.saathi.safety.ScreenSafety

/**
 * Runs [RecipeBook] recipes against one turn. Returns null whenever it is not sure, and the
 * model answers instead: no recipe fits what was said, two fit equally, the screen matches no
 * step, or a step has been repeated too often (the senior is stuck or the app looks different).
 */
class RecipeEngine(private val book: RecipeBook) {

    fun guide(req: TurnRequest): Guide? {
        if (book.recipes.isEmpty()) return null
        val recipe = active(req) ?: return null
        val screen = ScreenText.parse(req.tree)
        return if (recipe.anyApp || req.app in recipe.apps) inApp(recipe, req, screen) else outside(recipe, req, screen)
    }

    /** The recipe this turn belongs to, if any. */
    internal fun active(req: TurnRequest): Recipe? {
        val utterance = req.utterance?.takeIf { it.isNotBlank() }
        // Something said is a fresh request: it picks a recipe or goes to the model, even mid-task.
        if (utterance != null) return match(utterance, req.app)
        book.byGoal(req.goal)?.let { return it }
        // A typed request (debug broadcast) arrives as a goal with no history yet.
        return if (req.history.isEmpty() && req.goal.isNotBlank()) match(req.goal, req.app) else null
    }

    internal fun match(said: String, app: String): Recipe? {
        val text = ScreenText.norm(said)
        if (text.isEmpty()) return null
        val negated = book.common.unless.any { contains(text, it) }
        val scored = book.recipes.mapNotNull { r ->
            if (negated && !r.skipCommonUnless) return@mapNotNull null
            if (r.unless.any { contains(text, it) }) return@mapNotNull null
            // More matched groups is a more specific fit. On a tie, the recipe for the app that is
            // already open wins: "call" inside WhatsApp is a WhatsApp call, not the phone dialler.
            val best = r.triggers
                .filter { !it.inApp || app in r.apps }
                .filter { t -> t.all.all { group -> group.any { contains(text, it) } } }
                .maxOfOrNull { it.all.size * 2 + if (it.inApp) 1 else 0 } ?: return@mapNotNull null
            r to best
        }
        val top = scored.maxOfOrNull { it.second } ?: return null
        return scored.filter { it.second == top }.singleOrNull()?.first
    }

    private fun inApp(recipe: Recipe, req: TurnRequest, screen: ScreenText): Guide? {
        for (step in recipe.steps) {
            if (!step.screenHas.all { group -> group.any { screen.has(it, step.exact) } }) continue
            if (step.screenLacks.any { screen.has(it, step.exact) }) continue
            val after = step.after
            if (after != null) {
                val earlier = recipe.steps.first { it.id == after.step }.say[req.language] ?: continue
                val n = req.history.count { said(it, earlier) }
                if (n < after.min || n > after.max) continue
            }
            var node = if (step.target.isEmpty()) null else screen.find(step.target, step.exact) ?: continue
            var label = node?.label.orEmpty()
            if (step.switch.isNotEmpty()) {
                val sw = screen.switchFor(step.switch) ?: continue
                if (sw.on != (step.switchIs == "on")) continue
                if (step.tapSwitch) {
                    node = sw.toggle
                    label = sw.row.label
                }
            }
            if (node != null && ScreenSafety.isSensitiveTarget(node.label)) continue
            val template = step.say[req.language] ?: return null
            val say = template.replace("{label}", label)
            if (repeats(req.history, say) >= step.maxRepeats) continue
            return guide(recipe, step.id, say, node, label, step.status, step.danger)
        }
        return null
    }

    /** Not in the app yet: point at its icon on the home screen, or say how to get there. */
    private fun outside(recipe: Recipe, req: TurnRequest, screen: ScreenText): Guide? {
        val icon = if (isLauncher(req.app)) screen.find(recipe.appNames + recipe.appLabel) else null
        val which = when {
            icon != null -> "open"
            isLauncher(req.app) -> "drawer"
            else -> "find"
        }
        val template = book.common.lines[which]?.get(req.language) ?: return null
        val say = template.replace("{app}", recipe.appLabel)
        if (repeats(req.history, say) >= OUTSIDE_REPEATS) return null
        return guide(recipe, which, say, icon, icon?.label.orEmpty(), Status.NEXT_STEP, Danger.NONE)
    }

    private fun guide(recipe: Recipe, stepId: String, say: String, node: TreeNode?, label: String, status: String, danger: String) = Guide(
        say = say,
        goal = recipe.goal,
        targetKind = if (node != null) "node" else "none",
        nodeId = node?.id.orEmpty(),
        label = label,
        status = status,
        danger = danger,
        expect = "recipe ${recipe.id}/$stepId",
    )

    internal companion object {
        const val OUTSIDE_REPEATS = 3

        /** Home-screen apps: com.google.android.apps.nexuslauncher, com.sec.android.app.launcher, com.miui.home… */
        fun isLauncher(pkg: String) = "launcher" in pkg || pkg.endsWith(".home")

        private fun contains(text: String, phrase: String): Boolean {
            val p = ScreenText.norm(phrase)
            if (p.isEmpty()) return false
            if (p in text) return true
            val s = stem(p)
            return s != p && s in text
        }

        /**
         * Indian languages change a word's ending when they inflect it: Malayalam "ബ്ലിങ്കിറ്റ്"
         * becomes "ബ്ലിങ്കിറ്റിൽ" (on Blinkit), Tamil "கூகுள்" becomes "கூகுளில்". A phrase that
         * ends in a virama, a Malayalam chillu letter or an anusvara is matched by its stem.
         */
        fun stem(p: String): String {
            val last = p.lastOrNull() ?: return p
            val s = when {
                last in VIRAMAS -> p.dropLast(1)
                last in CHILLU -> p.dropLast(1) + CHILLU.getValue(last)
                last == '\u0D02' -> p.dropLast(1) // Malayalam anusvara: പേടിഎം → പേടിഎമ്മിൽ
                else -> return p
            }
            // A short stem matches too much: Tamil கால் (call) would match காலை (morning).
            return if (s.length >= MIN_STEM) s else p
        }

        private const val MIN_STEM = 4

        // Devanagari, Tamil, Telugu, Kannada and Malayalam viramas.
        private val VIRAMAS = setOf('\u094D', '\u0BCD', '\u0C4D', '\u0CCD', '\u0D4D')
        // Malayalam chillus and the consonant each one stands for: ഗൂഗിൾ → ഗൂഗിളിൽ.
        private val CHILLU = mapOf('\u0D7A' to '\u0D23', '\u0D7B' to '\u0D28', '\u0D7C' to '\u0D30', '\u0D7D' to '\u0D32', '\u0D7E' to '\u0D33', '\u0D7F' to '\u0D15')

        /** True if [past] said the line [template], with any "{label}" filled in. */
        fun said(past: PastStep, template: String): Boolean {
            if ("{label}" !in template) return past.say == template
            val (head, tail) = template.split("{label}", limit = 2)
            return past.say.length >= head.length + tail.length && past.say.startsWith(head) && past.say.endsWith(tail)
        }

        /** How many times in a row, counting back from the latest turn, exactly [say] was said. */
        fun repeats(history: List<PastStep>, say: String): Int =
            history.asReversed().takeWhile { it.say == say }.count()
    }
}
