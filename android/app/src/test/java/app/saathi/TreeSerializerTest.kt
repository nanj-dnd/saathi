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
