package com.demicourse.seance.ui

import com.demicourse.domain.AppSettings
import com.demicourse.domain.Measure
import com.demicourse.domain.PaceMode
import com.demicourse.domain.StepSpec

enum class SheetType { STEP, TEMPLATE, SETTINGS }

/** Where a template sheet opened mid-flow should return to once saved. */
enum class TemplateReturn { STEP, SETTINGS }

/**
 * The bottom sheet's editable draft. Numeric fields are kept as raw strings — exactly like the
 * prototype's text inputs — so a partial or invalid entry can still round-trip while the user
 * is typing; [SeanceViewModel.validate] is what turns them into errors or a committed [StepSpec].
 */
data class Draft(
    val name: String = "",
    val paceMode: PaceMode = PaceMode.SINGLE,
    val pace: String = "",
    val paceMax: String = "",
    val measure: Measure = Measure.DURATION,
    val value: String = "",
    val reps: String = "1",
    val recovery: Boolean = false,
    val recDur: String = "",
    val recPaceMode: PaceMode = PaceMode.SINGLE,
    val recPace: String = "",
    val recPaceMax: String = "",
    val saveTemplate: Boolean = false,
    val templateName: String = "",
)

data class DraftErrors(
    val pace: String? = null,
    val paceMax: String? = null,
    val value: String? = null,
    val reps: String? = null,
    val recDur: String? = null,
    val recPace: String? = null,
    val templateName: String? = null,
) {
    val isEmpty: Boolean get() = pace == null && paceMax == null && value == null && reps == null &&
        recDur == null && recPace == null && templateName == null
}

data class SheetState(
    val type: SheetType,
    val draft: Draft,
    val errors: DraftErrors = DraftErrors(),
    val editingStepId: String? = null,
    val editingTemplateId: String? = null,
    val templateReturn: TemplateReturn = TemplateReturn.STEP,
)

data class SeanceUiState(
    val steps: List<StepSpec> = emptyList(),
    val templates: List<StepSpec> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val sheet: SheetState? = null,
    val loaded: Boolean = false,
)

/** Field identity used both for validation/commit and for the Tab/Enter focus chain. */
enum class FieldKey { TEMPLATE_NAME, VALUE, PACE, PACE_MAX, REPS, REC_DUR, REC_PACE, REC_PACE_MAX }

/**
 * The order fields should receive focus in as the user advances with Tab/Enter — ports the
 * prototype's `fieldOrder()` exactly, including which fields only appear conditionally
 * (a pace range's second field, recovery's fields, "save as template"'s name field).
 */
fun fieldOrder(sheet: SheetState): List<FieldKey> {
    val d = sheet.draft
    if (sheet.type == SheetType.SETTINGS) {
        return buildList {
            add(FieldKey.REC_PACE)
            if (d.recPaceMode == PaceMode.RANGE) add(FieldKey.REC_PACE_MAX)
        }
    }
    return buildList {
        if (sheet.type == SheetType.TEMPLATE) add(FieldKey.TEMPLATE_NAME)
        add(FieldKey.VALUE)
        add(FieldKey.PACE)
        if (d.paceMode == PaceMode.RANGE) add(FieldKey.PACE_MAX)
        add(FieldKey.REPS)
        if (d.recovery) {
            add(FieldKey.REC_DUR)
            add(FieldKey.REC_PACE)
            if (d.recPaceMode == PaceMode.RANGE) add(FieldKey.REC_PACE_MAX)
        }
        if (sheet.type == SheetType.STEP && d.saveTemplate) add(FieldKey.TEMPLATE_NAME)
    }
}
