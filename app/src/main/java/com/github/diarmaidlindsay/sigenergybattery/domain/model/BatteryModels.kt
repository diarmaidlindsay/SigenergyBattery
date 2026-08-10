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

/** Built-in seasonal strategy templates served by the bridge. */
enum class Season(val key: String, val displayName: String) {
    SUMMER("summer", "Summer (Jun-Aug)"),
    SPRING("spring", "Spring (Mar-May)"),
    AUTUMN("autumn", "Autumn (Sep-Nov)"),
}

/** A step's entry condition: SOC threshold + direction, optionally gated by
 * a minimum time of day (HH:MM, local bridge time). */
data class StrategyCondition(
    val socThreshold: Double,
    val direction: Direction,
    val timeAfter: String? = null,
    val exitSocThreshold: Double? = null,
)

/** One state in a strategy. The bridge enters it when its [condition] is met
 * and runs [actions] (and [minerPreset] if SET_POWER_PRESET is selected). */
data class StrategyStep(
    val name: String,
    val condition: StrategyCondition,
    val actions: Set<TriggerAction> = setOf(TriggerAction.NOTIFY),
    val minerPreset: MinerPreset? = null,
)

/** User-facing configuration for a strategy. */
data class StrategyConfig(
    val name: String,
    val intervalMinutes: Int,
    val activeHoursStart: String,
    val activeHoursEnd: String,
    val steps: List<StrategyStep>,
)

/** Strategy config + runtime state returned by the bridge. */
data class StrategyStatus(
    val enabled: Boolean,
    val name: String?,
    val intervalMinutes: Int,
    val activeHoursStart: String?,
    val activeHoursEnd: String?,
    val steps: List<StrategyStep>,
    val currentStep: Int,
    val lastTransitionAt: Long?,
    val lastSoc: Double?,
    val lastError: String?,
)

/** What kind of automation produced an event recorded on the bridge. */
enum class EventType {
    TRIGGER,
    STRATEGY,
}

/** One recorded event for the SOC chart: a one-shot trigger firing or a
 * strategy step transition. `epochSeconds` locates the dot on the time axis;
 * type-specific fields feed the long-press overlay. */
data class ChartEvent(
    val type: EventType,
    val epochSeconds: Long,
    val soc: Double?,
    val error: String?,
    val thresholdSoc: Double?,
    val direction: Direction?,
    val actions: Set<TriggerAction> = emptySet(),
    val minerPreset: MinerPreset? = null,
    val reason: String? = null,
    val fromStep: Int? = null,
    val toStep: Int? = null,
    val stepName: String? = null,
    val strategyName: String? = null,
)
