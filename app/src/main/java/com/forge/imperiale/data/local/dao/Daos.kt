package com.forge.imperiale.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.forge.imperiale.data.local.entity.ActiveWorkoutStateEntity
import com.forge.imperiale.data.local.entity.BlockType
import com.forge.imperiale.data.local.entity.CalendarLinkEntity
import com.forge.imperiale.data.local.entity.ExerciseEntity
import com.forge.imperiale.data.local.entity.MealLogEntity
import com.forge.imperiale.data.local.entity.MealSlotEntity
import com.forge.imperiale.data.local.entity.ReminderEntity
import com.forge.imperiale.data.local.entity.ScheduleBlockEntity
import com.forge.imperiale.data.local.entity.SetLogEntity
import com.forge.imperiale.data.local.entity.TemplateExerciseEntity
import com.forge.imperiale.data.local.entity.UserProfileEntity
import com.forge.imperiale.data.local.entity.WaterLogEntity
import com.forge.imperiale.data.local.entity.WeeklyCheckinEntity
import com.forge.imperiale.data.local.entity.WorkoutSessionEntity
import com.forge.imperiale.data.local.entity.WorkoutTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_blocks ORDER BY dayOfWeek ASC, startMinute ASC")
    fun observeAllBlocks(): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM schedule_blocks WHERE dayOfWeek = :dayOfWeek ORDER BY startMinute ASC")
    fun observeBlocksForDay(dayOfWeek: Int): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM schedule_blocks ORDER BY dayOfWeek ASC, startMinute ASC")
    suspend fun getAllBlocks(): List<ScheduleBlockEntity>

    @Query("SELECT * FROM schedule_blocks WHERE locked = 1 ORDER BY dayOfWeek ASC, startMinute ASC")
    suspend fun getLockedBlocks(): List<ScheduleBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: ScheduleBlockEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<ScheduleBlockEntity>)

    @Update
    suspend fun updateBlock(block: ScheduleBlockEntity)

    @Query("DELETE FROM schedule_blocks WHERE id = :id")
    suspend fun deleteBlockById(id: Long)

    @Query("DELETE FROM schedule_blocks WHERE source = :source")
    suspend fun deleteBlocksBySource(source: String)

    @Query("DELETE FROM schedule_blocks WHERE locked = 0 AND type IN (:types)")
    suspend fun deleteGeneratedBlocks(types: List<BlockType>)

    @Query("DELETE FROM schedule_blocks")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceGeneratedSchedule(generated: List<ScheduleBlockEntity>) {
        deleteBlocksBySource("SCHEDULER")
        insertBlocks(generated)
    }
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun observeAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    suspend fun getAllExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExerciseById(id: String): ExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercises(exercises: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercise(exercise: ExerciseEntity)

    @Query("DELETE FROM exercises")
    suspend fun clearAll()
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_templates ORDER BY dayOfWeek ASC")
    fun observeTemplates(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_templates ORDER BY dayOfWeek ASC")
    suspend fun getTemplates(): List<WorkoutTemplateEntity>

    @Query("SELECT * FROM workout_templates WHERE id = :id")
    suspend fun getTemplateById(id: String): WorkoutTemplateEntity?

    @Query("SELECT * FROM workout_templates WHERE dayOfWeek = :dayOfWeek LIMIT 1")
    suspend fun getTemplateForDay(dayOfWeek: Int): WorkoutTemplateEntity?

    @Query("SELECT * FROM template_exercises WHERE templateId = :templateId ORDER BY orderIndex ASC")
    suspend fun getTemplateExercises(templateId: String): List<TemplateExerciseEntity>

    @Query("SELECT * FROM template_exercises ORDER BY templateId ASC, orderIndex ASC")
    suspend fun getAllTemplateExercises(): List<TemplateExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<WorkoutTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateExercises(items: List<TemplateExerciseEntity>)

    @Query("DELETE FROM template_exercises")
    suspend fun clearTemplateExercises()

    // Sessions
    @Query("SELECT * FROM workout_sessions ORDER BY startEpochMs DESC")
    fun observeAllSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions ORDER BY startEpochMs DESC")
    suspend fun getAllSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE dateIso = :dateIso ORDER BY startEpochMs DESC")
    suspend fun getSessionsForDate(dateIso: String): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): WorkoutSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<WorkoutSessionEntity>)

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    // Set Logs
    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeSetsForSession(sessionId: Long): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getSetsForSession(sessionId: Long): List<SetLogEntity>

    @Query("SELECT * FROM set_logs WHERE exerciseId = :exerciseId ORDER BY timestampEpochMs DESC")
    suspend fun getSetsForExercise(exerciseId: String): List<SetLogEntity>

    @Query("SELECT * FROM set_logs ORDER BY timestampEpochMs ASC")
    fun observeAllSets(): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_logs ORDER BY timestampEpochMs ASC")
    suspend fun getAllSets(): List<SetLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetLog(setLog: SetLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetLogs(setLogs: List<SetLogEntity>)

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteSetLogById(id: Long)

    // Active Workout Persistence
    @Query("SELECT * FROM active_workout_state WHERE id = 1")
    fun observeActiveState(): Flow<ActiveWorkoutStateEntity?>

    @Query("SELECT * FROM active_workout_state WHERE id = 1")
    suspend fun getActiveState(): ActiveWorkoutStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActiveState(state: ActiveWorkoutStateEntity)

    @Query("DELETE FROM active_workout_state")
    suspend fun clearActiveState()

    @Query("DELETE FROM workout_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM set_logs")
    suspend fun clearSetLogs()
}

@Dao
interface DietDao {
    @Query("SELECT * FROM meal_slots ORDER BY orderIndex ASC")
    fun observeMealSlots(): Flow<List<MealSlotEntity>>

    @Query("SELECT * FROM meal_slots ORDER BY orderIndex ASC")
    suspend fun getMealSlots(): List<MealSlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealSlots(slots: List<MealSlotEntity>)

    @Query("SELECT * FROM meal_logs WHERE dateIso = :dateIso")
    fun observeMealLogsForDate(dateIso: String): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs WHERE dateIso BETWEEN :startIso AND :endIso")
    fun observeMealLogsBetween(startIso: String, endIso: String): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs WHERE dateIso BETWEEN :startIso AND :endIso")
    suspend fun getMealLogsBetween(startIso: String, endIso: String): List<MealLogEntity>

    @Query("SELECT * FROM meal_logs")
    suspend fun getAllMealLogs(): List<MealLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMealLog(log: MealLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealLogs(logs: List<MealLogEntity>)

    @Query("SELECT * FROM water_logs WHERE dateIso = :dateIso")
    fun observeWaterForDate(dateIso: String): Flow<WaterLogEntity?>

    @Query("SELECT * FROM water_logs WHERE dateIso = :dateIso")
    suspend fun getWaterForDate(dateIso: String): WaterLogEntity?

    @Query("SELECT * FROM water_logs")
    suspend fun getAllWaterLogs(): List<WaterLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWaterLog(log: WaterLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaterLogs(logs: List<WaterLogEntity>)

    @Query("DELETE FROM meal_logs")
    suspend fun clearMealLogs()

    @Query("DELETE FROM water_logs")
    suspend fun clearWaterLogs()
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM weekly_checkins ORDER BY weekStartIso DESC")
    fun observeCheckins(): Flow<List<WeeklyCheckinEntity>>

    @Query("SELECT * FROM weekly_checkins ORDER BY weekStartIso DESC")
    suspend fun getAllCheckins(): List<WeeklyCheckinEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckin(checkin: WeeklyCheckinEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckins(checkins: List<WeeklyCheckinEntity>)

    @Query("DELETE FROM weekly_checkins")
    suspend fun clearCheckins()
}

@Dao
interface SystemSyncDao {
    @Query("SELECT * FROM reminders ORDER BY dayOfWeek ASC, triggerMinute ASC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY dayOfWeek ASC, triggerMinute ASC")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceReminders(reminders: List<ReminderEntity>)

    @Query("DELETE FROM reminders")
    suspend fun clearReminders()

    @Query("SELECT * FROM calendar_links")
    suspend fun getAllCalendarLinks(): List<CalendarLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCalendarLink(link: CalendarLinkEntity)

    @Query("DELETE FROM calendar_links WHERE externalKey = :key")
    suspend fun deleteCalendarLink(key: String)

    @Query("DELETE FROM calendar_links")
    suspend fun clearCalendarLinks()
}
