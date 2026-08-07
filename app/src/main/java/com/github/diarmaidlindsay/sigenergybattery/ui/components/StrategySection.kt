package com.github.diarmaidlindsay.sigenergybattery.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

/** Regex for HH:MM time-of-day input. */
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
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Miner strategy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Automated day-long mining schedule driven by battery SOC and active hours. " +
                    "Runs until you stop it. Mutually exclusive with one-shot alert monitoring.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            if (state.strategyEnabled) {
                StrategyStatusCard(state)
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Stop strategy")
                }
            } else {
                StrategyEditor(
                    state = state,
                    onSelectTemplate = onSelectTemplate,
                    onUpdateDraft = onUpdateDraft,
                    onStart = onStart,
                )
            }

            state.strategyError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StrategyStatusCard(state: MonitorUiState) {
    val current = state.strategySteps.getOrNull(state.strategyCurrentStep)
    val next = state.strategySteps.getOrNull(state.strategyCurrentStep + 1)
    val time = state.strategyLastTransitionAt?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    state.strategyName ?: "Miner strategy",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Active step: ${current?.name ?: "—"}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                val parts = buildList {
                    state.strategyLastSoc?.let { add("SOC ${"%.1f".format(it)}%") }
                    time?.let { add("since $it") }
                    add("active ${state.strategyActiveHoursStart}–${state.strategyActiveHoursEnd}")
                }
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                state.strategyEtaMinutes?.let { eta ->
                    next?.let {
                        Text(
                            "Next: ${it.name} (${conditionLabel(it.condition)}) in ${SocEtaCalculator.formatMinutes(eta)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                state.strategyLastError?.let {
                    Text(
                        "Last action error: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        state.strategySteps.forEachIndexed { index, step ->
            val isCurrent = index == state.strategyCurrentStep
            StepSummaryRow(step = step, current = isCurrent)
        }
    }
}

@Composable
private fun StepSummaryRow(step: StrategyStep, current: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = if (current) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ),
            modifier = Modifier.weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        step.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (current) {
                        Text(
                            "ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChargeGreen,
                        )
                    }
                }
                Text(
                    conditionLabel(step.condition),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    actionLabel(step),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrategyEditor(
    state: MonitorUiState,
    onSelectTemplate: (Season, StrategyConfig) -> Unit,
    onUpdateDraft: (StrategyConfig) -> Unit,
    onStart: () -> Unit,
) {
    val draft = state.draftStrategy

    // Template picker.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Start from a seasonal template",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Season.entries.forEach { season ->
                FilterChip(
                    selected = state.draftSeason == season,
                    onClick = {
                        state.strategyTemplates[season.key]?.let { dto ->
                            onSelectTemplate(season, dto.toStrategyConfig())
                        }
                    },
                    label = { Text(season.displayName) },
                )
            }
        }
    }

    if (state.strategyTemplatesLoading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Loading templates…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }

    if (draft == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = WarnYellow)
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                "Pick a template above (or the last used strategy will be reused when you start).",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        return
    }

    // Active hours + interval.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeField(
            value = draft.activeHoursStart,
            label = "Active from",
            modifier = Modifier.weight(1f),
            onValueChange = { newValue ->
                onUpdateDraft(draft.copy(activeHoursStart = newValue))
            },
        )
        TimeField(
            value = draft.activeHoursEnd,
            label = "Active until",
            modifier = Modifier.weight(1f),
            onValueChange = { newValue ->
                onUpdateDraft(draft.copy(activeHoursEnd = newValue))
            },
        )
    }

    // Steps editor.
    draft.steps.forEachIndexed { index, step ->
        StepEditor(
            step = step,
            stepNumber = index + 1,
            canDelete = draft.steps.size > 1,
            onStepChange = { updated ->
                onUpdateDraft(draft.copy(steps = draft.steps.mapIndexed { i, s -> if (i == index) updated else s }))
            },
            onDelete = {
                onUpdateDraft(draft.copy(steps = draft.steps.filterIndexed { i, _ -> i != index }))
            },
        )
    }

    OutlinedButton(
        onClick = {
            val newStep = StrategyStep(
                name = "Step ${draft.steps.size + 1}",
                condition = StrategyCondition(socThreshold = 70.0, direction = Direction.AT_OR_BELOW),
                actions = setOf(TriggerAction.MINER_OFF),
            )
            onUpdateDraft(draft.copy(steps = draft.steps + newStep))
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Add step")
    }

    val hasOffStep = draft.steps.any { TriggerAction.MINER_OFF in it.actions }
    if (!hasOffStep) {
        Text(
            "Add at least one step that turns the miners off — the strategy needs to be able to shut them down.",
            style = MaterialTheme.typography.bodySmall,
            color = WarnYellow,
        )
    }

    Button(
        onClick = onStart,
        enabled = !state.strategyLoading && draft.steps.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun StepEditor(
    step: StrategyStep,
    stepNumber: Int,
    canDelete: Boolean,
    onStepChange: (StrategyStep) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = step.name,
                    onValueChange = { onStepChange(step.copy(name = it)) },
                    label = { Text("Step $stepNumber name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete step")
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Enter when SOC:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = step.condition.direction == Direction.AT_OR_ABOVE,
                    onClick = {
                        onStepChange(
                            step.copy(condition = step.condition.copy(direction = Direction.AT_OR_ABOVE)),
                        )
                    },
                    label = { Text("≥") },
                )
                Spacer(modifier = Modifier.size(4.dp))
                FilterChip(
                    selected = step.condition.direction == Direction.AT_OR_BELOW,
                    onClick = {
                        onStepChange(
                            step.copy(condition = step.condition.copy(direction = Direction.AT_OR_BELOW)),
                        )
                    },
                    label = { Text("≤") },
                )
            }

            Slider(
                value = step.condition.socThreshold.toFloat(),
                onValueChange = { newValue ->
                    onStepChange(
                        step.copy(condition = step.condition.copy(socThreshold = newValue.toDouble())),
                    )
                },
                valueRange = 0f..100f,
                steps = 99,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("0%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(
                    "${step.condition.socThreshold.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("100%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }

            TimeField(
                value = step.condition.timeAfter.orEmpty(),
                label = "Earliest time (optional, HH:MM)",
                onValueChange = { newValue ->
                    val cleaned = if (newValue.isBlank()) null else newValue
                    onStepChange(step.copy(condition = step.condition.copy(timeAfter = cleaned)))
                },
            )

            Text(
                "Actions on entering:",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            ActionCheckboxRow(
                label = "Turn miners on",
                checked = TriggerAction.MINER_ON in step.actions,
                onCheckedChange = { checked ->
                    val updated = if (checked) step.actions + TriggerAction.MINER_ON else step.actions - TriggerAction.MINER_ON
                    onStepChange(step.copy(actions = normalizedActions(updated, if (checked) TriggerAction.MINER_ON else null)))
                },
            )
            ActionCheckboxRow(
                label = "Turn miners off",
                checked = TriggerAction.MINER_OFF in step.actions,
                onCheckedChange = { checked ->
                    val updated = if (checked) step.actions + TriggerAction.MINER_OFF else step.actions - TriggerAction.MINER_OFF
                    onStepChange(step.copy(actions = normalizedActions(updated, if (checked) TriggerAction.MINER_OFF else null)))
                },
            )
            ActionCheckboxRow(
                label = "Set power preset",
                checked = TriggerAction.SET_POWER_PRESET in step.actions,
                onCheckedChange = { checked ->
                    val updated = if (checked) step.actions + TriggerAction.SET_POWER_PRESET else step.actions - TriggerAction.SET_POWER_PRESET
                    onStepChange(step.copy(actions = normalizedActions(updated, if (checked) TriggerAction.SET_POWER_PRESET else null)))
                },
            )
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

@Composable
private fun ActionCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TimeField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        supportingText = if (showError) {
            { Text("Use HH:MM, e.g. 16:00") }
        } else {
            null
        },
        modifier = modifier,
    )
}

private fun normalizedActions(
    actions: Set<TriggerAction>,
    justSelected: TriggerAction?,
): Set<TriggerAction> {
    val result = actions.toMutableSet()
    when (justSelected) {
        TriggerAction.MINER_ON -> result.remove(TriggerAction.MINER_OFF)
        TriggerAction.MINER_OFF -> {
            result.remove(TriggerAction.MINER_ON)
            result.remove(TriggerAction.SET_POWER_PRESET)
        }
        TriggerAction.SET_POWER_PRESET -> result.remove(TriggerAction.MINER_OFF)
        TriggerAction.NOTIFY -> Unit
        null -> {
            if (TriggerAction.MINER_OFF in result) {
                result.remove(TriggerAction.MINER_ON)
                result.remove(TriggerAction.SET_POWER_PRESET)
            }
        }
    }
    return result
}

private fun conditionLabel(condition: StrategyCondition): String {
    val soc = "${condition.direction.directionSymbol} ${condition.socThreshold.toInt()}%"
    return if (condition.timeAfter != null) "$soc from ${condition.timeAfter}" else soc
}

private fun actionLabel(step: StrategyStep): String {
    val actions = buildList {
        if (TriggerAction.MINER_ON in step.actions) add("miners on")
        if (TriggerAction.MINER_OFF in step.actions) add("miners off")
        if (TriggerAction.SET_POWER_PRESET in step.actions) {
            add("power ${step.minerPreset?.slug ?: "?"}")
        }
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
