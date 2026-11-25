package com.forge.imperiale.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BlockType {
    TUITION,
    SCHOOL,
    WORKOUT,
    MEAL,
    SLEEP,
    OTHER
}

enum class VideoStreamType {
    MP4,
    YOUTUBE,
    NONE
}

enum class EffortLevel {
    EASY,
    GOOD,
    HARD,
    FAILED
}

enum class MealStatus {
    PENDING,
    DONE,
    SKIPPED
}

enum class ReminderCategory {
    WORKOUT,
    MEALS,
    SLEEP,
    COACH
}

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Arnab",
    val age: Int? = 16,
    val country: String = "India",
    val timezone: String = "Asia/Kolkata",
    val startDateIso: String,
    val hasDumbbells: Boolean = true,
    val hasBarbell: Boolean = true,
    val hasTreadmill: Boolean = true,
    val eatsFish: Boolean = false,
    val eatsPaneer: Boolean = true,
    val chickenDaysPerWeek: Int = 2,
    val barbellSafetyUnlocked: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val weightStepKg: Double = 1.0,
    val defaultRestSec: Int = 75,
    val waterGoalGlasses: Int = 11
)

@Entity(
    tableName = "schedule_blocks",
    indices = [Index(value = ["dayOfWeek", "startMinute"])]
)
data class ScheduleBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: BlockType,
    val dayOfWeek: Int, // 1 = Monday .. 7 = Sunday (ISO-8601)
    val startMinute: Int, // Minutes from 00:00 (e.g., 18:00 = 1080)
    val endMinute: Int,
    val title: String,
    val subtitle: String = "",
    val locked: Boolean = false,
    val source: String = "SEED", // SEED, USER, SCHEDULER, CALENDAR_BUSY
    val calendarEventId: Long? = null,
    val refId: String? = null // Links to workout templateId or meal slotKey
)

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val muscles: String,
    val equipment: String,
    val cues: String,
    val easierId: String? = null,
    val harderId: String? = null,
    val substituteId: String? = null,
    val isTimed: Boolean = false,
    val defaultSeconds: Int = 30,
    val isBarbellGated: Boolean = false,
    val videoType: VideoStreamType = VideoStreamType.YOUTUBE,
    val videoRef: String = "", // Direct MP4/HLS URL or 11-char YouTube ID
    val loopStartSec: Float = 0f,
    val loopEndSec: Float = 0f, // 0f means loop full clip duration
    val backupYoutubeId: String = "",
    val backupStartSec: Float = 0f,
    val backupEndSec: Float = 0f
)

@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dayOfWeek: Int, // Default day 1..7
    val category: String, // STRENGTH, CARDIO, CARDIO_CORE, REST_WALK
    val defaultStartMinute: Int,
    val durationMinutes: Int,
    val warmupCues: String,
    val cooldownCues: String,
    val description: String
)

@Entity(
    tableName = "template_exercises",
    indices = [Index(value = ["templateId", "orderIndex"])]
)
data class TemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val defaultSeconds: Int = 0,
    val restSec: Int = 75,
    val isMainLift: Boolean = false,
    val isAccessory: Boolean = false,
    val note: String = ""
)

@Entity(
    tableName = "workout_sessions",
    indices = [Index(value = ["dateIso"]), Index(value = ["templateId"])]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: String,
    val templateName: String,
    val dateIso: String,
    val startEpochMs: Long,
    val endEpochMs: Long? = null,
    val rating: Int? = null,
    val note: String = "",
    val completed: Boolean = false,
    val stoppedForSafety: Boolean = false,
    val coachVerdict: String = ""
)

@Entity(
    tableName = "set_logs",
    indices = [Index(value = ["sessionId"]), Index(value = ["exerciseId"])]
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val setNo: Int,
    val weightKg: Double,
    val reps: Int,
    val seconds: Int,
    val effort: EffortLevel,
    val skippedReason: String? = null,
    val timestampEpochMs: Long
)

@Entity(tableName = "active_workout_state")
data class ActiveWorkoutStateEntity(
    @PrimaryKey val id: Int = 1,
    val sessionId: Long,
    val templateId: String,
    val currentExerciseIndex: Int = 0,
    val currentSetNo: Int = 1,
    val restEndEpochMs: Long? = null,
    val restTotalSec: Int = 75,
    val isPaused: Boolean = false,
    val accumulatedElapsedSec: Long = 0,
    val lastResumeEpochMs: Long = 0,
    val lastInteractionEpochMs: Long = 0,
    val plannedEndEpochMs: Long = 0,
    val dropLastAccessorySet: Boolean = false,
    val cutHalfSets: Boolean = false,
    val exerciseOverridesJson: String = "{}" // Maps original exerciseId -> swapped exerciseId
)

@Entity(tableName = "meal_slots")
data class MealSlotEntity(
    @PrimaryKey val slotKey: String,
    val title: String,
    val orderIndex: Int,
    val showPlateRule: Boolean,
    val guidance: String
)

@Entity(
    tableName = "meal_logs",
    indices = [Index(value = ["dateIso", "slotKey"], unique = true)]
)
data class MealLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateIso: String,
    val slotKey: String,
    val status: MealStatus = MealStatus.PENDING,
    val itemsCheckedCsv: String = "",
    val scoopedExtraOil: Boolean = false,
    val friedCount: Int = 0,
    val mishtiCount: Int = 0,
    val loggedAtEpochMs: Long = 0
)

@Entity(tableName = "water_logs")
data class WaterLogEntity(
    @PrimaryKey val dateIso: String,
    val glasses: Int = 0,
    val updatedAtEpochMs: Long = 0
)

@Entity(
    tableName = "weekly_checkins",
    indices = [Index(value = ["weekStartIso"], unique = true)]
)
data class WeeklyCheckinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStartIso: String,
    val dateIso: String,
    val waistCm: Double,
    val weightKg: Double? = null,
    val pushupBest: Int,
    val plankSec: Int,
    val mood: Int, // 1..5
    val note: String = ""
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val category: ReminderCategory,
    val dayOfWeek: Int,
    val triggerMinute: Int,
    val title: String,
    val body: String,
    val enabled: Boolean = true,
    val targetRef: String = ""
)

@Entity(tableName = "calendar_links")
data class CalendarLinkEntity(
    @PrimaryKey val externalKey: String,
    val calendarId: Long,
    val eventId: Long,
    val contentHash: Int
)
