package com.pushupanalyzer.app

import kotlin.math.*

data class Joint(val x: Double, val y: Double, val z: Double, val confidence: Double)

/** Camera-relative signals, calibrated independently to avoid jumps when arms have different projections. */
object MultiViewCounter {
    fun angle(a: Joint?, b: Joint?, c: Joint?, depth: Boolean): Double? {
        if (listOf(a,b,c).any { it == null || it.confidence < 0.45 }) return null
        a!!; b!!; c!!
        val u = doubleArrayOf(a.x-b.x,a.y-b.y,if (depth) a.z-b.z else 0.0)
        val v = doubleArrayOf(c.x-b.x,c.y-b.y,if (depth) c.z-b.z else 0.0)
        val length = sqrt(u.sumOf { it*it } * v.sumOf { it*it })
        if (length < 1e-6) return null
        return Math.toDegrees(acos((u.indices.sumOf { u[it]*v[it] }/length).coerceIn(-1.0,1.0)))
    }
    fun reach(shoulder: Joint?, wrist: Joint?, hip: Joint?): Double? {
        if (listOf(shoulder,wrist,hip).any { it == null || it.confidence < 0.45 }) return null
        shoulder!!; wrist!!; hip!!
        fun distance(a: Joint,b: Joint) = sqrt((a.x-b.x).pow(2)+(a.y-b.y).pow(2)+(a.z-b.z).pow(2))
        val torso = distance(shoulder,hip)
        return if (torso < 1e-3) null else (distance(shoulder,wrist)/torso).takeIf { it.isFinite() && it in 0.05..4.0 }
    }
    fun detect(channels: Map<String,List<AngleSample>>, duration: Double,
               sensitivity: DetectionSensitivity = DetectionSensitivity.SENSITIVE): Analysis {
        require(duration > 0 && duration.isFinite())
        val candidates = channels.mapNotNull { (name,samples) ->
            val values = samples.mapNotNull { it.angle?.takeIf(Double::isFinite) }.sorted()
            if (values.size < 8) return@mapNotNull null
            fun percentile(p: Double) = values[((values.size-1)*p).toInt()]
            val low = percentile(0.05); val high = percentile(0.95); val range = high-low
            // Absolute noise floors stop stationary poses from becoming reps after normalization.
            val floor = if (name.contains("reach")) sensitivity.reachExcursion else sensitivity.angleExcursion
            if (range < floor) return@mapNotNull null
            val result = count(samples,duration,floor,sensitivity.recovery)
            val coverage = values.size.toDouble()/samples.size.coerceAtLeast(1)
            val lengths = result.reps.map { it.up.end-it.down.start }
            val consistency = if (lengths.size < 2) 0.7 else {
                val mean = lengths.average()
                1.0/(1.0+sqrt(lengths.sumOf { (it-mean).pow(2) }/lengths.size)/mean)
            }
            val preference = when { name.contains("3d") -> 1.0; name.contains("2d") -> 0.95; else -> 0.85 }
            val quality = coverage * consistency * preference * (range/(floor*2)).coerceAtMost(1.0)
            Triple(result.copy(coverage=coverage),quality,result.reps.isNotEmpty())
        }
        // Select one coherent trajectory for the whole clip, never add both arms' counts.
        return candidates.filter { it.third }.maxByOrNull { it.second }?.first
            ?: candidates.maxByOrNull { it.second }?.first
            ?: Analysis(duration,emptyList(),channels.values.maxOfOrNull { s ->
                s.count { it.angle != null }.toDouble()/s.size.coerceAtLeast(1) } ?: 0.0)
    }
    private fun count(input: List<AngleSample>, duration: Double, excursion: Double, recovery: Double): Analysis {
        require(duration > 0)
        require(input.zipWithNext().all { (a,b) -> b.time>a.time })
        val samples = input.map { it.copy(angle=it.angle?.takeIf(Double::isFinite)) }.toMutableList()
        // Bridge brief occlusion only; long gaps remain breaks in the state machine.
        var previous = -1
        for (i in samples.indices) if (samples[i].angle != null) {
            if (previous >= 0 && i>previous+1 && samples[i].time-samples[previous].time <= 0.31) {
                val a=samples[previous]; val b=samples[i]
                for (j in previous+1 until i) samples[j]=AngleSample(samples[j].time,
                    a.angle!!+(b.angle!!-a.angle)*(samples[j].time-a.time)/(b.time-a.time))
            }
            previous=i
        }
        val window=ArrayDeque<AngleSample>(); val reps=mutableListOf<Repetition>()
        var state=0 // establish a local top, descend, then recover upward
        var top=0.0; var bottom=0.0; var peak=Double.NEGATIVE_INFINITY
        var minimum=0.0; var prior=Double.NEGATIVE_INFINITY
        fun reset() { state=0; peak=Double.NEGATIVE_INFINITY; window.clear() }
        for (s in samples) {
            if (s.angle == null || !s.angle.isFinite()) { reset();continue }
            if (s.time-prior>0.35) reset()
            prior=s.time; window.addLast(s); if(window.size>3)window.removeFirst()
            if(window.size<3)continue
            val a=window.map { it.angle!! }.sorted()[1]; val t=window.elementAt(1).time
            when(state) {
                0 -> {
                    // Follow each local peak; shallow reps need not reach the clip's deepest bottom.
                    if(a>=peak){peak=a;top=t}
                    if(peak-a>=excursion){minimum=a;bottom=t;state=1}
                }
                1 -> {
                    if(a<=minimum){minimum=a;bottom=t}
                    val reversal=max(excursion*0.25,(peak-minimum)*0.15)
                    if(a-minimum>=reversal)state=2
                    if(t-top>30)reset()
                }
                2 -> {
                    if(a<minimum){minimum=a;bottom=t;state=1}
                    if(a>=minimum+(peak-minimum)*recovery){
                        val down=bottom-top;val up=t-bottom
                        if(down in 0.15..15.0 && up in 0.15..15.0 && down+up>=0.5)
                            reps+=Repetition(Segment(top,bottom,Phase.DOWN),Segment(bottom,t,Phase.UP))
                        peak=a;top=t;state=0
                    }
                    if(t-bottom>15)reset()
                }
            }
        }
        return Analysis(duration,reps,1.0)
    }
}
