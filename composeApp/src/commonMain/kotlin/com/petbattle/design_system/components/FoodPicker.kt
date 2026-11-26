package com.petbattle.design_system.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.petbattle.design_system.GameIcon
import com.petbattle.design_system.GameUi
import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_food.presentation.icon
import com.petbattle.shared_features.pet_food.presentation.label

/** Comidas por fila del menú (7 comidas: 4 arriba y 3 abajo, centradas). */
private const val FOODS_PER_ROW = 4

/** En pantallas estrechas (reloj) van de dos en dos, con scroll. */
private const val FOODS_PER_ROW_NARROW = 2
private val NarrowScreen = 360.dp

/** Lado máximo de cada casilla (en pantallas pequeñas encoge para caber). */
private val TileSide = 104.dp
private val TileGap = 10.dp

/**
 * Menú de comidas al pulsar "Alimentar": un modal con casillas cuadradas de madera en dos
 * filas. La elegida se queda en la mano para dejarla en el suelo. Muestra todas: averiguar qué
 * le gusta a cada especie forma parte del juego.
 */
@Composable
fun FoodPickerSheet(onPicked: (Food) -> Unit, onDismiss: () -> Unit) {
    // Sin el ancho máximo por defecto de los diálogos, que recortaría la primera fila.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val narrow = maxWidth < NarrowScreen
            val perRow = if (narrow) FOODS_PER_ROW_NARROW else FOODS_PER_ROW
            // Panel (16 dp de margen interior + 6 dp de borde) y un respiro a cada lado.
            val available = maxWidth - (16.dp + 6.dp + 12.dp) * 2 - TileGap * (perRow - 1)
            val side = minOf(TileSide, available / perRow)
            WoodPanel {
                Column(
                    Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (narrow) "¿Qué comida?" else "¿Qué comida dejas en el suelo?",
                            style = GameUi.text(if (narrow) 15.sp else 18.sp),
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        WoodIconButton(GameIcon.Close, "Cerrar", onDismiss, size = if (narrow) 32.dp else 40.dp)
                    }
                    for (row in Food.entries.chunked(perRow)) {
                        Row(Modifier.padding(top = TileGap), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                            row.forEach { food -> FoodTile(food, side) { onPicked(food) } }
                        }
                    }
                }
            }
        }
    }
}

/** Casilla cuadrada: icono grande y el nombre debajo, sobre madera oscura. */
@Composable
private fun FoodTile(food: Food, side: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(side)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(GameUi.WoodDark, GameUi.Inset)))
            .border(3.dp, GameUi.Outline, shape)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GameIconImage(food.icon, side * 0.48f, contentDescription = food.label)
            Text(
                food.label,
                style = GameUi.text(12.sp),
                maxLines = 2,
                lineHeight = 13.sp,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
