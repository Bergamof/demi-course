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
    fun `the remainder complements how far into the segment the turnaround falls`() {
        val s = SessionCalculator.compute(seedSteps, HalfBy.DISTANCE)
        val turn = s.turn!!
        // The turnaround falls inside one of the 1 km repetitions, so what is left of it is
        // whatever the turnaround did not eat — the two always add back up to the segment.
        assertEquals(1.0, turn.intoDistance + turn.restDistance, 1e-6)
        assertEquals(277.5, turn.intoTime + turn.restTime, 1e-6)
        assertEquals(1.0 - turn.intoDistance, turn.restDistance, 1e-6)
    }

    @Test
    fun `the remainder covers the current repetition only, not the rest of the step`() {
        // 3 x 2 min: the turnaround at half of 6 min falls 1 min into the second repetition,
        // so 1 min is left to run of it — not the 3 min left of the step as a whole.
        val intervals = StepSpec(
            id = "i1", paceMode = PaceMode.SINGLE, pace = "5.00", measure = Measure.DURATION, value = "2.00", reps = 3,
        )
        val turn = SessionCalculator.compute(listOf(intervals), HalfBy.DURATION).turn!!
        assertEquals(2, turn.rep)
        assertEquals(SegmentKind.RUN, turn.kind)
        assertEquals(60.0, turn.intoTime, 1e-6)
        assertEquals(60.0, turn.restTime, 1e-6)
        assertEquals(0.2, turn.restDistance, 1e-6) // 1 min at 5.00 = 200 m
    }

    @Test
    fun `a turnaround inside a recovery reports what is left of that recovery`() {
        val withRecovery = StepSpec(
            id = "r2", paceMode = PaceMode.SINGLE, pace = "4.00", measure = Measure.DISTANCE, value = "1", reps = 3,
            recovery = true, recDur = "5.00", recPaceMode = PaceMode.SINGLE, recPace = "10.00",
        )
        val turn = SessionCalculator.compute(listOf(withRecovery), HalfBy.DURATION).turn!!
        // 3 x (240 s run + 300 s recovery) = 1620 s; half is 810 s, which is 30 s into the
        // second recovery — so 4m30s of that recovery are left, and nothing further counts.
        assertEquals(SegmentKind.REC, turn.kind)
        assertEquals(2, turn.rep)
        assertEquals(30.0, turn.intoTime, 1e-6)
        assertEquals(270.0, turn.restTime, 1e-6)
        assertEquals(0.45, turn.restDistance, 1e-6) // 4m30s at the 10.00 recovery pace
    }

    @Test
    fun `a lone step leaves its own second half after the turnaround`() {
        // One step, one repetition, no recovery: the segment *is* the step, so half of it remains.
        val single = StepSpec(id = "o1", paceMode = PaceMode.SINGLE, pace = "5.00", measure = Measure.DISTANCE, value = "4")
        val turn = SessionCalculator.compute(listOf(single), HalfBy.DISTANCE).turn!!
        assertEquals(2.0, turn.intoDistance, 1e-6)
        assertEquals(2.0, turn.restDistance, 1e-6)
        assertEquals(600.0, turn.restTime, 1e-6)
    }

    @Test
    fun `empty session has no turnaround`() {
        val s = SessionCalculator.compute(emptyList(), HalfBy.DISTANCE)
        assertEquals(0.0, s.dMid, 1e-9)
        org.junit.Assert.assertNull(s.turn)
    }

    private fun assertNotEquals(a: Double, b: Double) = org.junit.Assert.assertNotEquals(a, b, 1e-6)
}
