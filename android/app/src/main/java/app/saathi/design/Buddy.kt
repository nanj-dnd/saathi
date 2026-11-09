package app.saathi.design

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.TypedValue

/**
 * Bob's visual system: station signboards. The step is set on signboard yellow in black, actions
 * are black boards with yellow lettering, and the hand that points is black with a yellow rim so it
 * reads on any app. There is one theme: the overlay and setup look the same whatever the phone's
 * dark-mode setting. State is carried by a badge's shape (tick, lock, warning), never colour alone.
 */
class Buddy private constructor() {
    /** The light dim over everything except the target. */
    val veil = 0x40000000
    /** The caption board: yellow, black keyline and lettering. No grey anywhere in its type. */
    val card = YELLOW
    val cardEdge = INK
    val cardInk = INK
    /** Repeat: a black board with yellow lettering. */
    val pill = INK
    val pillInk = YELLOW
    /** Close: a white board with a black keyline and black lettering. */
    val pillQuiet = WHITE
    val pillQuietInk = INK
    /** What can be pressed (Help, primary boards) is black; its lettering and the hand's rim are yellow. */
    val accent = INK
    val onAccent = YELLOW
    /** The spotlight's halo and the tap ripple: yellow, with a black hairline for white apps. */
    val accentLine = YELLOW
    val edge = YELLOW
    /** Badge fills and the glyph colour on each. "Yours alone" is black with a yellow lock, so it holds on the yellow board. */
    val done = 0xFF1E8A4C.toInt()
    val onDone = WHITE
    val caution = INK
    val onCaution = YELLOW
    val danger = 0xFFD7262E.toInt()
    val onDanger = WHITE

    companion object {
        const val YELLOW = 0xFFFFD200.toInt()
        const val INK = 0xFF121212.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()

        val LIGHT = Buddy()

        /** One theme for every phone; [context] is kept so callers need not change. */
        @Suppress("UNUSED_PARAMETER")
        fun of(context: Context): Buddy = LIGHT
    }
}

/**
 * A signboard: a flat rectangle with slightly rounded corners, a keyline inset from its edge and a
 * small rivet in each corner inside the keyline. Used for the caption, its buttons and the Help board.
 */
class BoardDrawable(
    context: Context,
    private var fill: Int,
    private var line: Int,
    private val rivets: Boolean = true,
    private val outer: Boolean = false,
) : Drawable() {
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, metrics)
    private val metrics = context.resources.displayMetrics
    private val radius = dp(8f)
    private val inset = dp(5f)
    private val stroke = dp(2f)
    private val rivetR = dp(2.4f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    fun set(fill: Int, line: Int) {
        this.fill = fill
        this.line = line
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        box.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawRoundRect(box, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = line
        if (outer) {
            // A white board keeps its keyline on the edge, so it still reads as a board on yellow.
            box.inset(stroke / 2, stroke / 2)
            canvas.drawRoundRect(box, radius - stroke / 2, radius - stroke / 2, paint)
        } else {
            box.inset(inset, inset)
            canvas.drawRoundRect(box, radius - inset / 2, radius - inset / 2, paint)
        }
        if (rivets) {
            paint.style = Paint.Style.FILL
            val d = if (outer) inset + rivetR * 1.6f else inset + stroke + rivetR * 2f
            val r = RectF(b.left + d, b.top + d, b.right - d, b.bottom - d)
            for (x in floatArrayOf(r.left, r.right)) for (y in floatArrayOf(r.top, r.bottom)) canvas.drawCircle(x, y, rivetR, paint)
        }
    }

    /** Lets a View's elevation cast the board's own shadow. */
    override fun getOutline(outline: Outline) { outline.setRoundRect(bounds, radius) }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
