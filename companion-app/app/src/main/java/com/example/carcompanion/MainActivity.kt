package com.example.carcompanion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.example.carcompanion.navigation.NavGraph
import com.example.carcompanion.ui.theme.Theme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle deep link or regular launch
        // The deep link handling logic is usually handled by Navigation component implicitly 
        // if configured in NavGraph, or we can handle it here if needed.
        // For now, standard setup.
        
        setContent {
            Theme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // If we want to handle deep links that come while app is running
        // navController.handleDeepLink(intent)
    }
}
