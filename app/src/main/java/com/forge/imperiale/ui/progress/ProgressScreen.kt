package com.forge.imperiale.ui.progress

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.forge.imperiale.R
import com.forge.imperiale.data.local.entity.ExerciseEntity
import com.forge.imperiale.data.local.entity.WeeklyCheckinEntity
import com.forge.imperiale.data.preferences.ForgePreferencesRepository
import com.forge.imperiale.data.repository.ForgeRepository
import com.forge.imperiale.ui.components.LinktreeBentoCard
import com.forge.imperiale.ui.components.LinktreeColorBlockHero
import com.forge.imperiale.ui.components.LinktreeMonoChip
import com.forge.imperiale.ui.components.LinktreePillButton
import com.forge.imperiale.ui.theme.ForgePalette
import com.forge.imperiale.ui.theme.LocalForgeColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class ProgressUiState(
    val startDateIso: String = LocalDate.now().toString(),
    val checkins: List<WeeklyCheckinEntity> = emptyList(),
    val weeklyVolumes: List<Float> = List(8) { 0f },
    val adherenceMatrix: List<List<Boolean>> = List(8) { List(7) { false } },
    val exercises: List<ExerciseEntity> = emptyList(),
    val topSetSeriesByExercise: Map<String, List<Float>> = emptyMap(),
    val badgesEarned: List<String> = emptyList(),
    val lowMoodTwoWeeks: Boolean = false,
    val photosEnabled: Boolean = false,
    val privatePhotoPaths: List<String> = emptyList(),
    val statusMessage: String? = null
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val repository: ForgeRepository,
    private val prefs: ForgePreferencesRepository
) : ViewModel() {

    private val photosFlow = MutableStateFlow<List<String>>(emptyList())
    private val statusFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProgressUiState> = combine(
        repository.profileFlow,
        repository.checkinsFlow,
        repository.sessionsFlow,
        repository.allSetsFlow,
        combine(repository.allExercisesFlow, prefs.settingsFlow, photosFlow, statusFlow) { e, s, p, st ->
            listOf(e, s, p, st)
        }
    ) { profile, checkins, sessions, allSets, misc ->
        @Suppress("UNCHECKED_CAST")
        val exercises = misc[0] as List<ExerciseEntity>
        val settings = misc[1] as com.forge.imperiale.data.preferences.ForgeAppSettings
        @Suppress("UNCHECKED_CAST")
        val photos = misc[2] as List<String>
        val status = misc[3] as String?

        val startIso = profile?.startDateIso ?: LocalDate.now().toString()
        val startDate = runCatching { LocalDate.parse(startIso) }.getOrDefault(LocalDate.now())
        val completedSessions = sessions.filter { it.completed }
        val completedDates = completedSessions.map { it.dateIso }.toSet()
        val sessionDateMap = sessions.associate { it.id to it.dateIso }

        // 8-Week Weekly Volume
        val volumes = MutableList(8) { 0f }
        allSets.filter { it.skippedReason == null }.forEach { set ->
            val dStr = sessionDateMap[set.sessionId] ?: return@forEach
            val d = runCatching { LocalDate.parse(dStr) }.getOrNull() ?: return@forEach
            val wk = (ChronoUnit.DAYS.between(startDate, d) / 7).toInt()
            if (wk in 0..7) {
                volumes[wk] += (set.weightKg * set.reps).toFloat()
            }
        }

        // 8-Week Adherence Heatmap (8 weeks x 7 days)
        val heatmap = List(8) { w ->
            List(7) { d ->
                val cellDate = startDate.plusDays((w * 7 + d).toLong())
                cellDate.toString() in completedDates
            }
        }

        // Top-set trend per exercise
        val seriesMap = exercises.associate { ex ->
            val topPerSession = allSets
                .filter { it.exerciseId == ex.id && it.skippedReason == null }
                .groupBy { it.sessionId }
                .values
                .map { sets ->
                    if (ex.isTimed) sets.maxOf { it.seconds.toFloat() }
                    else sets.maxOf { (it.weightKg * (1.0 + it.reps / 30.0)).toFloat() }
                }
            ex.id to topPerSession
        }

        // Consistency badges
        val badges = mutableListOf<String>()
        if (completedSessions.isNotEmpty()) badges += "First Iron Logged"
        if (completedSessions.size >= 3) badges += "3-Session Discipline"
        if (completedSessions.size >= 12) badges += "2-Week Consistency"
        if (completedSessions.size >= 24) badges += "Halfway Titan"
        if (completedSessions.size >= 40) badges += "8-Week Finisher"
        if (checkins.isNotEmpty()) badges += "Weekly Tracker"

        val lowMood = checkins.size >= 2 && checkins[0].mood <= 2 && checkins[1].mood <= 2

        ProgressUiState(
            startDateIso = startIso,
            checkins = checkins,
            weeklyVolumes = volumes,
            adherenceMatrix = heatmap,
            exercises = exercises.filter { !it.isBarbellGated },
            topSetSeriesByExercise = seriesMap,
            badgesEarned = badges,
            lowMoodTwoWeeks = lowMood,
            photosEnabled = settings.progressPhotosEnabled,
            privatePhotoPaths = photos,
            statusMessage = status
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun refreshPrivatePhotos(filesDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(filesDir, "progress_photos")
            val list = dir.listFiles()?.sortedByDescending { it.lastModified() }?.map { it.absolutePath }.orEmpty()
            photosFlow.value = list
        }
    }

    fun copyPrivateProgressPhoto(filesDir: File, resolver: android.content.ContentResolver, sourceUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val dir = File(filesDir, "progress_photos").apply { mkdirs() }
                val dest = File(dir, "forge_${LocalDate.now()}_${System.currentTimeMillis()}.jpg")
                resolver.openInputStream(sourceUri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                withContext(Dispatchers.Main) {
                    statusFlow.value = "Saved private progress photo inside app-only storage (excluded from cloud backup)."
                }
                refreshPrivatePhotos(filesDir)
            }
        }
    }

    fun submitWeeklyCheckin(
        waistText: String,
        weightText: String,
        pushupText: String,
        plankText: String,
        mood: Int,
        note: String
    ) {
        val waist = waistText.toDoubleOrNull()
        if (waist == null || waist <= 30.0) {
            statusFlow.value = "Enter a valid weekly waist measurement in cm."
            return
        }
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong()).toString()
        viewModelScope.launch {
            repository.saveWeeklyCheckin(
                WeeklyCheckinEntity(
                    weekStartIso = weekStart,
                    dateIso = today.toString(),
                    waistCm = waist,
                    weightKg = weightText.toDoubleOrNull(),
                    pushupBest = pushupText.toIntOrNull() ?: 0,
                    plankSec = plankText.toIntOrNull() ?: 0,
                    mood = mood.coerceIn(1, 5),
                    note = note.trim()
                )
            )
            statusFlow.value = "Saved weekly check-in for week of $weekStart."
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalForgeColors.current
    val context = LocalContext.current

    remember(context) {
        viewModel.refreshPrivatePhotos(context.filesDir)
        true
    }

    var waist by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var pushups by rememberSaveable { mutableStateOf("") }
    var plankSec by rememberSaveable { mutableStateOf("") }
    var mood by rememberSaveable { mutableIntStateOf(4) }
    var note by rememberSaveable { mutableStateOf("") }
    var selectedExId by rememberSaveable { mutableStateOf("goblet_squat") }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.copyPrivateProgressPhoto(context.filesDir, context.contentResolver, uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LinktreeColorBlockHero(
                eyebrow = "PERFORMANCE, CONSISTENCY & WEEKLY WAIST • NO DAILY SCALE PROMPTS",
                headline = "8-WEEK PROGRESSION",
                badgeText = "${state.badgesEarned.size} BADGES",
                subtitle = stringResource(R.string.expectations_banner),
                backgroundColor = ForgePalette.DeepEmerald,
                contentColor = ForgePalette.Celadon,
                badgeBgColor = ForgePalette.Chartreuse,
                badgeTextColor = ForgePalette.TrueBlack
            )
        }

        // Gentle support banner if mood <= 2 for 2 consecutive weeks (Section 6)
        if (state.lowMoodTwoWeeks) {
            item {
                LinktreeBentoCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = ForgePalette.Dahlia,
                    borderColor = ForgePalette.Lavender
                ) {
                    Text("WELLBEING CHECK-IN", style = MaterialTheme.typography.titleSmall, color = ForgePalette.Lavender)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.low_mood_support),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ForgePalette.PureWhite
                    )
                }
            }
        }

        // 1. 8-Week Adherence Heat Map
        item {
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                LinktreeMonoChip("8-WEEK ADHERENCE HEAT MAP", ForgePalette.Forest, ForgePalette.Chartreuse)
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    state.adherenceMatrix.forEachIndexed { wIdx, weekRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("W${wIdx + 1}", style = MaterialTheme.typography.labelSmall, color = colors.mutedText)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                weekRow.forEachIndexed { dIdx, done ->
                                    val isSun = dIdx == 6
                                    val cellColor = when {
                                        done -> ForgePalette.Canopy
                                        isSun -> ForgePalette.Dahlia.copy(alpha = 0.65f)
                                        else -> colors.cardElevated
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(cellColor)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Green = Logged workout • Plum = Locked Sunday recovery",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedText
                )
            }
        }

        // 2. Weekly Volume Bar Chart (Custom Canvas)
        item {
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Weekly Volume (kg × reps)", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                val maxVol = (state.weeklyVolumes.maxOrNull() ?: 1f).coerceAtLeast(1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    val barCount = 8
                    val slotW = size.width / barCount
                    val barW = slotW * 0.58f
                    state.weeklyVolumes.forEachIndexed { idx, vol ->
                        val ratio = (vol / maxVol).coerceIn(0.06f, 1f)
                        val barH = size.height * ratio
                        drawRoundRect(
                            color = if (vol > 0f) ForgePalette.Chartreuse else ForgePalette.Mortar,
                            topLeft = Offset(idx * slotW + (slotW - barW) / 2f, size.height - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                    }
                }
            }
        }

        // 3. Top-Set Trend Line Chart per Exercise
        item {
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Top-Set Trend Per Exercise", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.exercises.take(10).forEach { ex ->
                        FilterChip(
                            selected = selectedExId == ex.id,
                            onClick = { selectedExId = ex.id },
                            label = { Text(ex.name) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                val points = state.topSetSeriesByExercise[selectedExId].orEmpty()
                if (points.isEmpty()) {
                    Text(
                        "Log sets for this exercise to unlock your progression trend curve.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mutedText
                    )
                } else {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    ) {
                        val minV = (points.minOrNull() ?: 0f)
                        val maxV = (points.maxOrNull() ?: 1f).coerceAtLeast(minV + 1f)
                        val path = Path()
                        points.forEachIndexed { idx, v ->
                            val x = if (points.size == 1) size.width / 2f else idx * (size.width / (points.size - 1))
                            val y = size.height - ((v - minV) / (maxV - minV)) * (size.height * 0.8f) - size.height * 0.1f
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            drawCircle(ForgePalette.Canopy, radius = 8f, center = Offset(x, y))
                        }
                        if (points.size >= 2) {
                            drawPath(path, color = ForgePalette.Canopy, style = Stroke(width = 5f))
                        }
                    }
                }
            }
        }

        // 4. Consistency Badges
        if (state.badgesEarned.isNotEmpty()) {
            item {
                LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = ForgePalette.Marigold)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Earned Discipline Badges", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.badgesEarned.forEach { badge ->
                            LinktreeMonoChip(badge, ForgePalette.Forest, ForgePalette.Chartreuse)
                        }
                    }
                }
            }
        }

        // 5. Weekly Check-In Form (Section 7.8)
        item {
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                LinktreeMonoChip("ONCE-A-WEEK CHECK-IN", ForgePalette.Chartreuse, ForgePalette.TrueBlack)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Waist, Performance & Mood", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = waist,
                        onValueChange = { waist = it },
                        label = { Text("Waist (cm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Weight kg (opt)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pushups,
                        onValueChange = { pushups = it.filter(Char::isDigit) },
                        label = { Text("Best Push-Ups") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = plankSec,
                        onValueChange = { plankSec = it.filter(Char::isDigit) },
                        label = { Text("Best Plank (s)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Weekly Mood (1 = Low, 5 = Strong):", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in 1..5) {
                        FilterChip(
                            selected = mood == m,
                            onClick = { mood = m },
                            label = { Text("$m") }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Optional check-in note") },
                    modifier = Modifier.fillMaxWidth()
                )
                state.statusMessage?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = ForgePalette.Chartreuse)
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinktreePillButton(
                    text = "Save Weekly Check-In",
                    onClick = { viewModel.submitWeeklyCheckin(waist, weight, pushups, plankSec, mood, note) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 6. Optional App-Private Progress Photos (Off by default, never backed up)
        if (state.photosEnabled) {
            item {
                LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    LinktreeMonoChip("PRIVATE DEVICE-ONLY VAULT", ForgePalette.Dahlia, ForgePalette.Lavender, locked = true)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Private Progress Photos", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Stored strictly inside app-private files and excluded from cloud/device backup. Never shown as comparison prompts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mutedText
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinktreePillButton(
                        text = "Add Private Photo",
                        leadingIcon = Icons.Default.PhotoCamera,
                        onClick = { photoPicker.launch("image/*") }
                    )
                    if (state.privatePhotoPaths.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.privatePhotoPaths.forEach { path ->
                                AsyncImage(
                                    model = File(path),
                                    contentDescription = "Private progress photo",
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(104.dp)) }
    }
}
