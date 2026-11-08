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
        assertEquals(0.6,a.reps.single().down.duration,0.0001)
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
    @Test fun sensitiveCountsSixtyShallowRepsThatStandardRejects() {
        val channels=mapOf("left-2d" to trace(60,130.0,14.0))
        assertEquals(0,MultiViewCounter.detect(channels,90.0,DetectionSensitivity.STANDARD).reps.size)
        assertEquals(60,MultiViewCounter.detect(channels,90.0,DetectionSensitivity.SENSITIVE).reps.size)
    }
    @Test fun verySensitiveAllowsEvenSmallerMovements() {
        val channels=mapOf("left-3d" to trace(60,130.0,9.0))
        assertEquals(0,MultiViewCounter.detect(channels,90.0,DetectionSensitivity.SENSITIVE).reps.size)
        assertEquals(60,MultiViewCounter.detect(channels,90.0,DetectionSensitivity.VERY_SENSITIVE).reps.size)
    }
    @Test fun smallerBodyTravelIsAccepted() {
        for ((span,mode) in listOf(0.09 to DetectionSensitivity.SENSITIVE,0.06 to DetectionSensitivity.VERY_SENSITIVE)) {
            val channels=mapOf("left-reach" to trace(10,0.8,span))
            assertEquals(0,MultiViewCounter.detect(channels,15.0,DetectionSensitivity.STANDARD).reps.size)
            assertEquals(10,MultiViewCounter.detect(channels,15.0,mode).reps.size)
        }
    }
    @Test fun deepRepsDoNotSetAnUnreachableBottomForShallowReps() {
        val values=List(10) { rep -> cycle.map { 170.0-(1-it)*(if(rep%2==0)60.0 else 14.0) } }.flatten()
        val channels=mapOf("left-3d" to values.mapIndexed { i,a -> AngleSample(i/10.0,a) })
        assertEquals(10,MultiViewCounter.detect(channels,15.0,DetectionSensitivity.SENSITIVE).reps.size)
        assertEquals(5,MultiViewCounter.detect(channels,15.0,DetectionSensitivity.STANDARD).reps.size)
    }
    @Test fun reducedUpwardReturnCanCompleteASensitiveRep() {
        val values=cycle.mapIndexed { i,a -> if(i>=12)0.7 else a }
        val channels=mapOf("left-2d" to values.mapIndexed { i,a -> AngleSample(i/10.0,95+a*45) })
        assertEquals(0,MultiViewCounter.detect(channels,1.5,DetectionSensitivity.STANDARD).reps.size)
        assertEquals(1,MultiViewCounter.detect(channels,1.5,DetectionSensitivity.SENSITIVE).reps.size)
    }
    @Test fun allModesRejectSmallJitterAndSingleFrameSpikes() {
        val spikes=List(150) { i -> AngleSample(i/10.0,if(i%15==7)80.0 else 170.0) }
        for(mode in DetectionSensitivity.entries) {
            assertEquals(0,MultiViewCounter.detect(mapOf("left-3d" to trace(10,130.0,5.0)),15.0,mode).reps.size)
            assertEquals(0,MultiViewCounter.detect(mapOf("left-3d" to spikes),15.0,mode).reps.size)
        }
    }
    @Test fun startsAtBottomAndFastOscillationDoNotInventReps() {
        val tail=trace(1).drop(7).mapIndexed { i,s -> s.copy(time=i/10.0) }
        val rapid=trace(10).mapIndexed { i,s -> s.copy(time=i*0.02) }
        assertEquals(0,MultiViewCounter.detect(mapOf("left-3d" to tail),1.0,DetectionSensitivity.SENSITIVE).reps.size)
        assertEquals(0,MultiViewCounter.detect(mapOf("left-3d" to rapid),3.0,DetectionSensitivity.SENSITIVE).reps.size)
    }
    @Test fun unknownOrMissingPreferenceUsesSensitiveDefault() {
        assertEquals(DetectionSensitivity.SENSITIVE,DetectionSensitivity.fromName(null))
        assertEquals(DetectionSensitivity.SENSITIVE,DetectionSensitivity.fromName("invalid"))
        assertEquals(DetectionSensitivity.VERY_SENSITIVE,DetectionSensitivity.fromName("VERY_SENSITIVE"))
    }

    @Test fun quickShallowMovementsWithTopPausesAreNotRejected() {
        val values=List(60) { listOf(144.0,144.0,144.0,144.0,130.0,130.0,144.0,144.0,144.0,144.0) }.flatten()
        val channels=mapOf("left-2d" to values.mapIndexed { i,a -> AngleSample(i/10.0,a) })
        assertEquals(60,MultiViewCounter.detect(channels,60.0,DetectionSensitivity.SENSITIVE).reps.size)
    }

}
