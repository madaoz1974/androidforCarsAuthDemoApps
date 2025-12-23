package com.example.carcompanion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.carcompanion.scanner.ScanResult

@Composable
fun ScanOverlay(
    scanResult: ScanResult,
    modifier: Modifier = Modifier
) {
    val borderColor = when (scanResult) {
        is ScanResult.Success -> Color.Green
        is ScanResult.Error, is ScanResult.InvalidQRCode -> Color.Red
        else -> Color.White
    }

    Canvas(modifier = modifier) {
        val scanFrameSize = 250.dp.toPx()
        val frameLeft = (size.width - scanFrameSize) / 2
        val frameTop = (size.height - scanFrameSize) / 2
        
        val frameRect = Rect(
            offset = Offset(frameLeft, frameTop),
            size = Size(scanFrameSize, scanFrameSize)
        )
        
        // Darken background
        val backgroundPath = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
        }
        val framePath = Path().apply {
             addRoundRect(RoundRect(frameRect, CornerRadius(16.dp.toPx())))
        }
        
        val overlayPath = Path.combine(
            PathOperation.Difference,
            backgroundPath,
            framePath
        )
        
        drawPath(
            path = overlayPath,
            color = Color.Black.copy(alpha = 0.6f)
        )
        
        // Draw Frame
        drawPath(
            path = framePath,
            color = borderColor,
            style = Stroke(width = 4.dp.toPx())
        )
    }
}
