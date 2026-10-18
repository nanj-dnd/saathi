package app.saathi.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import app.saathi.BuildConfig
import app.saathi.Languages
import app.saathi.Prefs
import app.saathi.R
import app.saathi.brain.FileCacheStore
import app.saathi.brain.LocalFirstBrain
import app.saathi.brain.TurnCache
import app.saathi.capture.ScreenReader
import app.saathi.net.WorkerClient
import app.saathi.overlay.OverlayController
import app.saathi.playbooks.PlaybookStore
import app.saathi.recipes.RecipeSource
import app.saathi.safety.ScamGuard
import app.saathi.session.GuideSession
import app.saathi.session.Hands
import app.saathi.session.Lines
import app.saathi.session.ScreenEvent
import app.saathi.voice.CloudVoice
import app.saathi.voice.SpeechListener
import app.saathi.voice.Speaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Everything Saathi does runs inside this service: it reads the screen, hosts the overlay, and
 * watches for the senior's taps so it can give the next step. It never taps for the senior: the
 * only things it does itself are scrolling a list and opening an app (see [hands]).
 */
class BuddyAccessibilityService : AccessibilityService() {
    private val scope = MainScope()
    private lateinit var prefs: Prefs
    private lateinit var overlay: OverlayController
    private lateinit var speaker: Speaker
    private lateinit var session: GuideSession
    private val scamGuard = ScamGuard()

    private fun localized(): Context = Languages.context(this, prefs.language)

    override fun onServiceConnected() {
        prefs = Prefs(this)
        val playbooks = PlaybookStore(assets)
        val recipes = RecipeSource(assets, File(filesDir, "recipes.json"))
        val worker = WorkerClient(
            baseUrl = BuildConfig.WORKER_URL,
            prefs = prefs,
            fixtureDir = if (BuildConfig.DEBUG) File(getExternalFilesDir(null), "fixtures") else null,
        )
        speaker = Speaker(this, language = { prefs.language }, rate = { prefs.speechRate }, cloud = CloudVoice(this, worker::speak))
        // Scripted recipes, then answers this phone already got, then the Worker.
        val brain = LocalFirstBrain(
            recipes = { recipes.book },
            cache = TurnCache(FileCacheStore(File(cacheDir, "turn-cache.json"))),
            remote = worker,
            sensitiveLine = { tag -> Languages.context(this, tag).getString(R.string.say_sensitive) },
            log = { if (BuildConfig.DEBUG) Log.d(TAG, it) },
            canOpen = { packageManager.getLaunchIntentForPackage(it) != null },
        )
        scope.launch {
            withContext(Dispatchers.IO) { recipes.load() }
            refreshRecipes(recipes, worker)
        }
        overlay = OverlayController(
            service = this,
            prefs = prefs,
            strings = ::localized,
            onBubbleTap = { session.onBubbleTap() },
            onRepeat = { session.repeat() },
            onClose = { session.stop() },
        )
        session = GuideSession(
            scope = scope,
            eyes = ScreenReader(this),
            ears = SpeechListener(
                context = this,
                language = { prefs.language },
                onStart = { MicService.start(this) },
                onEnd = { MicService.stop(this) },
                onReady = { overlay.listeningCue() },
            ),
            mouth = speaker,
            pointer = overlay,
            brain = brain,
            playbook = playbooks::forApp,
            language = { prefs.language },
            lines = {
                val res = localized()
                object : Lines {
                    override val noSpeech = res.getString(R.string.say_no_speech)
                    override val network = res.getString(R.string.say_network)
                    override val micDenied = res.getString(R.string.say_mic_denied)
                    override val noSpeechAgain = res.getString(R.string.say_no_speech_again)
                    override val micBusy = res.getString(R.string.say_mic_busy)
                    override val inCall = res.getString(R.string.say_in_call)
                    override val speechOffline = res.getString(R.string.say_speech_offline)
                    override val speechUnavailable = res.getString(R.string.say_speech_unavailable)
                    override val wait = res.getString(R.string.say_wait)
                    override val busy = res.getString(R.string.say_busy)
                    override val limit = res.getString(R.string.say_limit)
                    override val scrolling = res.getString(R.string.say_scrolling)
                }
            },
            hasMic = { checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
            inCall = ::inCall,
            hands = hands,
        )
        // A screen that turns off mid-task means the senior put the phone down: stop talking.
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        overlay.show()
        if (BuildConfig.DEBUG) {
            // adb shell "am broadcast -a app.saathi.DEBUG_ASK --es text 'how do I make text bigger'"
            // adb shell "am broadcast -a app.saathi.DEBUG_OPENED --es pkg com.anydesk.anydeskandroid"
            registerReceiver(debugAsk, IntentFilter().apply { addAction(DEBUG_ASK); addAction(DEBUG_OPENED) }, RECEIVER_EXPORTED)
        }
    }

    /** Scrolling and opening apps are the only things Saathi does itself; it never taps. */
    private val hands = object : Hands {
        override suspend fun scrollDown(): Boolean {
            val list = mainList() ?: return false
            return try {
                list.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id) ||
                    list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            } finally {
                @Suppress("DEPRECATION") list.recycle()
            }
        }

        override fun openApp(pkg: String): Boolean {
            val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return false
            return runCatching { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
    }

    /**
     * The screen's main vertical list: the largest visible scrollable that scrolls down, so a
     * banner carousel (wide and short) is never scrolled by mistake.
     */
    private fun mainList(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        val r = android.graphics.Rect()
        var seen = 0
        while (queue.isNotEmpty() && seen < 600) {
            val n = queue.removeFirst()
            seen++
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
            if (!n.isScrollable || !n.isVisibleToUser) continue
            n.getBoundsInScreen(r)
            val down = n.actionList.any {
                it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id || it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            val area = r.width() * r.height()
            if (down && r.height() > r.width() / 2 && area > bestArea) {
                best = n
                bestArea = area
            }
        }
        return best
    }

    private fun inCall(): Boolean {
        val mode = getSystemService(AudioManager::class.java)?.mode ?: return false
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (::session.isInitialized) session.stop()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::session.isInitialized) return
        // Rotation moves every button: re-measure the overlay and look at the screen again.
        overlay.onScreenResized()
        session.onScreenEvent(ScreenEvent.NAVIGATED)
    }

    /** At most once a day, picks up recipe fixes from the Worker. Failure just means trying tomorrow. */
    private fun refreshRecipes(recipes: RecipeSource, worker: WorkerClient) {
        val now = System.currentTimeMillis()
        if (now - prefs.recipesCheckedAt < RECIPE_REFRESH_MS) return
        scope.launch {
            try {
                val json = worker.fetchRecipes()
                if (withContext(Dispatchers.IO) { recipes.install(json) }) prefs.recipesCheckedAt = now
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.i(TAG, "recipes not refreshed: ${e.message}")
            }
        }
    }

    private val debugAsk = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                DEBUG_ASK -> intent.getStringExtra("text")?.let(session::ask)
                // Pretend an app came to the front, to exercise the scam guard without installing it.
                DEBUG_OPENED -> intent.getStringExtra("pkg")?.let(::onAppOpened)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::session.isInitialized) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) {
            // Saathi's own setup screen came to the front: end any guidance (its ring and sign belong
            // to the app the senior just left) and hide the disc so it doesn't cover setup.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.className?.startsWith("$packageName.ui") == true) {
                session.stop()
                overlay.setBubbleVisible(false)
            }
            return
        }
        if (pkg in IGNORED || "inputmethod" in pkg) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) overlay.setBubbleVisible(true)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onAppOpened(pkg)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> session.onScreenEvent(ScreenEvent.CLICK)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> session.onScreenEvent(ScreenEvent.SCROLLED)
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> session.onScreenEvent(ScreenEvent.TYPED)
        }
    }

    private fun onAppOpened(pkg: String) {
        if (scamGuard.shouldWarn(pkg)) {
            session.warn(localized().getString(R.string.say_scam_remote))
            return
        }
        session.onScreenEvent(ScreenEvent.NAVIGATED)
    }

    override fun onInterrupt() {
        if (::session.isInitialized) session.stop()
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG && ::session.isInitialized) runCatching { unregisterReceiver(debugAsk) }
        if (::session.isInitialized) runCatching { unregisterReceiver(screenOff) }
        if (::overlay.isInitialized) overlay.remove()
        if (::speaker.isInitialized) speaker.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Saathi"
        private const val RECIPE_REFRESH_MS = 24 * 60 * 60 * 1000L
        private val IGNORED = setOf("com.android.systemui")
        private const val DEBUG_ASK = "app.saathi.DEBUG_ASK"
        private const val DEBUG_OPENED = "app.saathi.DEBUG_OPENED"

        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(AccessibilityManager::class.java)
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        }
    }
}
