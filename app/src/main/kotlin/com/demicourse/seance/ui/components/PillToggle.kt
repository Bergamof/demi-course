package com.demicourse.seance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demicourse.seance.ui.theme.LocalSeanceColors

/**
 * The pill-shaped segmented control used throughout the editor (Distance/Durée,
 * Unique/Intervalle, Système/Clair/Sombre): a bordered track with an accent-filled pill
 * on the selected option.
 */
@Composable
fun PillToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
) {
    val colors = LocalSeanceColors.current
    Row(
        modifier = modifier
            .background(colors.field, RoundedCornerShape(9.dp))
            .border(1.dp, colors.line, RoundedCornerShape(9.dp))
            .padding(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val itemModifier = if (fillWidth) Modifier.weight(1f) else Modifier
            Text(
                text = label,
                color = if (selected) colors.onAccent else colors.muted,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = if (fillWidth) TextAlign.Center else TextAlign.Unspecified,
                modifier = itemModifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) colors.accent else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}
