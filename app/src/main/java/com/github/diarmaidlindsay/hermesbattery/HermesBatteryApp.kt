package com.github.diarmaidlindsay.hermesbattery

import android.app.Application
import com.github.diarmaidlindsay.hermesbattery.core.di.AppContainer

class HermesBatteryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
