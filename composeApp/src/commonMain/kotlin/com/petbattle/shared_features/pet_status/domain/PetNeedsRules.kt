package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_status.domain.entities.PetNeedsEntity
import kotlin.math.min

/**
 * Reglas puras de las necesidades: `needs in -> needs out`. Sin UI, sin E/S, sin reloj. El
 * mismo [advanceTo] sirve tanto para el tick en vivo como para la puesta al día offline.
 *
 * Qué mueve cada necesidad (valores en PetNeedsBalance):
 * - Con el tiempo bajan todas las guardadas, cada una a su ritmo (el descanso se recupera
 *   descansando).
 * - Comida: hambre y digestión ([offerFood]). Comer no ensucia.
 * - Sueño: recupera el descanso ([toggleSleep]; de noche descansan solas).
 * - Interacción directa: diversión ([cuddle]).
 * - Limpieza: higiene, plumaje y pico y garras ([clean]).
 * - Combate ([fire]): gasta descanso, ensucia y despeina, y afila el pico y las garras.
 */
object PetNeedsRules {
    private const val MS_PER_HOUR = 3_600_000.0

    /**
     * Simula todo lo ocurrido entre `lastUpdatedAt` y [now]. [isNight] dice si un instante es
     * de noche: de noche descansa aunque no esté dormida (recupera descanso y todo baja más
     * despacio), pero sigue despierta para que se pueda jugar con ella.
     */
    fun advanceTo(
        pet: PetNeedsEntity,
        now: Long,
        isNight: (Long) -> Boolean = { false },
    ): PetNeedsEntity {
        var elapsed = now - pet.lastUpdatedAt
        // El reloj retrocedió: no se "des-decae", solo se reancla.
        if (elapsed <= 0) return pet.copy(lastUpdatedAt = now)
        if (elapsed > PetNeedsBalance.MAX_OFFLINE_CATCH_UP_MS) {
            elapsed = PetNeedsBalance.MAX_OFFLINE_CATCH_UP_MS
        }
        var current = pet
        var remaining = elapsed
        while (remaining > 0) {
            val chunk = min(PetNeedsBalance.SIMULATION_STEP_MS, remaining)
            current = step(current, chunk / MS_PER_HOUR, night = isNight(now - remaining))
            remaining -= chunk
        }
        return current.copy(lastUpdatedAt = now)
    }

    private fun step(pet: PetNeedsEntity, realHours: Double, night: Boolean): PetNeedsEntity {
        val h = realHours * PetNeedsBalance.TIME_SCALE
        // Dormida o de noche, descansa: el descanso se recupera y todo lo demás baja más despacio.
        val resting = pet.isSleeping || night
        val k = h * (if (resting) PetNeedsBalance.SLEEP_DECAY_MULTIPLIER else 1.0)
        val neglected = listOf(pet.satiety, pet.energy, pet.hygiene)
            .count { it < PetNeedsBalance.NEGLECT_THRESHOLD }
        val happinessRate = PetNeedsBalance.HAPPINESS_DECAY_PER_HOUR *
            (1 + neglected * PetNeedsBalance.NEGLECT_HAPPINESS_PENALTY)
        val next = pet.copy(
            satiety = clamp(pet.satiety - PetNeedsBalance.SATIETY_DECAY_PER_HOUR * k),
            energy = clamp(
                if (resting) pet.energy + PetNeedsBalance.SLEEP_ENERGY_REGEN_PER_HOUR * h
                else pet.energy - PetNeedsBalance.ENERGY_DECAY_PER_HOUR * h,
            ),
            happiness = clamp(pet.happiness - happinessRate * k),
            hygiene = clamp(pet.hygiene - PetNeedsBalance.HYGIENE_DECAY_PER_HOUR * k),
            plumage = clamp(pet.plumage - PetNeedsBalance.PLUMAGE_DECAY_PER_HOUR * k),
            digestion = clamp(pet.digestion - PetNeedsBalance.DIGESTION_DECAY_PER_HOUR * k),
            beakAndClaws = clamp(pet.beakAndClaws - PetNeedsBalance.BEAK_DECAY_PER_HOUR * k),
        )
        // Dormida sigue dormida aunque ya haya descansado del todo: solo se despierta a mano
        // (botón de despertar o al elegir otra acción, ver [wake]).
        return next
    }

    // Acciones del jugador. `canX` permite a la UI deshabilitar botones; `x` no hace nada
    // si la acción no está permitida, así que quien llama no puede corromper el estado.

    /** Siempre se le puede dar de comer despierta (aunque no tenga hambre: le sentará mal). */
    fun canFeed(pet: PetNeedsEntity) = !pet.isSleeping

    /** Tiene hambre: comer ahora le sienta bien. */
    fun isHungry(pet: PetNeedsEntity) = pet.satiety < PetNeedsBalance.NEED_THRESHOLD

    /** Se come algo de su dieta: sacia. */
    fun feed(pet: PetNeedsEntity) =
        if (!canFeed(pet)) pet else pet.copy(satiety = clamp(pet.satiety + PetNeedsBalance.FEED_SATIETY))

    /**
     * Ofrecer [food] a una mascota cuya dieta es [diet]. Siempre se la come, pero:
     * - de su dieta y con hambre: sacia y mejora la digestión;
     * - de su dieta sin hambre: sacia, pero empeora la digestión;
     * - fuera de su dieta: no sacia y empeora la digestión (más aún si encima no tenía hambre).
     * Dormida no come: necesidades sin cambios y resultado `null`.
     */
    fun offerFood(pet: PetNeedsEntity, food: Food, diet: List<Food>): Pair<PetNeedsEntity, FeedResult?> {
        if (!canFeed(pet)) return pet to null
        val hungry = isHungry(pet)
        val overeating = if (hungry) 0.0 else PetNeedsBalance.DIGESTION_OVEREATING
        if (food !in diet) {
            return digest(pet, -PetNeedsBalance.DIGESTION_DISLIKED_FOOD - overeating) to FeedResult.disliked
        }
        val fed = feed(pet)
        return if (hungry) digest(fed, PetNeedsBalance.DIGESTION_GOOD_MEAL) to FeedResult.eaten
        else digest(fed, -overeating) to FeedResult.overate
    }

    fun toggleSleep(pet: PetNeedsEntity) = pet.copy(isSleeping = !pet.isSleeping)

    /** La despierta (si dormía): pasa al elegir cualquier otra acción con ella. */
    fun wake(pet: PetNeedsEntity) = if (pet.isSleeping) pet.copy(isSleeping = false) else pet

    /** Se puede limpiar despierta si a alguna de sus necesidades de salud le falta algo. */
    fun canClean(pet: PetNeedsEntity) = !pet.isSleeping &&
        (pet.hygiene < PetNeedsBalance.MAX_STAT || pet.plumage < PetNeedsBalance.MAX_STAT ||
            pet.beakAndClaws < PetNeedsBalance.MAX_STAT)

    /** Limpiarla mejora toda su salud: higiene, plumaje y pico y garras. */
    fun clean(pet: PetNeedsEntity) = if (!canClean(pet)) pet else pet.copy(
        hygiene = clamp(pet.hygiene + PetNeedsBalance.CLEAN_HYGIENE),
        plumage = clamp(pet.plumage + PetNeedsBalance.CLEAN_PLUMAGE),
        beakAndClaws = clamp(pet.beakAndClaws + PetNeedsBalance.CLEAN_BEAK),
    )

    /** Tocar a la mascota (interacción directa): la forma de hacerla más feliz. */
    fun cuddle(pet: PetNeedsEntity) =
        if (pet.isSleeping) pet else pet.copy(happiness = clamp(pet.happiness + PetNeedsBalance.CUDDLE_HAPPINESS))

    /** Un disparo en combate: cansa, ensucia y despeina, y afila el pico y las garras. */
    fun fire(pet: PetNeedsEntity) = spendEnergy(pet, PetNeedsBalance.ENERGY_PER_SHOT).let {
        it.copy(
            hygiene = clamp(it.hygiene - PetNeedsBalance.HYGIENE_PER_SHOT),
            plumage = clamp(it.plumage - PetNeedsBalance.PLUMAGE_PER_SHOT),
            beakAndClaws = clamp(it.beakAndClaws + PetNeedsBalance.BEAK_PER_SHOT),
        )
    }

    /** Cansancio por luchar. */
    fun spendEnergy(pet: PetNeedsEntity, amount: Double) =
        if (amount <= 0) pet else pet.copy(energy = clamp(pet.energy - amount))

    private fun digest(pet: PetNeedsEntity, delta: Double) = pet.copy(digestion = clamp(pet.digestion + delta))

    private fun clamp(v: Double) = v.coerceIn(PetNeedsBalance.MIN_STAT, PetNeedsBalance.MAX_STAT)
}
