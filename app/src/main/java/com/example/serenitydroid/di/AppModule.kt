package com.example.serenitydroid.di

import android.content.Context
import com.example.serenitydroid.engine.InferenceManager
import com.example.serenitydroid.engine.ModelRefresher
import com.example.serenitydroid.engine.DeviceDetector
import com.example.serenitydroid.data.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideInferenceManager(@ApplicationContext context: Context): InferenceManager {
        return InferenceManager(context)
    }

    @Provides
    @Singleton
    fun provideModelRefresher(): ModelRefresher {
        return ModelRefresher()
    }

    @Provides
    @Singleton
    fun provideDeviceDetector(@ApplicationContext context: Context): DeviceDetector {
        return DeviceDetector(context)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }
}
