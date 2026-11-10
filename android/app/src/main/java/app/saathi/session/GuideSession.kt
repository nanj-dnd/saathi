package app.saathi.session

import app.saathi.brain.ScreenKey
import app.saathi.capture.Box
import app.saathi.capture.ScreenText
import app.saathi.capture.TreeNode
import app.saathi.net.Action
import app.saathi.net.Danger
import app.saathi.net.Guide
import app.saathi.net.PastStep
import app.saathi.net.Status
import app.saathi.net.TurnFailure
import app.saathi.net.TurnRequest
import app.saathi.net.TurnResponse
import app.saathi.recipes.RecipeEngine
import app.saathi.safety.ScreenSafety
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

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

/** The harmless moves Saathi may make itself (see [Action]). Each returns false if it couldn't. */
interface Hands {
    /** Scrolls the main list on screen down by a page. */
    suspend fun scrollDown(): Boolean
    /** Opens [pkg] as its home-screen icon would. */
    fun openApp(pkg: String): Boolean
    /** Opens [pkg]'s install page in the Play Store. */
    fun openStorePage(pkg: String): Boolean
}

object NoHands : Hands {
    override suspend fun scrollDown() = false
    override fun openApp(pkg: String) = false
    override fun openStorePage(pkg: String) = false
}

/** Localised lines the session speaks without asking the server. */
interface Lines {
    val noSpeech: String
    val network: String
    val micDenied: String
    val sensitive: String get() = "Do this part yourself. Never share your PIN or OTP."
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
    /** Said while Saathi scrolls to find the next button itself. */
    val scrolling: String get() = ""
    /** Asked when a task is done, before listening once more. Blank: just rest. */
    val anythingElse: String get() = ""
    /** Said when the senior answers that with "no", "that's all", "बस"… */
    val goodbye: String get() = ""
}

enum class GuideState { IDLE, LISTENING, THINKING, SPEAKING, WATCHING }

/**
 * How the senior changed the screen. Each has its own settle time before we look again.
 * [progress] events can move the task on without changing the screen's text (pressing "+" on a
 * font slider), so they are always answered; the others are skipped if nothing visibly changed.
 */
enum class ScreenEvent(val settleMs: Long, val progress: Boolean) {
    CLICK(500, true),
    NAVIGATED(500, false),
    SCROLLED(600, false),
    TYPED(3000, false),
    CONTENT_CHANGED(800, false),
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
    private val hands: Hands = NoHands,
    private val reportFailure: (Exception) -> Unit = {},
) {
    companion object {
        const val MAX_AUTO_TURNS = 15
        /** How long a screen gets to finish loading after the senior acts, in looks 250ms apart. */
        const val SETTLE_TRIES = 6
        const val SETTLE_STEP_MS = 250L
        /** After Saathi scrolls or opens an app silently, the screen gets this long to move. */
        const val ACT_SETTLE_MS = 600L
        /** How long an app Saathi opened may take to come up. */
        const val APP_START_MS = 6_000L
        const val PLAY_STORE = "com.android.vending"
        /**
         * Screen changes this soon after Saathi's own scroll or app launch are its own doing (an app
         * can take a second to finish opening), not the senior's. A real tap still counts.
         */
        const val OWN_CHANGE_MS = 2_000L
        const val WATCH_TIMEOUT_MS = 120_000L
        const val HISTORY = 8
        const val POINT_RADIUS_PX = 90
        /** After this long without an answer, say "one moment". */
        const val WAIT_HINT_MS = 3_500L
        /** A beat after "done" before asking for anything else. */
        const val FOLLOW_UP_PAUSE_MS = 1_500L
        private val CLOCK_LABEL = Regex("\\p{Nd}{1,2}:\\p{Nd}{2}(?::\\p{Nd}{2})?")
        /** From the first opening quote to the end: the example a search box cycles through. */
        private val QUOTED_TAIL = Regex("[\"“‘'].*$")
        /** How long a moving page gets to stop before the hand follows its button. */
        const val FOLLOW_SETTLE_MS = 450L
        /** Looks, [SETTLE_STEP_MS] apart, that check a freshly shown hand is still on its button. */
        const val POINT_GUARD_LOOKS = 6

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
    /** True while Saathi is scrolling or opening an app itself, so its own screen changes don't count as the senior's. */
    private var acting = false
    /** Active for [OWN_CHANGE_MS] after Saathi acted. */
    private var ownChange: Job? = null
    /** Changes while a reply is pending invalidate it, even if a tap left the text unchanged. */
    private var screenVersion = 0L
    private var pendingContent = false
    private var settlingEvent: ScreenEvent? = null
    /** Moves the hand after its button while a step is being said: see [followTarget]. */
    private var follow: Job? = null
    /** Takes the hand away if its button disappears just after it was shown: see [guardPoint]. */
    private var pointGuard: Job? = null

    fun onBubbleTap() {
        if (_state.value == GuideState.LISTENING) {
            ears.finish()
            return
        }
        launchPipeline { spokenTurn() }
    }

    fun onScreenEvent(event: ScreenEvent) {
        val s = _state.value
        if (s != GuideState.WATCHING && s != GuideState.SPEAKING && s != GuideState.THINKING) return
        if (lastGuide?.danger == Danger.SCAM) return
        if (acting || (event != ScreenEvent.CLICK && ownChange?.isActive == true)) return
        if (inCall()) {
            // A call started (often the one the senior was just helped to make): get out of the way.
            stop()
            return
        }
        if (event == ScreenEvent.CONTENT_CHANGED) {
            // A loading spinner or live region must not cancel narration or reset a typing pause.
            // Replies already revalidate their screen after THINKING.
            if (s == GuideState.THINKING) return
            if (s == GuideState.SPEAKING) {
                pendingContent = true
                followTarget()
                return
            }
            if (settlingEvent != null && settlingEvent != ScreenEvent.CONTENT_CHANGED) return
        }
        if (s == GuideState.THINKING) {
            screenVersion++
            return
        }
        if (autoTurns >= MAX_AUTO_TURNS) return
        // The senior acted, so the old spotlight is probably pointing at something that moved or is gone.
        pointGuard?.cancel()
        pointer.clear()
        launchPipeline(settleEvent = event) {
            setState(GuideState.WATCHING)
            delay(event.settleMs)
            settlingEvent = null
            setState(GuideState.THINKING)
            val snap = lookSettled()
            if (leftForHome(snap)) {
                // The senior pressed Home in the middle of a task: they're done with it, so Saathi
                // steps back instead of pulling them into the app again.
                reset()
                return@launchPipeline
            }
            if (!event.progress && unchanged(snap)) {
                // A scroll that went nowhere or a dialog that flickered: keep the same step on
                // screen, without asking again or repeating it out loud.
                val guide = lastGuide!!
                showStep(guide, snap)
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
            if (again.app.isNotEmpty() && sameScreen(snap, again)) return again
            if (again.app.isNotEmpty() || snap.app.isEmpty()) snap = again
        }
        return snap
    }

    /** On the home screen now, from an app, and not because Saathi said "press Home first". */
    private fun leftForHome(snap: Snapshot, before: String? = lastLook?.app): Boolean {
        before ?: return false
        return RecipeEngine.isLauncher(snap.app) && !RecipeEngine.isLauncher(before) && before != snap.app &&
            lastGuide?.expect?.endsWith("/find") != true
    }

    /**
     * Looks until [pkg] is on screen, then until it settles. A cold start on a budget phone takes
     * seconds; looking too soon sees the home screen and would open the app all over again.
     */
    private suspend fun lookFor(pkg: String): Snapshot {
        var waited = 0L
        var snap = eyes.look()
        while (snap.app != pkg && waited < APP_START_MS) {
            delay(SETTLE_STEP_MS)
            waited += SETTLE_STEP_MS
            snap = eyes.look()
        }
        return if (snap.app == pkg) lookSettled() else snap
    }

    private fun unchanged(snap: Snapshot): Boolean {
        val last = lastLook ?: return false
        return lastGuide != null && sameScreen(snap, last) && ScreenKey.informative(snap.tree)
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
        val guide = lastGuide
        launchPipeline {
            setState(GuideState.SPEAKING)
            mouth.say(say, slow = true)
            if (guide == null) setState(GuideState.IDLE) else afterGuide(guide)
        }
    }

    fun stop() {
        job?.cancel()
        reset()
    }

    /** Speaks a local warning (scam guard) regardless of what the session is doing. */
    fun warn(text: String) {
        launchPipeline {
            speakWarning(Guide(say = text, danger = Danger.SCAM, status = Status.CANNOT_HELP))
        }
    }

    private suspend fun speakWarning(guide: Guide) {
        reset()
        lastSay = guide.say
        lastGuide = guide.copy(goal = "")
        pointer.point(null, guide.say, Danger.SCAM)
        setState(GuideState.SPEAKING)
        mouth.say(guide.say)
        setState(GuideState.IDLE)
    }

    private fun launchPipeline(settleEvent: ScreenEvent? = null, block: suspend () -> Unit) {
        val previous = job
        settlingEvent = settleEvent
        if (settleEvent != null) pendingContent = false
        watchTimeout?.cancel()
        mouth.stop()
        job = scope.launch {
            previous?.cancelAndJoin()
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(e)
                reset()
                try {
                    speakAndIdle(failureLine(e))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportFailure(e)
                    reset()
                }
            }
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

    /** [speaking] is a line still being said (Saathi announcing its own scroll); the next step waits for it. */
    private suspend fun runTurn(utterance: String?, seen: Snapshot? = null, speaking: Job? = null) {
        setState(GuideState.THINKING)
        val version = screenVersion
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

        speaking?.join()
        if (inCall()) {
            reset()
            return
        }
        // Events can be delayed or absent during app switches. Check the actual screen too,
        // before recording this reply, drawing a pointer, or performing an automatic action.
        val current = eyes.look()
        // Screenshot throttling can briefly hide a window's secure flag. Once either look
        // identifies this app's screen as protected, hand it back instead of retrying the model.
        val protectedScreen = snap.app == current.app && (snap.secure || current.secure)
        if (!protectedScreen && (screenVersion != version || !sameScreen(snap, current))) {
            if (leftForHome(current, snap.app) || autoTurns >= MAX_AUTO_TURNS) {
                reset()
                return
            }
            autoTurns++
            val settled = lookSettled()
            if (leftForHome(settled, snap.app)) {
                reset()
                return
            }
            runTurn(utterance, seen = settled)
            return
        }
        val responseGuide = if (protectedScreen && response.guide.danger != Danger.SCAM)
            response.guide.copy(status = Status.USER_MUST_DO, danger = Danger.SENSITIVE) else response.guide
        val guide = safeGuide(responseGuide, current)
        if (guide.danger == Danger.SCAM) {
            speakWarning(guide)
            return
        }
        if (guide.goal.isNotBlank()) goal = guide.goal
        history.addLast(PastStep(guide.say, guide.label, guide.status, snap.app))
        while (history.size > HISTORY) history.removeFirst()
        lastSay = guide.say
        lastLook = snap
        lastGuide = guide

        if (guide.action.isNotEmpty() && act(guide)) return

        showStep(guide, current, done = guide.status == Status.DONE)
        setState(GuideState.SPEAKING)
        mouth.say(guide.say)

        afterGuide(guide)
    }

    private suspend fun afterGuide(guide: Guide) {
        when {
            guide.danger == Danger.SCAM -> setState(GuideState.IDLE) // caption stays until closed
            guide.status == Status.NEED_INFO -> spokenTurn()
            guide.status == Status.DONE -> followUp()
            guide.status == Status.CANNOT_HELP -> {
                delay(readingTimeMs(guide.say))
                reset()
            }
            else -> enterWatching() // next_step, user_must_do
        }
    }

    /**
     * Does [guide]'s action for the senior, says so briefly, and moves on to the next step. Returns
     * false if it couldn't (nothing to scroll, app not installed): the step is then spoken as usual.
     */
    private suspend fun act(guide: Guide): Boolean = coroutineScope {
        acting = true
        val speaking: Job?
        val seen: Snapshot
        try {
            val done = when (guide.action) {
                Action.SCROLL_DOWN -> hands.scrollDown()
                Action.OPEN_APP -> hands.openApp(guide.actionArg)
                Action.OPEN_STORE_PAGE -> hands.openStorePage(guide.actionArg)
                else -> false
            }
            if (!done) return@coroutineScope false
            val line = if (guide.action == Action.SCROLL_DOWN) lines().scrolling else guide.say
            // A run of scrolls is announced once, not every page.
            val repeated = history.size >= 2 && history[history.size - 2].say == guide.say
            pointer.point(null, line, Danger.NONE)
            setState(GuideState.SPEAKING)
            // The line is said while Saathi looks at the moved screen, so the next step is ready
            // the moment it ends instead of a look and a think later.
            speaking = if (line.isNotBlank() && !repeated) launch { mouth.say(line) } else null
            ownChange?.cancel()
            ownChange = scope.launch { delay(OWN_CHANGE_MS) }
            delay(ACT_SETTLE_MS)
            seen = when (guide.action) {
                Action.OPEN_APP -> lookFor(guide.actionArg)
                Action.OPEN_STORE_PAGE -> lookFor(PLAY_STORE)
                else -> lookSettled()
            }
        } finally {
            // runTurn turns THINKING straight away, which ignores screen events on its own.
            acting = false
        }
        if (guide.status == Status.DONE || autoTurns >= MAX_AUTO_TURNS) {
            // Opening an app that was asked for is the whole task: let the line finish, then ask
            // if there is anything else.
            speaking?.join()
            if (guide.status == Status.DONE) followUp() else reset()
            return@coroutineScope true
        }
        autoTurns++
        runTurn(utterance = null, seen = seen, speaking = speaking)
        true
    }

    /**
     * A task is done: asks if there is anything else and listens once, so the senior doesn't have
     * to find Help again. A new request starts a new task; "no", "that's all", "बस", "thank you"
     * get a short goodbye; silence just rests.
     */
    private suspend fun followUp() {
        delay(FOLLOW_UP_PAUSE_MS)
        reset()
        val ask = lines().anythingElse
        if (ask.isBlank() || !hasMic() || inCall()) return
        pointer.point(null, ask, Danger.NONE)
        setState(GuideState.SPEAKING)
        mouth.say(ask)
        setState(GuideState.LISTENING)
        val heard = ears.listen()
        pointer.clear()
        when {
            heard !is Heard.Words -> reset()
            Closing.isClosing(heard.text) -> speakAndIdle(lines().goodbye)
            else -> {
                silences = 0
                runTurn(heard.text)
            }
        }
    }

    private fun enterWatching() {
        setState(GuideState.WATCHING)
        watchTimeout?.cancel()
        watchTimeout = scope.launch {
            delay(WATCH_TIMEOUT_MS)
            if (_state.value == GuideState.WATCHING) reset()
        }
        if (pendingContent) {
            pendingContent = false
            onScreenEvent(ScreenEvent.CONTENT_CHANGED)
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
        lastSay = text
        lastGuide = null
        pointer.point(null, text, danger)
        setState(GuideState.SPEAKING)
        mouth.say(text)
        setState(GuideState.IDLE)
    }

    private fun reset() {
        watchTimeout?.cancel()
        pointGuard?.cancel()
        follow?.cancel()
        ownChange?.cancel()
        ownChange = null
        mouth.stop()
        pointer.clear()
        goal = ""
        history.clear()
        autoTurns = 0
        lastLook = null
        lastGuide = null
        lastSay = null
        pendingContent = false
        settlingEvent = null
        silences = 0
        setState(GuideState.IDLE)
    }

    private fun setState(s: GuideState) {
        _state.value = s
        pointer.state(s)
    }

    private fun sameScreen(a: Snapshot, b: Snapshot): Boolean =
        a.app == b.app && sameTree(a.tree, b.tree) && a.secure == b.secure &&
            a.screenW == b.screenW && a.screenH == b.screenH && a.shotBox == b.shotBox

    private fun sameTree(a: String, b: String): Boolean {
        if (a == b) return true
        val before = ScreenText.parse(a).nodes
        val after = ScreenText.parse(b).nodes
        if (before.isEmpty() || before.size != after.size) return false
        return before.zip(after).all { (old, now) ->
            old == now || (!old.clickable && !now.clickable &&
                CLOCK_LABEL.matches(old.label) && CLOCK_LABEL.matches(now.label) &&
                old.copy(label = "") == now.copy(label = "")) ||
                rotatingHint(old, now)
        }
    }

    /**
     * A search box cycling its example ("Search "milk"" → "Search "stationery"", typed out a letter
     * at a time) in the same place: not something the senior did, and not a new screen.
     */
    private fun rotatingHint(old: TreeNode, now: TreeNode): Boolean {
        val a = QUOTED_TAIL.find(old.label) ?: return false
        val b = QUOTED_TAIL.find(now.label) ?: return false
        val stem = old.label.substring(0, a.range.first)
        return stem.isNotBlank() && stem == now.label.substring(0, b.range.first) &&
            old.copy(label = "") == now.copy(label = "")
    }

    /**
     * The page moved under a step while it was being said (a banner collapsing above it): put the
     * hand back on the same button where it is now, without saying the step again. The pending
     * look after speaking then compares against the moved page, so it isn't taken as a new screen.
     */
    private fun followTarget() {
        val guide = lastGuide ?: return
        val before = lastLook ?: return
        if (guide.targetKind != "node") return
        val old = ScreenText.parse(before.tree).nodes.firstOrNull { it.id == guide.nodeId } ?: return
        follow?.cancel()
        follow = scope.launch {
            delay(FOLLOW_SETTLE_MS)
            if (_state.value != GuideState.SPEAKING || lastGuide !== guide) return@launch
            val snap = eyes.look()
            if (snap.app != before.app || snap.secure) return@launch
            val moved = ScreenText.parse(snap.tree).nodes
                .filter { it.label == old.label && it.role == old.role && it.clickable }
                .minByOrNull { abs(it.box.top - old.box.top) + abs(it.box.left - old.box.left) } ?: return@launch
            if (moved.box == old.box) return@launch
            val updated = guide.copy(nodeId = moved.id)
            val box = targetBox(updated, snap) ?: return@launch
            if (_state.value != GuideState.SPEAKING || lastGuide !== guide) return@launch
            lastGuide = updated
            lastLook = snap
            pointer.point(box, guide.say, guide.danger)
            guardPoint(updated, snap)
        }
    }

    /** Shows [guide] with its hand on the target found in [snap], then keeps checking the hand is still right. */
    private fun showStep(guide: Guide, snap: Snapshot, done: Boolean = false) {
        pointer.point(targetBox(guide, snap), guide.say, guide.danger, done)
        guardPoint(guide, snap)
    }

    /**
     * A look can land in the middle of a page change: the tree still shows the old page while the
     * new one is drawing. Blinkit's "View cart" was re-shown that way as checkout opened, and the
     * hand sat where "Place Order" then appeared. So for a moment after the hand goes up, look
     * again: once its button is no longer there (same text, same place), take the hand away and
     * keep the words. The next screen event brings the right step.
     */
    private fun guardPoint(guide: Guide, basis: Snapshot) {
        pointGuard?.cancel()
        val box = targetBox(guide, basis) ?: return
        val target = ScreenText.parse(basis.tree).nodes.firstOrNull { it.id == guide.nodeId && it.box == box }
        pointGuard = scope.launch {
            repeat(POINT_GUARD_LOOKS) {
                delay(SETTLE_STEP_MS)
                if (lastGuide !== guide) return@launch
                val now = eyes.look()
                if (now.app.isEmpty()) return@repeat
                val still = target != null && now.app == basis.app && !now.secure &&
                    ScreenText.parse(now.tree).nodes.any {
                        it.box == target.box && it.clickable && (it.label == target.label || rotatingHint(target, it))
                    }
                if (!still) {
                    if (lastGuide === guide) pointer.point(null, guide.say, guide.danger)
                    return@launch
                }
            }
        }
    }

    private fun sensitiveTarget(guide: Guide, snap: Snapshot): Boolean {
        if (ScreenSafety.isSensitiveTarget(guide.label)) return true
        val nodes = ScreenText.parse(snap.tree).nodes
        return when (guide.targetKind) {
            "node" -> nodes.any { it.id == guide.nodeId && ScreenSafety.isSensitiveTarget(it.label) }
            "point" -> {
                val x = snap.shotBox.left + guide.x * snap.shotBox.width
                val y = snap.shotBox.top + guide.y * snap.shotBox.height
                nodes.any {
                    x >= it.box.left && x < it.box.right && y >= it.box.top && y < it.box.bottom &&
                        ScreenSafety.isSensitiveTarget(it.label)
                }
            }
            else -> false
        }
    }

    /** Enforce this on the phone too: old caches and Worker versions may still contain targets. */
    private fun safeGuide(guide: Guide, snap: Snapshot): Guide {
        val sensitive = snap.secure || guide.danger == Danger.SENSITIVE ||
            guide.status == Status.USER_MUST_DO || sensitiveTarget(guide, snap)
        if (!sensitive && guide.danger != Danger.SCAM) return guide
        val safe = guide.copy(targetKind = "none", nodeId = "", x = -1.0, y = -1.0, action = "", actionArg = "")
        if (guide.danger == Danger.SCAM) return safe
        // A recipe's own hand-back line was written for this screen ("check the address, pay
        // yourself, keep your PIN secret") and is kept; anything else gets the fixed line.
        val scriptedHandBack = guide.expect.startsWith("recipe ") && guide.status == Status.USER_MUST_DO &&
            guide.danger == Danger.SENSITIVE && guide.targetKind == "none" && guide.action.isEmpty()
        return if (scriptedHandBack) safe else safe.copy(
            say = lines().sensitive, status = Status.USER_MUST_DO, danger = Danger.SENSITIVE,
        )
    }

    internal fun targetBox(guide: Guide, snap: Snapshot): Box? {
        if (snap.secure || guide.danger != Danger.NONE || guide.status == Status.USER_MUST_DO || sensitiveTarget(guide, snap)) return null
        return when (guide.targetKind) {
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
    }

    internal val currentGoal get() = goal
}
