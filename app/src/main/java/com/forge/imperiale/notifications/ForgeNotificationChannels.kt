package com.forge.imperiale.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.provider.Settings
import com.forge.imperiale.R

object ForgeNotificationChannels {
    const val CHANNEL_WORKOUT = "forge_workout"
    const val CHANNEL_MEALS = "forge_meals"
    const val CHANNEL_SLEEP = "forge_sleep"
    const val CHANNEL_COACH = "forge_coach"
    const val CHANNEL_REST_TIMER = "forge_rest_timer"

    fun ensureCreated(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val workout = NotificationChannel(
            CHANNEL_WORKOUT,
            context.getString(R.string.channel_workout),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            description = "Scheduled workouts, start/snooze/skip actions, and session check-ins"
        }

        val restTimer = NotificationChannel(
            CHANNEL_REST_TIMER,
            "Rest Timer Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 350, 150, 350)
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, alarmAttrs)
            description = "Fires haptic and sound when between-set rest reaches zero, even with screen off"
        }

        val meals = NotificationChannel(
            CHANNEL_MEALS,
            context.getString(R.string.channel_meals),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableVibration(true)
            description = "Meal timing and hydration reminders"
        }

        val sleep = NotificationChannel(
            CHANNEL_SLEEP,
            context.getString(R.string.channel_sleep),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Phone-down wind-down and 8.5h sleep protection reminders"
        }

        val coach = NotificationChannel(
            CHANNEL_COACH,
            context.getString(R.string.channel_coach),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            description = "Calendar conflict notifications and strict coach updates"
        }

        nm.createNotificationChannels(listOf(workout, restTimer, meals, sleep, coach))
    }
}
