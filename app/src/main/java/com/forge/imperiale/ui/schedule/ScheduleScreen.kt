package com.forge.imperiale.ui.schedule

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forge.imperiale.calendar.CalendarAndScheduleOrchestrator
import com.forge.imperiale.calendar.PhoneCalendarInfo
import com.forge.imperiale.data.local.entity.BlockType
import com.forge.imperiale.data.local.entity.ScheduleBlockEntity
import com.forge.imperiale.data.preferences.ForgeAppSettings
import com.forge.imperiale.data.preferences.ForgePreferencesRepository
import com.forge.imperiale.data.repository.ForgeRepository
import com.forge.imperiale.scheduler.SchedulerEngine
import com.forge.imperiale.ui.components.LinktreeBentoCard
import com.forge.imperiale.ui.components.LinktreeColorBlockHero
import com.forge.imperiale.ui.components.LinktreeMonoChip
import com.forge.imperiale.ui.components.LinktreeOutlinePillButton
import com.forge.imperiale.ui.components.LinktreePillButton
import com.forge.imperiale.ui.theme.ForgePalette
import com.forge.imperiale.ui.theme.LocalForgeColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ScheduleUiState(
    val allBlocks: List<ScheduleBlockEntity> = emptyList(),
    val settings: ForgeAppSettings = ForgeAppSettings(),
    val phoneCalendars: List<PhoneCalendarInfo> = emptyList(),
    val adjustmentReasons: List<String> = emptyList(),
    val statusMessage: String? = null
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ForgeRepository,
    private val prefs: ForgePreferencesRepository,
    private val orchestrator: CalendarAndScheduleOrchestrator
) : ViewModel() {

    private val calendarsFlow = MutableStateFlow<List<PhoneCalendarInfo>>(emptyList())
    private val adjustmentsFlow = MutableStateFlow<List<String>>(emptyList())
    private val statusFlow = MutableStateFlow<String?>(null)

    init {
        refreshCalendarsAndRebuild(notifyConflict = false)
    }

    val uiState: StateFlow<ScheduleUiState> = combine(
        repository.allScheduleBlocksFlow,
        prefs.settingsFlow,
        calendarsFlow,
        adjustmentsFlow,
        statusFlow
    ) { blocks, settings, cals, reasons, status ->
        ScheduleUiState(
            allBlocks = blocks,
            settings = settings,
            phoneCalendars = cals,
            adjustmentReasons = reasons,
            statusMessage = status
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    fun refreshCalendarsAndRebuild(notifyConflict: Boolean = true) {
        viewModelScope.launch {
            repository.ensureSeeded()
            if (orchestrator.hasCalendarPermissions()) {
                calendarsFlow.value = orchestrator.listExternalCalendars()
            }
            val reasons = orchestrator.rebuildScheduleAndSync(notifyConflict)
            adjustmentsFlow.value = reasons
        }
    }

    fun addOrUpdateLockedBlock(
        dayOfWeek: Int,
        startHm: String,
        endHm: String,
        title: String,
        type: BlockType
    ) {
        viewModelScope.launch {
            if (dayOfWeek == 7 && type == BlockType.WORKOUT) {
                statusFlow.value = "Refused: Sunday is a locked rest day — no extra workouts allowed."
                return@launch
            }
            val startMin = parseClock(startHm) ?: (17 * 60)
            val endMin = (parseClock(endHm) ?: (startMin + 60)).coerceAtLeast(startMin + 20)
            repository.saveBlock(
                ScheduleBlockEntity(
                    type = type,
                    dayOfWeek = dayOfWeek.coerceIn(1, 7),
                    startMinute = startMin,
                    endMinute = endMin.coerceAtMost(23 * 60 + 45),
                    title = title.trim().ifBlank { "Tuition / Exam" },
                    subtitle = if (type == BlockType.TUITION) "Locked Study / Tuition (15m buffer)" else "Locked Busy Block",
                    locked = true,
                    source = "USER"
                )
            )
            val reasons = orchestrator.rebuildScheduleAndSync(true)
            adjustmentsFlow.value = reasons
            statusFlow.value = "Saved block and recalculated conflict-free day plan."
        }
    }

    fun deleteBlock(block: ScheduleBlockEntity) {
        viewModelScope.launch {
            repository.deleteBlock(block.id)
            adjustmentsFlow.value = orchestrator.rebuildScheduleAndSync(false)
        }
    }

    fun toggleCalendarSync(enabled: Boolean) {
        viewModelScope.launch {
            val cals = if (orchestrator.hasCalendarPermissions()) orchestrator.listExternalCalendars() else emptyList()
            calendarsFlow.value = cals
            val defaultMonitored = uiState.value.settings.monitoredCalendarIds.ifEmpty {
                cals.map { it.id.toString() }.toSet()
            }
            prefs.setCalendarSync(enabled, defaultMonitored)
            adjustmentsFlow.value = orchestrator.rebuildScheduleAndSync(enabled)
            statusFlow.value = if (enabled) {
                "Synced week to 'Arnab Coach' phone calendar."
            } else {
                "Phone calendar sync paused."
            }
        }
    }

    fun toggleMonitoredCalendar(calId: Long) {
        viewModelScope.launch {
            val current = uiState.value.settings.monitoredCalendarIds.toMutableSet()
            val key = calId.toString()
            if (!current.add(key)) current.remove(key)
            prefs.setCalendarSync(uiState.value.settings.syncToPhoneCalendar, current)
            adjustmentsFlow.value = orchestrator.rebuildScheduleAndSync(true)
        }
    }

    fun updateAlarms(workout: Boolean, meals: Boolean, sleep: Boolean, coach: Boolean, fullScreenWake: Boolean) {
        viewModelScope.launch {
            prefs.setAlarmToggles(workout, meals, sleep, coach, fullScreenWake)
            orchestrator.rebuildScheduleAndSync(false)
        }
    }

    private fun parseClock(hm: String): Int? {
        val parts = hm.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h.coerceIn(0, 23) * 60 + m.coerceIn(0, 59)
    }
}

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalForgeColors.current

    var selectedDay by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    var laneFilter by rememberSaveable { mutableStateOf("ALL") }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res.values.all { it }) {
            viewModel.toggleCalendarSync(true)
        }
    }

    val filteredBlocks = remember(state.allBlocks, selectedDay, laneFilter) {
        state.allBlocks
            .filter { it.dayOfWeek == selectedDay }
            .filter {
                when (laneFilter) {
                    "TUITION" -> it.type == BlockType.TUITION || it.type == BlockType.SCHOOL
                    "WORKOUT" -> it.type == BlockType.WORKOUT
                    "MEALS" -> it.type == BlockType.MEAL
                    "SLEEP" -> it.type == BlockType.SLEEP
                    else -> true
                }
            }
            .sortedBy { it.startMinute }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LinktreeColorBlockHero(
                eyebrow = "CONFLICT-FREE TIMETABLE • 15M TUITION BUFFER",
                headline = "WEEKLY LANES & CALENDAR",
                badgeText = "8.5H SLEEP PROTECTED",
                subtitle = "Tuition blocks are locked. Workouts, meals, and phone-down reminders wrap around study hours automatically.",
                backgroundColor = ForgePalette.Dahlia,
                contentColor = ForgePalette.Lavender,
                badgeBgColor = ForgePalette.Chartreuse,
                badgeTextColor = ForgePalette.TrueBlack,
                bottomAction = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinktreePillButton(
                            text = "Add Tuition / Exam",
                            leadingIcon = Icons.Default.Add,
                            onClick = { showAddDialog = true }
                        )
                        LinktreeOutlinePillButton(
                            text = "Re-Sync",
                            leadingIcon = Icons.Default.Refresh,
                            borderColor = ForgePalette.Lavender,
                            contentColor = ForgePalette.Lavender,
                            onClick = { viewModel.refreshCalendarsAndRebuild(true) }
                        )
                    }
                }
            )
        }

        // Day selector row (Mon..Sun)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (day in 1..7) {
                    FilterChip(
                        selected = selectedDay == day,
                        onClick = { selectedDay = day },
                        label = { Text(SchedulerEngine.dayName(day)) }
                    )
                }
            }
        }

        // Category Lane selector (All, Tuition, Workouts, Meals, Sleep)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All Lanes",
                    "TUITION" to "Tuition (Locked)",
                    "WORKOUT" to "Workouts",
                    "MEALS" to "Meals",
                    "SLEEP" to "Sleep"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = laneFilter == key,
                        onClick = { laneFilter = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        // Conflict / Adjustment Highlight Card
        if (state.adjustmentReasons.isNotEmpty() || state.statusMessage != null) {
            item {
                LinktreeBentoCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = colors.cardElevated,
                    borderColor = ForgePalette.Marigold
                ) {
                    LinktreeMonoChip("SCHEDULER EXPLANATIONS", ForgePalette.Marigold, ForgePalette.TrueBlack)
                    Spacer(modifier = Modifier.height(6.dp))
                    state.statusMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = ForgePalette.Chartreuse)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    state.adjustmentReasons.forEach { reason ->
                        Text("• $reason", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Day Lane Blocks
        items(filteredBlocks, key = { "${it.id}_${it.startMinute}_${it.title}" }) { block ->
            val isTuition = block.type == BlockType.TUITION || block.type == BlockType.SCHOOL
            val bg = when {
                isTuition -> colors.tuitionBg
                block.type == BlockType.WORKOUT -> colors.workoutBg
                block.type == BlockType.MEAL -> colors.mealBg
                block.type == BlockType.SLEEP -> colors.sleepBg
                else -> colors.cardSurface
            }
            val ink = when {
                isTuition -> colors.tuitionInk
                block.type == BlockType.WORKOUT -> colors.workoutInk
                block.type == BlockType.MEAL -> colors.mealInk
                block.type == BlockType.SLEEP -> colors.sleepInk
                else -> MaterialTheme.colorScheme.onSurface
            }

            LinktreeBentoCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                containerColor = bg
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        LinktreeMonoChip(
                            text = "${SchedulerEngine.formatMinute(block.startMinute)} - ${SchedulerEngine.formatMinute(block.endMinute)} • ${block.type.name}",
                            backgroundColor = ForgePalette.TrueBlack.copy(alpha = 0.3f),
                            contentColor = ink,
                            locked = block.locked
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(block.title, style = MaterialTheme.typography.titleLarge, color = ink)
                        if (block.subtitle.isNotBlank()) {
                            Text(block.subtitle, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.85f))
                        }
                    }
                    if (block.source == "SEED" || block.source == "USER") {
                        IconButton(onClick = { viewModel.deleteBlock(block) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete block", tint = ink)
                        }
                    }
                }
            }
        }

        // Phone Calendar & Alarm Category Controls
        item {
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Phone Calendar & Category Alarms", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sync to 'Arnab Coach' Calendar", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Writes workouts, meals & tuition and reads busy exams.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.mutedText
                        )
                    }
                    Switch(
                        checked = state.settings.syncToPhoneCalendar,
                        onCheckedChange = { want ->
                            if (want) {
                                calendarPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            } else {
                                viewModel.toggleCalendarSync(false)
                            }
                        }
                    )
                }

                if (state.phoneCalendars.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Monitor Busy Events From:", style = MaterialTheme.typography.labelMedium)
                    state.phoneCalendars.forEach { cal ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = cal.id.toString() in state.settings.monitoredCalendarIds,
                                onCheckedChange = { viewModel.toggleMonitoredCalendar(cal.id) }
                            )
                            Text("${cal.displayName} (${cal.accountName})", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                val s = state.settings
                AlarmToggleRow("Workout Alarms", s.workoutAlarmsEnabled) {
                    viewModel.updateAlarms(it, s.mealAlarmsEnabled, s.sleepAlarmsEnabled, s.coachAlarmsEnabled, s.fullScreenWakeAlarm)
                }
                AlarmToggleRow("Meal & Water Reminders", s.mealAlarmsEnabled) {
                    viewModel.updateAlarms(s.workoutAlarmsEnabled, it, s.sleepAlarmsEnabled, s.coachAlarmsEnabled, s.fullScreenWakeAlarm)
                }
                AlarmToggleRow("Sleep Wind-Down Reminders", s.sleepAlarmsEnabled) {
                    viewModel.updateAlarms(s.workoutAlarmsEnabled, s.mealAlarmsEnabled, it, s.coachAlarmsEnabled, s.fullScreenWakeAlarm)
                }
                AlarmToggleRow("Full-Screen Morning Wake Alarm", s.fullScreenWakeAlarm) {
                    viewModel.updateAlarms(s.workoutAlarmsEnabled, s.mealAlarmsEnabled, s.sleepAlarmsEnabled, s.coachAlarmsEnabled, it)
                }
                Text(
                    "Quiet hours active during all locked Tuition blocks and ${SchedulerEngine.formatMinute(s.quietHoursStartMin)}-${SchedulerEngine.formatMinute(s.quietHoursEndMin)} (max 8 notifications/day).",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedText
                )
            }
        }

        item { Spacer(modifier = Modifier.height(104.dp)) }
    }

    if (showAddDialog) {
        var subject by remember { mutableStateOf("") }
        var startHm by remember { mutableStateOf("17:00") }
        var endHm by remember { mutableStateOf("18:30") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Locked Tuition / Exam Block (${SchedulerEngine.dayName(selectedDay)})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject / Exam Title") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startHm,
                            onValueChange = { startHm = it },
                            label = { Text("Start (HH:MM)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endHm,
                            onValueChange = { endHm = it },
                            label = { Text("End (HH:MM)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addOrUpdateLockedBlock(
                        dayOfWeek = selectedDay,
                        startHm = startHm,
                        endHm = endHm,
                        title = subject,
                        type = BlockType.TUITION
                    )
                    showAddDialog = false
                }) {
                    Text("Save & Recalculate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AlarmToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
