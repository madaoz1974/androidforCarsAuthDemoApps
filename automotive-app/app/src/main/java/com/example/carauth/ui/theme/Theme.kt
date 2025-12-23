package com.example.carauth.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

private val DarkColorScheme = darkColorScheme(
    /* Define colors here */
)

private val LightColorScheme = lightColorScheme(
    /* Define colors here */
)

@Composable
fun Theme(
    darkTheme: Boolean = true, // Automotive usually defaults to dark
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
