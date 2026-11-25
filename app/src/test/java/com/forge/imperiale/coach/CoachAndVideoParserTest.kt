package com.forge.imperiale.coach

import app.cash.turbine.test
import com.forge.imperiale.data.local.entity.VideoStreamType
import com.forge.imperiale.data.remote.MinimalBson
import com.forge.imperiale.data.remote.ParsedMongoUri
import com.forge.imperiale.domain.video.VideoLinkParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachAndVideoParserTest {

    private val coach = RuleBasedCoachEngine()

    @Test
    fun `coach enforces skipped meals and consecutive low mood guardrails`() {
        val verdict = coach.evaluateDailyCoach(
            CoachContext(
                currentStreakDays = 3,
                completedTodayWorkout = true,
                skippedMealsThisWeek = 2,
                recentMoodScoresNewestFirst = listOf(2, 1)
            )
        )
        assertTrue(verdict.headline.contains("discipline", ignoreCase = true))
        assertNotNull(verdict.skippedMealSafetyAlert)
        assertNotNull(verdict.lowMoodSupportAlert)
    }

    @Test
    fun `coach refuses sunday strength and solo barbell bench press`() {
        val sunGate = coach.canStartStrengthSession(dayOfWeek = 7, strengthSessionsAlreadyDoneToday = 0, trainingDaysThisWeek = 4)
        assertFalse(sunGate.allowed)

        val benchGate = coach.canUnlockBarbell(programWeek = 6, checklistAllConfirmed = true, exerciseName = "Barbell Bench Press")
        assertFalse(benchGate.allowed)

        val week3Barbell = coach.canUnlockBarbell(programWeek = 3, checklistAllConfirmed = true, exerciseName = "Light Barbell Squat")
        assertFalse(week3Barbell.allowed)

        val week5Barbell = coach.canUnlockBarbell(programWeek = 5, checklistAllConfirmed = true, exerciseName = "Light Barbell Squat")
        assertTrue(week5Barbell.allowed)
    }

    @Test
    fun `video link parser extracts youtube watch shorts youtu_be embed and mp4`() {
        val watch = VideoLinkParser.parse("https://www.youtube.com/watch?v=lRYBbchqxtI&t=12s")
        assertNotNull(watch)
        assertEquals(VideoStreamType.YOUTUBE, watch!!.type)
        assertEquals("lRYBbchqxtI", watch.reference)
        assertEquals(12f, watch.loopStartSec, 0.01f)

        val shortUrl = VideoLinkParser.parse("https://youtu.be/pKZ-lkKKMws")
        assertEquals("pKZ-lkKKMws", shortUrl?.reference)

        val shortsPath = VideoLinkParser.parse("https://youtube.com/shorts/Ye7G1wQCMgk?feature=share")
        assertEquals("Ye7G1wQCMgk", shortsPath?.reference)

        val embed = VideoLinkParser.parse("https://www.youtube.com/embed/DqLL45uk2Tk?start=4&end=22")
        assertEquals("DqLL45uk2Tk", embed?.reference)
        assertEquals(4f, embed?.loopStartSec ?: 0f, 0.01f)
        assertEquals(22f, embed?.loopEndSec ?: 0f, 0.01f)

        val mp4 = VideoLinkParser.parse("https://cdn.jsdelivr.net/gh/namnq/workout-free-videos@v2.0.0/media/video/bent-over-row.mp4", 0.5f, 7.5f)
        assertEquals(VideoStreamType.MP4, mp4?.type)
        assertEquals(7.5f, mp4?.loopEndSec ?: 0f, 0.01f)

        assertNull(VideoLinkParser.parse("not-a-valid-url"))
    }

    @Test
    fun `bson roundtrip and mongo uri parser work cleanly`() {
        val uri = ParsedMongoUri.parse("mongodb://user1:pass1@host1.example.net:27017,host2.example.net:27017/vellura-games?ssl=true&authSource=admin")
        assertNotNull(uri)
        assertEquals("user1", uri!!.username)
        assertEquals(2, uri.hosts.size)
        assertEquals("vellura-games", uri.database)

        val encoded = MinimalBson.encodeDocument(
            listOf("hello" to 1, "username" to "arnab", "active" to true)
        )
        val decoded = MinimalBson.decodeDocument(encoded)
        assertEquals(1, decoded["hello"])
        assertEquals("arnab", decoded["username"])
        assertEquals(true, decoded["active"])
    }

    @Test
    fun `reactive coach state flow emits deterministically under Turbine`() = runTest {
        val contextFlow = MutableStateFlow(CoachContext(nextWorkoutTimeFormatted = "16:00", nextWorkoutTitle = "Strength A"))
        contextFlow.test {
            val initial = coach.evaluateDailyCoach(awaitItem())
            assertEquals("Strength A at 16:00. Shoes on.", initial.headline)

            contextFlow.value = CoachContext(currentStreakDays = 4, completedTodayWorkout = true)
            val updated = coach.evaluateDailyCoach(awaitItem())
            assertEquals("4 sessions in a row. That is discipline.", updated.headline)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
