package com.petbattle.shared_features.pet_status.domain

/**
 * Valores de ajuste de las necesidades de la mascota (cómo bajan y suben). Cómo afectan al
 * combate está en PetCombatStatsRules. Solo datos, sin lógica: modifícalos con libertad.
 * Las reglas completas, explicadas, están en docs/reglas-de-cuidado.md.
 */
object PetNeedsBalance {
    const val MIN_STAT = 0.0
    const val MAX_STAT = 100.0

    /** Multiplica el paso del tiempo en todas las reglas. */
    const val TIME_SCALE = 1.0

    // --- Con el tiempo -------------------------------------------------------------------
    // Pérdida por hora real mientras está activa. Descansando (dormida o de noche) todas
    // bajan a SLEEP_DECAY_MULTIPLIER, salvo el descanso, que entonces se recupera.
    const val SATIETY_DECAY_PER_HOUR = 18.0
    const val HAPPINESS_DECAY_PER_HOUR = 12.0
    const val HYGIENE_DECAY_PER_HOUR = 8.0
    const val BEAK_DECAY_PER_HOUR = 6.0
    const val DIGESTION_DECAY_PER_HOUR = 5.0
    const val ENERGY_DECAY_PER_HOUR = 4.0
    const val PLUMAGE_DECAY_PER_HOUR = 3.0

    /** Descansando, todo lo demás baja más despacio. */
    const val SLEEP_DECAY_MULTIPLIER = 0.4

    /** Descansando, el descanso se recupera rápido: de vacío a lleno en ~10 segundos. */
    const val SLEEP_ENERGY_REGEN_PER_HOUR = MAX_STAT / 10 * 3600

    /**
     * Una necesidad básica (hambre, descanso, higiene) por debajo de esto está "descuidada" y
     * acelera la pérdida de diversión.
     */
    const val NEGLECT_THRESHOLD = 20.0
    const val NEGLECT_HAPPINESS_PENALTY = 0.5 // +50% por necesidad descuidada

    // --- Comida (hambre y digestión) -------------------------------------------------------

    /** Con la saciedad por debajo de esto tiene hambre: comer le sienta bien. */
    const val NEED_THRESHOLD = 75.0
    const val FEED_SATIETY = 25.0 // solo con comida de su dieta
    const val DIGESTION_GOOD_MEAL = 8.0 // su comida, con hambre
    const val DIGESTION_DISLIKED_FOOD = 15.0 // comida que no es de su dieta
    const val DIGESTION_OVEREATING = 10.0 // comer sin hambre

    // --- Interacción directa (diversión) ------------------------------------------------

    /** Diversión ganada por cada toque a la mascota. */
    const val CUDDLE_HAPPINESS = 10.0

    // --- Limpieza (salud) --------------------------------------------------------------
    const val CLEAN_HYGIENE = 45.0
    const val CLEAN_PLUMAGE = 20.0
    const val CLEAN_BEAK = 15.0

    // --- Combate (por cada disparo) ------------------------------------------------------
    const val ENERGY_PER_SHOT = 1.0
    const val HYGIENE_PER_SHOT = 0.5 // se ensucia
    const val PLUMAGE_PER_SHOT = 0.5 // se despeina
    const val BEAK_PER_SHOT = 0.5 // se afilan

    /** Granularidad (ms) y límite (ms) de la puesta al día sin conexión. */
    const val SIMULATION_STEP_MS = 60_000L
    const val MAX_OFFLINE_CATCH_UP_MS = 7L * 24 * 3600 * 1000
}
