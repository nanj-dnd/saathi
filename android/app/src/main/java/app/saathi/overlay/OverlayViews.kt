package app.saathi.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.saathi.capture.Box
import app.saathi.session.GuideState
import kotlin.math.hypot

internal fun Context.dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

internal object Palette {
    const val SAFFRON = 0xFFE8590C.toInt()
    const val LISTEN = 0xFFD6336C.toInt()
    const val THINK = 0xFF1971C2.toInt()
    const val SPEAK = 0xFF2B8A3E.toInt()
    const val RING = 0xFFFFC300.toInt()
    const val SCAM = 0xFFC92A2A.toInt()
    const val SENSITIVE = 0xFFB35C00.toInt()
    const val CARD = 0xFF1B1B1F.toInt()
}

/** Big round "Help" button. Tap to talk; drag to move it out of the way. */
@SuppressLint("ViewConstructor")
internal class BubbleView(
    context: Context,
    private val onTap: () -> Unit,
    private val onDrag: (dx: Int, dy: Int) -> Unit,
    private val onDragEnd: () -> Unit,
) : View(context) {
    private val size = context.dp(84f).toInt()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(4f)
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }
    private var label = ""
    private var state = GuideState.IDLE
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1100
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init {
        contentDescription = "Saathi"
    }

    fun render(state: GuideState, label: String) {
        this.state = state
        this.label = label
        fill.color = when (state) {
            GuideState.LISTENING -> Palette.LISTEN
            GuideState.THINKING -> Palette.THINK
            GuideState.SPEAKING -> Palette.SPEAK
            else -> Palette.SAFFRON
        }
        contentDescription = label
        if (state == GuideState.LISTENING || state == GuideState.THINKING) animator.start() else animator.cancel()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val pad = context.dp(14f).toInt()
        setMeasuredDimension(size + pad * 2, size + pad * 2)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = size / 2f
        if (state == GuideState.LISTENING) {
            halo.color = Palette.LISTEN
            halo.alpha = ((1 - phase) * 110).toInt()
            canvas.drawCircle(cx, cy, r + phase * (width / 2f - r), halo)
        }
        fill.setShadowLayer(context.dp(6f), 0f, context.dp(2f), 0x66000000)
        canvas.drawCircle(cx, cy, r, fill)
        if (state == GuideState.THINKING) {
            val inset = context.dp(7f)
            canvas.drawArc(cx - r + inset, cy - r + inset, cx + r - inset, cy + r - inset, phase * 360f, 100f, false, arc)
        }
        // Shrink long labels ("सुन रहा हूँ…") to fit inside the circle.
        textPaint.textSize = context.dp(20f)
        val maxW = r * 1.6f
        while (textPaint.measureText(label) > maxW && textPaint.textSize > context.dp(11f)) {
            textPaint.textSize -= context.dp(1f)
        }
        val y = cy - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, cx, y, textPaint)
    }

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; lastX = e.rawX; lastY = e.rawY; dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && hypot(e.rawX - downX, e.rawY - downY) > slop * 2) dragging = true
                if (dragging) {
                    onDrag((e.rawX - lastX).toInt(), (e.rawY - lastY).toInt())
                    lastX = e.rawX; lastY = e.rawY
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) onDragEnd() else performClick()
            }
        }
        return true
    }

    // TalkBack's double-tap arrives here, not through onTouchEvent, so the tap action lives here.
    override fun performClick(): Boolean {
        super.performClick()
        onTap()
        return true
    }

    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isClickable = true
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
    }
}

/**
 * Full-screen, touch-through layer: dims everything except the target, draws a pulsing ring around
 * it and a pointing hand. The spotlight glides from its last position so the eye can follow it.
 */
internal class PointerView(context: Context) : View(context) {
    private val dim = Paint().apply { color = 0x73000000 }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(6f)
        color = Palette.RING
    }
    // A dark edge under the yellow ring keeps it visible on yellow and white screens.
    private val ringEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(10f)
        color = 0xCC1B1B1F.toInt()
    }
    /** Small targets (a 24dp icon) get a spotlight at least this big, so they're easy to find. */
    private val minSide = context.dp(64f)
    private val pulse = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(4f)
        color = Palette.RING
    }
    private val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.dp(56f)
        textAlign = Paint.Align.CENTER
    }
    private val pad = context.dp(10f)
    private val corner = context.dp(18f)
    private val path = Path().apply { fillType = Path.FillType.EVEN_ODD }

    private var from: RectF? = null
    private var to: RectF? = null
    private val current = RectF()
    private var travel = 1f
    private var pulsePhase = 0f
    private val loc = IntArray(2)

    private val pulseAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { pulsePhase = it.animatedValue as Float; invalidate() }
    }
    private var travelAnim: ValueAnimator? = null

    /** [target] is in screen pixels; [origin] is where the spotlight starts if nothing is showing. */
    fun pointAt(target: Box, origin: Box) {
        getLocationOnScreen(loc)
        val next = RectF(
            target.left - loc[0] - pad, target.top - loc[1] - pad,
            target.right - loc[0] + pad, target.bottom - loc[1] + pad,
        )
        if (next.width() < minSide) next.inset(-(minSide - next.width()) / 2, 0f)
        if (next.height() < minSide) next.inset(0f, -(minSide - next.height()) / 2)
        from = if (to != null) RectF(current) else RectF(
            origin.left - loc[0].toFloat(), origin.top - loc[1].toFloat(),
            origin.right - loc[0].toFloat(), origin.bottom - loc[1].toFloat(),
        )
        to = next
        travelAnim?.cancel()
        travelAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 550
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { travel = it.animatedValue as Float; invalidate() }
            start()
        }
        if (!pulseAnim.isStarted) pulseAnim.start()
        visibility = VISIBLE
    }

    fun clear() {
        travelAnim?.cancel()
        pulseAnim.cancel()
        from = null
        to = null
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        val a = from ?: return
        val b = to ?: return
        current.set(
            a.left + (b.left - a.left) * travel, a.top + (b.top - a.top) * travel,
            a.right + (b.right - a.right) * travel, a.bottom + (b.bottom - a.bottom) * travel,
        )
        path.reset()
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.addRoundRect(current, corner, corner, Path.Direction.CW)
        canvas.drawPath(path, dim)
        canvas.drawRoundRect(current, corner, corner, ringEdge)
        canvas.drawRoundRect(current, corner, corner, ring)

        if (travel >= 1f) {
            val grow = pad * 2.5f * pulsePhase
            pulse.alpha = ((1 - pulsePhase) * 255).toInt()
            canvas.drawRoundRect(
                current.left - grow, current.top - grow, current.right + grow, current.bottom + grow,
                corner + grow, corner + grow, pulse,
            )
        }

        // Hand below the target, or above it when the target is near the bottom.
        val bob = (if (pulsePhase < 0.5f) pulsePhase else 1 - pulsePhase) * pad * 2
        val below = current.bottom + hand.textSize < height - pad * 6
        val x = current.centerX().coerceIn(hand.textSize / 2, width - hand.textSize / 2)
        if (below) canvas.drawText("👆", x, current.bottom + hand.textSize + bob, hand)
        else canvas.drawText("👇", x, current.top - pad - bob, hand)
    }
}

/** Large caption with Repeat / Close, placed on the half of the screen away from the target. */
@SuppressLint("ViewConstructor", "SetTextI18n")
internal class CaptionView(
    context: Context,
    onRepeat: () -> Unit,
    onClose: () -> Unit,
) : LinearLayout(context) {
    // Large system font sizes are common among seniors; past 1.3× the caption would cover the screen.
    private val fontScale = context.resources.configuration.fontScale.coerceIn(1f, 1.3f)
    private val text = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26f * fontScale)
        setLineSpacing(0f, 1.15f)
        maxLines = 5
        ellipsize = TextUtils.TruncateAt.END
    }
    private val repeat = pill(context, onRepeat)
    private val close = pill(context, onClose)
    private val bg = GradientDrawable().apply { cornerRadius = context.dp(22f) }

    init {
        orientation = VERTICAL
        background = bg
        val p = context.dp(18f).toInt()
        setPadding(p, p, p, p)
        addView(text)
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END
            setPadding(0, context.dp(14f).toInt(), 0, 0)
            addView(repeat)
            addView(close, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(12f).toInt()
            })
        })
    }

    fun render(caption: String, color: Int, repeatLabel: String, closeLabel: String) {
        text.text = caption
        bg.setColor(color)
        repeat.text = "🔁  $repeatLabel"
        close.text = "✕  $closeLabel"
        // Screen readers should say "Repeat", not "counterclockwise arrows button Repeat".
        repeat.contentDescription = repeatLabel
        close.contentDescription = closeLabel
    }

    private fun pill(context: Context, onClick: () -> Unit) = Button(context).apply {
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f * fontScale)
        setTextColor(Palette.CARD)
        minHeight = context.dp(56f).toInt()
        val pad = context.dp(18f).toInt()
        setPadding(pad, 0, pad, 0)
        background = GradientDrawable().apply { cornerRadius = context.dp(28f); setColor(Color.WHITE) }
        setOnClickListener { onClick() }
    }
}
