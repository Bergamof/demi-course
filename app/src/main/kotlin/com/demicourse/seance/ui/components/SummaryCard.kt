package com.demicourse.seance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demicourse.domain.PaceUnit
import com.demicourse.domain.SessionResult
import com.demicourse.seance.ui.Formatting
import com.demicourse.seance.ui.theme.LocalSeanceColors

@Composable
fun SummaryCard(session: SessionResult, unit: PaceUnit, stepCount: Int, modifier: Modifier = Modifier) {
    val colors = LocalSeanceColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatColumn("Distance (${com.demicourse.domain.PaceMath.unitDistanceLabel(unit)})", Formatting.totalDistanceText(session, unit), Modifier.weight(1f))
            StatColumn("Durée (min)", Formatting.totalDurationText(session), Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.accentSoft, RoundedCornerShape(15.dp))
                .border(1.dp, colors.accentSoftLine, RoundedCornerShape(15.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(colors.accent, RoundedCornerShape(19.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("↩", color = colors.onAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(
                    "DEMI-TOUR", color = colors.accentFg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                )
                Text(Formatting.turnMainText(session, unit), color = colors.fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(Formatting.turnSubText(session, stepCount), color = colors.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalSeanceColors.current
    Column(modifier = modifier) {
        Text(label.uppercase(), color = colors.muted, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = colors.fg, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
    }
}
