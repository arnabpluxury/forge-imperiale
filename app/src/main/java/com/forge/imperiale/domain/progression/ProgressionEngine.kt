package com.forge.imperiale.domain.progression

import com.forge.imperiale.data.local.entity.EffortLevel
import com.forge.imperiale.data.local.entity.SetLogEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

data class SessionExerciseHistory(
    val sessionId: Long,
    val sets: List<SetLogEntity>
)

data class ProgressionInput(
    val programWeek: Int, // 1..8
    val baseSets: Int,
    val repMin: Int,
    val repMax: Int,
    val isMainLift: Boolean,
    val isTimed: Boolean,
    val defaultSeconds: Int,
    val weightStepKg: Double,
    val recentSessionsNewestFirst: List<SessionExerciseHistory>
)

data class ProgressionRecommendation(
    val programWeek: Int,
    val phaseLabel: String,
    val recommendedSets: Int,
    val recommendedWeightKg: Double,
    val recommendedReps: Int,
    val recommendedSeconds: Int,
    val lastSessionSummary: String,
    val rationale: String,
    val suggestDeload: Boolean = false
)

/**
 * Pure Double-Progression Engine (Section 4 & Section 8.4):
 * - Weeks 1-2: Learn form with light weights.
 * - Weeks 3-4: Add reps within [repMin..repMax].
 * - Weeks 5-6: Add weightStepKg once ALL sets hit repMax at effort GOOD or EASY.
 * - Weeks 7-8: Add +1 extra set to main lifts while continuing double progression.
 * - Safety deload: If 2 consecutive sessions had FAILED sets, reduce weight by weightStepKg.
 */
object ProgressionEngine {

    fun computeProgramWeek(startDateIso: String, currentDate: LocalDate = LocalDate.now()): Int {
        val start = runCatching { LocalDate.parse(startDateIso) }.getOrDefault(currentDate)
        val days = ChronoUnit.DAYS.between(start, currentDate).coerceAtLeast(0)
        return ((days / 7).toInt() + 1).coerceIn(1, 8)
    }

    fun recommendTarget(input: ProgressionInput): ProgressionRecommendation {
        val week = input.programWeek.coerceIn(1, 8)
        val step = input.weightStepKg.coerceAtLeast(0.25)
        val targetSets = if (week >= 7 && input.isMainLift) input.baseSets + 1 else input.baseSets

        val phaseLabel = when (week) {
            1, 2 -> "Weeks 1-2 • Master Form"
            3, 4 -> "Weeks 3-4 • Build Reps"
            5, 6 -> "Weeks 5-6 • Add Load"
            else -> "Weeks 7-8 • Peak Volume (+1 Main Set)"
        }

        val validHistory = input.recentSessionsNewestFirst
            .map { hist -> hist.copy(sets = hist.sets.filter { it.skippedReason == null }) }
            .filter { it.sets.isNotEmpty() }

        val latest = validHistory.firstOrNull()
        if (latest == null) {
            return ProgressionRecommendation(
                programWeek = week,
                phaseLabel = phaseLabel,
                recommendedSets = targetSets,
                recommendedWeightKg = 0.0,
                recommendedReps = input.repMin,
                recommendedSeconds = input.defaultSeconds,
                lastSessionSummary = "First session — start light & crisp",
                rationale = "$phaseLabel: Start with clean control at ${if (input.isTimed) "${input.defaultSeconds}s" else "${input.repMin} reps"}."
            )
        }

        val lastSets = latest.sets
        val lastWeight = lastSets.maxOfOrNull { it.weightKg } ?: 0.0
        val lastAvgReps = lastSets.map { it.reps }.average().toInt().coerceAtLeast(input.repMin)
        val lastMaxSec = lastSets.maxOfOrNull { it.seconds } ?: input.defaultSeconds
        val lastSummary = if (input.isTimed) {
            "Last: ${lastSets.size}x${lastMaxSec}s"
        } else {
            "Last: ${lastSets.size} sets @ ${formatWeight(lastWeight)} kg (${lastSets.joinToString("/") { it.reps.toString() }})"
        }

        // Check two consecutive FAILED sessions -> suggest lighter weight
        val twoFailedInARow = validHistory.size >= 2 &&
            validHistory[0].sets.any { it.effort == EffortLevel.FAILED } &&
            validHistory[1].sets.any { it.effort == EffortLevel.FAILED }

        if (twoFailedInARow) {
            val lighter = max(0.0, lastWeight - step)
            val lighterSec = max(15, lastMaxSec - 5)
            return ProgressionRecommendation(
                programWeek = week,
                phaseLabel = phaseLabel,
                recommendedSets = targetSets,
                recommendedWeightKg = lighter,
                recommendedReps = input.repMin,
                recommendedSeconds = if (input.isTimed) lighterSec else input.defaultSeconds,
                lastSessionSummary = lastSummary,
                rationale = if (lastWeight > 0.0) {
                    "Two tough sessions in a row — step weight down to ${formatWeight(lighter)} kg and nail clean reps."
                } else {
                    "Two failed sessions in a row — use the easier exercise variant today and rebuild clean reps."
                },
                suggestDeload = true
            )
        }

        if (input.isTimed) {
            val allClean = lastSets.all { it.effort == EffortLevel.EASY || it.effort == EffortLevel.GOOD }
            val nextSec = if (week >= 3 && allClean) (lastMaxSec + 5).coerceAtMost(input.defaultSeconds + 35) else lastMaxSec
            return ProgressionRecommendation(
                programWeek = week,
                phaseLabel = phaseLabel,
                recommendedSets = targetSets,
                recommendedWeightKg = lastWeight,
                recommendedReps = 1,
                recommendedSeconds = nextSec,
                lastSessionSummary = lastSummary,
                rationale = if (nextSec > lastMaxSec) {
                    "Last hold was solid — add +5s to reach ${nextSec}s."
                } else {
                    "Hold steady at ${nextSec}s with strict posture."
                }
            )
        }

        val allHitTopAtGoodOrEasy = lastSets.size >= input.baseSets &&
            lastSets.all {
                it.reps >= input.repMax && (it.effort == EffortLevel.GOOD || it.effort == EffortLevel.EASY)
            }

        return when {
            week <= 2 -> {
                ProgressionRecommendation(
                    programWeek = week,
                    phaseLabel = phaseLabel,
                    recommendedSets = targetSets,
                    recommendedWeightKg = lastWeight,
                    recommendedReps = lastAvgReps.coerceIn(input.repMin, input.repMax),
                    recommendedSeconds = input.defaultSeconds,
                    lastSessionSummary = lastSummary,
                    rationale = "Weeks 1-2 focus on textbook form — keep ${formatWeight(lastWeight)} kg steady."
                )
            }
            week in 3..4 -> {
                val nextReps = if (allHitTopAtGoodOrEasy) input.repMax else (lastAvgReps + 1).coerceIn(input.repMin, input.repMax)
                ProgressionRecommendation(
                    programWeek = week,
                    phaseLabel = phaseLabel,
                    recommendedSets = targetSets,
                    recommendedWeightKg = lastWeight,
                    recommendedReps = nextReps,
                    recommendedSeconds = input.defaultSeconds,
                    lastSessionSummary = lastSummary,
                    rationale = "Build reps inside ${input.repMin}-${input.repMax} at ${formatWeight(lastWeight)} kg (target $nextReps reps)."
                )
            }
            else -> {
                if (allHitTopAtGoodOrEasy) {
                    val bumpedWeight = lastWeight + step
                    ProgressionRecommendation(
                        programWeek = week,
                        phaseLabel = phaseLabel,
                        recommendedSets = targetSets,
                        recommendedWeightKg = bumpedWeight,
                        recommendedReps = input.repMin,
                        recommendedSeconds = input.defaultSeconds,
                        lastSessionSummary = lastSummary,
                        rationale = "All sets hit ${input.repMax} cleanly — add +${formatWeight(step)} kg (${formatWeight(bumpedWeight)} kg x ${input.repMin})."
                    )
                } else {
                    val nextReps = (lastAvgReps + 1).coerceIn(input.repMin, input.repMax)
                    ProgressionRecommendation(
                        programWeek = week,
                        phaseLabel = phaseLabel,
                        recommendedSets = targetSets,
                        recommendedWeightKg = lastWeight,
                        recommendedReps = nextReps,
                        recommendedSeconds = input.defaultSeconds,
                        lastSessionSummary = lastSummary,
                        rationale = "Push toward ${input.repMax} clean reps across $targetSets sets before adding weight."
                    )
                }
            }
        }
    }

    private fun formatWeight(w: Double): String =
        if (w % 1.0 == 0.0) w.toInt().toString() else "%.1f".format(w)
}
