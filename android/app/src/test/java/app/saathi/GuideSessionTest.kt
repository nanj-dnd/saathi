package app.saathi

import app.saathi.capture.Box
import app.saathi.net.Danger
import app.saathi.net.Guide
import app.saathi.net.Status
import app.saathi.net.TurnRequest
import app.saathi.net.TurnResponse
import app.saathi.session.Brain
import app.saathi.session.Ears
import app.saathi.session.Eyes
import app.saathi.session.GuideSession
import app.saathi.session.GuideState
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
        var heard: String? = "ब्लिंकिट से दूध मंगाना है"
        var finished = false

        val eyes = object : Eyes {
            override suspend fun look() = Snapshot(
                app = "com.grofers.customerapp", screenshotB64 = "jpg", screenW = 1080, screenH = 2400,
                tree = "n2 Button \"ADD\" [800,300,1000,380] click",
                boxes = mapOf("n2" to Box(800, 300, 1000, 380)), secure = false,
            )
        }
        val ears = object : Ears {
            override suspend fun listen() = heard
            override fun finish() { finished = true }
        }
        val mouth = object : Mouth {
            override suspend fun say(text: String) { said += text }
            override fun stop() {}
        }
        val pointer = object : Pointer {
            override fun state(state: GuideState) {}
            override fun point(target: Box?, caption: String, danger: String) { pointed += target to caption }
            override fun clear() {}
        }
        val brain = object : Brain {
            override suspend fun turn(request: TurnRequest): TurnResponse {
                requests += request
                if (failNext) { failNext = false; throw IOException("down") }
                return TurnResponse(guide = replies.removeFirst())
            }
        }
        val lines = object : Lines {
            override val noSpeech = "no-speech"
            override val network = "network"
            override val micDenied = "mic"
        }

        fun session(scope: TestScope, mic: Boolean = true) = GuideSession(
            scope, eyes, ears, mouth, pointer, brain, { "hints" }, { "hi-IN" }, { lines }, { mic },
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
        advanceTimeBy(ScreenEvent.CLICK.settleMs + 1)
        runCurrent()

        assertEquals(2, f.requests.size)
        val second = f.requests[1]
        assertNull(second.utterance)
        assertEquals("order milk", second.goal)
        assertEquals(1, second.history.size)
        assertEquals(listOf("ADD दबाइए", "View cart दबाइए"), f.said)
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
        advanceTimeBy(ScreenEvent.SCROLLED.settleMs + 1)
        runCurrent()
        assertEquals(2, f.requests.size)
    }

    @Test fun `done resets goal and history`() = runTest {
        val f = Fakes().apply { replies += step("हो गया", status = Status.DONE) }
        val s = f.session(this)
        s.onBubbleTap()
        advanceUntilIdle()
        assertEquals(GuideState.IDLE, s.state.value)
        assertEquals("", s.currentGoal)
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
        val f = Fakes().apply { heard = null }
        val s = f.session(this)
        s.onBubbleTap()
        advanceUntilIdle()
        assertEquals(listOf("no-speech"), f.said)

        f.heard = "दूध"
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
                override suspend fun listen(): String { kotlinx.coroutines.delay(10_000); return "दूध" }
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
}
