package com.forge.imperiale.ui.workout

import android.app.Activity
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.imperiale.R
import com.forge.imperiale.data.local.entity.EffortLevel
import com.forge.imperiale.scheduler.SchedulerEngine
import com.forge.imperiale.ui.components.LinktreeBentoCard
import com.forge.imperiale.ui.components.LinktreeColorBlockHero
import com.forge.imperiale.ui.components.LinktreeMonoChip
import com.forge.imperiale.ui.components.LinktreeOutlinePillButton
import com.forge.imperiale.ui.components.LinktreePillButton
import com.forge.imperiale.ui.theme.ForgePalette
import com.forge.imperiale.ui.theme.LocalForgeColors
import kotlinx.coroutines.delay

@Composable
fun WorkoutScreen(
    requestedTemplateId: String? = null,
    onOpenExerciseLibrary: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalForgeColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // Start requested template if navigated from Today "Start Workout" button
    LaunchedEffect(requestedTemplateId) {
        if (!requestedTemplateId.isNullOrBlank() && state.activeSession == null) {
            viewModel.startWorkoutByTemplate(requestedTemplateId)
        }
    }

    // Keep screen awake during active workout (Section 2 & Section 7.3)
    DisposableEffect(state.activeSession != null) {
        val window = (context as? Activity)?.window
        if (state.activeSession != null) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Haptic + beep when circular rest timer reaches zero
    var previousRestSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.restRemainingSec) {
        if (previousRestSec > 0 && state.restRemainingSec == 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            playCoachBeep(250)
        }
        previousRestSec = state.restRemainingSec
    }

    // NON-NEGOTIABLE Section 6: Full-Screen Safety STOP Card
    if (state.showSafetyStopCard) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ForgePalette.Currant)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            LinktreeMonoChip("MEDICAL & SAFETY GUARDRAIL", ForgePalette.TrueBlack, ForgePalette.Blush)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.stop_card_title),
                style = MaterialTheme.typography.displayLarge,
                color = ForgePalette.PureWhite
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.stop_card_body),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = ForgePalette.Blush
            )
            Spacer(modifier = Modifier.height(28.dp))
            LinktreePillButton(
                text = stringResource(R.string.stop_card_action),
                containerColor = ForgePalette.PureWhite,
                contentColor = ForgePalette.Currant,
                onClick = viewModel::confirmEmergencySafetyStop,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    // Finish Summary Screen (Section 7.3)
    state.finishSummary?.let { summary ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                LinktreeColorBlockHero(
                    eyebrow = "SESSION LOGGED • WEEK ${state.programWeek}",
                    headline = "${summary.templateName.uppercase()} COMPLETE",
                    badgeText = "${summary.totalSets} SETS",
                    subtitle = summary.coachVerdict
                )
            }
            item {
                LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TOTAL SETS", style = MaterialTheme.typography.labelMedium, color = colors.mutedText)
                            Text("${summary.totalSets}", style = MaterialTheme.typography.displaySmall, color = ForgePalette.Chartreuse)
                        }
                        Column {
                            Text("VOLUME MOVED", style = MaterialTheme.typography.labelMedium, color = colors.mutedText)
                            Text("${summary.totalVolumeKg.toInt()} kg", style = MaterialTheme.typography.displaySmall, color = ForgePalette.Canopy)
                        }
                    }
                    if (summary.personalBests.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinktreeMonoChip("NEW PERSONAL BESTS", ForgePalette.Forest, ForgePalette.Chartreuse)
                        Spacer(modifier = Modifier.height(6.dp))
                        summary.personalBests.forEach { pb ->
                            Text("• $pb", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    LinktreePillButton(
                        text = "Done — Back to Workouts",
                        onClick = viewModel::dismissFinishSummary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        return
    }

    // Workout Hub Picker when no active session is running
    if (state.activeSession == null || state.exerciseSlots.isEmpty()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                LinktreeColorBlockHero(
                    eyebrow = "WEEK ${state.programWeek} OF 8 • HOME TRAINING",
                    headline = "STRICT TRAINING FLOOR",
                    badgeText = if (state.todayDayOfWeek == 7) "REST DAY LOCKED" else "READY",
                    subtitle = "Every exercise streams an auto-looping movement guide with double-progression targets.",
                    bottomAction = {
                        LinktreePillButton(
                            text = "Exercise Library & Video Manager",
                            leadingIcon = Icons.Default.VideoLibrary,
                            containerColor = ForgePalette.TrueBlack,
                            contentColor = ForgePalette.Chartreuse,
                            onClick = onOpenExerciseLibrary
                        )
                    }
                )
            }

            state.bannerMessage?.let { msg ->
                item {
                    LinktreeBentoCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        containerColor = colors.dangerBg,
                        borderColor = ForgePalette.Rose
                    ) {
                        Text(msg, style = MaterialTheme.typography.bodyMedium, color = colors.dangerInk)
                    }
                }
            }

            items(state.templates, key = { it.id }) { tmpl ->
                val isToday = tmpl.dayOfWeek == state.todayDayOfWeek
                LinktreeBentoCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = if (isToday) colors.workoutBg else colors.cardSurface,
                    borderColor = if (isToday) ForgePalette.Chartreuse else colors.cardBorder
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinktreeMonoChip(
                            text = "${SchedulerEngine.dayName(tmpl.dayOfWeek)} • ${tmpl.durationMinutes} MIN",
                            backgroundColor = ForgePalette.TrueBlack.copy(alpha = 0.35f),
                            contentColor = if (isToday) colors.workoutInk else ForgePalette.Chartreuse,
                            locked = tmpl.dayOfWeek == 7
                        )
                        if (isToday) {
                            LinktreeMonoChip("TODAY'S SLOT", ForgePalette.Chartreuse, ForgePalette.TrueBlack)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = tmpl.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (isToday) colors.workoutInk else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tmpl.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isToday) colors.workoutInk.copy(alpha = 0.85f) else colors.mutedText
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinktreePillButton(
                        text = if (tmpl.dayOfWeek == 7) "Start Recovery Walk" else "Start ${tmpl.name}",
                        trailingArrow = true,
                        onClick = { viewModel.startWorkoutByTemplate(tmpl.id) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(104.dp)) }
        }
        return
    }

    // ACTIVE WORKOUT SESSION SCREEN (Section 7.3)
    val activeSession = state.activeSession ?: return
    val currentSlot = state.exerciseSlots[state.currentExerciseIndex]
    val nextSlot = state.exerciseSlots.getOrNull(state.currentExerciseIndex + 1)
    val exSetsDone = state.loggedSets.filter { it.exerciseId == currentSlot.exercise.id && it.skippedReason == null }
    val totalTargetSets = state.exerciseSlots.sumOf { it.effectiveSets }.coerceAtLeast(1)
    val totalDoneSets = state.loggedSets.count { it.skippedReason == null }

    var showProblemDialog by rememberSaveable { mutableStateOf(false) }
    var showSkipDialog by rememberSaveable { mutableStateOf(false) }
    var showFinishDialog by rememberSaveable { mutableStateOf(false) }

    // Timed hold / cardio countdown state
    var timedRunning by remember(currentSlot.exercise.id, exSetsDone.size) { mutableStateOf(false) }
    var timedLeftSec by remember(currentSlot.exercise.id, state.inputSeconds, exSetsDone.size) {
        mutableIntStateOf(state.inputSeconds)
    }
    LaunchedEffect(timedRunning, currentSlot.exercise.id) {
        val halfPoint = (state.inputSeconds / 2).coerceAtLeast(1)
        while (timedRunning && timedLeftSec > 0) {
            delay(1_000L)
            timedLeftSec -= 1
            if (timedLeftSec == halfPoint) {
                playCoachBeep(140)
            } else if (timedLeftSec in 1..3) {
                playCoachBeep(90)
            } else if (timedLeftSec == 0) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                playCoachBeep(300)
                timedRunning = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Session Header: Elapsed Time, Progress Bar, Pause & Finish
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardElevated)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = activeSession.templateName.uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = ForgePalette.Chartreuse
                        )
                        Text(
                            text = "ELAPSED ${"%02d:%02d".format(state.elapsedSeconds / 60, state.elapsedSeconds % 60)}${if (state.isPaused) " (PAUSED)" else ""}",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = viewModel::togglePauseSession) {
                            Icon(
                                imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (state.isPaused) "Resume workout" else "Pause workout",
                                tint = ForgePalette.Chartreuse
                            )
                        }
                        LinktreeOutlinePillButton("Finish", onClick = { showFinishDialog = true })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (totalDoneSets.toFloat() / totalTargetSets).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape),
                    color = ForgePalette.Chartreuse,
                    trackColor = ForgePalette.TrueBlack
                )
            }
        }

        // 2. Top 16:9 Auto-Looping Video Guide
        item {
            ExerciseVideoPlayer(
                exercise = currentSlot.exercise,
                forceSlowSpeed = state.forceSlowMotionCues,
                onOpenVideoManager = onOpenExerciseLibrary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Banner / Undo Bar
        state.bannerMessage?.let { msg ->
            item {
                LinktreeBentoCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = colors.workoutBg
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.workoutInk,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.lastUndoableSetId != null) {
                            TextButton(onClick = viewModel::undoLastSet) {
                                Icon(Icons.Default.Undo, contentDescription = null, tint = ForgePalette.Chartreuse)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Undo", color = ForgePalette.Chartreuse)
                            }
                        }
                    }
                }
            }
        }

        // 3. Circular Rest Timer Card (when active between sets)
        if (state.restRemainingSec > 0) {
            item {
                LinktreeBentoCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = ForgePalette.Forest,
                    borderColor = ForgePalette.Chartreuse
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(78.dp)) {
                            val progress = (state.restRemainingSec.toFloat() / state.restTotalSec.coerceAtLeast(1)).coerceIn(0f, 1f)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = ForgePalette.TrueBlack.copy(alpha = 0.45f),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = ForgePalette.Chartreuse,
                                    startAngle = -90f,
                                    sweepAngle = 360f * progress,
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Text(
                                text = "${state.restRemainingSec}s",
                                style = MaterialTheme.typography.titleLarge,
                                color = ForgePalette.Chartreuse
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("REST TIMER ACTIVE", style = MaterialTheme.typography.labelMedium, color = ForgePalette.Chartreuse)
                            Text("Works with screen off • Breathe & reset posture", style = MaterialTheme.typography.bodySmall, color = ForgePalette.PureWhite)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinktreeOutlinePillButton(
                                    text = "+15s",
                                    borderColor = ForgePalette.Chartreuse,
                                    contentColor = ForgePalette.Chartreuse,
                                    onClick = { viewModel.addRestSeconds(15) }
                                )
                                LinktreePillButton(
                                    text = "Skip Rest",
                                    containerColor = ForgePalette.Chartreuse,
                                    contentColor = ForgePalette.TrueBlack,
                                    onClick = viewModel::skipRestTimer
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Exercise Header, Set Chips, and Progression Target Card
        item {
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinktreeMonoChip(
                        text = "EXERCISE ${state.currentExerciseIndex + 1} OF ${state.exerciseSlots.size}",
                        backgroundColor = ForgePalette.Chartreuse,
                        contentColor = ForgePalette.TrueBlack
                    )
                    Text(currentSlot.exercise.muscles, style = MaterialTheme.typography.labelSmall, color = colors.mutedText)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(currentSlot.exercise.name, style = MaterialTheme.typography.headlineLarge)

                // Row of Set Chips (todo, current, done)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val curSetNo = exSetsDone.size + 1
                    for (s in 1..currentSlot.effectiveSets) {
                        val isDone = s <= exSetsDone.size
                        val isCurrent = s == curSetNo
                        val bg = when {
                            isDone -> ForgePalette.Canopy
                            isCurrent -> ForgePalette.Chartreuse
                            else -> colors.cardElevated
                        }
                        val ink = if (isDone || isCurrent) ForgePalette.TrueBlack else colors.mutedText
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(bg)
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isDone) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = ink, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = if (isDone) "Set $s Done" else if (isCurrent) "Set $s • Current" else "Set $s",
                                style = MaterialTheme.typography.labelMedium,
                                color = ink
                            )
                        }
                    }
                }

                // Target Card (rep range, suggested weight, last session numbers)
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .background(colors.cardElevated)
                        .padding(12.dp)
                ) {
                    Text(
                        text = currentSlot.recommendation.phaseLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = ForgePalette.Chartreuse
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val targetLine = if (currentSlot.exercise.isTimed) {
                        "Target Hold / Time: ${currentSlot.recommendation.recommendedSeconds}s"
                    } else {
                        "Target: ${currentSlot.templateExercise.repMin}-${currentSlot.templateExercise.repMax} reps • Suggested ${currentSlot.recommendation.recommendedWeightKg} kg"
                    }
                    Text(targetLine, style = MaterialTheme.typography.titleMedium)
                    Text(currentSlot.recommendation.lastSessionSummary, style = MaterialTheme.typography.bodySmall, color = ForgePalette.Canopy)
                    Text(currentSlot.recommendation.rationale, style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
                }
            }
        }

        // 5. Steppers / Timed Countdown + Effort Selector + Thumb-Reachable SET DONE Button
        item {
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (currentSlot.exercise.isTimed) {
                    val halfMark = state.inputSeconds / 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TIMED EXERCISE COUNTDOWN", style = MaterialTheme.typography.labelMedium, color = colors.mutedText)
                            Text(
                                text = "%02d:%02d".format(timedLeftSec / 60, timedLeftSec % 60),
                                style = MaterialTheme.typography.displayMedium,
                                color = if (timedLeftSec <= halfMark && timedRunning) ForgePalette.Canopy else ForgePalette.Chartreuse
                            )
                            if (timedRunning && timedLeftSec <= halfMark) {
                                Text("Half-way passed — stay steady!", style = MaterialTheme.typography.bodySmall, color = ForgePalette.Canopy)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinktreeOutlinePillButton("-5s", onClick = { viewModel.adjustSeconds(-5) })
                            LinktreeOutlinePillButton("+5s", onClick = { viewModel.adjustSeconds(5) })
                            LinktreePillButton(
                                text = if (timedRunning) "Pause" else "Start Timer",
                                onClick = { timedRunning = !timedRunning }
                            )
                        }
                    }
                } else {
                    // Weight Stepper & Reps Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StepperColumn(
                            label = "WEIGHT (KG)",
                            valueText = "${state.inputWeightKg}",
                            onMinus = { viewModel.adjustWeight(-1) },
                            onPlus = { viewModel.adjustWeight(1) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StepperColumn(
                            label = "REPS",
                            valueText = "${state.inputReps}",
                            onMinus = { viewModel.adjustReps(-1) },
                            onPlus = { viewModel.adjustReps(1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("EFFORT RATING", style = MaterialTheme.typography.labelMedium, color = colors.mutedText)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EffortLevel.entries.forEach { eff ->
                        FilterChip(
                            selected = state.selectedEffort == eff,
                            onClick = { viewModel.selectEffort(eff) },
                            label = { Text(eff.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                LinktreePillButton(
                    text = "Set Done — Log Set ${exSetsDone.size + 1}",
                    leadingIcon = Icons.Default.Check,
                    onClick = viewModel::markSetDone,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 6. Troubleshoot ('Problem?'), Easier/Harder Variant Swap, Skip, Form Cues & Next Up
        item {
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinktreePillButton(
                        text = "Problem?",
                        leadingIcon = Icons.Default.Warning,
                        containerColor = ForgePalette.Marigold,
                        contentColor = ForgePalette.TrueBlack,
                        onClick = { showProblemDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    LinktreeOutlinePillButton(
                        text = if (state.expandFormCues) "Hide Cues" else "Form Cues",
                        onClick = viewModel::toggleExpandCues,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currentSlot.exercise.easierId?.let { easier ->
                        LinktreeOutlinePillButton(
                            text = "Easier Variant",
                            onClick = { viewModel.requestSwapVariant(easier) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    currentSlot.exercise.harderId?.let { harder ->
                        LinktreeOutlinePillButton(
                            text = "Harder Variant",
                            onClick = { viewModel.requestSwapVariant(harder) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    LinktreeOutlinePillButton(
                        text = "Skip",
                        onClick = { showSkipDialog = true },
                        modifier = Modifier.weight(0.8f)
                    )
                }

                if (state.expandFormCues) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("FORM CUES (SECTION 4):", style = MaterialTheme.typography.labelMedium, color = ForgePalette.Chartreuse)
                    Text(currentSlot.exercise.cues, style = MaterialTheme.typography.bodyMedium)
                }

                // Exercise Switcher Strip + Next Up Preview
                if (nextSlot != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                            .background(colors.cardElevated)
                            .clickable { viewModel.selectExerciseIndex(state.currentExerciseIndex + 1) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("NEXT UP", style = MaterialTheme.typography.labelSmall, color = ForgePalette.Chartreuse)
                            Text(nextSlot.exercise.name, style = MaterialTheme.typography.titleMedium)
                        }
                        Text("${nextSlot.effectiveSets} sets →", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(104.dp)) }
    }

    // Troubleshoot Dialog (Section 7.4: 7 deterministic options)
    if (showProblemDialog) {
        AlertDialog(
            onDismissRequest = { showProblemDialog = false },
            title = { Text("What's the problem right now?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TroubleshootIssue.entries.forEach { issue ->
                        val isStop = issue == TroubleshootIssue.PAIN_OR_DIZZY
                        LinktreePillButton(
                            text = issue.label,
                            containerColor = if (isStop) ForgePalette.Currant else colors.cardElevated,
                            contentColor = if (isStop) ForgePalette.PureWhite else MaterialTheme.colorScheme.onSurface,
                            onClick = {
                                showProblemDialog = false
                                viewModel.handleTroubleshoot(issue)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProblemDialog = false }) { Text("Close") }
            }
        )
    }

    // Skip Exercise Reason Required Dialog
    if (showSkipDialog) {
        var skipReason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSkipDialog = false },
            title = { Text("Skip ${currentSlot.exercise.name}") },
            text = {
                OutlinedTextField(
                    value = skipReason,
                    onValueChange = { skipReason = it },
                    label = { Text("Reason (required, e.g. equipment busy)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = skipReason.trim().length >= 3,
                    onClick = {
                        viewModel.skipCurrentExercise(skipReason)
                        showSkipDialog = false
                    }
                ) { Text("Confirm Skip") }
            },
            dismissButton = {
                TextButton(onClick = { showSkipDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Mid-Session 10-Min Idle / Planned-End Check-In (Section 7.3: 'Finished or problem?')
    if (state.showCheckinPrompt) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCheckinPromptAndContinue,
            title = { Text("Finished or problem?") },
            text = { Text("Planned session time passed or no set was logged for 10 minutes.") },
            confirmButton = {
                LinktreePillButton(
                    text = "Finished Session",
                    onClick = {
                        viewModel.dismissCheckinPromptAndContinue()
                        showFinishDialog = true
                    }
                )
            },
            dismissButton = {
                LinktreeOutlinePillButton(
                    text = "Have a Problem",
                    onClick = {
                        viewModel.dismissCheckinPromptAndContinue()
                        showProblemDialog = true
                    }
                )
            }
        )
    }

    // Barbell Week 5+ Safety Checklist Gate (Section 4)
    if (state.showBarbellSafetyGateForId != null) {
        var c1 by remember { mutableStateOf(false) }
        var c2 by remember { mutableStateOf(false) }
        var c3 by remember { mutableStateOf(false) }
        var c4 by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = viewModel::dismissBarbellGate,
            title = { Text("Week 5+ Light Barbell Safety Gate") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SafetyCheckItem("Collars are locked tightly on both ends of the bar.", c1) { c1 = it }
                    SafetyCheckItem("Load is light and well within strict control.", c2) { c2 = it }
                    SafetyCheckItem("Floor space is completely clear and an adult is at home.", c3) { c3 = it }
                    SafetyCheckItem("I understand barbell bench press alone is NEVER performed.", c4) { c4 = it }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = c1 && c2 && c3 && c4,
                    onClick = viewModel::confirmBarbellSafetyChecklist
                ) { Text("Unlock Light Barbell") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBarbellGate) { Text("Stay With Dumbbells") }
            }
        )
    }

    // Finish Session Rating + Note Dialog
    if (showFinishDialog) {
        var rating by remember { mutableIntStateOf(4) }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Finish & Rate Session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Session Effort / Quality (1-5):")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (r in 1..5) {
                            FilterChip(
                                selected = rating == r,
                                onClick = { rating = r },
                                label = { Text("$r") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Optional session note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFinishDialog = false
                    viewModel.finishWorkoutSession(rating, note)
                }) { Text("Save Session") }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text("Keep Training") }
            }
        )
    }
}

@Composable
private fun StepperColumn(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinktreeOutlinePillButton("-", onClick = onMinus)
            Text(valueText, style = MaterialTheme.typography.headlineMedium)
            LinktreeOutlinePillButton("+", onClick = onPlus)
        }
    }
}

@Composable
private fun SafetyCheckItem(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

private fun playCoachBeep(durationMs: Int) {
    runCatching {
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP, durationMs)
    }
}
