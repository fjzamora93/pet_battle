package com.petbattle.features.biome.presentation.game

import com.petbattle.shared_features.pet_food.domain.Food

/**
 * Lo que el jugador lleva "en la mano" sobre la escena: el cursor se convierte en ello y cambia
 * lo que hace un toque.
 */
sealed interface HandTool {
    /** Agua: tocar una mascota la limpia. */
    data object Water : HandTool

    /** Comida: tocar el suelo la deja allí para quien tenga hambre y le guste. */
    data class FoodItem(val food: Food) : HandTool
}

/**
 * Comida dejada en el suelo del bioma (no se guarda en la partida). [x] es la posición en la
 * zona de paseo (0..1) y [depth] la profundidad (0 = delante, 1 = al fondo), como las mascotas.
 * [droppedAt] es el instante (ms) en que se dejó: desaparece pasado un rato si nadie la come.
 */
data class GroundFood(
    val id: Long,
    val food: Food,
    val x: Double,
    val depth: Double,
    val droppedAt: Long,
)
