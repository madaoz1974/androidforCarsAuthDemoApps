package com.example.carcompanion.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDeepLink
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.carcompanion.scanner.ScanResult
import com.example.carcompanion.ui.screens.HomeScreen
import com.example.carcompanion.ui.screens.ProviderConfirmScreen
import com.example.carcompanion.ui.screens.ResultScreen
import com.example.carcompanion.ui.screens.ScanScreen
import com.example.carcompanion.viewmodel.AuthViewModel
import com.example.carcompanion.viewmodel.ScanViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scan : Screen("scan")
    object ProviderConfirm : Screen("provider_confirm")
    object Result : Screen("result/{success}") {
        fun createRoute(success: Boolean) = "result/$success"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    scanViewModel: ScanViewModel,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onStartScan = {
                    scanViewModel.startScanning()
                    navController.navigate(Screen.Scan.route)
                }
            )
        }
        
        composable(Screen.Scan.route) {
            val scanResult by scanViewModel.scanResult.collectAsState()
            
            ScanScreen(
                scanResult = scanResult,
                onQRCodeScanned = { scanViewModel.onQRCodeScanned(it) },
                onClose = { navController.popBackStack() }
            )
            
            // Navigate on success
            LaunchedEffect(scanResult) {
                if (scanResult is ScanResult.Success) {
                    navController.navigate(Screen.ProviderConfirm.route)
                }
            }
        }
        
        composable(Screen.ProviderConfirm.route) {
            val scanResult by scanViewModel.scanResult.collectAsState()
            val providerInfo = (scanResult as? ScanResult.Success)?.providerInfo
            
            if (providerInfo != null) {
                val authViewModel: AuthViewModel = hiltViewModel()
                val customTabsHelper = authViewModel.customTabsHelper
                val bluetoothScanState by authViewModel.scanState.collectAsState()
                val bluetoothConnectionState by authViewModel.connectionState.collectAsState()
                
                // Start Bluetooth scanning when entering this screen
                LaunchedEffect(Unit) {
                    authViewModel.startBluetoothScan()
                }
                
                ProviderConfirmScreen(
                    providerInfo = providerInfo,
                    onConfirm = {
                        customTabsHelper.openAuthUrl(providerInfo)
                    },
                    onCancel = {
                        authViewModel.stopBluetoothScan()
                        scanViewModel.resetScan()
                        navController.popBackStack(Screen.Home.route, false)
                    }
                )
            } else {
                // If scanned result is lost, go back to home
                LaunchedEffect(Unit) {
                    navController.popBackStack(Screen.Home.route, false)
                }
            }
        }
        
        composable(
            route = Screen.Result.route,
            arguments = listOf(navArgument("success") { 
                type = NavType.BoolType
                defaultValue = true
            }),
            deepLinks = listOf(NavDeepLink("carauth://auth"))
        ) { backStackEntry ->
            val success = backStackEntry.arguments?.getBoolean("success") ?: true
            
            ResultScreen(
                success = success,
                onDone = {
                    scanViewModel.resetScan()
                    navController.popBackStack(Screen.Home.route, false)
                },
                onRetry = {
                    scanViewModel.resetScan()
                    navController.popBackStack(Screen.Scan.route, false)
                }
            )
        }
    }
}
