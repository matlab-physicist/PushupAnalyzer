package com.pushupanalyzer.app

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class MultiViewCounterTest {
    private val cycle=listOf(1.0,1.0,1.0,0.9,0.7,0.5,0.3,0.0,0.0,0.1,0.3,0.5,0.8,1.0,1.0)
    private fun trace(reps:Int,low:Double=95.0,span:Double=45.0)=List(reps){cycle}.flatten().mapIndexed { i,a -> AngleSample(i/10.0,low+a*span) }
    @Test fun sixtyForeshortenedRepsAreCountedWithoutFixedExtensionThreshold() {
        val a=MultiViewCounter.detect(mapOf("left-2d" to trace(60)),90.0)
        assertEquals(60,a.reps.size)
        assertTrue(a.reps.all { it.down.duration>0 && it.up.duration>0 })
    }
    @Test fun calibratedPhasesRetainMediaTimeAndCountAtCompletion() {
        val a=MultiViewCounter.detect(mapOf("left-2d" to trace(1)),1.5)
        assertEquals(0.5,a.reps.single().down.duration,0.0001)
        assertEquals(0.4,a.reps.single().up.duration,0.0001)
        assertEquals(0,a.countAt(1.19));assertEquals(1,a.countAt(1.21))
    }
    @Test fun rearOrFrontViewCanUseReachWhenElbowProjectionIsFlat() {
        val a=MultiViewCounter.detect(mapOf("left-2d" to trace(4,170.0,3.0),"left-reach" to trace(4,0.3,0.5)),6.0)
        assertEquals(4,a.reps.size)
    }
    @Test fun bothArmsDoNotDoubleCount() {
        assertEquals(8,MultiViewCounter.detect(mapOf("left-3d" to trace(8),"right-3d" to trace(8,80.0,60.0)),12.0).reps.size)
    }
    @Test fun briefOcclusionIsBridgedButLongGapsAreNot() {
        val short=trace(4).mapIndexed { i,s -> if(i%15==6)s.copy(angle=null) else s }
        assertEquals(4,MultiViewCounter.detect(mapOf("left-3d" to short),6.0).reps.size)
        val long=trace(4).mapIndexed { i,s -> if(i%15 in 5..9)s.copy(angle=null) else s }
        assertEquals(0,MultiViewCounter.detect(mapOf("left-3d" to long),6.0).reps.size)
    }
    @Test fun stationaryPoseAndSmallNoiseAreNotRepetitions() {
        assertEquals(0,MultiViewCounter.detect(mapOf("left-3d" to trace(10,130.0,5.0)),15.0).reps.size)
        assertEquals(0,MultiViewCounter.detect(mapOf("left-reach" to trace(10,0.9,0.03)),15.0).reps.size)
    }
    @Test fun partialCycleIsNotCounted() {
        assertEquals(0,MultiViewCounter.detect(mapOf("left-2d" to trace(1).take(10)),1.0).reps.size)
    }
    @Test fun geometryIsInvariantToCameraRotationAndTranslation() {
        val s=Joint(0.0,0.0,0.0,0.9);val e=Joint(0.0,1.0,0.0,0.9);val w=Joint(1.0,1.0,0.0,0.9)
        fun rotate(p:Joint):Joint {
            val theta=1.2
            return Joint(p.x*cos(theta)+p.z*sin(theta)+10,p.y+20,-p.x*sin(theta)+p.z*cos(theta)+30,p.confidence)
        }
        assertEquals(MultiViewCounter.angle(s,e,w,true)!!,MultiViewCounter.angle(rotate(s),rotate(e),rotate(w),true)!!,0.001)
        assertNull(MultiViewCounter.angle(s.copy(confidence=0.2),e,w,true))
    }
}
