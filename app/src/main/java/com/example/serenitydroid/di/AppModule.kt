package com.example.serenitydroid.di

import android.content.Context
import com.example.serenitydroid.engine.LiteRtManager
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
    fun provideLiteRtManager(@ApplicationContext context: Context): LiteRtManager {
        return LiteRtManager(context)
    }
}
