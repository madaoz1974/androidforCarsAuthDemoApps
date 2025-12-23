package com.example.carcompanion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.carcompanion.R
import com.example.carcompanion.scanner.ScanResult
import com.example.carcompanion.ui.components.CameraPreview
import com.example.carcompanion.ui.components.ProviderBadge
import com.example.carcompanion.ui.components.ScanOverlay

@Composable
fun ScanScreen(
    scanResult: ScanResult,
    onQRCodeScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Camera Preview
        CameraPreview(
            onQRCodeScanned = onQRCodeScanned,
            modifier = Modifier.fillMaxSize()
        )
        
        // Scan Overlay
        ScanOverlay(
            scanResult = scanResult,
            modifier = Modifier.fillMaxSize()
        )
        
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White
                )
            }
            
            Text(
                text = stringResource(R.string.scan_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.size(48.dp))
        }
        
        // Bottom Instructions
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.scan_instruction),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Provider Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                ProviderBadge(
                    iconResId = R.drawable.ic_google,
                    name = "Google",
                    color = Color(0xFF4285F4)
                )
                ProviderBadge(
                    iconResId = R.drawable.ic_microsoft,
                    name = "Microsoft",
                    color = Color(0xFF00A4EF)
                )
            }
        }
    }
}
