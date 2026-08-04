package com.github.diarmaidlindsay.hermesbattery.core.di

import android.content.Context
import com.github.diarmaidlindsay.hermesbattery.data.api.ApiClientFactory
import com.github.diarmaidlindsay.hermesbattery.data.api.HermesApi
import com.github.diarmaidlindsay.hermesbattery.data.local.DataStoreSettingsStore
import com.github.diarmaidlindsay.hermesbattery.data.local.SettingsStore
import com.github.diarmaidlindsay.hermesbattery.domain.model.BridgeConfig
import com.github.diarmaidlindsay.hermesbattery.domain.model.MonitorConfig
import com.github.diarmaidlindsay.hermesbattery.service.PollingService

/**
 * Minimal manual dependency injection container. Keeps the object graph in one
 * place while letting ViewModels be constructed with fakes in tests.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsStore: SettingsStore by lazy { DataStoreSettingsStore(appContext) }

    /** Creates a Hermes API client bound to [config] (bearer auth baked in). */
    fun createApi(config: BridgeConfig): HermesApi = ApiClientFactory.create(config)

    /** Starts the background polling service (Android context-dependent). */
    fun startMonitoring(config: MonitorConfig) {
        PollingService.start(appContext)
    }

    /** Stops the background polling service. */
    fun stopMonitoring() {
        PollingService.stop(appContext)
    }
}
