package com.example.scrollproject

import android.app.Application
import com.example.scrollproject.core.TimerManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ScrollGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TimerManager.init(this)
    }
}
