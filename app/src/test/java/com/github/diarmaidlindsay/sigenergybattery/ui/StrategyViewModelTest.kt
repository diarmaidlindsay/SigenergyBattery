package com.github.diarmaidlindsay.sigenergybattery.ui

import com.github.diarmaidlindsay.sigenergybattery.MainDispatcherRule
import com.github.diarmaidlindsay.sigenergybattery.data.api.SolarNowDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.StrategyConditionDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.StrategyConfigDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.StrategyStatusDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.StrategyStepDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.StrategyTemplatesDto
import com.github.diarmaidlindsay.sigenergybattery.data.api.toStrategyConfig
import com.github.diarmaidlindsay.sigenergybattery.data.api.toStrategyStep
import com.github.diarmaidlindsay.sigenergybattery.data.api.toStrategyStepDto
import com.github.diarmaidlindsay.sigenergybattery.domain.model.BridgeConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MinerPreset
import com.github.diarmaidlindsay.sigenergybattery.domain.model.Season
import com.github.diarmaidlindsay.sigenergybattery.domain.model.StrategyConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.StrategyCondition
import com.github.diarmaidlindsay.sigenergybattery.domain.model.StrategyStep
import com.github.diarmaidlindsay.sigenergybattery.domain.model.TriggerAction
import com.github.diarmaidlindsay.sigenergybattery.fakes.FakeHermesApi
import com.github.diarmaidlindsay.sigenergybattery.fakes.FakeSettingsStore
import com.github.diarmaidlindsay.sigenergybattery.service.PollingState
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Builds the 4-step summer strategy (matching the bridge template). */
fun summerConfigDto(): StrategyConfigDto = StrategyConfigDto(
    name = "Summer (Jun-Aug)",
    intervalMinutes = 5,
    activeHoursStart = "06:00",
    activeHoursEnd = "22:00",
    steps = listOf(
        StrategyStepDto(
            name = "Idle",
            condition = StrategyConditionDto(socThreshold = 70.0, direction = "AT_OR_BELOW"),
            actions = listOf("MINER_OFF"),
        ),
        StrategyStepDto(
            name = "Ramp Up",
            condition = StrategyConditionDto(socThreshold = 80.0, direction = "AT_OR_ABOVE"),
            actions = listOf("MINER_ON", "SET_POWER_PRESET"),
            minerPreset = "low",
        ),
        StrategyStepDto(
            name = "Full Power",
            condition = StrategyConditionDto(socThreshold = 90.0, direction = "AT_OR_ABOVE"),
            actions = listOf("SET_POWER_PRESET"),
            minerPreset = "max",
        ),
        StrategyStepDto(
            name = "Winding Down",
            condition = StrategyConditionDto(
                socThreshold = 80.0,
                direction = "AT_OR_BELOW",
                timeAfter = "16:00",
            ),
            actions = listOf("SET_POWER_PRESET"),
            minerPreset = "low",
        ),
    ),
)

private fun summerConfig(): StrategyConfig = summerConfigDto().toStrategyConfig()

class StrategyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun resetPollingState() {
        PollingState.reset()
    }

    private val config = BridgeConfig("100.105.141.68", "8500", "key123")
    private val store = FakeSettingsStore(initialBridge = config)

    private fun connect(vm: MonitorViewModel, scope: kotlinx.coroutines.test.TestScope) {
        vm.onHostChange("100.105.141.68")
        vm.onPortChange("8500")
        vm.onApiKeyChange("key123")
        vm.connect()
        scope.advanceUntilIdle()
    }

    @Test
    fun loadStrategyTemplates_populatesTemplatesAndPrimesDraft() = runTest {
        val api = FakeHermesApi(
            result = SolarNowDto(batterySocPct = 60.0),
            strategyTemplates = StrategyTemplatesDto(templates = mapOf("summer" to summerConfigDto())),
        )
        val vm = MonitorViewModel(store = store, apiFactory = { api }, fcmTokenProvider = { null })
        connect(vm, this)

        assertEquals(1, api.templatesCalls)
        assertEquals(1, vm.uiState.value.strategyTemplates.size)
        assertNull(vm.uiState.value.draftStrategy)
    }

    @Test
    fun selectTemplate_setsDraft() = runTest {
        val vm = MonitorViewModel(store = store, apiFactory = { FakeHermesApi() }, fcmTokenProvider = { null })
        vm.onSelectStrategyTemplate(Season.SUMMER, summerConfig())

        assertEquals(Season.SUMMER, vm.uiState.value.draftSeason)
        assertEquals(4, vm.uiState.value.draftStrategy?.steps?.size)
        assertEquals("Summer (Jun-Aug)", vm.uiState.value.draftStrategy?.name)
    }

    @Test
    fun updateDraft_persistsEdits() = runTest {
        val vm = MonitorViewModel(store = store, apiFactory = { FakeHermesApi() }, fcmTokenProvider = { null })
        val edited = summerConfig().copy(activeHoursStart = "07:00")
        vm.updateDraftStrategy(edited)

        assertEquals("07:00", vm.uiState.value.draftStrategy?.activeHoursStart)
    }

    @Test
    fun startStrategy_postsDtoAndSaves() = runTest {
        val api = FakeHermesApi(
            strategyStatus = StrategyStatusDto(
                enabled = true,
                name = "Summer (Jun-Aug)",
                currentStep = 1,
                steps = summerConfigDto().steps,
            ),
        )
        val vm = MonitorViewModel(store = store, apiFactory = { api }, fcmTokenProvider = { null })
        vm.onSelectStrategyTemplate(Season.SUMMER, summerConfig())
        vm.startStrategy()
        advanceUntilIdle()

        assertEquals(1, api.setStrategyCalls)
        assertEquals("Summer (Jun-Aug)", api.lastStrategy?.name)
        assertEquals(4, api.lastStrategy?.steps?.size)
        assertEquals("max", api.lastStrategy?.steps?.get(2)?.minerPreset)
        assertEquals(store.savedStrategy?.name, "Summer (Jun-Aug)")
        assertTrue(vm.uiState.value.strategyEnabled)
        assertEquals(1, vm.uiState.value.strategyCurrentStep)
    }

    @Test
    fun startStrategy_withoutDraft_doesNothing() = runTest {
        val api = FakeHermesApi()
        val vm = MonitorViewModel(store = store, apiFactory = { api }, fcmTokenProvider = { null })
        vm.startStrategy()
        advanceUntilIdle()

        assertEquals(0, api.setStrategyCalls)
    }

    @Test
    fun stopStrategy_callsDeleteAndClearsState() = runTest {
        val api = FakeHermesApi(
            strategyStatus = StrategyStatusDto(enabled = true, name = "Summer (Jun-Aug)"),
        )
        val vm = MonitorViewModel(store = store, apiFactory = { api }, fcmTokenProvider = { null })
        vm.onSelectStrategyTemplate(Season.SUMMER, summerConfig())
        vm.startStrategy()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.strategyEnabled)

        vm.stopStrategy()
        advanceUntilIdle()

        assertEquals(1, api.deleteStrategyCalls)
        assertFalse(vm.uiState.value.strategyEnabled)
        assertNull(vm.uiState.value.strategyName)
    }

    @Test
    fun syncStrategyStatus_reflectsRunningStrategy() = runTest {
        val api = FakeHermesApi(
            result = SolarNowDto(batterySocPct = 60.0),
            strategyStatus = StrategyStatusDto(
                enabled = true,
                name = "Autumn (Sep-Nov)",
                currentStep = 2,
                lastSoc = 97.0,
                lastTransitionAt = 1000.0,
                steps = summerConfigDto().steps,
            ),
        )
        val vm = MonitorViewModel(store = store, apiFactory = { api }, fcmTokenProvider = { null })
        connect(vm, this)

        assertTrue(vm.uiState.value.strategyEnabled)
        assertEquals("Autumn (Sep-Nov)", vm.uiState.value.strategyName)
        assertEquals(2, vm.uiState.value.strategyCurrentStep)
        assertEquals(97.0, vm.uiState.value.strategyLastSoc!!, 0.001)
        assertEquals(1_000_000L, vm.uiState.value.strategyLastTransitionAt)
    }

    @Test
    fun startStrategy_whenTriggerActive_requiresConfirmation() = runTest {
        val api = FakeHermesApi()
        val vm = MonitorViewModel(store = store, apiFactory = { api }, fcmTokenProvider = { null })
        vm.onSelectStrategyTemplate(Season.SUMMER, summerConfig())
        // Simulate an active one-shot trigger.
        PollingState.active.value = true
        advanceUntilIdle()

        vm.onStartStrategyClick()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.confirmStartStrategyOverridesTrigger)
        assertEquals(0, api.setStrategyCalls)

        vm.confirmStartStrategyOverridesTrigger()
        advanceUntilIdle()
        assertEquals(1, api.setStrategyCalls)
        assertFalse(vm.uiState.value.monitoring)
    }

    @Test
    fun startStrategy_whenNoTriggerActive_startsImmediately() = runTest {
        val api = FakeHermesApi(
            strategyStatus = StrategyStatusDto(enabled = true),
        )
        val vm = MonitorViewModel(store = store, apiFactory = { api }, fcmTokenProvider = { null })
        vm.onSelectStrategyTemplate(Season.SUMMER, summerConfig())

        vm.onStartStrategyClick()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.confirmStartStrategyOverridesTrigger)
        assertEquals(1, api.setStrategyCalls)
    }

    @Test
    fun startMonitoring_whenStrategyActive_requiresConfirmation() = runTest {
        val api = FakeHermesApi()
        val vm = MonitorViewModel(store = store, apiFactory = { api }, fcmTokenProvider = { null })
        // Simulate an active strategy.
        PollingState.strategyActive.value = true
        advanceUntilIdle()

        vm.onStartMonitoringClick()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.confirmStartTriggerOverridesStrategy)
        assertEquals(0, api.setTriggerCalls)

        vm.confirmStartTriggerOverridesStrategy()
        advanceUntilIdle()
        assertEquals(1, api.setTriggerCalls)
        assertFalse(vm.uiState.value.strategyEnabled)
    }

    @Test
    fun startMonitoring_whenNoStrategy_startsImmediately() = runTest {
        val api = FakeHermesApi()
        val vm = MonitorViewModel(store = store, apiFactory = { api }, fcmTokenProvider = { null })

        vm.onStartMonitoringClick()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.confirmStartTriggerOverridesStrategy)
        assertEquals(1, api.setTriggerCalls)
    }

    @Test
    fun dismissConfirmations_doNotStartAnything() = runTest {
        val api = FakeHermesApi()
        val vm = MonitorViewModel(store = store, apiFactory = { api }, fcmTokenProvider = { null })
        vm.onSelectStrategyTemplate(Season.SUMMER, summerConfig())
        PollingState.active.value = true
        advanceUntilIdle()

        vm.onStartStrategyClick()
        advanceUntilIdle()
        vm.dismissStartStrategyOverridesTrigger()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.confirmStartStrategyOverridesTrigger)
        assertEquals(0, api.setStrategyCalls)
    }

    @Test
    fun mapping_roundTripsSteps() = runTest {
        val step = StrategyStep(
            name = "Full Power",
            condition = StrategyCondition(socThreshold = 90.0, direction = Direction.AT_OR_ABOVE),
            actions = setOf(TriggerAction.SET_POWER_PRESET),
            minerPreset = MinerPreset.MAX,
        )
        val dto = step.toStrategyStepDto()
        assertEquals("90.0".toDouble(), dto.condition.socThreshold, 0.001)
        assertEquals("AT_OR_ABOVE", dto.condition.direction)
        assertEquals("max", dto.minerPreset)

        val back = dto.toStrategyStep()
        assertEquals(TriggerAction.SET_POWER_PRESET, back.actions.single())
        assertEquals(MinerPreset.MAX, back.minerPreset)
        assertEquals(Direction.AT_OR_ABOVE, back.condition.direction)
    }
}
