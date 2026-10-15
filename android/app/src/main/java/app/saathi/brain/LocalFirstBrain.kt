package app.saathi.brain

import app.saathi.capture.ScreenText
import app.saathi.net.Danger
import app.saathi.net.Guide
import app.saathi.net.Source
import app.saathi.net.Status
import app.saathi.net.TurnRequest
import app.saathi.net.TurnResponse
import app.saathi.recipes.RecipeBook
import app.saathi.recipes.RecipeEngine
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
) : Brain {

    override suspend fun turn(request: TurnRequest): TurnResponse {
        val start = clock()
        fun local(guide: Guide, source: String): TurnResponse {
            log("turn ${request.app} via $source: ${guide.expect.ifBlank { guide.label }}")
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

        val risky = ScreenSafety.scamCue(request.tree) || ScreenSafety.scamCue(request.utterance.orEmpty())
        if (!risky) RecipeEngine(recipes()).guide(request)?.let { return local(it, Source.RECIPE) }

        val key = if (!risky && cacheable(request)) ScreenKey.turnKey(request) else null
        if (key != null) cache.get(key)?.takeIf { fits(it, request) }?.let { return local(it, Source.CACHE) }

        val response = try {
            remote.turn(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline: an old answer for this exact screen is still better than "no internet".
            key?.let { cache.get(it, allowStale = true) }?.takeIf { fits(it, request) }?.let { return local(it, Source.CACHE) }
            // A scam-looking turn skipped the recipes for the model; without it, a scripted warning
            // is still far better than silence.
            if (risky) scamWarning(request)?.let { return local(it, Source.RECIPE) }
            throw e
        }
        if (key != null && worthKeeping(response.guide)) cache.put(key, response.guide)
        log("turn ${request.app} via ${response.source}: ${response.guide.label}")
        return response
    }

    /** The scripted scam warning that fits what was said, ignoring ordinary scripts that might outrank it in this app. */
    private fun scamWarning(request: TurnRequest): Guide? {
        val book = recipes()
        val warnings = book.recipes.filter { r -> r.steps.isNotEmpty() && r.steps.all { it.danger == Danger.SCAM } }
        return RecipeEngine(RecipeBook(book.common, warnings)).guide(request)
    }

    internal companion object {
        /** Screens whose answer is safe to reuse: enough text to identify them, and no codes on them. */
        fun cacheable(req: TurnRequest): Boolean = ScreenKey.informative(req.tree) && !ScreenSafety.sensitive(req.tree)

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
