package com.demicourse.seance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demicourse.domain.PaceMode
import com.demicourse.domain.PaceUnit
import com.demicourse.domain.StepSpec
import com.demicourse.domain.ThemeChoice
import com.demicourse.seance.ui.FieldKey
import com.demicourse.seance.ui.Formatting
import com.demicourse.seance.ui.SheetState
import com.demicourse.seance.ui.theme.LocalSeanceColors
import com.demicourse.seance.ui.theme.resolveIsDark

@Composable
fun SettingsBody(
    controller: SheetFocusController,
    sheet: SheetState,
    unit: PaceUnit,
    themeChoice: ThemeChoice,
    templates: List<StepSpec>,
    onSetTheme: (ThemeChoice) -> Unit,
    onField: (FieldKey, String) -> Unit,
    onSetRecSingle: () -> Unit,
    onSetRecRange: () -> Unit,
    onEditTemplate: (StepSpec) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onNewTemplate: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalSeanceColors.current
    val d = sheet.draft
    val uP = com.demicourse.domain.PaceMath.unitPaceLabel(unit)
    val isDark = resolveIsDark(themeChoice)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Thème")
            PillToggle(
                options = listOf("Système", "Clair", "Sombre"),
                selectedIndex = when (themeChoice) { ThemeChoice.SYSTEM -> 0; ThemeChoice.LIGHT -> 1; ThemeChoice.DARK -> 2 },
                onSelect = { onSetTheme(listOf(ThemeChoice.SYSTEM, ThemeChoice.LIGHT, ThemeChoice.DARK)[it]) },
                fillWidth = true,
            )
            val note = if (themeChoice == ThemeChoice.SYSTEM) {
                "Suit le réglage d’Android · actuellement ${if (isDark) "sombre" else "clair"}."
            } else {
                "Thème ${if (isDark) "sombre" else "clair"} forcé, quel que soit le réglage du système."
            }
            Text(note, color = colors.muted2, fontSize = 12.sp, lineHeight = 17.sp)
        }

        Divider()

        Text("Allure de récupération proposée par défaut à chaque nouvelle étape.", color = colors.muted2, fontSize = 12.5.sp, lineHeight = 17.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Allure de récup.")
            PillToggle(
                options = listOf("Unique", "Intervalle"),
                selectedIndex = if (d.recPaceMode == PaceMode.RANGE) 1 else 0,
                onSelect = { if (it == 0) onSetRecSingle() else onSetRecRange() },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldBox(modifier = Modifier.weight(1f)) {
                SeanceTextField(
                    controller = controller, field = FieldKey.REC_PACE, sheet = sheet, value = d.recPace,
                    onValueChange = { onField(FieldKey.REC_PACE, it) }, onSubmit = onSubmit,
                    placeholder = "9.00", boxed = false, fontSize = 21.sp, modifier = Modifier.weight(1f),
                )
            }
            if (d.recPaceMode == PaceMode.RANGE) {
                Text("à", color = colors.muted2, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
                FieldBox(modifier = Modifier.weight(1f)) {
                    SeanceTextField(
                        controller = controller, field = FieldKey.REC_PACE_MAX, sheet = sheet, value = d.recPaceMax,
                        onValueChange = { onField(FieldKey.REC_PACE_MAX, it) }, onSubmit = onSubmit,
                        placeholder = "11.00", boxed = false, fontSize = 21.sp, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text(uP, color = colors.muted2, fontSize = 12.5.sp)
        FieldError(sheet.errors.recPace)

        Divider()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Modèles")
            templates.forEach { tpl ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.field, RoundedCornerShape(12.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tpl.name, color = colors.fg, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(Formatting.templateDetail(tpl, unit), color = colors.muted2, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    IconSquare(symbol = "✎", color = colors.fg3, onClick = { onEditTemplate(tpl) })
                    IconSquare(symbol = "×", color = colors.danger, onClick = { onDeleteTemplate(tpl.id) })
                }
            }
            if (templates.isEmpty()) {
                Text("Aucun modèle enregistré.", color = colors.muted2, fontSize = 12.5.sp, lineHeight = 17.sp)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .dashedBorder(colors.dash, 12.dp)
                    .clickable(onClick = onNewTemplate)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("+ Nouveau modèle", color = colors.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun IconSquare(symbol: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val colors = LocalSeanceColors.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = color, fontSize = 14.sp)
    }
}
