package com.faradaymicrosystems.nobodygenerator.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Teal = Color(0xFF2BB59A)
private val Ink = Color(0xFF0B1F2A)

@Composable
fun NobodyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF7FE0C9), secondary = Teal, background = Ink, surface = Ink)
        else -> lightColorScheme(primary = Color(0xFF00695C), secondary = Teal)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
