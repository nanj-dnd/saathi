package app.saathi.capture

import java.text.Normalizer
import java.util.Locale

/** One line of the serialized tree: `n12 Button "ADD" [l,t,r,b] click`. */
data class TreeNode(val id: String, val role: String, val label: String, val box: Box, val flags: Set<String>) {
    val clickable get() = "click" in flags || "edit" in flags
    val hasState get() = "checked" in flags || "unchecked" in flags
}

/** Reads the text [TreeSerializer] produced, for the checks that run on the phone without the model. */
class ScreenText(val nodes: List<TreeNode>) {
    private val normLabels = nodes.map { norm(it.label) }

    /** True if any element's text contains [needle], or equals it when [exact]. */
    fun has(needle: String, exact: Boolean = false): Boolean {
        val n = norm(needle)
        if (n.isEmpty()) return false
        return normLabels.any { if (exact) it == n else n in it }
    }

    /**
     * The clickable element that best matches one of [labels]: an exact match beats one that starts
     * with the label, which beats one that merely contains it; ties go to the earlier label, then
     * to the element higher in the tree.
     */
    fun find(labels: List<String>, exact: Boolean = false): TreeNode? {
        var best: TreeNode? = null
        var bestScore = Int.MAX_VALUE
        val wanted = labels.map(::norm).filter { it.isNotEmpty() }
        for ((i, node) in nodes.withIndex()) {
            val have = normLabels[i]
            if (!node.clickable || have.isEmpty()) continue
            for ((li, w) in wanted.withIndex()) {
                val rank = when {
                    have == w -> 0
                    exact -> continue
                    have.startsWith(w) -> 1
                    w in have -> 2
                    else -> continue
                }
                val score = rank * 1000 + li
                if (score < bestScore) {
                    bestScore = score
                    best = node
                }
            }
        }
        return best
    }

    /**
     * What to call [node] out loud when [find] picked it for [labels]. A Settings row's text is its
     * title plus summary ("Display & touch Dark theme, font size, touch"); the senior should only
     * hear the label they are looking for, written as the recipe writes it.
     */
    fun spokenLabel(node: TreeNode, labels: List<String>): String {
        val have = norm(node.label)
        if (labels.any { norm(it) == have }) return node.label
        return labels.firstOrNull { val w = norm(it); w.isNotEmpty() && have.startsWith(w) }
            ?: labels.firstOrNull { val w = norm(it); w.isNotEmpty() && w in have }
            ?: node.label
    }

    /** An on/off control: [row] is the labelled line, [toggle] the element that shows the state. */
    class Switch(val row: TreeNode, val toggle: TreeNode, val on: Boolean)

    /**
     * The switch on the row labelled with one of [labels] (whole label or its start). The state is
     * on the row itself in some apps, and on a separate unlabelled switch beside the text in others.
     */
    fun switchFor(labels: List<String>): Switch? {
        val wanted = labels.map(::norm).filter { it.isNotEmpty() }
        val rows = nodes.indices.mapNotNull { i ->
            val have = normLabels[i]
            val rank = wanted.withIndex().minOfOrNull { (li, w) ->
                when {
                    have == w -> li
                    have.startsWith("$w ") -> 1000 + li
                    else -> Int.MAX_VALUE
                }
            } ?: Int.MAX_VALUE
            if (rank == Int.MAX_VALUE) null else nodes[i] to rank
        }.sortedBy { it.second }.map { it.first }
        for (row in rows) {
            if (row.hasState) return Switch(row, row, "checked" in row.flags)
            val beside = nodes.firstOrNull { it.hasState && it.box.centerY in row.box.top..row.box.bottom }
            if (beside != null) return Switch(row, beside, "checked" in beside.flags)
        }
        return null
    }

    /** Elements with some visible text. A screen with very few is mostly a picture (game, video, map). */
    val labelled: Int get() = nodes.count { it.label.isNotBlank() }

    companion object {
        private val LINE = Regex("""^(n\d+) (\S+) "(.*)" \[(-?\d+),(-?\d+),(-?\d+),(-?\d+)](.*)$""")

        fun parse(tree: String): ScreenText = ScreenText(
            tree.lineSequence().mapNotNull { line ->
                val m = LINE.matchEntire(line.trim()) ?: return@mapNotNull null
                val (id, role, label, l, t, r, b, rest) = m.destructured
                TreeNode(id, role, label, Box(l.toInt(), t.toInt(), r.toInt(), b.toInt()), rest.trim().split(' ').filter { it.isNotEmpty() }.toSet())
            }.toList(),
        )

        /**
         * Lower-case, NFC and single spaces, so "बड़े" typed with or without the combined nukta, or
         * "Display  &  Touch", compare equal to how a recipe wrote them.
         */
        fun norm(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFC).lowercase(Locale.ROOT)
                .replace(JOINERS, "")
                .replace(DASHES, "-")
                .replace(SPACES, " ")
                .trim()

        // Speech recognisers add or drop zero-width joiners in Indic words ("స్క్రీన్‌షాట్").
        private val JOINERS = Regex("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]")
        // Android writes "Wi‑Fi" with a non-breaking hyphen and pads some labels with no-break spaces.
        private val DASHES = Regex("[\\u2010\\u2011\\u2012\\u2013]")
        private val SPACES = Regex("[\\s\\u00A0\\u202F\\u2007]+")
    }
}
