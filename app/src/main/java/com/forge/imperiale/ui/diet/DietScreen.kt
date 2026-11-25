package com.forge.imperiale.ui.diet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forge.imperiale.R
import com.forge.imperiale.data.local.entity.BlockType
import com.forge.imperiale.data.local.entity.MealLogEntity
import com.forge.imperiale.data.local.entity.MealSlotEntity
import com.forge.imperiale.data.local.entity.MealStatus
import com.forge.imperiale.data.repository.ForgeRepository
import com.forge.imperiale.data.seed.ForgeSeedData
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

data class ScheduledMealSlotUi(
    val slot: MealSlotEntity,
    val scheduledTimeFormatted: String?,
    val log: MealLogEntity?
)

data class DietUiState(
    val slots: List<ScheduledMealSlotUi> = emptyList(),
    val waterGlasses: Int = 0,
    val waterGoal: Int = 11,
    val friedThisWeek: Int = 0,
    val mishtiThisWeek: Int = 0,
    val skippedThisWeek: Int = 0,
    val chickenMealsThisWeek: Int = 0
)

@HiltViewModel
class DietViewModel @Inject constructor(
    private val repository: ForgeRepository
) : ViewModel() {

    private val today = LocalDate.now()
    private val todayIso = today.toString()
    private val weekStartIso = today.minusDays((today.dayOfWeek.value - 1).toLong()).toString()

    init {
        viewModelScope.launch { repository.ensureSeeded() }
    }

    val uiState: StateFlow<DietUiState> = combine(
        repository.rawMealSlotsFlow,
        repository.observeBlocksForDay(today.dayOfWeek.value),
        repository.observeMealLogsBetween(weekStartIso, todayIso),
        repository.observeWaterForDate(todayIso),
        repository.profileFlow
    ) { mealSlots, dayBlocks, weekLogs, water, profile ->
        val todayLogs = weekLogs.filter { it.dateIso == todayIso }.associateBy { it.slotKey }
        val scheduledMeals = dayBlocks.filter { it.type == BlockType.MEAL || it.type == BlockType.SLEEP }
            .associateBy { it.refId.orEmpty() }

        val uiSlots = mealSlots.map { slot ->
            val block = scheduledMeals[slot.slotKey]
            ScheduledMealSlotUi(
                slot = slot,
                scheduledTimeFormatted = block?.let { SchedulerEngine.formatMinute(it.startMinute) },
                log = todayLogs[slot.slotKey]
            )
        }

        val chickenCount = weekLogs.count {
            it.itemsCheckedCsv.contains("Chicken", ignoreCase = true)
        }

        DietUiState(
            slots = uiSlots,
            waterGlasses = water?.glasses ?: 0,
            waterGoal = profile?.waterGoalGlasses ?: 11,
            friedThisWeek = weekLogs.sumOf { it.friedCount },
            mishtiThisWeek = weekLogs.sumOf { it.mishtiCount },
            skippedThisWeek = weekLogs.count { it.status == MealStatus.SKIPPED },
            chickenMealsThisWeek = chickenCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietUiState())

    fun logMeal(
        slotKey: String,
        status: MealStatus,
        proteins: List<String>,
        scoopedOil: Boolean,
        friedDelta: Int = 0,
        mishtiDelta: Int = 0
    ) {
        viewModelScope.launch {
            val existing = uiState.value.slots.firstOrNull { it.slot.slotKey == slotKey }?.log
            repository.saveMealLog(
                dateIso = todayIso,
                slotKey = slotKey,
                status = status,
                itemsChecked = proteins,
                scoopedExtraOil = scoopedOil,
                friedCount = ((existing?.friedCount ?: 0) + friedDelta).coerceAtLeast(0),
                mishtiCount = ((existing?.mishtiCount ?: 0) + mishtiDelta).coerceAtLeast(0)
            )
        }
    }

    fun addWeeklyTreatCounter(isFried: Boolean, delta: Int) {
        viewModelScope.launch {
            val dinnerLog = uiState.value.slots.firstOrNull { it.slot.slotKey == "DINNER" }?.log
            val proteins = dinnerLog?.itemsCheckedCsv?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
            repository.saveMealLog(
                dateIso = todayIso,
                slotKey = "DINNER",
                status = dinnerLog?.status ?: MealStatus.PENDING,
                itemsChecked = proteins,
                scoopedExtraOil = dinnerLog?.scoopedExtraOil ?: false,
                friedCount = ((dinnerLog?.friedCount ?: 0) + if (isFried) delta else 0).coerceAtLeast(0),
                mishtiCount = ((dinnerLog?.mishtiCount ?: 0) + if (!isFried) delta else 0).coerceAtLeast(0)
            )
        }
    }

    fun adjustWater(delta: Int) {
        viewModelScope.launch { repository.adjustWaterGlasses(todayIso, delta) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DietScreen(
    viewModel: DietViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalForgeColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LinktreeColorBlockHero(
                eyebrow = "MEAL TIMING & PORTION CONTROL • ZERO CALORIE COUNTING",
                headline = "BENGALI HOME KITCHEN",
                badgeText = "NO FISH • PANEER & EGGS",
                subtitle = "Keep your family kitchen — control plate portions, scoop floating oil, and hit protein at every meal.",
                backgroundColor = ForgePalette.Marigold,
                contentColor = ForgePalette.Espresso,
                badgeBgColor = ForgePalette.Espresso,
                badgeTextColor = ForgePalette.Marigold
            )
        }

        // Skipped Meal Guardrail Warning (Section 6: >= 2 skipped meals in a week)
        if (state.skippedThisWeek >= 2) {
            item {
                LinktreeBentoCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = colors.dangerBg,
                    borderColor = ForgePalette.Rose
                ) {
                    Text("COACH SAFETY ALERT", style = MaterialTheme.typography.titleSmall, color = ForgePalette.Blush)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.skipped_meals_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.dangerInk
                    )
                }
            }
        }

        // Water Tap Counter + Weekly Home-Food Treat Counters
        item {
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = ForgePalette.Sky)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Hydration Counter", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${state.waterGlasses} / ${state.waterGoal} glasses (~${"%.2f".format(state.waterGlasses * 0.25)}L of 2.5-3L goal)",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.mutedText
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinktreeOutlinePillButton("-1", onClick = { viewModel.adjustWater(-1) })
                        LinktreePillButton(
                            text = "+1",
                            containerColor = ForgePalette.Sky,
                            contentColor = ForgePalette.TrueBlack,
                            onClick = { viewModel.adjustWater(1) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("WEEKLY HOME-FOOD RULES", style = MaterialTheme.typography.labelMedium, color = ForgePalette.Marigold)
                Spacer(modifier = Modifier.height(6.dp))
                CounterLine(
                    title = "Fried items (luchi / bhaja / singara)",
                    valueText = "${state.friedThisWeek} / 2 max",
                    overLimit = state.friedThisWeek > 2,
                    onMinus = { viewModel.addWeeklyTreatCounter(true, -1) },
                    onPlus = { viewModel.addWeeklyTreatCounter(true, 1) }
                )
                CounterLine(
                    title = "Mishti (after meal only)",
                    valueText = "${state.mishtiThisWeek} / 1 max",
                    overLimit = state.mishtiThisWeek > 1,
                    onMinus = { viewModel.addWeeklyTreatCounter(false, -1) },
                    onPlus = { viewModel.addWeeklyTreatCounter(false, 1) }
                )
                Text(
                    text = "Chicken logged this week: ${state.chickenMealsThisWeek} meal(s) • No sugary drinks or packet chips.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedText
                )
            }
        }

        // Meal Slots Checklist Cards
        items(state.slots, key = { it.slot.slotKey }) { item ->
            val slot = item.slot
            val log = item.log
            val initialProteins = remember(log?.itemsCheckedCsv) {
                mutableStateListOf<String>().apply {
                    addAll(log?.itemsCheckedCsv?.split("|")?.filter { it.isNotBlank() }.orEmpty())
                }
            }
            var scoopedOil by remember(log?.scoopedExtraOil) { mutableStateOf(log?.scoopedExtraOil ?: false) }

            LinktreeBentoCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                borderColor = if (log?.status == MealStatus.DONE) ForgePalette.Canopy else colors.cardBorder
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinktreeMonoChip(
                        text = "${item.scheduledTimeFormatted ?: "FLEX"} • ${log?.status?.name ?: "PENDING"}",
                        backgroundColor = if (log?.status == MealStatus.DONE) ForgePalette.Canopy else ForgePalette.Forest,
                        contentColor = if (log?.status == MealStatus.DONE) ForgePalette.TrueBlack else ForgePalette.Chartreuse
                    )
                    if (slot.showPlateRule) {
                        LinktreeMonoChip("50/25/25 PLATE", ForgePalette.Marigold, ForgePalette.Espresso)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(slot.title, style = MaterialTheme.typography.headlineSmall)
                Text(slot.guidance, style = MaterialTheme.typography.bodySmall, color = colors.mutedText)

                // Plate Rule Graphic on every Lunch and Dinner card (Section 5)
                if (slot.showPlateRule) {
                    Spacer(modifier = Modifier.height(12.dp))
                    BengaliPlateRuleGraphic()
                }

                // Protein Checklist for Breakfast, Lunch, Snacks, Dinner
                if (slot.slotKey !in setOf("WAKE_WATER", "WIND_DOWN")) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("TAP PROTEINS EATEN:", style = MaterialTheme.typography.labelSmall, color = ForgePalette.Chartreuse)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ForgeSeedData.BENGALI_PROTEIN_OPTIONS.forEach { protein ->
                            val selected = protein in initialProteins
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) initialProteins.remove(protein) else initialProteins.add(protein)
                                },
                                label = { Text(protein.substringBefore(" (")) }
                            )
                        }
                    }

                    if (slot.showPlateRule) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = scoopedOil, onCheckedChange = { scoopedOil = it })
                            Text("Scooped extra floating oil off gravy", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinktreePillButton(
                        text = if (log?.status == MealStatus.DONE) "Logged On Time" else "Mark Done",
                        leadingIcon = Icons.Default.Check,
                        onClick = {
                            viewModel.logMeal(
                                slotKey = slot.slotKey,
                                status = MealStatus.DONE,
                                proteins = initialProteins.toList(),
                                scoopedOil = scoopedOil
                            )
                        },
                        modifier = Modifier.weight(1.4f)
                    )
                    if (slot.slotKey in setOf("BREAKFAST", "LUNCH", "DINNER")) {
                        LinktreeOutlinePillButton(
                            text = "Skipped",
                            onClick = {
                                viewModel.logMeal(
                                    slotKey = slot.slotKey,
                                    status = MealStatus.SKIPPED,
                                    proteins = emptyList(),
                                    scoopedOil = false
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Coach Kitchen Tips Footer
        item {
            LinktreeBentoCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("BENGALI HOUSEHOLD COACH RULES", style = MaterialTheme.typography.titleSmall, color = ForgePalette.Marigold)
                Spacer(modifier = Modifier.height(6.dp))
                ForgeSeedData.HOME_FOOD_COACH_TIPS.forEach { tip ->
                    Text("• $tip", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        item { Spacer(modifier = Modifier.height(104.dp)) }
    }
}

@Composable
private fun BengaliPlateRuleGraphic() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(ForgePalette.TrueBlack.copy(alpha = 0.35f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Canvas(modifier = Modifier.size(68.dp)) {
            // 1/2 Vegetables / Sabzi / Salad (180 deg)
            drawArc(
                color = ForgePalette.Canopy,
                startAngle = 90f,
                sweepAngle = 180f,
                useCenter = true
            )
            // 1/4 Protein (90 deg)
            drawArc(
                color = ForgePalette.Marigold,
                startAngle = 270f,
                sweepAngle = 90f,
                useCenter = true
            )
            // 1/4 Rice or Roti (90 deg)
            drawArc(
                color = ForgePalette.Sky,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = true
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.plate_rule_title), style = MaterialTheme.typography.titleSmall)
            LegendDot("1/2 Sabzi or Salad", ForgePalette.Canopy)
            LegendDot("1/4 Protein (Paneer / Eggs / Dal / Curd)", ForgePalette.Marigold)
            LegendDot("1/4 Rice or Roti (cut usual rice by ~1/3)", ForgePalette.Sky)
        }
    }
}

@Composable
private fun LegendDot(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CounterLine(
    title: String,
    valueText: String,
    overLimit: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueText,
                style = MaterialTheme.typography.labelSmall,
                color = if (overLimit) ForgePalette.Rose else ForgePalette.Canopy,
                fontWeight = FontWeight.Bold
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LinktreeOutlinePillButton("-", onClick = onMinus)
            LinktreeOutlinePillButton("+", onClick = onPlus)
        }
    }
}
