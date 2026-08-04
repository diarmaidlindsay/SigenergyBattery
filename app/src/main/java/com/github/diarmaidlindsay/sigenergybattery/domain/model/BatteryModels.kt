package com.github.diarmaidlindsay.sigenergybattery.domain.model

/** Which side of the threshold should trigger the alert. */
enum class Direction {
    AT_OR_ABOVE,
    AT_OR_BELOW,
}

/** Actions the app may run when the SOC threshold is reached. */
enum class TriggerAction {
    NOTIFY,
    MINER_ON,
    MINER_OFF,
    SET_POWER_PRESET,
}

/** Power presets exposed by the bridge's power-preset endpoint. */
enum class MinerPreset(val watts: Int, val slug: String) {
    LOW(1000, "low"),
    EFFICIENT(2000, "efficient"),
    MAX(2760, "max"),
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
    val triggerActions: Set<TriggerAction> = setOf(TriggerAction.NOTIFY),
    val minerPreset: MinerPreset? = null,
) {
    val intervalMillis: Long get() = intervalMinutes * 60_000L
}

/** Live snapshot returned by the bridge. */
data class SolarSnapshot(
    val socPct: Double?,
    val batteryKw: Double? = null,
    val capacityKwh: Double? = null,
)
