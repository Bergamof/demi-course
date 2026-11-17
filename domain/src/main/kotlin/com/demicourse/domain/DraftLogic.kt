package com.demicourse.domain

/** The step or template an existing [StepSpec] turns into when reopened in the editor. */
fun StepSpec.toDraft(): Draft =
    Draft(
        name = name,
        paceMode = paceMode,
        pace = pace,
        paceMax = paceMax,
        measure = measure,
        value = value,
        reps = reps.toString(),
        recovery = recovery,
        recDur = recDur,
        recPaceMode = recPaceMode,
        recPace = recPace,
        recPaceMax = recPaceMax,
    )

/** A fresh draft whose recovery fields start from the user's default recovery pace. */
fun blankDraft(settings: AppSettings): Draft = Draft().withRecoveryDefaults(settings.recovery)

/** The draft with its recovery pace replaced by [defaults] (used for new drafts and steps saved without one). */
fun Draft.withRecoveryDefaults(defaults: RecoveryDefaults): Draft =
    copy(recPaceMode = defaults.recPaceMode, recPace = defaults.recPace, recPaceMax = defaults.recPaceMax)

/** The draft with its repetitions moved by [delta], kept within 1..[MAX_REPS]. */
fun Draft.withRepsBumped(delta: Int): Draft {
    val current = (reps.toIntOrNull() ?: 1).coerceAtLeast(1)
    return copy(reps = (current + delta).coerceIn(1, MAX_REPS).toString())
}

/**
 * The draft after picking [template]: repetitions and name are always copied, while pace, length and
 * recovery only overwrite the draft when the template actually defines them.
 */
fun Draft.withTemplate(template: StepSpec): Draft {
    var next = copy(reps = template.reps.toString(), name = template.name)
    if (template.pace.isNotBlank()) {
        next = next.copy(paceMode = template.paceMode, pace = template.pace, paceMax = template.paceMax)
    }
    if (template.value.isNotBlank()) {
        next = next.copy(measure = template.measure, value = template.value)
    }
    if (template.recovery) {
        next =
            next.copy(
                recovery = true,
                recDur = template.recDur,
                recPaceMode = template.recPaceMode,
                recPace = template.recPace,
                recPaceMax = template.recPaceMax,
            )
    }
    return next
}

/** Turns a sheet's draft into per-field error messages (all null when it can be committed). */
object DraftValidation {
    fun validate(sheet: SheetState): DraftErrors {
        val d = sheet.draft
        if (sheet.type == SheetType.SETTINGS) {
            return DraftErrors(recPace = paceError(d.recPaceMode, d.recPace, d.recPaceMax))
        }

        val isTemplate = sheet.type == SheetType.TEMPLATE
        val hasPace = d.pace.isNotBlank()
        val hasValue = d.value.isNotBlank()

        var paceErr: String? = null
        var paceMaxErr: String? = null
        var valueErr: String? = null

        if (isTemplate && !hasPace && !hasValue) {
            paceErr = "Renseignez au moins l’allure ou la longueur"
        } else {
            if (hasPace || !isTemplate) {
                paceErr = PaceMath.parseMmSs(d.pace).error
                if (d.paceMode == PaceMode.RANGE) paceMaxErr = PaceMath.parseMmSs(d.paceMax).error
            }
            if (hasValue || !isTemplate) {
                valueErr =
                    when (d.measure) {
                        Measure.DISTANCE -> PaceMath.parseDistance(d.value).error
                        Measure.DURATION -> PaceMath.parseMmSs(d.value).error
                    }
            }
        }

        val repsErr = if ((d.reps.toIntOrNull() ?: 0) in 1..MAX_REPS) null else "Répétitions : de 1 à $MAX_REPS"

        var recDurErr: String? = null
        var recPaceErr: String? = null
        if (d.recovery) {
            recDurErr = PaceMath.parseMmSs(d.recDur).error
            recPaceErr = paceError(d.recPaceMode, d.recPace, d.recPaceMax)
        }

        val needsTemplateName = isTemplate || d.saveTemplate
        val templateNameErr = if (needsTemplateName && d.templateName.isBlank()) "Donnez un nom au modèle" else null

        return DraftErrors(
            pace = paceErr,
            paceMax = paceMaxErr,
            value = valueErr,
            reps = repsErr,
            recDur = recDurErr,
            recPace = recPaceErr,
            templateName = templateNameErr,
        )
    }

    private fun paceError(
        mode: PaceMode,
        pace: String,
        paceMax: String,
    ): String? =
        PaceMath.parseMmSs(pace).error ?: if (mode == PaceMode.RANGE) PaceMath.parseMmSs(paceMax).error else null
}
