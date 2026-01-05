package com.example.carcompanion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.carcompanion.navigation.NavGraph
import com.example.carcompanion.ui.theme.Theme
import com.example.carcompanion.viewmodel.ScanViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var navController: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            Theme {
                navController = rememberNavController()
                // Create ScanViewModel at Activity scope to persist across navigation
                val scanViewModel: ScanViewModel = hiltViewModel()
                NavGraph(
                    navController = navController,
                    scanViewModel = scanViewModel
                )
            }
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        navController.handleDeepLink(intent)
    }
}
