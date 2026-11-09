package app.saathi.brain

import app.saathi.capture.ScreenText
import app.saathi.apps.AppCatalog
import app.saathi.net.Action
import app.saathi.net.Danger
import app.saathi.net.Guide
import app.saathi.net.Source
import app.saathi.net.Status
import app.saathi.net.TurnRequest
import app.saathi.net.TurnResponse
import app.saathi.recipes.RecipeBook
import app.saathi.recipes.RecipeEngine
import app.saathi.recipes.anyApp
import app.saathi.safety.ScreenSafety
import app.saathi.session.Brain
import kotlinx.coroutines.CancellationException

/**
 * Answers each turn as cheaply as it safely can, and calls [remote] (the Worker, then Claude)
 * only when nothing on the phone knows the answer:
 *
 * 1. A screen the phone could not capture (PIN, OTP, payment) gets a fixed "do this yourself" line.
 * 2. A scripted recipe, when one fits what was said and what is on screen.
 * 3. An answer this phone already got for the same screen, goal and progress.
 * 4. The Worker, whose answer is then saved for step 3.
 *
 * Steps 1–3 work offline. A screen that shows signs of a scam skips 2 and 3 so the model, which
 * knows how to warn, always sees it; if the model can't be reached, a scripted scam warning that
 * fits is still given.
 */
class LocalFirstBrain(
    private val recipes: () -> RecipeBook,
    private val cache: TurnCache,
    private val remote: Brain,
    /** The "do this part yourself" line in the given language. */
    private val sensitiveLine: (language: String) -> String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
    /** True if this phone can open a package directly; recipes then open apps themselves. Null: unknown. */
    private val canOpen: ((String) -> Boolean)? = null,
    private val resolveApp: ((label: String) -> String?)? = null,
    private val unreadableLine: ((language: String) -> String)? = null,
) : Brain {

    override suspend fun turn(request: TurnRequest): TurnResponse {
        val start = clock()
        fun local(guide: Guide, source: String): TurnResponse {
            log("turn ${request.app} via $source: ${guide.expect.ifBlank { guide.label }} (${clock() - start}ms)")
            return TurnResponse(guide, mapOf("local" to clock() - start), source)
        }

        if (request.secureScreen) {
            return local(
                Guide(
                    say = sensitiveLine(request.language),
                    goal = request.goal,
                    status = Status.USER_MUST_DO,
                    danger = Danger.SENSITIVE,
                    expect = "secure screen",
                ),
                Source.LOCAL,
            )
        }

        if (ScreenText.parse(request.tree).labelled == 0 && request.screenshotB64.isNullOrBlank() && unreadableLine != null) {
            return local(Guide(
                say = unreadableLine.invoke(request.language), goal = request.goal,
                status = Status.CANNOT_HELP, expect = "unreadable screen",
            ), Source.LOCAL)
        }

        val risky = ScreenSafety.scamCue(request.tree) || ScreenSafety.scamCue(request.utterance.orEmpty())
        if (!risky) {
            val book = recipes()
            val engine = RecipeEngine(book, canOpen)
            val namedApps = AppCatalog(request.installedApps).mentioned(request.utterance ?: request.goal)
            val active = engine.active(request)
            // "Send a photo in Signal" must not start a WhatsApp/Gallery script just because
            // it shares the verb. Any-app safety warnings still take precedence.
            val elsewhere = active != null && request.history.isNotEmpty() && !RecipeEngine.isLauncher(request.app) &&
                request.app !in active.apps && active.steps.none { request.app in it.apps }
            val unfamiliarForeground = request.appName.isNotBlank() && !RecipeEngine.isLauncher(request.app) &&
                book.recipes.none { request.app in it.apps }
            val preferCurrent = unfamiliarForeground && namedApps.isEmpty() &&
                (request.utterance != null || request.history.isEmpty()) && active?.apps?.contains("com.android.settings") != true
            if (active == null || active.anyApp || (!elsewhere && !preferCurrent && namedApps.all { it.packageName in active.apps })) {
                engine.guide(request)?.let { return local(it, Source.RECIPE) }
            }
            log("no recipe step: matched ${active?.id ?: "none"} in ${request.app}")
        } else {
            log("scam cue on screen or in words: straight to the model")
        }

        val key = if (!risky && cacheable(request)) ScreenKey.turnKey(request) else null
        if (key != null) cache.get(key)?.takeIf { fits(it, request) }?.let { return local(checked(it, request.language), Source.CACHE) }

        val response = try {
            remote.turn(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline: an old answer for this exact screen is still better than "no internet".
            key?.let { cache.get(it, allowStale = true) }?.takeIf { fits(it, request) }?.let { return local(checked(it, request.language), Source.CACHE) }
            // A scam-looking turn skipped the recipes for the model; without it, a scripted warning
            // is still far better than silence.
            if (risky) scamWarning(request)?.let { return local(it, Source.RECIPE) }
            throw e
        }
        if (key != null && worthKeeping(response.guide)) cache.put(key, response.guide)
        val guide = checked(response.guide, request.language)
        log("turn ${request.app} via ${response.source}: ${guide.label} | ${guide.say}${if (guide.action.isNotEmpty()) " [${guide.action} ${guide.actionArg}]" else ""}")
        return response.copy(guide = guide)
    }

    /**
     * An action the model chose, made safe for this phone. An app it named that isn't installed (or
     * a package it got wrong) becomes that app's Play Store page; without a Play Store, the senior
     * hears where to look. Unknown actions are dropped. With [canOpen] unknown, nothing changes.
     */
    private fun checked(g: Guide, language: String): Guide {
        val open = canOpen ?: return g
        if (g.action == Action.OPEN_APP && !open(g.actionArg)) {
            val resolved = resolveApp?.invoke(g.label)
            if (resolved != null && open(resolved)) return g.copy(actionArg = resolved)
            // The catalogue is available: a missing or ambiguous match is not a reason to
            // install an app the model guessed. The senior can open the intended app instead.
            if (resolveApp != null) return findInstead(g, language)
        }
        return when (g.action) {
            "", Action.SCROLL_DOWN -> g
            Action.OPEN_APP -> when {
                open(g.actionArg) -> g
                open(RecipeEngine.PLAY_STORE) && g.label.isNotBlank() -> g.copy(
                    action = Action.OPEN_STORE_PAGE,
                    say = (RecipeEngine.NOT_INSTALLED[language] ?: RecipeEngine.NOT_INSTALLED.getValue("en-IN")).replace("{app}", g.label),
                    goal = RecipeEngine.STORE_GOAL,
                )
                else -> findInstead(g, language)
            }
            Action.OPEN_STORE_PAGE -> if (open(RecipeEngine.PLAY_STORE)) g.copy(goal = RecipeEngine.STORE_GOAL) else findInstead(g, language)
            else -> g.copy(action = "", actionArg = "")
        }
    }

    private fun findInstead(g: Guide, language: String): Guide {
        val line = recipes().common.lines["drawer"]?.get(language)
        val say = if (line != null && g.label.isNotBlank()) line.replace("{app}", g.label) else g.say
        return g.copy(action = "", actionArg = "", say = say, status = if (g.status == Status.DONE) Status.NEXT_STEP else g.status)
    }

    /** The scripted scam warning that fits what was said, ignoring ordinary scripts that might outrank it in this app. */
    private fun scamWarning(request: TurnRequest): Guide? {
        return RecipeEngine(recipes().scamWarnings).guide(request)
    }

    internal companion object {
        /** Screens whose answer is safe to reuse: enough text to identify them, and no codes on them. */
        fun cacheable(req: TurnRequest): Boolean = ScreenKey.informative(req.tree) && !ScreenSafety.sensitive(req.tree) &&
            !AppCatalog(req.installedApps).ambiguousSelection(listOfNotNull(req.utterance, req.goal).joinToString(" "), req.app)

        /**
         * Only plain steps are kept. Warnings and sensitive screens are always re-checked, questions
         * depend on the conversation, and a point target means the model read the picture, which
         * the key does not cover.
         */
        fun worthKeeping(g: Guide): Boolean =
            g.say.isNotBlank() &&
                g.danger == Danger.NONE &&
                g.targetKind != "point" &&
                g.status in setOf(Status.NEXT_STEP, Status.DONE)

        /** A saved node target must still be on screen with the same text. */
        fun fits(g: Guide, req: TurnRequest): Boolean {
            if (g.targetKind != "node") return true
            val node = ScreenText.parse(req.tree).nodes.firstOrNull { it.id == g.nodeId } ?: return false
            return g.label.isBlank() || node.label == g.label || node.label.contains(g.label)
        }
    }
}
