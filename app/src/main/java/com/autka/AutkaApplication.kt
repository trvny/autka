package com.autka

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class AutkaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // OpenStreetMap tile servers reject the default User-Agent, so identify the app
        // before any MapView is created; otherwise tiles silently fail to load.
        Configuration.getInstance().userAgentValue = packageName
    }
}
