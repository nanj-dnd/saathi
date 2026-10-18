package app.saathi.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.view.accessibility.AccessibilityWindowInfo
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
    private val bubble = BubbleView(service, onBubbleTap, ::dragBubble, ::snapBubble)

    private val pointerParams = params(LP.MATCH_PARENT, LP.MATCH_PARENT, touchable = false)
    private val captionParams = params(LP.MATCH_PARENT, LP.WRAP_CONTENT, touchable = true)
    private val bubbleParams = params(LP.WRAP_CONTENT, LP.WRAP_CONTENT, touchable = true)

    private var state = GuideState.IDLE
    private var attached = false

    fun show() {
        if (attached) return
        sizeCaption()
        placeBubbleWhereSaved()
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

    /** After rotation or a fold: fit the caption to the new width and keep the bubble on screen. */
    fun onScreenResized() {
        if (!attached) return
        clear()
        sizeCaption()
        wm.updateViewLayout(caption, captionParams)
        placeBubbleWhereSaved()
        wm.updateViewLayout(bubble, bubbleParams)
    }

    /** Hidden while Saathi's own setup screen is in front; shown again over every other app. */
    fun setBubbleVisible(visible: Boolean) {
        if (attached) bubble.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** The mic just opened: a buzz, so the senior knows to start talking even without looking. */
    fun listeningCue() {
        if (attached) bubble.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
        if (attached) {
            if (state == GuideState.IDLE) {
                // Back to idle: the pill returns to where the senior put it, if a step had moved it.
                placeBubbleWhereSaved()
            } else if (bubbleParams.x > 0) {
                // The pill grows and shrinks with its word; one on the right keeps its right edge on the screen edge.
                bubbleParams.x = (screen.width() - bubble.wantedWidth).coerceAtLeast(0)
            }
            wm.updateViewLayout(bubble, bubbleParams)
        }
        // Keep our own drawing out of the screenshot the model is about to see.
        if (state == GuideState.THINKING || state == GuideState.LISTENING) {
            pointer.clear()
            caption.visibility = View.GONE
        }
    }

    override fun point(target: Box?, caption: String, danger: String, done: Boolean) {
        if (!attached) return
        val res = strings()
        // The badge's shape carries the state, so nothing depends on telling colours apart.
        val kind = when {
            danger == Danger.SCAM -> SignKind.WARNING
            danger == Danger.SENSITIVE -> SignKind.YOURSELF
            done -> SignKind.DONE
            target == null -> SignKind.NOTE
            else -> SignKind.STEP
        }
        this.caption.render(caption, kind, res.getString(R.string.btn_repeat), res.getString(R.string.btn_close))
        placeCaption(if (danger == Danger.SCAM) null else target)
        this.caption.visibility = View.VISIBLE
        moveBubbleOffCaption()

        if (target != null && danger != Danger.SCAM) {
            moveBubbleOffTarget(target)
            pointer.pointAt(target, bubbleBox())
        } else {
            pointer.clear()
        }
        // A buzz with every new step helps seniors who don't hear well; a stronger one for warnings.
        bubble.performHapticFeedback(if (danger == Danger.SCAM) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.CONFIRM)
    }

    /**
     * The caption floats at the top of the screen, below the status bar, unless that would cover
     * the target; then it floats at the bottom, above the navigation bar or an open keyboard (our
     * overlay draws above the keyboard, so it must never sit on the keys).
     */
    private fun placeCaption(target: Box?) {
        val s = screen
        val keyboardTop = keyboardTop()
        caption.measure(
            View.MeasureSpec.makeMeasureSpec(s.width(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val topY = statusBarInset() - service.dp(2f).toInt()
        // The spotlight reaches a little past the target, so leave it room too.
        val coversTarget = target != null && target.top < topY + caption.measuredHeight + service.dp(8f)
        captionParams.x = 0
        captionParams.width = s.width()
        if (!coversTarget) {
            captionParams.gravity = Gravity.TOP or Gravity.START
            captionParams.y = topY
        } else {
            captionParams.gravity = Gravity.BOTTOM or Gravity.START
            captionParams.y = (if (keyboardTop != null) s.height() - keyboardTop else navBarInset()) - service.dp(2f).toInt()
        }
        wm.updateViewLayout(caption, captionParams)
    }

    private fun statusBarInset(): Int =
        wm.currentWindowMetrics.windowInsets.getInsets(
            android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.displayCutout(),
        ).top

    private fun navBarInset(): Int =
        wm.currentWindowMetrics.windowInsets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom

    private fun keyboardTop(): Int? = runCatching {
        service.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?.let { Rect().also(it::getBoundsInScreen).top }
    }.getOrNull()

    /**
     * If the pill sits where the caption now floats (a senior who keeps it at the top), it steps just
     * clear of the card for this step, and goes back to the senior's spot at idle.
     */
    private fun moveBubbleOffCaption() {
        val s = screen
        val capH = caption.measuredHeight
        val top = captionParams.gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.TOP
        val capTop = if (top) captionParams.y else s.height() - captionParams.y - capH
        val capBottom = capTop + capH
        val h = bubbleHeight()
        if (bubbleParams.y >= capBottom || bubbleParams.y + h <= capTop) return
        bubbleParams.y = (if (top) capBottom else capTop - h).coerceIn(0, (s.height() - h).coerceAtLeast(0))
        wm.updateViewLayout(bubble, bubbleParams)
    }

    /** If the bubble sits on the button the senior must press, slide it to the other side. */
    private fun moveBubbleOffTarget(target: Box) {
        val margin = service.dp(24f).toInt()
        val b = bubbleBox()
        val overlaps = b.left < target.right + margin && b.right > target.left - margin &&
            b.top < target.bottom + margin && b.bottom > target.top - margin
        if (!overlaps) return
        val s = screen
        val w = bubble.wantedWidth
        val h = bubbleHeight()
        // Opposite horizontal side first; if the target spans the width, go above or below it.
        bubbleParams.x = if (target.centerX > s.width() / 2) 0 else s.width() - w
        if (target.width > s.width() - w * 2) {
            bubbleParams.y = if (target.centerY > s.height() / 2) (target.top - h - margin).coerceAtLeast(0)
            else (target.bottom + margin).coerceAtMost(s.height() - h)
        }
        wm.updateViewLayout(bubble, bubbleParams)
    }

    private fun sizeCaption() {
        captionParams.width = screen.width()
        captionParams.x = 0
    }

    private fun bubbleHeight() = bubble.height.takeIf { it > 0 } ?: service.dp(84f).toInt()

    /** The pill sits against the side the senior last left it on (the right, at first), at their height. */
    private fun placeBubbleWhereSaved() {
        val s = screen
        val w = bubble.wantedWidth
        val h = bubbleHeight()
        bubbleParams.x = if (prefs.bubbleX == 0) 0 else (s.width() - w).coerceAtLeast(0)
        bubbleParams.y = (prefs.bubbleY.takeIf { it >= 0 } ?: (s.height() * 0.62f).toInt()).coerceIn(0, (s.height() - h).coerceAtLeast(0))
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

    /** After a drag the pill settles against the nearer side of the screen, out of the way. */
    private fun snapBubble() {
        val s = screen
        bubbleParams.x = if (bubbleParams.x + bubble.width / 2 < s.width() / 2) 0 else s.width() - bubble.width
        wm.updateViewLayout(bubble, bubbleParams)
        saveBubble()
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
