package com.demicourse.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SeanceJsonTest {
    private val step =
        StepSpec(id = "s1", name = "Côte", pace = "5.00", measure = Measure.DISTANCE, value = "1", reps = 3)

    @Test
    fun `steps round-trip`() {
        assertEquals(listOf(step), SeanceJson.decodeSteps(SeanceJson.encodeSteps(listOf(step))))
    }

    @Test
    fun `settings round-trip`() {
        val settings = AppSettings(theme = ThemeChoice.DARK, unit = PaceUnit.MIN_PER_MILE, halfBy = HalfBy.DURATION)
        assertEquals(settings, SeanceJson.decodeSettings(SeanceJson.encodeSettings(settings)))
    }

    @Test
    fun `enum names are stored as-is so existing data keeps loading`() {
        val json = SeanceJson.encodeSettings(AppSettings(unit = PaceUnit.MIN_PER_MILE, halfBy = HalfBy.DURATION))
        assertEquals(true, json.contains("\"MIN_PER_MILE\"") && json.contains("\"DURATION\""))
    }

    @Test
    fun `unknown fields are ignored and missing ones take their default`() {
        val raw = """[{"id":"s1","pace":"5.00","fieldFromTheFuture":42}]"""
        val decoded = SeanceJson.decodeSteps(raw).single()
        assertEquals("5.00", decoded.pace)
        assertEquals(1, decoded.reps)
        assertEquals(PaceMode.SINGLE, decoded.paceMode)
    }

    @Test
    fun `corrupted or absent data falls back to empty or default`() {
        assertEquals(emptyList<StepSpec>(), SeanceJson.decodeSteps("{not json"))
        assertEquals(emptyList<StepSpec>(), SeanceJson.decodeSteps(null))
        assertEquals(AppSettings(), SeanceJson.decodeSettings("[]"))
        assertEquals(AppSettings(), SeanceJson.decodeSettings(null))
    }
}
