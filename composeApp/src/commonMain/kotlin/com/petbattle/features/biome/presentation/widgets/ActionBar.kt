package com.petbattle.features.biome.presentation.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petbattle.design_system.GameIcon
import com.petbattle.design_system.components.WoodIconButton
import com.petbattle.features.biome.presentation.game.HandTool
import com.petbattle.shared_features.pet_food.presentation.icon
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity

/** Ancho que ocupa la columna de acciones (la escena deja ese hueco libre a la izquierda). */
val ActionBarWidth: Dp = 72.dp

/**
 * Acciones: pequeña columna de botones abajo a la izquierda. Comida y limpieza no van a la
 * mascota seleccionada: ponen una herramienta en la mano ([handTool], con aro dorado) que se usa
 * tocando la escena; pulsar otra vez el botón la suelta. Dormir y defender actúan sobre [pet].
 */
@Composable
fun ActionBar(
    pet: PetEntity,
    handTool: HandTool?,
    onFeed: () -> Unit,
    onToggleSleep: () -> Unit,
    onClean: () -> Unit,
    onDefend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sleeping = pet.needs.isSleeping
    val food = (handTool as? HandTool.FoodItem)?.food
    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Con comida en la mano, el botón muestra cuál es.
        WoodIconButton(food?.icon ?: GameIcon.Feed, "Alimentar", onFeed, selected = food != null)
        WoodIconButton(if (sleeping) GameIcon.Wake else GameIcon.Sleep, if (sleeping) "Despertar" else "Dormir", onToggleSleep)
        WoodIconButton(GameIcon.Wash, "Limpiar", onClean, selected = handTool == HandTool.Water)
        WoodIconButton(GameIcon.Defend, "Defender", onDefend)
    }
}
