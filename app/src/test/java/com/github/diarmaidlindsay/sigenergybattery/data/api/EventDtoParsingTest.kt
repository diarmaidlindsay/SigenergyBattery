package com.github.diarmaidlindsay.sigenergybattery.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDtoParsingTest {

    private val json = ApiClientFactory.json

    @Test
    fun parsesTriggerEvent() {
        val dto = json.decodeFromString<EventDto>(
            """
            {
              "type": "trigger",
              "t": 1785942239.0,
              "soc": 20.0,
              "threshold_soc": 20.0,
              "direction": "AT_OR_BELOW",
              "actions": ["NOTIFY", "MINER_ON"],
              "miner_preset": "low",
              "error": null
            }
            """,
        )

        assertEquals("trigger", dto.type)
        assertEquals(1785942239.0, dto.t, 0.001)
        assertEquals(20.0, dto.soc!!, 0.001)
        assertEquals(20.0, dto.thresholdSoc!!, 0.001)
        assertEquals("AT_OR_BELOW", dto.direction)
        assertEquals(listOf("NOTIFY", "MINER_ON"), dto.actions)
        assertEquals("low", dto.minerPreset)
        assertNull(dto.error)
        assertNull(dto.reason)
        assertNull(dto.stepName)
    }

    @Test
    fun parsesStrategyEvent() {
        val dto = json.decodeFromString<EventDto>(
            """
            {
              "type": "strategy",
              "t": 1785945000.0,
              "soc": 85.0,
              "reason": "condition",
              "from_step": 0,
              "to_step": 1,
              "step_name": "Ramp Up",
              "strategy_name": "Summer (Jun-Aug)",
              "actions": ["MINER_ON", "SET_POWER_PRESET"],
              "miner_preset": "low",
              "error": "action failed"
            }
            """,
        )

        assertEquals("strategy", dto.type)
        assertEquals(1785945000.0, dto.t, 0.001)
        assertEquals(85.0, dto.soc!!, 0.001)
        assertEquals("condition", dto.reason)
        assertEquals(0, dto.fromStep)
        assertEquals(1, dto.toStep)
        assertEquals("Ramp Up", dto.stepName)
        assertEquals("Summer (Jun-Aug)", dto.strategyName)
        assertEquals("action failed", dto.error)
        assertNull(dto.thresholdSoc)
        assertNull(dto.direction)
    }

    @Test
    fun parsesEventsWrapperNewestFirst() {
        val dto = json.decodeFromString<EventsDto>(
            """
            {
              "events": [
                {"type": "strategy", "t": 3.0, "soc": 90.0, "step_name": "Full Power"},
                {"type": "trigger", "t": 2.0, "soc": 20.0, "threshold_soc": 20.0, "direction": "AT_OR_BELOW"},
                {"type": "strategy", "t": 1.0, "soc": 80.0, "step_name": "Ramp Up"}
              ]
            }
            """,
        )

        assertEquals(3, dto.events.size)
        assertEquals(listOf(3.0, 2.0, 1.0), dto.events.map { it.t })
        assertEquals("strategy", dto.events[0].type)
        assertEquals("trigger", dto.events[1].type)
        assertEquals("Ramp Up", dto.events[2].stepName)
    }

    @Test
    fun emptyEventsWrapperDefaultsToEmptyList() {
        val dto = json.decodeFromString<EventsDto>("""{"events": []}""")
        assertTrue(dto.events.isEmpty())
    }
}
