package com.forge.imperiale.ui.settings

import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forge.imperiale.calendar.CalendarAndScheduleOrchestrator
import com.forge.imperiale.data.preferences.ForgeAppSettings
import com.forge.imperiale.data.preferences.ForgePreferencesRepository
import com.forge.imperiale.data.repository.ForgeRepository
import com.forge.imperiale.ui.components.LinktreeBentoCard
import com.forge.imperiale.ui.components.LinktreeColorBlockHero
import com.forge.imperiale.ui.components.LinktreeMonoChip
import com.forge.imperiale.ui.components.LinktreeOutlinePillButton
import com.forge.imperiale.ui.components.LinktreePillButton
import com.forge.imperiale.ui.theme.ForgePalette
import com.forge.imperiale.ui.theme.ForgeThemeMode
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SettingsUiState(
    val settings: ForgeAppSettings = ForgeAppSettings(),
    val feedbackMessage: String? = null,
    val isSyncingCloud: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ForgeRepository,
    private val prefs: ForgePreferencesRepository,
    private val orchestrator: CalendarAndScheduleOrchestrator
) : ViewModel() {

    private val feedbackFlow = MutableStateFlow<String?>(null)
    private val syncingFlow = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs.settingsFlow,
        feedbackFlow,
        syncingFlow
    ) { settings, msg, syncing ->
        SettingsUiState(settings, msg, syncing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ForgeThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setWeightStep(step: Double) {
        viewModelScope.launch { prefs.setWeightStep(step) }
    }

    fun setRestTimerDefault(sec: Int) {
        viewModelScope.launch { prefs.setDefaultRestSec(sec) }
    }

    fun setPhotosEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setProgressPhotosEnabled(enabled) }
    }

    fun authMongoAccount(uri: String, username: String, password: String, register: Boolean) {
        viewModelScope.launch {
            syncingFlow.value = true
            val res = repository.registerOrLoginMongoAccount(uri, username, password, register)
            syncingFlow.value = false
            feedbackFlow.value = res.fold(
                onSuccess = {
                    viewModelScope.launch { orchestrator.rebuildScheduleAndSync(false) }
                    it
                },
                onFailure = { "MongoDB sync error: ${it.message}" }
            )
        }
    }

    fun pushCloudNow() {
        viewModelScope.launch {
            syncingFlow.value = true
            val res = repository.pushCloudSnapshot()
            syncingFlow.value = false
            feedbackFlow.value = res.fold(
                onSuccess = { it },
                onFailure = { "Cloud push failed: ${it.message}" }
            )
        }
    }

    fun exportJsonToUri(resolver: android.content.ContentResolver, destUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val json = repository.exportBackupJson()
                resolver.openOutputStream(destUri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                withContext(Dispatchers.Main) {
                    feedbackFlow.value = "Exported full Forge JSON backup."
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    feedbackFlow.value = "Export failed: ${err.message}"
                }
            }
        }
    }

    fun importJsonFromUri(resolver: android.content.ContentResolver, srcUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val json = resolver.openInputStream(srcUri)?.bufferedReader()?.readText().orEmpty()
                repository.importBackupJson(json).getOrThrow()
                orchestrator.rebuildScheduleAndSync(false)
                withContext(Dispatchers.Main) {
                    feedbackFlow.value = "Restored Forge backup and recalculated schedule."
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    feedbackFlow.value = "Import failed: ${err.message}"
                }
            }
        }
    }

    fun resetFullProgram() {
        viewModelScope.launch {
            repository.resetProgram()
            orchestrator.rebuildScheduleAndSync(false)
            feedbackFlow.value = "Program reset to Week 1 Day 1 with Arnab's default timetable."
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalForgeColors.current
    val context = LocalContext.current

    var mongoUri by rememberSaveable(state.settings.mongoUri) { mutableStateOf(state.settings.mongoUri) }
    var username by rememberSaveable(state.settings.accountUsername) {
        mutableStateOf(state.settings.accountUsername.ifBlank { "arnab" })
    }
    var password by rememberSaveable { mutableStateOf("") }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportJsonToUri(context.contentResolver, uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importJsonFromUri(context.contentResolver, uri)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LinktreeColorBlockHero(
                eyebrow = "PREFERENCES, CLOUD ACCOUNT & PORTABLE BACKUP",
                headline = "FORGE SETTINGS",
                badgeText = "OFFLINE-FIRST",
                subtitle = "Zero ads, zero tracking. Local Room DB + optional MongoDB replica account sync.",
                bottomAction = {
                    LinktreeOutlinePillButton(
                        text = "Back",
                        leadingIcon = Icons.Default.ArrowBack,
                        borderColor = ForgePalette.Forest,
                        contentColor = ForgePalette.Forest,
                        onClick = onBack
                    )
                }
            )
        }

        state.feedbackMessage?.let { msg ->
            item {
                LinktreeBentoCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = colors.workoutBg
                ) {
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = colors.workoutInk)
                }
            }
        }

        // 1. MongoDB Cloud Account Sync (Line 18)
        item {
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                LinktreeMonoChip("MONGODB CLOUD ACCOUNT", ForgePalette.Forest, ForgePalette.Chartreuse)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Forge Cloud Account & Sync", style = MaterialTheme.typography.titleLarge)
                if (state.settings.lastCloudSyncEpochMs > 0L) {
                    val fmt = remember(state.settings.lastCloudSyncEpochMs) {
                        SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(state.settings.lastCloudSyncEpochMs))
                    }
                    Text(
                        "Signed in as @${state.settings.accountUsername} • Last synced $fmt",
                        style = MaterialTheme.typography.bodySmall,
                        color = ForgePalette.Canopy
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = mongoUri,
                    onValueChange = { mongoUri = it },
                    label = { Text("MongoDB Replica URI (mongodb://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinktreePillButton(
                        text = if (state.isSyncingCloud) "Syncing..." else "Create / Update",
                        enabled = !state.isSyncingCloud,
                        onClick = { viewModel.authMongoAccount(mongoUri, username, password, register = true) },
                        modifier = Modifier.weight(1f)
                    )
                    LinktreeOutlinePillButton(
                        text = "Sign In & Pull",
                        enabled = !state.isSyncingCloud,
                        onClick = { viewModel.authMongoAccount(mongoUri, username, password, register = false) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (state.settings.accountUsername.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinktreePillButton(
                        text = "Sync Snapshot Now",
                        leadingIcon = Icons.Default.CloudSync,
                        containerColor = ForgePalette.Sky,
                        contentColor = ForgePalette.TrueBlack,
                        enabled = !state.isSyncingCloud,
                        onClick = viewModel::pushCloudNow,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 2. Theme Mode & Training Defaults
        item {
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Linktree Theme & Training Defaults", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Theme Mode:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ForgeThemeMode.AMOLED_DARK to "AMOLED Black (Default)",
                        ForgeThemeMode.LINKTREE_LIGHT to "Linktree Chalk Light",
                        ForgeThemeMode.SYSTEM to "System"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = state.settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Smallest Weight Step (${state.settings.weightStepKg} kg):", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5, 1.0, 1.25, 2.0, 2.5).forEach { step ->
                        FilterChip(
                            selected = state.settings.weightStepKg == step,
                            onClick = { viewModel.setWeightStep(step) },
                            label = { Text("${step}kg") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Default Rest Timer (${state.settings.defaultRestSec}s):", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60, 75, 90).forEach { sec ->
                        FilterChip(
                            selected = state.settings.defaultRestSec == sec,
                            onClick = { viewModel.setRestTimerDefault(sec) },
                            label = { Text("${sec}s") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Private Progress Photos", style = MaterialTheme.typography.titleMedium)
                        Text("Off by default • Stored only inside app-private directory", style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
                    }
                    Switch(
                        checked = state.settings.progressPhotosEnabled,
                        onCheckedChange = viewModel::setPhotosEnabled
                    )
                }
            }
        }

        // 3. JSON Backup, Restore & Program Reset
        item {
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Local JSON Backup & Program Reset", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinktreePillButton(
                        text = "Export JSON",
                        onClick = { exportLauncher.launch("forge_backup_${System.currentTimeMillis()}.json") },
                        modifier = Modifier.weight(1f)
                    )
                    LinktreeOutlinePillButton(
                        text = "Import JSON",
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinktreeOutlinePillButton(
                    text = "Reset 8-Week Program",
                    borderColor = ForgePalette.Rose,
                    contentColor = ForgePalette.Rose,
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Entire Program?") },
            text = { Text("This clears logged workouts, sets, meal logs, and check-ins, and restores Arnab's default 8-week schedule. Export a JSON backup first if you want to keep history.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    viewModel.resetFullProgram()
                }) { Text("Confirm Reset", color = ForgePalette.Rose) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
