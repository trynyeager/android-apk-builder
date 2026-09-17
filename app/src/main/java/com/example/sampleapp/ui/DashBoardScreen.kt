package com.example.sampleapp.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeefocus.data.local.*
import com.jeefocus.service.StudyForegroundService
import com.jeefocus.ui.components.ContributionHeatmap
import com.jeefocus.ui.theme.*

@Composable
fun StudyDashboard(
    todaySessions: List<StudySession>,
    screenTimeMs: Long,
    allAggregations: List<DayStudyAggregation>,
    progress: UserProgress?,
    tasks: List<TodoTask>,
    onToggleTask: (TodoTask) -> Unit,
    onAddTask: (String, Subject) -> Unit
) {
    val context = LocalContext.current
    var selectedSubject by remember { mutableStateOf(Subject.PHYSICS) }
    var newTaskTitle by remember { mutableStateOf("") }

    val totalStudySeconds = todaySessions.sumOf { it.durationSeconds }
    val studyHours = totalStudySeconds / 3600f
    val phoneHours = screenTimeMs / 3_600_000f

    val focusRatio = if (studyHours + phoneHours > 0f) {
        (studyHours / (studyHours + phoneHours)) * 100f
    } else 100f

    val totalXp = progress?.totalXp ?: 0L
    val overallLevel = (totalXp / 1000).toInt() + 1
    val rankTitle = when {
        overallLevel < 5 -> "Aspirant"
        overallLevel < 15 -> "Concept Builder"
        overallLevel < 30 -> "Problem Solver"
        overallLevel < 50 -> "Rank Chaser"
        else -> "AIR Tier"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GhCanvasDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar & Level Tier Badge
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("JEE DROP ENGINE", color = GhTextMuted, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text("Rank: $rankTitle (Lvl $overallLevel)", color = GhTextPrimary, fontSize = 18.sp)
                }
                Box(
                    modifier = Modifier
                        .background(GhCardBg, RoundedCornerShape(6.dp))
                        .border(1.dp, GhCardBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("${totalXp} XP", color = GhGreenAccent, fontSize = 13.sp)
                }
            }
        }

        // Active Session Controller
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GhCardBg, RoundedCornerShape(8.dp))
                    .border(1.dp, GhCardBorder, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("START SESSION", color = GhTextMuted, fontSize = 12.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Subject.values().forEach { subj ->
                            val isSelected = subj == selectedSubject
                            Button(
                                onClick = { selectedSubject = subj },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) GhGreenDark else GhCardBg,
                                    contentColor = if (isSelected) Color.White else GhTextPrimary
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, if (isSelected) GhGreenAccent else GhCardBorder, RoundedCornerShape(6.dp))
                            ) {
                                Text(subj.name.take(4), fontSize = 11.sp)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val intent = Intent(context, StudyForegroundService::class.java).apply {
                                action = StudyForegroundService.ACTION_START
                                putExtra(StudyForegroundService.EXTRA_SUBJECT, selectedSubject.name)
                            }
                            context.startForegroundService(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GhGreenAccent),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Launch Chronometer Engine", color = Color.White)
                    }
                }
            }
        }

        // Core Analytics Card (Study Time, Goal, Focus Ratio)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GhCardBg, RoundedCornerShape(8.dp))
                    .border(1.dp, GhCardBorder, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("TODAY'S METRICS", color = GhTextMuted, fontSize = 11.sp)
                        Text(String.format("%.2f / 6.0 hrs", studyHours), color = GhTextPrimary, fontSize = 22.sp)
                        Text("Phone Usage: ${String.format("%.2f", phoneHours)} hrs", color = GhTextMuted, fontSize = 12.sp)
                        Text("Focus Ratio: ${String.format("%.1f", focusRatio)}%", color = if (focusRatio >= 70) GhGreenAccent else GhRedAccent, fontSize = 13.sp)
                    }

                    // Circular 6-Hour Target Progress
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 6.dp.toPx()
                            drawCircle(color = GhCardBorder, style = Stroke(width = stroke))
                            val sweep = (studyHours / 6.0f).coerceIn(0f, 1f) * 360f
                            drawArc(
                                color = GhGreenAccent,
                                startAngle = -90f,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                        Text("${(studyHours / 6f * 100).toInt()}%", color = GhTextPrimary, fontSize = 12.sp)
                    }
                }
            }
        }

        // GitHub Contribution Heatmap
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GhCardBg, RoundedCornerShape(8.dp))
                    .border(1.dp, GhCardBorder, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                ContributionHeatmap(aggregations = allAggregations)
            }
        }

        // Daily JEE Checklist
        item {
            Text("DAILY TARGETS", color = GhTextMuted, fontSize = 12.sp)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    placeholder = { Text("e.g. 30 PYQs Electrostatics", color = GhTextMuted, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GhGreenAccent,
                        unfocusedBorderColor = GhCardBorder,
                        focusedTextColor = GhTextPrimary,
                        unfocusedTextColor = GhTextPrimary
                    )
                )
                Button(
                    onClick = {
                        if (newTaskTitle.isNotBlank()) {
                            onAddTask(newTaskTitle, selectedSubject)
                            newTaskTitle = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GhGreenDark),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("+", fontSize = 18.sp)
                }
            }
        }

        items(tasks) { task ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GhCardBg, RoundedCornerShape(6.dp))
                    .border(1.dp, GhCardBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Checkbox(
                        checked = task.isCompleted,
                        onCheckedChange = { onToggleTask(task) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = GhGreenAccent,
                            uncheckedColor = GhCardBorder,
                            checkmarkColor = Color.White
                        )
                    )
                    Text(
                        text = task.title,
                        color = if (task.isCompleted) GhTextMuted else GhTextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Text(
                    text = task.subject.name.take(3),
                    color = GhTextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .border(0.5.dp, GhCardBorder, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}