package com.github.diarmaidlindsay.sigenergybattery.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.github.diarmaidlindsay.sigenergybattery.data.api.toStrategyConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.SocEtaCalculator
import com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MinerPreset
import com.github.diarmaidlindsay.sigenergybattery.domain.model.Season
import com.github.diarmaidlindsay.sigenergybattery.domain.model.StrategyConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.StrategyCondition
import com.github.diarmaidlindsay.sigenergybattery.domain.model.StrategyStep
import com.github.diarmaidlindsay.sigenergybattery.domain.model.TriggerAction
import com.github.diarmaidlindsay.sigenergybattery.ui.MonitorUiState
import com.github.diarmaidlindsay.sigenergybattery.ui.theme.ChargeGreen
import com.github.diarmaidlindsay.sigenergybattery.ui.theme.TextSecondary
import com.github.diarmaidlindsay.sigenergybattery.ui.theme.WarnYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_REGEX = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$")

@Composable
fun StrategySection(
    state: MonitorUiState,
    onSelectTemplate: (Season, StrategyConfig) -> Unit,
    onUpdateDraft: (StrategyConfig) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editorOpen by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Miner strategy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (state.strategyEnabled) {
                StrategyStatusCard(state)
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Stop strategy")
                }
            } else {
                Text(
                    "Build a day-long mining schedule from battery SOC and active hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                val draft = state.draftStrategy
                if (draft == null) {
                    Text("No strategy configured yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "${draft.name} · ${draft.steps.size} steps · ${draft.activeHoursStart}–${draft.activeHoursEnd}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        draft.steps.joinToString(" · ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                OutlinedButton(onClick = { editorOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (draft == null) "Configure strategy" else "Edit strategy")
                }
            }
            state.strategyError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (editorOpen) {
        StrategyEditorDialog(
            state = state,
            onSelectTemplate = { season, config ->
                onSelectTemplate(season, config)
            },
            onUpdateDraft = onUpdateDraft,
            onStart = onStart,
            onDismiss = { editorOpen = false },
        )
    }
}

@Composable
private fun StrategyStatusCard(state: MonitorUiState) {
    val current = state.strategySteps.getOrNull(state.strategyCurrentStep)
    val next = state.strategySteps.getOrNull(state.strategyCurrentStep + 1)
    val time = state.strategyLastTransitionAt?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(state.strategyName ?: "Miner strategy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Active step: ${current?.name ?: "—"}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                val parts = buildList {
                    state.strategyLastSoc?.let { add("SOC ${"%.1f".format(it)}%") }
                    time?.let { add("since $it") }
                    add("active ${state.strategyActiveHoursStart}–${state.strategyActiveHoursEnd}")
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                next?.let {
                    val label = if (state.strategyEtaMinutes != null) {
                        "Next: ${it.name} (${conditionLabel(it.condition)}) in ${SocEtaCalculator.formatMinutes(state.strategyEtaMinutes)}"
                    } else {
                        "Next: ${it.name} (${conditionLabel(it.condition)})"
                    }
                    Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                state.strategyLastError?.let {
                    Text("Last action error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        state.strategySteps.forEachIndexed { index, step ->
            StepSummaryRow(step = step, current = index == state.strategyCurrentStep)
        }
    }
}

@Composable
private fun StepSummaryRow(step: StrategyStep, current: Boolean) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (current) Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = ChargeGreen)
            }
            Text(conditionLabel(step.condition), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(actionLabel(step), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrategyEditorDialog(
    state: MonitorUiState,
    onSelectTemplate: (Season, StrategyConfig) -> Unit,
    onUpdateDraft: (StrategyConfig) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(state.draftStrategy) }
    var selectedSeason by remember { mutableStateOf(state.draftSeason) }
    var expandedStep by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceBright) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel editing")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Edit strategy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Changes apply when you tap Done", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    TextButton(onClick = {
                        draft?.let { selectedSeason?.let { season -> onSelectTemplate(season, it) } ?: onUpdateDraft(it) }
                        onDismiss()
                    }) { Text("Done") }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Seasonal template", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Season.entries.forEach { season ->
                                    FilterChip(
                                        selected = selectedSeason == season,
                                        onClick = {
                                            state.strategyTemplates[season.key]?.let { dto ->
                                                val selected = dto.toStrategyConfig()
                                                draft = selected
                                                selectedSeason = season
                                                expandedStep = 0
                                            }
                                        },
                                        label = { Text(season.displayName) },
                                    )
                                }
                            }
                            if (state.strategyTemplatesLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Loading templates…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                        }
                    }
                    if (draft == null) {
                        item {
                            Text(
                                "Choose a template to start configuring the strategy.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    } else {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TimeField(
                                    value = draft!!.activeHoursStart,
                                    label = "Active from",
                                    modifier = Modifier.weight(1f),
                                    onValueChange = { value -> draft = draft!!.copy(activeHoursStart = value) },
                                )
                                TimeField(
                                    value = draft!!.activeHoursEnd,
                                    label = "Active until",
                                    modifier = Modifier.weight(1f),
                                    onValueChange = { value -> draft = draft!!.copy(activeHoursEnd = value) },
                                )
                            }
                        }
                        itemsIndexed(draft!!.steps, key = { index, step -> "${index}-${step.name}" }) { index, step ->
                            StepEditor(
                                step = step,
                                stepNumber = index + 1,
                                expanded = expandedStep == index,
                                canDelete = draft!!.steps.size > 1,
                                onExpand = { expandedStep = if (expandedStep == index) -1 else index },
                                onStepChange = { updated ->
                                    draft = draft!!.copy(steps = draft!!.steps.mapIndexed { i, old -> if (i == index) updated else old })
                                },
                                onDelete = {
                                    draft = draft!!.copy(steps = draft!!.steps.filterIndexed { i, _ -> i != index })
                                    expandedStep = (expandedStep - 1).coerceAtLeast(0)
                                },
                            )
                        }
                        item {
                            OutlinedButton(
                                onClick = {
                                    val next = StrategyStep(
                                        name = "Step ${draft!!.steps.size + 1}",
                                        condition = StrategyCondition(socThreshold = 70.0, direction = Direction.AT_OR_BELOW),
                                        actions = setOf(TriggerAction.MINER_OFF),
                                    )
                                    draft = draft!!.copy(steps = draft!!.steps + next)
                                    expandedStep = draft!!.steps.lastIndex
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Add step") }
                        }
                        if (draft!!.steps.none { TriggerAction.MINER_OFF in it.actions }) {
                            item {
                                Text(
                                    "Add at least one step that turns the miners off.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WarnYellow,
                                )
                            }
                        }
                    }
                }

                if (draft != null) {
                    Button(
                        onClick = {
                            onUpdateDraft(draft!!)
                            onStart()
                            onDismiss()
                        },
                        enabled = !state.strategyLoading && draft!!.steps.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(16.dp),
                    ) {
                        if (state.strategyLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Starting…")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Start strategy")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepEditor(
    step: StrategyStep,
    stepNumber: Int,
    expanded: Boolean,
    canDelete: Boolean,
    onExpand: () -> Unit,
    onStepChange: (StrategyStep) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (!expanded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("$stepNumber. ${step.name}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(conditionLabel(step.condition), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(actionLabel(step), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                IconButton(onClick = onExpand) { Icon(Icons.Default.ExpandMore, contentDescription = "Expand step") }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$stepNumber. Edit step", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onExpand) { Icon(Icons.Default.ExpandLess, contentDescription = "Collapse step") }
                    if (canDelete) {
                        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete step") }
                    }
                }
                OutlinedTextField(
                    value = step.name,
                    onValueChange = { onStepChange(step.copy(name = it)) },
                    label = { Text("Step name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enter when SOC:", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = step.condition.direction == Direction.AT_OR_ABOVE,
                        onClick = { onStepChange(step.copy(condition = step.condition.copy(direction = Direction.AT_OR_ABOVE))) },
                        label = { Text("≥") },
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    FilterChip(
                        selected = step.condition.direction == Direction.AT_OR_BELOW,
                        onClick = { onStepChange(step.copy(condition = step.condition.copy(direction = Direction.AT_OR_BELOW))) },
                        label = { Text("≤") },
                    )
                }
                Slider(
                    value = step.condition.socThreshold.toFloat().coerceIn(0f, 100f),
                    onValueChange = { value -> onStepChange(step.copy(condition = step.condition.copy(socThreshold = value.toDouble()))) },
                    valueRange = 0f..100f,
                    steps = 99,
                )
                PercentRangeLabel(step.condition.socThreshold, "Entry")

                if (TriggerAction.MINER_OFF !in step.actions) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = step.condition.exitSocThreshold != null,
                            onCheckedChange = { checked ->
                                onStepChange(
                                    step.copy(
                                        condition = step.condition.copy(
                                            exitSocThreshold = if (checked) {
                                                step.condition.exitSocThreshold ?: 70.0
                                            } else null,
                                        ),
                                    ),
                                )
                            },
                        )
                        Text("Hold until SOC falls to", style = MaterialTheme.typography.bodySmall)
                    }
                    step.condition.exitSocThreshold?.let { exit ->
                        Slider(
                            value = exit.toFloat().coerceIn(0f, 100f),
                            onValueChange = { value -> onStepChange(step.copy(condition = step.condition.copy(exitSocThreshold = value.toDouble()))) },
                            valueRange = 0f..100f,
                            steps = 99,
                        )
                        PercentRangeLabel(exit, "Exit / fallback")
                    }
                }

                TimeField(
                    value = step.condition.timeAfter.orEmpty(),
                    label = "Earliest time (optional, HH:MM)",
                    onValueChange = { value -> onStepChange(step.copy(condition = step.condition.copy(timeAfter = value.trim().ifBlank { null }))) },
                )
                Text("Actions on entering:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                ActionCheckboxRow("Turn miners on", TriggerAction.MINER_ON in step.actions) { checked ->
                    val updated = if (checked) step.actions + TriggerAction.MINER_ON else step.actions - TriggerAction.MINER_ON
                    onStepChange(step.copy(actions = normalizedActions(updated, if (checked) TriggerAction.MINER_ON else null)))
                }
                ActionCheckboxRow("Turn miners off", TriggerAction.MINER_OFF in step.actions) { checked ->
                    val updated = if (checked) step.actions + TriggerAction.MINER_OFF else step.actions - TriggerAction.MINER_OFF
                    onStepChange(step.copy(actions = normalizedActions(updated, if (checked) TriggerAction.MINER_OFF else null)))
                }
                ActionCheckboxRow("Set power preset", TriggerAction.SET_POWER_PRESET in step.actions) { checked ->
                    val updated = if (checked) step.actions + TriggerAction.SET_POWER_PRESET else step.actions - TriggerAction.SET_POWER_PRESET
                    onStepChange(step.copy(actions = normalizedActions(updated, if (checked) TriggerAction.SET_POWER_PRESET else null)))
                }
                if (TriggerAction.SET_POWER_PRESET in step.actions) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MinerPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = step.minerPreset == preset,
                                onClick = { onStepChange(step.copy(minerPreset = preset)) },
                                label = { Text(presetLabel(preset)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PercentRangeLabel(value: Double, label: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("0%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text("$label ${value.toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text("100%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun ActionCheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TimeField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var showError by remember(value) { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            showError = newValue.isNotBlank() && !TIME_REGEX.matches(newValue.trim())
            onValueChange(newValue)
        },
        label = { Text(label) },
        placeholder = { Text("06:00") },
        singleLine = true,
        isError = showError,
        supportingText = if (showError) ({ Text("Use HH:MM, e.g. 16:00") }) else null,
        modifier = modifier,
    )
}

private fun normalizedActions(actions: Set<TriggerAction>, justSelected: TriggerAction?): Set<TriggerAction> {
    val result = actions.toMutableSet()
    when (justSelected) {
        TriggerAction.MINER_ON -> result.remove(TriggerAction.MINER_OFF)
        TriggerAction.MINER_OFF -> {
            result.remove(TriggerAction.MINER_ON)
            result.remove(TriggerAction.SET_POWER_PRESET)
        }
        TriggerAction.SET_POWER_PRESET -> result.remove(TriggerAction.MINER_OFF)
        TriggerAction.NOTIFY -> Unit
        null -> if (TriggerAction.MINER_OFF in result) {
            result.remove(TriggerAction.MINER_ON)
            result.remove(TriggerAction.SET_POWER_PRESET)
        }
    }
    return result
}

private fun conditionLabel(condition: StrategyCondition): String {
    val entry = "${condition.direction.directionSymbol} ${condition.socThreshold.toInt()}%"
    val exit = condition.exitSocThreshold?.let { " · falls back at ${it.toInt()}%" }.orEmpty()
    return if (condition.timeAfter != null) "$entry$exit from ${condition.timeAfter}" else entry + exit
}

private fun actionLabel(step: StrategyStep): String {
    val actions = buildList {
        if (TriggerAction.MINER_ON in step.actions) add("miners on")
        if (TriggerAction.MINER_OFF in step.actions) add("miners off")
        if (TriggerAction.SET_POWER_PRESET in step.actions) add("power ${step.minerPreset?.slug ?: "?"}")
    }
    return "→ " + (actions.ifEmpty { listOf("notify") }).joinToString(" · ")
}

private val Direction.directionSymbol: String
    get() = if (this == Direction.AT_OR_ABOVE) "≥" else "≤"

private fun presetLabel(preset: MinerPreset): String = when (preset) {
    MinerPreset.LOW -> "Low · 1 kW"
    MinerPreset.EFFICIENT -> "Efficient · 2 kW"
    MinerPreset.MAX -> "Max · 2.76 kW"
}
