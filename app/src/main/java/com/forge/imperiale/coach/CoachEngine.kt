package com.forge.imperiale.coach

data class CoachContext(
    val currentStreakDays: Int = 0,
    val missedYesterdayWorkout: Boolean = false,
    val missedWorkoutTitle: String? = null,
    val nextWorkoutTimeFormatted: String? = null,
    val nextWorkoutTitle: String? = null,
    val completedTodayWorkout: Boolean = false,
    val hitPersonalBestToday: Boolean = false,
    val skippedMealsThisWeek: Int = 0,
    val friedItemsThisWeek: Int = 0,
    val mishtiThisWeek: Int = 0,
    val plannedSleepMinutes: Int = 510,
    val recentMoodScoresNewestFirst: List<Int> = emptyList(),
    val isSundayRestDay: Boolean = false
)

data class CoachVerdict(
    val headline: String,
    val skippedMealSafetyAlert: String? = null,
    val lowMoodSupportAlert: String? = null,
    val homeFoodTip: String? = null
)

data class SafetyGateResult(
    val allowed: Boolean,
    val reason: String
)

/**
 * Coach Persona Interface (Section 8.5):
 * Deterministic rule-based engine behind an interface so an on-device or cloud LLM can be plugged in later.
 *
 * Non-negotiable guardrails (Section 6):
 * - Comments ONLY on behavior & performance (attendance, logging, reps, weights, sleep, meal timing).
 * - NEVER comments negatively on body/weight/looks, never shames, never recommends fasting/pills/skipping meals.
 */
interface CoachEngine {
    fun evaluateDailyCoach(context: CoachContext): CoachVerdict
    fun sessionFinishVerdict(completedSets: Int, hitPersonalBest: Boolean, rating: Int): String
    fun canStartStrengthSession(dayOfWeek: Int, strengthSessionsAlreadyDoneToday: Int, trainingDaysThisWeek: Int): SafetyGateResult
    fun canUnlockBarbell(programWeek: Int, checklistAllConfirmed: Boolean, exerciseName: String): SafetyGateResult
}

class RuleBasedCoachEngine : CoachEngine {

    override fun evaluateDailyCoach(context: CoachContext): CoachVerdict {
        val mealAlert = if (context.skippedMealsThisWeek >= 2) {
            "You logged ${context.skippedMealsThisWeek} skipped meals this week. Eat on time — never skip meals. Talk to a parent or doctor if eating feels hard."
        } else null

        val lowMoodAlert = if (
            context.recentMoodScoresNewestFirst.size >= 2 &&
            context.recentMoodScoresNewestFirst[0] <= 2 &&
            context.recentMoodScoresNewestFirst[1] <= 2
        ) {
            "Your mood check-in has been 1-2 for two weeks in a row. Keep sessions light and talk openly with a parent or trusted adult."
        } else null

        val foodTip = when {
            context.friedItemsThisWeek > 2 ->
                "Fried count is ${context.friedItemsThisWeek}/2 this week. Hold luchi/bhaja/singara for next week and scoop extra oil off gravies."
            context.mishtiThisWeek > 1 ->
                "Mishti count is ${context.mishtiThisWeek}/1 this week. Skip sugary sweets for the rest of the week."
            else ->
                "Lunch & Dinner: half plate sabzi/salad, quarter protein, quarter rice or roti."
        }

        val headline = when {
            context.isSundayRestDay ->
                "Sunday recovery day. Easy 20-30 min walk and stretches only — zero heavy lifting."
            context.hitPersonalBestToday ->
                "New personal best logged today. Clean reps build real strength."
            context.completedTodayWorkout && context.currentStreakDays >= 3 ->
                "${context.currentStreakDays} sessions in a row. That is discipline."
            context.completedTodayWorkout ->
                "Session logged. Eat your next meal on time and protect tonight's sleep."
            context.missedYesterdayWorkout ->
                "You missed ${context.missedWorkoutTitle ?: "yesterday's session"}. Back on track today, no making it up by doing double."
            context.plannedSleepMinutes < 480 ->
                "Sleep window is tight tonight. Finish homework, phone down early, protect 8 hours."
            context.nextWorkoutTimeFormatted != null ->
                "${context.nextWorkoutTitle ?: "Session"} at ${context.nextWorkoutTimeFormatted}. Shoes on."
            else ->
                "Show up, log every set honestly, eat on schedule, sleep 8.5 hours."
        }

        return CoachVerdict(
            headline = headline,
            skippedMealSafetyAlert = mealAlert,
            lowMoodSupportAlert = lowMoodAlert,
            homeFoodTip = foodTip
        )
    }

    override fun sessionFinishVerdict(completedSets: Int, hitPersonalBest: Boolean, rating: Int): String = when {
        completedSets == 0 -> "Stopped early. Rest, hydrate, and come back cleanly on your next scheduled slot."
        hitPersonalBest -> "Personal best earned with logged sets. Hydrate and eat on schedule."
        rating >= 4 -> "$completedSets sets banked cleanly. Consistency beats ego lifting every time."
        rating <= 2 -> "$completedSets sets done on a tough day. Showing up and staying safe counts."
        else -> "$completedSets sets completed. Log dinner on time and get 8.5 hours of sleep."
    }

    override fun canStartStrengthSession(
        dayOfWeek: Int,
        strengthSessionsAlreadyDoneToday: Int,
        trainingDaysThisWeek: Int
    ): SafetyGateResult {
        if (dayOfWeek == 7) {
            return SafetyGateResult(
                allowed = false,
                reason = "Sunday is a locked rest day. Only easy walking and stretching are permitted — never extra strength training."
            )
        }
        if (strengthSessionsAlreadyDoneToday >= 1) {
            return SafetyGateResult(
                allowed = false,
                reason = "Maximum one strength session per day. Recover, eat on time, and sleep 8+ hours."
            )
        }
        if (trainingDaysThisWeek >= 6) {
            return SafetyGateResult(
                allowed = false,
                reason = "Maximum 6 training days per week reached. Take full recovery today."
            )
        }
        return SafetyGateResult(allowed = true, reason = "Cleared to train.")
    }

    override fun canUnlockBarbell(
        programWeek: Int,
        checklistAllConfirmed: Boolean,
        exerciseName: String
    ): SafetyGateResult {
        if (exerciseName.contains("bench press", ignoreCase = true)) {
            return SafetyGateResult(
                allowed = false,
                reason = "Barbell bench press alone at home is permanently blocked for safety."
            )
        }
        if (programWeek < 5) {
            return SafetyGateResult(
                allowed = false,
                reason = "Optional light barbell lifts unlock in Week 5 (currently Week $programWeek). Master dumbbell form first."
            )
        }
        if (!checklistAllConfirmed) {
            return SafetyGateResult(
                allowed = false,
                reason = "Complete every barbell safety check (collars locked, light load, adult nearby, clear floor) before unlocking."
            )
        }
        return SafetyGateResult(allowed = true, reason = "Light barbell safety checklist verified.")
    }
}
