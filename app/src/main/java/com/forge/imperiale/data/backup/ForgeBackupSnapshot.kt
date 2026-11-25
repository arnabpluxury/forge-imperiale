package com.forge.imperiale.data.backup

import com.forge.imperiale.data.local.entity.ExerciseEntity
import com.forge.imperiale.data.local.entity.MealLogEntity
import com.forge.imperiale.data.local.entity.ScheduleBlockEntity
import com.forge.imperiale.data.local.entity.SetLogEntity
import com.forge.imperiale.data.local.entity.UserProfileEntity
import com.forge.imperiale.data.local.entity.WaterLogEntity
import com.forge.imperiale.data.local.entity.WeeklyCheckinEntity
import com.forge.imperiale.data.local.entity.WorkoutSessionEntity

/**
 * Portable JSON Backup & Restore + MongoDB Cloud Sync Snapshot model.
 */
data class ForgeBackupSnapshot(
    val schemaVersion: Int = 1,
    val exportedAtEpochMs: Long,
    val profile: UserProfileEntity?,
    val customScheduleBlocks: List<ScheduleBlockEntity>,
    val exercises: List<ExerciseEntity>,
    val sessions: List<WorkoutSessionEntity>,
    val setLogs: List<SetLogEntity>,
    val mealLogs: List<MealLogEntity>,
    val waterLogs: List<WaterLogEntity>,
    val weeklyCheckins: List<WeeklyCheckinEntity>
)
