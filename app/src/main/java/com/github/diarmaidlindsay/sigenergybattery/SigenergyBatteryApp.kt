package com.github.diarmaidlindsay.sigenergybattery

import android.app.Application
import com.github.diarmaidlindsay.sigenergybattery.core.di.AppContainer

class SigenergyBatteryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
