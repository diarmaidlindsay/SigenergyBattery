package com.github.diarmaidlindsay.hermesbattery.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SolarHistoryDtoParsingTest {

    private val json = ApiClientFactory.json

    @Test
    fun parsesHistoryPayload() {
        val dto = json.decodeFromString<SolarHistoryDto>(
            """
            {
              "source": "home_assistant",
              "entity_id": "sensor.sigen_plant_battery_state_of_charge",
              "interval_minutes": 5,
              "start": 1000,
              "end": 973000,
              "points": [
                {"t": 1000, "soc": 52.3},
                {"t": 1300, "soc": 51.6},
                {"t": 1600, "soc": null}
              ]
            }
            """.trimIndent()
        )
        assertEquals(5, dto.intervalMinutes)
        assertEquals(1000L, dto.start)
        assertEquals(3, dto.points.size)
        assertEquals(52.3, dto.points[0].soc!!, 0.001)
        assertEquals(1300L, dto.points[1].t)
        assertNull(dto.points[2].soc)
    }

    @Test
    fun parsesEmptyPoints() {
        val dto = json.decodeFromString<SolarHistoryDto>(
            """{"interval_minutes":5,"start":100,"end":100,"points":[]}"""
        )
        assertEquals(0, dto.points.size)
        assertEquals(5, dto.intervalMinutes)
    }
}
