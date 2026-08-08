package com.github.diarmaidlindsay.sigenergybattery.data.api

import com.github.diarmaidlindsay.sigenergybattery.domain.model.BridgeConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.ChartEvent
import com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction
import com.github.diarmaidlindsay.sigenergybattery.domain.model.EventType
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MinerPreset
import com.github.diarmaidlindsay.sigenergybattery.domain.model.SolarSnapshot
import com.github.diarmaidlindsay.sigenergybattery.domain.model.StrategyCondition
import com.github.diarmaidlindsay.sigenergybattery.domain.model.StrategyConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.StrategyStep
import com.github.diarmaidlindsay.sigenergybattery.domain.model.StrategyStatus
import com.github.diarmaidlindsay.sigenergybattery.domain.model.TriggerAction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

@Serializable
data class SolarNowDto(
    @SerialName("battery_soc_pct") val batterySocPct: Double? = null,
    @SerialName("battery_kw") val batteryKw: Double? = null,
    val battery: BatteryDto? = null,
)

@Serializable
data class BatteryDto(
    @SerialName("soc_pct") val socPct: Double? = null,
    @SerialName("capacity_kwh") val capacityKwh: Double? = null,
)

@Serializable
data class SolarHistoryDto(
    @SerialName("interval_minutes") val intervalMinutes: Int = 5,
    @SerialName("start") val start: Long = 0,
    @SerialName("end") val end: Long = 0,
    val points: List<HistoryPointDto> = emptyList(),
)

@Serializable
data class HistoryPointDto(
    @SerialName("t") val t: Long = 0,
    val soc: Double? = null,
)

/** Acknowledgment returned by the miner action endpoints. */
@Serializable
data class MinerActionResponse(
    @SerialName("status") val status: String? = null,
)

/** Payload sent to the bridge when arming a SOC trigger. */
@Serializable
data class TriggerConfigDto(
    @SerialName("interval_minutes") val intervalMinutes: Int = 5,
    @SerialName("threshold_soc") val thresholdSoc: Double = 20.0,
    val direction: String = "AT_OR_BELOW",
    val actions: List<String> = listOf("NOTIFY"),
    @SerialName("miner_preset") val minerPreset: String? = null,
)

/** Full trigger config + runtime status returned by the bridge. */
@Serializable
data class TriggerStatusDto(
    val enabled: Boolean = false,
    @SerialName("interval_minutes") val intervalMinutes: Int = 5,
    @SerialName("threshold_soc") val thresholdSoc: Double = 20.0,
    val direction: String = "AT_OR_BELOW",
    val actions: List<String> = listOf("NOTIFY"),
    @SerialName("miner_preset") val minerPreset: String? = null,
    @SerialName("created_at") val createdAt: Double? = null,
    val fired: Boolean = false,
    @SerialName("fired_at") val firedAt: Double? = null,
    @SerialName("fired_soc") val firedSoc: Double? = null,
    @SerialName("action_error") val actionError: String? = null,
    @SerialName("last_checked_at") val lastCheckedAt: Double? = null,
    @SerialName("last_soc") val lastSoc: Double? = null,
)

/** Generic acknowledgment for trigger/device endpoints. */
@Serializable
data class TriggerAckDto(
    @SerialName("status") val status: String? = null,
)

/** FCM device token registration sent to the bridge. */
@Serializable
data class DeviceRegisterDto(
    val token: String,
)

/** One step's condition: SOC threshold + direction, optionally gated by a
 * minimum time of day (HH:MM, bridge-local). */
@Serializable
data class StrategyConditionDto(
    @SerialName("soc_threshold") val socThreshold: Double = 70.0,
    val direction: String = "AT_OR_BELOW",
    @SerialName("time_after") val timeAfter: String? = null,
)

/** One state in a strategy as sent/received by the bridge. */
@Serializable
data class StrategyStepDto(
    val name: String = "",
    val condition: StrategyConditionDto = StrategyConditionDto(),
    val actions: List<String> = listOf("MINER_OFF"),
    @SerialName("miner_preset") val minerPreset: String? = null,
)

/** Payload sent to the bridge when starting/replacing a strategy. */
@Serializable
data class StrategyConfigDto(
    val name: String = "Miner Strategy",
    @SerialName("interval_minutes") val intervalMinutes: Int = 5,
    @SerialName("active_hours_start") val activeHoursStart: String = "06:00",
    @SerialName("active_hours_end") val activeHoursEnd: String = "22:00",
    val steps: List<StrategyStepDto> = emptyList(),
)

/** Strategy config + runtime status returned by the bridge. */
@Serializable
data class StrategyStatusDto(
    val enabled: Boolean = false,
    val name: String? = null,
    @SerialName("interval_minutes") val intervalMinutes: Int = 5,
    @SerialName("active_hours_start") val activeHoursStart: String? = null,
    @SerialName("active_hours_end") val activeHoursEnd: String? = null,
    val steps: List<StrategyStepDto> = emptyList(),
    @SerialName("current_step") val currentStep: Int = 0,
    @SerialName("last_transition_at") val lastTransitionAt: Double? = null,
    @SerialName("last_soc") val lastSoc: Double? = null,
    @SerialName("last_error") val lastError: String? = null,
)

/** Seasonal strategy templates returned by the bridge. */
@Serializable
data class StrategyTemplatesDto(
    val templates: Map<String, StrategyConfigDto> = emptyMap(),
)

/** One recorded event (trigger fire or strategy transition) from /api/events.
 * Trigger events carry threshold/direction; strategy events carry
 * reason/from_step/to_step/step_name/strategy_name. */
@Serializable
data class EventDto(
    @SerialName("type") val type: String = "",
    @SerialName("t") val t: Double = 0.0,
    @SerialName("soc") val soc: Double? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("threshold_soc") val thresholdSoc: Double? = null,
    @SerialName("direction") val direction: String? = null,
    @SerialName("actions") val actions: List<String> = emptyList(),
    @SerialName("miner_preset") val minerPreset: String? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("from_step") val fromStep: Int? = null,
    @SerialName("to_step") val toStep: Int? = null,
    @SerialName("step_name") val stepName: String? = null,
    @SerialName("strategy_name") val strategyName: String? = null,
)

/** Response wrapper for /api/events: recorded events, newest first. */
@Serializable
data class EventsDto(
    val events: List<EventDto> = emptyList(),
)

/** Miner status for switch idempotency and the power-preset decision. */
@Serializable
data class MinerStatusDto(
    @SerialName("power_target_w") val powerTargetW: Int? = null,
    @SerialName("switches") val switches: Map<String, String> = emptyMap(),
) {
    /** HA switch state values ("on"/"off") for each miner smart plug. */
    val switchStates: List<String>
        get() = switches.values.toList()
}

fun SolarNowDto.toSnapshot(): SolarSnapshot = SolarSnapshot(
    socPct = batterySocPct ?: battery?.socPct,
    batteryKw = batteryKw,
    capacityKwh = battery?.capacityKwh,
)

fun StrategyConfigDto.toStrategyConfig(): StrategyConfig = StrategyConfig(
    name = name,
    intervalMinutes = intervalMinutes,
    activeHoursStart = activeHoursStart,
    activeHoursEnd = activeHoursEnd,
    steps = steps.map { it.toStrategyStep() },
)

fun StrategyConfig.toStrategyConfigDto(): StrategyConfigDto = StrategyConfigDto(
    name = name,
    intervalMinutes = intervalMinutes,
    activeHoursStart = activeHoursStart,
    activeHoursEnd = activeHoursEnd,
    steps = steps.map { it.toStrategyStepDto() },
)

fun StrategyStatusDto.toStrategyStatus(): StrategyStatus = StrategyStatus(
    enabled = enabled,
    name = name,
    intervalMinutes = intervalMinutes,
    activeHoursStart = activeHoursStart,
    activeHoursEnd = activeHoursEnd,
    steps = steps.map { it.toStrategyStep() },
    currentStep = currentStep,
    lastTransitionAt = lastTransitionAt?.let { (it * 1000).toLong() },
    lastSoc = lastSoc,
    lastError = lastError,
)

fun StrategyStepDto.toStrategyStep(): StrategyStep = StrategyStep(
    name = name,
    condition = StrategyCondition(
        socThreshold = condition.socThreshold,
        direction = directionFromName(condition.direction),
        timeAfter = condition.timeAfter,
    ),
    actions = actions.mapNotNull { actionName -> TriggerAction.entries.firstOrNull { it.name == actionName } }.toSet(),
    minerPreset = minerPreset?.let { slug -> MinerPreset.entries.firstOrNull { it.slug == slug } },
)

fun EventDto.toChartEvent(): ChartEvent = ChartEvent(
    type = if (type == "strategy") EventType.STRATEGY else EventType.TRIGGER,
    epochSeconds = t.toLong(),
    soc = soc,
    error = error,
    thresholdSoc = thresholdSoc,
    direction = direction?.let { directionFromName(it) },
    actions = actions.mapNotNull { actionName -> TriggerAction.entries.firstOrNull { it.name == actionName } }.toSet(),
    minerPreset = minerPreset?.let { slug -> MinerPreset.entries.firstOrNull { it.slug == slug } },
    reason = reason,
    fromStep = fromStep,
    toStep = toStep,
    stepName = stepName,
    strategyName = strategyName,
)

fun StrategyStep.toStrategyStepDto(): StrategyStepDto = StrategyStepDto(
    name = name,
    condition = StrategyConditionDto(
        socThreshold = condition.socThreshold,
        direction = condition.direction.name,
        timeAfter = condition.timeAfter,
    ),
    actions = actions.map { it.name },
    minerPreset = minerPreset?.slug,
)

private fun directionFromName(name: String): Direction = when (name) {
    "AT_OR_ABOVE" -> Direction.AT_OR_ABOVE
    else -> Direction.AT_OR_BELOW
}

/** Thin Retrofit interface for the Hermes bridge GET endpoints. */
interface HermesApi {
    @GET("api/solar/now")
    @Headers("Accept: application/json")
    suspend fun solarNow(): SolarNowDto

    @GET("api/solar/history")
    @Headers("Accept: application/json")
    suspend fun solarHistory(): SolarHistoryDto

    @POST("api/miner/on")
    @Headers("Accept: application/json")
    suspend fun minerOn(): MinerActionResponse

    @POST("api/miner/off")
    @Headers("Accept: application/json")
    suspend fun minerOff(): MinerActionResponse

    @POST("api/miner/power-preset/{preset}")
    @Headers("Accept: application/json")
    suspend fun setPowerPreset(@Path("preset") preset: String): MinerActionResponse

    @GET("api/miner/status")
    @Headers("Accept: application/json")
    suspend fun minerStatus(): MinerStatusDto

    @POST("api/trigger")
    @Headers("Accept: application/json")
    suspend fun setTrigger(@Body body: TriggerConfigDto): TriggerStatusDto

    @GET("api/trigger")
    @Headers("Accept: application/json")
    suspend fun getTrigger(): TriggerStatusDto

    @DELETE("api/trigger")
    @Headers("Accept: application/json")
    suspend fun deleteTrigger(): TriggerAckDto

    @POST("api/device")
    @Headers("Accept: application/json")
    suspend fun registerDevice(@Body body: DeviceRegisterDto): TriggerAckDto

    @GET("api/strategy/templates")
    @Headers("Accept: application/json")
    suspend fun strategyTemplates(): StrategyTemplatesDto

    @POST("api/strategy")
    @Headers("Accept: application/json")
    suspend fun setStrategy(@Body body: StrategyConfigDto): StrategyStatusDto

    @GET("api/strategy")
    @Headers("Accept: application/json")
    suspend fun getStrategy(): StrategyStatusDto

    @DELETE("api/strategy")
    @Headers("Accept: application/json")
    suspend fun deleteStrategy(): TriggerAckDto

    @GET("api/events")
    @Headers("Accept: application/json")
    suspend fun events(): EventsDto
}

object ApiClientFactory {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    /** Builds a [HermesApi] bound to [config], sending the API key as a bearer token. */
    fun create(config: BridgeConfig): HermesApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer ${config.apiKey.trim()}")
                    .build()
                chain.proceed(request)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("${config.baseUrl}/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(HermesApi::class.java)
    }
}
