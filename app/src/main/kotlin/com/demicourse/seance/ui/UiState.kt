package com.demicourse.seance.ui

import com.demicourse.domain.AppSettings
import com.demicourse.domain.SheetState
import com.demicourse.domain.StepSpec

/** A step just removed, kept long enough to offer « Annuler ». */
data class DeletedStep(
    val step: StepSpec,
    val index: Int,
)

data class SeanceUiState(
    val steps: List<StepSpec> = emptyList(),
    val templates: List<StepSpec> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val sheet: SheetState? = null,
    val loaded: Boolean = false,
    val lastDeleted: DeletedStep? = null,
)
