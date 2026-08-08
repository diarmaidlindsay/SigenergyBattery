package com.github.diarmaidlindsay.sigenergybattery.fakes

import com.github.diarmaidlindsay.sigenergybattery.data.api.HermesApi
import com.github.diarmaidlindsay.sigenergybattery.data.api.MinerActionResponse
import com.github.diarmaidlindsay.sigenergybattery.data.api.MinerStatusDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.SolarNowDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.DeviceRegisterDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.EventsDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.StrategyConfigDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.StrategyStatusDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.StrategyTemplatesDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.TriggerAckDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.TriggerConfigDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.TriggerStatusDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.github.diarmaidlindsay.sigenergybattery.data.local.SettingsStore
import com.github.diarmaidlindsay.sigenergybattery.domain.model.BridgeConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MonitorConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.StrategyConfig

class FakeSettingsStore(
    initialBridge: BridgeConfig = BridgeConfig("100.105.141.68", "8500", "key123"),
    initialMonitor: MonitorConfig = MonitorConfig(5, 20.0, com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction.AT_OR_BELOW),
    hasConnectedBefore: Boolean = false,
    initialCancelTriggerOnDisconnect: Boolean = true,
    initialStrategy: StrategyConfig? = null,
) : SettingsStore {
    private val bridge = MutableStateFlow(initialBridge)
    private val monitor = MutableStateFlow(initialMonitor)
    private val connected = MutableStateFlow(hasConnectedBefore)
    private val cancelOnDisconnect = MutableStateFlow(initialCancelTriggerOnDisconnect)
    private val strategy = MutableStateFlow(initialStrategy)
    var savedBridge: BridgeConfig? = null
    var savedMonitor: MonitorConfig? = null
    var savedCancelOnDisconnect: Boolean? = null
    var savedStrategy: StrategyConfig? = null

    override val bridgeConfig: Flow<BridgeConfig> = bridge
    override val monitorConfig: Flow<MonitorConfig> = monitor
    override val hasConnectedBefore: Flow<Boolean> = connected
    override val cancelTriggerOnDisconnect: Flow<Boolean> = cancelOnDisconnect
    override val strategyConfig: Flow<StrategyConfig?> = strategy

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

    override suspend fun setCancelTriggerOnDisconnect(value: Boolean) {
        savedCancelOnDisconnect = value
        cancelOnDisconnect.value = value
    }

    override suspend fun saveStrategyConfig(config: StrategyConfig?) {
        savedStrategy = config
        strategy.value = config
    }
}

class HangingHermesApi : HermesApi {
    override suspend fun solarNow(): SolarNowDto = kotlinx.coroutines.awaitCancellation()
    override suspend fun solarHistory(): com.github.diarmaidlindsay.sigenergybattery.data.api.SolarHistoryDto =
        kotlinx.coroutines.awaitCancellation()
    override suspend fun minerOn(): MinerActionResponse = kotlinx.coroutines.awaitCancellation()
    override suspend fun minerOff(): MinerActionResponse = kotlinx.coroutines.awaitCancellation()
    override suspend fun setPowerPreset(preset: String): MinerActionResponse =
        kotlinx.coroutines.awaitCancellation()
    override suspend fun minerStatus(): MinerStatusDto = kotlinx.coroutines.awaitCancellation()
    override suspend fun setTrigger(body: TriggerConfigDto): TriggerStatusDto =
        kotlinx.coroutines.awaitCancellation()
    override suspend fun getTrigger(): TriggerStatusDto = kotlinx.coroutines.awaitCancellation()
    override suspend fun deleteTrigger(): TriggerAckDto = kotlinx.coroutines.awaitCancellation()
    override suspend fun registerDevice(body: DeviceRegisterDto): TriggerAckDto =
        kotlinx.coroutines.awaitCancellation()
    override suspend fun strategyTemplates(): StrategyTemplatesDto = kotlinx.coroutines.awaitCancellation()
    override suspend fun setStrategy(body: StrategyConfigDto): StrategyStatusDto =
        kotlinx.coroutines.awaitCancellation()
    override suspend fun getStrategy(): StrategyStatusDto = kotlinx.coroutines.awaitCancellation()
    override suspend fun deleteStrategy(): TriggerAckDto = kotlinx.coroutines.awaitCancellation()
    override suspend fun events(): EventsDto = kotlinx.coroutines.awaitCancellation()
}

class FakeHermesApi(
    private val result: SolarNowDto? = null,
    private val error: Throwable? = null,
    private val history: com.github.diarmaidlindsay.sigenergybattery.data.api.SolarHistoryDto? = null,
    private val minerStatus: MinerStatusDto? = null,
    private val triggerStatus: TriggerStatusDto? = null,
    private val triggerError: Throwable? = null,
    private val strategyStatus: StrategyStatusDto? = null,
    private val strategyTemplates: StrategyTemplatesDto? = null,
    private val strategyError: Throwable? = null,
    private val events: EventsDto? = null,
    private val eventsError: Throwable? = null,
) : HermesApi {
    var calls = 0
    var historyCalls = 0
    var minerOnCalls = 0
    var minerOffCalls = 0
    var powerPresetCalls = 0
    var minerStatusCalls = 0
    var setTriggerCalls = 0
    var getTriggerCalls = 0
    var deleteTriggerCalls = 0
    var deviceRegisterCalls = 0
    var templatesCalls = 0
    var setStrategyCalls = 0
    var getStrategyCalls = 0
    var deleteStrategyCalls = 0
    var eventsCalls = 0
    var lastTrigger: TriggerConfigDto? = null
    var lastDeviceToken: String? = null
    var lastPreset: String? = null
    var lastStrategy: StrategyConfigDto? = null

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

    override suspend fun minerOn(): MinerActionResponse {
        minerOnCalls++
        error?.let { throw it }
        return MinerActionResponse("ok")
    }

    override suspend fun minerOff(): MinerActionResponse {
        minerOffCalls++
        error?.let { throw it }
        return MinerActionResponse("ok")
    }

    override suspend fun setPowerPreset(preset: String): MinerActionResponse {
        powerPresetCalls++
        lastPreset = preset
        error?.let { throw it }
        return MinerActionResponse("ok")
    }

    override suspend fun minerStatus(): MinerStatusDto {
        minerStatusCalls++
        error?.let { throw it }
        return minerStatus ?: MinerStatusDto()
    }

    override suspend fun setTrigger(body: TriggerConfigDto): TriggerStatusDto {
        setTriggerCalls++
        lastTrigger = body
        triggerError?.let { throw it }
        return triggerStatus ?: TriggerStatusDto(enabled = true)
    }

    override suspend fun getTrigger(): TriggerStatusDto {
        getTriggerCalls++
        triggerError?.let { throw it }
        return triggerStatus ?: TriggerStatusDto()
    }

    override suspend fun deleteTrigger(): TriggerAckDto {
        deleteTriggerCalls++
        triggerError?.let { throw it }
        return TriggerAckDto("ok")
    }

    override suspend fun registerDevice(body: DeviceRegisterDto): TriggerAckDto {
        deviceRegisterCalls++
        lastDeviceToken = body.token
        triggerError?.let { throw it }
        return TriggerAckDto("ok")
    }

    override suspend fun strategyTemplates(): StrategyTemplatesDto {
        templatesCalls++
        strategyError?.let { throw it }
        return strategyTemplates ?: StrategyTemplatesDto()
    }

    override suspend fun setStrategy(body: StrategyConfigDto): StrategyStatusDto {
        setStrategyCalls++
        lastStrategy = body
        strategyError?.let { throw it }
        return strategyStatus ?: StrategyStatusDto(enabled = true, currentStep = 0)
    }

    override suspend fun getStrategy(): StrategyStatusDto {
        getStrategyCalls++
        strategyError?.let { throw it }
        return strategyStatus ?: StrategyStatusDto()
    }

    override suspend fun deleteStrategy(): TriggerAckDto {
        deleteStrategyCalls++
        strategyError?.let { throw it }
        return TriggerAckDto("ok")
    }

    override suspend fun events(): EventsDto {
        eventsCalls++
        eventsError?.let { throw it }
        return events ?: EventsDto()
    }
}
