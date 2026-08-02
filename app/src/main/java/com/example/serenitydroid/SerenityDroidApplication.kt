package com.example.serenitydroid

import android.app.Application
import android.content.Context
import com.google.android.play.core.splitcompat.SplitCompat
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SerenityDroidApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        SplitCompat.install(this)
    }
}
