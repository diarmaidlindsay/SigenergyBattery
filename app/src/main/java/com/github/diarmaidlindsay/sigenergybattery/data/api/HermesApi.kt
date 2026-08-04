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
import retrofit2.http.GET
import retrofit2.http.Headers
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
