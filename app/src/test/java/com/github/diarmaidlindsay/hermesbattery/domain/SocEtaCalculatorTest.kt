package com.github.diarmaidlindsay.hermesbattery.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocEtaCalculatorTest {

    @Test
    fun chargingToHigherTarget() {
        // 5kW into a 20kWh battery = 25%/h; 50 → 100 needs 2h = 120m
        assertEquals(120L, SocEtaCalculator.minutesToTarget(50.0, 100.0, 5.0, 20.0))
    }

    @Test
    fun dischargingToLowerTarget() {
        // -5kW into a 20kWh battery = -25%/h; 50 → 20 needs 1.2h = 72m
        assertEquals(72L, SocEtaCalculator.minutesToTarget(50.0, 20.0, -5.0, 20.0))
    }

    @Test
    fun movingAwayFromTarget_returnsNull() {
        // Charging but target is below current.
        assertNull(SocEtaCalculator.minutesToTarget(50.0, 20.0, 5.0, 20.0))
        // Discharging but target is above current.
        assertNull(SocEtaCalculator.minutesToTarget(50.0, 100.0, -5.0, 20.0))
    }

    @Test
    fun zeroRate_returnsNull() {
        assertNull(SocEtaCalculator.minutesToTarget(50.0, 100.0, 0.0, 20.0))
    }

    @Test
    fun invalidCapacity_returnsNull() {
        assertNull(SocEtaCalculator.minutesToTarget(50.0, 100.0, 5.0, 0.0))
        assertNull(SocEtaCalculator.minutesToTarget(50.0, 100.0, 5.0, -1.0))
    }

    @Test
    fun alreadyAtTarget_returnsZero() {
        assertEquals(0L, SocEtaCalculator.minutesToTarget(50.0, 50.0, 5.0, 20.0))
    }

    @Test
    fun fractionalTimeRoundsDown() {
        // 3kW into 20kWh = 15%/h; 10 → 100 needs 6h = 360m
        assertEquals(360L, SocEtaCalculator.minutesToTarget(10.0, 100.0, 3.0, 20.0))
    }

    @Test
    fun formatMinutes() {
        assertEquals("<1m", SocEtaCalculator.formatMinutes(0))
        assertEquals("5m", SocEtaCalculator.formatMinutes(5))
        assertEquals("59m", SocEtaCalculator.formatMinutes(59))
        assertEquals("2h 10m", SocEtaCalculator.formatMinutes(130))
    }
}
