package app.saathi.design

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

/**
 * Saathi's drawn icon set on a 24-unit grid with one rounded stroke, used by the overlay and setup
 * alike. The pointing hand is the system's mark: the pointer, the Help button and the launcher icon.
 */
enum class Glyph { HAND, CHECK, LOCK, WARNING, MIC, REPEAT, CLOSE, SPEAKER, BACK }

object Glyphs {
    private const val STROKE = 2.4f

    /** The fingertip, in grid units: where the hand touches the thing it points at. */
    const val TIP_X = 9f
    const val TIP_Y = 2f

    /**
     * A hand pointing up with its index finger, as if about to tap: finger, three folded fingers,
     * palm and thumb, each a rounded shape. Filled together they read as one silhouette.
     */
    fun handPath() = Path().apply {
        addRoundRect(RectF(7.2f, 2f, 10.8f, 14.5f), 1.8f, 1.8f, Path.Direction.CW)
        addRoundRect(RectF(10.3f, 8.4f, 13.7f, 15f), 1.7f, 1.7f, Path.Direction.CW)
        addRoundRect(RectF(13.3f, 9.4f, 16.7f, 15.5f), 1.7f, 1.7f, Path.Direction.CW)
        addRoundRect(RectF(16.3f, 10.6f, 19.5f, 16f), 1.6f, 1.6f, Path.Direction.CW)
        addRoundRect(RectF(7.2f, 12f, 19.5f, 22f), 4f, 4f, Path.Direction.CW)
        // The thumb reaches out and up from beside the index finger.
        addPath(Path().apply {
            addRoundRect(RectF(3f, 12.4f, 9.4f, 15.8f), 1.7f, 1.7f, Path.Direction.CW)
            transform(Matrix().apply { setRotate(38f, 8.8f, 14.6f) })
        })
    }

    /** Draws [glyph] centred in [box] in [color]. */
    fun draw(canvas: Canvas, glyph: Glyph, box: RectF, color: Int) {
        val s = minOf(box.width(), box.height()) / 24f
        canvas.save()
        canvas.translate(box.centerX() - 12f * s, box.centerY() - 12f * s)
        canvas.scale(s, s)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
        when (glyph) {
            Glyph.HAND -> canvas.drawPath(handPath(), fill)
            Glyph.CHECK -> canvas.drawPath(Path().apply { moveTo(5f, 12.5f); lineTo(9.8f, 17.2f); lineTo(19f, 7f) }, stroke.apply { strokeWidth = 3f })
            Glyph.LOCK -> {
                canvas.drawRoundRect(RectF(5.5f, 10.5f, 18.5f, 20.5f), 3f, 3f, fill)
                canvas.drawPath(Path().apply { moveTo(8.5f, 10.5f); lineTo(8.5f, 7.8f); cubicTo(8.5f, 3.2f, 15.5f, 3.2f, 15.5f, 7.8f); lineTo(15.5f, 10.5f) }, stroke)
            }
            Glyph.WARNING -> {
                // The mark is knocked out of the triangle so it reads on any badge colour.
                val knock = Paint(fill).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
                canvas.saveLayer(0f, 0f, 24f, 24f, null)
                val tri = Path().apply { moveTo(12f, 3.5f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close() }
                canvas.drawPath(tri, Paint(stroke).apply { style = Paint.Style.FILL_AND_STROKE; strokeWidth = 2f })
                canvas.drawRoundRect(RectF(11f, 9f, 13f, 14.5f), 1f, 1f, knock)
                canvas.drawCircle(12f, 17.2f, 1.2f, knock)
                canvas.restore()
            }
            Glyph.MIC -> {
                canvas.drawRoundRect(RectF(9f, 3f, 15f, 14f), 3f, 3f, fill)
                canvas.drawPath(Path().apply { moveTo(5.5f, 11f); cubicTo(5.5f, 19f, 18.5f, 19f, 18.5f, 11f) }, stroke)
                canvas.drawLine(12f, 17.2f, 12f, 21f, stroke)
            }
            Glyph.REPEAT -> {
                // A clockwise circular arrow, open at the top right, its solid head at the top pointing on round.
                canvas.drawArc(RectF(5f, 5f, 19f, 19f), 0f, 270f, false, stroke)
                canvas.drawPath(Path().apply { moveTo(16.8f, 5f); lineTo(11f, 0.8f); lineTo(11f, 9.2f); close() }, fill)
            }
            Glyph.CLOSE -> {
                canvas.drawLine(6.5f, 6.5f, 17.5f, 17.5f, stroke)
                canvas.drawLine(17.5f, 6.5f, 6.5f, 17.5f, stroke)
            }
            Glyph.SPEAKER -> {
                canvas.drawPath(Path().apply { moveTo(3.5f, 9.5f); lineTo(7f, 9.5f); lineTo(11.5f, 5.5f); lineTo(11.5f, 18.5f); lineTo(7f, 14.5f); lineTo(3.5f, 14.5f); close() }, Paint(stroke).apply { style = Paint.Style.FILL_AND_STROKE; strokeWidth = 1.5f })
                canvas.drawArc(RectF(10f, 8f, 16f, 16f), -55f, 110f, false, stroke)
                canvas.drawArc(RectF(10f, 4.5f, 20.5f, 19.5f), -55f, 110f, false, stroke)
            }
            Glyph.BACK -> canvas.drawPath(Path().apply { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }, stroke.apply { strokeWidth = 2.6f })
        }
        canvas.restore()
    }
}
