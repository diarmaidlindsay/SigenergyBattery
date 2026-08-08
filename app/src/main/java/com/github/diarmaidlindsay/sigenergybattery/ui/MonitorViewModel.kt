package com.github.diarmaidlindsay.sigenergybattery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.diarmaidlindsay.sigenergybattery.core.di.AppContainer
import com.github.diarmaidlindsay.sigenergybattery.data.api.DeviceRegisterDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.HermesApi
import com.github.diarmaidlindsay.sigenergybattery.data.api.StrategyConfigDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.StrategyStatusDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.TriggerConfigDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.toSnapshot
import com.github.diarmaidlindsay.sigenergybattery.data.api.toChartEvent
import com.github.diarmaidlindsay.sigenergybattery.data.api.toStrategyConfig
import com.github.diarmaidlindsay.sigenergybattery.data.api.toStrategyConfigDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.toStrategyStatus
import com.github.diarmaidlindsay.sigenergybattery.data.api.toStrategyStep
import com.github.diarmaidlindsay.sigenergybattery.data.local.SettingsStore
import com.github.diarmaidlindsay.sigenergybattery.domain.BatteryMonitor
import com.github.diarmaidlindsay.sigenergybattery.domain.SocEtaCalculator
import com.github.diarmaidlindsay.sigenergybattery.domain.model.BridgeConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.ChartEvent
import com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MinerPreset
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MonitorConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.Season
import com.github.diarmaidlindsay.sigenergybattery.domain.model.SolarSnapshot
import com.github.diarmaidlindsay.sigenergybattery.domain.model.StrategyConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.StrategyStep
import com.github.diarmaidlindsay.sigenergybattery.domain.model.TriggerAction
import com.github.diarmaidlindsay.sigenergybattery.service.PollingState
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import java.io.IOException

data class MonitorUiState(
    val loaded: Boolean = false,
    val connected: Boolean = false,
    val host: String = SettingsStore.DEFAULT_HOST,
    val port: String = SettingsStore.DEFAULT_PORT,
    val apiKey: String = SettingsStore.DEFAULT_API_KEY,
    val isConnecting: Boolean = false,
    val autoConnecting: Boolean = false,
    val connectionError: String? = null,
    val intervalMinutes: Int = SettingsStore.DEFAULT_INTERVAL_MINUTES,
    val thresholdSoc: Float = SettingsStore.DEFAULT_THRESHOLD_SOC.toFloat(),
    val direction: Direction = SettingsStore.DEFAULT_DIRECTION,
    val triggerActions: Set<TriggerAction> = SettingsStore.DEFAULT_TRIGGER_ACTIONS,
    val minerPreset: MinerPreset = SettingsStore.DEFAULT_MINER_PRESET,
    val monitoring: Boolean = false,
    val currentSoc: Double? = null,
    val checking: Boolean = false,
    val lastSoc: Double? = null,
    val lastChecked: Long? = null,
    val etaMinutes: Long? = null,
    val alertFired: Boolean = false,
    val checkError: String? = null,
    val monitorError: String? = null,
    val cancelTriggerOnDisconnect: Boolean = SettingsStore.DEFAULT_CANCEL_TRIGGER_ON_DISCONNECT,
    val historySocs: List<Double?> = emptyList(),
    val historyStart: Long = 0,
    val historyIntervalMinutes: Int = 5,
    val historyLoading: Boolean = false,
    val historyError: String? = null,
    // Trigger + strategy events overlaid on the SOC history chart
    val events: List<ChartEvent> = emptyList(),
    val eventsLoading: Boolean = false,
    val eventsError: String? = null,
    // Strategy (automated miner scheduling)
    val strategyEnabled: Boolean = false,
    val strategyName: String? = null,
    val strategySteps: List<StrategyStep> = emptyList(),
    val strategyCurrentStep: Int = 0,
    val strategyLastTransitionAt: Long? = null,
    val strategyLastSoc: Double? = null,
    val strategyLastError: String? = null,
    val strategyEtaMinutes: Long? = null,
    val strategyActiveHoursStart: String = "06:00",
    val strategyActiveHoursEnd: String = "22:00",
    val strategyIntervalMinutes: Int = 5,
    val strategyLoading: Boolean = false,
    val strategyError: String? = null,
    val strategyTemplates: Map<String, StrategyConfigDto> = emptyMap(),
    val strategyTemplatesLoading: Boolean = false,
    val draftStrategy: StrategyConfig? = null,
    val draftSeason: Season? = null,
    val confirmStartTriggerOverridesStrategy: Boolean = false,
    val confirmStartStrategyOverridesTrigger: Boolean = false,
)

open class MonitorViewModel(
    private val store: SettingsStore,
    private val apiFactory: (BridgeConfig) -> HermesApi,
    private val fcmTokenProvider: suspend () -> String? = defaultFcmTokenProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorUiState())
    open val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    private var autoConnectAttempted = false

    /** Most recent live snapshot, kept for strategy ETA recomputation. */
    private var lastSnapshot: SolarSnapshot? = null

    init {
        viewModelScope.launch {
            store.bridgeConfig.collect { config ->
                _uiState.update {
                    it.copy(
                        host = config.host,
                        port = config.port,
                        apiKey = config.apiKey,
                    )
                }
            }
        }
        viewModelScope.launch {
            store.monitorConfig.collect { config ->
                _uiState.update {
                    it.copy(
                        intervalMinutes = config.intervalMinutes,
                        thresholdSoc = config.thresholdSoc.toFloat(),
                        direction = config.direction,
                        triggerActions = config.triggerActions,
                        minerPreset = config.minerPreset ?: SettingsStore.DEFAULT_MINER_PRESET,
                    )
                }
            }
        }
        viewModelScope.launch {
            store.hasConnectedBefore.collect { hasConnected ->
                if (hasConnected && !autoConnectAttempted) {
                    autoConnectAttempted = true
                    val config = store.bridgeConfig.first()
                    attemptConnect(config, isAuto = true)
                }
            }
        }
        viewModelScope.launch {
            store.cancelTriggerOnDisconnect.collect { value ->
                _uiState.update { it.copy(cancelTriggerOnDisconnect = value) }
            }
        }
        viewModelScope.launch {
            PollingState.active.collect { active ->
                _uiState.update { it.copy(monitoring = active) }
            }
        }
        viewModelScope.launch {
            PollingState.lastSoc.collect { soc ->
                _uiState.update { it.copy(lastSoc = soc) }
            }
        }
        viewModelScope.launch {
            PollingState.lastCheckedAt.collect { ts ->
                _uiState.update { it.copy(lastChecked = ts) }
            }
        }
        viewModelScope.launch {
            PollingState.etaMinutes.collect { eta ->
                _uiState.update { it.copy(etaMinutes = eta) }
            }
        }
        viewModelScope.launch {
            PollingState.alertFired.collect { fired ->
                if (fired) _uiState.update { it.copy(alertFired = true) }
            }
        }
        viewModelScope.launch {
            PollingState.strategyActive.collect { active ->
                _uiState.update { it.copy(strategyEnabled = active) }
            }
        }
        viewModelScope.launch {
            PollingState.strategyCurrentStep.collect { step ->
                _uiState.update {
                    it.copy(
                        strategyCurrentStep = step,
                        strategyEtaMinutes = computeStrategyEta(
                            enabled = it.strategyEnabled,
                            steps = it.strategySteps,
                            currentStep = step,
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            PollingState.strategyName.collect { name ->
                _uiState.update { it.copy(strategyName = name) }
            }
        }
    }

    fun markLoaded() = _uiState.update { it.copy(loaded = true) }

    fun onHostChange(host: String) = _uiState.update {
        it.copy(host = host, connectionError = null)
    }

    fun onPortChange(port: String) = _uiState.update {
        it.copy(port = port, connectionError = null)
    }

    fun onApiKeyChange(apiKey: String) = _uiState.update {
        it.copy(apiKey = apiKey, connectionError = null)
    }

    fun onIntervalChange(minutes: Int) = _uiState.update { it.copy(intervalMinutes = minutes) }

    fun onThresholdChange(soc: Float) = _uiState.update { it.copy(thresholdSoc = soc) }

    fun onDirectionChange(direction: Direction) = _uiState.update { it.copy(direction = direction) }

    /**
     * Toggles a trigger action, normalizing the set so only valid combinations
     * survive. The action being checked wins any conflict, so MINER_ON and
     * MINER_OFF can be switched freely while staying mutually exclusive.
     */
    fun onTriggerActionToggle(action: TriggerAction, checked: Boolean) {
        _uiState.update { state ->
            val updated = if (checked) state.triggerActions + action else state.triggerActions - action
            state.copy(
                triggerActions = BatteryMonitor.normalizeActions(
                    actions = updated,
                    justSelected = if (checked) action else null,
                ),
            )
        }
    }

    fun onMinerPresetChange(preset: MinerPreset) =
        _uiState.update { it.copy(minerPreset = preset) }

    /**
     * Toggles whether tapping Disconnect also cancels the bridge trigger. When
     * off, disconnecting leaves the scheduled monitoring running on the bridge.
     */
    fun onCancelTriggerOnDisconnectChange(value: Boolean) {
        _uiState.update { it.copy(cancelTriggerOnDisconnect = value) }
        viewModelScope.launch { store.setCancelTriggerOnDisconnect(value) }
    }

    fun currentConfig(): BridgeConfig = with(_uiState.value) {
        BridgeConfig(host = host, port = port, apiKey = apiKey)
    }

    fun connect() {
        val state = _uiState.value
        if (state.isConnecting || state.autoConnecting) return
        val host = state.host.trim()
        val port = state.port.trim()
        if (host.isEmpty() || port.isEmpty()) {
            _uiState.update { it.copy(connectionError = "Enter the bridge IP and port") }
            return
        }
        if (state.apiKey.isBlank()) {
            _uiState.update { it.copy(connectionError = "Enter the API key") }
            return
        }
        attemptConnect(
            config = BridgeConfig(host = host, port = port, apiKey = state.apiKey),
            isAuto = false,
        )
    }

    private fun attemptConnect(config: BridgeConfig, isAuto: Boolean) {
        _uiState.update {
            it.copy(
                isConnecting = true,
                autoConnecting = isAuto,
                connectionError = null,
                currentSoc = null,
            )
        }
        viewModelScope.launch {
            try {
                val snapshot = withTimeout(CONNECT_TIMEOUT_MS) {
                    apiFactory(config).solarNow().toSnapshot()
                }
                store.saveBridgeConfig(config)
                store.setHasConnectedBefore(true)
                val now = System.currentTimeMillis()
                lastSnapshot = snapshot
                val strategyEta = computeStrategyEta(
                    enabled = _uiState.value.strategyEnabled,
                    steps = _uiState.value.strategySteps,
                    currentStep = _uiState.value.strategyCurrentStep,
                )
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        autoConnecting = false,
                        connected = true,
                        currentSoc = snapshot.socPct,
                        lastSoc = snapshot.socPct,
                        lastChecked = now,
                        etaMinutes = computeEta(snapshot),
                        strategyEtaMinutes = strategyEta,
                    )
                }
                loadHistory()
                loadEvents()
                syncTriggerStatus()
                syncStrategyStatus()
                loadStrategyTemplates()
            } catch (e: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        autoConnecting = false,
                        connectionError = "Connection timed out after 30 seconds",
                    )
                }
            } catch (e: HttpException) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        autoConnecting = false,
                        connectionError = when (e.code()) {
                            401 -> "Unauthorized. Check the API key"
                            404 -> "API not found on this host/port"
                            else -> "Bridge error (HTTP ${e.code()})"
                        },
                    )
                }
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        autoConnecting = false,
                        connectionError = "Cannot reach ${config.host}:${config.port}",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        autoConnecting = false,
                        connectionError = e.message ?: "Connection failed",
                    )
                }
            }
        }
    }

    fun checkNow() {
        if (_uiState.value.checking) return
        _uiState.update { it.copy(checking = true, checkError = null) }
        val config = currentConfig()
        viewModelScope.launch {
            try {
                val snapshot = apiFactory(config).solarNow().toSnapshot()
                val now = System.currentTimeMillis()
                lastSnapshot = snapshot
                val strategyEta = computeStrategyEta(
                    enabled = _uiState.value.strategyEnabled,
                    steps = _uiState.value.strategySteps,
                    currentStep = _uiState.value.strategyCurrentStep,
                )
                _uiState.update {
                    it.copy(
                        checking = false,
                        currentSoc = snapshot.socPct,
                        lastSoc = snapshot.socPct,
                        lastChecked = now,
                        etaMinutes = computeEta(snapshot),
                        strategyEtaMinutes = strategyEta,
                        checkError = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(checking = false, checkError = e.message ?: "Check failed")
                }
            }
        }
    }

    /** Refreshes SOC, history, events, and the bridge trigger status. */
    fun refreshAll() {
        if (!_uiState.value.connected) return
        checkNow()
        loadHistory()
        loadEvents()
        syncTriggerStatus()
        syncStrategyStatus()
    }

    private fun computeEta(snapshot: SolarSnapshot): Long? {
        val soc = snapshot.socPct ?: return null
        val batteryKw = snapshot.batteryKw ?: return null
        val capacityKwh = snapshot.capacityKwh ?: return null
        return SocEtaCalculator.minutesToTarget(
            currentSoc = soc,
            targetSoc = _uiState.value.thresholdSoc.toDouble(),
            batteryKw = batteryKw,
            capacityKwh = capacityKwh,
        )
    }

    /** ETA for the running strategy to reach the next step's SOC threshold,
     * computed from the most recent live snapshot. Null when there is no next
     * step, no snapshot data, or the current rate opposes the target. */
    private fun computeStrategyEta(
        enabled: Boolean,
        steps: List<StrategyStep>,
        currentStep: Int,
    ): Long? {
        if (!enabled) return null
        val nextStep = steps.getOrNull(currentStep + 1) ?: return null
        val snapshot = lastSnapshot ?: return null
        val soc = snapshot.socPct ?: return null
        val batteryKw = snapshot.batteryKw ?: return null
        val capacityKwh = snapshot.capacityKwh ?: return null
        return SocEtaCalculator.minutesToTarget(
            currentSoc = soc,
            targetSoc = nextStep.condition.socThreshold,
            batteryKw = batteryKw,
            capacityKwh = capacityKwh,
        )
    }

    fun loadHistory() {
        if (_uiState.value.historyLoading) return
        _uiState.update { it.copy(historyLoading = true, historyError = null) }
        val config = currentConfig()
        viewModelScope.launch {
            try {
                val dto = apiFactory(config).solarHistory()
                _uiState.update {
                    it.copy(
                        historyLoading = false,
                        historyError = null,
                        historySocs = dto.points.map { p -> p.soc },
                        historyStart = dto.start,
                        historyIntervalMinutes = dto.intervalMinutes.takeIf { i -> i > 0 } ?: 5,
                    )
                }
            } catch (e: HttpException) {
                _uiState.update {
                    it.copy(historyLoading = false, historyError = "History unavailable (HTTP ${e.code()})")
                }
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(historyLoading = false, historyError = "Cannot reach bridge for history")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(historyLoading = false, historyError = e.message ?: "History load failed")
                }
            }
        }
    }

    /**
     * Loads the trigger + strategy events recorded on the bridge so the SOC
     * chart can overlay dots. Best-effort: a failure sets [eventsError] but
     * never blocks history or the rest of the UI.
     */
    fun loadEvents() {
        if (_uiState.value.eventsLoading) return
        _uiState.update { it.copy(eventsLoading = true, eventsError = null) }
        val config = currentConfig()
        viewModelScope.launch {
            try {
                val dto = apiFactory(config).events()
                _uiState.update {
                    it.copy(
                        eventsLoading = false,
                        eventsError = null,
                        events = dto.events.map { event -> event.toChartEvent() },
                    )
                }
            } catch (e: HttpException) {
                _uiState.update {
                    it.copy(eventsLoading = false, eventsError = "Events unavailable (HTTP ${e.code()})")
                }
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(eventsLoading = false, eventsError = "Cannot reach bridge for events")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(eventsLoading = false, eventsError = e.message ?: "Events load failed")
                }
            }
        }
    }

    /**
     * Arms the battery SOC trigger on the bridge. The bridge owns the
     * scheduling; this app just configures it and registers for FCM pushes.
     */
    fun beginMonitoring() {
        val state = _uiState.value
        val actions = BatteryMonitor.normalizeActions(state.triggerActions)
            .ifEmpty { setOf(TriggerAction.NOTIFY) }
        val config = MonitorConfig(
            intervalMinutes = state.intervalMinutes,
            thresholdSoc = state.thresholdSoc.toDouble(),
            direction = state.direction,
            triggerActions = actions,
            minerPreset = if (TriggerAction.SET_POWER_PRESET in actions) state.minerPreset else null,
        )
        viewModelScope.launch {
            store.saveMonitorConfig(config)
            _uiState.update { it.copy(monitorError = null) }
            runCatching {
                apiFactory(currentConfig()).setTrigger(config.toTriggerDto())
            }.onSuccess {
                _uiState.update { it.copy(alertFired = false) }
                PollingState.alertFired.value = false
                PollingState.active.value = true
                // Arming a one-shot trigger cancels any running strategy.
                PollingState.strategyActive.value = false
                PollingState.strategyName.value = null
                PollingState.strategyCurrentStep.value = 0
                _uiState.update {
                    it.copy(
                        strategyEnabled = false,
                        strategyName = null,
                        strategyCurrentStep = 0,
                        strategyEtaMinutes = null,
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(monitorError = e.toMonitorMessage()) }
            }
            registerDeviceToken()
        }
    }

    fun cancelMonitoring() {
        viewModelScope.launch {
            runCatching { apiFactory(currentConfig()).deleteTrigger() }
                .onFailure { e ->
                    _uiState.update { it.copy(monitorError = e.toMonitorMessage()) }
                }
            PollingState.active.value = false
            PollingState.alertFired.value = false
            _uiState.update { it.copy(alertFired = false) }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            if (_uiState.value.cancelTriggerOnDisconnect) {
                runCatching { apiFactory(currentConfig()).deleteTrigger() }
            }
        }
        PollingState.active.value = false
        PollingState.alertFired.value = false
        _uiState.update { it.copy(connected = false, alertFired = false) }
    }

    fun clearAlert() = _uiState.update { it.copy(alertFired = false) }

    /** Reconciles local state with the bridge's trigger status. */
    private fun syncTriggerStatus() {
        if (!_uiState.value.connected) return
        viewModelScope.launch {
            runCatching {
                val status = apiFactory(currentConfig()).getTrigger()
                PollingState.active.value = status.enabled && !status.fired
                PollingState.alertFired.value = status.fired
                status.lastSoc?.let { PollingState.lastSoc.value = it }
                status.lastCheckedAt?.let { ts ->
                    PollingState.lastCheckedAt.value = (ts * 1000).toLong()
                }
            }
        }
    }

    /** Registers this device's FCM token with the bridge (best effort). */
    private suspend fun registerDeviceToken() {
        val token = fcmTokenProvider() ?: return
        runCatching { apiFactory(currentConfig()).registerDevice(DeviceRegisterDto(token)) }
    }

    // ------------------------------------------------------------------
    // Strategy (automated miner scheduling)
    // ------------------------------------------------------------------

    /** Fetches the built-in seasonal templates so the UI can offer them. */
    fun loadStrategyTemplates() {
        if (!_uiState.value.connected || _uiState.value.strategyTemplates.isNotEmpty()) return
        _uiState.update { it.copy(strategyTemplatesLoading = true, strategyError = null) }
        viewModelScope.launch {
            runCatching { apiFactory(currentConfig()).strategyTemplates() }
                .onSuccess { dto ->
                    _uiState.update {
                        it.copy(
                            strategyTemplatesLoading = false,
                            strategyTemplates = dto.templates,
                            // Prime the draft with the running strategy if we
                            // have one and no draft exists yet.
                            draftStrategy = it.draftStrategy ?: runningStrategyFromState(it),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(strategyTemplatesLoading = false, strategyError = e.toMonitorMessage())
                    }
                }
        }
    }

    /** Reconciles local state with the bridge's running strategy. */
    fun syncStrategyStatus() {
        if (!_uiState.value.connected) return
        viewModelScope.launch {
            runCatching { apiFactory(currentConfig()).getStrategy() }
                .onSuccess { status ->
                    applyStrategyStatus(status)
                }
        }
    }

    private fun applyStrategyStatus(status: StrategyStatusDto) {
        val mapped = status.toStrategyStatus()
        PollingState.strategyActive.value = mapped.enabled
        PollingState.strategyName.value = mapped.name
        PollingState.strategyCurrentStep.value = mapped.currentStep
        _uiState.update {
            it.copy(
                strategyEnabled = mapped.enabled,
                strategyName = mapped.name,
                strategySteps = mapped.steps,
                strategyCurrentStep = mapped.currentStep,
                strategyLastTransitionAt = mapped.lastTransitionAt,
                strategyLastSoc = mapped.lastSoc,
                strategyLastError = mapped.lastError,
                strategyActiveHoursStart = mapped.activeHoursStart ?: it.strategyActiveHoursStart,
                strategyActiveHoursEnd = mapped.activeHoursEnd ?: it.strategyActiveHoursEnd,
                strategyIntervalMinutes = mapped.intervalMinutes,
                strategyEtaMinutes = computeStrategyEta(
                    enabled = mapped.enabled,
                    steps = mapped.steps,
                    currentStep = mapped.currentStep,
                ),
            )
        }
    }

    /** Loads a seasonal template into the editable draft. */
    fun onSelectStrategyTemplate(season: Season, config: StrategyConfig) {
        _uiState.update { it.copy(draftSeason = season, draftStrategy = config, strategyError = null) }
    }

    /** Updates the editable draft as the user edits steps/active hours. */
    fun updateDraftStrategy(draft: StrategyConfig) {
        _uiState.update { it.copy(draftStrategy = draft, strategyError = null) }
    }

    /** Handles the Start-strategy button, confirming when a one-shot trigger
     * is active (starting a strategy cancels it). */
    fun onStartStrategyClick() {
        if (_uiState.value.strategyEnabled) return
        val draft = _uiState.value.draftStrategy ?: return
        if (draft.steps.isEmpty()) return
        if (_uiState.value.monitoring) {
            _uiState.update { it.copy(confirmStartStrategyOverridesTrigger = true) }
        } else {
            startStrategy()
        }
    }

    fun confirmStartStrategyOverridesTrigger() {
        _uiState.update { it.copy(confirmStartStrategyOverridesTrigger = false) }
        startStrategy()
    }

    fun dismissStartStrategyOverridesTrigger() {
        _uiState.update { it.copy(confirmStartStrategyOverridesTrigger = false) }
    }

    /** Starts the editable draft strategy on the bridge. */
    fun startStrategy() {
        val draft = _uiState.value.draftStrategy ?: return
        if (draft.steps.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(strategyLoading = true, strategyError = null) }
            runCatching { apiFactory(currentConfig()).setStrategy(draft.toStrategyConfigDto()) }
                .onSuccess { status ->
                    store.saveStrategyConfig(draft)
                    PollingState.strategyActive.value = true
                    PollingState.strategyName.value = status.name
                    PollingState.strategyCurrentStep.value = status.currentStep
                    // Starting a strategy cancels any armed one-shot trigger.
                    PollingState.active.value = false
                    PollingState.alertFired.value = false
                    _uiState.update {
                        it.copy(
                            strategyLoading = false,
                            strategyEnabled = true,
                            strategyName = status.name,
                            strategySteps = status.steps.map { step -> step.toStrategyStep() },
                            strategyCurrentStep = status.currentStep,
                            strategyLastTransitionAt = status.lastTransitionAt?.let { ts -> (ts * 1000).toLong() },
                            strategyLastSoc = status.lastSoc,
                            strategyLastError = status.lastError,
                            strategyActiveHoursStart = status.activeHoursStart ?: draft.activeHoursStart,
                            strategyActiveHoursEnd = status.activeHoursEnd ?: draft.activeHoursEnd,
                            strategyIntervalMinutes = status.intervalMinutes,
                            strategyEtaMinutes = computeStrategyEta(
                                enabled = true,
                                steps = status.steps.map { step -> step.toStrategyStep() },
                                currentStep = status.currentStep,
                            ),
                            alertFired = false,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(strategyLoading = false, strategyError = e.toMonitorMessage()) }
                }
        }
    }

    /** Stops the running strategy on the bridge. */
    fun stopStrategy() {
        viewModelScope.launch {
            runCatching { apiFactory(currentConfig()).deleteStrategy() }
                .onFailure { e ->
                    _uiState.update { it.copy(strategyError = e.toMonitorMessage()) }
                }
            PollingState.strategyActive.value = false
            PollingState.strategyName.value = null
            PollingState.strategyCurrentStep.value = 0
            _uiState.update {
                it.copy(
                    strategyEnabled = false,
                    strategyName = null,
                    strategyCurrentStep = 0,
                    strategyLastTransitionAt = null,
                    strategyLastSoc = null,
                    strategyLastError = null,
                    strategyEtaMinutes = null,
                )
            }
        }
    }

    /** Handles the one-shot Start-monitoring button, confirming when a
     * strategy is running (arming a trigger cancels it). */
    fun onStartMonitoringClick() {
        if (_uiState.value.monitoring) return
        if (_uiState.value.strategyEnabled) {
            _uiState.update { it.copy(confirmStartTriggerOverridesStrategy = true) }
        } else {
            beginMonitoring()
        }
    }

    fun confirmStartTriggerOverridesStrategy() {
        _uiState.update { it.copy(confirmStartTriggerOverridesStrategy = false) }
        beginMonitoring()
    }

    fun dismissStartTriggerOverridesStrategy() {
        _uiState.update { it.copy(confirmStartTriggerOverridesStrategy = false) }
    }

    private fun runningStrategyFromState(state: MonitorUiState): StrategyConfig? =
        if (state.strategyEnabled) {
            StrategyConfig(
                name = state.strategyName ?: "Miner Strategy",
                intervalMinutes = state.strategyIntervalMinutes,
                activeHoursStart = state.strategyActiveHoursStart,
                activeHoursEnd = state.strategyActiveHoursEnd,
                steps = state.strategySteps,
            )
        } else {
            null
        }

    private fun MonitorConfig.toTriggerDto(): TriggerConfigDto = TriggerConfigDto(
        intervalMinutes = intervalMinutes,
        thresholdSoc = thresholdSoc,
        direction = direction.name,
        actions = triggerActions.map { it.name },
        minerPreset = minerPreset?.slug,
    )

    private fun Throwable.toMonitorMessage(): String = when (this) {
        is HttpException -> when (code()) {
            401 -> "Unauthorized. Check the API key"
            else -> "Bridge error (HTTP ${code()})"
        }
        is IOException -> "Cannot reach ${currentConfig().host}:${currentConfig().port}"
        else -> message ?: "Failed to update monitoring"
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 30_000L

        private val defaultFcmTokenProvider: suspend () -> String? = {
            runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MonitorViewModel::class.java)) {
                return MonitorViewModel(
                    store = container.settingsStore,
                    apiFactory = container::createApi,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
