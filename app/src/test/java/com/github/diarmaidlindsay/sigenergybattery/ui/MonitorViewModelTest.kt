package com.github.diarmaidlindsay.sigenergybattery.ui

import com.github.diarmaidlindsay.sigenergybattery.MainDispatcherRule
import com.github.diarmaidlindsay.sigenergybattery.data.api.SolarNowDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.TriggerConfigDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.TriggerStatusDto
import com.github.diarmaidlindsay.sigenergybattery.data.local.SettingsStore
import com.github.diarmaidlindsay.sigenergybattery.domain.model.BridgeConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MinerPreset
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MonitorConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.TriggerAction
import com.github.diarmaidlindsay.sigenergybattery.fakes.FakeHermesApi
import com.github.diarmaidlindsay.sigenergybattery.fakes.FakeSettingsStore
import com.github.diarmaidlindsay.sigenergybattery.fakes.HangingHermesApi
import com.github.diarmaidlindsay.sigenergybattery.service.PollingState
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class MonitorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun resetPollingState() {
        PollingState.reset()
    }

    private val config = BridgeConfig("100.105.141.68", "8500", "key123")
    private val store = FakeSettingsStore(initialBridge = config)

    @Test
    fun connect_success_setsConnectedAndSavesConfig() = runTest {
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { FakeHermesApi(result = SolarNowDto(batterySocPct = 77.5)) },
        )
        vm.onHostChange("100.105.141.68")
        vm.onPortChange("8500")
        vm.onApiKeyChange("key123")
        vm.connect()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.connected)
        assertEquals(77.5, vm.uiState.value.currentSoc!!, 0.001)
        assertEquals(config, store.savedBridge)
    }

    @Test
    fun connect_unauthorized_showsErrorMessage() = runTest {
        val unauthorized = HttpException(
            Response.error<Any>(401, "unauthorized".toResponseBody())
        )
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { FakeHermesApi(error = unauthorized) },
        )
        vm.onHostChange("100.105.141.68")
        vm.onPortChange("8500")
        vm.onApiKeyChange("wrong")
        vm.connect()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.connected)
        assertEquals("Unauthorized. Check the API key", vm.uiState.value.connectionError)
    }

    @Test
    fun connect_unreachableHost_showsError() = runTest {
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { FakeHermesApi(error = IOException("timeout")) },
        )
        vm.onHostChange("10.0.0.1")
        vm.onPortChange("8500")
        vm.onApiKeyChange("key123")
        vm.connect()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.connected)
        assertEquals("Cannot reach 10.0.0.1:8500", vm.uiState.value.connectionError)
    }

    @Test
    fun blankFields_blockConnect() = runTest {
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { FakeHermesApi() },
        )
        vm.onHostChange("")
        vm.onPortChange("")
        vm.connect()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.connected)
        assertEquals("Enter the bridge IP and port", vm.uiState.value.connectionError)
    }

    @Test
    fun beginMonitoring_postsTriggerAndSavesConfig() = runTest {
        val api = FakeHermesApi()
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { api },
            fcmTokenProvider = { null },
        )
        vm.onIntervalChange(15)
        vm.onThresholdChange(30f)
        vm.onDirectionChange(Direction.AT_OR_ABOVE)
        vm.beginMonitoring()
        advanceUntilIdle()

        assertEquals(1, api.setTriggerCalls)
        assertEquals(
            TriggerConfigDto(
                intervalMinutes = 15,
                thresholdSoc = 30.0,
                direction = "AT_OR_ABOVE",
                actions = listOf("NOTIFY"),
                minerPreset = null,
            ),
            api.lastTrigger,
        )
        assertEquals(MonitorConfig(15, 30.0, Direction.AT_OR_ABOVE), store.savedMonitor)
        assertTrue(vm.uiState.value.monitoring)

        vm.cancelMonitoring()
        advanceUntilIdle()
        assertEquals(1, api.deleteTriggerCalls)
        assertFalse(vm.uiState.value.monitoring)
    }

    @Test
    fun connect_requiresApiKey() = runTest {
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { FakeHermesApi() },
        )
        vm.onHostChange("100.105.141.68")
        vm.onPortChange("8500")
        vm.onApiKeyChange("")
        vm.connect()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.connected)
        assertEquals("Enter the API key", vm.uiState.value.connectionError)
    }

    @Test
    fun loadHistory_populatesSocs() = runTest {
        val history = com.github.diarmaidlindsay.sigenergybattery.data.api.SolarHistoryDto(
            intervalMinutes = 5,
            start = 1000L,
            points = listOf(
                com.github.diarmaidlindsay.sigenergybattery.data.api.HistoryPointDto(t = 1000L, soc = 50.0),
                com.github.diarmaidlindsay.sigenergybattery.data.api.HistoryPointDto(t = 1300L, soc = 48.0),
                com.github.diarmaidlindsay.sigenergybattery.data.api.HistoryPointDto(t = 1600L, soc = null),
            ),
        )
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { FakeHermesApi(history = history) },
        )
        vm.loadHistory()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.historyLoading)
        assertNull(state.historyError)
        assertEquals(listOf(50.0, 48.0, null), state.historySocs)
        assertEquals(1000L, state.historyStart)
        assertEquals(5, state.historyIntervalMinutes)
    }

    @Test
    fun loadHistory_errorSetsError() = runTest {
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { FakeHermesApi(error = IOException("timeout")) },
        )
        vm.loadHistory()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.historyLoading)
        assertEquals("Cannot reach bridge for history", vm.uiState.value.historyError)
    }

    @Test
    fun autoConnect_succeedsOnStartupWhenPreviouslyConnected() = runTest {
        val storeWithHistory = FakeSettingsStore(
            initialBridge = config,
            hasConnectedBefore = true,
        )
        val vm = MonitorViewModel(
            store = storeWithHistory,
            apiFactory = { FakeHermesApi(result = SolarNowDto(batterySocPct = 60.0)) },
        )
        advanceUntilIdle()

        assertTrue(vm.uiState.value.connected)
        assertEquals(60.0, vm.uiState.value.currentSoc!!, 0.001)
        assertFalse(vm.uiState.value.autoConnecting)
    }

    @Test
    fun autoConnect_doesNotRunOnFirstLaunch() = runTest {
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { FakeHermesApi(result = SolarNowDto(batterySocPct = 60.0)) },
        )
        advanceUntilIdle()

        assertFalse(vm.uiState.value.connected)
        assertFalse(vm.uiState.value.isConnecting)
        assertFalse(vm.uiState.value.autoConnecting)
    }

    @Test
    fun autoConnect_failureFallsBackToConnectScreen() = runTest {
        val storeWithHistory = FakeSettingsStore(
            initialBridge = config,
            hasConnectedBefore = true,
        )
        val vm = MonitorViewModel(
            store = storeWithHistory,
            apiFactory = { FakeHermesApi(error = IOException("timeout")) },
        )
        advanceUntilIdle()

        assertFalse(vm.uiState.value.connected)
        assertFalse(vm.uiState.value.autoConnecting)
        assertEquals("Cannot reach 100.105.141.68:8500", vm.uiState.value.connectionError)
    }

    @Test
    fun connect_timesOutAfter30Seconds() = runTest {
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { HangingHermesApi() },
        )
        vm.onHostChange("10.0.0.99")
        vm.onPortChange("8500")
        vm.onApiKeyChange("key123")
        vm.connect()
        advanceTimeBy(31_000)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.connected)
        assertFalse(vm.uiState.value.isConnecting)
        assertEquals("Connection timed out after 30 seconds", vm.uiState.value.connectionError)
    }

    @Test
    fun checkNow_updatesLastSocAndEta() = runTest {
        val storeWithTarget = FakeSettingsStore(
            initialBridge = config,
            initialMonitor = com.github.diarmaidlindsay.sigenergybattery.domain.model.MonitorConfig(
                5, 100.0, com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction.AT_OR_ABOVE,
            ),
        )
        val dto = SolarNowDto(
            batterySocPct = 50.0,
            batteryKw = 5.0,
            battery = com.github.diarmaidlindsay.sigenergybattery.data.api.BatteryDto(capacityKwh = 20.0),
        )
        val vm = MonitorViewModel(
            store = storeWithTarget,
            apiFactory = { FakeHermesApi(result = dto) },
        )
        vm.checkNow()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(50.0, state.currentSoc!!, 0.001)
        assertEquals(50.0, state.lastSoc!!, 0.001)
        assertNotNull(state.lastChecked)
        // 50 → 100 at 5kW/20kWh (25%/h) = 2h = 120m
        assertEquals(120L, state.etaMinutes)
    }

    @Test
    fun refreshAll_skippedWhenNotConnected() = runTest {
        val api = FakeHermesApi(result = SolarNowDto(batterySocPct = 60.0))
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { api },
        )
        vm.refreshAll()
        advanceUntilIdle()

        assertEquals(0, api.calls)
        assertEquals(0, api.historyCalls)
    }

    @Test
    fun refreshAll_refreshesSocAndHistoryWhenConnected() = runTest {
        val api = FakeHermesApi(
            result = SolarNowDto(batterySocPct = 60.0, batteryKw = 5.0),
        )
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { api },
        )
        vm.onHostChange("10.0.0.30")
        vm.onPortChange("8500")
        vm.onApiKeyChange("key123")
        vm.connect()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.connected)

        val callsBefore = api.calls
        vm.refreshAll()
        advanceUntilIdle()

        assertTrue(api.calls > callsBefore)
        assertTrue(api.historyCalls >= 1)
    }

    // --- Trigger action selection ---

    private fun viewModel(api: FakeHermesApi = FakeHermesApi()): MonitorViewModel =
        MonitorViewModel(
            store = store,
            apiFactory = { api },
            fcmTokenProvider = { null },
        )

    @Test
    fun triggerActions_defaultToNotifyOnly() = runTest {
        val vm = viewModel()
        assertEquals(setOf(TriggerAction.NOTIFY), vm.uiState.value.triggerActions)
    }

    @Test
    fun triggerAction_toggleOn_selectsIt() = runTest {
        val vm = viewModel()
        vm.onTriggerActionToggle(TriggerAction.MINER_ON, true)
        assertTrue(TriggerAction.MINER_ON in vm.uiState.value.triggerActions)
    }

    @Test
    fun triggerAction_toggleOff_deselectsIt() = runTest {
        val vm = viewModel()
        vm.onTriggerActionToggle(TriggerAction.MINER_ON, true)
        vm.onTriggerActionToggle(TriggerAction.MINER_ON, false)
        assertFalse(TriggerAction.MINER_ON in vm.uiState.value.triggerActions)
    }

    @Test
    fun triggerAction_checkingOff_removesOnAndPreset() = runTest {
        val vm = viewModel()
        vm.onTriggerActionToggle(TriggerAction.MINER_ON, true)
        vm.onTriggerActionToggle(TriggerAction.SET_POWER_PRESET, true)
        vm.onTriggerActionToggle(TriggerAction.MINER_OFF, true)

        val actions = vm.uiState.value.triggerActions
        assertTrue(TriggerAction.MINER_OFF in actions)
        assertFalse(TriggerAction.MINER_ON in actions)
        assertFalse(TriggerAction.SET_POWER_PRESET in actions)
        assertTrue(TriggerAction.NOTIFY in actions)
    }

    @Test
    fun triggerAction_checkingPreset_alone_isAllowed() = runTest {
        val vm = viewModel()
        vm.onTriggerActionToggle(TriggerAction.SET_POWER_PRESET, true)

        val actions = vm.uiState.value.triggerActions
        assertTrue(TriggerAction.SET_POWER_PRESET in actions)
        assertFalse(TriggerAction.MINER_ON in actions)
        assertFalse(TriggerAction.MINER_OFF in actions)
    }

    @Test
    fun beginMonitoring_presetOnly_sendsPresetWithoutOn() = runTest {
        val api = FakeHermesApi()
        val vm = viewModel(api)
        vm.onTriggerActionToggle(TriggerAction.SET_POWER_PRESET, true)
        vm.onMinerPresetChange(MinerPreset.LOW)
        vm.beginMonitoring()
        advanceUntilIdle()

        assertEquals(1, api.setTriggerCalls)
        assertEquals(listOf("NOTIFY", "SET_POWER_PRESET"), api.lastTrigger!!.actions)
        assertEquals("low", api.lastTrigger!!.minerPreset)
    }

    @Test
    fun triggerAction_checkingOn_whenOffSelected_switchesToOn() = runTest {
        val vm = viewModel()
        vm.onTriggerActionToggle(TriggerAction.MINER_OFF, true)
        assertTrue(TriggerAction.MINER_OFF in vm.uiState.value.triggerActions)

        vm.onTriggerActionToggle(TriggerAction.MINER_ON, true)

        val actions = vm.uiState.value.triggerActions
        assertTrue(TriggerAction.MINER_ON in actions)
        assertFalse(TriggerAction.MINER_OFF in actions)
    }

    @Test
    fun triggerAction_checkingOff_whenOnSelected_switchesToOff() = runTest {
        val vm = viewModel()
        vm.onTriggerActionToggle(TriggerAction.MINER_ON, true)
        assertTrue(TriggerAction.MINER_ON in vm.uiState.value.triggerActions)

        vm.onTriggerActionToggle(TriggerAction.MINER_OFF, true)

        val actions = vm.uiState.value.triggerActions
        assertTrue(TriggerAction.MINER_OFF in actions)
        assertFalse(TriggerAction.MINER_ON in actions)
    }

    @Test
    fun triggerAction_switchingOnThenOffThenOn_isAlwaysMutuallyExclusive() = runTest {
        val vm = viewModel()
        vm.onTriggerActionToggle(TriggerAction.MINER_OFF, true)
        vm.onTriggerActionToggle(TriggerAction.MINER_ON, true)
        vm.onTriggerActionToggle(TriggerAction.MINER_OFF, true)
        vm.onTriggerActionToggle(TriggerAction.MINER_ON, true)

        val actions = vm.uiState.value.triggerActions
        assertEquals(setOf(TriggerAction.NOTIFY, TriggerAction.MINER_ON), actions)
    }

    @Test
    fun beginMonitoring_sendsActionsAndPreset() = runTest {
        val api = FakeHermesApi()
        val vm = viewModel(api)
        vm.onTriggerActionToggle(TriggerAction.MINER_ON, true)
        vm.onTriggerActionToggle(TriggerAction.SET_POWER_PRESET, true)
        vm.onMinerPresetChange(MinerPreset.MAX)
        vm.beginMonitoring()
        advanceUntilIdle()

        assertEquals(1, api.setTriggerCalls)
        assertEquals(listOf("NOTIFY", "MINER_ON", "SET_POWER_PRESET"), api.lastTrigger!!.actions)
        assertEquals("max", api.lastTrigger!!.minerPreset)
        assertEquals(MinerPreset.MAX, store.savedMonitor?.minerPreset)
    }

    @Test
    fun beginMonitoring_offOnly_sendsNoPreset() = runTest {
        val api = FakeHermesApi()
        val vm = viewModel(api)
        vm.onTriggerActionToggle(TriggerAction.MINER_OFF, true)
        vm.beginMonitoring()
        advanceUntilIdle()

        assertEquals(1, api.setTriggerCalls)
        assertEquals(listOf("NOTIFY", "MINER_OFF"), api.lastTrigger!!.actions)
        assertNull(api.lastTrigger!!.minerPreset)
        assertNull(store.savedMonitor?.minerPreset)
    }

    @Test
    fun beginMonitoring_deselectAll_fallsBackToNotify() = runTest {
        val api = FakeHermesApi()
        val vm = viewModel(api)
        vm.onTriggerActionToggle(TriggerAction.NOTIFY, false)
        vm.beginMonitoring()
        advanceUntilIdle()

        assertEquals(1, api.setTriggerCalls)
        assertEquals(listOf("NOTIFY"), api.lastTrigger!!.actions)
    }

    @Test
    fun beginMonitoring_failure_setsMonitorError() = runTest {
        val api = FakeHermesApi(triggerError = IOException("timeout"))
        val vm = viewModel(api)
        vm.beginMonitoring()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.monitorError)
        assertFalse(vm.uiState.value.monitoring)
    }

    @Test
    fun connect_syncsFiredTriggerFromBridge() = runTest {
        val api = FakeHermesApi(
            result = SolarNowDto(batterySocPct = 97.8),
            triggerStatus = TriggerStatusDto(
                enabled = true,
                fired = true,
                firedSoc = 97.8,
                lastSoc = 97.8,
                lastCheckedAt = 1000.0,
            ),
        )
        val vm = viewModel(api)
        vm.onHostChange("100.105.141.68")
        vm.onPortChange("8500")
        vm.onApiKeyChange("key123")
        vm.connect()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.connected)
        assertTrue(vm.uiState.value.alertFired)
        assertFalse(vm.uiState.value.monitoring)
    }

    @Test
    fun disconnect_cancelsTriggerWhenSettingOn() = runTest {
        val api = FakeHermesApi()
        val vm = viewModel(api)
        vm.onHostChange("100.105.141.68")
        vm.onPortChange("8500")
        vm.onApiKeyChange("key123")
        vm.connect()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.connected)
        assertTrue(vm.uiState.value.cancelTriggerOnDisconnect)

        vm.disconnect()
        advanceUntilIdle()

        assertEquals(1, api.deleteTriggerCalls)
        assertFalse(vm.uiState.value.connected)
        assertFalse(vm.uiState.value.monitoring)
    }

    @Test
    fun disconnect_leavesTriggerRunningWhenSettingOff() = runTest {
        val api = FakeHermesApi()
        val storeOff = FakeSettingsStore(
            initialBridge = config,
            initialCancelTriggerOnDisconnect = false,
        )
        val vm = MonitorViewModel(
            store = storeOff,
            apiFactory = { api },
            fcmTokenProvider = { null },
        )
        vm.onHostChange("100.105.141.68")
        vm.onPortChange("8500")
        vm.onApiKeyChange("key123")
        vm.connect()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.connected)
        assertFalse(vm.uiState.value.cancelTriggerOnDisconnect)

        vm.disconnect()
        advanceUntilIdle()

        assertEquals(0, api.deleteTriggerCalls)
        assertFalse(vm.uiState.value.connected)
        assertFalse(vm.uiState.value.monitoring)
    }

    @Test
    fun onCancelTriggerOnDisconnectChange_persistsSetting() = runTest {
        val vm = viewModel()
        assertTrue(vm.uiState.value.cancelTriggerOnDisconnect)

        vm.onCancelTriggerOnDisconnectChange(false)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.cancelTriggerOnDisconnect)
        assertEquals(false, store.savedCancelOnDisconnect)

        vm.onCancelTriggerOnDisconnectChange(true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.cancelTriggerOnDisconnect)
        assertEquals(true, store.savedCancelOnDisconnect)
    }

    // --- Events (SOC chart dots) ---

    @Test
    fun loadEvents_populatesEvents() = runTest {
        val api = FakeHermesApi(
            events = com.github.diarmaidlindsay.sigenergybattery.data.api.EventsDto(
                events = listOf(
                    com.github.diarmaidlindsay.sigenergybattery.data.api.EventDto(
                        type = "trigger",
                        t = 2000.0,
                        soc = 20.0,
                        thresholdSoc = 20.0,
                        direction = "AT_OR_BELOW",
                    ),
                    com.github.diarmaidlindsay.sigenergybattery.data.api.EventDto(
                        type = "strategy",
                        t = 3000.0,
                        soc = 85.0,
                        reason = "condition",
                        stepName = "Ramp Up",
                    ),
                ),
            ),
        )
        val vm = viewModel(api)
        vm.loadEvents()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.eventsLoading)
        assertNull(state.eventsError)
        assertEquals(2, state.events.size)
        assertEquals(com.github.diarmaidlindsay.sigenergybattery.domain.model.EventType.TRIGGER, state.events[0].type)
        assertEquals(2000L, state.events[0].epochSeconds)
        assertEquals(com.github.diarmaidlindsay.sigenergybattery.domain.model.EventType.STRATEGY, state.events[1].type)
        assertEquals("Ramp Up", state.events[1].stepName)
    }

    @Test
    fun loadEvents_emptyResponse_leavesEmptyEvents() = runTest {
        val vm = viewModel(FakeHermesApi(events = com.github.diarmaidlindsay.sigenergybattery.data.api.EventsDto()))
        vm.loadEvents()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.eventsLoading)
        assertNull(state.eventsError)
        assertTrue(state.events.isEmpty())
    }

    @Test
    fun loadEvents_errorSetsErrorAndKeepsEventsEmpty() = runTest {
        val vm = viewModel(FakeHermesApi(eventsError = IOException("timeout")))
        vm.loadEvents()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.eventsLoading)
        assertEquals("Cannot reach bridge for events", state.eventsError)
        assertTrue(state.events.isEmpty())
    }

    @Test
    fun connect_loadsEventsAlongsideHistory() = runTest {
        val api = FakeHermesApi(
            result = SolarNowDto(batterySocPct = 60.0),
            events = com.github.diarmaidlindsay.sigenergybattery.data.api.EventsDto(
                events = listOf(
                    com.github.diarmaidlindsay.sigenergybattery.data.api.EventDto(type = "trigger", t = 1.0, soc = 20.0),
                ),
            ),
        )
        // Fresh store so auto-connect (hasConnectedBefore) doesn't add a second
        // connect round-trip to the api call counters.
        val freshStore = FakeSettingsStore(initialBridge = config)
        val vm = MonitorViewModel(
            store = freshStore,
            apiFactory = { api },
            fcmTokenProvider = { null },
        )
        vm.onHostChange("100.105.141.68")
        vm.onPortChange("8500")
        vm.onApiKeyChange("key123")
        vm.connect()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.connected)
        assertEquals(1, vm.uiState.value.events.size)
        assertTrue(api.eventsCalls >= 1)
    }

    @Test
    fun refreshAll_loadsEventsWhenConnected() = runTest {
        val api = FakeHermesApi(
            result = SolarNowDto(batterySocPct = 60.0, batteryKw = 5.0),
        )
        val vm = viewModel(api)
        vm.onHostChange("10.0.0.30")
        vm.onPortChange("8500")
        vm.onApiKeyChange("key123")
        vm.connect()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.connected)

        val eventsCallsBefore = api.eventsCalls
        vm.refreshAll()
        advanceUntilIdle()

        assertTrue(api.eventsCalls > eventsCallsBefore)
    }
}
