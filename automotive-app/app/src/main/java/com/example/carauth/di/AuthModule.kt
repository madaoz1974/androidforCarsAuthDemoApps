package com.example.carauth.di

import com.example.carauth.BuildConfig
import com.example.carauth.auth.AuthProvider
import com.example.carauth.auth.GoogleAuthProvider
import com.example.carauth.auth.AzureAuthProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    
    @Provides
    @Named("googleClientId")
    fun provideGoogleClientId(): String = BuildConfig.GOOGLE_CLIENT_ID
    
    @Provides
    @Named("azureTenantId")
    fun provideAzureTenantId(): String = BuildConfig.AZURE_TENANT_ID
    
    @Provides
    @Named("azureClientId")
    fun provideAzureClientId(): String = BuildConfig.AZURE_CLIENT_ID
    
    @Provides
    @Named("azureUseCiam")
    fun provideAzureUseCiam(): Boolean = BuildConfig.AZURE_USE_CIAM.toBoolean()
    
    @Provides
    @Singleton
    fun provideGoogleAuthProvider(
        httpClient: OkHttpClient,
        @Named("googleClientId") clientId: String
    ): GoogleAuthProvider = GoogleAuthProvider(httpClient, clientId)
    
    @Provides
    @Singleton
    fun provideAzureAuthProvider(
        httpClient: OkHttpClient,
        @Named("azureTenantId") tenantId: String,
        @Named("azureClientId") clientId: String,
        @Named("azureUseCiam") useCiam: Boolean
    ): AzureAuthProvider = AzureAuthProvider(httpClient, tenantId, clientId, useCiam)
}
