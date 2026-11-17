package com.demicourse.seance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demicourse.domain.SessionCalculator
import com.demicourse.seance.ui.components.BottomSheetHost
import com.demicourse.seance.ui.components.MIN_TOUCH_TARGET
import com.demicourse.seance.ui.components.StepCard
import com.demicourse.seance.ui.components.SummaryCard
import com.demicourse.seance.ui.components.dashedBorder
import com.demicourse.seance.ui.theme.LocalSeanceColors

@Composable
fun SeanceScreen(viewModel: SeanceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalSeanceColors.current
    val session =
        remember(state.steps, state.settings.halfBy) { SessionCalculator.compute(state.steps, state.settings.halfBy) }
    var confirmingReset by remember { mutableStateOf(false) }
    val bottomBarInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    // The window is edge-to-edge: the background above paints under the status bar,
                    // but the content itself must clear it (and any display cutout / gesture bar).
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
        ) {
            Header(onSettings = viewModel::openSettingsSheet, onReset = { confirmingReset = true })

            SummaryCard(
                session = session,
                unit = state.settings.unit,
                stepCount = state.steps.size,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    "ÉTAPES",
                    color = colors.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
                Text(Formatting.stepCountText(state.steps.size), color = colors.muted2, fontSize = 12.sp)
            }

            if (state.steps.isEmpty()) {
                EmptyState(modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp + bottomBarInset),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(state.steps, key = { _, step -> step.id }) { index, step ->
                        val turn = session.turn?.takeIf { it.stepIndex == index }
                        StepCard(
                            step = step,
                            index = index,
                            unit = state.settings.unit,
                            turn = turn,
                            onEdit = { viewModel.editStep(step) },
                            onDelete = { viewModel.deleteStep(step.id) },
                        )
                    }
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(colors.bg.copy(alpha = 0f), colors.bg, colors.bg)))
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    ).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.accent)
                        .clickable(role = Role.Button, onClick = viewModel::openStepSheet),
                horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "+",
                    color = colors.onAccent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Text("Ajouter une étape", color = colors.onAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (confirmingReset) {
            AlertDialog(
                onDismissRequest = { confirmingReset = false },
                containerColor = colors.surface,
                title = { Text("Réinitialiser la séance ?", color = colors.fg) },
                text = { Text("Toutes les étapes seront supprimées. Les modèles sont conservés.", color = colors.fg2) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmingReset = false
                            viewModel.resetSession()
                        },
                    ) { Text("Réinitialiser", color = colors.danger) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmingReset = false }) { Text("Annuler", color = colors.fg3) }
                },
            )
        }

        state.sheet?.let { sheet ->
            BottomSheetHost(
                sheet = sheet,
                templates = state.templates,
                unit = state.settings.unit,
                themeChoice = state.settings.theme,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun Header(
    onSettings: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = LocalSeanceColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Séance en cours",
                color = colors.fg,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text("Aller‑retour · demi‑tour à mi‑parcours", color = colors.muted, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircleButton(symbol = "⚙", label = "Paramètres", onClick = onSettings)
            CircleButton(symbol = "↺", label = "Réinitialiser la séance", onClick = onReset)
        }
    }
}

@Composable
private fun CircleButton(
    symbol: String,
    label: String,
    onClick: () -> Unit,
) {
    val colors = LocalSeanceColors.current
    Box(
        modifier =
            Modifier
                .size(MIN_TOUCH_TARGET)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .border(1.dp, colors.line3, RoundedCornerShape(17.dp))
                    .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, color = colors.muted, fontSize = 16.sp)
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val colors = LocalSeanceColors.current
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .dashedBorder(colors.line2, 16.dp)
                .padding(vertical = 26.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Aucune étape.\nAjoutez‑en une pour calculer le demi‑tour.",
            color = colors.muted2,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
    }
}
