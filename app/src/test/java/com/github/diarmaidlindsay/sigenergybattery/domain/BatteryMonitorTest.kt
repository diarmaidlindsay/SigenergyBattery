package com.github.diarmaidlindsay.sigenergybattery.domain

import com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction
import com.github.diarmaidlindsay.sigenergybattery.domain.model.TriggerAction
import org.junit.Assert.assertEquals
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

    // --- Trigger action validation ---

    private val notify = TriggerAction.NOTIFY
    private val on = TriggerAction.MINER_ON
    private val off = TriggerAction.MINER_OFF
    private val preset = TriggerAction.SET_POWER_PRESET

    @Test
    fun notifyAlone_isValid() {
        assertTrue(BatteryMonitor.isValidActions(setOf(notify)))
    }

    @Test
    fun onWithPresetAndNotify_isValid() {
        assertTrue(BatteryMonitor.isValidActions(setOf(on, preset, notify)))
    }

    @Test
    fun offWithNotify_isValid() {
        assertTrue(BatteryMonitor.isValidActions(setOf(off, notify)))
    }

    @Test
    fun onAndOffTogether_isInvalid() {
        assertFalse(BatteryMonitor.isValidActions(setOf(on, off, notify)))
    }

    @Test
    fun offWithPreset_isInvalid() {
        assertFalse(BatteryMonitor.isValidActions(setOf(off, preset, notify)))
    }

    @Test
    fun presetAlone_isValid() {
        assertTrue(BatteryMonitor.isValidActions(setOf(preset, notify)))
    }

    // --- normalizeActions (UI exclusion enforcement) ---

    @Test
    fun checkingOff_removesOnAndPreset() {
        val result = BatteryMonitor.normalizeActions(setOf(on, preset, notify, off))
        assertEquals(setOf(off, notify), result)
    }

    @Test
    fun checkingPreset_alone_isAllowed() {
        val result = BatteryMonitor.normalizeActions(setOf(notify, preset))
        assertEquals(setOf(notify, preset), result)
    }

    @Test
    fun uncheckingOff_keepsOnAndPreset() {
        val result = BatteryMonitor.normalizeActions(setOf(notify))
        assertEquals(setOf(notify), result)
    }

    @Test
    fun normalizeActions_leavesEmptySetEmpty() {
        assertTrue(BatteryMonitor.normalizeActions(emptySet()).isEmpty())
    }

    @Test
    fun checkingOn_whenOffSelected_switchesToOn() {
        val result = BatteryMonitor.normalizeActions(setOf(notify, off, on), justSelected = on)
        assertEquals(setOf(notify, on), result)
    }

    @Test
    fun checkingOff_whenOnSelected_switchesToOff() {
        val result = BatteryMonitor.normalizeActions(setOf(notify, on, off), justSelected = off)
        assertEquals(setOf(notify, off), result)
    }

    @Test
    fun checkingOn_whenOffAndPresetSelected_switchesToOnAndPreset() {
        val result = BatteryMonitor.normalizeActions(
            setOf(notify, off, preset, on),
            justSelected = on,
        )
        assertEquals(setOf(notify, on, preset), result)
    }

    @Test
    fun checkingPreset_whenOffSelected_switchesToPresetOnly() {
        val result = BatteryMonitor.normalizeActions(
            setOf(notify, off, preset),
            justSelected = preset,
        )
        assertEquals(setOf(notify, preset), result)
    }

    @Test
    fun uncheckingOn_fromOnAndPreset_leavesPresetOnly() {
        // Preset may stand alone, so unchecking ON leaves the preset selected.
        val result = BatteryMonitor.normalizeActions(setOf(notify, preset), justSelected = null)
        assertEquals(setOf(notify, preset), result)
    }

    // --- shouldSetPreset ---

    @Test
    fun preset_skippedWhenCurrentTargetMatches() {
        assertFalse(BatteryMonitor.shouldSetPreset(2000, 2000))
    }

    @Test
    fun preset_appliedWhenCurrentTargetDiffers() {
        assertTrue(BatteryMonitor.shouldSetPreset(2760, 2000))
    }

    @Test
    fun preset_appliedWhenCurrentTargetUnknown() {
        assertTrue(BatteryMonitor.shouldSetPreset(null, 2000))
    }

    // --- shouldToggleMiner (idempotency) ---

    @Test
    fun toggleOn_skippedWhenAllSwitchesAlreadyOn() {
        assertFalse(BatteryMonitor.shouldToggleMiner(listOf("on", "on"), "on"))
    }

    @Test
    fun toggleOn_proceedsWhenAnySwitchIsOff() {
        assertTrue(BatteryMonitor.shouldToggleMiner(listOf("on", "off"), "on"))
    }

    @Test
    fun toggleOff_skippedWhenAllSwitchesAlreadyOff() {
        assertFalse(BatteryMonitor.shouldToggleMiner(listOf("off", "off"), "off"))
    }

    @Test
    fun toggleOff_proceedsWhenAnySwitchIsOn() {
        assertTrue(BatteryMonitor.shouldToggleMiner(listOf("off", "on"), "off"))
    }

    @Test
    fun toggle_proceedsWhenReadFailsOrEmpty() {
        assertTrue(BatteryMonitor.shouldToggleMiner(null, "on"))
        assertTrue(BatteryMonitor.shouldToggleMiner(emptyList(), "off"))
    }

    @Test
    fun toggle_proceedsWhenSwitchStateUnknown() {
        assertTrue(BatteryMonitor.shouldToggleMiner(listOf("unknown", "off"), "off"))
    }
}
