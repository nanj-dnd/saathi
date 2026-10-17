package app.saathi.session

import app.saathi.brain.ScreenKey
import app.saathi.capture.Box
import app.saathi.net.Danger
import app.saathi.net.Guide
import app.saathi.net.PastStep
import app.saathi.net.Status
import app.saathi.net.TurnFailure
import app.saathi.net.TurnRequest
import app.saathi.net.TurnResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
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

/** What came of one attempt to listen. Each failure gets its own explanation. */
sealed interface Heard {
    data class Words(val text: String) : Heard
    /** Nothing was said, or nothing could be made out. */
    data object Silence : Heard
    /** The microphone is in use by something else (often a phone call). */
    data object MicBusy : Heard
    /** The speech service needs the internet and there is none. */
    data object Offline : Heard
    /** The phone has no speech service for this language. */
    data object Unavailable : Heard
}

interface Ears {
    /** Listens for one utterance. */
    suspend fun listen(): Heard
    /** Ends the current listening early (second tap on the bubble). */
    fun finish()
}

interface Mouth {
    /** Speaks [text]. Suspends until done. [slow] is for a repeat: the senior didn't catch it. */
    suspend fun say(text: String, slow: Boolean = false)
    fun stop()
}

interface Pointer {
    fun state(state: GuideState)
    /** Shows [caption] and spotlights [target]. [done] marks a finished task, shown as a success. */
    fun point(target: Box?, caption: String, danger: String, done: Boolean = false)
    fun clear()
}

interface Brain { suspend fun turn(request: TurnRequest): TurnResponse }

/** Localised lines the session speaks without asking the server. */
interface Lines {
    val noSpeech: String
    val network: String
    val micDenied: String
    /** Second silence in a row: how to be heard. */
    val noSpeechAgain: String get() = noSpeech
    val micBusy: String get() = noSpeech
    /** Help pressed during a call: a scam reminder, since that is when fraudsters strike. */
    val inCall: String get() = micBusy
    val speechOffline: String get() = network
    val speechUnavailable: String get() = noSpeech
    /** Said when an answer is slow, so silence doesn't feel like Saathi gave up. */
    val wait: String get() = ""
    val busy: String get() = network
    val limit: String get() = network
}

enum class GuideState { IDLE, LISTENING, THINKING, SPEAKING, WATCHING }

/**
 * How the senior changed the screen. Each has its own settle time before we look again.
 * [progress] events can move the task on without changing the screen's text (pressing "+" on a
 * font slider), so they are always answered; the others are skipped if nothing visibly changed.
 */
enum class ScreenEvent(val settleMs: Long, val progress: Boolean) {
    CLICK(1200, true),
    NAVIGATED(1200, false),
    SCROLLED(1500, false),
    TYPED(3000, false),
}

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
    /** True during a phone or video call: the mic is taken and narrating over a call helps no one. */
    private val inCall: () -> Boolean = { false },
) {
    companion object {
        const val MAX_AUTO_TURNS = 15
        /** How long a screen gets to finish loading after the senior acts, in looks 500ms apart. */
        const val SETTLE_TRIES = 4
        const val SETTLE_STEP_MS = 500L
        const val WATCH_TIMEOUT_MS = 120_000L
        const val HISTORY = 8
        const val POINT_RADIUS_PX = 90
        /** After this long without an answer, say "one moment". */
        const val WAIT_HINT_MS = 3_500L

        /** How long a finished step stays up: long enough for a slow reader, not forever. */
        fun readingTimeMs(text: String): Long = (2_500L + text.length * 60L).coerceIn(3_000L, 12_000L)
    }

    private val _state = MutableStateFlow(GuideState.IDLE)
    val state: StateFlow<GuideState> = _state

    private var job: Job? = null
    private var watchTimeout: Job? = null
    private var goal = ""
    private val history = ArrayDeque<PastStep>()
    private var autoTurns = 0
    private var lastSay: String? = null
    /** The last answered screen and its step, to re-show it when an event changed nothing. */
    private var lastLook: Snapshot? = null
    private var lastGuide: Guide? = null
    private var silences = 0

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
        if (inCall()) {
            // A call started (often the one the senior was just helped to make): get out of the way.
            stop()
            return
        }
        if (autoTurns >= MAX_AUTO_TURNS) return
        // The senior acted, so the old spotlight is probably pointing at something that moved or is gone.
        pointer.clear()
        launchPipeline {
            setState(GuideState.WATCHING)
            delay(event.settleMs)
            setState(GuideState.THINKING)
            val snap = lookSettled()
            if (!event.progress && unchanged(snap)) {
                // A scroll that went nowhere or a dialog that flickered: keep the same step on
                // screen, without asking again or repeating it out loud.
                val guide = lastGuide!!
                pointer.point(targetBox(guide, snap), guide.say, guide.danger)
                enterWatching()
                return@launchPipeline
            }
            autoTurns++
            runTurn(utterance = null, seen = snap)
        }
    }

    /**
     * Looks until two looks in a row read the same, so a page that fills in after it opens
     * (Android's Internet page adds its Wi-Fi switch a moment later) is judged whole rather than
     * half-loaded. Busy screens (carousels, videos) never settle, so it gives up after a few tries
     * and uses the latest look.
     */
    private suspend fun lookSettled(): Snapshot {
        var snap = eyes.look()
        repeat(SETTLE_TRIES) {
            delay(SETTLE_STEP_MS)
            val again = eyes.look()
            // An empty read (no window content yet) never counts as settled.
            if (again.app.isNotEmpty() && again.app == snap.app && again.tree == snap.tree) return again
            if (again.app.isNotEmpty() || snap.app.isEmpty()) snap = again
        }
        return snap
    }

    private fun unchanged(snap: Snapshot): Boolean {
        val last = lastLook ?: return false
        return lastGuide != null && snap.app == last.app && snap.tree == last.tree && ScreenKey.informative(snap.tree)
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
            mouth.say(say, slow = true)
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
        if (inCall()) {
            speakAndIdle(lines().inCall, Danger.SCAM)
            return
        }
        pointer.clear()
        setState(GuideState.LISTENING)
        val heard = ears.listen()
        if (heard !is Heard.Silence) silences = 0
        when (heard) {
            is Heard.Words -> {
                autoTurns = 0
                runTurn(heard.text)
            }
            Heard.Silence -> speakAndIdle(if (++silences >= 2) lines().noSpeechAgain else lines().noSpeech)
            Heard.MicBusy -> speakAndIdle(if (inCall()) lines().inCall else lines().micBusy)
            Heard.Offline -> speakAndIdle(lines().speechOffline)
            Heard.Unavailable -> speakAndIdle(lines().speechUnavailable)
        }
    }

    private suspend fun runTurn(utterance: String?, seen: Snapshot? = null) {
        setState(GuideState.THINKING)
        val snap = seen ?: eyes.look()
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
            withWaitHint { brain.turn(request) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            speakAndIdle(failureLine(e))
            return
        }

        val guide = response.guide
        if (guide.goal.isNotBlank()) goal = guide.goal
        history.addLast(PastStep(guide.say, guide.label, guide.status, snap.app))
        while (history.size > HISTORY) history.removeFirst()
        lastSay = guide.say
        lastLook = snap
        lastGuide = guide

        pointer.point(targetBox(guide, snap), guide.say, guide.danger, done = guide.status == Status.DONE)
        setState(GuideState.SPEAKING)
        mouth.say(guide.say)

        when {
            guide.danger == Danger.SCAM -> setState(GuideState.IDLE) // caption stays until closed
            guide.status == Status.NEED_INFO -> spokenTurn()
            guide.status == Status.DONE || guide.status == Status.CANNOT_HELP -> {
                delay(readingTimeMs(guide.say))
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

    /** Runs [block]; if it is slow, says "one moment" meanwhile and stops saying it once it returns. */
    private suspend fun <T> withWaitHint(block: suspend () -> T): T = coroutineScope {
        val hint = launch {
            delay(WAIT_HINT_MS)
            val wait = lines().wait
            if (wait.isNotBlank()) {
                pointer.point(null, wait, Danger.NONE)
                mouth.say(wait)
            }
        }
        try {
            block()
        } finally {
            hint.cancel()
        }
    }

    private fun failureLine(e: Exception): String = when ((e as? TurnFailure)?.kind) {
        TurnFailure.Kind.BUSY -> lines().busy
        TurnFailure.Kind.LIMIT -> lines().limit
        else -> lines().network
    }

    private suspend fun speakAndIdle(text: String, danger: String = Danger.NONE) {
        pointer.point(null, text, danger)
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
        lastLook = null
        lastGuide = null
        silences = 0
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
