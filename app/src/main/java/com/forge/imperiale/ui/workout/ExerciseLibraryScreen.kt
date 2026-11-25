package com.forge.imperiale.ui.workout

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.forge.imperiale.data.local.entity.ExerciseEntity
import com.forge.imperiale.data.repository.ForgeRepository
import com.forge.imperiale.data.seed.ForgeSeedData
import com.forge.imperiale.domain.video.VideoLinkParser
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
import javax.inject.Inject

data class ExerciseLibraryItemUi(
    val exercise: ExerciseEntity,
    val personalBestSummary: String
)

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val repository: ForgeRepository
) : ViewModel() {

    val itemsFlow: StateFlow<List<ExerciseLibraryItemUi>> = combine(
        repository.allExercisesFlow,
        repository.allSetsFlow
    ) { exercises, allSets ->
        val validSets = allSets.filter { it.skippedReason == null }
        exercises.map { ex ->
            val sets = validSets.filter { it.exerciseId == ex.id }
            val pb = if (sets.isEmpty()) {
                "No sets logged yet"
            } else if (ex.isTimed) {
                "PB: ${sets.maxOf { it.seconds }}s hold"
            } else {
                val top = sets.maxBy { it.weightKg * 100 + it.reps }
                "PB: ${top.weightKg} kg × ${top.reps} reps"
            }
            ExerciseLibraryItemUi(ex, pb)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveVideoLink(
        exercise: ExerciseEntity,
        rawUrl: String,
        startSecText: String,
        endSecText: String,
        onResult: (String) -> Unit
    ) {
        val start = startSecText.toFloatOrNull() ?: 0f
        val end = endSecText.toFloatOrNull() ?: 0f
        val parsed = VideoLinkParser.parse(rawUrl, start, end)
        if (parsed == null) {
            onResult("Invalid link. Paste a YouTube link (youtu.be, watch?v=, shorts, embed) or direct .mp4/.m3u8 URL.")
            return
        }
        viewModelScope.launch {
            repository.updateExercise(
                exercise.copy(
                    videoType = parsed.type,
                    videoRef = parsed.reference,
                    loopStartSec = parsed.loopStartSec,
                    loopEndSec = parsed.loopEndSec
                )
            )
            onResult("Saved ${parsed.type.name} stream (${parsed.reference}) with loop ${parsed.loopStartSec}s..${parsed.loopEndSec}s.")
        }
    }

    fun restoreDefaultStream(exerciseId: String) {
        viewModelScope.launch {
            val seed = ForgeSeedData.defaultExercises().firstOrNull { it.id == exerciseId } ?: return@launch
            repository.updateExercise(seed)
        }
    }
}

@Composable
fun ExerciseLibraryScreen(
    onBack: () -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel()
) {
    val items by viewModel.itemsFlow.collectAsStateWithLifecycle()
    val colors = LocalForgeColors.current
    var query by rememberSaveable { mutableStateOf("") }
    var editingExercise by remember { mutableStateOf<ExerciseEntity?>(null) }

    val filtered = remember(items, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) items else items.filter {
            it.exercise.name.lowercase().contains(q) ||
                it.exercise.muscles.lowercase().contains(q) ||
                it.exercise.equipment.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LinktreeColorBlockHero(
                eyebrow = "EXERCISE LIBRARY & VIDEO MANAGER",
                headline = "MOVEMENT VAULT",
                badgeText = "${items.size} EXERCISES",
                subtitle = "Every exercise comes pre-loaded with auto-looping streams. Customize links or loop timestamps any time.",
                bottomAction = {
                    LinktreeOutlinePillButton(
                        text = "Back to Workout Floor",
                        leadingIcon = Icons.Default.ArrowBack,
                        borderColor = ForgePalette.Forest,
                        contentColor = ForgePalette.Forest,
                        onClick = onBack
                    )
                }
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search exercise, muscle group, or equipment") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        items(filtered, key = { it.exercise.id }) { item ->
            val ex = item.exercise
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinktreeMonoChip(
                        text = "${ex.videoType.name} LOOP • ${ex.equipment}",
                        backgroundColor = ForgePalette.Forest,
                        contentColor = ForgePalette.Chartreuse,
                        locked = ex.isBarbellGated
                    )
                    Text(item.personalBestSummary, style = MaterialTheme.typography.labelSmall, color = ForgePalette.Canopy)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(ex.name, style = MaterialTheme.typography.titleLarge)
                Text(ex.muscles, style = MaterialTheme.typography.labelMedium, color = colors.mutedText)
                Spacer(modifier = Modifier.height(6.dp))
                Text(ex.cues, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinktreePillButton(
                        text = "Preview & Edit Video Loop",
                        onClick = { editingExercise = ex }
                    )
                    LinktreeOutlinePillButton(
                        text = "Reset Default",
                        onClick = { viewModel.restoreDefaultStream(ex.id) }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    editingExercise?.let { ex ->
        var rawLink by remember(ex.id) { mutableStateOf(ex.videoRef) }
        var startSec by remember(ex.id) { mutableStateOf(ex.loopStartSec.toString()) }
        var endSec by remember(ex.id) { mutableStateOf(ex.loopEndSec.toString()) }
        var feedback by remember(ex.id) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingExercise = null },
            title = { Text("Video Loop Manager • ${ex.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseVideoPlayer(
                        exercise = ex,
                        onOpenVideoManager = {}
                    )
                    OutlinedTextField(
                        value = rawLink,
                        onValueChange = { rawLink = it },
                        label = { Text("YouTube Link / ID or .mp4/.m3u8 URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startSec,
                            onValueChange = { startSec = it },
                            label = { Text("Loop Start (sec)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endSec,
                            onValueChange = { endSec = it },
                            label = { Text("Loop End (sec)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    feedback?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = ForgePalette.Chartreuse)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveVideoLink(ex, rawLink, startSec, endSec) { msg ->
                        feedback = msg
                    }
                }) { Text("Validate & Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingExercise = null }) { Text("Close") }
            }
        )
    }
}
