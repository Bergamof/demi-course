package com.demicourse.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftLogicTest {
    private fun stepSheet(draft: Draft) = SheetState(type = SheetType.STEP, draft = draft)

    private fun templateSheet(draft: Draft) = SheetState(type = SheetType.TEMPLATE, draft = draft)

    private val validStep = Draft(pace = "5.00", measure = Measure.DISTANCE, value = "2")

    // ── validation ────────────────────────────────────────────────

    @Test
    fun `a complete step has no error`() {
        assertTrue(DraftValidation.validate(stepSheet(validStep)).isEmpty)
    }

    @Test
    fun `a step requires both pace and length`() {
        val errors = DraftValidation.validate(stepSheet(Draft()))
        assertNotNull(errors.pace)
        assertNotNull(errors.value)
    }

    @Test
    fun `a template needs at least the pace or the length`() {
        val empty = DraftValidation.validate(templateSheet(Draft(templateName = "Seuil")))
        assertEquals("Renseignez au moins l’allure ou la longueur", empty.pace)
        assertNull(empty.value)

        assertTrue(DraftValidation.validate(templateSheet(Draft(templateName = "Seuil", pace = "5.00"))).isEmpty)
        assertTrue(DraftValidation.validate(templateSheet(Draft(templateName = "Seuil", value = "10.00"))).isEmpty)
    }

    @Test
    fun `a template still checks the part it defines`() {
        val errors = DraftValidation.validate(templateSheet(Draft(templateName = "Seuil", pace = "5.99")))
        assertNotNull(errors.pace)
    }

    @Test
    fun `a template and a saved-as-template step need a name`() {
        assertEquals(
            "Donnez un nom au modèle",
            DraftValidation.validate(templateSheet(Draft(pace = "5.00"))).templateName,
        )
        val saved = validStep.copy(saveTemplate = true)
        assertEquals("Donnez un nom au modèle", DraftValidation.validate(stepSheet(saved)).templateName)
        assertNull(DraftValidation.validate(stepSheet(saved.copy(templateName = "Mon modèle"))).templateName)
    }

    @Test
    fun `a pace range also validates its upper bound`() {
        val range = validStep.copy(paceMode = PaceMode.RANGE, paceMax = "")
        assertNotNull(DraftValidation.validate(stepSheet(range)).paceMax)
        assertNull(DraftValidation.validate(stepSheet(range.copy(paceMax = "5.30"))).paceMax)
    }

    @Test
    fun `repetitions must be between 1 and the cap`() {
        listOf("0", "100", "-1", "abc", "").forEach {
            assertNotNull("$it should be rejected", DraftValidation.validate(stepSheet(validStep.copy(reps = it))).reps)
        }
        listOf("1", "99").forEach {
            assertNull("$it should be accepted", DraftValidation.validate(stepSheet(validStep.copy(reps = it))).reps)
        }
    }

    @Test
    fun `recovery fields are only checked when recovery is on`() {
        assertTrue(DraftValidation.validate(stepSheet(validStep.copy(recDur = "garbage"))).isEmpty)
        val on = validStep.copy(recovery = true, recDur = "", recPace = "9.00")
        assertNotNull(DraftValidation.validate(stepSheet(on)).recDur)
    }

    @Test
    fun `the settings sheet only validates the recovery pace`() {
        val settings =
            SheetState(type = SheetType.SETTINGS, draft = Draft(recPaceMode = PaceMode.RANGE, recPace = "9.00"))
        assertNotNull(DraftValidation.validate(settings).recPace)
        val fixed = settings.copy(draft = settings.draft.copy(recPaceMax = "11.00"))
        assertTrue(DraftValidation.validate(fixed).isEmpty)
    }

    // ── focus chain ───────────────────────────────────────────────

    @Test
    fun `field order of a basic step`() {
        assertEquals(listOf(FieldKey.VALUE, FieldKey.PACE, FieldKey.REPS), fieldOrder(stepSheet(Draft())))
    }

    @Test
    fun `field order grows with pace range, recovery and save-as-template`() {
        val full =
            Draft(
                paceMode = PaceMode.RANGE,
                recovery = true,
                recPaceMode = PaceMode.RANGE,
                saveTemplate = true,
            )
        assertEquals(
            listOf(
                FieldKey.VALUE,
                FieldKey.PACE,
                FieldKey.PACE_MAX,
                FieldKey.REPS,
                FieldKey.REC_DUR,
                FieldKey.REC_PACE,
                FieldKey.REC_PACE_MAX,
                FieldKey.TEMPLATE_NAME,
            ),
            fieldOrder(stepSheet(full)),
        )
    }

    @Test
    fun `field order of a template starts with its name and ignores save-as-template`() {
        val order = fieldOrder(templateSheet(Draft(saveTemplate = true)))
        assertEquals(listOf(FieldKey.TEMPLATE_NAME, FieldKey.VALUE, FieldKey.PACE, FieldKey.REPS), order)
    }

    @Test
    fun `field order of the settings sheet`() {
        val single = SheetState(type = SheetType.SETTINGS, draft = Draft())
        assertEquals(listOf(FieldKey.REC_PACE), fieldOrder(single))
        val range = single.copy(draft = Draft(recPaceMode = PaceMode.RANGE))
        assertEquals(listOf(FieldKey.REC_PACE, FieldKey.REC_PACE_MAX), fieldOrder(range))
    }

    // ── draft editing ─────────────────────────────────────────────

    @Test
    fun `applying a template keeps what it leaves undefined`() {
        val draft = Draft(pace = "5.00", value = "3", measure = Measure.DISTANCE, reps = "4")
        val onlyReps = StepSpec(id = "t", name = "Rappel", reps = 2)
        val next = draft.withTemplate(onlyReps)
        assertEquals("2", next.reps)
        assertEquals("Rappel", next.name)
        assertEquals("5.00", next.pace)
        assertEquals("3", next.value)
        assertEquals(false, next.recovery)
    }

    @Test
    fun `applying a template overwrites pace, length and recovery it defines`() {
        val template =
            StepSpec(
                id = "t",
                name = "Fractionné",
                paceMode = PaceMode.RANGE,
                pace = "4.30",
                paceMax = "4.45",
                measure = Measure.DURATION,
                value = "2.00",
                reps = 6,
                recovery = true,
                recDur = "1.00",
                recPace = "9.00",
            )
        val next = Draft(pace = "7.00", value = "1").withTemplate(template)
        assertEquals(PaceMode.RANGE, next.paceMode)
        assertEquals("4.30", next.pace)
        assertEquals("4.45", next.paceMax)
        assertEquals(Measure.DURATION, next.measure)
        assertEquals("2.00", next.value)
        assertEquals(true, next.recovery)
        assertEquals("1.00", next.recDur)
    }

    @Test
    fun `bumping repetitions stays within bounds and repairs garbage`() {
        assertEquals("2", Draft(reps = "1").withRepsBumped(1).reps)
        assertEquals("1", Draft(reps = "1").withRepsBumped(-1).reps)
        assertEquals("$MAX_REPS", Draft(reps = "$MAX_REPS").withRepsBumped(1).reps)
        assertEquals("2", Draft(reps = "abc").withRepsBumped(1).reps)
    }

    @Test
    fun `a draft round-trips through a step`() {
        val step =
            StepSpec(
                id = "s",
                name = "Côte",
                paceMode = PaceMode.RANGE,
                pace = "4.30",
                paceMax = "4.45",
                measure = Measure.DISTANCE,
                value = "1",
                reps = 3,
                recovery = true,
                recDur = "2.00",
                recPaceMode = PaceMode.RANGE,
                recPace = "9.00",
                recPaceMax = "11.00",
            )
        assertEquals(step.copy(id = "x"), step.toDraft().toStepSpec(id = "x", name = "Côte"))
    }

    @Test
    fun `converting a draft drops the fields its toggles turned off`() {
        val spec =
            Draft(paceMax = "5.30", recDur = "2.00", recPace = "9.00", recPaceMax = "11.00", reps = "500")
                .toStepSpec()
        assertEquals("", spec.paceMax)
        assertEquals("", spec.recDur)
        assertEquals("", spec.recPace)
        assertEquals("", spec.recPaceMax)
        assertEquals(MAX_REPS, spec.reps)
    }

    @Test
    fun `a blank draft starts from the default recovery pace`() {
        val draft = blankDraft(AppSettings(recovery = RecoveryDefaults(PaceMode.RANGE, "8.00", "10.00")))
        assertEquals(PaceMode.RANGE, draft.recPaceMode)
        assertEquals("8.00", draft.recPace)
        assertEquals("10.00", draft.recPaceMax)
    }
}
