package com.github.diarmaidlindsay.sigenergybattery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MinerPreset
import com.github.diarmaidlindsay.sigenergybattery.domain.model.TriggerAction
import com.github.diarmaidlindsay.sigenergybattery.domain.SocEtaCalculator
import com.github.diarmaidlindsay.sigenergybattery.ui.components.SocHistoryChart
import com.github.diarmaidlindsay.sigenergybattery.ui.theme.ChargeGreen
import com.github.diarmaidlindsay.sigenergybattery.ui.theme.TextSecondary
import com.github.diarmaidlindsay.sigenergybattery.ui.theme.WarnYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshAll()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.connected) {
        if (state.connected) viewModel.loadHistory()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.connected) {
            MonitorPanel(
                state = state,
                notificationsGranted = notificationsGranted,
                onRequestNotifications = onRequestNotifications,
                onIntervalChange = viewModel::onIntervalChange,
                onThresholdChange = viewModel::onThresholdChange,
                onDirectionChange = viewModel::onDirectionChange,
                onTriggerActionToggle = viewModel::onTriggerActionToggle,
                onMinerPresetChange = viewModel::onMinerPresetChange,
                onCheckNow = viewModel::checkNow,
                onLoadHistory = viewModel::loadHistory,
                onStart = viewModel::beginMonitoring,
                onStop = viewModel::cancelMonitoring,
                onDisconnect = viewModel::disconnect,
                onDismissAlert = viewModel::clearAlert,
            )
        } else {
            ConnectPanel(
                state = state,
                onHostChange = viewModel::onHostChange,
                onPortChange = viewModel::onPortChange,
                onApiKeyChange = viewModel::onApiKeyChange,
                onConnect = viewModel::connect,
            )
        }
    }
}

@Composable
private fun ConnectPanel(
    state: MonitorUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = ChargeGreen)
                Spacer(modifier = Modifier.size(10.dp))
                Column {
                    Text("Sigenergy Battery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Connect to the Hermes bridge to monitor home battery SOC.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            OutlinedTextField(
                value = state.host,
                onValueChange = onHostChange,
                label = { Text("Bridge IP") },
                placeholder = { Text("e.g. 100.105.141.68") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.port,
                    onValueChange = onPortChange,
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.weight(2f),
                )
            }
            Text(
                "API URL: ${state.host.trim().trimEnd('/')}:${state.port.trim()}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            state.connectionError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (state.autoConnecting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        "Auto-connecting to ${state.host.trim()}:${state.port.trim()}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            Button(
                onClick = onConnect,
                enabled = !state.isConnecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Connecting…")
                } else {
                    Text("Connect")
                }
            }
        }
    }
}

@Composable
private fun MonitorPanel(
    state: MonitorUiState,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onIntervalChange: (Int) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onDirectionChange: (Direction) -> Unit,
    onTriggerActionToggle: (TriggerAction, Boolean) -> Unit,
    onMinerPresetChange: (MinerPreset) -> Unit,
    onCheckNow: () -> Unit,
    onLoadHistory: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDisconnect: () -> Unit,
    onDismissAlert: () -> Unit,
) {
    SocCard(state)

    HistoryCard(state = state, onRefresh = onLoadHistory)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onCheckNow,
            enabled = !state.checking,
            modifier = Modifier.weight(1f),
        ) {
            if (state.checking) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.size(6.dp))
            }
            Text("Check now")
        }
        TextButton(onClick = onDisconnect) { Text("Disconnect") }
    }

    state.checkError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Alert settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            IntervalDropdown(intervalMinutes = state.intervalMinutes, onIntervalChange = onIntervalChange)

            Column {
                Text(
                    "Notify when battery SOC is:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.direction == Direction.AT_OR_BELOW,
                        onClick = { onDirectionChange(Direction.AT_OR_BELOW) },
                        label = { Text("≤ ${state.thresholdSoc.toInt()}%") },
                    )
                    FilterChip(
                        selected = state.direction == Direction.AT_OR_ABOVE,
                        onClick = { onDirectionChange(Direction.AT_OR_ABOVE) },
                        label = { Text("≥ ${state.thresholdSoc.toInt()}%") },
                    )
                }
                Slider(
                    value = state.thresholdSoc,
                    onValueChange = onThresholdChange,
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
                        "${state.thresholdSoc.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("100%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            TriggerActionsSection(
                triggerActions = state.triggerActions,
                minerPreset = state.minerPreset,
                onTriggerActionToggle = onTriggerActionToggle,
                onMinerPresetChange = onMinerPresetChange,
            )

            state.monitorError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            when {
                state.monitoring -> {
                    StatusLine(state)
                    Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                        Text("Stop monitoring")
                    }
                }

                state.alertFired -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ChargeGreen)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            "Alert fired. Monitoring stopped automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChargeGreen,
                        )
                    }
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text("Monitor again")
                    }
                    TextButton(onClick = onDismissAlert, modifier = Modifier.fillMaxWidth()) {
                        Text("Dismiss")
                    }
                }

                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = WarnYellow)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            "Monitoring is off. The bridge checks the battery on the interval you set and stops when the alert fires.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Button(
                        onClick = {
                            if (notificationsGranted) {
                                onStart()
                            } else {
                                onRequestNotifications()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Start monitoring")
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(state: MonitorUiState, onRefresh: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Battery SOC · last 24h",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "5-minute samples · 0-100%",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                IconButton(onClick = onRefresh, enabled = !state.historyLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh chart")
                }
            }

            when {
                state.historyLoading -> {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }

                state.historyError != null -> {
                    Text(
                        state.historyError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRefresh) { Text("Retry") }
                }

                state.historySocs.none { it != null } -> {
                    Text(
                        "No history data yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }

                else -> {
                    val intervalSec = state.historyIntervalMinutes * 60L
                    SocHistoryChart(
                        startEpochSeconds = state.historyStart,
                        intervalSeconds = intervalSec,
                        socs = state.historySocs,
                    )
                    val values = state.historySocs.filterNotNull()
                    if (values.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            HistoryStat("Min", "%.0f%%".format(values.min()))
                            HistoryStat("Max", "%.0f%%".format(values.max()))
                            HistoryStat(
                                "Now",
                                state.currentSoc?.let { "%.1f%%".format(it) } ?: "N/A",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SocCard(state: MonitorUiState) {
    val soc = state.currentSoc ?: state.lastSoc
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "BATTERY SOC",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                soc?.let { "%.1f%%".format(it) } ?: "N/A",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun TriggerActionsSection(
    triggerActions: Set<TriggerAction>,
    minerPreset: MinerPreset,
    onTriggerActionToggle: (TriggerAction, Boolean) -> Unit,
    onMinerPresetChange: (MinerPreset) -> Unit,
) {
    Column {
        Text(
            "When triggered, also run:",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        ActionCheckbox(
            label = "Send notification",
            checked = TriggerAction.NOTIFY in triggerActions,
            onCheckedChange = { onTriggerActionToggle(TriggerAction.NOTIFY, it) },
        )
        ActionCheckbox(
            label = "Turn miners on",
            checked = TriggerAction.MINER_ON in triggerActions,
            onCheckedChange = { onTriggerActionToggle(TriggerAction.MINER_ON, it) },
        )
        ActionCheckbox(
            label = "Turn miners off",
            checked = TriggerAction.MINER_OFF in triggerActions,
            onCheckedChange = { onTriggerActionToggle(TriggerAction.MINER_OFF, it) },
        )
        ActionCheckbox(
            label = "Set power preset",
            checked = TriggerAction.SET_POWER_PRESET in triggerActions,
            onCheckedChange = { onTriggerActionToggle(TriggerAction.SET_POWER_PRESET, it) },
        )
        if (TriggerAction.SET_POWER_PRESET in triggerActions) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MinerPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = minerPreset == preset,
                        onClick = { onMinerPresetChange(preset) },
                        label = { Text(presetLabel(preset)) },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "When \"Turn miners on\" is combined with a power preset, the preset is applied " +
                "~2 minutes later (after the miner boots) and only if the current power target " +
                "differs. The preset alone is applied immediately.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun ActionCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun presetLabel(preset: MinerPreset): String = when (preset) {
    MinerPreset.LOW -> "Low · 1 kW"
    MinerPreset.EFFICIENT -> "Efficient · 2 kW"
    MinerPreset.MAX -> "Max · 2.76 kW"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalDropdown(intervalMinutes: Int, onIntervalChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = (1..60).toList()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = "${intervalMinutes} min",
            onValueChange = {},
            readOnly = true,
            label = { Text("Poll interval") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text("$minutes min") },
                    onClick = {
                        onIntervalChange(minutes)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusLine(state: MonitorUiState) {
    val time = state.lastChecked?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Monitoring every ${state.intervalMinutes} min…",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        val parts = buildList {
            state.lastSoc?.let { add("last SOC: %.1f%%".format(it)) }
            time?.let { add("checked at $it") }
            state.etaMinutes?.let { eta ->
                add("${state.thresholdSoc.toInt()}% in ${SocEtaCalculator.formatMinutes(eta)}")
            }
        }
        if (parts.isNotEmpty()) {
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}
