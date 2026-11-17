package com.demicourse.seance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demicourse.domain.AppSettings
import com.demicourse.domain.Draft
import com.demicourse.domain.DraftValidation
import com.demicourse.domain.FieldKey
import com.demicourse.domain.HalfBy
import com.demicourse.domain.MAX_REPS
import com.demicourse.domain.Measure
import com.demicourse.domain.PaceMath
import com.demicourse.domain.PaceMode
import com.demicourse.domain.PaceUnit
import com.demicourse.domain.RecoveryDefaults
import com.demicourse.domain.SheetState
import com.demicourse.domain.SheetType
import com.demicourse.domain.StepSpec
import com.demicourse.domain.TemplateReturn
import com.demicourse.domain.ThemeChoice
import com.demicourse.domain.blankDraft
import com.demicourse.domain.toDraft
import com.demicourse.domain.toStepSpec
import com.demicourse.domain.withRecoveryDefaults
import com.demicourse.domain.withRepsBumped
import com.demicourse.domain.withTemplate
import com.demicourse.seance.data.SeanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class SeanceViewModel(
    private val repository: SeanceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SeanceUiState())
    val state: StateFlow<SeanceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Read once: this view model is the only writer, so the in-memory state is the source of
            // truth and the DataStore a mirror. Collecting it would replay stale blobs over newer state.
            val data = repository.data.first()
            _state.value =
                _state.value.copy(
                    steps = data.steps,
                    templates = data.templates,
                    settings = data.settings,
                    loaded = true,
                )
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

    fun openStepSheet() {
        val settings = _state.value.settings
        update { it.copy(sheet = SheetState(type = SheetType.STEP, draft = blankDraft(settings))) }
    }

    fun editStep(step: StepSpec) {
        val settings = _state.value.settings
        var draft = step.toDraft().copy(saveTemplate = false, templateName = "")
        if (draft.recPace.isBlank()) draft = draft.withRecoveryDefaults(settings.recovery)
        update { it.copy(sheet = SheetState(type = SheetType.STEP, draft = draft, editingStepId = step.id)) }
    }

    fun deleteStep(id: String) {
        val steps = _state.value.steps
        val index = steps.indexOfFirst { it.id == id }
        if (index < 0) return
        persistSteps(steps.filterNot { it.id == id })
        update { it.copy(lastDeleted = DeletedStep(steps[index], index)) }
    }

    /** Puts the last deleted step back where it was. */
    fun undoDelete() {
        val deleted = _state.value.lastDeleted ?: return
        val steps = _state.value.steps
        persistSteps(steps.toMutableList().apply { add(deleted.index.coerceAtMost(size), deleted.step) })
        update { it.copy(lastDeleted = null) }
    }

    fun dismissUndo() {
        update { it.copy(lastDeleted = null) }
    }

    fun openTemplateSheet(existing: StepSpec? = null) {
        val current = _state.value.sheet
        val returnTo = if (current?.type == SheetType.SETTINGS) TemplateReturn.SETTINGS else TemplateReturn.STEP
        val draft =
            if (existing != null) {
                existing.toDraft().copy(templateName = existing.name, saveTemplate = false)
            } else {
                Draft(measure = Measure.DURATION)
            }
        update {
            it.copy(
                sheet =
                    SheetState(
                        type = SheetType.TEMPLATE,
                        draft = draft,
                        editingTemplateId = existing?.id,
                        templateReturn = returnTo,
                    ),
            )
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
    private fun updateDraftClearingError(
        field: FieldKey,
        transform: (Draft) -> Draft,
    ) {
        val sheet = _state.value.sheet ?: return
        val errors =
            when (field) {
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

    fun setField(
        field: FieldKey,
        value: String,
    ) {
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

    fun bumpReps(delta: Int) = updateDraftClearingError(FieldKey.REPS) { it.withRepsBumped(delta) }

    fun applyTemplate(template: StepSpec) = updateDraft { it.withTemplate(template) }

    fun setTheme(choice: ThemeChoice) = persistSettings(_state.value.settings.copy(theme = choice))

    fun setHalfBy(halfBy: HalfBy) = persistSettings(_state.value.settings.copy(halfBy = halfBy))

    fun setUnit(unit: PaceUnit) = persistSettings(_state.value.settings.copy(unit = unit))

    // ── validation & submit ───────────────────────────────────────

    /** Validates the current sheet's draft, commits it if valid, and returns whether it committed. */
    fun submit(): Boolean {
        val sheet = _state.value.sheet ?: return false
        val errors = DraftValidation.validate(sheet)
        if (!errors.isEmpty) {
            update { it.copy(sheet = sheet.copy(errors = errors)) }
            return false
        }
        val d = sheet.draft

        if (sheet.type == SheetType.SETTINGS) {
            val recovery =
                RecoveryDefaults(
                    recPaceMode = d.recPaceMode,
                    recPace = d.recPace,
                    recPaceMax = if (d.recPaceMode == PaceMode.RANGE) d.recPaceMax else "",
                )
            persistSettings(_state.value.settings.copy(recovery = recovery))
            update { it.copy(sheet = null) }
            return true
        }

        val base = d.toStepSpec()

        if (sheet.type == SheetType.TEMPLATE) {
            val name = d.templateName.trim()
            val id = sheet.editingTemplateId ?: newId("tpl")
            val template = base.copy(id = id, name = name)
            val templates =
                if (sheet.editingTemplateId != null) {
                    _state.value.templates.map { if (it.id == id) template else it }
                } else {
                    _state.value.templates + template
                }
            persistTemplates(templates)
            if (sheet.templateReturn == TemplateReturn.SETTINGS) {
                update {
                    it.copy(
                        sheet = SheetState(type = SheetType.SETTINGS, draft = blankDraft(_state.value.settings)),
                    )
                }
            } else {
                update {
                    it.copy(
                        sheet =
                            SheetState(
                                type = SheetType.STEP,
                                draft = d.copy(saveTemplate = false, templateName = "", name = name),
                            ),
                    )
                }
            }
            return true
        }

        var templates = _state.value.templates
        if (d.saveTemplate) {
            templates = templates + base.copy(id = newId("tpl"), name = d.templateName.trim())
        }
        val id = sheet.editingStepId ?: newId("s")
        val step = base.copy(id = id, name = d.name)
        val steps =
            if (sheet.editingStepId != null) {
                _state.value.steps.map { if (it.id == id) step else it }
            } else {
                _state.value.steps + step
            }
        if (templates !== _state.value.templates) persistTemplates(templates)
        persistSteps(steps)
        update { it.copy(sheet = null) }
        return true
    }
}

private fun newId(prefix: String): String = prefix + UUID.randomUUID()
