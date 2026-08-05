package com.github.diarmaidlindsay.sigenergybattery.data.api

import com.github.diarmaidlindsay.sigenergybattery.domain.model.BridgeConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.SolarSnapshot
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
