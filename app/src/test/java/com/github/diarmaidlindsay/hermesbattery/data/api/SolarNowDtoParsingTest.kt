package com.github.diarmaidlindsay.hermesbattery.data.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SolarNowDtoParsingTest {

    private val json = ApiClientFactory.json

    @Test
    fun parsesTopLevelSoc() {
        val dto = json.decodeFromString<SolarNowDto>(
            """{"battery_soc_pct":78.5,"pv_kw":5.7,"battery":{"soc_pct":78.5}}"""
        )
        assertEquals(78.5, dto.toSnapshot().socPct!!, 0.001)
    }

    @Test
    fun fallsBackToNestedSoc() {
        val dto = json.decodeFromString<SolarNowDto>(
            """{"pv_kw":5.7,"battery":{"soc_pct":42.0}}"""
        )
        assertEquals(42.0, dto.toSnapshot().socPct!!, 0.001)
    }

    @Test
    fun ignoresUnknownFields() {
        val dto = json.decodeFromString<SolarNowDto>(
            """{"some_future_field":[1,2,3],"battery_soc_pct":100.0}"""
        )
        assertEquals(100.0, dto.toSnapshot().socPct!!, 0.001)
    }

    @Test
    fun nullWhenNoSocPresent() {
        val dto = json.decodeFromString<SolarNowDto>("""{"pv_kw":0.0}""")
        assertNull(dto.toSnapshot().socPct)
    }
}
