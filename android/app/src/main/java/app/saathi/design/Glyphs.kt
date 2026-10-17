package app.saathi.design

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Saathi's drawn icon set, on a 24-unit grid with one heavy stroke weight, used by the overlay and
 * setup alike. Each sign kind has a distinct shape so state never depends on colour alone.
 */
enum class Glyph { ARROW_DOWN, ARROW_UP, CHECK, LOCK, WARNING, STOP, MIC, REPEAT, CLOSE, SPEAKER, GUIDE }

object Glyphs {
    private const val STROKE = 3.2f

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
            Glyph.ARROW_DOWN -> arrow(canvas, fill, down = true)
            Glyph.ARROW_UP -> arrow(canvas, fill, down = false)
            Glyph.CHECK -> canvas.drawPath(Path().apply { moveTo(4.5f, 12.5f); lineTo(9.5f, 17.5f); lineTo(19.5f, 6.5f) }, stroke.apply { strokeWidth = 3.6f })
            Glyph.LOCK -> {
                canvas.drawRoundRect(RectF(5f, 10.5f, 19f, 21f), 2f, 2f, fill)
                canvas.drawPath(Path().apply { moveTo(8.5f, 10.5f); lineTo(8.5f, 7.5f); cubicTo(8.5f, 2.8f, 15.5f, 2.8f, 15.5f, 7.5f); lineTo(15.5f, 10.5f) }, stroke)
            }
            Glyph.WARNING -> {
                val knock = Paint(fill).apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) }
                // Punch the exclamation mark out so it reads on any band colour.
                canvas.saveLayer(0f, 0f, 24f, 24f, null)
                canvas.drawPath(Path().apply { moveTo(12f, 2.5f); lineTo(22.5f, 21f); lineTo(1.5f, 21f); close() }, fill)
                canvas.drawRoundRect(RectF(10.8f, 8.5f, 13.2f, 15f), 1.2f, 1.2f, knock)
                canvas.drawCircle(12f, 18f, 1.4f, knock)
                canvas.restore()
            }
            Glyph.STOP -> {
                canvas.drawCircle(12f, 12f, 8.5f, stroke)
                canvas.drawCircle(12f, 12f, 4f, fill)
            }
            Glyph.MIC -> {
                canvas.drawRoundRect(RectF(8.5f, 2.5f, 15.5f, 14.5f), 3.5f, 3.5f, fill)
                canvas.drawPath(Path().apply { moveTo(5f, 11f); cubicTo(5f, 19.5f, 19f, 19.5f, 19f, 11f) }, stroke)
                canvas.drawLine(12f, 17.5f, 12f, 21.5f, stroke)
            }
            Glyph.REPEAT -> {
                // A circular arrow: a 300° arc ending at the top, with its head pointing on round.
                canvas.drawArc(RectF(4.5f, 4.5f, 19.5f, 19.5f), -30f, 300f, false, stroke)
                canvas.drawPath(Path().apply { moveTo(10.5f, 0.8f); lineTo(16f, 4.5f); lineTo(10.5f, 8.2f); close() }, fill)
            }
            Glyph.CLOSE -> {
                canvas.drawLine(6f, 6f, 18f, 18f, stroke)
                canvas.drawLine(18f, 6f, 6f, 18f, stroke)
            }
            Glyph.SPEAKER -> {
                canvas.drawPath(Path().apply { moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 4.5f); lineTo(12f, 19.5f); lineTo(7f, 15f); lineTo(3f, 15f); close() }, fill)
                canvas.drawArc(RectF(10f, 7.5f, 17f, 16.5f), -50f, 100f, false, stroke)
                canvas.drawArc(RectF(10f, 3.5f, 21.5f, 20.5f), -50f, 100f, false, stroke)
            }
            Glyph.GUIDE -> {
                // Saathi's mark: an arrow arriving at a station dot.
                canvas.drawLine(2.5f, 12f, 13f, 12f, stroke)
                canvas.drawPath(Path().apply { moveTo(9f, 7f); lineTo(14f, 12f); lineTo(9f, 17f) }, stroke)
                canvas.drawCircle(19.5f, 12f, 3f, fill)
            }
        }
        canvas.restore()
    }

    /** A heavy signage arrow: wide head, thick shaft. */
    private fun arrow(canvas: Canvas, fill: Paint, down: Boolean) {
        val p = Path().apply {
            moveTo(9.5f, 2f); lineTo(14.5f, 2f); lineTo(14.5f, 12.5f); lineTo(20.5f, 12.5f)
            lineTo(12f, 22f); lineTo(3.5f, 12.5f); lineTo(9.5f, 12.5f); close()
        }
        if (!down) {
            canvas.save()
            canvas.rotate(180f, 12f, 12f)
            canvas.drawPath(p, fill)
            canvas.restore()
        } else {
            canvas.drawPath(p, fill)
        }
    }
}
