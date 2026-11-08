package com.pushupanalyzer.app

import android.graphics.*
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class FrameComposer(private val analysis: Analysis) : AutoCloseable {
    val bitmap: Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ink = Color.rgb(16,25,40)
    private val upColor = Color.rgb(75,195,255)
    private val downColor = Color.rgb(255,175,90)
    private fun seconds(time: Double?) = time?.let { String.format(Locale.US,"%.2fs",it) } ?: "--"
    fun compose(video: Bitmap, time: Double): Bitmap {
        canvas.drawColor(ink)
        val scale = min(WIDTH.toFloat() / video.width, VIDEO_HEIGHT.toFloat() / video.height)
        val w = video.width * scale; val h = video.height * scale
        canvas.drawBitmap(video,null,RectF((WIDTH-w)/2,(VIDEO_HEIGHT-h)/2,(WIDTH+w)/2,(VIDEO_HEIGHT+h)/2),paint)
        paint.color = Color.argb(210,16,25,40)
        canvas.drawRoundRect(12f,12f,WIDTH-12f,108f,10f,10f,paint)
        paint.color = Color.WHITE; paint.textSize = 27f; paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("REPS ${analysis.countAt(time)}     ELAPSED ${seconds(time)}",28f,49f,paint)
        canvas.drawText("UP ${seconds(analysis.timeAt(Phase.UP,time))}     DOWN ${seconds(analysis.timeAt(Phase.DOWN,time))}",28f,88f,paint)
        paint.typeface = Typeface.DEFAULT
        drawGraph(time)
        return bitmap
    }
    private fun drawGraph(time: Double) {
        val left = 76f; val right = WIDTH-28f; val top = VIDEO_HEIGHT+65f; val bottom = HEIGHT-62f
        paint.color = ink; canvas.drawRect(0f,VIDEO_HEIGHT.toFloat(),WIDTH.toFloat(),HEIGHT.toFloat(),paint)
        paint.color = Color.WHITE; paint.textSize = 22f
        canvas.drawText("PHASE DURATION (seconds)",left,VIDEO_HEIGHT+32f,paint)
        paint.color = upColor; canvas.drawText("UP",WIDTH-190f,VIDEO_HEIGHT+32f,paint)
        paint.color = downColor; canvas.drawText("DOWN",WIDTH-130f,VIDEO_HEIGHT+32f,paint)
        val maxY = max(1.0, (analysis.segments.maxOfOrNull { it.duration } ?: 1.0) * 1.15)
        fun x(t: Double) = left + (t / analysis.duration * (right-left)).toFloat()
        fun y(t: Double) = bottom - (t / maxY * (bottom-top)).toFloat()
        paint.textSize = 18f; paint.strokeWidth = 1f
        for (i in 0..4) {
            val value = maxY*i/4; val yy = y(value)
            paint.color = Color.rgb(60,76,96); canvas.drawLine(left,yy,right,yy,paint)
            paint.color = Color.LTGRAY; canvas.drawText(String.format(Locale.US,"%.1f",value),14f,yy+6f,paint)
        }
        for (phase in Phase.entries) {
            paint.color = if (phase == Phase.UP) upColor else downColor
            paint.strokeWidth = 3f
            var previous: Segment? = null
            for (s in analysis.segments.filter { it.phase == phase }) {
                // Completed phase timings are placed at their completion timestamp.
                previous?.let { p -> canvas.drawLine(x(p.end),y(p.duration),x(s.end),y(s.duration),paint) }
                canvas.drawCircle(x(s.end),y(s.duration),4f,paint)
                previous = s
            }
        }
        paint.color = Color.WHITE; paint.strokeWidth = 2f
        canvas.drawLine(x(time),top,x(time),bottom,paint)
        paint.textSize = 18f
        canvas.drawText("0s",left,bottom+26,paint)
        canvas.drawText(seconds(analysis.duration),right-90,bottom+26,paint)
        paint.textSize = 16f; paint.color = Color.LTGRAY
        canvas.drawText("Estimated full cycles • adaptive pose • 0.1s sampling",left,HEIGHT-10f,paint)
    }
    override fun close() { bitmap.recycle() }
    companion object {
        const val WIDTH = 960
        const val HEIGHT = 960
        const val VIDEO_HEIGHT = 640
    }
}
