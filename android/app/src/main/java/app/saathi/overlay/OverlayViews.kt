package app.saathi.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.saathi.capture.Box
import app.saathi.R
import app.saathi.design.BoardDrawable
import app.saathi.design.Buddy
import app.saathi.design.Glyph
import app.saathi.design.Glyphs
import app.saathi.design.SaathiType
import app.saathi.session.GuideState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

internal fun Context.dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

/** The system's "Remove animations" setting: motion becomes instant cuts. */
private fun animationsOn() = ValueAnimator.areAnimatorsEnabled()

/** Material's emphasized curve: a quick start that settles gently. */
private val SETTLE = PathInterpolator(0.3f, 0f, 0.1f, 1f)

/**
 * The Help button: Bob himself. A small square yellow signboard (black keyline set in from the edge,
 * a rivet in each corner) with the clay face filling it, which the senior taps to talk and drags out
 * of the way. Listening swaps the face for black voice bars, Looking for a black spinning arc, so the
 * state reads by shape, not colour alone; TalkBack hears the word ("Help", "Listening", "Looking").
 */
@SuppressLint("ViewConstructor")
internal class BubbleView(
    context: Context,
    private val onTap: () -> Unit,
    private val onDrag: (dx: Int, dy: Int) -> Unit,
    private val onDragEnd: () -> Unit,
) : View(context) {
    private var buddy = Buddy.of(context)
    /** Room around the board for its shadow and the listening pulse. */
    private val pad = context.dp(12f).toInt()
    private val side = context.dp(68f)
    private val corner = context.dp(8f)
    /** The face sits inside the keyline with a little yellow around it. */
    private val faceInset = context.dp(12f)
    /** The eyes sit high in the face; nudged down, they clear the top rivets. */
    private val faceDrop = context.dp(1.5f)
    private val icon = context.dp(30f)

    private val board = BoardDrawable(context, Buddy.YELLOW, Buddy.INK)
    /** Keyline and rivets again, over the face, so the image never hides them. */
    private val trim = BoardDrawable(context, 0x00000000, Buddy.INK)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Buddy.YELLOW
        setShadowLayer(context.dp(8f), 0f, context.dp(3f), 0x4D000000)
    }
    private val face = BitmapFactory.decodeResource(context.resources, R.drawable.bob_mark)
    private val faceBox = RectF()
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // A soft halo, not a hard ring, breathes out from the board while the mic is open.
    // Its reach (growth, half the stroke, the blur) stays inside the padding, or the window edge clips it square.
    private val pulse = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(3f)
        maskFilter = BlurMaskFilter(context.dp(3f), BlurMaskFilter.Blur.NORMAL)
    }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeWidth = context.dp(4f) }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = context.dp(4f) }
    private val arcBox = RectF()
    private val box = RectF()
    private var state = GuideState.IDLE
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init {
        contentDescription = "Bob"
    }

    /** The view's width; the caller anchors the board against its screen edge by it. */
    val wantedWidth get() = (side + pad * 2).toInt()

    /** Shows [state]; [label] is what TalkBack says. */
    fun render(state: GuideState, label: String) {
        this.state = state
        buddy = Buddy.of(context)
        contentDescription = label
        val moving = state == GuideState.LISTENING || state == GuideState.THINKING
        if (moving && animationsOn()) animator.start() else animator.cancel()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(wantedWidth, (side + pad * 2).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        box.set(pad.toFloat(), pad.toFloat(), pad + side, pad + side)
        val r = corner
        if (state == GuideState.LISTENING) {
            // Two soft rings travel outward while the mic is open.
            pulse.color = buddy.cardInk
            for (k in 0..1) {
                val t = (phase + k * 0.5f) % 1f
                pulse.alpha = ((1 - t) * 150).toInt()
                val grow = t * (pad - context.dp(7f))
                canvas.drawRoundRect(box.left - grow, box.top - grow, box.right + grow, box.bottom + grow, r + grow, r + grow, pulse)
            }
        }
        canvas.drawRoundRect(box, r, r, shadow)
        board.setBounds(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt())
        board.draw(canvas)

        val cx = box.centerX()
        val cy = box.centerY()
        when (state) {
            GuideState.LISTENING -> {
                // Four voice bars, each rising and falling a little out of step.
                bar.color = buddy.cardInk
                val step = icon / 4f
                for (k in 0..3) {
                    val wave = 0.35f + 0.65f * abs(sin((phase * 2f + k * 0.27f) * PI)).toFloat()
                    val h = icon * (if (animationsOn()) wave else 0.6f) / 2
                    val x = cx - icon / 2 + step * (k + 0.5f)
                    canvas.drawLine(x, cy - h, x, cy + h, bar)
                }
            }
            GuideState.THINKING -> {
                arcBox.set(cx - icon / 2, cy - icon / 2, cx + icon / 2, cy + icon / 2)
                arc.color = 0x33121212
                canvas.drawOval(arcBox, arc)
                arc.color = buddy.cardInk
                canvas.drawArc(arcBox, phase * 360f - 90f, 110f, false, arc)
            }
            else -> {
                // The mark's own yellow matches the board, so the face simply sits inside the keyline.
                faceBox.set(box.left + faceInset, box.top + faceInset + faceDrop, box.right - faceInset, box.bottom - faceInset + faceDrop)
                canvas.drawBitmap(face, null, faceBox, facePaint)
            }
        }
        trim.setBounds(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt())
        trim.draw(canvas)
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
 * Full-screen, touch-through layer. A light dim over everything except the target, which sits in
 * a soft blue-edged spotlight, and the pointing hand: it flies on an arc from the Help button (or from its
 * last stop) and lands with its tip on the target. That flight is Saathi's one signature motion.
 */
internal class PointerView(context: Context) : View(context) {
    private var buddy = Buddy.of(context)
    private val veil = Paint()
    // The halo carries the target; the hairline only gives the spotlight a crisp edge.
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(context.dp(18f), BlurMaskFilter.Blur.OUTER) }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = context.dp(2f) }
    /** A full-width row's spotlight stays this far in from the screen edges. */
    private val inset = context.dp(12f)
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG)
    /** Small targets (a 24dp icon) get a spotlight at least this big, so they're easy to find. */
    private val minSide = context.dp(56f)
    private val pad = context.dp(6f)
    private val corner = context.dp(18f)
    private val handSize = context.dp(48f)
    private val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val hand = Glyphs.handPath()
    private val handEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val handFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripple = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = context.dp(2.5f) }

    private var fromSpot: RectF? = null
    private var spot: RectF? = null
    private val currentSpot = RectF()
    private val start = PointF()
    private val control = PointF()
    private val end = PointF()
    /** Which way the hand extends from its fingertip (mirrored near the right or bottom edge). */
    private var flipX = 1f
    private var flipY = 1f
    private var travel = 1f
    private var trailAlpha = 0f
    private var beat = 0f
    private val loc = IntArray(2)

    // Once landed, the finger taps the target over and over, with a ripple where it touches.
    private val beatAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { beat = it.animatedValue as Float; invalidate() }
    }
    private var travelAnim: ValueAnimator? = null

    /** [target] is in screen pixels; [origin] is where the hand starts if nothing is showing. */
    fun pointAt(target: Box, origin: Box) {
        buddy = Buddy.of(context)
        getLocationOnScreen(loc)
        val next = RectF(
            target.left - loc[0] - pad, target.top - loc[1] - pad,
            target.right - loc[0] + pad, target.bottom - loc[1] + pad,
        )
        if (next.width() < minSide) next.inset(-(minSide - next.width()) / 2, 0f)
        if (next.height() < minSide) next.inset(0f, -(minSide - next.height()) / 2)
        next.left = next.left.coerceAtLeast(inset)
        next.right = next.right.coerceAtMost(width - inset)
        next.top = next.top.coerceAtLeast(inset)
        next.bottom = next.bottom.coerceAtMost(height - inset)

        // The tip lands inside the target, so the hand rests on it, fingertip on the button; the
        // overlay passes touches through, and a body hanging over the next row would invite a tap
        // there. Near the bottom or right of the screen it mirrors so it never leaves the screen.
        val room = handSize * 0.9f
        // On a wide row the label sits at its start, so the hand lands well past it (even at large
        // font sizes), short of the switch or chevron at the row's end.
        val tipX = if (next.width() > width * 0.6f) next.left + next.width() * 0.78f else next.centerX()
        flipX = if (tipX + room > width) -1f else 1f
        val down = next.top + next.height() * 0.42f
        flipY = if (down + room > height) -1f else 1f
        val tipY = if (flipY > 0) down else next.bottom - next.height() * 0.42f

        val hadSpot = spot != null
        start.set(
            if (hadSpot) end.x else origin.centerX - loc[0].toFloat(),
            if (hadSpot) end.y else origin.centerY - loc[1].toFloat(),
        )
        fromSpot = if (hadSpot) RectF(currentSpot) else null
        spot = next
        end.set(tipX, tipY)
        // The arc bows upward, like a hand lifting to point.
        val len = hypot(end.x - start.x, end.y - start.y)
        control.set((start.x + end.x) / 2, minOf(start.y, end.y) - len * 0.22f)

        travelAnim?.cancel()
        if (animationsOn() && len > 1f) {
            travelAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 720
                interpolator = SETTLE
                addUpdateListener {
                    travel = it.animatedValue as Float
                    // The trail shows during the flight and fades out once the hand has landed.
                    trailAlpha = if (travel < 0.85f) 1f else (1f - travel) / 0.15f
                    invalidate()
                }
                start()
            }
            if (!beatAnim.isStarted) beatAnim.start()
        } else {
            travel = 1f
            trailAlpha = 0f
            invalidate()
        }
        visibility = VISIBLE
    }

    fun clear() {
        travelAnim?.cancel()
        beatAnim.cancel()
        fromSpot = null
        spot = null
        visibility = GONE
    }

    private fun along(t: Float, out: PointF) {
        val u = 1 - t
        out.set(
            u * u * start.x + 2 * u * t * control.x + t * t * end.x,
            u * u * start.y + 2 * u * t * control.y + t * t * end.y,
        )
    }

    private val at = PointF()

    override fun onDraw(canvas: Canvas) {
        val b = spot ?: return
        val a = fromSpot
        if (a != null) {
            currentSpot.set(
                a.left + (b.left - a.left) * travel, a.top + (b.top - a.top) * travel,
                a.right + (b.right - a.right) * travel, a.bottom + (b.bottom - a.bottom) * travel,
            )
        } else {
            currentSpot.set(b)
        }
        // A first step fades the dim and the spotlight in while the hand is on its way.
        val fade = if (a == null) (travel / 0.5f).coerceAtMost(1f) else 1f

        veil.color = buddy.veil
        veil.alpha = (android.graphics.Color.alpha(buddy.veil) * fade).toInt()
        path.reset()
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.addRoundRect(currentSpot, corner, corner, Path.Direction.CW)
        canvas.drawPath(path, veil)
        glow.color = buddy.accentLine
        glow.alpha = (200 * fade).toInt()
        canvas.drawRoundRect(currentSpot, corner, corner, glow)
        // Black keyline: the yellow halo alone vanishes on a white app.
        outline.color = buddy.cardInk
        outline.alpha = (255 * fade).toInt()
        canvas.drawRoundRect(currentSpot, corner, corner, outline)

        if (trailAlpha > 0f) {
            // Dots along the path already flown, brighter nearer the hand.
            trail.color = buddy.cardInk
            val n = 14
            for (k in 1 until n) {
                val t = travel * k / n
                along(t, at)
                trail.alpha = (190 * trailAlpha * (k.toFloat() / n)).toInt()
                canvas.drawCircle(at.x, at.y, context.dp(3f), trail)
            }
        }

        along(travel, at)
        val landed = travel >= 1f && animationsOn()
        // The tap: the finger dips toward the target early in each beat, and a ring spreads from it.
        val press = if (landed && beat < 0.3f) sin(beat / 0.3f * PI).toFloat() else 0f
        if (landed) {
            val t = beat
            ripple.color = buddy.cardInk
            ripple.alpha = ((1 - t) * 200).toInt()
            canvas.drawCircle(at.x, at.y, context.dp(5f) + t * context.dp(24f), ripple)
        }
        drawHand(canvas, at.x, at.y, press)
    }

    /** The hand with its fingertip at (x, y): a yellow rim and soft shadow, then the black hand. */
    private fun drawHand(canvas: Canvas, x: Float, y: Float, press: Float) {
        val s = handSize / 24f * (1 - 0.06f * press)
        canvas.save()
        canvas.translate(x, y + flipY * context.dp(3f) * press)
        canvas.scale(flipX * s, flipY * s)
        canvas.translate(-Glyphs.TIP_X, -Glyphs.TIP_Y)
        handEdge.color = buddy.edge
        handEdge.strokeWidth = 2 * context.dp(3f) / s
        handEdge.setShadowLayer(context.dp(6f) / s, 0f, context.dp(2f) / s, 0x59000000)
        canvas.drawPath(hand, handEdge)
        handFill.color = buddy.accent
        canvas.drawPath(hand, handFill)
        canvas.restore()
    }
}

/** What a caption is saying; the three that aren't a plain step carry a badge with its own shape. */
internal enum class SignKind { STEP, NOTE, DONE, YOURSELF, WARNING }

/**
 * The caption: a yellow signboard floating over the app, the first sentence large and black, the
 * rest smaller beneath, Repeat and Close as boards inside it. A badge (tick, lock, warning) leads
 * the text when the caption is more than a step.
 */
@SuppressLint("ViewConstructor")
internal class CaptionView(
    context: Context,
    onRepeat: () -> Unit,
    onClose: () -> Unit,
) : FrameLayout(context) {
    private var buddy = Buddy.of(context)
    // Large system font sizes are common among seniors; past 1.3× the card would cover the screen.
    // Read on every render so a change in system settings applies to the next caption.
    private val fontScale get() = context.resources.configuration.fontScale.coerceIn(1f, 1.3f)

    private val badge = BadgeView(context)
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
    private val cardBg = BoardDrawable(context, Buddy.YELLOW, Buddy.INK)
    private val repeat = PillButton(context, Glyph.REPEAT, primary = true, onRepeat)
    private val close = PillButton(context, Glyph.CLOSE, primary = false, onClose)
    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = cardBg
        elevation = context.dp(10f)
    }

    init {
        // Room around the card for its shadow; the window spans the screen width.
        val margin = context.dp(14f).toInt()
        setPadding(margin, margin, margin, margin)
        clipToPadding = false
        val p = context.dp(22f).toInt()
        card.setPadding(p, p, p, context.dp(20f).toInt())
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(head)
            addView(sub, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(6f).toInt() })
        }
        val message = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(badge, LinearLayout.LayoutParams(context.dp(36f).toInt(), context.dp(36f).toInt()).apply {
                marginEnd = context.dp(14f).toInt()
            })
            addView(texts, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(repeat, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            // Kept apart so Close is not hit by accident.
            addView(View(context), LinearLayout.LayoutParams(context.dp(14f).toInt(), 1))
            addView(close, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        card.addView(message)
        card.addView(buttons, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(18f).toInt()
        })
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun render(caption: String, kind: SignKind, repeatLabel: String, closeLabel: String, rtl: Boolean = false) {
        buddy = Buddy.of(context)
        layoutDirection = if (rtl) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR
        val (first, rest) = split(caption)
        head.text = first
        sub.text = rest
        sub.visibility = if (rest.isEmpty()) GONE else VISIBLE
        head.setTextColor(buddy.cardInk)
        // Both lines in full black: rank comes from size and weight, never a faded grey.
        sub.setTextColor(buddy.cardInk)
        // Typeface first: setLineHeight is computed from the current font's metrics.
        head.typeface = SaathiType.typeface(700)
        sub.typeface = SaathiType.typeface(500)
        val headSize = context.dp(24f * fontScale)
        val subSize = context.dp(18f * fontScale)
        head.setTextSize(TypedValue.COMPLEX_UNIT_PX, headSize)
        sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, subSize)
        // Explicit line heights: Devanagari and the Dravidian scripts have tall font metrics that
        // otherwise push a two-line instruction apart into what reads as two separate lines.
        head.isFallbackLineSpacing = false
        sub.isFallbackLineSpacing = false
        head.lineHeight = (headSize * 1.3f).toInt()
        sub.lineHeight = (subSize * 1.38f).toInt()
        cardBg.set(buddy.card, buddy.cardEdge)

        when (kind) {
            SignKind.DONE -> badge.show(Glyph.CHECK, buddy.done, buddy.onDone)
            SignKind.YOURSELF -> badge.show(Glyph.LOCK, buddy.caution, buddy.onCaution)
            SignKind.WARNING -> badge.show(Glyph.WARNING, buddy.danger, buddy.onDanger)
            else -> badge.hide()
        }
        // The badge sits level with the first line, whatever the script's line height.
        (badge.layoutParams as LinearLayout.LayoutParams).topMargin = ((head.lineHeight - context.dp(36f)) / 2).toInt().coerceAtLeast(0)

        repeat.render(repeatLabel, buddy, fontScale)
        close.render(closeLabel, buddy, fontScale)
        // TalkBack reads the whole caption as one announcement.
        card.contentDescription = caption
        card.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
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

/** A round badge with a glyph: green tick, amber lock or red warning. */
internal class BadgeView(context: Context) : View(context) {
    private var glyph = Glyph.CHECK
    private var fillColor = 0
    private var ink = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun show(glyph: Glyph, fill: Int, ink: Int) {
        this.glyph = glyph
        this.fillColor = fill
        this.ink = ink
        visibility = VISIBLE
        invalidate()
    }

    fun hide() {
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        val r = width / 2f
        paint.color = fillColor
        canvas.drawCircle(r, height / 2f, r, paint)
        val g = width * 0.6f
        box.set(r - g / 2, height / 2f - g / 2, r + g / 2, height / 2f + g / 2)
        Glyphs.draw(canvas, glyph, box, ink)
    }
}

/** A glyph in a fixed square, used beside a pill's word. */
internal class GlyphView(context: Context) : View(context) {
    private var glyph = Glyph.CHECK
    private var color = 0
    private val box = RectF()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun show(glyph: Glyph, color: Int) {
        this.glyph = glyph
        this.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        Glyphs.draw(canvas, glyph, box, color)
    }
}

/**
 * A caption button: a black board with yellow lettering for Repeat, a white keylined board for
 * Close. The drawn glyph stays beside the word for seniors who recognise ↻ and ✕ faster than they
 * read; the word shrinks to fit.
 */
@SuppressLint("ViewConstructor")
internal class PillButton(
    context: Context,
    private val glyph: Glyph,
    private val primary: Boolean,
    onClick: () -> Unit,
) : FrameLayout(context) {
    private val bg = BoardDrawable(context, Buddy.INK, Buddy.INK, rivets = false, outer = !primary)
    private val icon = GlyphView(context)
    private val text = TextView(context).apply { maxLines = 1; includeFontPadding = false }
    private val iconSize = context.dp(22f).toInt()
    private val gap = context.dp(8f).toInt()
    private val pad = context.dp(16f).toInt()
    private var targetPx = context.dp(19f)

    init {
        background = bg
        minimumHeight = context.dp(56f).toInt()
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

    fun render(label: String, buddy: Buddy, fontScale: Float) {
        val fg = if (primary) buddy.pillInk else buddy.pillQuietInk
        bg.set(if (primary) buddy.pill else buddy.pillQuiet, buddy.cardInk)
        text.text = label
        text.setTextColor(fg)
        text.typeface = SaathiType.signFor(context, label)
        targetPx = context.dp(19f * fontScale)
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
        val paint = Paint(text.paint)
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
