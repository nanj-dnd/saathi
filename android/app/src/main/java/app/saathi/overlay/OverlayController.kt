package app.saathi.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import app.saathi.Prefs
import app.saathi.R
import app.saathi.capture.Box
import app.saathi.net.Danger
import app.saathi.session.GuideState
import app.saathi.session.Pointer

/**
 * Owns the three accessibility-overlay windows. TYPE_ACCESSIBILITY_OVERLAY needs no
 * SYSTEM_ALERT_WINDOW permission and draws above every app.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val prefs: Prefs,
    /** Resources in the senior's chosen language. */
    private val strings: () -> Context,
    onBubbleTap: () -> Unit,
    private val onRepeat: () -> Unit,
    private val onClose: () -> Unit,
) : Pointer {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val screen get() = wm.currentWindowMetrics.bounds

    private val pointer = PointerView(service).apply { visibility = View.GONE }
    private val caption = CaptionView(service, { onRepeat() }, { onClose() }).apply { visibility = View.GONE }
    private val bubble = BubbleView(service, onBubbleTap, ::dragBubble, ::saveBubble)

    private val pointerParams = params(LP.MATCH_PARENT, LP.MATCH_PARENT, touchable = false)
    private val captionParams = params(LP.MATCH_PARENT, LP.WRAP_CONTENT, touchable = true)
    private val bubbleParams = params(LP.WRAP_CONTENT, LP.WRAP_CONTENT, touchable = true)

    private var state = GuideState.IDLE
    private var attached = false

    fun show() {
        if (attached) return
        val margin = service.dp(12f).toInt()
        captionParams.width = screen.width() - margin * 2
        captionParams.x = margin
        bubbleParams.x = prefs.bubbleX.takeIf { it >= 0 } ?: (screen.width() - service.dp(112f).toInt())
        bubbleParams.y = prefs.bubbleY.takeIf { it >= 0 } ?: (screen.height() * 0.62f).toInt()
        wm.addView(pointer, pointerParams)
        wm.addView(caption, captionParams)
        wm.addView(bubble, bubbleParams)
        attached = true
        state(GuideState.IDLE)
    }

    fun remove() {
        if (!attached) return
        listOf(bubble, caption, pointer).forEach { runCatching { wm.removeView(it) } }
        attached = false
    }

    override fun state(state: GuideState) {
        this.state = state
        val res = strings()
        val label = when (state) {
            GuideState.LISTENING -> res.getString(R.string.bubble_listening)
            GuideState.THINKING -> res.getString(R.string.bubble_looking)
            else -> res.getString(R.string.bubble_help)
        }
        bubble.render(state, label)
        // Keep our own drawing out of the screenshot the model is about to see.
        if (state == GuideState.THINKING || state == GuideState.LISTENING) {
            pointer.clear()
            caption.visibility = View.GONE
        }
    }

    override fun point(target: Box?, caption: String, danger: String) {
        if (!attached) return
        val res = strings()
        val color = when (danger) {
            Danger.SCAM -> Palette.SCAM
            Danger.SENSITIVE -> Palette.SENSITIVE
            else -> Palette.CARD
        }
        this.caption.render(caption, color, res.getString(R.string.btn_repeat), res.getString(R.string.btn_close))

        // Caption goes on the half of the screen away from the target.
        val targetLow = target != null && target.centerY > screen.height() / 2
        captionParams.gravity = (if (targetLow) Gravity.TOP else Gravity.BOTTOM) or Gravity.START
        captionParams.y = service.dp(if (targetLow) 36f else 24f).toInt()
        wm.updateViewLayout(this.caption, captionParams)
        this.caption.visibility = View.VISIBLE

        if (target != null && danger != Danger.SCAM) {
            pointer.pointAt(target, bubbleBox())
        } else {
            pointer.clear()
        }
    }

    override fun clear() {
        pointer.clear()
        caption.visibility = View.GONE
    }

    private fun bubbleBox() = Box(
        bubbleParams.x, bubbleParams.y,
        bubbleParams.x + bubble.width, bubbleParams.y + bubble.height,
    )

    private fun dragBubble(dx: Int, dy: Int) {
        val s = screen
        bubbleParams.x = (bubbleParams.x + dx).coerceIn(0, s.width() - bubble.width)
        bubbleParams.y = (bubbleParams.y + dy).coerceIn(0, s.height() - bubble.height)
        wm.updateViewLayout(bubble, bubbleParams)
    }

    private fun saveBubble() {
        prefs.bubbleX = bubbleParams.x
        prefs.bubbleY = bubbleParams.y
    }

    private fun params(w: Int, h: Int, touchable: Boolean) = LP(
        w, h, LP.TYPE_ACCESSIBILITY_OVERLAY,
        LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS or
            LP.FLAG_NOT_TOUCH_MODAL or (if (touchable) 0 else LP.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }
}
