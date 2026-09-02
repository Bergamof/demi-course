package com.demicourse.seance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demicourse.domain.AppSettings
import com.demicourse.domain.Measure
import com.demicourse.domain.PaceMath
import com.demicourse.domain.PaceMode
import com.demicourse.domain.RecoveryDefaults
import com.demicourse.domain.StepSpec
import com.demicourse.domain.ThemeChoice
import com.demicourse.seance.data.SeanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SeanceViewModel(private val repository: SeanceRepository) : ViewModel() {

    private val _state = MutableStateFlow(SeanceUiState())
    val state: StateFlow<SeanceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.data.collect { data ->
                _state.value = _state.value.copy(
                    steps = data.steps, templates = data.templates, settings = data.settings, loaded = true,
                )
            }
        }
    }

    private fun update(transform: (SeanceUiState) -> SeanceUiState) {
        _state.value = transform(_state.value)
    }

    private fun persistSteps(steps: List<StepSpec>) {
        update { it.copy(steps = steps) }
        viewModelScope.launch { repository.saveSteps(steps) }
    }

    private fun persistTemplates(templates: List<StepSpec>) {
        update { it.copy(templates = templates) }
        viewModelScope.launch { repository.saveTemplates(templates) }
    }

    private fun persistSettings(settings: AppSettings) {
        update { it.copy(settings = settings) }
        viewModelScope.launch { repository.saveSettings(settings) }
    }

    // ── sheet lifecycle ───────────────────────────────────────────

    private fun blankDraft(settings: AppSettings): Draft = Draft(
        recPaceMode = settings.recovery.recPaceMode,
        recPace = settings.recovery.recPace,
        recPaceMax = settings.recovery.recPaceMax,
    )

    fun openStepSheet() {
        val settings = _state.value.settings
        update { it.copy(sheet = SheetState(type = SheetType.STEP, draft = blankDraft(settings))) }
    }

    fun editStep(step: StepSpec) {
        val settings = _state.value.settings
        var draft = fromStep(step).copy(saveTemplate = false, templateName = "")
        if (draft.recPace.isBlank()) {
            draft = draft.copy(recPaceMode = settings.recovery.recPaceMode, recPace = settings.recovery.recPace, recPaceMax = settings.recovery.recPaceMax)
        }
        update { it.copy(sheet = SheetState(type = SheetType.STEP, draft = draft, editingStepId = step.id)) }
    }

    fun deleteStep(id: String) {
        persistSteps(_state.value.steps.filterNot { it.id == id })
    }

    fun openTemplateSheet(existing: StepSpec? = null) {
        val current = _state.value.sheet
        val returnTo = if (current?.type == SheetType.SETTINGS) TemplateReturn.SETTINGS else TemplateReturn.STEP
        val draft = if (existing != null) {
            fromStep(existing).copy(templateName = existing.name, saveTemplate = false)
        } else {
            Draft(measure = Measure.DURATION)
        }
        update {
            it.copy(sheet = SheetState(
                type = SheetType.TEMPLATE, draft = draft,
                editingTemplateId = existing?.id, templateReturn = returnTo,
            ))
        }
    }

    fun deleteTemplate(id: String) {
        persistTemplates(_state.value.templates.filterNot { it.id == id })
    }

    fun openSettingsSheet() {
        update { it.copy(sheet = SheetState(type = SheetType.SETTINGS, draft = blankDraft(it.settings))) }
    }

    fun closeSheet() {
        update { it.copy(sheet = null) }
    }

    fun resetSession() {
        persistSteps(emptyList())
    }

    // ── draft field editing ───────────────────────────────────────

    private fun updateDraft(transform: (Draft) -> Draft) {
        val sheet = _state.value.sheet ?: return
        update { it.copy(sheet = sheet.copy(draft = transform(sheet.draft))) }
    }

    /** Same as [updateDraft], but also clears the error for the one field being fixed. */
    private fun updateDraftClearingError(field: FieldKey, transform: (Draft) -> Draft) {
        val sheet = _state.value.sheet ?: return
        val errors = when (field) {
            FieldKey.TEMPLATE_NAME -> sheet.errors.copy(templateName = null)
            FieldKey.VALUE -> sheet.errors.copy(value = null)
            FieldKey.PACE -> sheet.errors.copy(pace = null)
            FieldKey.PACE_MAX -> sheet.errors.copy(paceMax = null)
            FieldKey.REPS -> sheet.errors.copy(reps = null)
            FieldKey.REC_DUR -> sheet.errors.copy(recDur = null)
            FieldKey.REC_PACE -> sheet.errors.copy(recPace = null)
            // paceMax/recPaceMax errors are folded into the pace/recPace range error, so editing
            // the second field of a range doesn't clear it — matches the prototype exactly.
            FieldKey.REC_PACE_MAX -> sheet.errors
        }
        update { it.copy(sheet = sheet.copy(draft = transform(sheet.draft), errors = errors)) }
    }

    fun setField(field: FieldKey, value: String) {
        updateDraftClearingError(field) { d ->
            when (field) {
                FieldKey.TEMPLATE_NAME -> d.copy(templateName = value)
                FieldKey.VALUE -> d.copy(value = value)
                FieldKey.PACE -> d.copy(pace = value)
                FieldKey.PACE_MAX -> d.copy(paceMax = value)
                FieldKey.REPS -> d.copy(reps = value)
                FieldKey.REC_DUR -> d.copy(recDur = value)
                FieldKey.REC_PACE -> d.copy(recPace = value)
                FieldKey.REC_PACE_MAX -> d.copy(recPaceMax = value)
            }
        }
    }

    fun setPaceSingle() = updateDraft { it.copy(paceMode = PaceMode.SINGLE) }
    fun setPaceRange() = updateDraft { it.copy(paceMode = PaceMode.RANGE) }
    fun setMeasureDistance() = updateDraft { it.copy(measure = Measure.DISTANCE, value = "") }
    fun setMeasureDuration() = updateDraft { it.copy(measure = Measure.DURATION, value = "") }
    fun setRecSingle() = updateDraft { it.copy(recPaceMode = PaceMode.SINGLE) }
    fun setRecRange() = updateDraft { it.copy(recPaceMode = PaceMode.RANGE) }
    fun toggleRecovery() = updateDraft { it.copy(recovery = !it.recovery) }
    fun toggleSaveTemplate() = updateDraft { it.copy(saveTemplate = !it.saveTemplate) }

    fun bumpReps(delta: Int) = updateDraftClearingError(FieldKey.REPS) { d ->
        val cur = (d.reps.toIntOrNull() ?: 1).coerceAtLeast(1)
        d.copy(reps = (cur + delta).coerceIn(1, 99).toString())
    }

    fun applyTemplate(template: StepSpec) = updateDraft { d ->
        var next = d.copy(reps = template.reps.toString(), name = template.name)
        if (template.pace.isNotBlank()) {
            next = next.copy(paceMode = template.paceMode, pace = template.pace, paceMax = template.paceMax)
        }
        if (template.value.isNotBlank()) {
            next = next.copy(measure = template.measure, value = template.value)
        }
        if (template.recovery) {
            next = next.copy(
                recovery = true, recDur = template.recDur,
                recPaceMode = template.recPaceMode, recPace = template.recPace, recPaceMax = template.recPaceMax,
            )
        }
        next
    }

    fun setTheme(choice: ThemeChoice) = persistSettings(_state.value.settings.copy(theme = choice))

    // ── validation & submit ───────────────────────────────────────

    fun validate(sheet: SheetState): DraftErrors {
        val d = sheet.draft
        if (sheet.type == SheetType.SETTINGS) {
            var recPaceErr = PaceMath.parseMmSs(d.recPace).error
            if (d.recPaceMode == PaceMode.RANGE) {
                recPaceErr = recPaceErr ?: PaceMath.parseMmSs(d.recPaceMax).error
            }
            return DraftErrors(recPace = recPaceErr)
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
                valueErr = if (d.measure == Measure.DISTANCE) PaceMath.parseDistance(d.value).error else PaceMath.parseMmSs(d.value).error
            }
        }

        val repsErr = if ((d.reps.toIntOrNull() ?: 0) >= 1) null else "Répétitions invalides"

        var recDurErr: String? = null
        var recPaceErr: String? = null
        if (d.recovery) {
            recDurErr = PaceMath.parseMmSs(d.recDur).error
            recPaceErr = PaceMath.parseMmSs(d.recPace).error
            if (d.recPaceMode == PaceMode.RANGE) {
                recPaceErr = recPaceErr ?: PaceMath.parseMmSs(d.recPaceMax).error
            }
        }

        val templateNameErr = if ((isTemplate || d.saveTemplate) && d.templateName.isBlank()) "Donnez un nom au modèle" else null

        return DraftErrors(
            pace = paceErr, paceMax = paceMaxErr, value = valueErr, reps = repsErr,
            recDur = recDurErr, recPace = recPaceErr, templateName = templateNameErr,
        )
    }

    /** Validates the current sheet's draft, commits it if valid, and returns whether it committed. */
    fun submit(): Boolean {
        val sheet = _state.value.sheet ?: return false
        val errors = validate(sheet)
        if (!errors.isEmpty) {
            update { it.copy(sheet = sheet.copy(errors = errors)) }
            return false
        }
        val d = sheet.draft

        if (sheet.type == SheetType.SETTINGS) {
            val recovery = RecoveryDefaults(
                recPaceMode = d.recPaceMode, recPace = d.recPace,
                recPaceMax = if (d.recPaceMode == PaceMode.RANGE) d.recPaceMax else "",
            )
            persistSettings(_state.value.settings.copy(recovery = recovery))
            update { it.copy(sheet = null) }
            return true
        }

        val base = StepSpec(
            id = "", // filled in below per-context
            paceMode = d.paceMode, pace = d.pace, paceMax = if (d.paceMode == PaceMode.RANGE) d.paceMax else "",
            measure = d.measure, value = d.value, reps = (d.reps.toIntOrNull() ?: 1).coerceAtLeast(1),
            recovery = d.recovery, recDur = if (d.recovery) d.recDur else "",
            recPaceMode = d.recPaceMode, recPace = if (d.recovery) d.recPace else "",
            recPaceMax = if (d.recPaceMode == PaceMode.RANGE) d.recPaceMax else "",
        )

        if (sheet.type == SheetType.TEMPLATE) {
            val name = d.templateName.trim()
            val id = sheet.editingTemplateId ?: newId("tpl")
            val template = base.copy(id = id, name = name)
            val templates = if (sheet.editingTemplateId != null) {
                _state.value.templates.map { if (it.id == id) template else it }
            } else {
                _state.value.templates + template
            }
            persistTemplates(templates)
            if (sheet.templateReturn == TemplateReturn.SETTINGS) {
                update { it.copy(sheet = SheetState(type = SheetType.SETTINGS, draft = blankDraft(_state.value.settings))) }
            } else {
                update { it.copy(sheet = SheetState(type = SheetType.STEP, draft = d.copy(saveTemplate = false, templateName = "", name = name))) }
            }
            return true
        }

        var templates = _state.value.templates
        if (d.saveTemplate) {
            templates = templates + base.copy(id = newId("tpl"), name = d.templateName.trim())
        }
        val id = sheet.editingStepId ?: newId("s")
        val step = base.copy(id = id, name = d.name)
        val steps = if (sheet.editingStepId != null) {
            _state.value.steps.map { if (it.id == id) step else it }
        } else {
            _state.value.steps + step
        }
        persistTemplates(templates)
        persistSteps(steps)
        update { it.copy(sheet = null) }
        return true
    }

    companion object {
        private var counter = 0L
        private fun newId(prefix: String): String {
            counter += 1
            return "$prefix${System.currentTimeMillis()}$counter"
        }
    }
}

private fun fromStep(step: StepSpec): Draft = Draft(
    name = step.name, paceMode = step.paceMode, pace = step.pace, paceMax = step.paceMax,
    measure = step.measure, value = step.value, reps = step.reps.toString(),
    recovery = step.recovery, recDur = step.recDur,
    recPaceMode = step.recPaceMode, recPace = step.recPace, recPaceMax = step.recPaceMax,
)
