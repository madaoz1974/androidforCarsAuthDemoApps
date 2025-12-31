package com.example.carcompanion.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PermissionRequest(
    permission: String,
    onPermissionGranted: () -> Unit
) {
    // This is a simplified version. Actual implementation should use Accompanist or native permission handling
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Please grant permission: $permission")
    }
}
