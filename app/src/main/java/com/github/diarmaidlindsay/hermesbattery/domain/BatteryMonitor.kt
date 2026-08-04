package com.github.diarmaidlindsay.hermesbattery.domain

import com.github.diarmaidlindsay.hermesbattery.domain.model.Direction

/**
 * Pure decision logic for when to fire the battery alert. Kept dependency-free
 * so it can be unit tested directly.
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
}
