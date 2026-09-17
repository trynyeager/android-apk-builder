package com.example.sampleapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeefocus.data.local.DayStudyAggregation
import com.jeefocus.ui.theme.GhCardBorder
import com.jeefocus.ui.theme.GhTextMuted
import java.time.LocalDate

@Composable
fun ContributionHeatmap(
    aggregations: List<DayStudyAggregation>,
    modifier: Modifier = Modifier
) {
    val aggMap = aggregations.associate { it.dateString to it.totalSeconds }
    val today = LocalDate.now()
    // Past 20 weeks (140 days) chunked into 7-day columns
    val daysCount = 140
    val dates = (daysCount downTo 0).map { today.minusDays(it.toLong()) }
    val weeks = dates.chunked(7)

    Column(modifier = modifier) {
        Text(
            text = "Study Heatmap (Past 20 Weeks)",
            color = GhTextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(weeks) { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { date ->
                        val seconds = aggMap[date.toString()] ?: 0L
                        val color = when {
                            seconds == 0L -> Color(0xFF161B22)
                            seconds < 7200 -> Color(0xFF0E4429)       // < 2 hrs
                            seconds < 14400 -> Color(0xFF006D32)      // 2-4 hrs
                            seconds < 21600 -> Color(0xFF26A641)      // 4-6 hrs
                            else -> Color(0xFF39D353)                 // 6+ hrs
                        }
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .background(color, RoundedCornerShape(2.dp))
                                .border(0.5.dp, GhCardBorder, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}