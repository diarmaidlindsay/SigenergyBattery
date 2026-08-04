package com.github.diarmaidlindsay.hermesbattery.domain.model

/** Which side of the threshold should trigger the alert. */
enum class Direction {
    AT_OR_ABOVE,
    AT_OR_BELOW,
}

/** Settings describing the bridge to talk to. */
data class BridgeConfig(
    val host: String,
    val port: String,
    val apiKey: String,
) {
    val baseUrl: String
        get() = "http://${host.trim().trimEnd('/')}:${port.trim()}"
}

/** User-facing polling/alert configuration. */
data class MonitorConfig(
    val intervalMinutes: Int,
    val thresholdSoc: Double,
    val direction: Direction,
) {
    val intervalMillis: Long get() = intervalMinutes * 60_000L
}

/** Live snapshot returned by the bridge. */
data class SolarSnapshot(
    val socPct: Double?,
    val batteryKw: Double? = null,
    val capacityKwh: Double? = null,
)
