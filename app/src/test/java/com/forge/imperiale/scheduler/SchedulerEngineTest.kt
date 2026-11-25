package com.forge.imperiale.scheduler

import com.forge.imperiale.data.local.entity.BlockType
import com.forge.imperiale.data.local.entity.ScheduleBlockEntity
import com.forge.imperiale.data.seed.ForgeSeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerEngineTest {

    private val tuition = ForgeSeedData.defaultTuitionBlocks()
    private val templates = ForgeSeedData.defaultTemplates()

    @Test
    fun `never overlaps locked tuition blocks and keeps 15 minute buffer`() {
        val week = SchedulerEngine.generateWeekPlan(tuition, templates)
        for (day in 1..7) {
            val dayTuition = tuition.filter { it.dayOfWeek == day }
            val workout = week[day]!!.placedBlocks.firstOrNull { it.type == BlockType.WORKOUT }
            if (workout != null) {
                dayTuition.forEach { t ->
                    val bufferedStart = t.startMinute - SchedulerEngine.TUITION_BUFFER_MIN
                    val bufferedEnd = t.endMinute + SchedulerEngine.TUITION_BUFFER_MIN
                    val overlaps = workout.startMinute < bufferedEnd && workout.endMinute > bufferedStart
                    assertFalse(
                        "Day $day workout (${workout.startMinute}-${workout.endMinute}) collided with buffered tuition (${t.title}: $bufferedStart-$bufferedEnd)",
                        overlaps
                    )
                }
            }
        }
    }

    @Test
    fun `wednesday and friday breakfast finishes before 08_00 SST tuition`() {
        val week = SchedulerEngine.generateWeekPlan(tuition, templates)
        for (day in listOf(3, 5)) {
            val breakfast = week[day]!!.placedBlocks.first { it.refId == "BREAKFAST" }
            assertTrue(
                "Day $day breakfast must finish by 07:45 (15m buffer before 08:00 SST), got ${breakfast.endMinute}",
                breakfast.endMinute <= 7 * 60 + 45
            )
        }
    }

    @Test
    fun `monday and thursday dinner is placed at 21_45 after maths tuition`() {
        val week = SchedulerEngine.generateWeekPlan(tuition, templates)
        for (day in listOf(1, 4)) {
            val dinner = week[day]!!.placedBlocks.first { it.refId == "DINNER" }
            assertEquals(21 * 60 + 45, dinner.startMinute)
        }
    }

    @Test
    fun `refuses strength workout on locked sunday rest day`() {
        val strengthTemplateOnSunday = templates.first { it.id == "strength_a" }.copy(dayOfWeek = 7)
        val out = SchedulerEngine.generateDayPlan(
            DayScheduleInput(
                dayOfWeek = 7,
                lockedBlocks = tuition.filter { it.dayOfWeek == 7 },
                template = strengthTemplateOnSunday,
                wakeMinute = 7 * 60 + 30,
                nextDayWakeMinute = 6 * 60 + 30
            )
        )
        assertNull(out.placedBlocks.firstOrNull { it.type == BlockType.WORKOUT })
        assertTrue(out.adjustments.any { it.reason.contains("Sunday is a locked rest day") })
    }

    @Test
    fun `calendar busy conflict moves workout and reports non-silent explanation`() {
        val examConflict = ScheduleBlockEntity(
            type = BlockType.SCHOOL,
            dayOfWeek = 1,
            startMinute = 15 * 60 + 45,
            endMinute = 17 * 60,
            title = "Physics Pre-Board Exam",
            locked = true,
            source = "CALENDAR_BUSY"
        )
        val monLocks = tuition.filter { it.dayOfWeek == 1 } + examConflict
        val monTemplate = templates.first { it.dayOfWeek == 1 } // Default 16:00-16:45
        val out = SchedulerEngine.generateDayPlan(
            DayScheduleInput(
                dayOfWeek = 1,
                lockedBlocks = monLocks,
                template = monTemplate,
                wakeMinute = 6 * 60 + 30,
                nextDayWakeMinute = 6 * 60 + 30
            )
        )
        val movedWorkout = out.placedBlocks.firstOrNull { it.type == BlockType.WORKOUT }
        assertNotNull(movedWorkout)
        assertTrue(movedWorkout!!.endMinute <= examConflict.startMinute || movedWorkout.startMinute >= examConflict.endMinute)
        assertTrue(out.adjustments.isNotEmpty())
    }

    @Test
    fun `missed session never doubles onto strength day or sunday rest`() {
        val strengthA = templates.first { it.id == "strength_a" }
        val strengthC = templates.first { it.id == "strength_c" }
        val cardioTue = templates.first { it.id == "cardio_tue" }

        // Missed Monday -> Tuesday only has cardio, no heavy day before -> moves cleanly
        val moveOk = SchedulerEngine.evaluateMissedSession(
            missedTemplate = strengthA,
            missedDayOfWeek = 1,
            nextDayLockedBlocks = tuition.filter { it.dayOfWeek == 2 },
            nextDayScheduledTemplate = cardioTue,
            didHeavySessionDayBeforeTarget = false
        )
        assertFalse(moveOk.skipped)
        assertEquals(2, moveOk.movedToDayOfWeek)

        // Missed Saturday -> Sunday is rest day -> skipped, never placed on Sunday
        val sundayRefused = SchedulerEngine.evaluateMissedSession(
            missedTemplate = strengthC,
            missedDayOfWeek = 6,
            nextDayLockedBlocks = tuition.filter { it.dayOfWeek == 7 },
            nextDayScheduledTemplate = templates.first { it.dayOfWeek == 7 },
            didHeavySessionDayBeforeTarget = false
        )
        assertTrue(sundayRefused.skipped)
        assertNull(sundayRefused.movedToDayOfWeek)
    }

    @Test
    fun `daily reminders never exceed 8 and never fire inside tuition blocks`() {
        val week = SchedulerEngine.generateWeekPlan(tuition, templates)
        for (day in 1..7) {
            val out = week[day]!!
            assertTrue("Max 8 reminders/day violated on day $day", out.reminders.size <= 8)
            val dayTuition = tuition.filter { it.dayOfWeek == day }
            out.reminders.forEach { rem ->
                dayTuition.forEach { t ->
                    val inside = rem.triggerMinute in t.startMinute..t.endMinute
                    assertFalse("Reminder ${rem.title} fired inside tuition ${t.title}", inside)
                }
            }
        }
    }
}
