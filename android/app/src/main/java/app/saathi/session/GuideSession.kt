package app.saathi.session

import app.saathi.capture.Box
import app.saathi.net.Danger
import app.saathi.net.Guide
import app.saathi.net.PastStep
import app.saathi.net.Status
import app.saathi.net.TurnRequest
import app.saathi.net.TurnResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the phone sees at one moment. */
class Snapshot(
    val app: String,
    val screenshotB64: String?,
    val screenW: Int,
    val screenH: Int,
    val tree: String,
    val boxes: Map<String, Box>,
    val secure: Boolean,
    /** Where the screenshot sits on screen; "point" targets are fractions of this box. */
    val shotBox: Box = Box(0, 0, screenW, screenH),
)

interface Eyes { suspend fun look(): Snapshot }

interface Ears {
    /** Listens for one utterance and returns the transcript, or null if nothing was understood. */
    suspend fun listen(): String?
    /** Ends the current listening early (second tap on the bubble). */
    fun finish()
}

interface Mouth {
    /** Speaks [text]. Suspends until done. */
    suspend fun say(text: String)
    fun stop()
}

interface Pointer {
    fun state(state: GuideState)
    fun point(target: Box?, caption: String, danger: String)
    fun clear()
}

interface Brain { suspend fun turn(request: TurnRequest): TurnResponse }

/** Localised lines the session speaks without asking the server. */
interface Lines {
    val noSpeech: String
    val network: String
    val micDenied: String
}

enum class GuideState { IDLE, LISTENING, THINKING, SPEAKING, WATCHING }

/** How the senior changed the screen. Each has its own settle time before we look again. */
enum class ScreenEvent(val settleMs: Long) { CLICK(1200), NAVIGATED(1200), SCROLLED(1500), TYPED(3000) }

/**
 * The guide loop: listen → look → ask → speak + point → watch for the senior's tap → look again.
 * Everything runs on [scope]; only one pipeline job is active at a time.
 */
class GuideSession(
    private val scope: CoroutineScope,
    private val eyes: Eyes,
    private val ears: Ears,
    private val mouth: Mouth,
    private val pointer: Pointer,
    private val brain: Brain,
    private val playbook: (app: String) -> String,
    private val language: () -> String,
    private val lines: () -> Lines,
    private val hasMic: () -> Boolean,
) {
    companion object {
        const val MAX_AUTO_TURNS = 15
        const val WATCH_TIMEOUT_MS = 120_000L
        const val HISTORY = 8
        const val POINT_RADIUS_PX = 90
    }

    private val _state = MutableStateFlow(GuideState.IDLE)
    val state: StateFlow<GuideState> = _state

    private var job: Job? = null
    private var watchTimeout: Job? = null
    private var goal = ""
    private val history = ArrayDeque<PastStep>()
    private var autoTurns = 0
    private var lastSay: String? = null

    fun onBubbleTap() {
        if (_state.value == GuideState.LISTENING) {
            ears.finish()
            return
        }
        launchPipeline { spokenTurn() }
    }

    fun onScreenEvent(event: ScreenEvent) {
        val s = _state.value
        // Only re-look while we are waiting on the senior, or if they acted before we finished talking.
        if (s != GuideState.WATCHING && s != GuideState.SPEAKING) return
        if (autoTurns >= MAX_AUTO_TURNS) return
        // The senior acted, so the old spotlight is probably pointing at something that moved or is gone.
        pointer.clear()
        launchPipeline {
            setState(GuideState.WATCHING)
            delay(event.settleMs)
            autoTurns++
            runTurn(utterance = null)
        }
    }

    /** A typed request instead of a spoken one (debug broadcast today; a text box later). */
    fun ask(text: String) {
        launchPipeline {
            goal = text
            history.clear()
            autoTurns = 0
            runTurn(utterance = null)
        }
    }

    fun repeat() {
        val say = lastSay ?: return
        launchPipeline {
            setState(GuideState.SPEAKING)
            mouth.say(say)
            enterWatching()
        }
    }

    fun stop() {
        job?.cancel()
        reset()
    }

    /** Speaks a local warning (scam guard) regardless of what the session is doing. */
    fun warn(text: String) {
        launchPipeline {
            pointer.point(null, text, Danger.SCAM)
            setState(GuideState.SPEAKING)
            mouth.say(text)
            setState(GuideState.IDLE)
        }
    }

    private fun launchPipeline(block: suspend () -> Unit) {
        val previous = job
        watchTimeout?.cancel()
        mouth.stop()
        job = scope.launch {
            previous?.cancelAndJoin()
            block()
        }
    }

    private suspend fun spokenTurn() {
        if (!hasMic()) {
            speakAndIdle(lines().micDenied)
            return
        }
        pointer.clear()
        setState(GuideState.LISTENING)
        val heard = ears.listen()
        if (heard == null) {
            speakAndIdle(lines().noSpeech)
            return
        }
        autoTurns = 0
        runTurn(heard)
    }

    private suspend fun runTurn(utterance: String?) {
        setState(GuideState.THINKING)
        val snap = eyes.look()
        val request = TurnRequest(
            language = language(),
            utterance = utterance,
            goal = goal,
            app = snap.app,
            playbook = playbook(snap.app),
            screenshotB64 = snap.screenshotB64,
            screenW = snap.screenW,
            screenH = snap.screenH,
            tree = snap.tree,
            secureScreen = snap.secure,
            history = history.toList(),
        )
        val response = try {
            brain.turn(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            speakAndIdle(lines().network)
            return
        }

        val guide = response.guide
        if (guide.goal.isNotBlank()) goal = guide.goal
        history.addLast(PastStep(guide.say, guide.label, guide.status, snap.app))
        while (history.size > HISTORY) history.removeFirst()
        lastSay = guide.say

        pointer.point(targetBox(guide, snap), guide.say, guide.danger)
        setState(GuideState.SPEAKING)
        mouth.say(guide.say)

        when {
            guide.danger == Danger.SCAM -> setState(GuideState.IDLE) // caption stays until closed
            guide.status == Status.NEED_INFO -> spokenTurn()
            guide.status == Status.DONE || guide.status == Status.CANNOT_HELP -> {
                delay(2500)
                reset()
            }
            else -> enterWatching() // next_step, user_must_do
        }
    }

    private fun enterWatching() {
        setState(GuideState.WATCHING)
        watchTimeout?.cancel()
        watchTimeout = scope.launch {
            delay(WATCH_TIMEOUT_MS)
            if (_state.value == GuideState.WATCHING) reset()
        }
    }

    private suspend fun speakAndIdle(text: String) {
        pointer.point(null, text, Danger.NONE)
        setState(GuideState.SPEAKING)
        mouth.say(text)
        setState(GuideState.IDLE)
    }

    private fun reset() {
        watchTimeout?.cancel()
        mouth.stop()
        pointer.clear()
        goal = ""
        history.clear()
        autoTurns = 0
        setState(GuideState.IDLE)
    }

    private fun setState(s: GuideState) {
        _state.value = s
        pointer.state(s)
    }

    internal fun targetBox(guide: Guide, snap: Snapshot): Box? = when (guide.targetKind) {
        "node" -> snap.boxes[guide.nodeId]
        "point" -> if (guide.x in 0.0..1.0 && guide.y in 0.0..1.0) {
            Box.around(
                snap.shotBox.left + (guide.x * snap.shotBox.width).toInt(),
                snap.shotBox.top + (guide.y * snap.shotBox.height).toInt(),
                POINT_RADIUS_PX,
            )
        } else null
        else -> null
    }

    internal val currentGoal get() = goal
}
