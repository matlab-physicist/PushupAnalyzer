package com.pushupanalyzer.app

import kotlin.math.*

data class AngleSample(val time: Double, val angle: Double?)
enum class Phase { UP, DOWN }
data class Segment(val start: Double, val end: Double, val phase: Phase) {
    val duration: Double get() = end - start
}
data class Repetition(val down: Segment, val up: Segment)
data class Analysis(val duration: Double, val reps: List<Repetition>, val coverage: Double) {
    val segments: List<Segment> = reps.flatMap { listOf(it.down, it.up) }
    fun countAt(time: Double): Int = reps.count { it.up.end <= time }
    fun timeAt(phase: Phase, time: Double): Double? {
        val s = segments.lastOrNull { it.phase == phase && it.start <= time } ?: return null
        return (time.coerceAtMost(s.end) - s.start).coerceAtLeast(0.0)
    }
}

/** Full extended -> bent -> extended cycles. Missing poses break cycles.
 * Times are estimates with 100 ms sample resolution, not form certification.
 */
object RepDetector {
    fun elbowAngle(sx: Double, sy: Double, ex: Double, ey: Double, wx: Double, wy: Double): Double? {
        val ax = sx - ex; val ay = sy - ey
        val bx = wx - ex; val by = wy - ey
        val length = hypot(ax, ay) * hypot(bx, by)
        if (length < 1e-6) return null
        return Math.toDegrees(acos(((ax * bx + ay * by) / length).coerceIn(-1.0, 1.0)))
    }
    fun detect(samples: List<AngleSample>, duration: Double): Analysis {
        require(duration > 0 && duration.isFinite())
        require(samples.zipWithNext().all { (a, b) -> b.time > a.time })
        val reps = mutableListOf<Repetition>()
        var state = 0 // seeking top / descending / ascending
        var top = 0.0; var bottom = 0.0; var minimum = 180.0
        var previousTime = Double.NEGATIVE_INFINITY
        var valid = 0
        val window = ArrayDeque<AngleSample>()
        for (sample in samples) {
            val angle = sample.angle
            if (angle == null || !angle.isFinite() || angle !in 0.0..180.0) {
                state = 0; window.clear(); previousTime = Double.NEGATIVE_INFINITY
                continue
            }
            valid++
            if (sample.time - previousTime > 0.35) { state = 0; window.clear() }
            previousTime = sample.time
            window.addLast(sample)
            if (window.size > 3) window.removeFirst()
            if (window.size < 3) continue
            val a = window.map { it.angle!! }.sorted()[1]
            val t = window.elementAt(1).time
            when (state) {
                0 -> if (a >= 150.0) { top = t; minimum = a; state = 1 }
                1 -> {
                    if (a >= 150.0) top = t
                    if (a <= minimum) { minimum = a; bottom = t }
                    if (minimum <= 110.0 && a >= minimum + 8.0) state = 2
                    if (t - top > 30.0) state = 0
                }
                2 -> {
                    if (a < minimum) { minimum = a; bottom = t; state = 1 }
                    if (a >= 150.0) {
                        val down = bottom - top; val up = t - bottom
                        if (down in 0.15..15.0 && up in 0.15..15.0) {
                            reps += Repetition(Segment(top, bottom, Phase.DOWN), Segment(bottom, t, Phase.UP))
                        }
                        top = t; minimum = a; state = 1
                    }
                    if (t - bottom > 15.0) state = 0
                }
            }
        }
        return Analysis(duration, reps, valid.toDouble() / samples.size.coerceAtLeast(1))
    }
}
