package com.petbattle.features.defense.domain

import com.petbattle.features.defense.domain.entities.EnemyKind
import com.petbattle.shared_features.pet_combat.domain.PetCombatStats
import kotlin.math.floor

/** Números ajustables del modo tower defense. Píxeles de mapa y segundos. */
object DefenseBalance {
    // Enemigos
    const val BASE_ENEMY_SPEED = 36.0
    const val SPEED_STEP_SECONDS = 10.0

    /** Cada paso suma esta fracción de la velocidad base (crecimiento lineal). */
    const val SPEED_STEP_INCREASE = 0.2
    const val SPAWN_INTERVAL = 2.2

    /** Los enemigos aparecen en nivel 1 y suben un nivel cada este tiempo. */
    const val ENEMY_LEVEL_STEP_SECONDS = 20.0

    /** Desde este nivel de enemigo dejan de salir gusanos y solo salen mariposas. */
    const val BUTTERFLY_MIN_LEVEL = 8
    const val LIVES = 10

    // Mascotas. Daño, velocidad, confianza (acierto) y rango (alcance) son sus stats de
    // combate (ver PetCombatStatsRules).
    const val PROJECTILE_SPEED = 650.0

    /**
     * Energía por segundo que recupera una mascota retirada a la reserva en plena partida
     * (hasta la energía con la que entró al combate).
     */
    const val RESERVE_ENERGY_REGEN_PER_SECOND = 5.0

    /** Huella en el suelo que debe estar íntegramente sobre hierba. */
    const val PET_FOOTPRINT_RADIUS = 28.0
}

/** Reglas puras de una partida de defensa, fuera de la UI para poder testearlas. */
object DefenseRules {
    /** 0 durante los primeros [DefenseBalance.SPEED_STEP_SECONDS], luego +1 por paso. */
    fun speedLevel(elapsed: Double) =
        if (elapsed <= 0) 0 else floor(elapsed / DefenseBalance.SPEED_STEP_SECONDS).toInt()

    /** Velocidad actual del enemigo (px de mapa/s). Se aplica a todos los enemigos del mapa. */
    fun enemySpeed(elapsed: Double) =
        DefenseBalance.BASE_ENEMY_SPEED * (1 + DefenseBalance.SPEED_STEP_INCREASE * speedLevel(elapsed))

    /** Nivel de los enemigos que aparecen en [elapsed]: 1, luego +1 por paso. */
    fun enemyLevel(elapsed: Double) =
        1 + if (elapsed <= 0) 0 else floor(elapsed / DefenseBalance.ENEMY_LEVEL_STEP_SECONDS).toInt()

    /**
     * Tipo de los enemigos que nacen a nivel [level]: gusanos hasta el nivel anterior a
     * [DefenseBalance.BUTTERFLY_MIN_LEVEL] y, desde él, solo mariposas.
     */
    fun enemyKind(level: Int) =
        if (level >= DefenseBalance.BUTTERFLY_MIN_LEVEL) EnemyKind.butterfly else EnemyKind.worm

    /** Multiplicador de velocidad mostrado al jugador (1.0, 1.2, 1.4...). */
    fun speedMultiplier(elapsed: Double) = enemySpeed(elapsed) / DefenseBalance.BASE_ENEMY_SPEED

    /** Si un disparo acierta, dado un [roll] aleatorio uniforme en 0..1 (según la confianza). */
    fun rollHit(stats: PetCombatStats, roll: Double) = roll < stats.hitChance
}
