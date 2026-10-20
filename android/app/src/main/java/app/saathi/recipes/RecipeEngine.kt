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
    /**
     * True if this phone can open [pkg] directly (it has a launcher entry). Null when unknown (tests,
     * tools): then Saathi never opens apps itself and never concludes an app is missing.
     */
    private val canOpen: ((pkg: String) -> Boolean)? = null,
) {

    fun guide(req: TurnRequest): Guide? {
        if (book.recipes.isEmpty()) return null
        // No app means the screen couldn't be read (Android 14+ hides "data sensitive" pages such
        // as Wi-Fi from non-tool accessibility services). That is not "outside the app": leave it
        // to the model, which still gets the screenshot.
        if (req.app.isBlank()) return null
        // A fresh request, spoken or typed (a typed one arrives as a goal with no history yet).
        val fresh = req.utterance?.takeIf { it.isNotBlank() } ?: req.goal.takeIf { it.isNotBlank() && req.history.isEmpty() }
        fresh?.let { said -> download(said, req)?.let { return it } }
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

    /** Every recipe [said] matches, with its score: for tests and for tuning word families. */
    internal fun candidates(said: String, app: String): List<Pair<String, Int>> = scored(said, app).map { it.first.id to it.second }

    internal fun match(said: String, app: String): Recipe? {
        val scored = scored(said, app)
        val top = scored.maxOfOrNull { it.second } ?: return null
        return scored.filter { it.second == top }.singleOrNull()?.first
    }

    private fun scored(said: String, app: String): List<Pair<Recipe, Int>> {
        val text = ScreenText.norm(said)
        if (text.isEmpty()) return emptyList()
        val asked = book.mentions(text)
        val scored = book.recipes.mapNotNull { r ->
            if (!r.skipCommonUnless && negated(text, r)) return@mapNotNull null
            if (r.unless.any { contains(text, it, wordStart = false) }) return@mapNotNull null
            // Safety warnings (spoken in any app) always speak, whatever else was asked.
            val warning = r.anyApp && r.steps.any { it.danger != Danger.NONE }
            if (!warning && !fits(asked, r)) return@mapNotNull null
            // More matched groups is a more specific fit. On a tie, the recipe for the app that is
            // already open wins: "call" inside WhatsApp is a WhatsApp call, not the phone dialler.
            // A script that matched in its own words beats one that matched only through a shared
            // word family, so families add tolerance without overriding a script's precise wording.
            val best = book.triggers.getValue(r.id).zip(r.triggers)
                .filter { (wide, _) -> !wide.inApp || app in r.apps }
                .filter { (wide, _) -> wide.all.all { group -> group.any { contains(text, it) } } }
                .maxOfOrNull { (wide, own) ->
                    val exact = own.all.all { group -> group.any { contains(text, it) } }
                    wide.all.size * 4 + (if (exact) 2 else 0) + (if (wide.inApp) 1 else 0)
                } ?: return@mapNotNull null
            r to best
        }
        return scored
    }

    /**
     * The failsafes against the right words in the wrong script. A request that asks for something
     * the script doesn't do ("download Blinkit" against Blinkit's ordering script), or for the
     * opposite of what it does ("turn on" against a "turn off" script), never picks it.
     */
    private fun fits(asked: Set<String>, r: Recipe): Boolean {
        val covers = book.covers.getValue(r.id)
        val exclusive = book.common.concepts.filterValues { it.exclusive }.keys
        if (asked.any { it in exclusive && it !in covers }) return false
        return book.common.opposites.none { pair ->
            pair.size == 2 && (pair[0] in asked && pair[1] in covers && pair[0] !in covers ||
                pair[1] in asked && pair[0] in covers && pair[1] !in covers)
        }
    }

    /**
     * True if [text] carries one of the shared negations ("not ", "why", "नहीं"…) outside this
     * recipe's own phrases, so "do not disturb" can still start the Do Not Disturb recipe while
     * "do not disturb is not turning off" goes to the model.
     */
    private fun negated(text: String, r: Recipe): Boolean {
        var rest = text
        for (phrase in book.triggers.getValue(r.id).flatMap { t -> t.all.flatten() }) {
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
        val open = canOpen
        // The app isn't on this phone: hunting for its icon would never end. Offer its Play Store page.
        if (open != null && storeApp(recipe) && recipe.apps.none(open)) storePage(recipe, req)?.let { return it }
        // At the start of a task, or from the home screen, Saathi opens the app itself. Mid-task in
        // another app (a share sheet, say) it never pulls the senior away; it says where to go.
        val pkg = recipe.apps.firstOrNull { it != ANY_APP && open?.invoke(it) == true }
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

    /**
     * "Download Blinkit", "I want to install WhatsApp": an app named alongside a download word. Saathi
     * opens the app if it is already here, or its Play Store page if not, rather than starting that
     * app's own task. A safety tip ("someone asked me to install an app") still speaks first.
     */
    private fun download(said: String, req: TurnRequest): Guide? {
        val open = canOpen ?: return null
        val text = ScreenText.norm(said)
        if (DOWNLOAD_WORDS.none { contains(text, it, wordStart = false) }) return null
        if (UNINSTALL_WORDS.any { contains(text, it, wordStart = false) }) return null
        if (match(said, req.app)?.anyApp == true) return null
        val recipe = book.recipes.firstOrNull { !it.anyApp && storeApp(it) && names(it, text) } ?: return null
        val installed = recipe.apps.firstOrNull(open)
        if (installed == null) return storePage(recipe, req)
        val say = line(ALREADY, req.language).replace("{app}", recipe.appLabel)
        return Guide(say = say, status = Status.DONE, action = Action.OPEN_APP, actionArg = installed, expect = "download ${recipe.id}/already")
    }

    /** Opens [recipe]'s app's page in the Play Store, and hands over to the Play Store install recipe. */
    private fun storePage(recipe: Recipe, req: TurnRequest): Guide? {
        if (canOpen?.invoke(PLAY_STORE) != true) return null
        val say = line(NOT_INSTALLED, req.language).replace("{app}", recipe.appLabel)
        if (repeats(req.history, say) >= OUTSIDE_REPEATS) return null
        return Guide(
            say = say,
            goal = STORE_GOAL,
            action = Action.OPEN_STORE_PAGE,
            actionArg = recipe.apps.first(),
            expect = "download ${recipe.id}/store",
        )
    }

    /** True if [text] names [recipe]'s app: its launcher names, or a trigger group that is its name in each language. */
    private fun names(recipe: Recipe, text: String): Boolean {
        fun compact(s: String) = ScreenText.norm(s).replace(" ", "")
        val label = compact(recipe.appLabel)
        val nameGroups = recipe.triggers.flatMap { it.all }.filter { g -> g.any { compact(it) == label } }
        // "install" starts with "insta", in every script (इंस्टाल, இன்ஸ்டால்): the download words
        // themselves never name an app.
        val rest = text.split(' ').filterNot { w -> DOWNLOAD_STEMS.any { w.startsWith(it) } }.joinToString(" ")
        return (recipe.appNames + recipe.appLabel + nameGroups.flatten()).any { heardName(rest, ScreenText.norm(it)) }
    }

    private fun line(lines: Map<String, String>, language: String) = lines[language] ?: lines.getValue("en-IN")

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

        const val PLAY_STORE = "com.android.vending"
        /** The goal of shared/recipes/playstore-install.json, which runs on an app's Play Store page. */
        const val STORE_GOAL = "download an app from the Play Store"

        /**
         * Packages that come with the phone (and differ by maker), never "missing, go download it":
         * a phone whose camera is com.lava.camera still has a camera.
         */
        private val SYSTEM_PREFIXES = listOf(
            "com.android.", "com.google.android.", "com.samsung.", "com.sec.", "com.miui.", "com.coloros.",
            "com.oppo.", "com.vivo.", "com.oneplus.", "com.motorola.", "com.transsion.",
        )

        fun storeApp(r: Recipe) = !r.anyApp && r.apps.none { p -> SYSTEM_PREFIXES.any { p.startsWith(it) } }

        private val DOWNLOAD_WORDS = listOf(
            "download", "install", "डाउनलोड", "इंस्टॉल", "इनस्टॉल", "इंस्टाल", "டவுன்லோட்", "இன்ஸ்டால்", "நிறுவ",
            "డౌన్‌లోడ్", "ఇన్‌స్టాల్", "ಡೌನ್‌ಲೋಡ್", "ಇನ್‌ಸ್ಟಾಲ್", "ഡൗൺലോഡ്", "ഇൻസ്റ്റാൾ",
        )
        private val DOWNLOAD_STEMS by lazy { DOWNLOAD_WORDS.map(ScreenText::norm) }

        /**
         * True if [name] starts a word of [text] ("instagram", "इंस्टाग्राम को"), arrives split or a
         * letter off ("blink it", "whatsap"), or, for a Latin name, has the same consonants with
         * the vowels misheard: recognisers hear "blanket" for Blinkit.
         */
        fun heardName(text: String, name: String): Boolean {
            if (name.replace(" ", "").length < 3) return false
            if (contains(text, name)) return true
            if (name.any { !(it in 'a'..'z' || it == ' ' || it == '-') }) return false
            val compact = name.replace(" ", "").replace("-", "")
            val bones = consonants(compact)
            return bones.length >= 4 && text.split(' ').any { w ->
                w.length >= 5 && w[0] == compact[0] && w.all { it in 'a'..'z' } && consonants(w) == bones
            }
        }

        private fun consonants(w: String) = w.filter { it !in "aeiouy" }

        private val UNINSTALL_WORDS = listOf("uninstall", "अनइंस्टॉल", "அன்இன்ஸ்டால்", "అన్‌ఇన్‌స్టాల్", "ಅನ್‌ಇನ್‌ಸ್ಟಾಲ್", "അൺഇൻസ്റ്റാൾ")

        /** Hindi is feminine: the voice is a woman's. */
        val NOT_INSTALLED = mapOf(
            "hi-IN" to "'{app}' अभी इस फ़ोन में नहीं है। Play Store में उसका पेज खोल रही हूँ।",
            "en-IN" to "'{app}' is not on this phone yet. Opening its page in Play Store.",
            "ta-IN" to "'{app}' இந்த போனில் இன்னும் இல்லை. Play Store-ல் அதன் பக்கத்தைத் திறக்கிறேன்.",
            "te-IN" to "'{app}' ఈ ఫోన్‌లో ఇంకా లేదు. Play Store లో దాని పేజీ తెరుస్తున్నాను.",
            "kn-IN" to "'{app}' ಈ ಫೋನ್‌ನಲ್ಲಿ ಇನ್ನೂ ಇಲ್ಲ. Play Store ನಲ್ಲಿ ಅದರ ಪುಟ ತೆರೆಯುತ್ತಿದ್ದೇನೆ.",
            "ml-IN" to "'{app}' ഈ ഫോണിൽ ഇതുവരെ ഇല്ല. Play Store-ൽ അതിന്റെ പേജ് തുറക്കുന്നു.",
        )
        val ALREADY = mapOf(
            "hi-IN" to "'{app}' पहले से इस फ़ोन में है। उसे खोल रही हूँ।",
            "en-IN" to "'{app}' is already on this phone. Opening it for you.",
            "ta-IN" to "'{app}' ஏற்கனவே இந்த போனில் இருக்கிறது. அதைத் திறக்கிறேன்.",
            "te-IN" to "'{app}' ఇప్పటికే ఈ ఫోన్‌లో ఉంది. దాన్ని తెరుస్తున్నాను.",
            "kn-IN" to "'{app}' ಈಗಾಗಲೇ ಈ ಫೋನ್‌ನಲ್ಲಿದೆ. ಅದನ್ನು ತೆರೆಯುತ್ತಿದ್ದೇನೆ.",
            "ml-IN" to "'{app}' ഇതിനകം ഈ ഫോണിലുണ്ട്. അത് തുറക്കുന്നു.",
        )

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
            if (phrase.last().isWhitespace()) return found(text, p, wordStart, wordEnd = true) || wordStart && heardAs(text, p)
            if (found(text, p, wordStart)) return true
            val s = stem(p)
            if (s != p && found(text, s, wordStart)) return true
            return wordStart && heardAs(text, p)
        }

        /**
         * Speech recognisers split and misspell names: "blink it" for Blinkit, "whatsap", "youtub".
         * For a Latin phrase, spaces don't count, and a long enough word may be a letter or two off.
         * Indian scripts are left exact: recognisers spell them consistently, and short words there
         * differ by one sign in meaning.
         */
        private fun heardAs(text: String, p: String): Boolean {
            if (p.any { !(it in 'a'..'z' || it == ' ' || it == '-') }) return false
            val compact = p.replace(" ", "").replace("-", "")
            if (compact.length >= 5 && joinsTo(text, compact)) return true
            // Five-letter words are too close to each other to forgive a slip ("prime" and "prize").
            if (' ' in p || compact.length < 6) return false
            val allowed = if (compact.length >= 9) 2 else 1
            return text.split(' ').any { w ->
                w.length >= 5 && w.all { it in 'a'..'z' } && kotlin.math.abs(w.length - compact.length) <= allowed &&
                    w[0] == compact[0] && distance(w, compact) <= allowed
            }
        }

        /**
         * True if some run of whole words in [text], joined without spaces, is exactly [compact]:
         * "blink it" is "blinkit", but "uninstall" never contains "install".
         */
        private fun joinsTo(text: String, compact: String): Boolean {
            val words = text.split(' ', '-').filter { it.isNotEmpty() }
            for (start in words.indices) {
                val acc = StringBuilder()
                for (end in start until words.size) {
                    acc.append(words[end])
                    if (acc.length >= compact.length) {
                        if (acc.toString() == compact) return true
                        break
                    }
                }
            }
            return false
        }

        /** True if [n] appears in [p] as whole words, not as the start of a longer word. */
        fun wholeWords(p: String, n: String): Boolean = n.isNotEmpty() && found(p, n, wordStart = true, wordEnd = true)

        /** Levenshtein edit distance. */
        private fun distance(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1)
                cur[0] = i
                for (j in 1..b.length) {
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                }
                prev = cur
            }
            return prev[b.length]
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
