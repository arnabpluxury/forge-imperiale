package com.forge.imperiale.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.forge.imperiale.data.local.dao.DietDao
import com.forge.imperiale.data.local.dao.ExerciseDao
import com.forge.imperiale.data.local.dao.ProgressDao
import com.forge.imperiale.data.local.dao.ScheduleDao
import com.forge.imperiale.data.local.dao.SystemSyncDao
import com.forge.imperiale.data.local.dao.UserProfileDao
import com.forge.imperiale.data.local.dao.WorkoutDao
import com.forge.imperiale.data.local.entity.ActiveWorkoutStateEntity
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

@Database(
    entities = [
        UserProfileEntity::class,
        ScheduleBlockEntity::class,
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        TemplateExerciseEntity::class,
        WorkoutSessionEntity::class,
        SetLogEntity::class,
        ActiveWorkoutStateEntity::class,
        MealSlotEntity::class,
        MealLogEntity::class,
        WaterLogEntity::class,
        WeeklyCheckinEntity::class,
        ReminderEntity::class,
        CalendarLinkEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(ForgeTypeConverters::class)
abstract class ForgeDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun dietDao(): DietDao
    abstract fun progressDao(): ProgressDao
    abstract fun systemSyncDao(): SystemSyncDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reserved baseline migration path for future schema upgrades
            }
        }
    }
}
