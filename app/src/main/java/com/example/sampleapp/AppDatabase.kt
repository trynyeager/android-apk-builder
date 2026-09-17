package com.example.sampleapp

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class Subject { PHYSICS, CHEMISTRY, MATHEMATICS }

@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: Subject,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val durationSeconds: Long,
    val dateString: String // Format: YYYY-MM-DD
)

@Entity(tableName = "daily_screen_time")
data class DailyScreenTime(
    @PrimaryKey val dateString: String,
    val totalScreenTimeMs: Long,
    val lastHourlyNotifiedHour: Int = 0
)

@Entity(tableName = "todo_tasks")
data class TodoTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: Subject,
    val isCompleted: Boolean = false,
    val targetDate: String
)

@Entity(tableName = "user_progress")
data class UserProgress(
    @PrimaryKey val id: Int = 1,
    val totalXp: Long = 0,
    val physicsXp: Long = 0,
    val chemistryXp: Long = 0,
    val mathXp: Long = 0,
    val dailyGoalHours: Float = 6.0f
)

data class DayStudyAggregation(
    val dateString: String,
    val totalSeconds: Long
)

@Dao
interface StudyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySession): Long

    @Query("SELECT * FROM study_sessions WHERE dateString = :date ORDER BY startTimeEpochMs DESC")
    fun getSessionsForDate(date: String): Flow<List<StudySession>>

    @Query("SELECT dateString, SUM(durationSeconds) as totalSeconds FROM study_sessions GROUP BY dateString")
    fun getAllDailyStudyAggregations(): Flow<List<DayStudyAggregation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScreenTime(screenTime: DailyScreenTime)

    @Query("SELECT * FROM daily_screen_time WHERE dateString = :date")
    fun getScreenTimeForDate(date: String): Flow<DailyScreenTime?>

    @Query("SELECT * FROM daily_screen_time WHERE dateString = :date")
    suspend fun getScreenTimeDirect(date: String): DailyScreenTime?

    @Query("SELECT * FROM todo_tasks WHERE targetDate = :date ORDER BY id DESC")
    fun getTasksForDate(date: String): Flow<List<TodoTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TodoTask)

    @Update
    suspend fun updateTask(task: TodoTask)

    @Delete
    suspend fun deleteTask(task: TodoTask)

    @Query("SELECT * FROM user_progress WHERE id = 1")
    fun getUserProgress(): Flow<UserProgress?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProgress(progress: UserProgress)
}

@Database(
    entities = [StudySession::class, DailyScreenTime::class, TodoTask::class, UserProgress::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao
}