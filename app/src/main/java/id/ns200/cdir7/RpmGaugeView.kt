package id.ns200.cdir7

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class RpmGaugeView(context: Context) : View(context) {
    var rpm = 0; set(value) { field = value.coerceIn(0, 12000); invalidate() }
    var limit = 9500; set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, min(width * 3 / 4, MeasureSpec.getSize(h).takeIf { it > 0 } ?: width))
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height * .78f; val r = min(width, height) * .55f
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * .075f; paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(51, 51, 51); c.drawArc(cx-r, cy-r, cx+r, cy+r, 150f, 240f, false, paint)
        paint.color = if (rpm >= limit) Color.rgb(255, 68, 68) else Color.rgb(242, 125, 38)
        c.drawArc(cx-r, cy-r, cx+r, cy+r, 150f, 240f * rpm / 12000f, false, paint)
        for (n in 0..12) {
            val a = Math.toRadians((150 + n * 20).toDouble())
            val x1 = cx + cos(a).toFloat() * r * .82f; val y1 = cy + sin(a).toFloat() * r * .82f
            val x2 = cx + cos(a).toFloat() * r * .95f; val y2 = cy + sin(a).toFloat() * r * .95f
            paint.strokeWidth = 3f; paint.color = Color.rgb(154,160,166); c.drawLine(x1,y1,x2,y2,paint)
        }
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); paint.color = Color.rgb(0,229,255)
        paint.textSize = r * .35f; c.drawText(rpm.toString(), cx, cy-r*.12f, paint)
        paint.textSize = r * .12f; paint.color = Color.rgb(154,160,166)
        c.drawText("RPM  •  LIMIT $limit", cx, cy+r*.10f, paint)
    }
}
