package com.billate.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val BillateColorScheme = lightColorScheme()

@Composable
fun BillateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BillateColorScheme,
        content = content,
    )
}
