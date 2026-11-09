package app.saathi

import app.saathi.capture.Box
import app.saathi.capture.RawNode
import app.saathi.capture.TreeSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeSerializerTest {
    private val full = Box(0, 0, 1080, 2400)

    @Test fun `emits labelled and clickable nodes with ids and bounds`() {
        val root = RawNode(
            bounds = full,
            children = listOf(
                RawNode(className = "android.widget.TextView", text = "Fresh milk", bounds = Box(40, 300, 600, 360)),
                RawNode(className = "android.widget.Button", text = "ADD", bounds = Box(800, 300, 1000, 380), clickable = true),
                RawNode(className = "android.view.View", bounds = Box(0, 0, 10, 10)), // nothing to say, not interactive
            ),
        )
        val t = TreeSerializer.serialize(root, 1080, 2400)
        assertEquals(
            "n1 TextView \"Fresh milk\" [40,300,600,360]\nn2 Button \"ADD\" [800,300,1000,380] click",
            t.text,
        )
        assertEquals(Box(800, 300, 1000, 380), t.boxes["n2"])
    }

    @Test fun `typed text and passwords never leave the device`() {
        val root = RawNode(
            bounds = full,
            children = listOf(
                RawNode(text = "9876543210", hint = "Mobile number", editable = true, bounds = Box(0, 100, 1080, 200)),
                RawNode(text = "hunter2", editable = true, password = true, bounds = Box(0, 200, 1080, 300)),
                RawNode(text = "secret note", editable = true, bounds = Box(0, 300, 1080, 400)),
            ),
        )
        val text = TreeSerializer.serialize(root, 1080, 2400).text
        assertFalse(text.contains("9876543210"))
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("secret note"))
        assertTrue(text.contains("\"Mobile number\""))
        assertTrue(text.contains("\"(hidden)\""))
        assertTrue(text.contains("\"(text box)\""))
    }

    @Test fun `one-time codes shown as text are masked`() {
        assertEquals("Your OTP is •••• for login", TreeSerializer.redactCodes("Your OTP is 482913 for login"))
        assertEquals("ओटीपी •••• है", TreeSerializer.redactCodes("ओटीपी 4829 है"))
        assertEquals("Order total 249", TreeSerializer.redactCodes("Order total 249"))
    }

    @Test fun `OTP context masks sibling and individual digit views`() {
        for (digits in listOf(listOf("482913"), listOf("4", "8", "2", "9", "1", "3"), listOf("४८२९१३"))) {
            val root = RawNode(bounds = full, children = (digits + "Your OTP").mapIndexed { i, text ->
                RawNode(text = text, bounds = Box(0, i * 100, 500, i * 100 + 80))
            })
            val tree = TreeSerializer.serialize(root, 1080, 2400)
            for (digit in digits) assertFalse(tree.text.contains("\"$digit\""))
            assertTrue(tree.text.contains("••••"))
            assertTrue(tree.omitScreenshot)
        }
    }

    @Test fun `privacy checks happen before label truncation and output limits`() {
        val rows = (0 until TreeSerializer.MAX_NODES).map {
            RawNode(text = if (it == 0) "482913" else "row", bounds = Box(0, 100, 500, 180))
        }
        val root = RawNode(bounds = full, children = rows + RawNode(
            text = "x".repeat(100) + " OTP", bounds = Box(0, 200, 500, 280),
        ))
        val tree = TreeSerializer.serialize(root, 1080, 2400)
        assertFalse(tree.text.contains("482913"))
        assertTrue(tree.omitScreenshot)
    }

    @Test fun `screenshots cannot expose redacted inputs even without sensitive keywords`() {
        for (field in listOf(
            RawNode(text = "9876543210", hint = "Mobile number", editable = true, bounds = full),
            RawNode(text = "private message", desc = "private message", editable = true, bounds = full),
            RawNode(text = "private child", editable = true, bounds = full, children = listOf(
                RawNode(text = "private child", bounds = full),
            )),
            RawNode(text = "secret", password = true, bounds = full),
        )) {
            val tree = TreeSerializer.serialize(RawNode(bounds = full, children = listOf(field)), 1080, 2400)
            assertTrue(tree.omitScreenshot)
            assertFalse(tree.text.contains(field.text!!))
        }
        val lateField = RawNode(bounds = full, children = List(TreeSerializer.MAX_NODES) {
            RawNode(text = "row", bounds = full)
        } + RawNode(text = "private", editable = true, bounds = full))
        assertTrue(TreeSerializer.serialize(lateField, 1080, 2400).omitScreenshot)
    }

    @Test fun `ordinary screens keep screenshots and unrelated hidden fields do not block them`() {
        val tree = TreeSerializer.serialize(RawNode(bounds = full, children = listOf(
            RawNode(text = "Shopping", bounds = full),
            RawNode(text = "Order total 249", bounds = full),
            RawNode(editable = true, visible = false, bounds = full),
            RawNode(editable = true, bounds = Box(0, 3000, 500, 3080)),
        )), 1080, 2400)
        assertFalse(tree.omitScreenshot)
        assertTrue(tree.text.contains("249"))
    }

    @Test fun `web views never send a picture before or after their document is ready`() {
        for (className in listOf("android.webkit.WebView", "org.mozilla.geckoview.GeckoView")) {
            val loading = RawNode(className = className, clickable = true, bounds = full)
            val first = TreeSerializer.serialize(loading, 1080, 2400)
            assertTrue(first.omitScreenshot)
            assertTrue(first.opaqueWebView)
            val ready = TreeSerializer.serialize(loading.copy(children = listOf(
                RawNode(text = "Sample portal", bounds = full),
                RawNode(text = "PRIVATE-WEB-5678", hint = "Reference", editable = true, bounds = full),
            )), 1080, 2400)
            assertTrue(ready.omitScreenshot)
            assertFalse(ready.opaqueWebView)
            assertFalse(ready.text.contains("PRIVATE-WEB-5678"))
            assertTrue(ready.text.contains("Reference"))
            val noFieldsYet = TreeSerializer.serialize(loading.copy(children = listOf(
                RawNode(text = "Portal loaded", bounds = full),
            )), 1080, 2400)
            assertTrue(noFieldsYet.omitScreenshot) // a field may be painted before its new node
        }
        assertFalse(TreeSerializer.serialize(RawNode(className = "android.view.View", bounds = full), 1080, 2400).omitScreenshot)
    }

    @Test fun `clickable containers borrow their children's text`() {
        val root = RawNode(
            bounds = full,
            children = listOf(
                RawNode(
                    clickable = true, bounds = Box(0, 2200, 1080, 2400),
                    children = listOf(
                        RawNode(text = "1 item", bounds = Box(20, 2220, 300, 2260)),
                        RawNode(text = "View cart", bounds = Box(700, 2220, 1000, 2260)),
                    ),
                ),
            ),
        )
        val first = TreeSerializer.serialize(root, 1080, 2400).text.lines().first()
        assertEquals("n1 View \"1 item View cart\" [0,2200,1080,2400] click", first)
    }

    @Test fun `invisible and off-screen nodes are skipped and output is capped`() {
        val many = (0 until 500).map { RawNode(text = "row $it", bounds = Box(0, it, 100, it + 1)) }
        val root = RawNode(
            bounds = full,
            children = listOf(
                RawNode(text = "hidden", visible = false, bounds = Box(0, 0, 100, 100)),
                RawNode(text = "below", bounds = Box(0, 3000, 100, 3100)),
            ) + many,
        )
        val t = TreeSerializer.serialize(root, 1080, 2400)
        assertFalse(t.text.contains("hidden"))
        assertFalse(t.text.contains("below"))
        assertEquals(TreeSerializer.MAX_NODES, t.boxes.size)
    }
}
