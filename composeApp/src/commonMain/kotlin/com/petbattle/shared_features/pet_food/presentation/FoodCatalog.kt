package com.petbattle.shared_features.pet_food.presentation

import com.petbattle.design_system.GameIcon
import com.petbattle.shared_features.pet_food.domain.Food

/** Datos de presentación de cada comida: nombre visible e icono. */
val Food.label: String
    get() = when (this) {
        Food.seeds -> "Semillas"
        Food.nuts -> "Frutos secos"
        Food.fruit -> "Fruta"
        Food.insects -> "Insectos"
        Food.worms -> "Gusanos"
        Food.nectar -> "Néctar"
        Food.carrion -> "Carroña"
    }

val Food.icon: GameIcon
    get() = when (this) {
        Food.seeds -> GameIcon.Seeds
        Food.nuts -> GameIcon.Nuts
        Food.fruit -> GameIcon.Fruit
        Food.insects -> GameIcon.Insects
        Food.worms -> GameIcon.Worms
        Food.nectar -> GameIcon.Nectar
        Food.carrion -> GameIcon.Carrion
    }
