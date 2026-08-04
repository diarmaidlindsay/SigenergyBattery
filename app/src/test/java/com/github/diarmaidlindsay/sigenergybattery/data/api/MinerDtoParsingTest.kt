package com.github.diarmaidlindsay.sigenergybattery.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MinerDtoParsingTest {

    private val json = ApiClientFactory.json

    @Test
    fun parsesPowerTargetFromStatus() {
        val dto = json.decodeFromString<MinerStatusDto>(
            """{
                "switches": {"switch.antminer1": "on", "switch.antminer2": "on"},
                "miner": {"data": {}},
                "power_target_w": 2000
            }"""
        )
        assertEquals(2000, dto.powerTargetW)
        assertEquals(listOf("on", "on"), dto.switchStates)
    }

    @Test
    fun parsesSwitchStatesWhenMixed() {
        val dto = json.decodeFromString<MinerStatusDto>(
            """{"switches":{"switch.antminer1":"on","switch.antminer2":"off"}}"""
        )
        assertEquals(listOf("on", "off"), dto.switchStates)
    }

    @Test
    fun powerTargetNullWhenMinerUnreachable() {
        val dto = json.decodeFromString<MinerStatusDto>(
            """{"switches": {},"miner": {"error": "no route"}, "power_target_w": null}"""
        )
        assertNull(dto.powerTargetW)
    }

    @Test
    fun powerTargetNullWhenFieldMissing() {
        val dto = json.decodeFromString<MinerStatusDto>("""{"switches": {}}""")
        assertNull(dto.powerTargetW)
    }

    @Test
    fun parsesActionResponse() {
        val dto = json.decodeFromString<MinerActionResponse>(
            """{"status": "ok", "switches": ["switch.antminer1", "switch.antminer2"]}"""
        )
        assertEquals("ok", dto.status)
    }
}
