package com.forge.imperiale.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.forge.imperiale.BuildConfig
import com.forge.imperiale.ui.theme.ForgeThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.forgeDataStore: DataStore<Preferences> by preferencesDataStore(name = "forge_settings")

data class ForgeAppSettings(
    val themeMode: ForgeThemeMode = ForgeThemeMode.AMOLED_DARK,
    val unitLabel: String = "kg",
    val weightStepKg: Double = 1.0,
    val defaultRestSec: Int = 75,
    val weekdayWakeMin: Int = 6 * 60 + 30, // 06:30
    val wedFriWakeMin: Int = 6 * 60 + 45,  // 06:45 (before 08:00 SST)
    val weekendWakeMin: Int = 7 * 60 + 30, // 07:30
    val sleepTargetMinutes: Int = 8 * 60 + 30, // 8.5 hours
    val workoutAlarmsEnabled: Boolean = true,
    val mealAlarmsEnabled: Boolean = true,
    val sleepAlarmsEnabled: Boolean = true,
    val coachAlarmsEnabled: Boolean = true,
    val fullScreenWakeAlarm: Boolean = false,
    val quietHoursStartMin: Int = 22 * 60,
    val quietHoursEndMin: Int = 6 * 60 + 30,
    val syncToPhoneCalendar: Boolean = false,
    val monitoredCalendarIds: Set<String> = emptySet(),
    val progressPhotosEnabled: Boolean = false,
    val mongoUri: String = BuildConfig.DEFAULT_MONGO_URI,
    val accountUsername: String = "",
    val accountDisplayName: String = "Arnab",
    val lastCloudSyncEpochMs: Long = 0L
) {
    fun wakeMinutesMap(): Map<Int, Int> = mapOf(
        1 to weekdayWakeMin,
        2 to weekdayWakeMin,
        3 to wedFriWakeMin,
        4 to weekdayWakeMin,
        5 to wedFriWakeMin,
        6 to weekendWakeMin,
        7 to weekendWakeMin
    )
}

@Singleton
class ForgePreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val UNIT_LABEL = stringPreferencesKey("unit_label")
        val WEIGHT_STEP_KG = doublePreferencesKey("weight_step_kg")
        val DEFAULT_REST_SEC = intPreferencesKey("default_rest_sec")
        val WEEKDAY_WAKE = intPreferencesKey("weekday_wake_min")
        val WED_FRI_WAKE = intPreferencesKey("wed_fri_wake_min")
        val WEEKEND_WAKE = intPreferencesKey("weekend_wake_min")
        val SLEEP_TARGET = intPreferencesKey("sleep_target_min")
        val ALARM_WORKOUT = booleanPreferencesKey("alarm_workout")
        val ALARM_MEALS = booleanPreferencesKey("alarm_meals")
        val ALARM_SLEEP = booleanPreferencesKey("alarm_sleep")
        val ALARM_COACH = booleanPreferencesKey("alarm_coach")
        val FULLSCREEN_WAKE = booleanPreferencesKey("fullscreen_wake")
        val QUIET_START = intPreferencesKey("quiet_start_min")
        val QUIET_END = intPreferencesKey("quiet_end_min")
        val CALENDAR_SYNC = booleanPreferencesKey("calendar_sync")
        val MONITORED_CAL_IDS = stringSetPreferencesKey("monitored_cal_ids")
        val PROGRESS_PHOTOS = booleanPreferencesKey("progress_photos_enabled")
        val MONGO_URI = stringPreferencesKey("mongo_uri")
        val ACCOUNT_USERNAME = stringPreferencesKey("account_username")
        val ACCOUNT_DISPLAY = stringPreferencesKey("account_display_name")
        val LAST_CLOUD_SYNC = longPreferencesKey("last_cloud_sync_ms")
    }

    val settingsFlow: Flow<ForgeAppSettings> = context.forgeDataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ForgeThemeMode.AMOLED_DARK.name
        val theme = runCatching { ForgeThemeMode.valueOf(themeStr) }.getOrDefault(ForgeThemeMode.AMOLED_DARK)
        val storedUri = prefs[Keys.MONGO_URI]?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_MONGO_URI
        ForgeAppSettings(
            themeMode = theme,
            unitLabel = prefs[Keys.UNIT_LABEL] ?: "kg",
            weightStepKg = prefs[Keys.WEIGHT_STEP_KG] ?: 1.0,
            defaultRestSec = prefs[Keys.DEFAULT_REST_SEC] ?: 75,
            weekdayWakeMin = prefs[Keys.WEEKDAY_WAKE] ?: (6 * 60 + 30),
            wedFriWakeMin = prefs[Keys.WED_FRI_WAKE] ?: (6 * 60 + 45),
            weekendWakeMin = prefs[Keys.WEEKEND_WAKE] ?: (7 * 60 + 30),
            sleepTargetMinutes = prefs[Keys.SLEEP_TARGET] ?: (8 * 60 + 30),
            workoutAlarmsEnabled = prefs[Keys.ALARM_WORKOUT] ?: true,
            mealAlarmsEnabled = prefs[Keys.ALARM_MEALS] ?: true,
            sleepAlarmsEnabled = prefs[Keys.ALARM_SLEEP] ?: true,
            coachAlarmsEnabled = prefs[Keys.ALARM_COACH] ?: true,
            fullScreenWakeAlarm = prefs[Keys.FULLSCREEN_WAKE] ?: false,
            quietHoursStartMin = prefs[Keys.QUIET_START] ?: (22 * 60),
            quietHoursEndMin = prefs[Keys.QUIET_END] ?: (6 * 60 + 30),
            syncToPhoneCalendar = prefs[Keys.CALENDAR_SYNC] ?: false,
            monitoredCalendarIds = prefs[Keys.MONITORED_CAL_IDS] ?: emptySet(),
            progressPhotosEnabled = prefs[Keys.PROGRESS_PHOTOS] ?: false,
            mongoUri = storedUri,
            accountUsername = prefs[Keys.ACCOUNT_USERNAME] ?: "",
            accountDisplayName = prefs[Keys.ACCOUNT_DISPLAY] ?: "Arnab",
            lastCloudSyncEpochMs = prefs[Keys.LAST_CLOUD_SYNC] ?: 0L
        )
    }

    suspend fun setThemeMode(mode: ForgeThemeMode) {
        context.forgeDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setWeightStep(stepKg: Double) {
        context.forgeDataStore.edit { it[Keys.WEIGHT_STEP_KG] = stepKg.coerceIn(0.25, 10.0) }
    }

    suspend fun setDefaultRestSec(sec: Int) {
        context.forgeDataStore.edit { it[Keys.DEFAULT_REST_SEC] = sec.coerceIn(45, 180) }
    }

    suspend fun setWakeTimes(weekdayMin: Int, wedFriMin: Int, weekendMin: Int) {
        context.forgeDataStore.edit {
            it[Keys.WEEKDAY_WAKE] = weekdayMin
            it[Keys.WED_FRI_WAKE] = wedFriMin
            it[Keys.WEEKEND_WAKE] = weekendMin
        }
    }

    suspend fun setAlarmToggles(workout: Boolean, meals: Boolean, sleep: Boolean, coach: Boolean, fullScreenWake: Boolean) {
        context.forgeDataStore.edit {
            it[Keys.ALARM_WORKOUT] = workout
            it[Keys.ALARM_MEALS] = meals
            it[Keys.ALARM_SLEEP] = sleep
            it[Keys.ALARM_COACH] = coach
            it[Keys.FULLSCREEN_WAKE] = fullScreenWake
        }
    }

    suspend fun setCalendarSync(enabled: Boolean, monitoredIds: Set<String>? = null) {
        context.forgeDataStore.edit {
            it[Keys.CALENDAR_SYNC] = enabled
            if (monitoredIds != null) {
                it[Keys.MONITORED_CAL_IDS] = monitoredIds
            }
        }
    }

    suspend fun setProgressPhotosEnabled(enabled: Boolean) {
        context.forgeDataStore.edit { it[Keys.PROGRESS_PHOTOS] = enabled }
    }

    suspend fun setMongoAccount(uri: String, username: String, displayName: String, syncedAtMs: Long? = null) {
        context.forgeDataStore.edit {
            if (uri.isNotBlank()) it[Keys.MONGO_URI] = uri.trim()
            it[Keys.ACCOUNT_USERNAME] = username.trim()
            it[Keys.ACCOUNT_DISPLAY] = displayName.trim().ifBlank { "Arnab" }
            if (syncedAtMs != null) it[Keys.LAST_CLOUD_SYNC] = syncedAtMs
        }
    }

    suspend fun markCloudSynced(timestampMs: Long) {
        context.forgeDataStore.edit { it[Keys.LAST_CLOUD_SYNC] = timestampMs }
    }
}
