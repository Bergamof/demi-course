package com.demicourse.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattingTest {
    private fun turn(
        kind: SegmentKind,
        rep: Int = 1,
        reps: Int = 1,
    ) = TurnPoint(
        stepIndex = 0,
        rep = rep,
        reps = reps,
        kind = kind,
        distance = 1.0,
        time = 300.0,
        intoDistance = 0.25,
        intoTime = 75.0,
        restDistance = 0.75,
        restTime = 225.0,
    )

    @Test
    fun `the marker of a repeated effort names the repetition and counts what is left of it`() {
        val text =
            Formatting.turnaroundMarkerText(
                turn(SegmentKind.RUN, rep = 2, reps = 3),
                PaceUnit.MIN_PER_KM,
                Measure.DISTANCE,
            )
        assertTrue(text, text.contains("pendant l’effort de la répétition 2 sur 3"))
        assertTrue(text, text.contains("il reste 0,75 km dans la répétition"))
    }

    @Test
    fun `the marker of a single effort speaks of the effort`() {
        val text = Formatting.turnaroundMarkerText(turn(SegmentKind.RUN), PaceUnit.MIN_PER_KM, Measure.DISTANCE)
        assertTrue(text, text.contains("pendant l’effort —"))
        assertTrue(text, text.contains("dans l’effort."))
    }

    @Test
    fun `an effort set by duration reports what is left as a time`() {
        val text = Formatting.turnaroundMarkerText(turn(SegmentKind.RUN), PaceUnit.MIN_PER_KM, Measure.DURATION)
        assertTrue(text, text.contains("il reste 3m45s"))
    }

    @Test
    fun `a recovery always reports what is left as a time`() {
        val text =
            Formatting.turnaroundMarkerText(
                turn(SegmentKind.REC, rep = 1, reps = 2),
                PaceUnit.MIN_PER_KM,
                Measure.DISTANCE,
            )
        assertTrue(text, text.contains("pendant la récupération de la répétition 1 sur 2"))
        assertTrue(text, text.contains("il reste 3m45s dans la récupération"))
    }

    @Test
    fun `the marker uses the unit of the user`() {
        val text = Formatting.turnaroundMarkerText(turn(SegmentKind.RUN), PaceUnit.MIN_PER_MILE, Measure.DISTANCE)
        assertTrue(text, text.contains("0,75 mi"))
    }

    @Test
    fun `step counts are pluralised`() {
        assertEquals("1 étape", Formatting.stepCountText(1))
        assertEquals("3 étapes", Formatting.stepCountText(3))
    }

    @Test
    fun `totals show a dash when nothing is computable`() {
        val empty = SessionCalculator.compute(emptyList(), HalfBy.DISTANCE)
        assertEquals("—", Formatting.totalDistanceText(empty))
        assertEquals("—", Formatting.totalDurationText(empty))
    }

    @Test
    fun `the editor hint explains the mm-ss format while the draft is unusable`() {
        val hint = Formatting.previewLine(Draft(pace = "x"), isTemplate = false, unit = PaceUnit.MIN_PER_KM)
        assertTrue(hint, hint.contains("mm.ss"))
    }

    @Test
    fun `the editor hint announces the totals of a valid draft`() {
        val draft = Draft(pace = "5.00", measure = Measure.DISTANCE, value = "2")
        assertEquals(
            "Cette étape : 2,00 km en 10m.",
            Formatting.previewLine(draft, isTemplate = false, unit = PaceUnit.MIN_PER_KM),
        )
    }
}
