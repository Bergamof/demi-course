package com.demicourse.seance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demicourse.domain.PaceUnit
import com.demicourse.domain.StepSpec
import com.demicourse.domain.ThemeChoice
import com.demicourse.seance.ui.FieldKey
import com.demicourse.seance.ui.SeanceViewModel
import com.demicourse.seance.ui.SheetState
import com.demicourse.seance.ui.SheetType
import com.demicourse.seance.ui.theme.LocalSeanceColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetHost(
    sheet: SheetState,
    templates: List<StepSpec>,
    unit: PaceUnit,
    themeChoice: ThemeChoice,
    hintsOn: Boolean,
    viewModel: SeanceViewModel,
) {
    val colors = LocalSeanceColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { viewModel.closeSheet() }
    }

    val openKey = Triple(sheet.type, sheet.editingStepId, sheet.editingTemplateId)
    val controller = rememberSheetFocusController(openKey)

    LaunchedEffect(openKey) {
        delay(80)
        controller.focusFirst(sheet)
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.closeSheet() },
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.fg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .width(34.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.line2),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
                .onPreviewKeyEvent { event ->
                    controller.handleKeyEvent(event, sheet, onSubmit = { viewModel.submit() }, onClose = ::close)
                },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(title = sheetTitle(sheet), onClose = ::close)

            if (sheet.type == SheetType.STEP && sheet.editingStepId == null) {
                TemplatePicker(templates = templates, unit = unit, onPick = viewModel::applyTemplate, onNew = { viewModel.openTemplateSheet() })
            }

            if (sheet.type == SheetType.TEMPLATE) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Nom du modèle")
                    FieldBox {
                        SeanceTextField(
                            controller = controller, field = FieldKey.TEMPLATE_NAME, sheet = sheet, value = sheet.draft.templateName,
                            onValueChange = { viewModel.setField(FieldKey.TEMPLATE_NAME, it) }, onSubmit = { viewModel.submit() },
                            placeholder = "Fractionné 400 m", boxed = false, monospace = false, fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    FieldError(sheet.errors.templateName)
                }
            }

            if (sheet.type == SheetType.STEP || sheet.type == SheetType.TEMPLATE) {
                EditorBody(
                    controller = controller, sheet = sheet, unit = unit,
                    onField = viewModel::setField,
                    onSetMeasureDistance = viewModel::setMeasureDistance,
                    onSetMeasureDuration = viewModel::setMeasureDuration,
                    onSetPaceSingle = viewModel::setPaceSingle,
                    onSetPaceRange = viewModel::setPaceRange,
                    onSetRecSingle = viewModel::setRecSingle,
                    onSetRecRange = viewModel::setRecRange,
                    onToggleRecovery = viewModel::toggleRecovery,
                    onRepsUp = { viewModel.bumpReps(1) },
                    onRepsDown = { viewModel.bumpReps(-1) },
                    onSubmit = { viewModel.submit() },
                )
            }

            if (sheet.type == SheetType.SETTINGS) {
                SettingsBody(
                    controller = controller, sheet = sheet, unit = unit, themeChoice = themeChoice, templates = templates,
                    onSetTheme = viewModel::setTheme,
                    onField = viewModel::setField,
                    onSetRecSingle = viewModel::setRecSingle,
                    onSetRecRange = viewModel::setRecRange,
                    onEditTemplate = { viewModel.openTemplateSheet(it) },
                    onDeleteTemplate = viewModel::deleteTemplate,
                    onNewTemplate = { viewModel.openTemplateSheet() },
                    onSubmit = { viewModel.submit() },
                )
            }

            if (sheet.type == SheetType.STEP) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CheckRow(checked = sheet.draft.saveTemplate, label = "Enregistrer comme modèle", onToggle = viewModel::toggleSaveTemplate)
                    if (sheet.draft.saveTemplate) {
                        FieldBox(height = 46.dp) {
                            SeanceTextField(
                                controller = controller, field = FieldKey.TEMPLATE_NAME, sheet = sheet, value = sheet.draft.templateName,
                                onValueChange = { viewModel.setField(FieldKey.TEMPLATE_NAME, it) }, onSubmit = { viewModel.submit() },
                                placeholder = "Nom du modèle", boxed = false, monospace = false, fontSize = 15.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            FooterButtons(
                cancelLabel = "Annuler", submitLabel = submitLabel(sheet),
                onCancel = ::close, onSubmit = { viewModel.submit() },
            )

            HintsRow(visible = hintsOn)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplatePicker(templates: List<StepSpec>, unit: PaceUnit, onPick: (StepSpec) -> Unit, onNew: () -> Unit) {
    val colors = LocalSeanceColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Modèle")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            templates.forEach { tpl ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface2)
                        .clickable { onPick(tpl) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(tpl.name, color = colors.fg2, fontSize = 13.sp)
                    Text(
                        com.demicourse.seance.ui.Formatting.templateHint(tpl, unit),
                        color = colors.muted2, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .dashedBorder(colors.dash, 10.dp)
                    .clickable(onClick = onNew)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("+ Nouveau modèle", color = colors.muted, fontSize = 13.sp)
            }
        }
    }
}

private fun sheetTitle(sheet: SheetState): String = when (sheet.type) {
    SheetType.SETTINGS -> "Paramètres"
    SheetType.TEMPLATE -> if (sheet.editingTemplateId != null) "Modifier le modèle" else "Nouveau modèle"
    SheetType.STEP -> if (sheet.editingStepId != null) "Modifier l’étape" else "Nouvelle étape"
}

private fun submitLabel(sheet: SheetState): String = when (sheet.type) {
    SheetType.SETTINGS -> "Enregistrer"
    SheetType.TEMPLATE -> if (sheet.editingTemplateId != null) "Enregistrer" else "Créer le modèle"
    SheetType.STEP -> if (sheet.editingStepId != null) "Enregistrer" else "Ajouter"
}
