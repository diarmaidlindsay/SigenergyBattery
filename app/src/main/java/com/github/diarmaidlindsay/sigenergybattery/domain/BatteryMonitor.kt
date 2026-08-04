package com.github.diarmaidlindsay.sigenergybattery.domain

import com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction
import com.github.diarmaidlindsay.sigenergybattery.domain.model.TriggerAction

/**
 * Pure decision logic for the battery alert. Kept dependency-free so it can be
 * unit tested directly.
 */
object BatteryMonitor {

    /**
     * Returns true when [soc] has reached the user's threshold in the given
     * direction. A null/unavailable SOC never triggers.
     */
    fun shouldNotify(
        soc: Double?,
        threshold: Double,
        direction: Direction,
    ): Boolean {
        if (soc == null) return false
        return when (direction) {
            Direction.AT_OR_ABOVE -> soc >= threshold
            Direction.AT_OR_BELOW -> soc <= threshold
        }
    }

    /**
     * Whether [actions] is a valid combination. MINER_ON and MINER_OFF are
     * mutually exclusive and MINER_OFF excludes SET_POWER_PRESET. NOTIFY is
     * always allowed; SET_POWER_PRESET may be selected alone (e.g. changing
     * the target of an already-running miner) or with MINER_ON.
     */
    fun isValidActions(actions: Set<TriggerAction>): Boolean {
        val hasOn = TriggerAction.MINER_ON in actions
        val hasOff = TriggerAction.MINER_OFF in actions
        val hasPreset = TriggerAction.SET_POWER_PRESET in actions
        return !(hasOn && hasOff) && !(hasOff && hasPreset)
    }

    /**
     * Enforces the exclusion rules as the user toggles actions. [justSelected]
     * is the action the user most recently checked; it wins any conflict so
     * MINER_ON and MINER_OFF can be switched freely while staying mutually
     * exclusive:
     * - checking MINER_OFF removes MINER_ON and SET_POWER_PRESET
     * - checking MINER_ON removes MINER_OFF
     * - checking SET_POWER_PRESET removes MINER_OFF (preset may stand alone)
     * - when unchecking (null/NOTIFY), the base rules apply: OFF excludes ON
     *   and preset
     */
    fun normalizeActions(
        actions: Set<TriggerAction>,
        justSelected: TriggerAction? = null,
    ): Set<TriggerAction> = buildSet {
        addAll(actions)
        when (justSelected) {
            TriggerAction.MINER_ON -> remove(TriggerAction.MINER_OFF)
            TriggerAction.MINER_OFF -> {
                remove(TriggerAction.MINER_ON)
                remove(TriggerAction.SET_POWER_PRESET)
            }
            TriggerAction.SET_POWER_PRESET -> remove(TriggerAction.MINER_OFF)
            null, TriggerAction.NOTIFY -> {
                if (TriggerAction.MINER_OFF in this) {
                    remove(TriggerAction.MINER_ON)
                    remove(TriggerAction.SET_POWER_PRESET)
                }
            }
        }
    }

    /**
     * Whether a miner switch action is needed given the current HA switch
     * states (values "on"/"off"). A null/empty read counts as "proceed" so the
     * intended state is still reached.
     */
    fun shouldToggleMiner(currentStates: Collection<String?>?, desired: String): Boolean {
        if (currentStates.isNullOrEmpty()) return true
        return currentStates.any { it != desired }
    }

    /**
     * Whether the power preset should be (re)applied given the miner's current
     * autotuning power target. A null read (unreachable) counts as "set it" so
     * the intended state is still reached.
     */
    fun shouldSetPreset(currentPowerTargetW: Int?, desiredWatts: Int): Boolean =
        currentPowerTargetW == null || currentPowerTargetW != desiredWatts
}
