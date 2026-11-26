package com.petbattle.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

@Composable
actual fun PlatformBackHandler(onBack: () -> Unit) = Unit

// Cursor transparente de 1x1; se crea al usarlo por primera vez (sin pantalla fallaría).
private val emptyPointer by lazy {
    PointerIcon(
        Toolkit.getDefaultToolkit().createCustomCursor(
            BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "vacío",
        ),
    )
}

actual fun Modifier.hiddenPointer(hidden: Boolean): Modifier = if (hidden) pointerHoverIcon(emptyPointer) else this

@Composable
actual fun isRoundScreen(): Boolean = false
