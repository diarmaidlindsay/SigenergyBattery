package com.github.diarmaidlindsay.sigenergybattery.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyDtoParsingTest {

    private val json = ApiClientFactory.json

    @Test
    fun parsesStrategyStatusFromJson() {
        val dto = json.decodeFromString<StrategyStatusDto>(
            """
            {
              "enabled": true,
              "name": "Summer (Jun-Aug)",
              "interval_minutes": 5,
              "active_hours_start": "06:00",
              "active_hours_end": "22:00",
              "steps": [
                {
                  "name": "Idle",
                  "condition": {"soc_threshold": 70, "direction": "AT_OR_BELOW", "time_after": null},
                  "actions": ["MINER_OFF"]
                },
                {
                  "name": "Ramp Up",
                  "condition": {"soc_threshold": 80, "direction": "AT_OR_ABOVE"},
                  "actions": ["MINER_ON", "SET_POWER_PRESET"],
                  "miner_preset": "low"
                }
              ],
              "current_step": 1,
              "last_transition_at": 1785939827.0,
              "last_soc": 85.2,
              "last_error": null
            }
            """,
        )

        assertTrue(dto.enabled)
        assertEquals("Summer (Jun-Aug)", dto.name)
        assertEquals("06:00", dto.activeHoursStart)
        assertEquals(1, dto.currentStep)
        assertEquals(85.2, dto.lastSoc!!, 0.001)
        assertEquals(1785939827.0, dto.lastTransitionAt!!, 0.001)
        assertEquals(2, dto.steps.size)

        val mapped = dto.toStrategyStatus()
        assertEquals(2, mapped.steps.size)
        assertEquals("Idle", mapped.steps[0].name)
        assertTrue("MINER_OFF" in mapped.steps[0].actions.map { it.name })
        assertEquals("Ramp Up", mapped.steps[1].name)
        assertEquals(1785939827000L, mapped.lastTransitionAt)
    }

    @Test
    fun parsesDisabledStrategyWithNoSteps() {
        val dto = json.decodeFromString<StrategyStatusDto>("""{"enabled": false}""")
        assertFalse(dto.enabled)
        assertNull(dto.name)
        assertTrue(dto.steps.isEmpty())
        assertNull(dto.toStrategyStatus().lastTransitionAt)
    }

    @Test
    fun roundTripsStrategyConfig() {
        val config = StrategyConfigDto(
            name = "Autumn (Sep-Nov)",
            intervalMinutes = 10,
            activeHoursStart = "08:00",
            activeHoursEnd = "20:00",
            steps = listOf(
                StrategyStepDto(
                    name = "Winding Down",
                    condition = StrategyConditionDto(socThreshold = 80.0, direction = "AT_OR_BELOW", timeAfter = "14:00"),
                    actions = listOf("SET_POWER_PRESET"),
                    minerPreset = "low",
                ),
            ),
        )

        val encoded = json.encodeToString(StrategyConfigDto.serializer(), config)
        val decoded = json.decodeFromString<StrategyConfigDto>(encoded)

        assertEquals(config, decoded)
        val mapped = decoded.toStrategyConfig()
        assertEquals("08:00", mapped.activeHoursStart)
        assertEquals("14:00", mapped.steps.single().condition.timeAfter)
    }

    @Test
    fun parsesTemplatesMap() {
        val dto = json.decodeFromString<StrategyTemplatesDto>(
            """
            {
              "templates": {
                "summer": {"name": "Summer (Jun-Aug)", "interval_minutes": 5, "active_hours_start": "06:00", "active_hours_end": "22:00", "steps": []},
                "spring": {"name": "Spring (Mar-May)", "interval_minutes": 5, "active_hours_start": "07:00", "active_hours_end": "21:00", "steps": []}
              }
            }
            """,
        )
        assertEquals(2, dto.templates.size)
        assertEquals("Summer (Jun-Aug)", dto.templates["summer"]?.name)
        assertEquals("Spring (Mar-May)", dto.templates["spring"]?.name)
    }
}
