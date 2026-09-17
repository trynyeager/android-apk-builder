package com.example.sampleapp

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.core.app.NotificationCompat
import com.jeefocus.data.local.AppDatabase
import com.jeefocus.data.local.DailyScreenTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class ScreenTimeEngine(private val context: Context, private val db: AppDatabase) {

    companion object {
        const val ALERT_CHANNEL_ID = "jee_screen_alert_channel"
        const val ALERT_NOTIFICATION_ID = 2002
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun syncScreenTimeAndCheckThreshold(): Long = withContext(Dispatchers.IO) {
        if (!hasUsageStatsPermission()) return@withContext 0L

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val today = LocalDate.now()
        val startOfDayEpochMs = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val nowEpochMs = System.currentTimeMillis()

        val events = usageStatsManager.queryEvents(startOfDayEpochMs, nowEpochMs)
        val event = UsageEvents.Event()

        var totalUsageMs = 0L
        var lastResumeTime = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // Exclude our own package from distracting screen time
            if (event.packageName == context.packageName) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumeTime = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (lastResumeTime != 0L && event.timeStamp > lastResumeTime) {
                        totalUsageMs += (event.timeStamp - lastResumeTime)
                        lastResumeTime = 0L
                    }
                }
            }
        }

        val dateStr = today.toString()
        val existingRecord = db.studyDao().getScreenTimeDirect(dateStr)
        val currentTotalHours = (totalUsageMs / 3_600_000).toInt()
        val lastNotified = existingRecord?.lastHourlyNotifiedHour ?: 0

        if (currentTotalHours > lastNotified && currentTotalHours >= 1) {
            triggerHourlyNudge(currentTotalHours)
            db.studyDao().upsertScreenTime(
                DailyScreenTime(
                    dateString = dateStr,
                    totalScreenTimeMs = totalUsageMs,
                    lastHourlyNotifiedHour = currentTotalHours
                )
            )
        } else {
            db.studyDao().upsertScreenTime(
                DailyScreenTime(
                    dateString = dateStr,
                    totalScreenTimeMs = totalUsageMs,
                    lastHourlyNotifiedHour = lastNotified
                )
            )
        }

        return@withContext totalUsageMs
    }

    private fun triggerHourlyNudge(hoursUsed: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Study Warnings",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            description = "Alerts when daily phone time crosses hour thresholds"
        }
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("Screen Time Alert: ${hoursUsed}h Crossed")
            .setContentText("You've spent $hoursUsed hours off-target today. Return to your JEE study blocks.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }
}