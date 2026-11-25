package com.forge.imperiale.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.forge.imperiale.data.local.ForgeDatabase
import com.forge.imperiale.data.local.entity.BlockType
import com.forge.imperiale.data.local.entity.CalendarLinkEntity
import com.forge.imperiale.data.local.entity.ScheduleBlockEntity
import com.forge.imperiale.data.preferences.ForgePreferencesRepository
import com.forge.imperiale.notifications.AlarmScheduler
import com.forge.imperiale.scheduler.SchedulerEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

data class PhoneCalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String
)

/**
 * Two-way Phone Calendar Integration (Section 8.2):
 * 1. Creates a dedicated local calendar named `"Arnab Coach"` (`ACCOUNT_TYPE_LOCAL`) and syncs
 *    placed workouts, meals, sleep wind-down, and tuition blocks with stable external keys so
 *    nothing ever duplicates and deleted items are cleaned up.
 * 2. Reads chosen external phone calendars for upcoming 7 days and imports busy events (exams,
 *    extra classes) as locked `CALENDAR_BUSY` blocks.
 * 3. Orchestrates full week generation + exact alarm scheduling + conflict notifications.
 */
@Singleton
class CalendarAndScheduleOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ForgeDatabase,
    private val prefs: ForgePreferencesRepository,
    private val alarmScheduler: AlarmScheduler
) {
    companion object {
        const val COACH_CALENDAR_NAME = "Arnab Coach"
        private const val LOCAL_ACCOUNT_NAME = "ForgeLocalCoach"
    }

    fun hasCalendarPermissions(): Boolean {
        val readOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readOk && writeOk
    }

    suspend fun listExternalCalendars(): List<PhoneCalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermissions()) return@withContext emptyList()
        val list = mutableListOf<PhoneCalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1) ?: "Calendar"
                    val acct = cursor.getString(2) ?: ""
                    if (name != COACH_CALENDAR_NAME && acct != LOCAL_ACCOUNT_NAME) {
                        list += PhoneCalendarInfo(id = id, displayName = name, accountName = acct)
                    }
                }
            }
        }
        list
    }

    /**
     * Recomputes the entire 7-day schedule around locked Tuition + User + External Busy Calendar blocks,
     * persists generated blocks & reminders, schedules exact alarms, and syncs `"Arnab Coach"` calendar if enabled.
     */
    suspend fun rebuildScheduleAndSync(notifyOnCalendarConflict: Boolean = false): List<String> =
        withContext(Dispatchers.IO) {
            val settings = prefs.settingsFlow.first()

            // Step 1: Refresh external busy blocks if calendar sync + permissions are active
            if (settings.syncToPhoneCalendar && hasCalendarPermissions() && settings.monitoredCalendarIds.isNotEmpty()) {
                val busyBlocks = readExternalBusyBlocks(settings.monitoredCalendarIds)
                db.scheduleDao().deleteBlocksBySource("CALENDAR_BUSY")
                if (busyBlocks.isNotEmpty()) {
                    db.scheduleDao().insertBlocks(busyBlocks)
                }
            }

            // Step 2: Run pure SchedulerEngine for all 7 days
            val lockedBlocks = db.scheduleDao().getLockedBlocks()
            val templates = db.workoutDao().getTemplates()
            val weekPlan = SchedulerEngine.generateWeekPlan(
                allLockedBlocks = lockedBlocks,
                templates = templates,
                wakeMinutesByDay = settings.wakeMinutesMap(),
                sleepTargetMinutes = settings.sleepTargetMinutes
            )

            val generatedBlocks = weekPlan.values.flatMap { it.placedBlocks }
            val generatedReminders = weekPlan.values.flatMap { it.reminders }
            val allAdjustments = weekPlan.values.flatMap { it.adjustments }

            db.scheduleDao().replaceGeneratedSchedule(generatedBlocks)
            db.systemSyncDao().clearReminders()
            db.systemSyncDao().replaceReminders(generatedReminders)

            // Step 3: Schedule exact / fallback alarms
            alarmScheduler.scheduleWeeklyReminders(generatedReminders, settings)

            // Step 4: Sync to 'Arnab Coach' phone calendar if enabled
            if (settings.syncToPhoneCalendar && hasCalendarPermissions()) {
                val allBlocksToSync = (lockedBlocks.filter { it.source != "CALENDAR_BUSY" } + generatedBlocks)
                syncToArnabCoachCalendar(allBlocksToSync)
            }

            val reasonStrings = allAdjustments.map { it.reason }
            if (notifyOnCalendarConflict && reasonStrings.isNotEmpty()) {
                alarmScheduler.postImmediateCoachNotification(
                    title = "Schedule Adjusted Around Busy Block",
                    body = reasonStrings.first()
                )
            }
            reasonStrings
        }

    private fun readExternalBusyBlocks(monitoredIds: Set<String>): List<ScheduleBlockEntity> {
        val idsLong = monitoredIds.mapNotNull { it.toLongOrNull() }
        if (idsLong.isEmpty()) return emptyList()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val startMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = today.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMs)
        ContentUris.appendId(builder, endMs)

        val projection = arrayOf(
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        )

        val result = mutableListOf<ScheduleBlockEntity>()
        runCatching {
            context.contentResolver.query(builder.build(), projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val calId = c.getLong(0)
                    if (calId !in idsLong) continue
                    val allDay = c.getInt(5) == 1
                    if (allDay) continue
                    val eventId = c.getLong(1)
                    val title = (c.getString(2) ?: "Busy Event").trim()
                    val beginMs = c.getLong(3)
                    val finishMs = c.getLong(4)

                    val startZdt = Instant.ofEpochMilli(beginMs).atZone(zone)
                    val endZdt = Instant.ofEpochMilli(finishMs).atZone(zone)
                    val startMin = startZdt.hour * 60 + startZdt.minute
                    val endMin = (endZdt.hour * 60 + endZdt.minute).coerceAtLeast(startMin + 15)

                    result += ScheduleBlockEntity(
                        type = BlockType.SCHOOL,
                        dayOfWeek = startZdt.dayOfWeek.value,
                        startMinute = startMin,
                        endMinute = endMin.coerceAtMost(23 * 60 + 55),
                        title = title,
                        subtitle = "Phone Calendar Busy Event",
                        locked = true,
                        source = "CALENDAR_BUSY",
                        calendarEventId = eventId
                    )
                }
            }
        }
        return result
    }

    private suspend fun syncToArnabCoachCalendar(blocks: List<ScheduleBlockEntity>) {
        val calId = ensureCoachCalendarId() ?: return
        val existingLinks = db.systemSyncDao().getAllCalendarLinks().associateBy { it.externalKey }
        val activeKeys = mutableSetOf<String>()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        for (block in blocks) {
            val key = "${block.dayOfWeek}_${block.type.name}_${block.refId ?: block.title}_${block.startMinute}"
            activeKeys += key
            val hash = Triple(block.title, block.startMinute, block.endMinute).hashCode()

            val targetDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(block.dayOfWeek.coerceIn(1, 7))))
            val startEpoch = targetDate.atStartOfDay(zone).plusMinutes(block.startMinute.toLong()).toInstant().toEpochMilli()
            val endEpoch = targetDate.atStartOfDay(zone).plusMinutes(block.endMinute.toLong()).toInstant().toEpochMilli()

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calId)
                put(CalendarContract.Events.TITLE, "[Forge] ${block.title}")
                put(CalendarContract.Events.DESCRIPTION, block.subtitle)
                put(CalendarContract.Events.DTSTART, startEpoch)
                put(CalendarContract.Events.DTEND, endEpoch)
                put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            }

            val existing = existingLinks[key]
            if (existing != null) {
                if (existing.contentHash != hash) {
                    val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing.eventId)
                    val updated = runCatching { context.contentResolver.update(eventUri, values, null, null) }.getOrDefault(0)
                    if (updated == 0) {
                        insertCalendarEvent(calId, key, hash, values)
                    } else {
                        db.systemSyncDao().upsertCalendarLink(existing.copy(contentHash = hash))
                    }
                }
            } else {
                insertCalendarEvent(calId, key, hash, values)
            }
        }

        // Delete stale events no longer in the schedule
        for ((key, link) in existingLinks) {
            if (key !in activeKeys) {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, link.eventId)
                runCatching { context.contentResolver.delete(uri, null, null) }
                db.systemSyncDao().deleteCalendarLink(key)
            }
        }
    }

    private suspend fun insertCalendarEvent(calId: Long, key: String, hash: Int, values: ContentValues) {
        val insertedUri = runCatching {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        }.getOrNull()
        val eventId = insertedUri?.lastPathSegment?.toLongOrNull() ?: return
        db.systemSyncDao().upsertCalendarLink(
            CalendarLinkEntity(
                externalKey = key,
                calendarId = calId,
                eventId = eventId,
                contentHash = hash
            )
        )
    }

    private fun ensureCoachCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} = ?"
        val args = arrayOf(LOCAL_ACCOUNT_NAME, COACH_CALENDAR_NAME)

        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                args,
                null
            )?.use { c ->
                if (c.moveToFirst()) return c.getLong(0)
            }
        }

        val syncAdapterUri: Uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, COACH_CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, COACH_CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFFD2E823.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        val uri = runCatching { context.contentResolver.insert(syncAdapterUri, values) }.getOrNull()
        return uri?.lastPathSegment?.toLongOrNull()
    }
}
