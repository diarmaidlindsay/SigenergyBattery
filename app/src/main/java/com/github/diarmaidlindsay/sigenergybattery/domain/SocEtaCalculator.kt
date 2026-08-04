package com.github.diarmaidlindsay.sigenergybattery.domain

/**
 * Estimates how long it will take the battery to reach a target SOC at the
 * current charge/discharge rate. The rate is the standard power-based method:
 *
 *     rate (%/h) = battery power (kW) / battery capacity (kWh) × 100
 *
 * Positive [batteryKw] charges the battery (SOC rises), negative discharges it
 * (SOC falls). The estimate is only meaningful when the battery is moving
 * toward the target, so it returns null when the sign of the rate opposes the
 * required SOC change, the rate is zero, or the capacity is unknown.
 */
object SocEtaCalculator {

    /** Minutes to reach [targetSoc] from [currentSoc], or null if unreachable. */
    fun minutesToTarget(
        currentSoc: Double,
        targetSoc: Double,
        batteryKw: Double,
        capacityKwh: Double,
    ): Long? {
        if (capacityKwh <= 0.0) return null
        val delta = targetSoc - currentSoc
        if (delta == 0.0) return 0L
        val ratePerHour = batteryKw / capacityKwh * 100.0
        if (ratePerHour == 0.0) return null
        if (delta * ratePerHour < 0.0) return null
        val hours = delta / ratePerHour
        if (hours < 0.0) return null
        return (hours * 60.0).toLong()
    }

    /** Formats [minutes] as a short human-readable duration. */
    fun formatMinutes(minutes: Long): String = when {
        minutes < 1L -> "<1m"
        minutes < 60L -> "${minutes}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
