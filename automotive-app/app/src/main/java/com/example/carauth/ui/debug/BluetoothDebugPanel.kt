package com.example.carauth.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.carauth.BuildConfig
import com.example.carauth.bluetooth.BluetoothConnectionState
import com.example.carauth.bluetooth.FakeBluetoothAuthService

/**
 * Debug panel for testing Bluetooth authentication flow on emulator.
 * Only visible in debug builds when USE_FAKE_BLUETOOTH is true.
 */
@Composable
fun BluetoothDebugPanel(
    fakeBluetoothService: FakeBluetoothAuthService?,
    modifier: Modifier = Modifier
) {
    // Only show in debug builds with fake Bluetooth
    if (!BuildConfig.DEBUG || !BuildConfig.USE_FAKE_BLUETOOTH || fakeBluetoothService == null) {
        return
    }
    
    val isAdvertising by fakeBluetoothService.isAdvertising.collectAsState()
    val connectionState by fakeBluetoothService.connectionState.collectAsState()
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFE0B2) // Orange background
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🔧 Bluetooth Debug Panel",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Status display
            Text(
                "Status: ${if (isAdvertising) "📡 Advertising" else "⏸ Idle"}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray
            )
            Text(
                "Connection: ${formatConnectionState(connectionState)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Simulate token reception
            Button(
                onClick = {
                    fakeBluetoothService.simulateTokenReceived(
                        "debug_access_token_${System.currentTimeMillis()}"
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50) // Green
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📱 Simulate Token from Phone")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Connection simulation buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { fakeBluetoothService.simulateConnection() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Connect")
                }
                OutlinedButton(
                    onClick = { fakeBluetoothService.simulateDisconnection() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disconnect")
                }
            }
        }
    }
}

private fun formatConnectionState(state: BluetoothConnectionState): String {
    return when (state) {
        is BluetoothConnectionState.Disconnected -> "❌ Disconnected"
        is BluetoothConnectionState.Connected -> "✅ Connected (${state.deviceAddress})"
    }
}
