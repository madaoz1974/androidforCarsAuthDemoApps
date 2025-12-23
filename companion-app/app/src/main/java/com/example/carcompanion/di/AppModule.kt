package com.example.carcompanion.di

import android.content.Context
import com.example.carcompanion.util.VibrationUtils
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
    fun provideVibrationUtils(
        @ApplicationContext context: Context
    ): VibrationUtils = VibrationUtils(context)
}
