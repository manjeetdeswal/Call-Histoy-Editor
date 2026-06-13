package com.manjeet_deswal.callhistoryeditorandbackup

import android.app.Application
import com.google.android.material.color.DynamicColors

class MyApp:Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}