package com.example.carcompanion.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class QRCodeAnalyzer(
    private val onQRCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    
    // Debounce state
    private var lastScannedValue: String? = null
    private var lastScannedTime: Long = 0
    private val debounceInterval = 2000L // 2 seconds debounce

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val currentTime = System.currentTimeMillis()
                for (barcode in barcodes) {
                    barcode.rawValue?.let { value ->
                        // Only trigger callback if different QR or debounce interval passed
                        if (value != lastScannedValue || 
                            currentTime - lastScannedTime > debounceInterval) {
                            lastScannedValue = value
                            lastScannedTime = currentTime
                            onQRCodeScanned(value)
                        }
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}

