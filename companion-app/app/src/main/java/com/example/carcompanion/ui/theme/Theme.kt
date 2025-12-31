package com.example.carcompanion.ui.theme

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
    darkTheme: Boolean = false, // Phone usually defaults depending on system, set default here
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
