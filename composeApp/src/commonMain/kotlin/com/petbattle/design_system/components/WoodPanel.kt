package com.petbattle.design_system.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.petbattle.design_system.GameUi

/** Panel de madera con contorno oscuro. */
@Composable
fun WoodPanel(
    modifier: Modifier = Modifier,
    shape: Shape = GameUi.PanelShape,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(GameUi.Wood)
            .border(3.dp, GameUi.Outline, shape)
            .padding(3.dp)
            // Filete claro interior: da el canto de la tabla.
            .border(1.5.dp, Color(0x66FFF1D6), shape),
        content = content,
    )
}
