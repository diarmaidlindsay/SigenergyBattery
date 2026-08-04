package com.github.diarmaidlindsay.sigenergybattery.domain

import com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryMonitorTest {

    @Test
    fun atOrBelow_firesBelowThreshold() {
        assertTrue(BatteryMonitor.shouldNotify(19.9, 20.0, Direction.AT_OR_BELOW))
    }

    @Test
    fun atOrBelow_firesExactlyAtThreshold() {
        assertTrue(BatteryMonitor.shouldNotify(20.0, 20.0, Direction.AT_OR_BELOW))
    }

    @Test
    fun atOrBelow_doesNotFireAboveThreshold() {
        assertFalse(BatteryMonitor.shouldNotify(20.1, 20.0, Direction.AT_OR_BELOW))
    }

    @Test
    fun atOrAbove_firesAboveThreshold() {
        assertTrue(BatteryMonitor.shouldNotify(90.5, 90.0, Direction.AT_OR_ABOVE))
    }

    @Test
    fun atOrAbove_firesExactlyAtThreshold() {
        assertTrue(BatteryMonitor.shouldNotify(90.0, 90.0, Direction.AT_OR_ABOVE))
    }

    @Test
    fun atOrAbove_doesNotFireBelowThreshold() {
        assertFalse(BatteryMonitor.shouldNotify(89.9, 90.0, Direction.AT_OR_ABOVE))
    }

    @Test
    fun nullSocNeverFires() {
        assertFalse(BatteryMonitor.shouldNotify(null, 20.0, Direction.AT_OR_BELOW))
        assertFalse(BatteryMonitor.shouldNotify(null, 90.0, Direction.AT_OR_ABOVE))
    }

    @Test
    fun extremeThresholds() {
        // Below 0% can never be reached with a valid reading.
        assertTrue(BatteryMonitor.shouldNotify(0.0, 0.0, Direction.AT_OR_BELOW))
        assertTrue(BatteryMonitor.shouldNotify(100.0, 100.0, Direction.AT_OR_ABOVE))
    }
}
