package com.github.diarmaidlindsay.sigenergybattery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.diarmaidlindsay.sigenergybattery.core.di.AppContainer
import com.github.diarmaidlindsay.sigenergybattery.data.api.HermesApi
import com.github.diarmaidlindsay.sigenergybattery.data.api.toSnapshot
import com.github.diarmaidlindsay.sigenergybattery.data.local.SettingsStore
import com.github.diarmaidlindsay.sigenergybattery.domain.SocEtaCalculator
import com.github.diarmaidlindsay.sigenergybattery.domain.model.BridgeConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MonitorConfig
import com.github.diarmaidlindsay.sigenergybattery.service.PollingState
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val monitoring: Boolean = false,
    val currentSoc: Double? = null,
    val checking: Boolean = false,
    val lastSoc: Double? = null,
    val lastChecked: Long? = null,
    val etaMinutes: Long? = null,
    val alertFired: Boolean = false,
    val checkError: String? = null,
    val historySocs: List<Double?> = emptyList(),
    val historyStart: Long = 0,
    val historyIntervalMinutes: Int = 5,
    val historyLoading: Boolean = false,
    val historyError: String? = null,
)

open class MonitorViewModel(
    private val store: SettingsStore,
    private val apiFactory: (BridgeConfig) -> HermesApi,
    private val startMonitoring: (MonitorConfig) -> Unit,
    private val stopMonitoring: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorUiState())
    open val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    private var autoConnectAttempted = false

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
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        autoConnecting = false,
                        connected = true,
                        currentSoc = snapshot.socPct,
                        lastSoc = snapshot.socPct,
                        lastChecked = now,
                        etaMinutes = computeEta(snapshot),
                    )
                }
                loadHistory()
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
                            401 -> "Unauthorized — check the API key"
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
                _uiState.update {
                    it.copy(
                        checking = false,
                        currentSoc = snapshot.socPct,
                        lastSoc = snapshot.socPct,
                        lastChecked = now,
                        etaMinutes = computeEta(snapshot),
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

    /** Refreshes every SOC value (top card, history chart, and last-SOC line). */
    fun refreshAll() {
        if (!_uiState.value.connected) return
        checkNow()
        loadHistory()
    }

    private fun computeEta(snapshot: com.github.diarmaidlindsay.sigenergybattery.domain.model.SolarSnapshot): Long? {
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

    fun beginMonitoring() {
        val config = MonitorConfig(
            intervalMinutes = _uiState.value.intervalMinutes,
            thresholdSoc = _uiState.value.thresholdSoc.toDouble(),
            direction = _uiState.value.direction,
        )
        viewModelScope.launch {
            store.saveMonitorConfig(config)
        }
        _uiState.update { it.copy(alertFired = false) }
        PollingState.alertFired.value = false
        startMonitoring(config)
    }

    fun cancelMonitoring() {
        stopMonitoring()
    }

    fun disconnect() {
        stopMonitoring()
        _uiState.update { it.copy(connected = false, alertFired = false) }
    }

    fun clearAlert() = _uiState.update { it.copy(alertFired = false) }

    companion object {
        const val CONNECT_TIMEOUT_MS = 30_000L
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MonitorViewModel::class.java)) {
                return MonitorViewModel(
                    store = container.settingsStore,
                    apiFactory = container::createApi,
                    startMonitoring = container::startMonitoring,
                    stopMonitoring = container::stopMonitoring,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
