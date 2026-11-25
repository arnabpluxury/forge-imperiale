package com.forge.imperiale.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forge.imperiale.R
import com.forge.imperiale.calendar.CalendarAndScheduleOrchestrator
import com.forge.imperiale.data.local.entity.BlockType
import com.forge.imperiale.data.local.entity.ScheduleBlockEntity
import com.forge.imperiale.data.local.entity.UserProfileEntity
import com.forge.imperiale.data.preferences.ForgePreferencesRepository
import com.forge.imperiale.data.repository.ForgeRepository
import com.forge.imperiale.notifications.AlarmScheduler
import com.forge.imperiale.scheduler.SchedulerEngine
import com.forge.imperiale.ui.components.LinktreeBentoCard
import com.forge.imperiale.ui.components.LinktreeColorBlockHero
import com.forge.imperiale.ui.components.LinktreeMonoChip
import com.forge.imperiale.ui.components.LinktreeOutlinePillButton
import com.forge.imperiale.ui.components.LinktreePillButton
import com.forge.imperiale.ui.theme.ForgePalette
import com.forge.imperiale.ui.theme.LocalForgeColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class OnboardingUiState(
    val profile: UserProfileEntity? = null,
    val tuitionBlocks: List<ScheduleBlockEntity> = emptyList(),
    val weekdayWakeMin: Int = 6 * 60 + 30,
    val wedFriWakeMin: Int = 6 * 60 + 45,
    val weekendWakeMin: Int = 7 * 60 + 30,
    val exactAlarmGranted: Boolean = true
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: ForgeRepository,
    private val prefs: ForgePreferencesRepository,
    private val orchestrator: CalendarAndScheduleOrchestrator,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    val uiState: StateFlow<OnboardingUiState> = combine(
        repository.profileFlow,
        repository.allScheduleBlocksFlow,
        prefs.settingsFlow
    ) { profile, blocks, settings ->
        OnboardingUiState(
            profile = profile,
            tuitionBlocks = blocks.filter { it.type == BlockType.TUITION },
            weekdayWakeMin = settings.weekdayWakeMin,
            wedFriWakeMin = settings.wedFriWakeMin,
            weekendWakeMin = settings.weekendWakeMin,
            exactAlarmGranted = alarmScheduler.canUseExactAlarms()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    fun addQuickTuitionBlock(dayOfWeek: Int, startHour: Int, endHour: Int, subject: String) {
        viewModelScope.launch {
            repository.saveBlock(
                ScheduleBlockEntity(
                    type = BlockType.TUITION,
                    dayOfWeek = dayOfWeek.coerceIn(1, 7),
                    startMinute = (startHour * 60).coerceIn(0, 1410),
                    endMinute = (endHour * 60).coerceIn((startHour * 60) + 30, 1435),
                    title = subject.trim().ifBlank { "Tuition Class" },
                    subtitle = "Locked Study / Tuition",
                    locked = true,
                    source = "USER"
                )
            )
        }
    }

    fun deleteTuitionBlock(id: Long) {
        viewModelScope.launch { repository.deleteBlock(id) }
    }

    fun finishOnboarding(
        name: String,
        ageText: String,
        startDateIso: String,
        hasDumbbells: Boolean,
        hasBarbell: Boolean,
        hasTreadmill: Boolean,
        eatsPaneer: Boolean,
        chickenDays: Int,
        weekdayWakeMin: Int,
        wedFriWakeMin: Int,
        weekendWakeMin: Int,
        enableCalendarSync: Boolean,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val base = repository.getProfileOrDefault()
            val validDate = runCatching { LocalDate.parse(startDateIso.trim()).toString() }
                .getOrDefault(LocalDate.now().toString())
            repository.updateProfile(
                base.copy(
                    name = name.trim().ifBlank { "Arnab" },
                    age = ageText.toIntOrNull(),
                    startDateIso = validDate,
                    hasDumbbells = hasDumbbells,
                    hasBarbell = hasBarbell,
                    hasTreadmill = hasTreadmill,
                    eatsFish = false,
                    eatsPaneer = eatsPaneer,
                    chickenDaysPerWeek = chickenDays.coerceIn(0, 7),
                    onboardingCompleted = true
                )
            )
            prefs.setWakeTimes(weekdayWakeMin, wedFriWakeMin, weekendWakeMin)
            if (enableCalendarSync) {
                prefs.setCalendarSync(true)
            }
            orchestrator.rebuildScheduleAndSync(notifyOnCalendarConflict = false)
            onComplete()
        }
    }
}

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalForgeColors.current
    val context = LocalContext.current

    var step by rememberSaveable { mutableIntStateOf(0) }
    var parentConfirmed by rememberSaveable { mutableStateOf(false) }

    var name by rememberSaveable { mutableStateOf("Arnab") }
    var age by rememberSaveable { mutableStateOf("16") }
    var startDateIso by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var hasDumbbells by rememberSaveable { mutableStateOf(true) }
    var hasBarbell by rememberSaveable { mutableStateOf(true) }
    var hasTreadmill by rememberSaveable { mutableStateOf(true) }
    var eatsPaneer by rememberSaveable { mutableStateOf(true) }
    var chickenDays by rememberSaveable { mutableIntStateOf(2) }

    var weekdayWakeH by rememberSaveable { mutableStateOf("06:30") }
    var wedFriWakeH by rememberSaveable { mutableStateOf("06:45") }
    var weekendWakeH by rememberSaveable { mutableStateOf("07:30") }
    var newSubject by rememberSaveable { mutableStateOf("") }
    var newDay by rememberSaveable { mutableIntStateOf(1) }

    var notifRequested by rememberSaveable { mutableStateOf(false) }
    var calendarGranted by rememberSaveable { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notifRequested = true }

    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        calendarGranted = res.values.all { it }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LinktreeColorBlockHero(
                eyebrow = "FORGE STRICT COACH • STEP ${step + 1} OF 4",
                headline = when (step) {
                    0 -> "SAFETY & REALISTIC EXPECTATIONS"
                    1 -> "ARNAB'S HOME GYM & KITCHEN"
                    2 -> "LOCKED TUITION & WAKE SCHEDULE"
                    else -> "PERMISSIONS & EXACT ALARMS"
                },
                badgeText = "ASIA/KOLKATA",
                subtitle = when (step) {
                    0 -> "Built for discipline around your school tuition — never starvation, ego lifting, or pushing through pain."
                    1 -> "8-week fat-loss & muscle-gain setup tailored to home equipment and Bengali meals."
                    2 -> "Forge locks every tuition block with a 15-minute buffer and protects 8.5 hours of sleep."
                    else -> "Each permission is optional with a clean fallback if skipped."
                }
            )
        }

        when (step) {
            0 -> {
                item {
                    LinktreeBentoCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        containerColor = colors.dangerBg,
                        borderColor = ForgePalette.Rose
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = ForgePalette.Blush)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text(
                                text = "PARENT & DOCTOR SAFETY NOTICE",
                                style = MaterialTheme.typography.titleSmall,
                                color = ForgePalette.Blush
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.first_run_parent_doctor_notice),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.dangerInk
                        )
                    }
                }
                item {
                    LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        LinktreeMonoChip(
                            text = "8-WEEK EXPECTATIONS",
                            backgroundColor = ForgePalette.Forest,
                            contentColor = ForgePalette.Chartreuse
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.expectations_banner),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = parentConfirmed,
                                onCheckedChange = { parentConfirmed = it }
                            )
                            Text(
                                text = "I will share this training & schedule plan with a parent/guardian and stop immediately if I feel pain or dizziness.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                item {
                    LinktreePillButton(
                        text = "Continue to Profile & Kitchen",
                        enabled = parentConfirmed,
                        onClick = { step = 1 },
                        trailingArrow = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            }

            1 -> {
                item {
                    LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Athlete Profile", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = age,
                                onValueChange = { age = it.filter(Char::isDigit).take(2) },
                                label = { Text("Age (optional)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = startDateIso,
                                onValueChange = { startDateIso = it },
                                label = { Text("Start Date (YYYY-MM-DD)") },
                                modifier = Modifier.weight(1.4f),
                                singleLine = true
                            )
                        }
                    }
                }
                item {
                    LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Home Equipment Confirm", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        ToggleRow("Dumbbells at home", hasDumbbells) { hasDumbbells = it }
                        ToggleRow("Barbell (light Week 5+ optional, collars required)", hasBarbell) { hasBarbell = it }
                        ToggleRow("Home Treadmill", hasTreadmill) { hasTreadmill = it }
                    }
                }
                item {
                    LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Bengali Household Food Confirm", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "No fish • Regular paneer, eggs, dal, curd, milk • Portion & oil-scoop coaching (zero calorie counting).",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.mutedText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ToggleRow("Paneer regularly (palm-sized, low oil)", eatsPaneer) { eatsPaneer = it }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Chicken days per week", style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinktreeOutlinePillButton("-", onClick = { chickenDays = (chickenDays - 1).coerceAtLeast(0) })
                                Text("$chickenDays d/wk", style = MaterialTheme.typography.titleMedium)
                                LinktreeOutlinePillButton("+", onClick = { chickenDays = (chickenDays + 1).coerceAtMost(4) })
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LinktreeOutlinePillButton("Back", onClick = { step = 0 }, modifier = Modifier.weight(1f))
                        LinktreePillButton("Review Tuition", onClick = { step = 2 }, trailingArrow = true, modifier = Modifier.weight(1.8f))
                    }
                }
            }

            2 -> {
                item {
                    LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Wake-Up Schedule (8.5h Sleep Protected)", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = weekdayWakeH,
                                onValueChange = { weekdayWakeH = it },
                                label = { Text("Mon/Tue/Thu") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = wedFriWakeH,
                                onValueChange = { wedFriWakeH = it },
                                label = { Text("Wed/Fri") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = weekendWakeH,
                                onValueChange = { weekendWakeH = it },
                                label = { Text("Sat/Sun") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }
                items(state.tuitionBlocks, key = { it.id }) { block ->
                    LinktreeBentoCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        containerColor = colors.tuitionBg
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                LinktreeMonoChip(
                                    text = "${SchedulerEngine.dayName(block.dayOfWeek)} • ${SchedulerEngine.formatMinute(block.startMinute)}-${SchedulerEngine.formatMinute(block.endMinute)}",
                                    backgroundColor = ForgePalette.TrueBlack.copy(alpha = 0.35f),
                                    contentColor = colors.tuitionInk,
                                    locked = true
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(block.title, style = MaterialTheme.typography.titleMedium, color = colors.tuitionInk)
                            }
                            IconButton(onClick = { viewModel.deleteTuitionBlock(block.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove tuition block", tint = colors.tuitionInk)
                            }
                        }
                    }
                }
                item {
                    LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Add Extra Tuition / Exam Block", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newSubject,
                                onValueChange = { newSubject = it },
                                label = { Text("Subject (e.g. Extra Maths)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            LinktreeOutlinePillButton(
                                text = SchedulerEngine.dayName(newDay),
                                onClick = { newDay = if (newDay == 7) 1 else newDay + 1 }
                            )
                            IconButton(onClick = {
                                if (newSubject.isNotBlank()) {
                                    viewModel.addQuickTuitionBlock(newDay, 17, 18, newSubject)
                                    newSubject = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add block")
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LinktreeOutlinePillButton("Back", onClick = { step = 1 }, modifier = Modifier.weight(1f))
                        LinktreePillButton("Permissions", onClick = { step = 3 }, trailingArrow = true, modifier = Modifier.weight(1.8f))
                    }
                }
            }

            else -> {
                item {
                    LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        LinktreeMonoChip("1 OF 3 • NOTIFICATIONS", ForgePalette.Forest, ForgePalette.Chartreuse)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Workout, Meal & Rest Timer Alerts", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Needed so between-set rest timers beep with the screen off and workouts/meals stay on schedule. Never fires during tuition or sleep.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.mutedText
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinktreeOutlinePillButton(
                            text = if (notifRequested || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) "Notifications Ready" else "Allow Notifications",
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    notifRequested = true
                                }
                            }
                        )
                    }
                }
                item {
                    LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        LinktreeMonoChip("2 OF 3 • EXACT ALARMS", ForgePalette.Forest, ForgePalette.Chartreuse)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Exact Session & Rest Countdown Timing", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ensures 75-second rest timers and pre-tuition meal slots fire on the exact minute. Falls back gracefully to standard alarms if skipped.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.mutedText
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinktreeOutlinePillButton(
                            text = if (state.exactAlarmGranted) "Exact Alarms Active" else "Open Exact Alarm Settings",
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !state.exactAlarmGranted) {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                    }
                                }
                            }
                        )
                    }
                }
                item {
                    LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        LinktreeMonoChip("3 OF 3 • PHONE CALENDAR", ForgePalette.Dahlia, ForgePalette.Lavender)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("'Arnab Coach' Two-Way Calendar Sync", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Writes workouts, meals, and tuition into a dedicated 'Arnab Coach' calendar and shifts sessions automatically if a school exam appears.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.mutedText
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinktreeOutlinePillButton(
                            text = if (calendarGranted) "Calendar Sync Granted" else "Allow Calendar Access (Optional)",
                            onClick = {
                                calendarLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            }
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LinktreeOutlinePillButton("Back", onClick = { step = 2 }, modifier = Modifier.weight(1f))
                        LinktreePillButton(
                            text = "Launch Forge Plan",
                            onClick = {
                                viewModel.finishOnboarding(
                                    name = name,
                                    ageText = age,
                                    startDateIso = startDateIso,
                                    hasDumbbells = hasDumbbells,
                                    hasBarbell = hasBarbell,
                                    hasTreadmill = hasTreadmill,
                                    eatsPaneer = eatsPaneer,
                                    chickenDays = chickenDays,
                                    weekdayWakeMin = parseHm(weekdayWakeH, 6 * 60 + 30),
                                    wedFriWakeMin = parseHm(wedFriWakeH, 6 * 60 + 45),
                                    weekendWakeMin = parseHm(weekendWakeH, 7 * 60 + 30),
                                    enableCalendarSync = calendarGranted,
                                    onComplete = onFinished
                                )
                            },
                            trailingArrow = true,
                            modifier = Modifier.weight(2f)
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun parseHm(text: String, fallback: Int): Int {
    val parts = text.trim().split(":")
    if (parts.size != 2) return fallback
    val h = parts[0].toIntOrNull() ?: return fallback
    val m = parts[1].toIntOrNull() ?: return fallback
    return (h.coerceIn(4, 11) * 60 + m.coerceIn(0, 59))
}
