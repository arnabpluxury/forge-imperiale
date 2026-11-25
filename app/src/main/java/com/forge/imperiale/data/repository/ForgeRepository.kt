package com.forge.imperiale.data.repository

import com.forge.imperiale.data.backup.ForgeBackupSnapshot
import com.forge.imperiale.data.local.ForgeDatabase
import com.forge.imperiale.data.local.entity.ActiveWorkoutStateEntity
import com.forge.imperiale.data.local.entity.ExerciseEntity
import com.forge.imperiale.data.local.entity.MealLogEntity
import com.forge.imperiale.data.local.entity.MealStatus
import com.forge.imperiale.data.local.entity.ScheduleBlockEntity
import com.forge.imperiale.data.local.entity.SetLogEntity
import com.forge.imperiale.data.local.entity.TemplateExerciseEntity
import com.forge.imperiale.data.local.entity.UserProfileEntity
import com.forge.imperiale.data.local.entity.WaterLogEntity
import com.forge.imperiale.data.local.entity.WeeklyCheckinEntity
import com.forge.imperiale.data.local.entity.WorkoutSessionEntity
import com.forge.imperiale.data.local.entity.WorkoutTemplateEntity
import com.forge.imperiale.data.preferences.ForgePreferencesRepository
import com.forge.imperiale.data.remote.MongoWireClient
import com.forge.imperiale.data.remote.ParsedMongoUri
import com.forge.imperiale.data.seed.ForgeSeedData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForgeRepository @Inject constructor(
    private val db: ForgeDatabase,
    private val prefs: ForgePreferencesRepository,
    private val gson: Gson
) {
    val profileFlow: Flow<UserProfileEntity?> = db.userProfileDao().observeProfile()
    val allScheduleBlocksFlow: Flow<List<ScheduleBlockEntity>> = db.scheduleDao().observeAllBlocks()
    val allExercisesFlow: Flow<List<ExerciseEntity>> = db.exerciseDao().observeAllExercises()
    val templatesFlow: Flow<List<WorkoutTemplateEntity>> = db.workoutDao().observeTemplates()
    val sessionsFlow: Flow<List<WorkoutSessionEntity>> = db.workoutDao().observeAllSessions()
    val allSetsFlow: Flow<List<SetLogEntity>> = db.workoutDao().observeAllSets()
    val activeWorkoutFlow: Flow<ActiveWorkoutStateEntity?> = db.workoutDao().observeActiveState()
    val mealSlotsFlow: Flow<List<MealSlotEntityRef>> get() = throw UnsupportedOperationException()
    val rawMealSlotsFlow = db.dietDao().observeMealSlots()
    val checkinsFlow: Flow<List<WeeklyCheckinEntity>> = db.progressDao().observeCheckins()

    private var seededInMemory = false

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (seededInMemory) return@withContext
        val existingProfile = db.userProfileDao().getProfile()
        if (existingProfile == null) {
            db.userProfileDao().upsertProfile(ForgeSeedData.defaultUserProfile())
        }
        val existingExercises = db.exerciseDao().getAllExercises()
        if (existingExercises.isEmpty()) {
            db.exerciseDao().upsertExercises(ForgeSeedData.defaultExercises())
        }
        val existingTemplates = db.workoutDao().getTemplates()
        if (existingTemplates.isEmpty()) {
            db.workoutDao().insertTemplates(ForgeSeedData.defaultTemplates())
            db.workoutDao().insertTemplateExercises(ForgeSeedData.defaultTemplateExercises())
        }
        val existingSlots = db.dietDao().getMealSlots()
        if (existingSlots.isEmpty()) {
            db.dietDao().insertMealSlots(ForgeSeedData.defaultMealSlots())
        }
        val existingBlocks = db.scheduleDao().getAllBlocks()
        if (existingBlocks.isEmpty()) {
            db.scheduleDao().insertBlocks(ForgeSeedData.defaultTuitionBlocks())
        }
        seededInMemory = true
    }

    suspend fun getProfileOrDefault(): UserProfileEntity {
        ensureSeeded()
        return db.userProfileDao().getProfile() ?: ForgeSeedData.defaultUserProfile()
    }

    suspend fun updateProfile(profile: UserProfileEntity) {
        db.userProfileDao().upsertProfile(profile)
    }

    // Schedule operations
    fun observeBlocksForDay(dayOfWeek: Int): Flow<List<ScheduleBlockEntity>> =
        db.scheduleDao().observeBlocksForDay(dayOfWeek)

    suspend fun getAllBlocks(): List<ScheduleBlockEntity> = db.scheduleDao().getAllBlocks()

    suspend fun saveBlock(block: ScheduleBlockEntity): Long = db.scheduleDao().insertBlock(block)

    suspend fun deleteBlock(id: Long) = db.scheduleDao().deleteBlockById(id)

    suspend fun replaceSchedulerBlocks(blocks: List<ScheduleBlockEntity>) =
        db.scheduleDao().replaceGeneratedSchedule(blocks)

    // Exercise & Video Manager operations
    suspend fun getExercise(id: String): ExerciseEntity? = db.exerciseDao().getExerciseById(id)

    suspend fun getAllExercises(): List<ExerciseEntity> = db.exerciseDao().getAllExercises()

    suspend fun updateExercise(exercise: ExerciseEntity) = db.exerciseDao().upsertExercise(exercise)

    // Workout & Set operations
    suspend fun getTemplates(): List<WorkoutTemplateEntity> = db.workoutDao().getTemplates()

    suspend fun getTemplate(id: String): WorkoutTemplateEntity? = db.workoutDao().getTemplateById(id)

    suspend fun getTemplateForDay(dayOfWeek: Int): WorkoutTemplateEntity? =
        db.workoutDao().getTemplateForDay(dayOfWeek)

    suspend fun getTemplateExercises(templateId: String): List<TemplateExerciseEntity> =
        db.workoutDao().getTemplateExercises(templateId)

    suspend fun getSessionsForDate(dateIso: String): List<WorkoutSessionEntity> =
        db.workoutDao().getSessionsForDate(dateIso)

    suspend fun getAllSessions(): List<WorkoutSessionEntity> = db.workoutDao().getAllSessions()

    suspend fun getSession(id: Long): WorkoutSessionEntity? = db.workoutDao().getSessionById(id)

    suspend fun startOrCreateSession(session: WorkoutSessionEntity): Long =
        db.workoutDao().insertSession(session)

    suspend fun updateSession(session: WorkoutSessionEntity) =
        db.workoutDao().updateSession(session)

    fun observeSetsForSession(sessionId: Long): Flow<List<SetLogEntity>> =
        db.workoutDao().observeSetsForSession(sessionId)

    suspend fun getSetsForSession(sessionId: Long): List<SetLogEntity> =
        db.workoutDao().getSetsForSession(sessionId)

    suspend fun getSetsForExercise(exerciseId: String): List<SetLogEntity> =
        db.workoutDao().getSetsForExercise(exerciseId)

    suspend fun logSet(setLog: SetLogEntity): Long = db.workoutDao().insertSetLog(setLog)

    suspend fun undoSet(setId: Long) = db.workoutDao().deleteSetLogById(setId)

    suspend fun getActiveWorkoutState(): ActiveWorkoutStateEntity? = db.workoutDao().getActiveState()

    suspend fun saveActiveWorkoutState(state: ActiveWorkoutStateEntity) =
        db.workoutDao().saveActiveState(state)

    suspend fun clearActiveWorkoutState() = db.workoutDao().clearActiveState()

    // Diet & Hydration operations
    fun observeMealLogsForDate(dateIso: String): Flow<List<MealLogEntity>> =
        db.dietDao().observeMealLogsForDate(dateIso)

    fun observeMealLogsBetween(startIso: String, endIso: String): Flow<List<MealLogEntity>> =
        db.dietDao().observeMealLogsBetween(startIso, endIso)

    suspend fun getMealLogsBetween(startIso: String, endIso: String): List<MealLogEntity> =
        db.dietDao().getMealLogsBetween(startIso, endIso)

    suspend fun saveMealLog(
        dateIso: String,
        slotKey: String,
        status: MealStatus,
        itemsChecked: List<String>,
        scoopedExtraOil: Boolean,
        friedCount: Int,
        mishtiCount: Int
    ) {
        val existing = db.dietDao().getMealLogsBetween(dateIso, dateIso)
            .firstOrNull { it.slotKey == slotKey }
        val entity = MealLogEntity(
            id = existing?.id ?: 0,
            dateIso = dateIso,
            slotKey = slotKey,
            status = status,
            itemsCheckedCsv = itemsChecked.joinToString("|"),
            scoopedExtraOil = scoopedExtraOil,
            friedCount = friedCount.coerceAtLeast(0),
            mishtiCount = mishtiCount.coerceAtLeast(0),
            loggedAtEpochMs = System.currentTimeMillis()
        )
        db.dietDao().upsertMealLog(entity)
    }

    fun observeWaterForDate(dateIso: String): Flow<WaterLogEntity?> =
        db.dietDao().observeWaterForDate(dateIso)

    suspend fun adjustWaterGlasses(dateIso: String, delta: Int) {
        val current = db.dietDao().getWaterForDate(dateIso)?.glasses ?: 0
        val next = (current + delta).coerceIn(0, 30)
        db.dietDao().upsertWaterLog(
            WaterLogEntity(
                dateIso = dateIso,
                glasses = next,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    // Weekly Check-ins
    suspend fun saveWeeklyCheckin(checkin: WeeklyCheckinEntity) {
        val existing = db.progressDao().getAllCheckins()
            .firstOrNull { it.weekStartIso == checkin.weekStartIso }
        db.progressDao().upsertCheckin(
            if (existing != null) checkin.copy(id = existing.id) else checkin
        )
    }

    suspend fun getAllCheckins(): List<WeeklyCheckinEntity> = db.progressDao().getAllCheckins()

    // JSON Backup & Restore (Section 7.9)
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val snapshot = createSnapshot()
        gson.toJson(snapshot)
    }

    suspend fun importBackupJson(json: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = gson.fromJson(json, ForgeBackupSnapshot::class.java)
                ?: error("Invalid Forge backup JSON")
            restoreSnapshot(snapshot)
        }
    }

    suspend fun resetProgram(newStartDateIso: String = LocalDate.now().toString()) = withContext(Dispatchers.IO) {
        db.workoutDao().clearActiveState()
        db.workoutDao().clearSetLogs()
        db.workoutDao().clearSessions()
        db.dietDao().clearMealLogs()
        db.dietDao().clearWaterLogs()
        db.progressDao().clearCheckins()
        db.scheduleDao().clearAll()
        db.exerciseDao().clearAll()
        db.workoutDao().clearTemplateExercises()
        seededInMemory = false
        db.userProfileDao().upsertProfile(
            ForgeSeedData.defaultUserProfile(newStartDateIso).copy(onboardingCompleted = true)
        )
        ensureSeeded()
    }

    // MongoDB Account Registration / Sign-In + Cloud Sync (Line 18)
    suspend fun registerOrLoginMongoAccount(
        mongoUri: String,
        username: String,
        passwordPlain: String,
        isRegistration: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = ParsedMongoUri.parse(mongoUri)
                ?: error("Enter a valid mongodb:// replica set connection URI.")
            val cleanUser = username.trim().lowercase()
            if (cleanUser.length < 3) error("Username must be at least 3 characters.")
            if (passwordPlain.length < 4) error("Password must be at least 4 characters.")

            val client = MongoWireClient(parsed)
            val passHash = sha256Hex("forge-salt:$cleanUser:$passwordPlain")
            val existing = client.findOneDocument("forge_accounts", mapOf("username" to cleanUser)).getOrThrow()

            if (isRegistration) {
                if (existing != null && existing["passwordHash"] != passHash) {
                    error("Username '$cleanUser' already exists with a different password.")
                }
                val profile = getProfileOrDefault()
                client.upsertDocument(
                    collection = "forge_accounts",
                    filter = mapOf("username" to cleanUser),
                    document = mapOf(
                        "username" to cleanUser,
                        "passwordHash" to passHash,
                        "displayName" to profile.name,
                        "updatedAtMs" to System.currentTimeMillis(),
                        "snapshotJson" to exportBackupJson()
                    )
                ).getOrThrow()
                val now = System.currentTimeMillis()
                prefs.setMongoAccount(mongoUri, cleanUser, profile.name, now)
                "Account '$cleanUser' synced to MongoDB (${parsed.database})."
            } else {
                if (existing == null) error("No Forge account found for '$cleanUser'. Tap Create & Sync first.")
                if (existing["passwordHash"] != passHash) error("Incorrect password for '$cleanUser'.")
                val snapJson = existing["snapshotJson"] as? String
                if (!snapJson.isNullOrBlank()) {
                    val snap = gson.fromJson(snapJson, ForgeBackupSnapshot::class.java)
                    if (snap != null) restoreSnapshot(snap)
                }
                val displayName = (existing["displayName"] as? String) ?: "Arnab"
                val now = System.currentTimeMillis()
                prefs.setMongoAccount(mongoUri, cleanUser, displayName, now)
                "Signed in as '$cleanUser' and restored cloud data."
            }
        }
    }

    suspend fun pushCloudSnapshot(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val currentSettings = prefs.settingsFlow.first()
            if (currentSettings.accountUsername.isBlank()) {
                error("Sign in to your MongoDB Forge account in Settings first.")
            }
            val parsed = ParsedMongoUri.parse(currentSettings.mongoUri)
                ?: error("MongoDB connection URI is missing or invalid.")
            val client = MongoWireClient(parsed)
            val now = System.currentTimeMillis()
            client.upsertDocument(
                collection = "forge_accounts",
                filter = mapOf("username" to currentSettings.accountUsername),
                document = mapOf(
                    "updatedAtMs" to now,
                    "snapshotJson" to exportBackupJson()
                )
            ).getOrThrow()
            prefs.markCloudSynced(now)
            "Synced all workouts, diet logs, check-ins, and schedule to MongoDB."
        }
    }

    private suspend fun createSnapshot(): ForgeBackupSnapshot {
        val customBlocks = db.scheduleDao().getAllBlocks().filter { it.source != "SCHEDULER" }
        return ForgeBackupSnapshot(
            schemaVersion = 1,
            exportedAtEpochMs = System.currentTimeMillis(),
            profile = db.userProfileDao().getProfile(),
            customScheduleBlocks = customBlocks,
            exercises = db.exerciseDao().getAllExercises(),
            sessions = db.workoutDao().getAllSessions(),
            setLogs = db.workoutDao().getAllSets(),
            mealLogs = db.dietDao().getAllMealLogs(),
            waterLogs = db.dietDao().getAllWaterLogs(),
            weeklyCheckins = db.progressDao().getAllCheckins()
        )
    }

    private suspend fun restoreSnapshot(snapshot: ForgeBackupSnapshot) {
        ensureSeeded()
        snapshot.profile?.let { db.userProfileDao().upsertProfile(it) }
        if (snapshot.exercises.isNotEmpty()) {
            db.exerciseDao().upsertExercises(snapshot.exercises)
        }
        if (snapshot.customScheduleBlocks.isNotEmpty()) {
            db.scheduleDao().clearAll()
            db.scheduleDao().insertBlocks(snapshot.customScheduleBlocks)
        }
        db.workoutDao().clearSetLogs()
        db.workoutDao().clearSessions()
        if (snapshot.sessions.isNotEmpty()) db.workoutDao().insertSessions(snapshot.sessions)
        if (snapshot.setLogs.isNotEmpty()) db.workoutDao().insertSetLogs(snapshot.setLogs)
        db.dietDao().clearMealLogs()
        db.dietDao().clearWaterLogs()
        if (snapshot.mealLogs.isNotEmpty()) db.dietDao().insertMealLogs(snapshot.mealLogs)
        if (snapshot.waterLogs.isNotEmpty()) db.dietDao().insertWaterLogs(snapshot.waterLogs)
        db.progressDao().clearCheckins()
        if (snapshot.weeklyCheckins.isNotEmpty()) db.progressDao().insertCheckins(snapshot.weeklyCheckins)
    }

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

typealias MealSlotEntityRef = com.forge.imperiale.data.local.entity.MealSlotEntity
