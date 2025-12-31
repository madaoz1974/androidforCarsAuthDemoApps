package com.example.carauth.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.carauth.auth.AuthState
import com.example.carauth.ui.screens.AuthStatusScreen
import com.example.carauth.ui.screens.ProviderSelectionScreen
import com.example.carauth.ui.screens.QRCodeScreen
import com.example.carauth.viewmodel.AuthViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.ProviderSelection.route
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()
    val providers = authViewModel.providers
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.ProviderSelection.route) {
            // Check state to navigate
            LaunchedEffect(authState) {
                when (authState) {
                    is AuthState.DisplayingQR -> navController.navigate(Screen.QRCode.route)
                    is AuthState.Authenticated, is AuthState.Error -> navController.navigate(Screen.AuthStatus.route)
                    else -> { /* Stay here */ }
                }
            }
            
            ProviderSelectionScreen(
                providers = providers,
                onProviderSelected = { provider ->
                    authViewModel.selectProvider(provider)
                }
            )
        }
        
        composable(Screen.QRCode.route) {
            val state = authState // Capture for smart cast
            if (state is AuthState.DisplayingQR) {
                // Check if we moved out of DisplayingQR
                LaunchedEffect(authState) {
                     when (authState) {
                         is AuthState.Authenticated, is AuthState.Error -> {
                             navController.navigate(Screen.AuthStatus.route) {
                                 popUpTo(Screen.ProviderSelection.route)
                             }
                         }
                         else -> {}
                     }
                }

                QRCodeScreen(
                    state = state,
                    onCancel = {
                        authViewModel.resetInfo()
                        navController.popBackStack()
                    }
                )
            } else {
                 LaunchedEffect(Unit) {
                     // If we are here but state is not DisplayingQR, it means we probably navigated away or back
                     // Just in case, redirect or show loading
                     if (state is AuthState.Authenticated || state is AuthState.Error) {
                          navController.navigate(Screen.AuthStatus.route) {
                              popUpTo(Screen.ProviderSelection.route)
                          }
                     } else {
                         // Go back
                         navController.popBackStack()
                     }
                 }
            }
        }
        
        composable(Screen.AuthStatus.route) {
             // Only reset if we are intentionally leaving this screen via buttons
             // No auto-navigation here generally
             
             AuthStatusScreen(
                state = authState,
                onRetry = {
                    authViewModel.resetInfo()
                    // resetInfo sets state to Idle.
                    // The ProviderSelection screen observes Idle and stays there.
                    navController.navigate(Screen.ProviderSelection.route) {
                        popUpTo(Screen.ProviderSelection.route) { inclusive = true }
                    }
                },
                onReset = {
                    authViewModel.resetInfo()
                    navController.navigate(Screen.ProviderSelection.route) {
                         popUpTo(Screen.ProviderSelection.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
