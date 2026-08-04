package com.github.diarmaidlindsay.sigenergybattery.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.diarmaidlindsay.sigenergybattery.domain.model.BridgeConfig
import com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MonitorConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Abstraction over app settings persistence so ViewModels can be tested with
 * in-memory fakes.
 */
interface SettingsStore {
    val bridgeConfig: Flow<BridgeConfig>
    val monitorConfig: Flow<MonitorConfig>
    val hasConnectedBefore: Flow<Boolean>
    suspend fun saveBridgeConfig(config: BridgeConfig)
    suspend fun saveMonitorConfig(config: MonitorConfig)
    suspend fun setHasConnectedBefore(value: Boolean)

    companion object {
        val DEFAULT_HOST = "10.0.0.30"
        val DEFAULT_PORT = "8500"
        val DEFAULT_API_KEY: String = com.github.diarmaidlindsay.sigenergybattery.BuildConfig.HERMES_API_KEY
        const val DEFAULT_INTERVAL_MINUTES = 5
        const val DEFAULT_THRESHOLD_SOC = 20.0
        val DEFAULT_DIRECTION = Direction.AT_OR_BELOW
    }
}

private val Context.dataStore by preferencesDataStore(name = "hermes_settings")

class DataStoreSettingsStore(context: Context) : SettingsStore {

    private val dataStore = context.applicationContext.dataStore

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = stringPreferencesKey("port")
        val API_KEY = stringPreferencesKey("api_key")
        val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        val THRESHOLD_SOC = doublePreferencesKey("threshold_soc")
        val DIRECTION = stringPreferencesKey("direction")
        val HAS_CONNECTED_BEFORE = booleanPreferencesKey("has_connected_before")
    }

    override val bridgeConfig: Flow<BridgeConfig> = dataStore.data.map { prefs ->
        BridgeConfig(
            host = prefs[Keys.HOST] ?: DEFAULT_HOST,
            port = prefs[Keys.PORT] ?: DEFAULT_PORT,
            apiKey = prefs[Keys.API_KEY] ?: DEFAULT_API_KEY,
        )
    }

    override val monitorConfig: Flow<MonitorConfig> = dataStore.data.map { prefs ->
        MonitorConfig(
            intervalMinutes = prefs[Keys.INTERVAL_MINUTES] ?: DEFAULT_INTERVAL_MINUTES,
            thresholdSoc = prefs[Keys.THRESHOLD_SOC] ?: DEFAULT_THRESHOLD_SOC,
            direction = prefs[Keys.DIRECTION]?.let { d ->
                Direction.entries.firstOrNull { it.name == d }
            } ?: DEFAULT_DIRECTION,
        )
    }

    override val hasConnectedBefore: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.HAS_CONNECTED_BEFORE] ?: false
    }

    override suspend fun saveBridgeConfig(config: BridgeConfig) {
        dataStore.edit {
            it[Keys.HOST] = config.host.trim()
            it[Keys.PORT] = config.port.trim()
            it[Keys.API_KEY] = config.apiKey.trim()
        }
    }

    override suspend fun saveMonitorConfig(config: MonitorConfig) {
        dataStore.edit {
            it[Keys.INTERVAL_MINUTES] = config.intervalMinutes
            it[Keys.THRESHOLD_SOC] = config.thresholdSoc
            it[Keys.DIRECTION] = config.direction.name
        }
    }

    override suspend fun setHasConnectedBefore(value: Boolean) {
        dataStore.edit { it[Keys.HAS_CONNECTED_BEFORE] = value }
    }

    companion object {
        val DEFAULT_HOST = "10.0.0.30"
        val DEFAULT_PORT = "8500"
        val DEFAULT_API_KEY: String = com.github.diarmaidlindsay.sigenergybattery.BuildConfig.HERMES_API_KEY
        const val DEFAULT_INTERVAL_MINUTES = 5
        const val DEFAULT_THRESHOLD_SOC = 20.0
        val DEFAULT_DIRECTION = Direction.AT_OR_BELOW
    }
}
