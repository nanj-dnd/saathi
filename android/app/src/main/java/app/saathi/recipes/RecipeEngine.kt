package app.saathi.recipes

import app.saathi.capture.ScreenText
import app.saathi.capture.TreeNode
import app.saathi.net.Action
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
class RecipeEngine(
    private val book: RecipeBook,
    /** True if this phone can open [pkg] directly (it has a launcher entry). */
    private val canOpen: (pkg: String) -> Boolean = { false },
) {

    fun guide(req: TurnRequest): Guide? {
        if (book.recipes.isEmpty()) return null
        // No app means the screen couldn't be read (Android 14+ hides "data sensitive" pages such
        // as Wi-Fi from non-tool accessibility services). That is not "outside the app": leave it
        // to the model, which still gets the screenshot.
        if (req.app.isBlank()) return null
        val recipe = active(req) ?: return null
        val screen = ScreenText.parse(req.tree)
        val home = recipe.anyApp || req.app in recipe.apps
        return steps(recipe, req, screen, home) ?: if (home) null else outside(recipe, req, screen)
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
        val scored = book.recipes.mapNotNull { r ->
            if (!r.skipCommonUnless && negated(text, r)) return@mapNotNull null
            if (r.unless.any { contains(text, it, wordStart = false) }) return@mapNotNull null
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

    /**
     * True if [text] carries one of the shared negations ("not ", "why", "नहीं"…) outside this
     * recipe's own phrases, so "do not disturb" can still start the Do Not Disturb recipe while
     * "do not disturb is not turning off" goes to the model.
     */
    private fun negated(text: String, r: Recipe): Boolean {
        var rest = text
        for (phrase in r.triggers.flatMap { t -> t.all.flatten() }) {
            val p = ScreenText.norm(phrase)
            if (' ' in p) rest = rest.replace(p, " ")
        }
        return book.common.unless.any { contains(rest, it, wordStart = false) }
    }

    /** The first step that fits this screen. [home] is true when the recipe's own app is in front. */
    private fun steps(recipe: Recipe, req: TurnRequest, screen: ScreenText, home: Boolean): Guide? {
        for (step in recipe.steps) {
            if (if (step.apps.isEmpty()) !home else req.app !in step.apps) continue
            if (!step.screenHas.all { group -> group.any { screen.has(it, step.exact) } }) continue
            if (step.screenLacks.any { screen.has(it, step.exact) }) continue
            val after = step.after
            if (after != null) {
                val earlier = recipe.steps.first { it.id == after.step }.say[req.language] ?: continue
                val n = req.history.count { said(it, earlier) }
                if (n < after.min || n > after.max) continue
            }
            var node = if (step.target.isEmpty()) null else screen.find(step.target, step.exact) ?: continue
            var label = node?.let { screen.spokenLabel(it, step.target) }.orEmpty()
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
            var say = template.replace("{label}", label)
            if (step.readScreen) say = say.replace("{screen}", readable(screen) ?: continue)
            if (repeats(req.history, say) >= step.maxRepeats) continue
            val g = guide(recipe, step.id, say, node, label, step.status, step.danger)
            // "Slide the screen up" steps: Saathi scrolls for the senior, and the line is the fallback.
            return if (step.id == SCROLL_STEP && node == null) g.copy(action = Action.SCROLL_DOWN) else g
        }
        return null
    }

    /** Not in the app yet: point at its icon on the home screen, or say how to get there. */
    private fun outside(recipe: Recipe, req: TurnRequest, screen: ScreenText): Guide? {
        // At the start of a task, or from the home screen, Saathi opens the app itself. Mid-task in
        // another app (a share sheet, say) it never pulls the senior away; it says where to go.
        val pkg = recipe.apps.firstOrNull { it != ANY_APP && canOpen(it) }
        if (pkg != null && (req.history.isEmpty() || isLauncher(req.app))) {
            val line = (book.common.lines["opening"] ?: OPENING)[req.language] ?: OPENING.getValue("en-IN")
            val say = line.replace("{app}", recipe.appLabel)
            if (repeats(req.history, say) < OUTSIDE_REPEATS) {
                return guide(recipe, "opening", say, null, "", Status.NEXT_STEP, Danger.NONE)
                    .copy(action = Action.OPEN_APP, actionArg = pkg)
            }
        }
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
        /** The step id recipes give their "slide the screen up" step. */
        const val SCROLL_STEP = "scroll"

        /** Said while Saathi opens the app; a recipe bundle's "opening" common line overrides it. Hindi is feminine: the voice is a woman's. */
        val OPENING = mapOf(
            "hi-IN" to "'{app}' खोल रही हूँ।",
            "en-IN" to "Opening '{app}' for you.",
            "ta-IN" to "'{app}' ஐத் திறக்கிறேன்.",
            "te-IN" to "'{app}' తెరుస్తున్నాను.",
            "kn-IN" to "'{app}' ತೆರೆಯುತ್ತಿದ್ದೇನೆ.",
            "ml-IN" to "'{app}' തുറക്കുന്നു.",
        )

        private const val READ_LIMIT = 700

        /**
         * The screen's text in reading order, for reading aloud: plain text rather than buttons,
         * each line once, short enough to listen to. Null when there is nothing worth reading.
         * Codes were already masked when the tree was built, and none of this leaves the phone.
         */
        fun readable(screen: ScreenText): String? {
            val seen = HashSet<String>()
            val parts = screen.nodes.asSequence()
                .filter { !it.clickable && it.label.length >= 2 && it.label != "(hidden)" }
                .map { it.label.trim() }
                .filter { seen.add(ScreenText.norm(it)) }
                .toList()
            if (parts.isEmpty()) return null
            val out = StringBuilder()
            for (p in parts) {
                if (out.length + p.length > READ_LIMIT) break
                if (out.isNotEmpty()) out.append(if (out.last() in ".!?।") " " else ". ")
                out.append(p)
            }
            return out.toString().ifBlank { null }
        }

        /** Home-screen apps: com.google.android.apps.nexuslauncher, com.sec.android.app.launcher, com.miui.home… */
        fun isLauncher(pkg: String) = "launcher" in pkg || pkg.endsWith(".home")

        /**
         * True if [phrase] (or its stem) is in [text]. Trigger phrases must start a word, so Hindi
         * गाना (song) doesn't fire inside लगाना (to set). "Unless" phrases match anywhere, because
         * Dravidian negations are word endings: Tamil செய்யவில்லை, Malayalam വരുന്നില്ല.
         */
        fun contains(text: String, phrase: String, wordStart: Boolean = true): Boolean {
            val p = ScreenText.norm(phrase)
            if (p.isEmpty()) return false
            // A phrase written with a trailing space must end a word: "not " is the word "not",
            // and must not fire inside "notifications" or "note".
            if (phrase.last().isWhitespace()) return found(text, p, wordStart, wordEnd = true)
            if (found(text, p, wordStart)) return true
            val s = stem(p)
            return s != p && found(text, s, wordStart)
        }

        private fun found(text: String, p: String, wordStart: Boolean, wordEnd: Boolean = false): Boolean {
            if (!wordStart && !wordEnd) return p in text
            var i = text.indexOf(p)
            while (i >= 0) {
                val end = i + p.length
                val startOk = !wordStart || i == 0 || isBoundary(text[i - 1])
                val endOk = !wordEnd || end == text.length || isBoundary(text[end])
                if (startOk && endOk) return true
                i = text.indexOf(p, i + 1)
            }
            return false
        }

        private fun isBoundary(c: Char): Boolean {
            if (c.isWhitespace()) return true
            if (c.isLetterOrDigit()) return false
            // Vowel signs and viramas are part of the word they follow.
            return when (Character.getType(c).toByte()) {
                Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> false
                else -> true
            }
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
