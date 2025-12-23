package com.example.carauth.auth

import com.example.carauth.data.model.ProviderType
import javax.inject.Inject

class AuthProviderFactory @Inject constructor(
    private val googleProvider: GoogleAuthProvider,
    private val azureProvider: AzureAuthProvider
) {
    private val providers = mapOf(
        ProviderType.GOOGLE to googleProvider,
        ProviderType.AZURE to azureProvider
    )
    
    fun getProvider(type: ProviderType): AuthProvider = 
        providers[type] ?: throw IllegalArgumentException("Unknown provider: $type")
    
    fun getAllProviders(): List<AuthProvider> = providers.values.toList()
    
    fun getProviderById(id: String): AuthProvider? = 
        providers.entries.find { it.key.id == id }?.value
}
