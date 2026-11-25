package com.forge.imperiale

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.forge.imperiale.data.preferences.ForgePreferencesRepository
import com.forge.imperiale.data.repository.ForgeRepository
import com.forge.imperiale.ui.components.ForgeNavDestination
import com.forge.imperiale.ui.components.LinktreeFloatingBottomBar
import com.forge.imperiale.ui.diet.DietScreen
import com.forge.imperiale.ui.onboarding.OnboardingScreen
import com.forge.imperiale.ui.progress.ProgressScreen
import com.forge.imperiale.ui.schedule.ScheduleScreen
import com.forge.imperiale.ui.settings.SettingsScreen
import com.forge.imperiale.ui.theme.ForgeTheme
import com.forge.imperiale.ui.theme.ForgeThemeMode
import com.forge.imperiale.ui.theme.LocalForgeColors
import com.forge.imperiale.ui.today.TodayScreen
import com.forge.imperiale.ui.workout.ExerciseLibraryScreen
import com.forge.imperiale.ui.workout.WorkoutScreen
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootShellState(
    val isReady: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val themeMode: ForgeThemeMode = ForgeThemeMode.AMOLED_DARK
)

@HiltViewModel
class RootViewModel @Inject constructor(
    private val repository: ForgeRepository,
    prefs: ForgePreferencesRepository
) : ViewModel() {

    init {
        viewModelScope.launch { repository.ensureSeeded() }
    }

    val state: StateFlow<RootShellState> = combine(
        repository.profileFlow,
        prefs.settingsFlow
    ) { profile, settings ->
        RootShellState(
            isReady = profile != null,
            onboardingCompleted = profile?.onboardingCompleted == true,
            themeMode = settings.themeMode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootShellState())
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialNav = intent?.getStringExtra("navigate_to")
        val initialTemplate = intent?.getStringExtra("template_id")

        setContent {
            val rootVm: RootViewModel = hiltViewModel()
            val shell by rootVm.state.collectAsStateWithLifecycle()

            ForgeTheme(themeMode = shell.themeMode) {
                if (shell.isReady) {
                    ForgeRootApp(
                        onboardingCompleted = shell.onboardingCompleted,
                        initialNav = initialNav,
                        initialTemplate = initialTemplate
                    )
                }
            }
        }
    }
}

@Composable
fun ForgeRootApp(
    onboardingCompleted: Boolean,
    initialNav: String? = null,
    initialTemplate: String? = null
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route?.substringBefore("?")
    val colors = LocalForgeColors.current

    val bottomDestinations = listOf(
        ForgeNavDestination("today", stringResource(R.string.nav_today), Icons.Default.Today),
        ForgeNavDestination("workout", stringResource(R.string.nav_workout), Icons.Default.FitnessCenter),
        ForgeNavDestination("diet", stringResource(R.string.nav_diet), Icons.Default.Restaurant),
        ForgeNavDestination("schedule", stringResource(R.string.nav_schedule), Icons.Default.CalendarMonth),
        ForgeNavDestination("progress", stringResource(R.string.nav_progress), Icons.Default.ShowChart)
    )

    val startRoute = if (onboardingCompleted) "today" else "onboarding"

    LaunchedEffect(onboardingCompleted, initialNav) {
        if (onboardingCompleted && initialNav == "workout") {
            val route = if (!initialTemplate.isNullOrBlank()) {
                "workout?templateId=$initialTemplate"
            } else {
                "workout"
            }
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    val showBottomBar = currentRoute in setOf("today", "workout", "diet", "schedule", "progress")

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier.fillMaxSize()
            ) {
                composable("onboarding") {
                    OnboardingScreen(
                        onFinished = {
                            navController.navigate("today") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    )
                }

                composable("today") {
                    TodayScreen(
                        onStartWorkout = { tmplId ->
                            val target = if (!tmplId.isNullOrBlank()) "workout?templateId=$tmplId" else "workout"
                            navController.navigate(target) { launchSingleTop = true }
                        },
                        onOpenDiet = {
                            navController.navigate("diet") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenSettings = { navController.navigate("settings") }
                    )
                }

                composable(
                    route = "workout?templateId={templateId}",
                    arguments = listOf(
                        navArgument("templateId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { entry ->
                    val templateId = entry.arguments?.getString("templateId")
                    WorkoutScreen(
                        requestedTemplateId = templateId,
                        onOpenExerciseLibrary = { navController.navigate("exercise_library") }
                    )
                }

                composable("diet") {
                    DietScreen()
                }

                composable("schedule") {
                    ScheduleScreen()
                }

                composable("progress") {
                    ProgressScreen()
                }

                composable("settings") {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }

                composable("exercise_library") {
                    ExerciseLibraryScreen(onBack = { navController.popBackStack() })
                }
            }

            if (showBottomBar) {
                LinktreeFloatingBottomBar(
                    destinations = bottomDestinations,
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                )
            }
        }
    }
}
