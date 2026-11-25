package com.forge.imperiale.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.forge.imperiale.MainActivity
import com.forge.imperiale.R
import com.forge.imperiale.data.local.ForgeDatabase
import com.forge.imperiale.data.local.entity.BlockType
import com.forge.imperiale.data.local.entity.ReminderCategory
import com.forge.imperiale.data.local.entity.ReminderEntity
import com.forge.imperiale.data.preferences.ForgeAppSettings
import com.forge.imperiale.data.preferences.ForgePreferencesRepository
import com.forge.imperiale.ui.components.LinktreeColorBlockHero
import com.forge.imperiale.ui.components.LinktreeOutlinePillButton
import com.forge.imperiale.ui.components.LinktreePillButton
import com.forge.imperiale.ui.theme.ForgePalette
import com.forge.imperiale.ui.theme.ForgeTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canUseExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    fun scheduleWeeklyReminders(
        reminders: List<ReminderEntity>,
        settings: ForgeAppSettings
    ) {
        // Cancel existing reminder slots first
        for (day in 1..7) {
            for (slot in 0..12) {
                val reqCode = 10_000 + day * 100 + slot
                val pi = PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    Intent(context, AlarmReceiver::class.java),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pi != null) alarmManager.cancel(pi)
            }
        }

        val byDay = reminders.groupBy { it.dayOfWeek }
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()

        byDay.forEach { (day, dayReminders) ->
            dayReminders.take(8).forEachIndexed { idx, reminder ->
                val categoryEnabled = when (reminder.category) {
                    ReminderCategory.WORKOUT -> settings.workoutAlarmsEnabled
                    ReminderCategory.MEALS -> settings.mealAlarmsEnabled
                    ReminderCategory.SLEEP -> settings.sleepAlarmsEnabled
                    ReminderCategory.COACH -> settings.coachAlarmsEnabled
                }
                if (!reminder.enabled || !categoryEnabled) return@forEachIndexed

                val triggerEpochMs = computeNextOccurrenceEpochMs(now, zone, day, reminder.triggerMinute)
                val reqCode = 10_000 + day * 100 + idx
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra(EXTRA_REMINDER_ID, reminder.id)
                    putExtra(EXTRA_CATEGORY, reminder.category.name)
                    putExtra(EXTRA_TITLE, reminder.title)
                    putExtra(EXTRA_BODY, reminder.body)
                    putExtra(EXTRA_TARGET_REF, reminder.targetRef)
                    putExtra(EXTRA_DAY_OF_WEEK, day)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setExactOrFallback(triggerEpochMs, pi)
            }

            // Optional Full-Screen Wake-Up Alarm
            if (settings.fullScreenWakeAlarm) {
                val wakeMin = settings.wakeMinutesMap()[day] ?: (6 * 60 + 30)
                val wakeEpochMs = computeNextOccurrenceEpochMs(now, zone, day, wakeMin)
                val wakeReqCode = 20_000 + day
                val wakeIntent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra(EXTRA_REMINDER_ID, "wake_$day")
                    putExtra(EXTRA_CATEGORY, "WAKE_ALARM")
                    putExtra(EXTRA_TITLE, "Good Morning, Arnab")
                    putExtra(EXTRA_BODY, "Drink 1-2 glasses of water and check today's tuition + training plan.")
                    putExtra(EXTRA_DAY_OF_WEEK, day)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    wakeReqCode,
                    wakeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setExactOrFallback(wakeEpochMs, pi)
            }
        }
    }

    fun scheduleRestTimerAlarm(endEpochMs: Long, nextExerciseName: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, "rest_timer_zero")
            putExtra(EXTRA_CATEGORY, "REST_TIMER")
            putExtra(EXTRA_TITLE, "Rest Complete — Next Set!")
            putExtra(EXTRA_BODY, "Back on the bar/dumbbells: $nextExerciseName")
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REST_TIMER_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExactOrFallback(endEpochMs, pi)
    }

    fun cancelRestTimerAlarm() {
        val pi = PendingIntent.getBroadcast(
            context,
            REST_TIMER_REQ_CODE,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) alarmManager.cancel(pi)
    }

    fun scheduleSnooze(title: String, body: String, category: String, targetRef: String, minutes: Int = 10) {
        val triggerMs = System.currentTimeMillis() + minutes * 60_000L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, "snooze_${System.currentTimeMillis()}")
            putExtra(EXTRA_CATEGORY, category)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_TARGET_REF, targetRef)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            SNOOZE_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExactOrFallback(triggerMs, pi)
    }

    fun postImmediateCoachNotification(title: String, body: String) {
        postNotification(
            context = context,
            notificationId = (System.currentTimeMillis() % 50_000).toInt() + 40_000,
            channelId = ForgeNotificationChannels.CHANNEL_COACH,
            title = title,
            body = body,
            isWorkout = false,
            targetRef = ""
        )
    }

    private fun setExactOrFallback(triggerEpochMs: Long, pendingIntent: PendingIntent) {
        try {
            if (canUseExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMs,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMs,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerEpochMs,
                pendingIntent
            )
        }
    }

    private fun computeNextOccurrenceEpochMs(
        now: LocalDateTime,
        zone: ZoneId,
        isoDayOfWeek: Int,
        minuteOfDay: Int
    ): Long {
        val targetDow = DayOfWeek.of(isoDayOfWeek.coerceIn(1, 7))
        val h = (minuteOfDay / 60).coerceIn(0, 23)
        val m = (minuteOfDay % 60).coerceIn(0, 59)
        var candidate = now.with(TemporalAdjusters.nextOrSame(targetDow))
            .withHour(h)
            .withMinute(m)
            .withSecond(0)
            .withNano(0)
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1)
        }
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }

    companion object {
        const val REST_TIMER_REQ_CODE = 30_001
        const val SNOOZE_REQ_CODE = 30_002
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_TARGET_REF = "extra_target_ref"
        const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"

        fun postNotification(
            context: Context,
            notificationId: Int,
            channelId: String,
            title: String,
            body: String,
            isWorkout: Boolean,
            targetRef: String,
            fullScreenWake: Boolean = false
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            ForgeNotificationChannels.ensureCreated(context)
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", if (isWorkout) "workout" else "today")
                putExtra("template_id", targetRef)
            }
            val openPi = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            if (fullScreenWake) {
                val wakeIntent = Intent(context, WakeUpAlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val wakePi = PendingIntent.getActivity(
                    context,
                    notificationId + 900,
                    wakeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setFullScreenIntent(wakePi, true)
            }

            // Section 8.3: Actions on workout notifications: Start, Snooze 10 min, Skip
            if (isWorkout) {
                builder.addAction(0, "Start", openPi)

                val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_SNOOZE_10M
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_BODY, body)
                    putExtra(EXTRA_TARGET_REF, targetRef)
                }
                val snoozePi = PendingIntent.getBroadcast(
                    context,
                    notificationId + 1,
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "Snooze 10 min", snoozePi)

                val skipIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_SKIP_WORKOUT
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(EXTRA_TARGET_REF, targetRef)
                }
                val skipPi = PendingIntent.getBroadcast(
                    context,
                    notificationId + 2,
                    skipIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "Skip", skipPi)
            }

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notificationId, builder.build())
        }

        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var db: ForgeDatabase
    @Inject lateinit var prefs: ForgePreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val category = intent.getStringExtra(AlarmScheduler.EXTRA_CATEGORY) ?: "COACH"
                val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Forge Coach"
                val body = intent.getStringExtra(AlarmScheduler.EXTRA_BODY) ?: ""
                val targetRef = intent.getStringExtra(AlarmScheduler.EXTRA_TARGET_REF) ?: ""

                // Rest timer always fires (even if in quiet hours) because user is actively mid-workout
                if (category == "REST_TIMER") {
                    AlarmScheduler.postNotification(
                        context = context,
                        notificationId = 8801,
                        channelId = ForgeNotificationChannels.CHANNEL_REST_TIMER,
                        title = title,
                        body = body,
                        isWorkout = false,
                        targetRef = targetRef
                    )
                    return@launch
                }

                // Enforce Quiet Rules (Section 8.3): Never notify during tuition blocks or sleep quiet hours
                val now = LocalDateTime.now()
                val nowMin = now.hour * 60 + now.minute
                val dow = now.dayOfWeek.value
                val settings = prefs.settingsFlow.first()

                if (category != "WAKE_ALARM") {
                    val inQuietHours = if (settings.quietHoursStartMin > settings.quietHoursEndMin) {
                        nowMin >= settings.quietHoursStartMin || nowMin < settings.quietHoursEndMin
                    } else {
                        nowMin in settings.quietHoursStartMin..settings.quietHoursEndMin
                    }
                    val todayBlocks = db.scheduleDao().getAllBlocks().filter { it.dayOfWeek == dow }
                    val inTuition = todayBlocks.any {
                        it.type == BlockType.TUITION && nowMin in it.startMinute..it.endMinute
                    }
                    if (inQuietHours || inTuition) return@launch
                }

                val channel = when (category) {
                    "WORKOUT" -> ForgeNotificationChannels.CHANNEL_WORKOUT
                    "MEALS" -> ForgeNotificationChannels.CHANNEL_MEALS
                    "SLEEP", "WAKE_ALARM" -> ForgeNotificationChannels.CHANNEL_SLEEP
                    else -> ForgeNotificationChannels.CHANNEL_COACH
                }
                AlarmScheduler.postNotification(
                    context = context,
                    notificationId = (title.hashCode() and 0x7FFF) + 5000,
                    channelId = channel,
                    title = title,
                    body = body,
                    isWorkout = category == "WORKOUT",
                    targetRef = targetRef,
                    fullScreenWake = category == "WAKE_ALARM"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(AlarmScheduler.EXTRA_NOTIFICATION_ID, -1)
        if (notifId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notifId)
        }
        when (intent.action) {
            ACTION_SNOOZE_10M -> {
                val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Workout Session"
                val body = intent.getStringExtra(AlarmScheduler.EXTRA_BODY) ?: "Session ready. Shoes on."
                val targetRef = intent.getStringExtra(AlarmScheduler.EXTRA_TARGET_REF) ?: ""
                alarmScheduler.scheduleSnooze(title, body, "WORKOUT", targetRef, 10)
            }
            ACTION_SKIP_WORKOUT -> {
                alarmScheduler.postImmediateCoachNotification(
                    title = "Session Skipped",
                    body = "Skipped today's slot. Never double up tomorrow — stay on schedule."
                )
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE_10M = "com.forge.imperiale.ACTION_SNOOZE_10M"
        const val ACTION_SKIP_WORKOUT = "com.forge.imperiale.ACTION_SKIP_WORKOUT"
    }
}

@AndroidEntryPoint
class BootAndTimeReceiver : BroadcastReceiver() {

    @Inject lateinit var db: ForgeDatabase
    @Inject lateinit var prefs: ForgePreferencesRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val reminders = db.systemSyncDao().getAllReminders()
                val settings = prefs.settingsFlow.first()
                if (reminders.isNotEmpty()) {
                    alarmScheduler.scheduleWeeklyReminders(reminders, settings)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

@AndroidEntryPoint
class WakeUpAlarmActivity : ComponentActivity() {

    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ForgeTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ForgePalette.TrueBlack)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    LinktreeColorBlockHero(
                        eyebrow = "FORGE WAKE-UP • ASIA/KOLKATA",
                        headline = "UP & HYDRATE, ARNAB.",
                        badgeText = "MORNING START",
                        subtitle = "Drink 1-2 glasses of water now. Breakfast & study blocks are lined up."
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    LinktreePillButton(
                        text = "I'm Awake — Open Today Plan",
                        onClick = {
                            startActivity(Intent(this@WakeUpAlarmActivity, MainActivity::class.java))
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        trailingArrow = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinktreeOutlinePillButton(
                        text = "Snooze 10 Minutes",
                        onClick = {
                            alarmScheduler.scheduleSnooze(
                                title = "Wake Up, Arnab",
                                body = "Time to get up and drink water.",
                                category = "WAKE_ALARM",
                                targetRef = "",
                                minutes = 10
                            )
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
