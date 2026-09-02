package com.demicourse.seance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demicourse.domain.Measure
import com.demicourse.domain.PaceMode
import com.demicourse.domain.PaceUnit
import com.demicourse.seance.ui.FieldKey
import com.demicourse.seance.ui.Formatting
import com.demicourse.seance.ui.SheetState
import com.demicourse.seance.ui.SheetType
import com.demicourse.seance.ui.theme.LocalSeanceColors

/** The shared step/template editor body: length, target pace, repetitions, recovery, and the preview hint. */
@Composable
fun EditorBody(
    controller: SheetFocusController,
    sheet: SheetState,
    unit: PaceUnit,
    onField: (FieldKey, String) -> Unit,
    onSetMeasureDistance: () -> Unit,
    onSetMeasureDuration: () -> Unit,
    onSetPaceSingle: () -> Unit,
    onSetPaceRange: () -> Unit,
    onSetRecSingle: () -> Unit,
    onSetRecRange: () -> Unit,
    onToggleRecovery: () -> Unit,
    onRepsUp: () -> Unit,
    onRepsDown: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalSeanceColors.current
    val d = sheet.draft
    val errors = sheet.errors
    val isTemplate = sheet.type == SheetType.TEMPLATE
    val optionalNote = if (isTemplate) "· facultatif" else ""
    val uD = com.demicourse.domain.PaceMath.unitDistanceLabel(unit)
    val uP = com.demicourse.domain.PaceMath.unitPaceLabel(unit)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Longueur
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Longueur", optionalNote)
                PillToggle(
                    options = listOf("Distance ($uD)", "Durée"),
                    selectedIndex = if (d.measure == Measure.DISTANCE) 0 else 1,
                    onSelect = { if (it == 0) onSetMeasureDistance() else onSetMeasureDuration() },
                )
            }
            FieldBox {
                SeanceTextField(
                    controller = controller, field = FieldKey.VALUE, sheet = sheet, value = d.value,
                    onValueChange = { onField(FieldKey.VALUE, it) }, onSubmit = onSubmit,
                    placeholder = if (d.measure == Measure.DISTANCE) "1,5" else "10.00",
                    boxed = false, fontSize = 21.sp, modifier = Modifier.weight(1f),
                )
                Text(if (d.measure == Measure.DISTANCE) uD else "mm.ss", color = colors.muted2, fontSize = 12.sp)
            }
            FieldError(errors.value)
        }

        // Allure cible
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Allure cible", optionalNote)
                PillToggle(
                    options = listOf("Unique", "Intervalle"),
                    selectedIndex = if (d.paceMode == PaceMode.RANGE) 1 else 0,
                    onSelect = { if (it == 0) onSetPaceSingle() else onSetPaceRange() },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldBox(modifier = Modifier.weight(1f)) {
                    SeanceTextField(
                        controller = controller, field = FieldKey.PACE, sheet = sheet, value = d.pace,
                        onValueChange = { onField(FieldKey.PACE, it) }, onSubmit = onSubmit,
                        placeholder = "4.30", boxed = false, fontSize = 21.sp, modifier = Modifier.weight(1f),
                    )
                }
                if (d.paceMode == PaceMode.RANGE) {
                    Text("à", color = colors.muted2, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
                    FieldBox(modifier = Modifier.weight(1f)) {
                        SeanceTextField(
                            controller = controller, field = FieldKey.PACE_MAX, sheet = sheet, value = d.paceMax,
                            onValueChange = { onField(FieldKey.PACE_MAX, it) }, onSubmit = onSubmit,
                            placeholder = "4.45", boxed = false, fontSize = 21.sp, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Text(uP, color = colors.muted2, fontSize = 12.5.sp)
            FieldError(errors.pace ?: errors.paceMax)
        }

        // Répétitions
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Répétitions")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StepperButton("−", onClick = onRepsDown)
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(40.dp)
                        .background(colors.field, RoundedCornerShape(11.dp))
                        .border(1.dp, colors.line2, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    SeanceTextField(
                        controller = controller, field = FieldKey.REPS, sheet = sheet, value = d.reps,
                        onValueChange = { onField(FieldKey.REPS, it) }, onSubmit = onSubmit,
                        boxed = false, fontSize = 17.sp, monospace = false,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                StepperButton("+", onClick = onRepsUp)
            }
        }
        FieldError(errors.reps)

        Divider()

        // Récupération
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CheckRow(checked = d.recovery, label = "Récupération après chaque répétition", onToggle = onToggleRecovery)
            if (d.recovery) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.field, RoundedCornerShape(14.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                        .padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { SectionLabel("Durée") }
                        FieldBox(modifier = Modifier.width(150.dp), height = 46.dp, deep = true) {
                            SeanceTextField(
                                controller = controller, field = FieldKey.REC_DUR, sheet = sheet, value = d.recDur,
                                onValueChange = { onField(FieldKey.REC_DUR, it) }, onSubmit = onSubmit,
                                placeholder = "2.00", boxed = false, fontSize = 19.sp, modifier = Modifier.weight(1f),
                            )
                            Text("mm.ss", color = colors.muted2, fontSize = 12.sp)
                        }
                    }
                    FieldError(errors.recDur)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Allure de récup.")
                        PillToggle(
                            options = listOf("Unique", "Intervalle"),
                            selectedIndex = if (d.recPaceMode == PaceMode.RANGE) 1 else 0,
                            onSelect = { if (it == 0) onSetRecSingle() else onSetRecRange() },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FieldBox(modifier = Modifier.weight(1f), height = 46.dp, deep = true) {
                            SeanceTextField(
                                controller = controller, field = FieldKey.REC_PACE, sheet = sheet, value = d.recPace,
                                onValueChange = { onField(FieldKey.REC_PACE, it) }, onSubmit = onSubmit,
                                placeholder = "9.00", boxed = false, fontSize = 19.sp, modifier = Modifier.weight(1f),
                            )
                        }
                        if (d.recPaceMode == PaceMode.RANGE) {
                            Text("à", color = colors.muted2, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                            FieldBox(modifier = Modifier.weight(1f), height = 46.dp, deep = true) {
                                SeanceTextField(
                                    controller = controller, field = FieldKey.REC_PACE_MAX, sheet = sheet, value = d.recPaceMax,
                                    onValueChange = { onField(FieldKey.REC_PACE_MAX, it) }, onSubmit = onSubmit,
                                    placeholder = "11.00", boxed = false, fontSize = 19.sp, modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Text(uP, color = colors.muted2, fontSize = 12.5.sp)
                    FieldError(errors.recPace)
                }
            }
        }

        Divider()

        Text(
            Formatting.previewLine(d, isTemplate, unit),
            color = colors.muted2, fontSize = 12.5.sp, lineHeight = 17.sp,
        )
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    val colors = LocalSeanceColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = colors.fg2, fontSize = 20.sp, fontWeight = FontWeight.Normal)
    }
}
