package com.petbattle.design_system.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.petbattle.design_system.GameIcon
import org.jetbrains.compose.resources.painterResource

@Composable
fun GameIconImage(icon: GameIcon, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(painterResource(icon.res), contentDescription, modifier.size(size))
}
