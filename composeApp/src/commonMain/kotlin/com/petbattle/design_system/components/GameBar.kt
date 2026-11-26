package com.petbattle.design_system.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petbattle.design_system.GameUi

/** Barra de progreso del juego: fondo oscuro con contorno y relleno [color] (0..1). */
@Composable
fun GameBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(GameUi.Inset)
            .border(1.5.dp, GameUi.Outline, shape),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(shape)
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 1f), color.darken(0.75f)))),
        )
    }
}

private fun Color.darken(k: Float) = Color(red * k, green * k, blue * k, alpha)
