package com.forge.imperiale.domain.progression

import com.forge.imperiale.data.local.entity.EffortLevel
import com.forge.imperiale.data.local.entity.SetLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionEngineTest {

    private fun makeSet(sessionId: Long, setNo: Int, weight: Double, reps: Int, effort: EffortLevel) =
        SetLogEntity(
            sessionId = sessionId,
            exerciseId = "goblet_squat",
            setNo = setNo,
            weightKg = weight,
            reps = reps,
            seconds = 0,
            effort = effort,
            timestampEpochMs = sessionId * 1000L + setNo
        )

    @Test
    fun `weeks 1 to 2 keep weight steady to master form`() {
        val rec = ProgressionEngine.recommendTarget(
            ProgressionInput(
                programWeek = 2,
                baseSets = 3,
                repMin = 10,
                repMax = 12,
                isMainLift = true,
                isTimed = false,
                defaultSeconds = 0,
                weightStepKg = 1.0,
                recentSessionsNewestFirst = listOf(
                    SessionExerciseHistory(
                        1L,
                        listOf(
                            makeSet(1L, 1, 8.0, 12, EffortLevel.EASY),
                            makeSet(1L, 2, 8.0, 12, EffortLevel.GOOD),
                            makeSet(1L, 3, 8.0, 12, EffortLevel.GOOD)
                        )
                    )
                )
            )
        )
        assertEquals(8.0, rec.recommendedWeightKg, 0.01)
        assertEquals(3, rec.recommendedSets)
        assertFalse(rec.suggestDeload)
    }

    @Test
    fun `weeks 5 to 6 bump weight by step when all sets hit repMax at GOOD or EASY`() {
        val rec = ProgressionEngine.recommendTarget(
            ProgressionInput(
                programWeek = 5,
                baseSets = 3,
                repMin = 10,
                repMax = 12,
                isMainLift = true,
                isTimed = false,
                defaultSeconds = 0,
                weightStepKg = 1.0,
                recentSessionsNewestFirst = listOf(
                    SessionExerciseHistory(
                        2L,
                        listOf(
                            makeSet(2L, 1, 10.0, 12, EffortLevel.GOOD),
                            makeSet(2L, 2, 10.0, 12, EffortLevel.EASY),
                            makeSet(2L, 3, 10.0, 12, EffortLevel.GOOD)
                        )
                    )
                )
            )
        )
        assertEquals(11.0, rec.recommendedWeightKg, 0.01)
        assertEquals(10, rec.recommendedReps)
    }

    @Test
    fun `weeks 7 to 8 add one extra set to main lifts`() {
        val rec = ProgressionEngine.recommendTarget(
            ProgressionInput(
                programWeek = 7,
                baseSets = 3,
                repMin = 8,
                repMax = 10,
                isMainLift = true,
                isTimed = false,
                defaultSeconds = 0,
                weightStepKg = 1.0,
                recentSessionsNewestFirst = emptyList()
            )
        )
        assertEquals(4, rec.recommendedSets)
    }

    @Test
    fun `two consecutive FAILED sessions suggest lighter weight deload`() {
        val rec = ProgressionEngine.recommendTarget(
            ProgressionInput(
                programWeek = 5,
                baseSets = 3,
                repMin = 10,
                repMax = 12,
                isMainLift = true,
                isTimed = false,
                defaultSeconds = 0,
                weightStepKg = 1.0,
                recentSessionsNewestFirst = listOf(
                    SessionExerciseHistory(2L, listOf(makeSet(2L, 1, 12.0, 7, EffortLevel.FAILED))),
                    SessionExerciseHistory(1L, listOf(makeSet(1L, 1, 12.0, 8, EffortLevel.FAILED)))
                )
            )
        )
        assertTrue(rec.suggestDeload)
        assertEquals(11.0, rec.recommendedWeightKg, 0.01)
    }
}
