package app.saathi.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.saathi.capture.Box
import app.saathi.design.Glyph
import app.saathi.design.Glyphs
import app.saathi.design.Metro
import app.saathi.design.SaathiType
import app.saathi.session.GuideState
import kotlin.math.hypot

internal fun Context.dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

/** The system's "Remove animations" setting: motion becomes instant cuts. */
private fun animationsOn() = ValueAnimator.areAnimatorsEnabled()

/**
 * The Help disc: a metro-blue station marker the senior taps to talk, and drags out of the way.
 * Its glyph changes shape with the state (guide mark, microphone, looking arc), not just colour.
 */
@SuppressLint("ViewConstructor")
internal class BubbleView(
    context: Context,
    private val onTap: () -> Unit,
    private val onDrag: (dx: Int, dy: Int) -> Unit,
    private val onDragEnd: () -> Unit,
) : View(context) {
    private var metro = Metro.of(context)
    // The disc carries a word under its glyph ("Help", "मदद") for seniors who don't know the mark yet.
    // It is sized once per language (80–124dp) to fit its longest state word, never below 14dp.
    private val minSize = context.dp(80f).toInt()
    private val maxSize = context.dp(124f).toInt()
    private var size = minSize
    private val pad = context.dp(16f).toInt()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    // The rim keeps the disc visible on blue, orange and red apps (Paytm, Swiggy, PhonePe).
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = context.dp(3f) }
    private val rings = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = context.dp(2.5f) }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(4f)
        strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var text = ""
    private var language = "hi-IN"
    private var state = GuideState.IDLE
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init {
        contentDescription = "Saathi"
    }

    /**
     * Sizes the disc once for a language, to fit the longest of its state words ("Help",
     * "Listening", "Looking"), so it never changes size, or moves, as the state changes.
     * Returns how much the diameter changed, so the caller can keep the disc's centre still.
     */
    fun sizeFor(labels: List<String>, language: String): Int {
        label.typeface = SaathiType.typeface(context, language, weight = 700, width = 88)
        label.textSize = context.dp(16f)
        val want = (minSize..maxSize step context.dp(2f).toInt())
            .firstOrNull { d -> labels.all { fits(it, d / 2f) } } ?: maxSize
        val delta = want - size
        if (delta != 0) {
            size = want
            requestLayout()
        }
        return delta
    }

    fun render(state: GuideState, label: String, language: String) {
        this.state = state
        this.text = label
        this.language = language
        metro = Metro.of(context)
        contentDescription = label
        val moving = state == GuideState.LISTENING || state == GuideState.THINKING
        if (moving && animationsOn()) animator.start() else animator.cancel()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(size + pad * 2, size + pad * 2)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = size / 2f
        fill.color = metro.accentFill
        rim.color = metro.ringEdge
        if (state == GuideState.LISTENING) {
            // Two rings travel outward from the disc while the mic is open (comp 2).
            rings.color = metro.accent
            for (k in 0..1) {
                val t = (phase + k * 0.5f) % 1f
                rings.alpha = ((1 - t) * 255).toInt()
                canvas.drawCircle(cx, cy, r + t * pad, rings)
            }
        }
        fill.setShadowLayer(context.dp(6f), 0f, context.dp(2f), 0x55000000)
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r - rim.strokeWidth / 2, rim)

        if (state == GuideState.THINKING) {
            arc.color = metro.onAccent
            val inset = context.dp(7f)
            canvas.drawArc(cx - r + inset, cy - r + inset, cx + r - inset, cy + r - inset, phase * 360f, 100f, false, arc)
        }
        val glyph = if (state == GuideState.LISTENING) Glyph.MIC else Glyph.GUIDE
        val g = context.dp(26f)
        Glyphs.draw(canvas, glyph, RectF(cx - g / 2, cy - r * 0.62f, cx + g / 2, cy - r * 0.62f + g), metro.onAccent)

        // The word sits under the glyph; past the disc's largest size it shrinks, but never below 14dp.
        label.color = metro.onAccent
        label.typeface = SaathiType.typeface(context, language, weight = 700, width = 88)
        label.textSize = context.dp(16f)
        while (!fits(text, r) && label.textSize > context.dp(14f)) label.textSize -= context.dp(0.5f)
        canvas.drawText(text, cx, cy + r * BASELINE, label)
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

    private val ink = android.graphics.Rect()
    /** Room inside the rim and the looking arc (its inset plus stroke). */
    private val inner = context.dp(12f)

    /**
     * True if [text], at the label paint's current size, fits inside a disc of radius [r]: its ink
     * (Devanagari's ू and ँ included) must clear the inner circle at the ink's farthest point from
     * the centre, where the circle is narrowest.
     */
    private fun fits(text: String, r: Float): Boolean {
        if (text.isEmpty()) return true
        label.getTextBounds(text, 0, text.length, ink)
        val ri = r - inner
        val baseline = r * BASELINE
        val far = maxOf(kotlin.math.abs(baseline + ink.top), kotlin.math.abs(baseline + ink.bottom))
        if (far >= ri) return false
        val half = kotlin.math.sqrt(ri * ri - far * far)
        return ink.width() / 2f <= half
    }

    private companion object {
        /** The word's baseline, below the disc's centre, as a fraction of the radius. */
        const val BASELINE = 0.42f
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isClickable = true
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
    }
}

/**
 * Full-screen, touch-through layer: a flat veil over everything except the target, which is
 * ringed in metro blue with a station dot on its leading end. The ring glides from its last stop
 * (or from the Help disc) so the eye can follow it; that glide is Saathi's one signature motion.
 */
internal class PointerView(context: Context) : View(context) {
    private var metro = Metro.of(context)
    private val veil = Paint()
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = context.dp(6f) }
    private val ringEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = context.dp(11f) }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotEdge = Paint(Paint.ANTI_ALIAS_FLAG)
    /** Small targets (a 24dp icon) get a ring at least this big, so they're easy to find. */
    private val minSide = context.dp(64f)
    private val pad = context.dp(8f)
    private val corner = context.dp(16f)
    private val dotR = context.dp(9f)
    private val path = Path().apply { fillType = Path.FillType.EVEN_ODD }

    private var from: RectF? = null
    private var to: RectF? = null
    private val current = RectF()
    private var travel = 1f
    private var beat = 0f
    private val loc = IntArray(2)

    // The station dot breathes slowly once the ring has arrived; nothing else moves.
    private val beatAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { beat = it.animatedValue as Float; invalidate() }
    }
    private var travelAnim: ValueAnimator? = null

    /** [target] is in screen pixels; [origin] is where the ring starts if nothing is showing. */
    fun pointAt(target: Box, origin: Box) {
        metro = Metro.of(context)
        getLocationOnScreen(loc)
        val next = RectF(
            target.left - loc[0] - pad, target.top - loc[1] - pad,
            target.right - loc[0] + pad, target.bottom - loc[1] + pad,
        )
        if (next.width() < minSide) next.inset(-(minSide - next.width()) / 2, 0f)
        if (next.height() < minSide) next.inset(0f, -(minSide - next.height()) / 2)
        // Keep the whole ring, its white edge and the station dot on screen for full-width rows.
        val edge = ringEdge.strokeWidth / 2
        next.left = next.left.coerceAtLeast(dotR + context.dp(4f))
        next.right = next.right.coerceAtMost(width - edge)
        next.top = next.top.coerceAtLeast(edge)
        next.bottom = next.bottom.coerceAtMost(height - edge)
        from = if (to != null) RectF(current) else RectF(
            origin.left - loc[0].toFloat(), origin.top - loc[1].toFloat(),
            origin.right - loc[0].toFloat(), origin.bottom - loc[1].toFloat(),
        )
        to = next
        travelAnim?.cancel()
        if (animationsOn()) {
            travelAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 520
                interpolator = DecelerateInterpolator(2f)
                addUpdateListener { travel = it.animatedValue as Float; invalidate() }
                start()
            }
            if (!beatAnim.isStarted) beatAnim.start()
        } else {
            travel = 1f
            invalidate()
        }
        visibility = VISIBLE
    }

    fun clear() {
        travelAnim?.cancel()
        beatAnim.cancel()
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
        veil.color = metro.veil
        ring.color = metro.accent
        ringEdge.color = metro.ringEdge
        dot.color = metro.accent
        dotEdge.color = metro.ringEdge

        path.reset()
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.addRoundRect(current, corner, corner, Path.Direction.CW)
        canvas.drawPath(path, veil)
        canvas.drawRoundRect(current, corner, corner, ringEdge)
        canvas.drawRoundRect(current, corner, corner, ring)

        // The station dot on the ring's leading end.
        val swell = if (travel >= 1f) 1f + 0.18f * (if (beat < 0.5f) beat * 2 else (1 - beat) * 2) else 1f
        canvas.drawCircle(current.left, current.centerY(), dotR * swell + context.dp(2.5f), dotEdge)
        canvas.drawCircle(current.left, current.centerY(), dotR * swell, dot)
    }
}

/** What a sign is saying; each kind has its own glyph shape and band colour. */
internal enum class SignKind { DOWN, UP, NOTE, DONE, YOURSELF, WARNING }

/**
 * The instruction sign: an enamel panel with a coloured band on its leading edge, the glyph (an
 * arrow aimed at the target, or the state's mark), the first sentence large and the rest smaller.
 * At the top of the screen it is an inset card with Repeat and Close on the veil beneath it (comp 1);
 * at the bottom it docks full-width like a platform board, with the buttons inside it (comp 2).
 */
@SuppressLint("ViewConstructor")
internal class CaptionView(
    context: Context,
    onRepeat: () -> Unit,
    onClose: () -> Unit,
) : LinearLayout(context) {
    private var metro = Metro.of(context)
    // Large system font sizes are common among seniors; past 1.3× the sign would cover the screen.
    // Read on every render so a change in system settings applies to the next sign.
    private val fontScale get() = context.resources.configuration.fontScale.coerceIn(1f, 1.3f)

    private val band = View(context)
    private val glyph = GlyphView(context)
    private val head = TextView(context).apply {
        maxLines = 4
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }
    private val sub = TextView(context).apply {
        maxLines = 4
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }
    private val panelBg = GradientDrawable()
    private val content = LinearLayout(context).apply { orientation = VERTICAL }
    private val panel = LinearLayout(context).apply {
        orientation = HORIZONTAL
        background = panelBg
        clipToOutline = true
        elevation = context.dp(4f)
    }
    private val repeat = SignButton(context, Glyph.REPEAT, filled = true, onRepeat)
    private val close = SignButton(context, Glyph.CLOSE, filled = false, onClose)
    // Repeat and Close, kept apart so Close is never hit by accident.
    private val buttons = LinearLayout(context).apply {
        orientation = HORIZONTAL
        addView(repeat, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(View(context), LayoutParams(context.dp(28f).toInt(), 1))
        addView(close, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    init {
        orientation = VERTICAL
        val p = context.dp(18f).toInt()
        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, p, p, p)
            addView(head)
            addView(sub, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(6f).toInt() })
        }
        val message = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(glyph, LayoutParams(context.dp(64f).toInt(), context.dp(64f).toInt()).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = context.dp(8f).toInt()
                marginEnd = context.dp(6f).toInt()
            })
            addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(message)
        panel.addView(band, LayoutParams(context.dp(12f).toInt(), LayoutParams.MATCH_PARENT))
        panel.addView(content, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /**
     * [docked]: at the bottom edge, full-width and flat, buttons inside, padded clear of the
     * navigation bar by [bottomInset]. [veiled]: the screen behind is dimmed, so Close can let it through.
     */
    fun render(
        caption: String,
        kind: SignKind,
        language: String,
        repeatLabel: String,
        closeLabel: String,
        docked: Boolean,
        veiled: Boolean,
        bottomInset: Int,
    ) {
        metro = Metro.of(context)
        val bandColor = when (kind) {
            SignKind.DONE -> metro.done
            SignKind.YOURSELF -> metro.caution
            SignKind.WARNING -> metro.danger
            else -> metro.accent
        }
        val (first, rest) = split(caption)
        head.text = first
        sub.text = rest
        sub.visibility = if (rest.isEmpty()) GONE else VISIBLE
        head.setTextColor(metro.ink)
        sub.setTextColor(metro.inkSoft)
        // Typeface first: setLineHeight is computed from the current font's metrics, so setting it
        // under the system font and then switching to Anek left the first sign of a session loose.
        head.typeface = SaathiType.typeface(context, language, weight = 700)
        sub.typeface = SaathiType.typeface(context, language, weight = 500)
        val headSize = context.dp(27f * fontScale)
        val subSize = context.dp(20f * fontScale)
        head.setTextSize(TypedValue.COMPLEX_UNIT_PX, headSize)
        sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, subSize)
        // Explicit line heights: Devanagari and the Dravidian scripts have tall font metrics that
        // otherwise push a two-line instruction apart into what reads as two separate lines.
        // Fallback line spacing would re-expand lines to the font's tallest extents and undo this.
        head.isFallbackLineSpacing = false
        sub.isFallbackLineSpacing = false
        head.lineHeight = (headSize * 1.3f).toInt()
        sub.lineHeight = (subSize * 1.35f).toInt()
        panelBg.setColor(metro.panel)
        panelBg.cornerRadius = if (docked) 0f else context.dp(8f)
        band.setBackgroundColor(bandColor)
        glyph.show(
            when (kind) {
                SignKind.DOWN -> Glyph.ARROW_DOWN
                SignKind.UP -> Glyph.ARROW_UP
                SignKind.NOTE -> Glyph.STOP
                SignKind.DONE -> Glyph.CHECK
                SignKind.YOURSELF -> Glyph.LOCK
                SignKind.WARNING -> Glyph.WARNING
            },
            if (kind == SignKind.DOWN || kind == SignKind.UP) metro.ink else bandColor,
        )
        repeat.render(repeatLabel, metro, language, fontScale, clearFill = false)
        // On the veil (comp 1) Close is an outline the veil shows through; on the panel it sits on white.
        close.render(closeLabel, metro, language, fontScale, clearFill = veiled && !docked)

        (buttons.parent as? android.view.ViewGroup)?.removeView(buttons)
        val p = context.dp(18f).toInt()
        if (docked) {
            buttons.setPadding(context.dp(8f).toInt(), 0, p, p + bottomInset)
            content.addView(buttons, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            panel.elevation = context.dp(8f)
        } else {
            buttons.setPadding(0, context.dp(12f).toInt(), 0, 0)
            addView(buttons, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            panel.elevation = context.dp(4f)
        }
        // TalkBack reads the whole sign as one announcement.
        panel.contentDescription = caption
        panel.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    internal companion object {
        /** Splits off the first sentence for the large line; the rest goes small beneath it. */
        fun split(caption: String): Pair<String, String> {
            val text = caption.trim()
            val end = Regex("[.!?।](\\s+)").find(text) ?: return text to ""
            val cut = end.range.first + 1
            return text.substring(0, cut).trim() to text.substring(cut).trim()
        }
    }
}

/** A glyph in a fixed square, used by the sign. */
internal class GlyphView(context: Context) : View(context) {
    private var glyph = Glyph.STOP
    private var color = 0
    private val box = RectF()

    fun show(glyph: Glyph, color: Int) {
        this.glyph = glyph
        this.color = color
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val inset = width * 0.08f
        box.set(inset, inset, width - inset, height - inset)
        Glyphs.draw(canvas, glyph, box, color)
    }
}

/**
 * A sign button: a rounded rectangle like the enamel panels, filled metro blue for Repeat and an
 * outline for Close. The drawn glyph stays beside the word for seniors who recognise ↻ and ✕ faster
 * than they read the label; glyph and word are centred together and the word shrinks to fit.
 */
@SuppressLint("ViewConstructor")
internal class SignButton(
    context: Context,
    private val glyph: Glyph,
    private val filled: Boolean,
    onClick: () -> Unit,
) : FrameLayout(context) {
    private val bg = GradientDrawable().apply { cornerRadius = context.dp(10f) }
    private val icon = GlyphView(context)
    private val text = TextView(context).apply { maxLines = 1; includeFontPadding = false }
    private val iconSize = context.dp(24f).toInt()
    private val gap = context.dp(10f).toInt()
    private val pad = context.dp(14f).toInt()
    private var targetPx = context.dp(21f)

    init {
        background = bg
        minimumHeight = context.dp(60f).toInt()
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(icon, LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = gap })
            addView(text)
        }
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    fun render(label: String, metro: Metro, language: String, fontScale: Float, clearFill: Boolean) {
        val fg = if (filled) metro.onAccent else metro.accent
        if (filled) {
            bg.setColor(metro.accentFill)
            bg.setStroke(0, 0)
        } else {
            // On the veil Close is a flat neutral (comp 1's light grey): secondary, and opaque so the
            // app underneath never shows through its label.
            bg.setColor(if (clearFill) (if (metro === Metro.DARK) 0xFF2A2D33.toInt() else 0xFFE9EBEE.toInt()) else metro.panel)
            bg.setStroke(context.dp(2.5f).toInt(), metro.accent)
        }
        text.text = label
        text.setTextColor(fg)
        text.typeface = SaathiType.typeface(context, language, weight = 700)
        targetPx = context.dp(21f * fontScale)
        icon.show(glyph, fg)
        // Screen readers should say "Repeat", not describe the icon.
        contentDescription = label
        fit(width)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) post { fit(w) }
    }

    /** Largest size up to the target at which the word fits beside its glyph ("மீண்டும் கேள்"). */
    private fun fit(width: Int) {
        if (width <= 0) {
            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, targetPx)
            return
        }
        val room = width - pad * 2 - iconSize - gap
        val paint = android.graphics.Paint(text.paint)
        var size = targetPx
        paint.textSize = size
        while (paint.measureText(text.text.toString()) > room && size > context.dp(13f)) {
            size -= context.dp(0.5f)
            paint.textSize = size
        }
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        text.post { alignIcon() }
    }

    /**
     * Centres the glyph on the word's ink rather than its line box. Devanagari's headline and
     * the Dravidian scripts' tall vowels put the visual centre well above the box centre.
     */
    private fun alignIcon() {
        val label = text.text.toString()
        if (label.isEmpty() || text.height == 0) return
        val ink = android.graphics.Rect()
        text.paint.getTextBounds(label, 0, label.length, ink)
        val inkCenter = text.baseline + (ink.top + ink.bottom) / 2f
        icon.translationY = inkCenter - text.height / 2f
    }

    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name
}
