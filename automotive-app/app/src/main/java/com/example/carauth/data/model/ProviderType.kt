package com.example.carauth.data.model

enum class ProviderType(
    val id: String,
    val displayName: String
) {
    GOOGLE("google", "Google"),
    AZURE("azure", "Microsoft");
    
    companion object {
        fun fromId(id: String): ProviderType? = 
            values().find { it.id == id }
    }
}
