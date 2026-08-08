package com.github.diarmaidlindsay.sigenergybattery.data.api

import com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction
import com.github.diarmaidlindsay.sigenergybattery.domain.model.EventType
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MinerPreset
import com.github.diarmaidlindsay.sigenergybattery.domain.model.TriggerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDtoMappingTest {

    @Test
    fun mapsTriggerEvent() {
        val event = EventDto(
            type = "trigger",
            t = 1785942239.5,
            soc = 20.0,
            thresholdSoc = 20.0,
            direction = "AT_OR_BELOW",
            actions = listOf("NOTIFY", "MINER_ON"),
            minerPreset = "low",
            error = null,
        ).toChartEvent()

        assertEquals(EventType.TRIGGER, event.type)
        assertEquals(1785942239L, event.epochSeconds)
        assertEquals(20.0, event.soc!!, 0.001)
        assertEquals(20.0, event.thresholdSoc!!, 0.001)
        assertEquals(Direction.AT_OR_BELOW, event.direction)
        assertEquals(setOf(TriggerAction.NOTIFY, TriggerAction.MINER_ON), event.actions)
        assertEquals(MinerPreset.LOW, event.minerPreset)
        assertNull(event.error)
        assertNull(event.stepName)
        assertNull(event.strategyName)
    }

    @Test
    fun mapsStrategyEvent() {
        val event = EventDto(
            type = "strategy",
            t = 1785945000.0,
            soc = 85.0,
            reason = "condition",
            fromStep = 0,
            toStep = 1,
            stepName = "Ramp Up",
            strategyName = "Summer (Jun-Aug)",
            actions = listOf("MINER_ON", "SET_POWER_PRESET"),
            minerPreset = "efficient",
            error = "action failed",
        ).toChartEvent()

        assertEquals(EventType.STRATEGY, event.type)
        assertEquals(1785945000L, event.epochSeconds)
        assertEquals(85.0, event.soc!!, 0.001)
        assertEquals("condition", event.reason)
        assertEquals(0, event.fromStep)
        assertEquals(1, event.toStep)
        assertEquals("Ramp Up", event.stepName)
        assertEquals("Summer (Jun-Aug)", event.strategyName)
        assertEquals(setOf(TriggerAction.MINER_ON, TriggerAction.SET_POWER_PRESET), event.actions)
        assertEquals(MinerPreset.EFFICIENT, event.minerPreset)
        assertEquals("action failed", event.error)
    }

    @Test
    fun mapsNullSocAndMissingOptionalFields() {
        val event = EventDto(type = "trigger", t = 100.0).toChartEvent()

        assertEquals(EventType.TRIGGER, event.type)
        assertEquals(100L, event.epochSeconds)
        assertNull(event.soc)
        assertNull(event.thresholdSoc)
        assertNull(event.direction)
        assertNull(event.minerPreset)
        assertNull(event.error)
        assertTrue(event.actions.isEmpty())
    }

    @Test
    fun mapsUnknownTypeToTrigger() {
        val event = EventDto(type = "bogus", t = 1.0).toChartEvent()
        assertEquals(EventType.TRIGGER, event.type)
    }

    @Test
    fun mapsUnknownActionsAndPresetToNull() {
        val event = EventDto(
            type = "trigger",
            t = 1.0,
            actions = listOf("BOGUS_ACTION"),
            minerPreset = "bogus",
        ).toChartEvent()

        assertTrue(event.actions.isEmpty())
        assertNull(event.minerPreset)
    }
}
