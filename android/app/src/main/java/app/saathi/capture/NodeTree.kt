package app.saathi.capture

/** Screen rectangle in physical pixels, same space as AccessibilityNodeInfo.getBoundsInScreen. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    val isEmpty get() = width <= 0 || height <= 0

    fun clip(w: Int, h: Int) = Box(left.coerceIn(0, w), top.coerceIn(0, h), right.coerceIn(0, w), bottom.coerceIn(0, h))

    companion object {
        fun around(x: Int, y: Int, radius: Int) = Box(x - radius, y - radius, x + radius, y + radius)
    }
}

/**
 * Plain copy of the parts of an AccessibilityNodeInfo we use, so serialisation and redaction
 * can be unit-tested on the JVM without a device.
 */
data class RawNode(
    val className: String? = null,
    val text: String? = null,
    val desc: String? = null,
    val hint: String? = null,
    val bounds: Box,
    val visible: Boolean = true,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val password: Boolean = false,
    val scrollable: Boolean = false,
    val checked: Boolean? = null,
    val children: List<RawNode> = emptyList(),
)

class SerializedTree(val text: String, val boxes: Map<String, Box>)

object TreeSerializer {
    const val MAX_NODES = 220
    private const val MAX_LABEL = 80

    // Numbers near these words are one-time codes; keep them out of what leaves the phone.
    private val codeWords = Regex(
        "otp|one[- ]time|pin|passcode|verification|cvv|ओटीपी|पिन|ஓடிபி|ఓటీపీ|ಒಟಿಪಿ|ഒടിപി",
        RegexOption.IGNORE_CASE,
    )
    private val digitRun = Regex("\\d[\\d ]{2,10}\\d")

    fun serialize(root: RawNode, screenW: Int, screenH: Int): SerializedTree {
        val lines = StringBuilder()
        val boxes = LinkedHashMap<String, Box>()
        var next = 1

        fun visit(node: RawNode) {
            if (boxes.size >= MAX_NODES) return
            if (!node.visible) return
            val box = node.bounds.clip(screenW, screenH)
            if (box.isEmpty) return

            val own = labelOf(node)
            val label = if (own.isEmpty() && node.clickable) descendantLabel(node) else own
            val emit = label.isNotEmpty() || node.clickable || node.editable || node.scrollable
            if (emit) {
                val id = "n${next++}"
                boxes[id] = box
                lines.append(id).append(' ').append(role(node)).append(" \"").append(label.replace("\"", "'"))
                    .append("\" [").append(box.left).append(',').append(box.top).append(',')
                    .append(box.right).append(',').append(box.bottom).append(']')
                flags(node).forEach { lines.append(' ').append(it) }
                lines.append('\n')
            }
            node.children.forEach(::visit)
        }

        visit(root)
        return SerializedTree(lines.toString().trimEnd(), boxes)
    }

    /** What we are willing to send for this node. Typed text and passwords never leave the device. */
    fun labelOf(node: RawNode): String {
        val raw = when {
            node.password -> "(hidden)"
            node.editable -> node.hint ?: node.desc ?: "(text box)"
            else -> node.text ?: node.desc ?: ""
        }
        return redactCodes(clean(raw))
    }

    fun redactCodes(s: String): String =
        if (codeWords.containsMatchIn(s)) digitRun.replace(s, "••••") else s

    private fun descendantLabel(node: RawNode): String {
        val parts = ArrayList<String>(3)
        fun walk(n: RawNode) {
            for (c in n.children) {
                if (parts.size >= 3) return
                if (!c.visible) continue
                val l = labelOf(c)
                if (l.isNotEmpty()) parts += l else walk(c)
            }
        }
        walk(node)
        return clean(parts.joinToString(" "))
    }

    private fun clean(s: String): String {
        val collapsed = s.replace(Regex("\\s+"), " ").trim()
        return if (collapsed.length > MAX_LABEL) collapsed.take(MAX_LABEL - 1) + "…" else collapsed
    }

    private fun role(node: RawNode): String {
        val simple = node.className?.substringAfterLast('.')?.takeIf { it.isNotBlank() } ?: "View"
        return when {
            node.editable -> "EditText"
            else -> simple
        }
    }

    private fun flags(node: RawNode): List<String> = buildList {
        if (node.clickable) add("click")
        if (node.editable) add("edit")
        if (node.scrollable) add("scroll")
        if (node.password) add("password")
        when (node.checked) {
            true -> add("checked")
            false -> add("unchecked")
            null -> {}
        }
    }
}
