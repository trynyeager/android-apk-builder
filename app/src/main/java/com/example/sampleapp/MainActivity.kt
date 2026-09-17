package com.example.sampleapp

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.jeefocus.data.local.*
import com.jeefocus.monitor.ScreenTimeEngine
import com.jeefocus.ui.StudyDashboard
import com.jeefocus.ui.theme.JeeFocusTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private lateinit var db: AppDatabase
    private lateinit var screenTimeEngine: ScreenTimeEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "jee_tracker.db")
            .fallbackToDestructiveMigration()
            .build()

        screenTimeEngine = ScreenTimeEngine(this, db)

        if (!screenTimeEngine.hasUsageStatsPermission()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        lifecycleScope.launch {
            screenTimeEngine.syncScreenTimeAndCheckThreshold()
        }

        val todayStr = LocalDate.now().toString()

        setContent {
            JeeFocusTheme {
                val sessions by db.studyDao().getSessionsForDate(todayStr).collectAsState(initial = emptyList())
                val screenTimeRecord by db.studyDao().getScreenTimeForDate(todayStr).collectAsState(initial = null)
                val aggregations by db.studyDao().getAllDailyStudyAggregations().collectAsState(initial = emptyList())
                val progress by db.studyDao().getUserProgress().collectAsState(initial = null)
                val tasks by db.studyDao().getTasksForDate(todayStr).collectAsState(initial = emptyList())

                StudyDashboard(
                    todaySessions = sessions,
                    screenTimeMs = screenTimeRecord?.totalScreenTimeMs ?: 0L,
                    allAggregations = aggregations,
                    progress = progress,
                    tasks = tasks,
                    onToggleTask = { task ->
                        lifecycleScope.launch {
                            db.studyDao().updateTask(task.copy(isCompleted = !task.isCompleted))
                        }
                    },
                    onAddTask = { title, subj ->
                        lifecycleScope.launch {
                            db.studyDao().insertTask(
                                TodoTask(
                                    title = title,
                                    subject = subj,
                                    targetDate = todayStr
                                )
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            screenTimeEngine.syncScreenTimeAndCheckThreshold()
        }
    }
}