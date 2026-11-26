package com.petbattle.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformBackHandler(onBack: () -> Unit) = BackHandler(onBack = onBack)

actual fun Modifier.hiddenPointer(hidden: Boolean): Modifier = this

@Composable
actual fun isRoundScreen(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.isScreenRound
