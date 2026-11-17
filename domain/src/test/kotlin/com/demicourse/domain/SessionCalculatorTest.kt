package com.demicourse.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionCalculatorTest {
    // The prototype's seed session: warm-up, 4x1km fractionné, cool-down.
    private val warmup =
        StepSpec(
            id = "s1",
            name = "Échauffement",
            paceMode = PaceMode.SINGLE,
            pace = "6.00",
            measure = Measure.DURATION,
            value = "10.00",
            reps = 1,
        )
    private val fractionne =
        StepSpec(
            id = "s2",
            paceMode = PaceMode.RANGE,
            pace = "4.30",
            paceMax = "4.45",
            measure = Measure.DISTANCE,
            value = "1",
            reps = 4,
        )
    private val cooldown =
        StepSpec(
            id = "s3",
            name = "Retour au calme",
            paceMode = PaceMode.SINGLE,
            pace = "6.30",
            measure = Measure.DURATION,
            value = "8.00",
            reps = 1,
        )
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
    fun `turnaround by duration lands at half of the total time`() {
        val turn = SessionCalculator.compute(seedSteps, HalfBy.DURATION).turn!!
        // Half of 2190 s = 1095 s: 600 s of warm-up, then 495 s into the fractionné, i.e. 217.5 s into rep 2.
        assertEquals(1, turn.stepIndex)
        assertEquals(2, turn.rep)
        assertEquals(1095.0, turn.time, 1e-6)
        assertEquals(217.5, turn.intoTime, 1e-6)
    }

    @Test
    fun `halving by distance and by duration place the turnaround at different instants`() {
        val byDist = SessionCalculator.compute(seedSteps, HalfBy.DISTANCE).turn!!
        val byDur = SessionCalculator.compute(seedSteps, HalfBy.DURATION).turn!!
        assertEquals(1094.5, byDist.time, 0.05)
        assertEquals(0.5, byDur.time - byDist.time, 0.05)
    }

    @Test
    fun `halving can land in a run or in a recovery depending on the setting`() {
        // 1 km at 4.00 (240 s), then a 10 min recovery at 20.00 (0.5 km): 1.5 km and 840 s in all.
        val step =
            StepSpec(
                id = "h1",
                pace = "4.00",
                measure = Measure.DISTANCE,
                value = "1",
                recovery = true,
                recDur = "10.00",
                recPace = "20.00",
            )
        // By distance the middle is 0.75 km: inside the run. By duration it is 420 s: inside the recovery.
        assertEquals(SegmentKind.RUN, SessionCalculator.compute(listOf(step), HalfBy.DISTANCE).turn!!.kind)
        assertEquals(SegmentKind.REC, SessionCalculator.compute(listOf(step), HalfBy.DURATION).turn!!.kind)
    }

    @Test
    fun `a turnaround exactly on a segment boundary belongs to the start of the next segment`() {
        // 2 x (1 km + 0.5 km of recovery): the middle, 1.5 km, is the end of the first recovery.
        val step =
            StepSpec(
                id = "b1",
                pace = "4.00",
                measure = Measure.DISTANCE,
                value = "1",
                reps = 2,
                recovery = true,
                recDur = "5.00",
                recPace = "10.00",
            )
        val turn = SessionCalculator.compute(listOf(step), HalfBy.DISTANCE).turn!!
        assertEquals(SegmentKind.RUN, turn.kind)
        assertEquals(2, turn.rep)
        assertEquals(0.0, turn.intoDistance, 1e-6)
        assertEquals(1.0, turn.restDistance, 1e-6)
    }

    @Test
    fun `segments alternate run and recovery for every repetition including the last`() {
        val step =
            StepSpec(
                id = "g1",
                pace = "5.00",
                measure = Measure.DURATION,
                value = "2.00",
                reps = 2,
                recovery = true,
                recDur = "1.00",
                recPace = "9.00",
            )
        val kinds = SessionCalculator.compute(listOf(step), HalfBy.DISTANCE).segments.map { it.kind }
        assertEquals(listOf(SegmentKind.RUN, SegmentKind.REC, SegmentKind.RUN, SegmentKind.REC), kinds)
    }

    @Test
    fun `an invalid step is skipped and later steps keep their index`() {
        val invalid = StepSpec(id = "bad", pace = "", value = "1")
        val valid = StepSpec(id = "ok", pace = "5.00", measure = Measure.DISTANCE, value = "2")
        val s = SessionCalculator.compute(listOf(invalid, valid), HalfBy.DISTANCE)
        assertEquals(2.0, s.dMid, 1e-6)
        assertEquals(1, s.turn!!.stepIndex)
    }

    @Test
    fun `totals expose the min and max envelopes of a pace range`() {
        val s = SessionCalculator.compute(listOf(fractionne), HalfBy.DISTANCE)
        assertEquals(4.0, s.dMin, 1e-6)
        assertEquals(4.0, s.dMax, 1e-6)
        assertEquals(4 * 270.0, s.tMin, 1e-6)
        assertEquals(4 * 285.0, s.tMax, 1e-6)
    }

    @Test
    fun `a repetition count above the cap is clamped instead of exploding the segment list`() {
        val huge = StepSpec(id = "x", pace = "5.00", measure = Measure.DURATION, value = "1.00", reps = Int.MAX_VALUE)
        val s = SessionCalculator.compute(listOf(huge), HalfBy.DISTANCE)
        assertEquals(MAX_REPS, s.segments.size)
        assertEquals(MAX_REPS * 60.0, s.tMid, 1e-6)
    }

    @Test
    fun `zero or negative repetitions count as one`() {
        val step = StepSpec(id = "z", pace = "5.00", measure = Measure.DURATION, value = "1.00", reps = 0)
        assertEquals(1, SessionCalculator.compute(listOf(step), HalfBy.DISTANCE).segments.size)
    }

    @Test
    fun `recovery segments are included and can host the turnaround`() {
        val withRecovery =
            StepSpec(
                id = "r1",
                paceMode = PaceMode.SINGLE,
                pace = "4.00",
                measure = Measure.DISTANCE,
                value = "1",
                reps = 3,
                recovery = true,
                recDur = "20.00",
                recPaceMode = PaceMode.SINGLE,
                recPace = "10.00",
            )
        val s = SessionCalculator.compute(listOf(withRecovery), HalfBy.DISTANCE)
        // Each repetition is 1 km of run plus 2 km of recovery: 9 km in all, half 4.5 km.
        // That is 1 km into the second recovery, which spans km 4.0..6.0.
        assertEquals(9.0, s.dMid, 1e-6)
        val turn = s.turn!!
        assertEquals(SegmentKind.REC, turn.kind)
        assertEquals(2, turn.rep)
        assertEquals(4.5, turn.distance, 1e-6)
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
        val intervals =
            StepSpec(
                id = "i1",
                paceMode = PaceMode.SINGLE,
                pace = "5.00",
                measure = Measure.DURATION,
                value = "2.00",
                reps = 3,
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
        val withRecovery =
            StepSpec(
                id = "r2",
                paceMode = PaceMode.SINGLE,
                pace = "4.00",
                measure = Measure.DISTANCE,
                value = "1",
                reps = 3,
                recovery = true,
                recDur = "5.00",
                recPaceMode = PaceMode.SINGLE,
                recPace = "10.00",
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
        val single =
            StepSpec(id = "o1", paceMode = PaceMode.SINGLE, pace = "5.00", measure = Measure.DISTANCE, value = "4")
        val turn = SessionCalculator.compute(listOf(single), HalfBy.DISTANCE).turn!!
        assertEquals(2.0, turn.intoDistance, 1e-6)
        assertEquals(2.0, turn.restDistance, 1e-6)
        assertEquals(600.0, turn.restTime, 1e-6)
    }

    @Test
    fun `empty session has no turnaround`() {
        val s = SessionCalculator.compute(emptyList(), HalfBy.DISTANCE)
        assertEquals(0.0, s.dMid, 1e-9)
        assertNull(s.turn)
    }
}
