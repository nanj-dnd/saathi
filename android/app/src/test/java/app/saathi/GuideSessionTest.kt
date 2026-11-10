package app.saathi

import app.saathi.capture.Box
import app.saathi.net.Action
import app.saathi.net.Danger
import app.saathi.net.Guide
import app.saathi.net.Status
import app.saathi.net.TurnRequest
import app.saathi.net.TurnFailure
import app.saathi.net.TurnResponse
import app.saathi.session.Brain
import app.saathi.session.Ears
import app.saathi.session.Eyes
import app.saathi.session.GuideSession
import app.saathi.session.GuideState
import app.saathi.session.Hands
import app.saathi.session.Heard
import app.saathi.session.Lines
import app.saathi.session.Mouth
import app.saathi.session.Pointer
import app.saathi.session.ScreenEvent
import app.saathi.session.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class GuideSessionTest {
    private val target = Box(800, 300, 1000, 380)

    private class Fakes {
        val requests = mutableListOf<TurnRequest>()
        val said = mutableListOf<String>()
        val pointed = mutableListOf<Pair<Box?, String>>()
        val replies = ArrayDeque<Guide>()
        var failNext = false
        var replyDelay = 0L
        var speechDelay = 0L
        var heard: Heard = Heard.Words("ब्लिंकिट से दूध मंगाना है")
        var finished = false
        var calling = false
        val slowSaid = mutableListOf<String>()
        var tree = "n2 Button \"ADD\" [800,300,1000,380] click"
        var app = "com.grofers.customerapp"
        var secure = false
        var looks = 0

        val eyes = object : Eyes {
            override suspend fun look(): Snapshot {
                looks++
                return Snapshot(
                    app = app, screenshotB64 = "jpg", screenW = 1080, screenH = 2400,
                    tree = tree, boxes = mapOf("n2" to Box(800, 300, 1000, 380)), secure = secure,
                )
            }
        }
        /** Answers for the next listens in order; then [heard]. */
        val hearNext = ArrayDeque<Heard>()
        var anythingElse = ""
        val ears = object : Ears {
            override suspend fun listen() = hearNext.removeFirstOrNull() ?: heard
            override fun finish() { finished = true }
        }
        val mouth = object : Mouth {
            override suspend fun say(text: String, slow: Boolean) {
                said += text
                if (slow) slowSaid += text
                kotlinx.coroutines.delay(speechDelay)
            }
            override fun stop() {}
        }
        val pointer = object : Pointer {
            override fun state(state: GuideState) {}
            override fun point(target: Box?, caption: String, danger: String, done: Boolean) { pointed += target to caption }
            override fun clear() {}
        }
        val brain = object : Brain {
            override suspend fun turn(request: TurnRequest): TurnResponse {
                requests += request
                kotlinx.coroutines.delay(replyDelay)
                if (failNext) { failNext = false; throw IOException("down") }
                return TurnResponse(guide = replies.removeFirst())
            }
        }
        val lines = object : Lines {
            override val noSpeech = "no-speech"
            override val network = "network"
            override val micDenied = "mic"
            override val noSpeechAgain = "louder"
            override val micBusy = "mic-busy"
            override val inCall = "in-call"
            override val speechOffline = "speech-offline"
            override val speechUnavailable = "speech-unavailable"
            override val wait = "wait"
            override val busy = "busy"
            override val limit = "limit"
            override val scrolling = "scrolling"
            override val anythingElse get() = this@Fakes.anythingElse
            override val goodbye = "goodbye"
        }

        var scrolls = 0
        var opened = mutableListOf<String>()
        var handsWork = true
        val hands = object : Hands {
            override suspend fun scrollDown(): Boolean { scrolls++; return handsWork }
            override fun openApp(pkg: String): Boolean { opened += pkg; return handsWork }
            override fun openStorePage(pkg: String): Boolean { opened += "store:$pkg"; return handsWork }
        }

        fun session(scope: TestScope, mic: Boolean = true) = GuideSession(
            scope, eyes, ears, mouth, pointer, brain, { "hints" }, { "hi-IN" }, { lines }, { mic }, { calling }, hands,
        )
    }

    private fun step(say: String, status: String = Status.NEXT_STEP, goal: String = "order milk") =
        Guide(say = say, goal = goal, targetKind = "node", nodeId = "n2", label = "ADD", status = status)

    @Test fun `spoken turn points at the node and then watches`() = runTest {
        val f = Fakes().apply { replies += step("ADD दबाइए") }
        val s = f.session(this)
        s.onBubbleTap()
        advanceUntilIdle()
        assertEquals(GuideState.IDLE, s.state.value) // watch timed out after 2 minutes of virtual time
        assertEquals(listOf("ADD दबाइए"), f.said)
        assertEquals(target, f.pointed.single().first)
        val req = f.requests.single()
        assertEquals("ब्लिंकिट से दूध मंगाना है", req.utterance)
        assertEquals("hints", req.playbook)
        assertEquals("", req.goal)
    }

    @Test fun `a tap on the screen triggers the next step with goal and history`() = runTest {
        val f = Fakes().apply { replies += step("ADD दबाइए"); replies += step("View cart दबाइए") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        assertEquals(GuideState.WATCHING, s.state.value)

        s.onScreenEvent(ScreenEvent.CLICK)
        advanceTimeBy(ScreenEvent.CLICK.settleMs + GuideSession.SETTLE_STEP_MS + 1)
        runCurrent()

        assertEquals(2, f.requests.size)
        val second = f.requests[1]
        assertNull(second.utterance)
        assertEquals("order milk", second.goal)
        assertEquals(1, second.history.size)
        assertEquals(listOf("ADD दबाइए", "View cart दबाइए"), f.said)
    }

    @Test fun `Saathi scrolls by itself, says so, and goes straight to the next step`() = runTest {
        val f = Fakes().apply {
            replies += Guide(say = "slide up", goal = "order milk", action = Action.SCROLL_DOWN)
            replies += step("ADD दबाइए")
        }
        val s = f.session(this)
        s.ask("order milk")
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, f.scrolls)
        assertEquals(listOf("scrolling", "ADD दबाइए"), f.said)
        assertEquals(2, f.requests.size)
        assertEquals(GuideState.WATCHING, s.state.value)
    }

    @Test fun `Saathi opens the app itself and carries on inside it`() = runTest {
        val f = Fakes().apply {
            replies += Guide(say = "opening Blinkit", goal = "order milk", action = Action.OPEN_APP, actionArg = "com.grofers.customerapp")
            replies += step("ADD दबाइए")
        }
        val s = f.session(this)
        s.ask("order milk")
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("com.grofers.customerapp"), f.opened)
        assertEquals(listOf("opening Blinkit", "ADD दबाइए"), f.said)
    }

    @Test fun `when Saathi can't do it, the senior hears how to do it by hand`() = runTest {
        val f = Fakes().apply {
            handsWork = false
            replies += Guide(say = "slide up", goal = "order milk", action = Action.SCROLL_DOWN)
        }
        val s = f.session(this)
        s.ask("order milk")
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("slide up"), f.said)
        assertEquals(1, f.requests.size)
        assertEquals(GuideState.WATCHING, s.state.value)
    }

    private val fullScreen = (1..6).joinToString("\n") { "n$it TextView \"Item $it\" [0,${it * 100},500,${it * 100 + 80}]" } +
        "\nn2 Button \"ADD\" [800,300,1000,380] click"

    @Test fun `a scroll that changed nothing keeps the step without asking or speaking again`() = runTest {
        val f = Fakes().apply { tree = fullScreen; replies += step("ADD दबाइए") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        s.onScreenEvent(ScreenEvent.SCROLLED)
        advanceTimeBy(ScreenEvent.SCROLLED.settleMs + GuideSession.SETTLE_STEP_MS + 1)
        runCurrent()
        assertEquals(1, f.requests.size)
        assertEquals(listOf("ADD दबाइए"), f.said)
        assertEquals(2, f.pointed.size) // shown again after the look
        assertEquals(target, f.pointed.last().first)
        assertEquals(GuideState.WATCHING, s.state.value)
    }

    @Test fun `a tap is answered even when the screen text did not change`() = runTest {
        // Pressing "+" on a slider moves the task on without changing any label.
        val f = Fakes().apply { tree = fullScreen; replies += step("+ दबाइए"); replies += step("एक बार और") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        s.onScreenEvent(ScreenEvent.CLICK)
        advanceTimeBy(ScreenEvent.CLICK.settleMs + GuideSession.SETTLE_STEP_MS + 1)
        runCurrent()
        assertEquals(2, f.requests.size)
        // First capture, two settling reads, and a fresh validation before each reply is applied.
        assertEquals(5, f.looks)
    }

    @Test fun `a page that fills in after opening is judged once it has settled`() = runTest {
        // Android's Internet page adds its Wi-Fi switch a moment after it opens.
        val f = Fakes().apply { tree = fullScreen; replies += step("Internet दबाइए"); replies += step("Wi-Fi चालू है") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        val half = fullScreen.replace("Item 6", "Loading")
        f.tree = half
        s.onScreenEvent(ScreenEvent.CLICK)
        advanceTimeBy(ScreenEvent.CLICK.settleMs + 1)
        runCurrent()                      // first look: half-loaded
        f.tree = fullScreen.replace("Item 6", "Wi-Fi")
        advanceTimeBy(GuideSession.SETTLE_STEP_MS + 1)
        runCurrent()                      // second look differs: keep waiting
        advanceTimeBy(GuideSession.SETTLE_STEP_MS + 1)
        runCurrent()                      // third look matches: settled
        assertEquals(2, f.requests.size)
        assertTrue(f.requests.last().tree.contains("Wi-Fi"))
    }

    @Test fun `a scroll that changed the screen asks again`() = runTest {
        val f = Fakes().apply { tree = fullScreen; replies += step("a"); replies += step("b") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        f.tree = fullScreen.replace("Item 6", "Item 7")
        s.onScreenEvent(ScreenEvent.SCROLLED)
        advanceTimeBy(ScreenEvent.SCROLLED.settleMs + GuideSession.SETTLE_STEP_MS + 1)
        runCurrent()
        assertEquals(2, f.requests.size)
    }

    @Test fun `screen events are ignored while idle or listening`() = runTest {
        val f = Fakes()
        val s = f.session(this)
        s.onScreenEvent(ScreenEvent.CLICK)
        advanceUntilIdle()
        assertTrue(f.requests.isEmpty())
    }

    @Test fun `rapid screen events collapse into one look`() = runTest {
        val f = Fakes().apply { replies += step("a"); replies += step("b") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        repeat(5) {
            s.onScreenEvent(ScreenEvent.SCROLLED)
            advanceTimeBy(200)
        }
        advanceTimeBy(ScreenEvent.SCROLLED.settleMs + GuideSession.SETTLE_STEP_MS + 1)
        runCurrent()
        assertEquals(2, f.requests.size)
    }

    @Test fun `dynamic content changes advance unfamiliar apps without click events`() = runTest {
        val f = Fakes().apply {
            app = "example.unfamiliar.app"; tree = fullScreen
            replies += step("first step"); replies += step("next step")
        }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        f.tree = fullScreen.replace("Item 6", "Loaded result")
        s.onScreenEvent(ScreenEvent.CONTENT_CHANGED)
        advanceTimeBy(1200)
        runCurrent()
        assertEquals(listOf("first step", "next step"), f.said)
        assertEquals("example.unfamiliar.app", f.requests.last().app)
    }

    @Test fun `unchanged content notifications do not ask the model again`() = runTest {
        val f = Fakes().apply { tree = fullScreen; replies += step("first step") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        repeat(5) {
            s.onScreenEvent(ScreenEvent.CONTENT_CHANGED)
            advanceTimeBy(100)
        }
        advanceTimeBy(1200)
        runCurrent()
        assertEquals(1, f.requests.size)
        assertEquals(listOf("first step"), f.said)
    }

    @Test fun `content notifications wait for speech and preserve a typing pause`() = runTest {
        val f = Fakes().apply {
            tree = fullScreen; speechDelay = 2000
            replies += step("finish this instruction"); replies += step("new screen")
        }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        assertEquals(GuideState.SPEAKING, s.state.value)
        f.tree = fullScreen.replace("Item 6", "Loaded result")
        s.onScreenEvent(ScreenEvent.CONTENT_CHANGED)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(1, f.requests.size)
        assertEquals(GuideState.SPEAKING, s.state.value)
        advanceTimeBy(4500)
        runCurrent()
        assertEquals(listOf("finish this instruction", "new screen"), f.said)
        s.stop()

        val typing = Fakes().apply { tree = fullScreen; replies += step("type here"); replies += step("search result") }
        val t = typing.session(this)
        t.onBubbleTap()
        runCurrent()
        t.onScreenEvent(ScreenEvent.TYPED)
        typing.tree = fullScreen.replace("Item 6", "Search result")
        advanceTimeBy(1000)
        t.onScreenEvent(ScreenEvent.CONTENT_CHANGED)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(1, typing.requests.size)
        advanceTimeBy(1300)
        runCurrent()
        assertEquals(2, typing.requests.size)
    }

    @Test fun `content notifications during a pending turn do not discard unchanged replies`() = runTest {
        val f = Fakes().apply { tree = fullScreen; replyDelay = 1500; replies += step("next step") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        repeat(5) {
            s.onScreenEvent(ScreenEvent.CONTENT_CHANGED)
            advanceTimeBy(100)
        }
        advanceTimeBy(1100)
        runCurrent()
        assertEquals(1, f.requests.size)
        assertEquals(listOf("next step"), f.said)
    }

    @Test fun `a ticking noninteractive clock does not starve or repeat guidance`() = runTest {
        val f = Fakes().apply {
            tree = fullScreen.replace("Item 6", "00:01")
            replyDelay = 1500
            replies += step("Press ADD")
        }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        f.tree = f.tree.replace("00:01", "00:02")
        s.onScreenEvent(ScreenEvent.CONTENT_CHANGED)
        advanceTimeBy(1600)
        runCurrent()
        assertEquals(listOf("Press ADD"), f.said)
        assertEquals(1, f.requests.size)
        f.tree = f.tree.replace("00:02", "00:03")
        s.onScreenEvent(ScreenEvent.CONTENT_CHANGED)
        advanceTimeBy(1200)
        runCurrent()
        assertEquals(1, f.requests.size)
    }

    @Test fun `pressing Home in the middle of a task ends it instead of reopening the app`() = runTest {
        val f = Fakes().apply { replies += step("ADD दबाइए"); replies += step("Blinkit खोल रही हूँ") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        assertEquals(GuideState.WATCHING, s.state.value)

        f.app = "com.android.launcher3"
        f.tree = "n1 TextView \"Blinkit\" [0,0,200,200] click"
        s.onScreenEvent(ScreenEvent.NAVIGATED)
        advanceTimeBy(ScreenEvent.NAVIGATED.settleMs + GuideSession.SETTLE_STEP_MS * (GuideSession.SETTLE_TRIES + 1) + 1)
        runCurrent()
        assertEquals(1, f.requests.size)
        assertEquals(GuideState.IDLE, s.state.value)
        assertEquals("", s.currentGoal)
    }

    @Test fun `Home during a pending turn drops its pointer and automatic action`() = runTest {
        for (reply in listOf(
            step("stale pointer"),
            Guide(say = "stale launch", action = Action.OPEN_APP, actionArg = "com.whatsapp"),
        )) {
            val f = Fakes().apply { replyDelay = 2000; replies += reply }
            val s = f.session(this)
            s.onBubbleTap()
            runCurrent()
            assertEquals(GuideState.THINKING, s.state.value)
            f.app = "com.android.launcher3"
            s.onScreenEvent(ScreenEvent.NAVIGATED)
            advanceTimeBy(2001)
            runCurrent()
            assertTrue(f.pointed.isEmpty())
            assertTrue(f.said.isEmpty())
            assertTrue(f.opened.isEmpty())
            assertEquals(GuideState.IDLE, s.state.value)
            assertEquals("", s.currentGoal)
        }
    }

    @Test fun `a screen change without an event retries with the original request`() = runTest {
        val f = Fakes().apply {
            replyDelay = 2000
            replies += step("stale step")
            replies += step("fresh step")
        }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        f.tree = f.tree.replace("ADD", "New item")
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(listOf("fresh step"), f.said)
        assertEquals(2, f.requests.size)
        assertEquals(f.requests.first().utterance, f.requests.last().utterance)
        assertTrue(f.requests.last().history.isEmpty())
        assertEquals("", f.requests.last().goal)
        assertEquals(f.tree, f.requests.last().tree)
    }

    @Test fun `a tap while thinking invalidates an answer even when labels stay the same`() = runTest {
        val f = Fakes().apply {
            replyDelay = 2000
            replies += step("stale step")
            replies += step("fresh step")
        }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        s.onScreenEvent(ScreenEvent.CLICK)
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(listOf("fresh step"), f.said)
        assertEquals(2, f.requests.size)
    }

    @Test fun `Home while a stale screen settles also ends the task`() = runTest {
        val f = Fakes().apply { replyDelay = 1000; replies += step("stale step") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        f.tree = f.tree.replace("ADD", "Loading")
        advanceTimeBy(1001)
        runCurrent()
        f.app = "com.android.launcher3"
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(1, f.requests.size)
        assertTrue(f.said.isEmpty())
        assertEquals(GuideState.IDLE, s.state.value)
    }

    @Test fun `protected replies cannot draw pointers or perform actions on the phone`() = runTest {
        for (protected in listOf(
            step("unsafe").copy(danger = Danger.SENSITIVE),
            step("unsafe").copy(danger = Danger.SCAM),
            step("unsafe", status = Status.USER_MUST_DO),
            Guide(say = "unsafe", danger = Danger.SENSITIVE, action = Action.SCROLL_DOWN),
            Guide(say = "unsafe", status = Status.USER_MUST_DO, action = Action.OPEN_APP, actionArg = "com.whatsapp"),
        )) {
            val f = Fakes().apply { replies += protected }
            val s = f.session(this)
            s.onBubbleTap()
            runCurrent()
            assertNull(f.pointed.single().first)
            assertEquals(0, f.scrolls)
            assertTrue(f.opened.isEmpty())
            s.stop()
        }
    }

    @Test fun `a protected flag in either look hands control back without retrying`() = runTest {
        for (initiallySecure in listOf(true, false)) {
            val f = Fakes().apply { secure = initiallySecure; replyDelay = 1000; replies += step("unsafe pointer") }
            val s = f.session(this)
            s.onBubbleTap()
            runCurrent()
            f.secure = !initiallySecure
            advanceTimeBy(1100)
            runCurrent()
            assertEquals(1, f.requests.size)
            assertEquals(listOf(f.lines.sensitive), f.said)
            assertNull(f.pointed.single().first)
            s.stop()
        }
    }

    @Test fun `the hand comes off when its button disappears right after it is shown`() = runTest {
        val f = Fakes().apply { replies += step("Press ADD") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        assertEquals(Box(800, 300, 1000, 380), f.pointed.last().first)
        // Checkout draws where the cart was, with no screen event yet.
        f.tree = "n2 Button \"Place Order\" [800,300,1000,380] click"
        advanceTimeBy(600)
        runCurrent()
        assertNull(f.pointed.last().first)
        assertEquals("Press ADD", f.pointed.last().second)
        s.stop()
    }

    @Test fun `the hand stays on a search box cycling its example`() = runTest {
        val f = Fakes().apply {
            tree = "n2 EditText \"Search 'milk'\" [800,300,1000,380] click edit"
            replies += step("Press Search")
        }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        f.tree = "n2 EditText \"Search 'stationery'\" [800,300,1000,380] click edit"
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(Box(800, 300, 1000, 380), f.pointed.last().first)
        s.stop()
    }

    @Test fun `the hand stays while its button is still there`() = runTest {
        val f = Fakes().apply { replies += step("Press ADD") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(Box(800, 300, 1000, 380), f.pointed.last().first)
        s.stop()
    }

    @Test fun `a recipe's own hand-back line is kept on a protected screen, the model's is not`() = runTest {
        for ((expect, keeps) in listOf("recipe blinkit-order/pay" to true, "" to false)) {
            val f = Fakes().apply {
                secure = true
                replies += step("Check the address, then pay yourself. Never tell anyone your PIN.")
                    .copy(targetKind = "none", nodeId = "", status = Status.USER_MUST_DO, danger = Danger.SENSITIVE, expect = expect)
            }
            val s = f.session(this)
            s.onBubbleTap()
            runCurrent()
            val expected = if (keeps) "Check the address, then pay yourself. Never tell anyone your PIN." else f.lines.sensitive
            assertEquals(listOf(expected), f.said)
            assertNull(f.pointed.single().first)
            s.stop()
        }
    }

    @Test fun `unflagged payment targets are replaced with the local sensitive warning`() = runTest {
        for (kind in listOf("node", "point")) {
            val f = Fakes().apply {
                tree = tree.replace("ADD", "Pay now")
                replies += step("Press Pay now").copy(targetKind = kind, label = "", x = 0.83, y = 0.14)
            }
            val s = f.session(this)
            s.onBubbleTap()
            runCurrent()
            assertNull(f.pointed.single().first)
            assertEquals(listOf(f.lines.sensitive), f.said)
            s.stop()
        }
    }

    @Test fun `done resets goal and history`() = runTest {
        val f = Fakes().apply { replies += step("हो गया", status = Status.DONE) }
        val s = f.session(this)
        s.onBubbleTap()
        advanceUntilIdle()
        assertEquals(GuideState.IDLE, s.state.value)
        assertEquals("", s.currentGoal)
    }

    @Test fun `after a finished task Saathi asks for anything else and listens again`() = runTest {
        val f = Fakes().apply {
            anythingElse = "anything-else"
            replies += step("हो गया", status = Status.DONE)
            replies += step("ADD दबाइए")
            hearNext += Heard.Words("दूध मंगाना है")
            hearNext += Heard.Words("अब दवाई मंगानी है")
        }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        advanceTimeBy(GuideSession.FOLLOW_UP_PAUSE_MS + 1)
        runCurrent()
        assertEquals(listOf("हो गया", "anything-else", "ADD दबाइए"), f.said)
        // The new request is a new task: no old goal or history carried over.
        assertEquals("अब दवाई मंगानी है", f.requests[1].utterance)
        assertEquals("", f.requests[1].goal)
        assertTrue(f.requests[1].history.isEmpty())
    }

    @Test fun `no thanks after a finished task says goodbye, silence just rests`() = runTest {
        val f = Fakes().apply {
            anythingElse = "anything-else"
            replies += step("हो गया", status = Status.DONE)
            hearNext += Heard.Words("दूध मंगाना है")
            hearNext += Heard.Words("nahi bas, thank you")
        }
        val s = f.session(this)
        s.onBubbleTap()
        advanceUntilIdle()
        assertEquals(listOf("हो गया", "anything-else", "goodbye"), f.said)
        assertEquals(1, f.requests.size)
        assertEquals(GuideState.IDLE, s.state.value)

        val quiet = Fakes().apply {
            anythingElse = "anything-else"
            replies += step("हो गया", status = Status.DONE)
            hearNext += Heard.Words("दूध मंगाना है")
            hearNext += Heard.Silence
        }
        val q = quiet.session(this)
        q.onBubbleTap()
        advanceUntilIdle()
        assertEquals(listOf("हो गया", "anything-else"), quiet.said)
        assertEquals(GuideState.IDLE, q.state.value)
    }

    @Test fun `need_info listens again without another tap`() = runTest {
        val f = Fakes().apply {
            replies += step("कौन सा दूध?", status = Status.NEED_INFO)
            replies += step("ADD दबाइए")
        }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        assertEquals(2, f.requests.size)
        assertTrue(f.requests[1].utterance != null)
    }

    @Test fun `silence and network errors are spoken locally`() = runTest {
        val f = Fakes().apply { heard = Heard.Silence }
        val s = f.session(this)
        s.onBubbleTap()
        advanceUntilIdle()
        assertEquals(listOf("no-speech"), f.said)

        f.heard = Heard.Words("दूध")
        f.failNext = true
        s.onBubbleTap()
        advanceUntilIdle()
        assertEquals(listOf("no-speech", "network"), f.said)
        assertEquals(GuideState.IDLE, s.state.value)
    }

    @Test fun `no microphone permission is explained, not attempted`() = runTest {
        val f = Fakes()
        val s = f.session(this, mic = false)
        s.onBubbleTap()
        advanceUntilIdle()
        assertEquals(listOf("mic"), f.said)
        assertTrue(f.requests.isEmpty())
    }

    @Test fun `scam warning stops auto-advance`() = runTest {
        val f = Fakes().apply { replies += step("रुकिए! यह धोखा है", status = Status.CANNOT_HELP).copy(danger = Danger.SCAM) }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        assertEquals(GuideState.IDLE, s.state.value)
        s.onScreenEvent(ScreenEvent.CLICK)
        advanceUntilIdle()
        assertEquals(1, f.requests.size)
    }

    @Test fun `point targets map through the screenshot box`() = runTest {
        val f = Fakes()
        val s = f.session(this)
        val snap = Snapshot("a", null, 1000, 2000, "", emptyMap(), false, shotBox = Box(0, 100, 1000, 2100))
        val box = s.targetBox(Guide(say = "x", targetKind = "point", x = 0.5, y = 0.5), snap)!!
        assertEquals(500, box.centerX)
        assertEquals(1100, box.centerY)
        assertNull(s.targetBox(Guide(say = "x", targetKind = "node", nodeId = "n404"), snap))
    }

    @Test fun `second tap while listening finishes the recording`() = runTest {
        val f = Fakes()
        f.replies += step("x")
        val s = GuideSession(
            this, f.eyes,
            object : Ears {
                override suspend fun listen(): Heard { kotlinx.coroutines.delay(10_000); return Heard.Words("दूध") }
                override fun finish() { f.finished = true }
            },
            f.mouth, f.pointer, f.brain, { "" }, { "hi-IN" }, { f.lines }, { true },
        )
        s.onBubbleTap()
        runCurrent()
        assertEquals(GuideState.LISTENING, s.state.value)
        s.onBubbleTap()
        assertTrue(f.finished)
    }

    @Test fun `each listening failure gets its own explanation`() = runTest {
        val f = Fakes()
        val s = f.session(this)
        for (h in listOf(Heard.MicBusy, Heard.Offline, Heard.Unavailable)) {
            f.heard = h
            s.onBubbleTap()
            advanceUntilIdle()
        }
        assertEquals(listOf("mic-busy", "speech-offline", "speech-unavailable"), f.said)
        assertTrue(f.requests.isEmpty())
    }

    @Test fun `a second silence in a row explains how to be heard`() = runTest {
        val f = Fakes().apply { heard = Heard.Silence }
        val s = f.session(this)
        repeat(3) { s.onBubbleTap(); advanceUntilIdle() }
        assertEquals(listOf("no-speech", "louder", "louder"), f.said)
    }

    @Test fun `help during a call gives the scam reminder without listening`() = runTest {
        val f = Fakes().apply { calling = true }
        val s = f.session(this)
        s.onBubbleTap()
        advanceUntilIdle()
        assertEquals(listOf("in-call"), f.said)
        assertTrue(f.requests.isEmpty())
    }

    @Test fun `a call starting mid-task clears the overlay instead of narrating`() = runTest {
        val f = Fakes().apply { replies += step("कैमरा दबाइए") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        f.calling = true
        s.onScreenEvent(ScreenEvent.NAVIGATED)
        advanceUntilIdle()
        assertEquals(GuideState.IDLE, s.state.value)
        assertEquals(1, f.requests.size)
    }

    @Test fun `a slow answer gets a spoken one moment first`() = runTest {
        val f = Fakes().apply { replies += step("ADD दबाइए") }
        val slowBrain = object : Brain {
            override suspend fun turn(request: TurnRequest): TurnResponse {
                kotlinx.coroutines.delay(6_000)
                return f.brain.turn(request)
            }
        }
        val s = GuideSession(this, f.eyes, f.ears, f.mouth, f.pointer, slowBrain, { "" }, { "hi-IN" }, { f.lines }, { true })
        s.onBubbleTap()
        advanceTimeBy(7_000)
        runCurrent()
        assertEquals(listOf("wait", "ADD दबाइए"), f.said)

        // A quick answer says nothing extra.
        val g = Fakes().apply { replies += step("x") }
        val quick = g.session(this)
        quick.onBubbleTap()
        runCurrent()
        assertEquals(listOf("x"), g.said)
    }

    @Test fun `busy and limit failures are explained, not called a network problem`() = runTest {
        for ((kind, line) in listOf(TurnFailure.Kind.BUSY to "busy", TurnFailure.Kind.LIMIT to "limit", TurnFailure.Kind.OFFLINE to "network")) {
            val f = Fakes()
            val failing = object : Brain {
                override suspend fun turn(request: TurnRequest): TurnResponse = throw TurnFailure(kind, "x")
            }
            val s = GuideSession(this, f.eyes, f.ears, f.mouth, f.pointer, failing, { "" }, { "hi-IN" }, { f.lines }, { true })
            s.onBubbleTap()
            advanceUntilIdle()
            assertEquals(listOf(line), f.said)
        }
    }

    @Test fun `repeat speaks the last step more slowly`() = runTest {
        val f = Fakes().apply { replies += step("ADD दबाइए") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        s.repeat()
        runCurrent()
        assertEquals(listOf("ADD दबाइए"), f.slowSaid)
    }

    @Test fun `stopped tasks cannot be repeated`() = runTest {
        val f = Fakes().apply { replies += step("old task") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        s.stop()
        s.repeat()
        runCurrent()
        assertTrue(f.slowSaid.isEmpty())
        assertEquals(GuideState.IDLE, s.state.value)
    }

    @Test fun `repeating a scam warning never resumes the interrupted task`() = runTest {
        val f = Fakes().apply { replies += step("old task") }
        val s = f.session(this)
        s.onBubbleTap()
        runCurrent()
        s.warn("Stop, this may be a scam")
        runCurrent()
        s.repeat()
        runCurrent()
        assertEquals(listOf("Stop, this may be a scam"), f.slowSaid)
        assertEquals("", s.currentGoal)
        assertEquals(GuideState.IDLE, s.state.value)
        s.onScreenEvent(ScreenEvent.CLICK)
        runCurrent()
        assertEquals(1, f.requests.size)
    }

    @Test fun `failed screen capture recovers the session`() = runTest {
        val f = Fakes()
        val s = GuideSession(this, object : Eyes {
            override suspend fun look(): Snapshot = throw IOException("screen unavailable")
        }, f.ears, f.mouth, f.pointer, f.brain, { "" }, { "hi-IN" }, { f.lines }, { true })
        s.onBubbleTap()
        runCurrent()
        assertEquals(listOf("network"), f.said)
        assertEquals(GuideState.IDLE, s.state.value)
    }

    @Test fun `speech playback failure leaves the session idle`() = runTest {
        val f = Fakes().apply { replies += step("next step") }
        val failures = mutableListOf<Exception>()
        val s = GuideSession(this, f.eyes, f.ears, object : Mouth {
            override suspend fun say(text: String, slow: Boolean): Unit = throw IOException("speaker failed")
            override fun stop() {}
        }, f.pointer, f.brain, { "" }, { "hi-IN" }, { f.lines }, { true }, reportFailure = { failures += it })
        s.onBubbleTap()
        runCurrent()
        assertEquals(GuideState.IDLE, s.state.value)
        assertTrue(failures.isNotEmpty())
        assertEquals("", s.currentGoal)
    }

    @Test fun `repeating a question listens for an answer again`() = runTest {
        val f = Fakes().apply {
            replies += step("Which milk?", status = Status.NEED_INFO)
            replies += step("fresh step")
        }
        var listens = 0
        val ears = object : Ears {
            override suspend fun listen(): Heard {
                listens++
                if (listens == 2) kotlinx.coroutines.awaitCancellation()
                return Heard.Words(if (listens == 1) "order milk" else "Amul")
            }
            override fun finish() {}
        }
        val s = GuideSession(this, f.eyes, ears, f.mouth, f.pointer, f.brain, { "" }, { "hi-IN" }, { f.lines }, { true })
        s.onBubbleTap()
        runCurrent()
        assertEquals(GuideState.LISTENING, s.state.value)
        s.repeat()
        runCurrent()
        assertEquals(3, listens)
        assertEquals(listOf("Which milk?"), f.slowSaid)
        assertEquals("Amul", f.requests.last().utterance)
        s.stop()
    }

    @Test fun `finished steps stay up long enough to read`() {
        assertEquals(3_000L, GuideSession.readingTimeMs("हो गया"))
        assertTrue(GuideSession.readingTimeMs("x".repeat(120)) > 9_000L)
        assertEquals(12_000L, GuideSession.readingTimeMs("x".repeat(1_000)))
    }
}
