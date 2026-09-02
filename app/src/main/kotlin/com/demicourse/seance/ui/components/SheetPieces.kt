package com.demicourse.seance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demicourse.seance.ui.theme.LocalSeanceColors

/** A dashed rounded-rect border, matching the prototype's `border:1px dashed var(--dash)` accents. */
fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.dp): Modifier = this.drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = Stroke(width = strokeWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))),
    )
}

@Composable
fun SheetHeader(title: String, onClose: () -> Unit) {
    val colors = LocalSeanceColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = colors.fg, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(colors.surface2)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = colors.muted, fontSize = 16.sp)
        }
    }
}

@Composable
fun SectionLabel(text: String, suffix: String = "") {
    val colors = LocalSeanceColors.current
    Row {
        Text(text.uppercase(), color = colors.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
        if (suffix.isNotEmpty()) {
            Text(" $suffix", color = colors.muted3, fontSize = 11.sp)
        }
    }
}

@Composable
fun FieldError(message: String?) {
    if (!message.isNullOrEmpty()) {
        val colors = LocalSeanceColors.current
        Text(message, color = colors.danger, fontSize = 12.sp)
    }
}

@Composable
fun CheckRow(checked: Boolean, label: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSeanceColors.current
    Row(
        modifier = modifier.clickable(onClick = onToggle),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) colors.accent else Color.Transparent)
                .border(1.dp, colors.line2, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("✓", color = colors.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = colors.fg3, fontSize = 13.5.sp)
    }
}

@Composable
fun FooterButtons(cancelLabel: String, submitLabel: String, onCancel: () -> Unit, onSubmit: () -> Unit) {
    val colors = LocalSeanceColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, colors.line2, RoundedCornerShape(14.dp))
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Text(cancelLabel, color = colors.fg3, fontSize = 15.sp)
        }
        Box(
            modifier = Modifier
                .weight(1.6f)
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.accent)
                .clickable(onClick = onSubmit),
            contentAlignment = Alignment.Center,
        ) {
            Text(submitLabel, color = colors.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun Divider() {
    val colors = LocalSeanceColors.current
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.line))
}

@Composable
fun FieldBox(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 52.dp,
    deep: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val colors = LocalSeanceColors.current
    Row(
        modifier = modifier
            .height(height)
            .background(if (deep) colors.fieldDeep else colors.field, RoundedCornerShape(12.dp))
            .border(1.dp, colors.line2, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun HintsRow(visible: Boolean) {
    if (!visible) return
    val colors = LocalSeanceColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        listOf("Tab / ⏎ champ suivant", "Ctrl+⏎ valider", "Échap fermer").forEach { hint ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(colors.field)
                    .border(1.dp, colors.line, RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(hint, color = colors.muted3, fontSize = 11.sp)
            }
        }
    }
}
