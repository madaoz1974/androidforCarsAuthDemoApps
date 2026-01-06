package com.example.carauth.di

import com.example.carauth.BuildConfig
import com.example.carauth.bluetooth.BluetoothAuthService
import com.example.carauth.bluetooth.BluetoothAuthServiceInterface
import com.example.carauth.bluetooth.FakeBluetoothAuthService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing Bluetooth authentication service.
 * Switches between real and fake implementation based on build config.
 */
@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {
    
    @Provides
    @Singleton
    fun provideBluetoothAuthService(
        realService: BluetoothAuthService,
        fakeService: FakeBluetoothAuthService
    ): BluetoothAuthServiceInterface {
        // Use fake service in debug builds or when USE_FAKE_BLUETOOTH is true
        return if (BuildConfig.DEBUG && BuildConfig.USE_FAKE_BLUETOOTH) {
            fakeService
        } else {
            realService
        }
    }
    
    /**
     * Provide FakeBluetoothAuthService for debug panel access.
     * Only accessible in debug builds.
     */
    @Provides
    @Singleton
    fun provideFakeBluetoothService(): FakeBluetoothAuthService {
        return FakeBluetoothAuthService()
    }
}
