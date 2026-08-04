package com.github.diarmaidlindsay.sigenergybattery.ui

import com.github.diarmaidlindsay.sigenergybattery.MainDispatcherRule
import com.github.diarmaidlindsay.sigenergybattery.data.api.SolarNowDto
import com.github.diarmaidlindsay.sigenergybattery.domain.model.BridgeConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MonitorConfig
import com.github.diarmaidlindsay.sigenergybattery.fakes.FakeHermesApi
import com.github.diarmaidlindsay.sigenergybattery.fakes.FakeSettingsStore
import com.github.diarmaidlindsay.sigenergybattery.fakes.HangingHermesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class MonitorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val config = BridgeConfig("100.105.141.68", "8500", "key123")
    private val store = FakeSettingsStore(initialBridge = config)

    @Test
    fun connect_success_setsConnectedAndSavesConfig() = runTest {
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { FakeHermesApi(result = SolarNowDto(batterySocPct = 77.5)) },
            startMonitoring = {},
            stopMonitoring = {},
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
            startMonitoring = {},
            stopMonitoring = {},
        )
        vm.onHostChange("100.105.141.68")
        vm.onPortChange("8500")
        vm.onApiKeyChange("wrong")
        vm.connect()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.connected)
        assertEquals("Unauthorized — check the API key", vm.uiState.value.connectionError)
    }

    @Test
    fun connect_unreachableHost_showsError() = runTest {
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { FakeHermesApi(error = IOException("timeout")) },
            startMonitoring = {},
            stopMonitoring = {},
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
            startMonitoring = {},
            stopMonitoring = {},
        )
        vm.onHostChange("")
        vm.onPortChange("")
        vm.connect()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.connected)
        assertEquals("Enter the bridge IP and port", vm.uiState.value.connectionError)
    }

    @Test
    fun beginMonitoring_savesConfigAndStartsService() = runTest {
        var startedConfig: MonitorConfig? = null
        var stopped = false
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { FakeHermesApi() },
            startMonitoring = { startedConfig = it },
            stopMonitoring = { stopped = true },
        )
        vm.onIntervalChange(15)
        vm.onThresholdChange(30f)
        vm.onDirectionChange(Direction.AT_OR_ABOVE)
        vm.beginMonitoring()
        advanceUntilIdle()

        assertEquals(MonitorConfig(15, 30.0, Direction.AT_OR_ABOVE), startedConfig)
        assertEquals(startedConfig, store.savedMonitor)

        vm.cancelMonitoring()
        assertTrue(stopped)
    }

    @Test
    fun connect_requiresApiKey() = runTest {
        val vm = MonitorViewModel(
            store = store,
            apiFactory = { FakeHermesApi() },
            startMonitoring = {},
            stopMonitoring = {},
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
            startMonitoring = {},
            stopMonitoring = {},
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
            startMonitoring = {},
            stopMonitoring = {},
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
            startMonitoring = {},
            stopMonitoring = {},
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
            startMonitoring = {},
            stopMonitoring = {},
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
            startMonitoring = {},
            stopMonitoring = {},
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
            startMonitoring = {},
            stopMonitoring = {},
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
            startMonitoring = {},
            stopMonitoring = {},
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
            startMonitoring = {},
            stopMonitoring = {},
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
            startMonitoring = {},
            stopMonitoring = {},
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
}
