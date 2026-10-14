package app.saathi.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import app.saathi.BuildConfig
import app.saathi.Languages
import app.saathi.Prefs
import app.saathi.R
import app.saathi.capture.ScreenReader
import app.saathi.net.WorkerClient
import app.saathi.overlay.OverlayController
import app.saathi.playbooks.PlaybookStore
import app.saathi.safety.ScamGuard
import app.saathi.session.GuideSession
import app.saathi.session.Lines
import app.saathi.session.ScreenEvent
import app.saathi.voice.SpeechListener
import app.saathi.voice.Speaker
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import java.io.File

/**
 * Everything Saathi does runs inside this service: it reads the screen, hosts the overlay, and
 * watches for the senior's taps so it can give the next step. It never performs actions.
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
        speaker = Speaker(this) { prefs.language }
        val playbooks = PlaybookStore(assets)
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
            ),
            mouth = speaker,
            pointer = overlay,
            brain = WorkerClient(
                baseUrl = BuildConfig.WORKER_URL,
                prefs = prefs,
                fixtureDir = if (BuildConfig.DEBUG) File(getExternalFilesDir(null), "fixtures") else null,
            ),
            playbook = playbooks::forApp,
            language = { prefs.language },
            lines = {
                val res = localized()
                object : Lines {
                    override val noSpeech = res.getString(R.string.say_no_speech)
                    override val network = res.getString(R.string.say_network)
                    override val micDenied = res.getString(R.string.say_mic_denied)
                }
            },
            hasMic = { checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
        )
        overlay.show()
        if (BuildConfig.DEBUG) {
            // adb shell am broadcast -a app.saathi.DEBUG_ASK --es text "how do I make text bigger"
            registerReceiver(debugAsk, IntentFilter(DEBUG_ASK), RECEIVER_EXPORTED)
        }
    }

    private val debugAsk = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra("text")?.let(session::ask)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::session.isInitialized) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in IGNORED || "inputmethod" in pkg) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (scamGuard.shouldWarn(pkg)) {
                    session.warn(localized().getString(R.string.say_scam_remote))
                    return
                }
                session.onScreenEvent(ScreenEvent.NAVIGATED)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> session.onScreenEvent(ScreenEvent.CLICK)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> session.onScreenEvent(ScreenEvent.SCROLLED)
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> session.onScreenEvent(ScreenEvent.TYPED)
        }
    }

    override fun onInterrupt() {
        if (::session.isInitialized) session.stop()
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG && ::session.isInitialized) runCatching { unregisterReceiver(debugAsk) }
        if (::overlay.isInitialized) overlay.remove()
        if (::speaker.isInitialized) speaker.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val IGNORED = setOf("com.android.systemui")
        private const val DEBUG_ASK = "app.saathi.DEBUG_ASK"

        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(AccessibilityManager::class.java)
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        }
    }
}
