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
    fun `turnaround reports what is left of its step, repetitions included`() {
        val s = SessionCalculator.compute(seedSteps, HalfBy.DISTANCE)
        val turn = s.turn!!
        // The turnaround falls in rep 2 of the 4x1 km step, at half of 6.897 km = 3.449 km;
        // the warm-up ate 1.667 km, so 1.782 km of the step are done and 4 - 1.782 remain.
        val doneInStep = s.dMid / 2 - 1.0 / 6.0 * 10.0
        assertEquals(4.0 - doneInStep, turn.stepRestDistance, 1e-6)
        // Same point expressed in time: the step's own total is 4 x 1 km at the 4.30-4.45 midpoint.
        assertEquals(4.0 * 277.5 - doneInStep * 277.5, turn.stepRestTime, 1e-6)
    }

    @Test
    fun `step remainder is measured across the whole step, not just the current segment`() {
        val withRecovery = StepSpec(
            id = "r1", paceMode = PaceMode.SINGLE, pace = "4.00", measure = Measure.DISTANCE, value = "1", reps = 2,
            recovery = true, recDur = "5.00", recPaceMode = PaceMode.SINGLE, recPace = "10.00",
        )
        val s = SessionCalculator.compute(listOf(withRecovery), HalfBy.DISTANCE)
        val turn = s.turn!!
        // The step is 2 x (1 km run + 0.5 km recovery) = 3 km, so the turnaround at 1.5 km lands
        // exactly at the end of the first recovery: the whole second repetition is still to come.
        assertEquals(SegmentKind.REC, turn.kind)
        assertEquals(1.5, turn.stepRestDistance, 1e-6)
        // Time: the step totals 2 x (240 s + 300 s) = 1080 s, of which the first repetition's 540 s are done.
        assertEquals(540.0, turn.stepRestTime, 1e-6)
    }

    @Test
    fun `a lone step leaves its own second half after the turnaround`() {
        // One step, one repetition: half of it is done at the turnaround, so half of it remains.
        val single = StepSpec(id = "o1", paceMode = PaceMode.SINGLE, pace = "5.00", measure = Measure.DISTANCE, value = "4")
        val turn = SessionCalculator.compute(listOf(single), HalfBy.DISTANCE).turn!!
        assertEquals(2.0, turn.intoDistance, 1e-6)
        assertEquals(2.0, turn.stepRestDistance, 1e-6)
        assertEquals(600.0, turn.stepRestTime, 1e-6)
    }

    @Test
    fun `empty session has no turnaround`() {
        val s = SessionCalculator.compute(emptyList(), HalfBy.DISTANCE)
        assertEquals(0.0, s.dMid, 1e-9)
        org.junit.Assert.assertNull(s.turn)
    }

    private fun assertNotEquals(a: Double, b: Double) = org.junit.Assert.assertNotEquals(a, b, 1e-6)
}
