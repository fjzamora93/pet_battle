package com.petbattle.shared_features.pet_combat.domain

import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_status.domain.PetDerivedNeeds
import com.petbattle.shared_features.pet_status.domain.PetNeedsBalance
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity

/** Valores base de los stats de combate. Solo datos: modifícalos con libertad. */
object PetCombatBalance {
    /** Daño de una mascota de nivel 0. */
    const val LEVEL_ZERO_DAMAGE = 1.0

    /** Daño por nivel; igual a los PV del enemigo por nivel (nivel N mata a N de un golpe). */
    const val DAMAGE_PER_LEVEL = 1.2

    /** Segundos entre disparos con la velocidad al máximo. */
    const val BASE_FIRE_INTERVAL = 0.7

    const val MAX_CONFIDENCE = 100.0

    /**
     * Velocidad que quita el descanso a 0 (fracción del total): 0,5 = la mitad, es decir,
     * un 0,5 % por cada punto que falta. No hay corte: sin descanso sigue atacando.
     */
    const val ENERGY_SPEED_PENALTY = 0.5

    /** Velocidad que quitan la digestión y el clima a 0 (cada uno): un tercio del total. */
    const val DIGESTION_SPEED_PENALTY = 1.0 / 3.0
    const val CLIMATE_SPEED_PENALTY = 1.0 / 3.0

    /** Alcance de referencia (px de mapa); cada especie tiene el suyo (ver [range]). */
    const val DEFAULT_RANGE = 300.0

    /**
     * Alcance de cada especie, según su tamaño (longitud aproximada del pájaro real): las
     * grandes llegan más lejos y las pequeñas menos.
     */
    fun range(species: PetSpecies): Double = when (species) {
        PetSpecies.crow -> 380.0 // ~45 cm
        PetSpecies.magpie -> 370.0 // ~45 cm (con la cola)
        PetSpecies.kestrel -> 350.0 // ~34 cm
        PetSpecies.blueJay -> 340.0 // ~34 cm
        PetSpecies.whiteDove -> 330.0 // ~30 cm
        PetSpecies.beeEater -> 310.0 // ~28 cm
        PetSpecies.cardinal -> 290.0 // ~22 cm
        PetSpecies.woodThrush -> 285.0 // ~20 cm
        PetSpecies.chucao -> 280.0 // ~19 cm
        PetSpecies.cedarWaxwing -> 270.0 // ~17 cm
        PetSpecies.redRobin -> 260.0 // ~14 cm
        PetSpecies.houseFinch -> 255.0 // ~14 cm
        PetSpecies.chickadee -> 245.0 // ~12 cm
        PetSpecies.hummingbird -> 230.0 // ~9 cm
        PetSpecies.chick -> 220.0 // no combate
    }
}

/**
 * Cómo se calculan los stats de combate. Los totales dependen del nivel; lo que rinde ahora
 * es el total multiplicado por la media de tres necesidades (todas al 100% = el total):
 *
 * | stat de combate     | necesidades que lo suben o bajan          |
 * |---------------------|-------------------------------------------|
 * | daño                | hambre (saciedad), plumaje, pico y garras |
 * | velocidad de ataque | descanso (energía), digestión, clima      |
 * | confianza (acierto) | diversión, social, higiene                |
 *
 * La velocidad no usa la media: cada necesidad resta su propia penalización por punto que
 * falta (descanso 0,5 %, digestión y clima 0,33 %; ver PetCombatBalance). Sin descanso no
 * hay corte: ataca a la mitad. El rango (alcance) no depende de las necesidades: es fijo
 * por especie (PetCombatBalance.range).
 */
object PetCombatStatsRules {
    /** Daño total de una mascota de nivel [level] (con todas sus necesidades al máximo). */
    fun maxDamage(level: Int) =
        if (level <= 0) PetCombatBalance.LEVEL_ZERO_DAMAGE else PetCombatBalance.DAMAGE_PER_LEVEL * level

    /** El mayor alcance de todas las especies (referencia para comparar rangos). */
    val maxRange: Double get() = PetSpecies.entries.maxOf { PetCombatBalance.range(it) }

    /** Ataques por segundo totales (de momento no depende del nivel). */
    val maxAttacksPerSecond get() = 1.0 / PetCombatBalance.BASE_FIRE_INTERVAL

    /** Stats de combate de [pet] ahora; [socialization] depende de su bioma (PetDerivedNeeds). */
    fun statsFor(pet: PetEntity, socialization: Double): PetCombatStats {
        val n = pet.needs
        val damageFactor = mean(n.satiety, n.plumage, n.beakAndClaws)
        val speedFactor = speedFactor(n.energy, n.digestion, PetDerivedNeeds.climate(pet))
        val maxDamage = maxDamage(pet.progression.level)
        return PetCombatStats(
            damage = maxDamage * damageFactor,
            maxDamage = maxDamage,
            attacksPerSecond = maxAttacksPerSecond * speedFactor,
            maxAttacksPerSecond = maxAttacksPerSecond,
            confidence = confidence(pet, socialization),
            range = PetCombatBalance.range(pet.species),
        )
    }

    /** Confianza (0..100): el equilibrio entre diversión, social e higiene. */
    fun confidence(pet: PetEntity, socialization: Double) =
        PetCombatBalance.MAX_CONFIDENCE * mean(pet.needs.happiness, socialization, pet.needs.hygiene)

    /**
     * Fracción (0..1) de la velocidad total que rinde ahora: el total menos lo que resta cada
     * necesidad según lo que le falta (penalizaciones de PetCombatBalance).
     */
    fun speedFactor(energy: Double, digestion: Double, climate: Double): Double {
        fun missing(need: Double) = 1.0 - (need / PetNeedsBalance.MAX_STAT).coerceIn(0.0, 1.0)
        val penalty = PetCombatBalance.ENERGY_SPEED_PENALTY * missing(energy) +
            PetCombatBalance.DIGESTION_SPEED_PENALTY * missing(digestion) +
            PetCombatBalance.CLIMATE_SPEED_PENALTY * missing(climate)
        return (1.0 - penalty).coerceIn(0.0, 1.0)
    }

    /** Media de necesidades como fracción 0..1. */
    private fun mean(vararg needs: Double) =
        (needs.sum() / needs.size / PetNeedsBalance.MAX_STAT).coerceIn(0.0, 1.0)
}
