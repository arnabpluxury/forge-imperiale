package com.forge.imperiale.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.imperiale.coach.CoachEngine
import com.forge.imperiale.data.local.entity.ActiveWorkoutStateEntity
import com.forge.imperiale.data.local.entity.EffortLevel
import com.forge.imperiale.data.local.entity.ExerciseEntity
import com.forge.imperiale.data.local.entity.SetLogEntity
import com.forge.imperiale.data.local.entity.TemplateExerciseEntity
import com.forge.imperiale.data.local.entity.WorkoutSessionEntity
import com.forge.imperiale.data.local.entity.WorkoutTemplateEntity
import com.forge.imperiale.data.preferences.ForgePreferencesRepository
import com.forge.imperiale.data.repository.ForgeRepository
import com.forge.imperiale.domain.progression.ProgressionEngine
import com.forge.imperiale.domain.progression.ProgressionInput
import com.forge.imperiale.domain.progression.ProgressionRecommendation
import com.forge.imperiale.domain.progression.SessionExerciseHistory
import com.forge.imperiale.notifications.AlarmScheduler
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.max

enum class TroubleshootIssue(val label: String) {
    TOO_HEAVY("Too heavy"),
    TOO_LIGHT("Too light"),
    CANNOT_DO_FORM("Cannot do the form"),
    PAIN_OR_DIZZY("Pain or dizzy"),
    NO_EQUIPMENT("No equipment"),
    SHORT_ON_TIME("Short on time"),
    FEELING_TIRED("Feeling tired")
}

data class ActiveExerciseSlot(
    val templateExercise: TemplateExerciseEntity,
    val exercise: ExerciseEntity,
    val effectiveSets: Int,
    val recommendation: ProgressionRecommendation
)

data class FinishSummaryUi(
    val templateName: String,
    val totalSets: Int,
    val totalVolumeKg: Double,
    val personalBests: List<String>,
    val coachVerdict: String,
    val sessionId: Long
)

data class WorkoutScreenUiState(
    val templates: List<WorkoutTemplateEntity> = emptyList(),
    val todayDayOfWeek: Int = LocalDate.now().dayOfWeek.value,
    val programWeek: Int = 1,
    val weightStepKg: Double = 1.0,
    val defaultRestSec: Int = 75,
    val barbellSafetyUnlocked: Boolean = false,
    val activeSession: WorkoutSessionEntity? = null,
    val exerciseSlots: List<ActiveExerciseSlot> = emptyList(),
    val currentExerciseIndex: Int = 0,
    val loggedSets: List<SetLogEntity> = emptyList(),
    val lastUndoableSetId: Long? = null,
    val inputWeightKg: Double = 0.0,
    val inputReps: Int = 10,
    val inputSeconds: Int = 25,
    val selectedEffort: EffortLevel = EffortLevel.GOOD,
    val elapsedSeconds: Long = 0,
    val isPaused: Boolean = false,
    val restRemainingSec: Int = 0,
    val restTotalSec: Int = 75,
    val showSafetyStopCard: Boolean = false,
    val showCheckinPrompt: Boolean = false,
    val showBarbellSafetyGateForId: String? = null,
    val forceSlowMotionCues: Boolean = false,
    val expandFormCues: Boolean = false,
    val bannerMessage: String? = null,
    val finishSummary: FinishSummaryUi? = null
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val repository: ForgeRepository,
    private val prefs: ForgePreferencesRepository,
    private val coachEngine: CoachEngine,
    private val alarmScheduler: AlarmScheduler,
    private val gson: Gson
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutScreenUiState())
    val uiState: StateFlow<WorkoutScreenUiState> = _uiState.asStateFlow()

    private var activePersistState: ActiveWorkoutStateEntity? = null

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            refreshHubAndRestoreSession()
            startLiveTicker()
        }
    }

    private suspend fun refreshHubAndRestoreSession() {
        val profile = repository.getProfileOrDefault()
        val settings = prefs.settingsFlow.first()
        val templates = repository.getTemplates()
        val today = LocalDate.now()
        val week = ProgressionEngine.computeProgramWeek(profile.startDateIso, today)

        _uiState.update {
            it.copy(
                templates = templates,
                todayDayOfWeek = today.dayOfWeek.value,
                programWeek = week,
                weightStepKg = settings.weightStepKg,
                defaultRestSec = settings.defaultRestSec,
                barbellSafetyUnlocked = profile.barbellSafetyUnlocked
            )
        }

        // Restore in-progress session across process death / phone call (Section 10)
        val savedActive = repository.getActiveWorkoutState()
        if (savedActive != null) {
            val session = repository.getSession(savedActive.sessionId)
            if (session != null && !session.completed) {
                activePersistState = savedActive
                loadSessionIntoUi(session, savedActive)
            } else {
                repository.clearActiveWorkoutState()
            }
        }
    }

    private fun startLiveTicker() {
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val persisted = activePersistState ?: continue
                val now = System.currentTimeMillis()
                val elapsed = if (persisted.isPaused) {
                    persisted.accumulatedElapsedSec
                } else {
                    persisted.accumulatedElapsedSec + ((now - persisted.lastResumeEpochMs) / 1000L).coerceAtLeast(0L)
                }

                val restLeft = persisted.restEndEpochMs?.let { endMs ->
                    ((endMs - now + 999L) / 1000L).toInt().coerceAtLeast(0)
                } ?: 0

                if (persisted.restEndEpochMs != null && restLeft == 0) {
                    val cleared = persisted.copy(restEndEpochMs = null)
                    activePersistState = cleared
                    repository.saveActiveWorkoutState(cleared)
                }

                val idleOver10Min = !persisted.isPaused && (now - persisted.lastInteractionEpochMs) >= 10 * 60_000L
                val pastPlannedEnd = !persisted.isPaused && persisted.plannedEndEpochMs > 0 && now > persisted.plannedEndEpochMs

                _uiState.update { state ->
                    state.copy(
                        elapsedSeconds = elapsed,
                        restRemainingSec = restLeft,
                        restTotalSec = persisted.restTotalSec,
                        showCheckinPrompt = state.showCheckinPrompt || ((idleOver10Min || pastPlannedEnd) && !state.showSafetyStopCard)
                    )
                }
            }
        }
    }

    fun startWorkoutByTemplate(templateId: String?) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.activeSession != null && (templateId == null || state.activeSession.templateId == templateId)) {
                return@launch
            }
            val today = LocalDate.now()
            val targetTemplate = if (templateId != null) {
                repository.getTemplate(templateId)
            } else {
                repository.getTemplateForDay(today.dayOfWeek.value)
            } ?: repository.getTemplates().firstOrNull() ?: return@launch

            // Enforce Section 6 Hard Guardrails: Sunday rest lock & max 1 strength session/day & max 6 days/week
            if (targetTemplate.category == "STRENGTH") {
                val todaySessions = repository.getSessionsForDate(today.toString())
                val strengthDoneToday = todaySessions.count { it.completed && it.templateId.startsWith("strength") }
                val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
                val trainingDaysWeek = repository.getAllSessions()
                    .filter { it.completed && it.dateIso >= weekStart.toString() }
                    .map { it.dateIso }
                    .distinct()
                    .size
                val gate = coachEngine.canStartStrengthSession(
                    dayOfWeek = today.dayOfWeek.value,
                    strengthSessionsAlreadyDoneToday = strengthDoneToday,
                    trainingDaysThisWeek = trainingDaysWeek
                )
                if (!gate.allowed) {
                    _uiState.update { it.copy(bannerMessage = gate.reason) }
                    return@launch
                }
            }

            val now = System.currentTimeMillis()
            val sessionId = repository.startOrCreateSession(
                WorkoutSessionEntity(
                    templateId = targetTemplate.id,
                    templateName = targetTemplate.name,
                    dateIso = today.toString(),
                    startEpochMs = now
                )
            )
            val newPersist = ActiveWorkoutStateEntity(
                id = 1,
                sessionId = sessionId,
                templateId = targetTemplate.id,
                currentExerciseIndex = 0,
                currentSetNo = 1,
                restEndEpochMs = null,
                restTotalSec = _uiState.value.defaultRestSec,
                isPaused = false,
                accumulatedElapsedSec = 0,
                lastResumeEpochMs = now,
                lastInteractionEpochMs = now,
                plannedEndEpochMs = now + targetTemplate.durationMinutes * 60_000L
            )
            activePersistState = newPersist
            repository.saveActiveWorkoutState(newPersist)
            val createdSession = repository.getSession(sessionId) ?: return@launch
            loadSessionIntoUi(createdSession, newPersist)
        }
    }

    private suspend fun loadSessionIntoUi(
        session: WorkoutSessionEntity,
        persisted: ActiveWorkoutStateEntity
    ) {
        val profile = repository.getProfileOrDefault()
        val settings = prefs.settingsFlow.first()
        val week = ProgressionEngine.computeProgramWeek(profile.startDateIso, LocalDate.now())
        val templateExercises = repository.getTemplateExercises(session.templateId)
        val allExercises = repository.getAllExercises().associateBy { it.id }
        val overrides = parseOverrides(persisted.exerciseOverridesJson)
        val allHistoricalSets = repository.allSetsFlow.first()

        val slots = templateExercises.mapNotNull { te ->
            val effectiveExId = overrides[te.exerciseId] ?: te.exerciseId
            val ex = allExercises[effectiveExId] ?: return@mapNotNull null

            val historyBySession = allHistoricalSets
                .filter { it.exerciseId == ex.id && it.sessionId != session.id }
                .groupBy { it.sessionId }
                .entries
                .sortedByDescending { entry -> entry.value.maxOfOrNull { it.timestampEpochMs } ?: 0L }
                .take(4)
                .map { SessionExerciseHistory(it.key, it.value) }

            val rec = ProgressionEngine.recommendTarget(
                ProgressionInput(
                    programWeek = week,
                    baseSets = te.sets,
                    repMin = te.repMin,
                    repMax = te.repMax,
                    isMainLift = te.isMainLift,
                    isTimed = ex.isTimed,
                    defaultSeconds = if (te.defaultSeconds > 0) te.defaultSeconds else ex.defaultSeconds,
                    weightStepKg = settings.weightStepKg,
                    recentSessionsNewestFirst = historyBySession
                )
            )

            var effectiveSets = rec.recommendedSets
            if (persisted.dropLastAccessorySet && te.isAccessory && effectiveSets > 1) {
                effectiveSets -= 1
            }
            if (persisted.cutHalfSets && effectiveSets > 1) {
                effectiveSets = max(1, effectiveSets / 2)
            }

            ActiveExerciseSlot(
                templateExercise = te,
                exercise = ex,
                effectiveSets = effectiveSets,
                recommendation = rec
            )
        }

        val logged = repository.getSetsForSession(session.id)
        val safeIdx = persisted.currentExerciseIndex.coerceIn(0, (slots.size - 1).coerceAtLeast(0))
        val curSlot = slots.getOrNull(safeIdx)

        _uiState.update {
            it.copy(
                activeSession = session,
                exerciseSlots = slots,
                currentExerciseIndex = safeIdx,
                loggedSets = logged,
                inputWeightKg = curSlot?.recommendation?.recommendedWeightKg ?: 0.0,
                inputReps = curSlot?.recommendation?.recommendedReps ?: 10,
                inputSeconds = curSlot?.recommendation?.recommendedSeconds ?: 25,
                isPaused = persisted.isPaused,
                finishSummary = null,
                bannerMessage = null
            )
        }
    }

    fun selectExerciseIndex(index: Int) {
        val state = _uiState.value
        val slot = state.exerciseSlots.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                currentExerciseIndex = index,
                inputWeightKg = slot.recommendation.recommendedWeightKg,
                inputReps = slot.recommendation.recommendedReps,
                inputSeconds = slot.recommendation.recommendedSeconds,
                forceSlowMotionCues = false
            )
        }
        touchInteraction(newExerciseIndex = index)
    }

    fun adjustWeight(deltaMultiplier: Int) {
        val step = _uiState.value.weightStepKg
        _uiState.update {
            it.copy(inputWeightKg = max(0.0, it.inputWeightKg + deltaMultiplier * step))
        }
        touchInteraction()
    }

    fun adjustReps(delta: Int) {
        _uiState.update {
            it.copy(inputReps = (it.inputReps + delta).coerceIn(1, 100))
        }
        touchInteraction()
    }

    fun adjustSeconds(delta: Int) {
        _uiState.update {
            it.copy(inputSeconds = (it.inputSeconds + delta).coerceIn(5, 3600))
        }
        touchInteraction()
    }

    fun selectEffort(effort: EffortLevel) {
        _uiState.update { it.copy(selectedEffort = effort) }
        touchInteraction()
    }

    fun toggleExpandCues() {
        _uiState.update { it.copy(expandFormCues = !it.expandFormCues) }
    }

    fun togglePauseSession() {
        viewModelScope.launch {
            val persisted = activePersistState ?: return@launch
            val now = System.currentTimeMillis()
            val updated = if (persisted.isPaused) {
                persisted.copy(
                    isPaused = false,
                    lastResumeEpochMs = now,
                    lastInteractionEpochMs = now
                )
            } else {
                val gained = ((now - persisted.lastResumeEpochMs) / 1000L).coerceAtLeast(0L)
                persisted.copy(
                    isPaused = true,
                    accumulatedElapsedSec = persisted.accumulatedElapsedSec + gained,
                    lastInteractionEpochMs = now
                )
            }
            activePersistState = updated
            repository.saveActiveWorkoutState(updated)
            _uiState.update { it.copy(isPaused = updated.isPaused) }
        }
    }

    fun markSetDone() {
        viewModelScope.launch {
            val state = _uiState.value
            val session = state.activeSession ?: return@launch
            val slot = state.exerciseSlots.getOrNull(state.currentExerciseIndex) ?: return@launch
            val doneForEx = state.loggedSets.count { it.exerciseId == slot.exercise.id && it.skippedReason == null }
            val nextSetNo = doneForEx + 1

            val newSet = SetLogEntity(
                sessionId = session.id,
                exerciseId = slot.exercise.id,
                setNo = nextSetNo,
                weightKg = if (slot.exercise.isTimed) 0.0 else state.inputWeightKg,
                reps = if (slot.exercise.isTimed) 1 else state.inputReps,
                seconds = if (slot.exercise.isTimed) state.inputSeconds else 0,
                effort = state.selectedEffort,
                timestampEpochMs = System.currentTimeMillis()
            )
            val setId = repository.logSet(newSet)
            val refreshedSets = repository.getSetsForSession(session.id)

            // Start automatic circular rest timer (60-90s default 75s) unless cooldown
            val restSec = slot.templateExercise.restSec.takeIf { it > 0 } ?: state.defaultRestSec
            val shouldStartRest = slot.templateExercise.restSec > 0
            val now = System.currentTimeMillis()
            val restEnd = if (shouldStartRest) now + restSec * 1000L else null

            val advanceExercise = nextSetNo >= slot.effectiveSets && state.currentExerciseIndex < state.exerciseSlots.lastIndex
            val nextIdx = if (advanceExercise) state.currentExerciseIndex + 1 else state.currentExerciseIndex
            val nextSlot = state.exerciseSlots.getOrNull(nextIdx)

            if (restEnd != null && nextSlot != null) {
                alarmScheduler.scheduleRestTimerAlarm(restEnd, nextSlot.exercise.name)
            }

            val persisted = activePersistState?.copy(
                currentExerciseIndex = nextIdx,
                currentSetNo = if (advanceExercise) 1 else nextSetNo + 1,
                restEndEpochMs = restEnd,
                restTotalSec = restSec,
                lastInteractionEpochMs = now
            )
            if (persisted != null) {
                activePersistState = persisted
                repository.saveActiveWorkoutState(persisted)
            }

            _uiState.update {
                it.copy(
                    loggedSets = refreshedSets,
                    lastUndoableSetId = setId,
                    currentExerciseIndex = nextIdx,
                    inputWeightKg = if (advanceExercise && nextSlot != null) nextSlot.recommendation.recommendedWeightKg else it.inputWeightKg,
                    inputReps = if (advanceExercise && nextSlot != null) nextSlot.recommendation.recommendedReps else it.inputReps,
                    inputSeconds = if (advanceExercise && nextSlot != null) nextSlot.recommendation.recommendedSeconds else it.inputSeconds,
                    restRemainingSec = if (shouldStartRest) restSec else 0,
                    restTotalSec = restSec,
                    bannerMessage = "Set $nextSetNo logged for ${slot.exercise.name}."
                )
            }
        }
    }

    fun undoLastSet() {
        viewModelScope.launch {
            val state = _uiState.value
            val session = state.activeSession ?: return@launch
            val undoId = state.lastUndoableSetId ?: return@launch
            repository.undoSet(undoId)
            alarmScheduler.cancelRestTimerAlarm()
            val refreshed = repository.getSetsForSession(session.id)
            activePersistState = activePersistState?.copy(restEndEpochMs = null)
            activePersistState?.let { repository.saveActiveWorkoutState(it) }
            _uiState.update {
                it.copy(
                    loggedSets = refreshed,
                    lastUndoableSetId = null,
                    restRemainingSec = 0,
                    bannerMessage = "Last set undone."
                )
            }
        }
    }

    fun addRestSeconds(extraSec: Int) {
        viewModelScope.launch {
            val persisted = activePersistState ?: return@launch
            val now = System.currentTimeMillis()
            val currentEnd = persisted.restEndEpochMs ?: now
            val newEnd = max(now, currentEnd) + extraSec * 1000L
            val newTotal = (persisted.restTotalSec + extraSec).coerceIn(15, 300)
            val updated = persisted.copy(restEndEpochMs = newEnd, restTotalSec = newTotal, lastInteractionEpochMs = now)
            activePersistState = updated
            repository.saveActiveWorkoutState(updated)
            val curName = _uiState.value.exerciseSlots.getOrNull(_uiState.value.currentExerciseIndex)?.exercise?.name ?: "Next Set"
            alarmScheduler.scheduleRestTimerAlarm(newEnd, curName)
        }
    }

    fun skipRestTimer() {
        viewModelScope.launch {
            alarmScheduler.cancelRestTimerAlarm()
            val persisted = activePersistState?.copy(restEndEpochMs = null, lastInteractionEpochMs = System.currentTimeMillis())
            if (persisted != null) {
                activePersistState = persisted
                repository.saveActiveWorkoutState(persisted)
            }
            _uiState.update { it.copy(restRemainingSec = 0) }
        }
    }

    fun skipCurrentExercise(reason: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val session = state.activeSession ?: return@launch
            val slot = state.exerciseSlots.getOrNull(state.currentExerciseIndex) ?: return@launch
            val cleanReason = reason.trim().ifBlank { "Equipment / fatigue adjustment" }
            repository.logSet(
                SetLogEntity(
                    sessionId = session.id,
                    exerciseId = slot.exercise.id,
                    setNo = 0,
                    weightKg = 0.0,
                    reps = 0,
                    seconds = 0,
                    effort = EffortLevel.EASY,
                    skippedReason = cleanReason,
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
            val nextIdx = (state.currentExerciseIndex + 1).coerceAtMost(state.exerciseSlots.lastIndex)
            selectExerciseIndex(nextIdx)
            _uiState.update {
                it.copy(
                    loggedSets = repository.getSetsForSession(session.id),
                    bannerMessage = "Skipped ${slot.exercise.name}: $cleanReason"
                )
            }
        }
    }

    /**
     * Deterministic Troubleshoot Flow (Section 7.4).
     */
    fun handleTroubleshoot(issue: TroubleshootIssue) {
        viewModelScope.launch {
            val state = _uiState.value
            val slot = state.exerciseSlots.getOrNull(state.currentExerciseIndex) ?: return@launch
            val ex = slot.exercise
            touchInteraction()

            when (issue) {
                TroubleshootIssue.PAIN_OR_DIZZY -> {
                    // NON-NEGOTIABLE Section 6: Full-screen STOP card, no option to push through
                    alarmScheduler.cancelRestTimerAlarm()
                    _uiState.update { it.copy(showSafetyStopCard = true, showCheckinPrompt = false) }
                }
                TroubleshootIssue.TOO_HEAVY -> {
                    if (state.inputWeightKg >= state.weightStepKg) {
                        val nextW = max(0.0, state.inputWeightKg - state.weightStepKg)
                        _uiState.update {
                            it.copy(
                                inputWeightKg = nextW,
                                bannerMessage = "Lowered load to $nextW kg. Keep every rep smooth."
                            )
                        }
                    } else if (ex.easierId != null) {
                        swapCurrentExerciseTo(ex.easierId, "Swapped to easier variant for clean control.")
                    } else {
                        _uiState.update {
                            it.copy(
                                inputReps = max(4, it.inputReps - 2),
                                bannerMessage = "Trimmed target by 2 reps — focus on strict posture."
                            )
                        }
                    }
                }
                TroubleshootIssue.TOO_LIGHT -> {
                    if (!ex.isTimed && state.inputWeightKg > 0.0) {
                        val nextW = state.inputWeightKg + state.weightStepKg
                        _uiState.update {
                            it.copy(
                                inputWeightKg = nextW,
                                bannerMessage = "Raised load to $nextW kg."
                            )
                        }
                    } else if (ex.harderId != null) {
                        requestSwapVariant(ex.harderId)
                    } else {
                        _uiState.update {
                            it.copy(
                                inputReps = it.inputReps + 2,
                                bannerMessage = "Added +2 controlled reps with 3-second lowering."
                            )
                        }
                    }
                }
                TroubleshootIssue.CANNOT_DO_FORM -> {
                    if (ex.easierId != null) {
                        swapCurrentExerciseTo(ex.easierId, "Switched to easier variant at 0.5x video speed + cues expanded.")
                    }
                    _uiState.update {
                        it.copy(expandFormCues = true, forceSlowMotionCues = true)
                    }
                }
                TroubleshootIssue.NO_EQUIPMENT -> {
                    val sub = ex.substituteId ?: ex.easierId
                    if (sub != null) {
                        swapCurrentExerciseTo(sub, "Swapped to home bodyweight substitute.")
                    } else {
                        _uiState.update { it.copy(bannerMessage = "Perform bodyweight tempo movement with strict control.") }
                    }
                }
                TroubleshootIssue.SHORT_ON_TIME -> {
                    val persisted = activePersistState ?: return@launch
                    val session = state.activeSession ?: return@launch
                    val updated = persisted.copy(dropLastAccessorySet = true)
                    activePersistState = updated
                    repository.saveActiveWorkoutState(updated)
                    loadSessionIntoUi(session, updated)
                    _uiState.update {
                        it.copy(bannerMessage = "Short-on-time mode: dropped final set of accessories (warm-up kept intact).")
                    }
                }
                TroubleshootIssue.FEELING_TIRED -> {
                    val persisted = activePersistState ?: return@launch
                    val session = state.activeSession ?: return@launch
                    val updated = persisted.copy(cutHalfSets = true)
                    activePersistState = updated
                    repository.saveActiveWorkoutState(updated)
                    loadSessionIntoUi(session, updated)
                    _uiState.update {
                        it.copy(bannerMessage = "Low-energy protocol: cut sets in half (never zero) — finish crisp and rest.")
                    }
                }
            }
        }
    }

    fun requestSwapVariant(targetExerciseId: String) {
        viewModelScope.launch {
            val target = repository.getExercise(targetExerciseId) ?: return@launch
            val state = _uiState.value
            if (target.isBarbellGated) {
                val gate = coachEngine.canUnlockBarbell(
                    programWeek = state.programWeek,
                    checklistAllConfirmed = state.barbellSafetyUnlocked,
                    exerciseName = target.name
                )
                if (!gate.allowed) {
                    if (state.programWeek >= 5) {
                        _uiState.update { it.copy(showBarbellSafetyGateForId = targetExerciseId) }
                    } else {
                        _uiState.update { it.copy(bannerMessage = gate.reason) }
                    }
                    return@launch
                }
            }
            swapCurrentExerciseTo(targetExerciseId, "Swapped to ${target.name}.")
        }
    }

    fun confirmBarbellSafetyChecklist() {
        viewModelScope.launch {
            val targetId = _uiState.value.showBarbellSafetyGateForId ?: return@launch
            val profile = repository.getProfileOrDefault()
            repository.updateProfile(profile.copy(barbellSafetyUnlocked = true))
            _uiState.update { it.copy(barbellSafetyUnlocked = true, showBarbellSafetyGateForId = null) }
            swapCurrentExerciseTo(targetId, "Barbell safety verified — light load with collars only.")
        }
    }

    fun dismissBarbellGate() {
        _uiState.update { it.copy(showBarbellSafetyGateForId = null) }
    }

    fun dismissCheckinPromptAndContinue() {
        touchInteraction(extendPlannedMinutes = 10)
        _uiState.update { it.copy(showCheckinPrompt = false) }
    }

    fun confirmEmergencySafetyStop() {
        viewModelScope.launch {
            val session = _uiState.value.activeSession
            alarmScheduler.cancelRestTimerAlarm()
            if (session != null) {
                repository.updateSession(
                    session.copy(
                        endEpochMs = System.currentTimeMillis(),
                        completed = false,
                        stoppedForSafety = true,
                        note = "Stopped immediately via Pain/Dizziness safety protocol.",
                        coachVerdict = "Session ended safely. Rest, hydrate, tell an adult, and consult a doctor if symptoms continue."
                    )
                )
            }
            activePersistState = null
            repository.clearActiveWorkoutState()
            _uiState.update {
                it.copy(
                    activeSession = null,
                    exerciseSlots = emptyList(),
                    showSafetyStopCard = false,
                    restRemainingSec = 0,
                    bannerMessage = "Session ended safely. Sit down, drink water, and inform an adult."
                )
            }
        }
    }

    fun finishWorkoutSession(rating: Int, note: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val session = state.activeSession ?: return@launch
            alarmScheduler.cancelRestTimerAlarm()

            val sessionSets = repository.getSetsForSession(session.id).filter { it.skippedReason == null }
            val allSetsBefore = repository.allSetsFlow.first().filter { it.sessionId != session.id && it.skippedReason == null }

            val totalVolume = sessionSets.sumOf { it.weightKg * it.reps }
            val pbs = mutableListOf<String>()
            val exercisesMap = repository.getAllExercises().associateBy { it.id }

            sessionSets.groupBy { it.exerciseId }.forEach { (exId, sets) ->
                val exName = exercisesMap[exId]?.name ?: exId
                val prevSets = allSetsBefore.filter { it.exerciseId == exId }
                val bestTodayScore = sets.maxOfOrNull { it.weightKg * 100 + it.reps + it.seconds } ?: 0.0
                val bestPrevScore = prevSets.maxOfOrNull { it.weightKg * 100 + it.reps + it.seconds } ?: 0.0
                if (bestTodayScore > bestPrevScore && bestTodayScore > 0.0) {
                    pbs += exName
                }
            }

            val verdict = coachEngine.sessionFinishVerdict(
                completedSets = sessionSets.size,
                hitPersonalBest = pbs.isNotEmpty(),
                rating = rating.coerceIn(1, 5)
            )

            repository.updateSession(
                session.copy(
                    endEpochMs = System.currentTimeMillis(),
                    rating = rating.coerceIn(1, 5),
                    note = note.trim(),
                    completed = sessionSets.isNotEmpty(),
                    coachVerdict = verdict
                )
            )
            activePersistState = null
            repository.clearActiveWorkoutState()

            _uiState.update {
                it.copy(
                    activeSession = null,
                    exerciseSlots = emptyList(),
                    restRemainingSec = 0,
                    showCheckinPrompt = false,
                    finishSummary = FinishSummaryUi(
                        templateName = session.templateName,
                        totalSets = sessionSets.size,
                        totalVolumeKg = totalVolume,
                        personalBests = pbs,
                        coachVerdict = verdict,
                        sessionId = session.id
                    )
                )
            }
        }
    }

    fun dismissFinishSummary() {
        _uiState.update { it.copy(finishSummary = null) }
    }

    private suspend fun swapCurrentExerciseTo(newExerciseId: String, message: String) {
        val persisted = activePersistState ?: return
        val session = _uiState.value.activeSession ?: return
        val slot = _uiState.value.exerciseSlots.getOrNull(_uiState.value.currentExerciseIndex) ?: return
        val map = parseOverrides(persisted.exerciseOverridesJson).toMutableMap()
        map[slot.templateExercise.exerciseId] = newExerciseId
        val updated = persisted.copy(
            exerciseOverridesJson = gson.toJson(map),
            lastInteractionEpochMs = System.currentTimeMillis()
        )
        activePersistState = updated
        repository.saveActiveWorkoutState(updated)
        loadSessionIntoUi(session, updated)
        _uiState.update { it.copy(bannerMessage = message) }
    }

    private fun touchInteraction(newExerciseIndex: Int? = null, extendPlannedMinutes: Int = 0) {
        viewModelScope.launch {
            val persisted = activePersistState ?: return@launch
            val now = System.currentTimeMillis()
            val updated = persisted.copy(
                currentExerciseIndex = newExerciseIndex ?: persisted.currentExerciseIndex,
                lastInteractionEpochMs = now,
                plannedEndEpochMs = if (extendPlannedMinutes > 0) now + extendPlannedMinutes * 60_000L else persisted.plannedEndEpochMs
            )
            activePersistState = updated
            repository.saveActiveWorkoutState(updated)
        }
    }

    private fun parseOverrides(json: String): Map<String, String> = runCatching {
        val type = object : TypeToken<Map<String, String>>() {}.type
        gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
    }.getOrDefault(emptyMap())
}
