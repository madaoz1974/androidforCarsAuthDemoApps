package com.example.carauth.navigation

sealed class Screen(val route: String) {
    object ProviderSelection : Screen("provider_selection")
    object QRCode : Screen("qr_code")
    object AuthStatus : Screen("auth_status")
}
