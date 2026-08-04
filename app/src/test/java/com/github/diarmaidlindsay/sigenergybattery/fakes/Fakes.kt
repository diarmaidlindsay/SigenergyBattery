package com.github.diarmaidlindsay.sigenergybattery.fakes

import com.github.diarmaidlindsay.sigenergybattery.data.api.HermesApi
import com.github.diarmaidlindsay.sigenergybattery.data.api.SolarNowDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.github.diarmaidlindsay.sigenergybattery.data.local.SettingsStore
import com.github.diarmaidlindsay.sigenergybattery.domain.model.BridgeConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MonitorConfig

class FakeSettingsStore(
    initialBridge: BridgeConfig = BridgeConfig("100.105.141.68", "8500", "key123"),
    initialMonitor: MonitorConfig = MonitorConfig(5, 20.0, com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction.AT_OR_BELOW),
    hasConnectedBefore: Boolean = false,
) : SettingsStore {
    private val bridge = MutableStateFlow(initialBridge)
    private val monitor = MutableStateFlow(initialMonitor)
    private val connected = MutableStateFlow(hasConnectedBefore)
    var savedBridge: BridgeConfig? = null
    var savedMonitor: MonitorConfig? = null

    override val bridgeConfig: Flow<BridgeConfig> = bridge
    override val monitorConfig: Flow<MonitorConfig> = monitor
    override val hasConnectedBefore: Flow<Boolean> = connected

    override suspend fun saveBridgeConfig(config: BridgeConfig) {
        savedBridge = config
        bridge.value = config
    }

    override suspend fun saveMonitorConfig(config: MonitorConfig) {
        savedMonitor = config
        monitor.value = config
    }

    override suspend fun setHasConnectedBefore(value: Boolean) {
        connected.value = value
    }
}

class HangingHermesApi : HermesApi {
    override suspend fun solarNow(): SolarNowDto = kotlinx.coroutines.awaitCancellation()
    override suspend fun solarHistory(): com.github.diarmaidlindsay.sigenergybattery.data.api.SolarHistoryDto =
        kotlinx.coroutines.awaitCancellation()
}

class FakeHermesApi(
    private val result: SolarNowDto? = null,
    private val error: Throwable? = null,
    private val history: com.github.diarmaidlindsay.sigenergybattery.data.api.SolarHistoryDto? = null,
) : HermesApi {
    var calls = 0
    var historyCalls = 0
    override suspend fun solarNow(): SolarNowDto {
        calls++
        error?.let { throw it }
        return result ?: SolarNowDto()
    }

    override suspend fun solarHistory(): com.github.diarmaidlindsay.sigenergybattery.data.api.SolarHistoryDto {
        historyCalls++
        error?.let { throw it }
        return history ?: com.github.diarmaidlindsay.sigenergybattery.data.api.SolarHistoryDto()
    }
}
