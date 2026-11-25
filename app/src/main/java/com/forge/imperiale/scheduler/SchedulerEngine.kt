package com.forge.imperiale.scheduler

import com.forge.imperiale.data.local.entity.BlockType
import com.forge.imperiale.data.local.entity.ReminderCategory
import com.forge.imperiale.data.local.entity.ReminderEntity
import com.forge.imperiale.data.local.entity.ScheduleBlockEntity
import com.forge.imperiale.data.local.entity.WorkoutTemplateEntity
import kotlin.math.abs

data class ScheduleAdjustment(
    val dayOfWeek: Int,
    val itemTitle: String,
    val requestedStartMin: Int,
    val placedStartMin: Int,
    val reason: String
)

data class DayScheduleInput(
    val dayOfWeek: Int, // 1=Mon .. 7=Sun
    val nextDayOfWeek: Int = if (dayOfWeek == 7) 1 else dayOfWeek + 1,
    val lockedBlocks: List<ScheduleBlockEntity>,
    val template: WorkoutTemplateEntity?,
    val wakeMinute: Int,
    val nextDayWakeMinute: Int,
    val sleepTargetMinutes: Int = 510 // 8.5 hours default; never cut below 480 (8.0h)
)

data class DayScheduleOutput(
    val placedBlocks: List<ScheduleBlockEntity>,
    val reminders: List<ReminderEntity>,
    val adjustments: List<ScheduleAdjustment>,
    val bedtimeMinute: Int,
    val windDownMinute: Int
)

data class MissedWorkoutDecision(
    val movedToDayOfWeek: Int?,
    val placedStartMin: Int?,
    val skipped: Boolean,
    val explanation: String
)

/**
 * Pure, deterministic Scheduler Engine (Section 8.1).
 *
 * Enforces:
 * 1. Never overlap any locked block (TUITION, SCHOOL, CALENDAR_BUSY).
 * 2. 15-minute buffer before and after every TUITION block.
 * 3. Protect >= 8 hours of sleep (default 8.5h target); bedtime = nextDayWake - sleepTarget.
 * 4. Respect ~3.5-5h spacing between main meals; Wed/Fri breakfast finished before 08:00 class;
 *    Mon/Thu dinner at 21:45 after Maths; Sat/Sun dinner at 19:30 before 20:00 Bengali.
 * 5. Maximum 1 strength session per day; Sunday (day 7) rest day is locked (no strength allowed).
 * 6. Quiet hours: zero notifications during tuition blocks or after bedtime; max 8 notifications/day.
 */
object SchedulerEngine {

    const val TUITION_BUFFER_MIN = 15
    const val MIN_SLEEP_MINUTES = 8 * 60 // 480 min hard floor
    const val MAX_DAILY_NOTIFICATIONS = 8
    private const val MINUTES_PER_DAY = 24 * 60

    fun defaultWakeMinutes(): Map<Int, Int> = mapOf(
        1 to (6 * 60 + 30), // Mon 06:30
        2 to (6 * 60 + 30), // Tue 06:30
        3 to (6 * 60 + 45), // Wed 06:45 (08:00 SST)
        4 to (6 * 60 + 30), // Thu 06:30
        5 to (6 * 60 + 45), // Fri 06:45 (08:00 SST)
        6 to (7 * 60 + 30), // Sat 07:30
        7 to (7 * 60 + 30)  // Sun 07:30
    )

    fun defaultDinnerMinute(dayOfWeek: Int): Int = when (dayOfWeek) {
        1, 4 -> 21 * 60 + 45 // Mon & Thu after 21:30 Maths + 15m buffer
        6, 7 -> 19 * 60 + 30 // Sat & Sun before 20:00 Bengali
        else -> 19 * 60 + 45 // Tue, Wed, Fri
    }

    fun generateWeekPlan(
        allLockedBlocks: List<ScheduleBlockEntity>,
        templates: List<WorkoutTemplateEntity>,
        wakeMinutesByDay: Map<Int, Int> = defaultWakeMinutes(),
        sleepTargetMinutes: Int = 510
    ): Map<Int, DayScheduleOutput> {
        val result = linkedMapOf<Int, DayScheduleOutput>()
        for (day in 1..7) {
            val nextDay = if (day == 7) 1 else day + 1
            val dayLocked = allLockedBlocks.filter { it.dayOfWeek == day && it.locked }
            val template = templates.firstOrNull { it.dayOfWeek == day }
            val wake = wakeMinutesByDay[day] ?: (6 * 60 + 30)
            val nextWake = wakeMinutesByDay[nextDay] ?: (6 * 60 + 30)
            result[day] = generateDayPlan(
                DayScheduleInput(
                    dayOfWeek = day,
                    nextDayOfWeek = nextDay,
                    lockedBlocks = dayLocked,
                    template = template,
                    wakeMinute = wake,
                    nextDayWakeMinute = nextWake,
                    sleepTargetMinutes = sleepTargetMinutes
                )
            )
        }
        return result
    }

    fun generateDayPlan(input: DayScheduleInput): DayScheduleOutput {
        val effectiveSleep = input.sleepTargetMinutes.coerceAtLeast(MIN_SLEEP_MINUTES)
        // Compute bedtime on today's 0..1440 timeline: nextDayWake + 1440 - effectiveSleep
        val rawBedtime = (input.nextDayWakeMinute + MINUTES_PER_DAY - effectiveSleep)
            .coerceIn(20 * 60 + 30, 23 * 60 + 30)

        // Ensure bedtime never cuts into a late locked block, while still protecting >= 8.0h sleep
        val latestLockedEnd = input.lockedBlocks.maxOfOrNull {
            if (it.type == BlockType.TUITION) it.endMinute + TUITION_BUFFER_MIN else it.endMinute
        } ?: 0
        val maxAllowedBedtimeFor8h = (input.nextDayWakeMinute + MINUTES_PER_DAY - MIN_SLEEP_MINUTES)
            .coerceAtMost(23 * 60 + 45)
        val bedtimeMinute = maxOf(rawBedtime, latestLockedEnd.coerceAtMost(maxAllowedBedtimeFor8h))
        val windDownMinute = (bedtimeMinute - 30).coerceAtLeast(input.wakeMinute + 60)

        val adjustments = mutableListOf<ScheduleAdjustment>()
        val placed = mutableListOf<ScheduleBlockEntity>()

        // Build forbidden intervals: locked blocks (expanded by 15m before/after for TUITION)
        val forbidden = mutableListOf<IntRange>()
        input.lockedBlocks.forEach { b ->
            val pad = if (b.type == BlockType.TUITION) TUITION_BUFFER_MIN else 5
            forbidden += (b.startMinute - pad).coerceAtLeast(0)..(b.endMinute + pad).coerceAtMost(MINUTES_PER_DAY)
        }

        // Active waking window: [wakeMinute .. bedtimeMinute]
        val dayStart = input.wakeMinute
        val dayEnd = bedtimeMinute

        // 1. Place Workout (respecting Sunday Rest Day guardrail!)
        var placedWorkoutRange: IntRange? = null
        val template = input.template
        if (template != null) {
            if (input.dayOfWeek == 7 && template.category == "STRENGTH") {
                adjustments += ScheduleAdjustment(
                    dayOfWeek = 7,
                    itemTitle = template.name,
                    requestedStartMin = template.defaultStartMinute,
                    placedStartMin = -1,
                    reason = "Refused: Sunday is a locked rest day (no strength work allowed)."
                )
            } else {
                val dur = template.durationMinutes.coerceIn(20, 90)
                val preferred = template.defaultStartMinute
                // Latest end for workout is bedtime - 20 min so sleep is protected
                val workoutLatestEnd = (dayEnd - 15).coerceAtLeast(dayStart + dur)
                val slotStart = findNearestValidSlot(
                    preferredStart = preferred,
                    duration = dur,
                    earliestStart = dayStart + 30,
                    latestEnd = workoutLatestEnd,
                    forbidden = forbidden
                )
                if (slotStart != null) {
                    if (slotStart != preferred) {
                        val colliding = findCollidingTitle(preferred, dur, input.lockedBlocks)
                        adjustments += ScheduleAdjustment(
                            dayOfWeek = input.dayOfWeek,
                            itemTitle = template.name,
                            requestedStartMin = preferred,
                            placedStartMin = slotStart,
                            reason = if (colliding != null) {
                                "Moved ${template.name} from ${formatMinute(preferred)} to ${formatMinute(slotStart)} to keep 15-min buffer around $colliding and protect 8h+ sleep."
                            } else {
                                "Moved ${template.name} to ${formatMinute(slotStart)} to protect 8h+ sleep window."
                            }
                        )
                    }
                    val block = ScheduleBlockEntity(
                        type = BlockType.WORKOUT,
                        dayOfWeek = input.dayOfWeek,
                        startMinute = slotStart,
                        endMinute = slotStart + dur,
                        title = template.name,
                        subtitle = template.description,
                        locked = input.dayOfWeek == 7,
                        source = "SCHEDULER",
                        refId = template.id
                    )
                    placed += block
                    placedWorkoutRange = slotStart..(slotStart + dur)
                    forbidden += slotStart..(slotStart + dur)
                } else {
                    adjustments += ScheduleAdjustment(
                        dayOfWeek = input.dayOfWeek,
                        itemTitle = template.name,
                        requestedStartMin = preferred,
                        placedStartMin = -1,
                        reason = "No conflict-free ${dur}m slot found today without cutting 8h sleep."
                    )
                }
            }
        }

        // 2. Wake-Up Water
        val wakeWaterStart = findNearestValidSlot(
            preferredStart = dayStart + 5,
            duration = 10,
            earliestStart = dayStart,
            latestEnd = dayStart + 90,
            forbidden = forbidden
        ) ?: dayStart
        placed += mealBlock(input.dayOfWeek, wakeWaterStart, 10, "Wake-Up Water", "1-2 glasses water", "WAKE_WATER")

        // 3. Breakfast (Wed & Fri must finish BEFORE 08:00 class -> latestEnd = 07:45 with 15m buffer!)
        val hasEarlyMorningClass = input.lockedBlocks.any { it.startMinute <= 8 * 60 + 15 }
        val preferredBreakfast = if (hasEarlyMorningClass) (dayStart + 20) else (dayStart + 35)
        val breakfastLatestEnd = if (hasEarlyMorningClass) (8 * 60 - TUITION_BUFFER_MIN) else (10 * 60 + 30)
        val breakfastStart = findNearestValidSlot(
            preferredStart = preferredBreakfast,
            duration = 25,
            earliestStart = dayStart + 10,
            latestEnd = breakfastLatestEnd.coerceAtLeast(dayStart + 35),
            forbidden = forbidden
        ) ?: preferredBreakfast
        placed += mealBlock(
            input.dayOfWeek,
            breakfastStart,
            25,
            "Breakfast",
            if (hasEarlyMorningClass) "Finish before 08:00 class • Protein + complex carbs" else "Eggs / milk / curd / paneer / chana",
            "BREAKFAST"
        )
        forbidden += breakfastStart..(breakfastStart + 25)

        // 4. Lunch (~4.5h after breakfast, 12:30-13:30 typical, spacing 3.5-5h)
        val preferredLunch = (breakfastStart + 270).coerceIn(12 * 60, 14 * 60)
        val lunchStart = findNearestValidSlot(
            preferredStart = preferredLunch,
            duration = 30,
            earliestStart = breakfastStart + 210, // >= 3.5h spacing
            latestEnd = 15 * 60 + 15,
            forbidden = forbidden
        ) ?: preferredLunch
        placed += mealBlock(
            input.dayOfWeek,
            lunchStart,
            30,
            "Lunch",
            "Plate rule: 1/2 sabzi, 1/4 protein, 1/4 rice (reduce usual rice by ~1/3)",
            "LUNCH"
        )
        forbidden += lunchStart..(lunchStart + 30)

        // 5. Dinner (Mon/Thu 21:45 light after tuition; Sat/Sun 19:30 before 20:00 Bengali; else 19:45)
        val preferredDinner = defaultDinnerMinute(input.dayOfWeek)
        val dinnerStart = findNearestValidSlot(
            preferredStart = preferredDinner,
            duration = 25,
            earliestStart = (lunchStart + 210).coerceAtMost(preferredDinner),
            latestEnd = (dayEnd + 10).coerceAtMost(22 * 60 + 30),
            forbidden = forbidden
        ) ?: preferredDinner
        if (abs(dinnerStart - preferredDinner) >= 15) {
            adjustments += ScheduleAdjustment(
                dayOfWeek = input.dayOfWeek,
                itemTitle = "Dinner",
                requestedStartMin = preferredDinner,
                placedStartMin = dinnerStart,
                reason = "Shifted Dinner from ${formatMinute(preferredDinner)} to ${formatMinute(dinnerStart)} around tuition/busy block."
            )
        }
        val dinnerSub = if (input.dayOfWeek == 1 || input.dayOfWeek == 4) {
            "Post-tuition light dinner • 1/2 sabzi, 1/4 protein, light roti/rice"
        } else {
            "Plate rule: 1/2 sabzi, 1/4 protein, 1/4 rice or roti"
        }
        placed += mealBlock(input.dayOfWeek, dinnerStart, 25, "Dinner", dinnerSub, "DINNER")
        forbidden += dinnerStart..(dinnerStart + 25)

        // 6. Pre-Workout Snack (~60 min before workout) & Post-Workout Snack (if dinner isn't right away)
        if (placedWorkoutRange != null && input.dayOfWeek != 7) {
            val targetPre = placedWorkoutRange.first - 60
            val preStart = findNearestValidSlot(
                preferredStart = targetPre,
                duration = 15,
                earliestStart = lunchStart + 45,
                latestEnd = placedWorkoutRange.first - 20,
                forbidden = forbidden
            )
            if (preStart != null) {
                placed += mealBlock(
                    input.dayOfWeek,
                    preStart,
                    15,
                    "Pre-Workout Snack",
                    "~1 hr before session: fruit + roasted chana / peanuts / milk",
                    "PRE_WORKOUT"
                )
                forbidden += preStart..(preStart + 15)
            }

            // Post-workout snack only if dinner is more than 55 min after workout ends
            if (dinnerStart - placedWorkoutRange.last >= 55) {
                val postStart = findNearestValidSlot(
                    preferredStart = placedWorkoutRange.last + 15,
                    duration = 15,
                    earliestStart = placedWorkoutRange.last + 5,
                    latestEnd = dinnerStart - 30,
                    forbidden = forbidden
                )
                if (postStart != null) {
                    placed += mealBlock(
                        input.dayOfWeek,
                        postStart,
                        15,
                        "Post-Workout Snack",
                        "Only if hungry: curd, milk, boiled eggs, or paneer",
                        "POST_WORKOUT"
                    )
                }
            }
        }

        // 7. Sleep Wind-Down & Protected Sleep Block
        val safeWindDown = findNearestValidSlot(
            preferredStart = windDownMinute,
            duration = 20,
            earliestStart = (bedtimeMinute - 60).coerceAtLeast(dayStart),
            latestEnd = bedtimeMinute,
            forbidden = input.lockedBlocks.map { it.startMinute..it.endMinute }
        ) ?: bedtimeMinute - 15
        placed += ScheduleBlockEntity(
            type = BlockType.SLEEP,
            dayOfWeek = input.dayOfWeek,
            startMinute = safeWindDown.coerceAtLeast(0),
            endMinute = bedtimeMinute,
            title = "Phone-Down & Wind-Down",
            subtitle = "Target 8.5h sleep • Bedtime ${formatMinute(bedtimeMinute)} → Wake ${formatMinute(input.nextDayWakeMinute)}",
            locked = false,
            source = "SCHEDULER",
            refId = "WIND_DOWN"
        )

        val sortedPlaced = placed.sortedBy { it.startMinute }
        val reminders = buildQuietSafeReminders(
            dayOfWeek = input.dayOfWeek,
            wakeMinute = dayStart,
            bedtimeMinute = bedtimeMinute,
            lockedBlocks = input.lockedBlocks,
            placedBlocks = sortedPlaced
        )

        return DayScheduleOutput(
            placedBlocks = sortedPlaced,
            reminders = reminders,
            adjustments = adjustments,
            bedtimeMinute = bedtimeMinute,
            windDownMinute = safeWindDown
        )
    }

    /**
     * Section 6 Missed Session Rule:
     * A missed session is NEVER doubled up. Move to the next day ONLY if:
     * - Next day is NOT Sunday (day 7, locked rest day),
     * - Next day does not already have a Strength session,
     * - Day before next day (today) did NOT execute a heavy strength session.
     * Otherwise skip cleanly and continue.
     */
    fun evaluateMissedSession(
        missedTemplate: WorkoutTemplateEntity,
        missedDayOfWeek: Int,
        nextDayLockedBlocks: List<ScheduleBlockEntity>,
        nextDayScheduledTemplate: WorkoutTemplateEntity?,
        didHeavySessionDayBeforeTarget: Boolean,
        nextDayWakeMin: Int = 6 * 60 + 30,
        dayAfterNextWakeMin: Int = 6 * 60 + 45
    ): MissedWorkoutDecision {
        val targetDay = if (missedDayOfWeek == 7) 1 else missedDayOfWeek + 1
        if (targetDay == 7) {
            return MissedWorkoutDecision(
                movedToDayOfWeek = null,
                placedStartMin = null,
                skipped = true,
                explanation = "Missed ${missedTemplate.name}. Sunday rest is locked — never double up. Continue fresh on Monday."
            )
        }
        if (nextDayScheduledTemplate?.category == "STRENGTH") {
            return MissedWorkoutDecision(
                movedToDayOfWeek = null,
                placedStartMin = null,
                skipped = true,
                explanation = "Missed ${missedTemplate.name}. Tomorrow already has ${nextDayScheduledTemplate.name} — never double up sessions."
            )
        }
        if (didHeavySessionDayBeforeTarget) {
            return MissedWorkoutDecision(
                movedToDayOfWeek = null,
                placedStartMin = null,
                skipped = true,
                explanation = "Missed ${missedTemplate.name}. Skipped instead of stacking after a heavy training day."
            )
        }

        val probe = generateDayPlan(
            DayScheduleInput(
                dayOfWeek = targetDay,
                lockedBlocks = nextDayLockedBlocks,
                template = missedTemplate.copy(dayOfWeek = targetDay),
                wakeMinute = nextDayWakeMin,
                nextDayWakeMinute = dayAfterNextWakeMin
            )
        )
        val placedWorkout = probe.placedBlocks.firstOrNull { it.type == BlockType.WORKOUT }
        return if (placedWorkout != null) {
            MissedWorkoutDecision(
                movedToDayOfWeek = targetDay,
                placedStartMin = placedWorkout.startMinute,
                skipped = false,
                explanation = "Moved ${missedTemplate.name} to ${dayName(targetDay)} at ${formatMinute(placedWorkout.startMinute)} (replaces light cardio, zero doubling)."
            )
        } else {
            MissedWorkoutDecision(
                movedToDayOfWeek = null,
                placedStartMin = null,
                skipped = true,
                explanation = "No conflict-free slot on ${dayName(targetDay)} without hurting tuition or 8h sleep — skipped cleanly."
            )
        }
    }

    private fun buildQuietSafeReminders(
        dayOfWeek: Int,
        wakeMinute: Int,
        bedtimeMinute: Int,
        lockedBlocks: List<ScheduleBlockEntity>,
        placedBlocks: List<ScheduleBlockEntity>
    ): List<ReminderEntity> {
        val raw = mutableListOf<ReminderEntity>()

        fun isQuietOrTuition(minute: Int): Boolean {
            if (minute < wakeMinute || minute >= bedtimeMinute) return true
            return lockedBlocks.any { b ->
                minute >= b.startMinute && minute <= b.endMinute
            }
        }

        fun snapSafeMinute(preferred: Int): Int? {
            if (!isQuietOrTuition(preferred)) return preferred
            for (delta in listOf(-15, -25, 15, 25, -40)) {
                val cand = preferred + delta
                if (!isQuietOrTuition(cand)) return cand
            }
            return null
        }

        // 1. Workout reminder (10 min before workout)
        placedBlocks.firstOrNull { it.type == BlockType.WORKOUT }?.let { wb ->
            snapSafeMinute(wb.startMinute - 10)?.let { trig ->
                raw += ReminderEntity(
                    id = "d${dayOfWeek}_workout",
                    category = ReminderCategory.WORKOUT,
                    dayOfWeek = dayOfWeek,
                    triggerMinute = trig,
                    title = "${wb.title} at ${formatMinute(wb.startMinute)}",
                    body = "Session at ${formatMinute(wb.startMinute)}. Shoes on.",
                    targetRef = wb.refId.orEmpty()
                )
            }
        }

        // 2. Main meal reminders (Breakfast, Lunch, Dinner -> max 3)
        placedBlocks.filter {
            it.type == BlockType.MEAL && it.refId in setOf("BREAKFAST", "LUNCH", "DINNER")
        }.forEach { mb ->
            snapSafeMinute(mb.startMinute)?.let { trig ->
                raw += ReminderEntity(
                    id = "d${dayOfWeek}_meal_${mb.refId}",
                    category = ReminderCategory.MEALS,
                    dayOfWeek = dayOfWeek,
                    triggerMinute = trig,
                    title = "${mb.title} Time",
                    body = mb.subtitle,
                    targetRef = mb.refId.orEmpty()
                )
            }
        }

        // 3. Water reminders (up to 3 spaced across day -> keeps total <= 8/day!)
        val waterCandidates = listOf(10 * 60 + 30, 14 * 60 + 30, 17 * 60 - 20)
        waterCandidates.forEachIndexed { idx, wm ->
            snapSafeMinute(wm)?.let { trig ->
                raw += ReminderEntity(
                    id = "d${dayOfWeek}_water_${idx + 1}",
                    category = ReminderCategory.MEALS,
                    dayOfWeek = dayOfWeek,
                    triggerMinute = trig,
                    title = "Hydration Check",
                    body = "Drink 2 glasses of water. Target 2.5-3 L across the day.",
                    targetRef = "WATER"
                )
            }
        }

        // 4. Phone-down sleep reminder (1)
        placedBlocks.firstOrNull { it.type == BlockType.SLEEP }?.let { sb ->
            snapSafeMinute(sb.startMinute)?.let { trig ->
                raw += ReminderEntity(
                    id = "d${dayOfWeek}_sleep",
                    category = ReminderCategory.SLEEP,
                    dayOfWeek = dayOfWeek,
                    triggerMinute = trig,
                    title = "Phone Down • Sleep Protection",
                    body = "Bedtime at ${formatMinute(bedtimeMinute)}. Protect 8+ hours of recovery.",
                    targetRef = "WIND_DOWN"
                )
            }
        }

        return raw
            .sortedBy { it.triggerMinute }
            .distinctBy { it.triggerMinute / 10 }
            .take(MAX_DAILY_NOTIFICATIONS)
    }

    private fun findNearestValidSlot(
        preferredStart: Int,
        duration: Int,
        earliestStart: Int,
        latestEnd: Int,
        forbidden: List<IntRange>
    ): Int? {
        if (earliestStart + duration > latestEnd) return null
        val clampedPref = preferredStart.coerceIn(earliestStart, latestEnd - duration)
        if (isFree(clampedPref, duration, forbidden)) return clampedPref

        var step = 5
        while (step <= MINUTES_PER_DAY) {
            val earlier = clampedPref - step
            if (earlier >= earliestStart && isFree(earlier, duration, forbidden)) {
                return earlier
            }
            val later = clampedPref + step
            if (later + duration <= latestEnd && isFree(later, duration, forbidden)) {
                return later
            }
            step += 5
        }
        return null
    }

    private fun isFree(start: Int, duration: Int, forbidden: List<IntRange>): Boolean {
        val end = start + duration
        return forbidden.none { range ->
            start < range.last && end > range.first
        }
    }

    private fun findCollidingTitle(start: Int, duration: Int, locked: List<ScheduleBlockEntity>): String? {
        val end = start + duration
        return locked.firstOrNull { b ->
            val pad = if (b.type == BlockType.TUITION) TUITION_BUFFER_MIN else 0
            start < (b.endMinute + pad) && end > (b.startMinute - pad)
        }?.title
    }

    private fun mealBlock(
        day: Int,
        start: Int,
        dur: Int,
        title: String,
        sub: String,
        slotKey: String
    ) = ScheduleBlockEntity(
        type = BlockType.MEAL,
        dayOfWeek = day,
        startMinute = start,
        endMinute = start + dur,
        title = title,
        subtitle = sub,
        locked = false,
        source = "SCHEDULER",
        refId = slotKey
    )

    fun formatMinute(minuteOfDay: Int): String {
        val norm = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val h = norm / 60
        val m = norm % 60
        return "%02d:%02d".format(h, m)
    }

    fun dayName(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        7 -> "Sun"
        else -> "Day"
    }
}
