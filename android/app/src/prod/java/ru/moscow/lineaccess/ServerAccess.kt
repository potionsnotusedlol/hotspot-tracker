package ru.moscow.lineaccess

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable

@Composable
internal fun ComponentActivity.WithServerAccess(content: @Composable () -> Unit) = content()
