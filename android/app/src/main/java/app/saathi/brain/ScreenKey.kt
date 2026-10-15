package app.saathi.brain

import app.saathi.capture.ScreenText
import app.saathi.net.TurnRequest
import java.security.MessageDigest

/** Turns a screen plus the task so far into a cache key: same key, same next step. */
object ScreenKey {
    /** Below this many labelled elements the model is mostly reading the screenshot, not the tree. */
    const val MIN_LABELLED = 5
    /** Bounds are rounded to this fraction of the screen so a pixel of layout jitter still matches. */
    private const val GRID = 50

    fun informative(tree: String): Boolean = ScreenText.parse(tree).labelled >= MIN_LABELLED

    /** The tree without the noise: element ids, roles, text and flags, with bounds on a coarse grid. */
    fun signature(tree: String, screenW: Int, screenH: Int): String {
        val w = screenW.coerceAtLeast(1)
        val h = screenH.coerceAtLeast(1)
        return ScreenText.parse(tree).nodes.joinToString("\n") { n ->
            val b = n.box
            "${n.id} ${n.role} ${n.label} ${b.left * GRID / w},${b.top * GRID / h},${b.right * GRID / w},${b.bottom * GRID / h} ${n.flags.sorted().joinToString(",")}"
        }
    }

    /**
     * Everything the next step depends on: language, app, goal, what was just said, the screen,
     * and the last two steps (the same screen can need a different step once a button was pressed).
     */
    fun turnKey(req: TurnRequest): String {
        val parts = listOf(
            "v1",
            req.language,
            req.app,
            ScreenText.norm(req.goal),
            ScreenText.norm(req.utterance.orEmpty()),
            signature(req.tree, req.screenW, req.screenH),
            req.history.takeLast(2).joinToString(";") { "${it.label}|${it.status}" },
        )
        return sha256(parts.joinToString("\u0001"))
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
