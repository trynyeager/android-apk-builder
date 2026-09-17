package com.example.sampleapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.jeefocus.MainActivity
import com.jeefocus.data.local.AppDatabase
import com.jeefocus.data.local.StudySession
import com.jeefocus.data.local.Subject
import com.jeefocus.data.local.UserProgress
import kotlinx.coroutines.*
import java.time.LocalDate

class StudyForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var database: AppDatabase

    private var subject: Subject = Subject.PHYSICS
    private var baseTime: Long = 0L
    private var isRunning: Boolean = false
    private var accumulatedSeconds: Long = 0L
    private var startEpochMs: Long = 0L

    companion object {
        const val CHANNEL_ID = "jee_study_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_SUBJECT = "EXTRA_SUBJECT"
    }

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "jee_tracker.db").build()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val subjectName = intent.getStringExtra(EXTRA_SUBJECT) ?: Subject.PHYSICS.name
                subject = Subject.valueOf(subjectName)
                startEpochMs = System.currentTimeMillis()
                baseTime = SystemClock.elapsedRealtime()
                accumulatedSeconds = 0L
                isRunning = true
                startForeground(NOTIFICATION_ID, buildNotification(isRunning = true))
            }
            ACTION_PAUSE -> {
                if (isRunning) {
                    accumulatedSeconds += (SystemClock.elapsedRealtime() - baseTime) / 1000
                    isRunning = false
                    updateNotification()
                }
            }
            ACTION_RESUME -> {
                if (!isRunning) {
                    baseTime = SystemClock.elapsedRealtime()
                    isRunning = true
                    updateNotification()
                }
            }
            ACTION_STOP -> {
                if (isRunning) {
                    accumulatedSeconds += (SystemClock.elapsedRealtime() - baseTime) / 1000
                }
                saveSessionAndStop()
            }
        }
        return START_STICKY
    }

    private fun saveSessionAndStop() {
        val totalSecs = accumulatedSeconds
        val endEpochMs = System.currentTimeMillis()
        val dateToday = LocalDate.now().toString()

        serviceScope.launch {
            if (totalSecs > 10) {
                val session = StudySession(
                    subject = subject,
                    startTimeEpochMs = startEpochMs,
                    endTimeEpochMs = endEpochMs,
                    durationSeconds = totalSecs,
                    dateString = dateToday
                )
                database.studyDao().insertSession(session)

                val earnedXp = (totalSecs / 60) * 10
                val currentProgress = database.studyDao().getUserProgress()
                // Synchronous update
                val prev = database.studyDao().getScreenTimeDirect(dateToday) // Keep flow warm
                val p = database.studyDao().getUserProgress()
                // Update progress row
                database.studyDao().upsertUserProgress(
                    UserProgress(
                        id = 1,
                        totalXp = (p?.totalXp ?: 0) + earnedXp,
                        physicsXp = (p?.physicsXp ?: 0) + if (subject == Subject.PHYSICS) earnedXp else 0,
                        chemistryXp = (p?.chemistryXp ?: 0) + if (subject == Subject.CHEMISTRY) earnedXp else 0,
                        mathXp = (p?.mathXp ?: 0) + if (subject == Subject.MATHEMATICS) earnedXp else 0
                    )
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(isRunning))
    }

    private fun buildNotification(isRunning: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = PendingIntent.getService(
            this, 1,
            Intent(this, StudyForegroundService::class.java).apply {
                action = if (isRunning) ACTION_PAUSE else ACTION_RESUME
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, StudyForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JEE Study Engine: ${subject.name}")
            .setContentText(if (isRunning) "Deep focus session running" else "Session paused")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppIntent)
            .setOngoing(isRunning)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .addAction(
                if (isRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isRunning) "Pause" else "Resume",
                pauseResumeIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop & Save", stopIntent)

        if (isRunning) {
            builder.setUsesChronometer(true)
                .setWhen(baseTime - (accumulatedSeconds * 1000))
        } else {
            builder.setUsesChronometer(false)
            val minutes = accumulatedSeconds / 60
            val seconds = accumulatedSeconds % 60
            builder.setContentText("Paused at ${String.format("%02d:%02d", minutes, seconds)}")
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active Study Sessions",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent low-latency chronometer notification"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}