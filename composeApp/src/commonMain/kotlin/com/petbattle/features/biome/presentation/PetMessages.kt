package com.petbattle.features.biome.presentation

import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_food.presentation.label
import com.petbattle.shared_features.pet_species.presentation.label
import com.petbattle.shared_features.pet_status.application.PetController
import com.petbattle.shared_features.pet_status.domain.FeedResult
import com.petbattle.shared_features.pet_status.domain.LifeEvent
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity

// Avisos breves que comparten el bioma y el modo cuidado.

/** Aviso de un huevo puesto o de una eclosión. */
fun lifeEventMessage(event: LifeEvent): String = when (event) {
    is LifeEvent.EggLaid -> "¡${event.parents.first} y ${event.parents.second} han puesto un huevo!"
    is LifeEvent.Hatched -> "¡Ha nacido ${event.name}!"
}

/** Por qué no se ha podido limpiar a [pet]. */
fun cleanRefusedMessage(pet: PetEntity): String =
    if (pet.needs.isSleeping) "${pet.name} está durmiendo" else "${pet.name} no necesita un baño"

/** Da de comer [food] a [eater] con el controlador y devuelve el aviso del resultado (o null). */
fun feedAndDescribe(controller: PetController, eater: PetEntity, food: Food): String? =
    when (controller.feedPet(eater.id, food)) {
        FeedResult.eaten -> "¡A ${eater.name} le encanta! Ñam ñam"
        FeedResult.overate -> "${eater.name} come sin hambre: le sienta mal"
        FeedResult.disliked -> "${eater.name} se come ${food.label.lowercase()} a disgusto"
        FeedResult.grew -> {
            val adult = controller.gameState.value?.byId(eater.id)?.species
            "¡${eater.name} ha crecido! Ahora es: ${adult?.label ?: "?"}"
        }
        null -> null
    }
