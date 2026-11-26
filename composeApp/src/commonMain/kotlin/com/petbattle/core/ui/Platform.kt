package com.petbattle.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Gesto/botón de atrás del sistema (Android); no hace nada en escritorio. */
@Composable
expect fun PlatformBackHandler(onBack: () -> Unit)

/** Oculta el puntero del ratón sobre este elemento si [hidden] (escritorio); Android no tiene puntero. */
expect fun Modifier.hiddenPointer(hidden: Boolean): Modifier

/** Si la pantalla es redonda (esfera de reloj): hay que dejar libres sus esquinas. */
@Composable
expect fun isRoundScreen(): Boolean
