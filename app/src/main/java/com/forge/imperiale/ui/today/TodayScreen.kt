package com.forge.imperiale.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forge.imperiale.calendar.CalendarAndScheduleOrchestrator
import com.forge.imperiale.coach.CoachContext
import com.forge.imperiale.coach.CoachEngine
import com.forge.imperiale.coach.CoachVerdict
import com.forge.imperiale.data.local.entity.BlockType
import com.forge.imperiale.data.local.entity.MealLogEntity
import com.forge.imperiale.data.local.entity.MealStatus
import com.forge.imperiale.data.local.entity.ScheduleBlockEntity
import com.forge.imperiale.data.repository.ForgeRepository
import com.forge.imperiale.domain.progression.ProgressionEngine
import com.forge.imperiale.scheduler.SchedulerEngine
import com.forge.imperiale.ui.components.LinktreeBentoCard
import com.forge.imperiale.ui.components.LinktreeColorBlockHero
import com.forge.imperiale.ui.components.LinktreeMonoChip
import com.forge.imperiale.ui.components.LinktreeOutlinePillButton
import com.forge.imperiale.ui.components.LinktreePillButton
import com.forge.imperiale.ui.theme.ForgePalette
import com.forge.imperiale.ui.theme.LocalForgeColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class TodayUiState(
    val userName: String = "Arnab",
    val programWeek: Int = 1,
    val dayOfWeek: Int = LocalDate.now().dayOfWeek.value,
    val todayDateIso: String = LocalDate.now().toString(),
    val streakCount: Int = 0,
    val coachVerdict: CoachVerdict = CoachVerdict("Session lined up. Shoes on."),
    val timelineBlocks: List<ScheduleBlockEntity> = emptyList(),
    val completedMealSlots: Set<String> = emptySet(),
    val todayWorkoutCompleted: Boolean = false,
    val activeWorkoutTemplateId: String? = null,
    val waterGlasses: Int = 0,
    val waterGoal: Int = 11,
    val checklistDoneCount: Int = 0,
    val checklistTotalCount: Int = 7
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: ForgeRepository,
    private val orchestrator: CalendarAndScheduleOrchestrator,
    private val coachEngine: CoachEngine
) : ViewModel() {

    private val today = LocalDate.now()
    private val todayIso = today.toString()
    private val weekStartIso = today.minusDays((today.dayOfWeek.value - 1).toLong()).toString()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            if (repository.getAllBlocks().none { it.source == "SCHEDULER" }) {
                orchestrator.rebuildScheduleAndSync(false)
            }
        }
    }

    val uiState: StateFlow<TodayUiState> = combine(
        repository.profileFlow,
        repository.observeBlocksForDay(today.dayOfWeek.value),
        repository.sessionsFlow,
        repository.observeMealLogsBetween(weekStartIso, todayIso),
        combine(
            repository.observeWaterForDate(todayIso),
            repository.activeWorkoutFlow,
            repository.checkinsFlow
        ) { w, a, c -> Triple(w, a, c) }
    ) { profile, dayBlocks, sessions, weekMeals, triple ->
        val (waterLog, activeState, checkins) = triple
        val p = profile
        val programWeek = if (p != null) ProgressionEngine.computeProgramWeek(p.startDateIso, today) else 1

        val todayMeals: List<MealLogEntity> = weekMeals.filter { it.dateIso == todayIso }
        val doneMeals = todayMeals.filter { it.status == MealStatus.DONE }.map { it.slotKey }.toSet()
        val skippedWeek = weekMeals.count { it.status == MealStatus.SKIPPED }
        val friedWeek = weekMeals.sumOf { it.friedCount }
        val mishtiWeek = weekMeals.sumOf { it.mishtiCount }

        val completedSessions = sessions.filter { it.completed }
        val todayDone = completedSessions.any { it.dateIso == todayIso }

        // Compute streak of consecutive days with completed workouts (counting Sunday rest as neutral)
        val completedDates = completedSessions.map { it.dateIso }.toSet()
        var streak = 0
        var cursor = if (todayDone) today else today.minusDays(1)
        for (i in 0..56) {
            val iso = cursor.toString()
            if (iso in completedDates) {
                streak++
                cursor = cursor.minusDays(1)
            } else if (cursor.dayOfWeek.value == 7) {
                cursor = cursor.minusDays(1)
            } else {
                break
            }
        }

        val yesterday = today.minusDays(1)
        val missedYesterday = yesterday.dayOfWeek.value != 7 && yesterday.toString() !in completedDates && completedDates.isNotEmpty()

        val nextWorkoutBlock = dayBlocks.firstOrNull { it.type == BlockType.WORKOUT }
        val verdict = coachEngine.evaluateDailyCoach(
            CoachContext(
                currentStreakDays = streak,
                missedYesterdayWorkout = missedYesterday,
                missedWorkoutTitle = SchedulerEngine.dayName(yesterday.dayOfWeek.value) + " Session",
                nextWorkoutTimeFormatted = nextWorkoutBlock?.let { SchedulerEngine.formatMinute(it.startMinute) },
                nextWorkoutTitle = nextWorkoutBlock?.title,
                completedTodayWorkout = todayDone,
                skippedMealsThisWeek = skippedWeek,
                friedItemsThisWeek = friedWeek,
                mishtiThisWeek = mishtiWeek,
                recentMoodScoresNewestFirst = checkins.map { it.mood },
                isSundayRestDay = today.dayOfWeek.value == 7
            )
        )

        val glasses = waterLog?.glasses ?: 0
        val goal = p?.waterGoalGlasses ?: 11
        val totalChecklist = 6 + (if (nextWorkoutBlock != null) 1 else 0)
        val doneCount = doneMeals.size.coerceAtMost(5) +
            (if (glasses >= goal) 1 else 0) +
            (if (todayDone) 1 else 0)

        TodayUiState(
            userName = p?.name ?: "Arnab",
            programWeek = programWeek,
            dayOfWeek = today.dayOfWeek.value,
            todayDateIso = todayIso,
            streakCount = streak,
            coachVerdict = verdict,
            timelineBlocks = dayBlocks.sortedBy { it.startMinute },
            completedMealSlots = doneMeals,
            todayWorkoutCompleted = todayDone,
            activeWorkoutTemplateId = activeState?.templateId,
            waterGlasses = glasses,
            waterGoal = goal,
            checklistDoneCount = doneCount,
            checklistTotalCount = totalChecklist
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun quickLogMealDone(slotKey: String) {
        viewModelScope.launch {
            repository.saveMealLog(
                dateIso = todayIso,
                slotKey = slotKey,
                status = MealStatus.DONE,
                itemsChecked = listOf("Logged on time"),
                scoopedExtraOil = true,
                friedCount = 0,
                mishtiCount = 0
            )
        }
    }

    fun addWaterGlass(delta: Int) {
        viewModelScope.launch {
            repository.adjustWaterGlasses(todayIso, delta)
        }
    }
}

@Composable
fun TodayScreen(
    onStartWorkout: (String?) -> Unit,
    onOpenDiet: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalForgeColors.current

    var nowMinute by remember {
        mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute })
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            val t = LocalTime.now()
            nowMinute = t.hour * 60 + t.minute
        }
    }

    val nextUpBlock = remember(state.timelineBlocks, nowMinute, state.completedMealSlots, state.todayWorkoutCompleted) {
        state.timelineBlocks.firstOrNull { block ->
            val notDone = when (block.type) {
                BlockType.WORKOUT -> !state.todayWorkoutCompleted
                BlockType.MEAL -> block.refId !in state.completedMealSlots
                else -> true
            }
            notDone && block.endMinute >= nowMinute
        } ?: state.timelineBlocks.lastOrNull()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box {
                LinktreeColorBlockHero(
                    eyebrow = "WEEK ${state.programWeek} OF 8 • ${SchedulerEngine.dayName(state.dayOfWeek).uppercase()} • ${state.todayDateIso}",
                    headline = "COACH BRIEFING, ${state.userName.uppercase()}",
                    badgeText = "${state.streakCount}D STREAK",
                    subtitle = state.coachVerdict.headline
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 98.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Settings",
                        tint = colors.heroBannerInk
                    )
                }
            }
        }

        // Safety & Guardrail Banners (Skipped meals >= 2 or Low mood 2 weeks in a row)
        state.coachVerdict.skippedMealSafetyAlert?.let { alert ->
            item {
                LinktreeBentoCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = colors.dangerBg,
                    borderColor = ForgePalette.Rose
                ) {
                    Text("NUTRITION SAFETY GUARDRAIL", style = MaterialTheme.typography.titleSmall, color = ForgePalette.Blush)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(alert, style = MaterialTheme.typography.bodyMedium, color = colors.dangerInk)
                }
            }
        }

        state.coachVerdict.lowMoodSupportAlert?.let { support ->
            item {
                LinktreeBentoCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = ForgePalette.Dahlia,
                    borderColor = ForgePalette.Lavender
                ) {
                    Text("CHECK-IN SUPPORT", style = MaterialTheme.typography.titleSmall, color = ForgePalette.Lavender)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(support, style = MaterialTheme.typography.bodyMedium, color = ForgePalette.PureWhite)
                }
            }
        }

        // Active Resume Card if mid-workout
        if (state.activeWorkoutTemplateId != null) {
            item {
                LinktreeBentoCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = colors.workoutBg,
                    borderColor = ForgePalette.Chartreuse
                ) {
                    LinktreeMonoChip("SESSION IN PROGRESS", ForgePalette.TrueBlack, ForgePalette.Chartreuse)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Paused session saved on device — pick up right at your current set.",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.workoutInk
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinktreePillButton(
                        text = "Resume Active Session",
                        onClick = { onStartWorkout(state.activeWorkoutTemplateId) },
                        trailingArrow = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // NEXT UP Hero Countdown Card (Section 7.2)
        if (nextUpBlock != null) {
            item {
                val minsUntil = (nextUpBlock.startMinute - nowMinute)
                val countdownLabel = when {
                    nowMinute in nextUpBlock.startMinute..nextUpBlock.endMinute -> "HAPPENING NOW"
                    minsUntil > 0 -> "IN ${minsUntil / 60}H ${minsUntil % 60}M"
                    else -> "WRAP UP TODAY"
                }
                LinktreeBentoCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = colors.cardElevated,
                    borderColor = ForgePalette.Chartreuse.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinktreeMonoChip(
                            text = "NEXT UP • ${SchedulerEngine.formatMinute(nextUpBlock.startMinute)}",
                            backgroundColor = ForgePalette.Chartreuse,
                            contentColor = ForgePalette.TrueBlack,
                            locked = nextUpBlock.locked
                        )
                        LinktreeMonoChip(
                            text = countdownLabel,
                            backgroundColor = ForgePalette.Forest,
                            contentColor = ForgePalette.Chartreuse
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(nextUpBlock.title, style = MaterialTheme.typography.headlineMedium)
                    if (nextUpBlock.subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(nextUpBlock.subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    when (nextUpBlock.type) {
                        BlockType.WORKOUT -> {
                            LinktreePillButton(
                                text = "Start Workout",
                                leadingIcon = Icons.Default.FitnessCenter,
                                trailingArrow = true,
                                onClick = { onStartWorkout(nextUpBlock.refId) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        BlockType.MEAL -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                LinktreePillButton(
                                    text = "Log Meal Done",
                                    leadingIcon = Icons.Default.Restaurant,
                                    onClick = { nextUpBlock.refId?.let(viewModel::quickLogMealDone) },
                                    modifier = Modifier.weight(1.4f)
                                )
                                LinktreeOutlinePillButton(
                                    text = "Plate & Protein",
                                    onClick = onOpenDiet,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        else -> {
                            LinktreeOutlinePillButton(
                                text = if (nextUpBlock.type == BlockType.TUITION) "Tuition Protected (15m Buffer)" else "View Full Diet & Water",
                                onClick = onOpenDiet,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Daily Checklist Progress + Quick Water Bar
        item {
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Daily Discipline Checklist",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${state.checklistDoneCount} / ${state.checklistTotalCount}",
                        style = MaterialTheme.typography.titleSmall,
                        color = ForgePalette.Chartreuse
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = {
                        (state.checklistDoneCount.toFloat() / state.checklistTotalCount.coerceAtLeast(1)).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(9.dp)
                        .clip(CircleShape),
                    color = ForgePalette.Canopy,
                    trackColor = colors.cardElevated
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = ForgePalette.Sky)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Water: ${state.waterGlasses} / ${state.waterGoal} glasses (~${"%.1f".format(state.waterGlasses * 0.25)}L)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinktreeOutlinePillButton("-1", onClick = { viewModel.addWaterGlass(-1) })
                        LinktreePillButton(
                            text = "+1 Glass",
                            containerColor = ForgePalette.Sky,
                            contentColor = ForgePalette.TrueBlack,
                            onClick = { viewModel.addWaterGlass(1) }
                        )
                    }
                }
            }
        }

        // Vertical Full-Day Timeline Header
        item {
            Text(
                text = "FULL-DAY TIMELINE",
                style = MaterialTheme.typography.titleSmall,
                color = colors.mutedText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }

        items(state.timelineBlocks, key = { "${it.type}_${it.startMinute}_${it.title}" }) { block ->
            val isTuition = block.type == BlockType.TUITION || block.type == BlockType.SCHOOL
            val isDone = when (block.type) {
                BlockType.WORKOUT -> state.todayWorkoutCompleted
                BlockType.MEAL -> block.refId in state.completedMealSlots
                else -> nowMinute > block.endMinute
            }
            val cardBg = when {
                isTuition -> colors.tuitionBg
                block.type == BlockType.WORKOUT -> colors.workoutBg
                block.type == BlockType.MEAL -> colors.mealBg
                block.type == BlockType.SLEEP -> colors.sleepBg
                else -> colors.cardSurface
            }
            val cardInk = when {
                isTuition -> colors.tuitionInk
                block.type == BlockType.WORKOUT -> colors.workoutInk
                block.type == BlockType.MEAL -> colors.mealInk
                block.type == BlockType.SLEEP -> colors.sleepInk
                else -> MaterialTheme.colorScheme.onSurface
            }

            LinktreeBentoCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                containerColor = cardBg,
                onClick = when (block.type) {
                    BlockType.WORKOUT -> ({ onStartWorkout(block.refId) })
                    BlockType.MEAL -> ({ onOpenDiet() })
                    else -> null
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinktreeMonoChip(
                        text = "${SchedulerEngine.formatMinute(block.startMinute)} - ${SchedulerEngine.formatMinute(block.endMinute)} • ${block.type.name}",
                        backgroundColor = ForgePalette.TrueBlack.copy(alpha = 0.28f),
                        contentColor = cardInk,
                        locked = block.locked
                    )
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = ForgePalette.Canopy
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(block.title, style = MaterialTheme.typography.titleLarge, color = cardInk)
                if (block.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(block.subtitle, style = MaterialTheme.typography.bodySmall, color = cardInk.copy(alpha = 0.85f))
                }
            }
        }

        item { Spacer(modifier = Modifier.height(104.dp)) }
    }
}
