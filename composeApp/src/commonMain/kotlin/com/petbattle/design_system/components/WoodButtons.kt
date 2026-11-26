package com.petbattle.design_system.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.design_system.GameIcon
import com.petbattle.design_system.GameUi

/**
 * Botón redondo de madera con un icono del juego. Sin [onClick] se ve apagado; con [selected]
 * lleva un aro dorado (p. ej. la herramienta que se tiene en la mano).
 */
@Composable
fun WoodIconButton(
    icon: GameIcon,
    contentDescription: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    badge: GameIcon? = null,
    selected: Boolean = false,
) {
    Box(modifier.size(size)) {
        if (selected) Box(Modifier.align(Alignment.Center).requiredSize(size + 10.dp).border(4.dp, GameUi.Gold, CircleShape))
        WoodPanel(
            Modifier
                .matchParentSize()
                .alpha(if (onClick == null) 0.45f else 1f)
                .clickable(
                    enabled = onClick != null,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onClick?.invoke() },
                ),
            shape = CircleShape,
        ) {
            GameIconImage(icon, size * 0.62f, Modifier.align(Alignment.Center), contentDescription)
        }
        if (badge != null) GameIconImage(badge, size * 0.38f, Modifier.align(Alignment.BottomEnd))
    }
}

/** Botón rectangular de madera con texto (y un icono opcional). */
@Composable
fun WoodButton(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: GameIcon? = null,
) {
    WoodPanel(
        modifier
            .alpha(if (onClick == null) 0.5f else 1f)
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() }),
        shape = RoundedCornerShape(14.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) GameIconImage(icon, 28.dp, Modifier.padding(end = 8.dp))
            Text(text, style = GameUi.text(16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
