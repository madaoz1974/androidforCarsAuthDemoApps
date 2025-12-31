package com.example.carcompanion.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CustomTabsHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun openAuthUrl(providerInfo: ProviderInfo) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(providerInfo.brandColor.toArgb())
                    .build()
            )
            .build()
        
        try {
            val intent = customTabsIntent.intent
            intent.data = Uri.parse(providerInfo.verificationUrl)
            // Adding FLAG_ACTIVITY_NEW_TASK is necessary when launching from Application context
            // or when we want to ensure it opens properly.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: Open in default browser
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(providerInfo.verificationUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
