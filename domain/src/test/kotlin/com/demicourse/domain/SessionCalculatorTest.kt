package com.demicourse.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionCalculatorTest {

    // The prototype's seed session: warm-up, 4x1km fractionné, cool-down.
    private val warmup = StepSpec(id = "s1", name = "Échauffement", paceMode = PaceMode.SINGLE, pace = "6.00", measure = Measure.DURATION, value = "10.00", reps = 1)
    private val fractionne = StepSpec(id = "s2", paceMode = PaceMode.RANGE, pace = "4.30", paceMax = "4.45", measure = Measure.DISTANCE, value = "1", reps = 4)
    private val cooldown = StepSpec(id = "s3", name = "Retour au calme", paceMode = PaceMode.SINGLE, pace = "6.30", measure = Measure.DURATION, value = "8.00", reps = 1)
    private val seedSteps = listOf(warmup, fractionne, cooldown)

    @Test
    fun `totals the seed session`() {
        val s = SessionCalculator.compute(seedSteps, HalfBy.DISTANCE)
        // 10 min @ 6.00 = 1.6667 km; 4x1 km = 4 km; 8 min @ 6.30 = 1.2308 km.
        assertEquals(1.0 / 6.0 * 10.0 + 4.0 + 8.0 / 6.5, s.dMid, 1e-6)
        assertEquals(600.0 + 1110.0 + 480.0, s.tMid, 1e-6)
    }

    @Test
    fun `turnaround by distance lands mid-way through the second interval repetition`() {
        val s = SessionCalculator.compute(seedSteps, HalfBy.DISTANCE)
        val turn = s.turn
        assertNotNull(turn)
        turn!!
        assertEquals(1, turn.stepIndex) // the fractionné step
        assertEquals(2, turn.rep)
        assertEquals(4, turn.reps)
        assertEquals(SegmentKind.RUN, turn.kind)
        assertEquals(s.dMid / 2, turn.distance, 1e-6)
    }

    @Test
    fun `turnaround by duration differs from turnaround by distance`() {
        val byDist = SessionCalculator.compute(seedSteps, HalfBy.DISTANCE).turn!!
        val byDur = SessionCalculator.compute(seedSteps, HalfBy.DURATION).turn!!
        // By duration, target = tMid/2 = 1095s, which also falls in rep 2 of the fractionné here,
        // but at a different point in time/distance than the distance-based target.
        assertNotEquals(byDist.time, byDur.time)
    }

    @Test
    fun `recovery segments are included and can host the turnaround`() {
        val withRecovery = StepSpec(
            id = "r1", paceMode = PaceMode.SINGLE, pace = "4.00", measure = Measure.DISTANCE, value = "1", reps = 2,
            recovery = true, recDur = "5.00", recPaceMode = PaceMode.SINGLE, recPace = "10.00",
        )
        val s = SessionCalculator.compute(listOf(withRecovery), HalfBy.DISTANCE)
        // total distance = 2 * (1 + 0.5) = 3 km; half = 1.5 km, which falls inside the first recovery
        // (after 1 km of running, the recovery covers km 1.0..1.5 of distance).
        assertEquals(3.0, s.dMid, 1e-6)
        val turn = s.turn!!
        assertEquals(SegmentKind.REC, turn.kind)
        assertEquals(1, turn.rep)
        assertEquals(1.5, turn.distance, 1e-6)
    }

    @Test
    fun `empty session has no turnaround`() {
        val s = SessionCalculator.compute(emptyList(), HalfBy.DISTANCE)
        assertEquals(0.0, s.dMid, 1e-9)
        org.junit.Assert.assertNull(s.turn)
    }

    private fun assertNotEquals(a: Double, b: Double) = org.junit.Assert.assertNotEquals(a, b, 1e-6)
}
