package com.demicourse.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceMathTest {

    @Test
    fun `parses mm-ss with implicit leading zero and two-digit seconds`() {
        assertEquals(90, PaceMath.parseMmSs("01.30").value)
        assertEquals(90, PaceMath.parseMmSs("1.30").value)
    }

    @Test
    fun `single digit after separator is ones of seconds, not tens`() {
        // "2.1" = 2 min 1 s, same as "2.01" -- matches the spec's stated examples.
        assertEquals(121, PaceMath.parseMmSs("2.1").value)
        assertEquals(121, PaceMath.parseMmSs("2.01").value)
    }

    @Test
    fun `colon separator also accepted`() {
        assertEquals(90, PaceMath.parseMmSs("1:30").value)
    }

    @Test
    fun `comma decimal separator normalized to dot`() {
        assertEquals(90, PaceMath.parseMmSs("1,30").value)
    }

    @Test
    fun `rejects seconds greater than 59`() {
        val r = PaceMath.parseMmSs("1.75")
        assertFalse(r.ok)
        assertEquals("Les secondes doivent être inférieures à 60", r.error)
    }

    @Test
    fun `rejects empty and zero`() {
        assertEquals("Requis", PaceMath.parseMmSs("").error)
        assertEquals("Doit être supérieur à zéro", PaceMath.parseMmSs("0.00").error)
    }

    @Test
    fun `rejects malformed text`() {
        assertFalse(PaceMath.parseMmSs("abc").ok)
    }

    @Test
    fun `parses distance with comma or dot`() {
        assertEquals(1.5, PaceMath.parseDistance("1,5").value!!, 1e-9)
        assertEquals(1.5, PaceMath.parseDistance("1.5").value!!, 1e-9)
    }

    @Test
    fun `distance must be positive`() {
        assertFalse(PaceMath.parseDistance("0").ok)
        assertFalse(PaceMath.parseDistance("-1").ok)
    }

    @Test
    fun `formats seconds back to mm-ss`() {
        assertEquals("1.30", PaceMath.formatSeconds(90.0))
        assertEquals("10.00", PaceMath.formatSeconds(600.0))
        assertEquals("2.01", PaceMath.formatSeconds(121.0))
    }

    @Test
    fun `formats distance with french comma`() {
        assertEquals("1,50", PaceMath.formatDistance(1.5))
        assertEquals("6,90", PaceMath.formatDistance(6.8974358974))
    }

    @Test
    fun `formats range collapses when within epsilon`() {
        assertEquals("1,50", PaceMath.formatRange(1.5, 1.5001, { PaceMath.formatDistance(it) }, 0.005))
        assertEquals("1,50–2,00", PaceMath.formatRange(1.5, 2.0, { PaceMath.formatDistance(it) }, 0.005))
    }

    @Test
    fun `piece with duration measure derives distance bounds from pace range`() {
        // 10 min at a single 6.00 pace -> 10/6 = 1.6667 km both ends.
        val p = PaceMath.piece(PaceMode.SINGLE, "6.00", "", Measure.DURATION, "10.00")
        assertTrue(p.ok)
        assertEquals(600.0, p.tMin, 1e-9)
        assertEquals(600.0, p.tMax, 1e-9)
        assertEquals(600.0 / 360.0, p.dMin, 1e-9)
        assertEquals(600.0 / 360.0, p.dMax, 1e-9)
    }

    @Test
    fun `piece with distance measure keeps distance fixed and varies time by pace range`() {
        // 1 km at 4.30-4.45 -> distance fixed at 1, time spans 270..285 s.
        val p = PaceMath.piece(PaceMode.RANGE, "4.30", "4.45", Measure.DISTANCE, "1")
        assertTrue(p.ok)
        assertEquals(1.0, p.dMin, 1e-9)
        assertEquals(1.0, p.dMax, 1e-9)
        assertEquals(270.0, p.tMin, 1e-9)
        assertEquals(285.0, p.tMax, 1e-9)
    }

    @Test
    fun `metrics multiplies by repetitions and folds in recovery`() {
        val step = StepSpec(
            id = "s", paceMode = PaceMode.SINGLE, pace = "4.30", measure = Measure.DISTANCE, value = "1", reps = 4,
            recovery = true, recDur = "2.00", recPaceMode = PaceMode.SINGLE, recPace = "9.00",
        )
        val m = PaceMath.metrics(step)
        assertTrue(m.ok)
        // run: 1km @ 4.30 -> d=1, t=270; recovery: 2.00 @ 9.00 -> d = 120/540, t=120. Per rep total d = 1 + 120/540.
        val perRepD = 1.0 + 120.0 / 540.0
        val perRepT = 270.0 + 120.0
        assertEquals(perRepD * 4, m.dMid, 1e-6)
        assertEquals(perRepT * 4, m.tMid, 1e-6)
    }

    @Test
    fun `metrics fails when pace or length invalid`() {
        assertFalse(PaceMath.metrics(StepSpec(id = "s", pace = "", value = "1")).ok)
        assertFalse(PaceMath.metrics(StepSpec(id = "s", pace = "6.00", value = "")).ok)
    }

    @Test
    fun `autoName falls back to formatted length when unnamed`() {
        val distStep = StepSpec(id = "s", measure = Measure.DISTANCE, value = "5")
        assertEquals("5 km", PaceMath.autoName(distStep, PaceUnit.MIN_PER_KM))
        val durStep = StepSpec(id = "s", measure = Measure.DURATION, value = "10.00")
        assertEquals("10.00 min", PaceMath.autoName(durStep, PaceUnit.MIN_PER_KM))
        val named = StepSpec(id = "s", name = "Custom", measure = Measure.DURATION, value = "10.00")
        assertEquals("Custom", PaceMath.autoName(named, PaceUnit.MIN_PER_KM))
    }
}
