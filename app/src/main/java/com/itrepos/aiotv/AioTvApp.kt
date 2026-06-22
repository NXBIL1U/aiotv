package com.itrepos.aiotv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AioTvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.itrepos.aiotv.util.CrashLogger.install(this)
    }
}
