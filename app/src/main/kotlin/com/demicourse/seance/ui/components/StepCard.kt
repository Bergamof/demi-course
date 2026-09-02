package com.demicourse.seance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demicourse.domain.PaceUnit
import com.demicourse.domain.StepSpec
import com.demicourse.domain.TurnPoint
import com.demicourse.seance.ui.Formatting
import com.demicourse.seance.ui.theme.LocalSeanceColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StepCard(
    step: StepSpec,
    index: Int,
    unit: PaceUnit,
    turn: TurnPoint?,
    stepCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSeanceColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(colors.surface3, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${index + 1}", color = colors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Column(
                modifier = Modifier.weight(1f).clickable(onClick = onEdit),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        com.demicourse.domain.PaceMath.autoName(step, unit),
                        color = colors.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    )
                    if (step.reps > 1) {
                        Chip(text = "× ${step.reps}", background = colors.accentChip, foreground = colors.accentFg, bold = true)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(text = Formatting.paceText(step, unit), background = colors.surface2, foreground = colors.fg2, monospace = true)
                    Chip(text = Formatting.measureText(step, unit), background = colors.surface2, foreground = colors.fg2, monospace = true)
                    if (step.recovery) {
                        Chip(
                            text = Formatting.recoveryChipText(step, unit), background = Color.Transparent, foreground = colors.muted,
                            monospace = true, dashedBorder = colors.dash,
                        )
                    }
                }
                Text(Formatting.totalsText(step, unit), color = colors.muted2, fontSize = 12.sp)
            }

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = colors.muted3, fontSize = 16.sp)
            }
        }

        if (turn != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.accentFaint, RoundedCornerShape(11.dp))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("↩", color = colors.accent, fontSize = 14.sp)
                Text(
                    Formatting.turnaroundMarkerText(turn, stepCount, unit),
                    color = colors.accentFg, fontSize = 11.5.sp, lineHeight = 16.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Chip(
    text: String,
    background: Color,
    foreground: Color,
    monospace: Boolean = false,
    bold: Boolean = false,
    dashedBorder: Color? = null,
) {
    val cornerDp = 8.dp
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(cornerDp))
            .background(background)
            .let { m -> if (dashedBorder != null) m.dashedBorder(dashedBorder, cornerDp) else m }
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            color = foreground,
            fontSize = if (monospace) 13.sp else 11.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        )
    }
}
