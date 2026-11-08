package com.pushupanalyzer.app

import org.junit.Assert.*
import org.junit.Test

class RepDetectorTest {
    private fun samples(values: List<Double?>, dt: Double = 0.1) = values.mapIndexed { i, a -> AngleSample(i*dt,a) }
    private val cycle = listOf(170.0,170.0,170.0,160.0,140.0,120.0,100.0,80.0,80.0,90.0,110.0,130.0,150.0,170.0,170.0)
    @Test fun fullCycleHasSeparateDownAndUpTimes() {
        val a = RepDetector.detect(samples(cycle),1.5)
        assertEquals(1,a.reps.size)
        assertEquals(0.5,a.reps[0].down.duration,0.0001)
        assertEquals(0.4,a.reps[0].up.duration,0.0001)
        assertEquals(0,a.countAt(1.19)); assertEquals(1,a.countAt(1.21))
        assertEquals(0.2,a.timeAt(Phase.DOWN,0.5)!!,0.0001)
        assertEquals(0.4,a.timeAt(Phase.UP,1.4)!!,0.0001)
    }
    @Test fun repeatedCyclesCountOnceEach() {
        assertEquals(3,RepDetector.detect(samples(cycle+cycle+cycle),4.5).reps.size)
    }
    @Test fun slowRepetitionsAreNotDiscarded() {
        val a = RepDetector.detect(samples(cycle,0.3),4.5)
        assertEquals(1,a.reps.size)
        assertTrue(a.reps[0].down.duration > 1.4)
    }
    @Test fun partialRepetitionAtStartOrEndDoesNotCount() {
        assertEquals(0,RepDetector.detect(samples(cycle.take(10)),1.0).reps.size)
        assertEquals(0,RepDetector.detect(samples(cycle.drop(5)),1.0).reps.size)
    }
    @Test fun jitterAndShallowBendsDoNotCount() {
        assertEquals(0,RepDetector.detect(samples(List(100) { if (it%2==0) 168.0 else 161.0 }),10.0).reps.size)
        assertEquals(0,RepDetector.detect(samples(cycle.map { it.coerceAtLeast(125.0) }),1.5).reps.size)
    }
    @Test fun missingPoseBreaksCycleWithoutInterpolation() {
        val values = cycle.mapIndexed { i,a -> if (i==8) null else a }
        val a = RepDetector.detect(samples(values),1.5)
        assertEquals(0,a.reps.size); assertTrue(a.coverage < 1.0)
    }
    @Test fun timestampGapBreaksCycle() {
        val values = samples(cycle).mapIndexed { i,a -> if (i>=8) a.copy(time=a.time+1) else a }
        assertEquals(0,RepDetector.detect(values,2.5).reps.size)
    }
    @Test fun invalidSamplesAndEmptyInputAreSafe() {
        assertEquals(0,RepDetector.detect(emptyList(),1.0).reps.size)
        assertEquals(0.0,RepDetector.detect(emptyList(),1.0).coverage,0.0)
        assertEquals(0,RepDetector.detect(samples(listOf(Double.NaN,null,Double.POSITIVE_INFINITY)),1.0).reps.size)
    }
    @Test fun angleIsIndependentOfClothingAndScale() {
        assertEquals(90.0,RepDetector.elbowAngle(0.0,0.0,0.0,1.0,1.0,1.0)!!,0.001)
        assertEquals(180.0,RepDetector.elbowAngle(0.0,0.0,0.0,2.0,0.0,4.0)!!,0.001)
        assertNull(RepDetector.elbowAngle(0.0,0.0,0.0,0.0,1.0,1.0))
    }
    @Test(expected = IllegalArgumentException::class) fun nonIncreasingTimestampsAreRejected() {
        RepDetector.detect(listOf(AngleSample(1.0,170.0),AngleSample(1.0,90.0)),2.0)
    }
}
