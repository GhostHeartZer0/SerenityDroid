package com.example.serenitydroid.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

class InferenceService : Service() {
    
    @dagger.hilt.EntryPoint
    @InstallIn(SingletonComponent::class)
    interface InferenceEntryPoint {
        fun getInferenceManager(): InferenceManager
    }

    lateinit var inferenceManager: InferenceManager

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): InferenceService = this@InferenceService
    }

    override fun onCreate() {
        super.onCreate()
        
        val entryPoint = EntryPoints.get(applicationContext, InferenceEntryPoint::class.java)
        inferenceManager = entryPoint.getInferenceManager()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Serenity AI Engine is active"))
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Serenity AI Inference",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Serenity AI")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "inference_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
