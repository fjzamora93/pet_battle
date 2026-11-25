package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.biomes.domain.isPlayable
import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_skills.domain.PetProgression
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import com.petbattle.shared_features.pet_status.domain.entities.GameStateEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetNeedsEntity
import kotlin.random.Random

/** Motivo por el que no se puede usar un nombre. La UI asigna un mensaje a cada caso. */
enum class PetNameError { empty, tooLong, taken }

/**
 * Reglas puras sobre toda la partida.
 * - El jugador elige bioma, nunca especie. Una mascota nueva es siempre un polluelo del bioma
 *   actual (hay que ponerle nombre); varias mascotas pueden convivir en el mismo bioma.
 * - Economía: cada enemigo derrotado da [GOLD_PER_ENEMY] de oro. Un huevo cuesta [EGG_PRICE]
 *   (el primero es gratis) y desbloquear un bioma [BIOME_PRICE] (la pradera viene gratis).
 * - Solo se puede ir a biomas comprados; cada uno muestra las mascotas que viven allí.
 * - El tiempo es absoluto y común: todas las mascotas viven en tiempo real (sus necesidades
 *   avanzan igual estén seleccionadas o no, y en cualquier bioma).
 */
object GameRules {
    const val MAX_NAME_LENGTH = 16
    const val GOLD_PER_ENEMY = 1
    const val EGG_PRICE = 10
    const val BIOME_PRICE = 20

    /** Lo que dura la comida dejada en el suelo si nadie se la come. */
    const val GROUND_FOOD_LIFETIME_MS = 10_000L

    /** Lo que cuesta el próximo huevo (0 si aún no se ha usado el gratis). */
    fun eggPrice(gameState: GameStateEntity) = if (gameState.freeEggUsed) EGG_PRICE else 0

    /** Oro que falta para el próximo huevo (0 si alcanza). */
    fun goldMissingForEgg(gameState: GameStateEntity) = (eggPrice(gameState) - gameState.gold).coerceAtLeast(0)

    /** Se puede comprar si es jugable (Biome.isPlayable), aún no es tuyo y alcanza el oro. */
    fun canBuyBiome(gameState: GameStateEntity, biome: Biome) =
        biome.isPlayable && !gameState.isUnlocked(biome) && gameState.gold >= BIOME_PRICE

    /** Compra [biome] (sin viajar a él). No hace nada si ya está comprado o no alcanza el oro. */
    fun buyBiome(gameState: GameStateEntity, biome: Biome): GameStateEntity =
        if (!canBuyBiome(gameState, biome)) gameState
        else gameState.copy(gold = gameState.gold - BIOME_PRICE, unlockedBiomes = gameState.unlockedBiomes + biome)

    fun awardGold(gameState: GameStateEntity, amount: Int) =
        if (amount <= 0) gameState else gameState.copy(gold = gameState.gold + amount)

    fun normalizeName(raw: String) = raw.trim().replace(Regex("\\s+"), " ")

    /** `null` si [raw] es un nombre válido para una mascota nueva en [gameState]. */
    fun validateName(gameState: GameStateEntity, raw: String): PetNameError? {
        val name = normalizeName(raw)
        if (name.isEmpty()) return PetNameError.empty
        if (name.length > MAX_NAME_LENGTH) return PetNameError.tooLong
        if (gameState.pets.any { it.name.equals(name, ignoreCase = true) }) return PetNameError.taken
        return null
    }

    /** Hace falta estar en un bioma y tener oro para el huevo. */
    fun canHatch(gameState: GameStateEntity) = gameState.currentBiome != null && goldMissingForEgg(gameState) == 0

    /**
     * Compra un huevo y añade el polluelo al bioma actual, activo, con su rareza sorteada
     * (TierRules.purchased). Quien llama debe validar antes.
     */
    fun hatch(gameState: GameStateEntity, name: String, now: Long, random: Random = Random.Default): GameStateEntity {
        val biome = requireNotNull(gameState.currentBiome) { "no biome chosen" }
        require(canHatch(gameState)) { "not enough gold" }
        val error = validateName(gameState, name)
        require(error == null) { "invalid name: $error" }
        // El número de mascotas evita ids repetidos si se crían dos en el mismo milisegundo.
        val id = "chick-$now-${gameState.pets.size}"
        val pet = PetEntity.hatch(name = normalizeName(name), biome = biome, now = now, id = id)
            .copy(tier = TierRules.purchased(random))
        return gameState.copy(
            pets = gameState.pets + pet,
            activeId = pet.id,
            gold = gameState.gold - eggPrice(gameState),
            freeEggUsed = true,
        )
    }

    /** Viaja a [biome] (si está comprado): se activa su primera mascota, o ninguna si está vacío. */
    fun enterBiome(gameState: GameStateEntity, biome: Biome): GameStateEntity {
        if (biome == gameState.currentBiome || !gameState.isUnlocked(biome) || !biome.isPlayable) return gameState
        val first = gameState.petsIn(biome).firstOrNull { !it.isEgg }
        val moved = gameState.copy(currentBiome = biome, activeId = null)
        return if (first == null) moved else switchTo(moved, first.id)
    }

    /**
     * Libera a la mascota [petId]: desaparece para siempre (su nombre vuelve a quedar libre).
     * Si era la activa, pasa a serlo otra del mismo bioma, o ninguna.
     */
    fun release(gameState: GameStateEntity, petId: String): GameStateEntity {
        val pet = gameState.byId(petId) ?: return gameState
        val rest = gameState.copy(pets = gameState.pets - pet)
        if (petId != gameState.activeId) return rest
        val next = rest.petsIn(pet.biome).firstOrNull { !it.isEgg } ?: return rest.copy(activeId = null)
        return switchTo(rest.copy(activeId = null), next.id)
    }

    /** Selecciona [petId] (y viaja a su bioma). Su tiempo ya corría: no se toca. */
    fun switchTo(gameState: GameStateEntity, petId: String): GameStateEntity {
        val pet = gameState.byId(petId) ?: return gameState
        // Los huevos no se pueden seleccionar.
        if (petId == gameState.activeId || pet.isEgg) return gameState
        return gameState.copy(activeId = petId, currentBiome = pet.biome)
    }

    /** Ofrece [food] a la mascota activa (ver [feedPet]). */
    fun feedActive(gameState: GameStateEntity, food: Food, random: Random): Pair<GameStateEntity, FeedResult?> {
        val pet = gameState.active ?: return gameState to null
        return feedPet(gameState, pet.id, food, random)
    }

    /**
     * La mascota [petId] se come [food]. Si es un polluelo, cuenta la comida y, al llegar a
     * GrowthRules.MEALS_TO_GROW, crece. `null` si ahora no puede comer (o no existe).
     */
    fun feedPet(gameState: GameStateEntity, petId: String, food: Food, random: Random): Pair<GameStateEntity, FeedResult?> {
        val pet = gameState.byId(petId) ?: return gameState to null
        if (pet.isEgg || !PetNeedsRules.canFeed(pet.needs)) return gameState to null
        val (needs, result) = PetNeedsRules.offerFood(pet.needs, food, pet.diet)
        if (result == null) return gameState to null
        // Siempre se la come: el polluelo cuenta cualquier comida para crecer.
        val fed = GrowthRules.recordMeal(pet.copy(needs = needs), food)
        if (GrowthRules.shouldGrow(fed)) return gameState.replace(GrowthRules.grow(fed, random)) to FeedResult.grew
        return gameState.replace(fed) to result
    }

    /**
     * Si la mascota irá a por [food] dejada en el suelo: despierta y si es de su dieta (los
     * polluelos comen de todo), tenga hambre o no. Sin hambre se la come igual, a costa de la
     * digestión (ver PetNeedsRules.offerFood).
     */
    fun wantsGroundFood(pet: PetEntity, food: Food) =
        !pet.isEgg && PetNeedsRules.canFeed(pet.needs) && food in pet.diet

    /** Aplica [change] al estado de la mascota [petId] (no hace nada si no existe o es un huevo). */
    fun updateNeeds(
        gameState: GameStateEntity,
        petId: String,
        change: (PetNeedsEntity) -> PetNeedsEntity,
    ): GameStateEntity {
        val pet = gameState.byId(petId)?.takeIf { !it.isEgg } ?: return gameState
        return gameState.replace(pet.copy(needs = change(pet.needs)))
    }

    /**
     * Pone al día partidas antiguas: sin bioma actual, se coloca al jugador en el de su mascota
     * activa (o en la pradera); se dan por comprados la pradera y los biomas donde ya vive
     * alguna mascota, y si ya había mascotas el huevo gratis se considera usado. Las mascotas
     * que vivían en biomas que aún no se pueden jugar (Biome.isPlayable) se mudan a la pradera,
     * para que no queden abandonadas donde no se puede ir.
     */
    fun migrate(gameState: GameStateEntity): GameStateEntity {
        val pets = gameState.pets.map { if (it.biome.isPlayable) it else it.copy(biome = Biome.grassland) }
        val wanted = gameState.currentBiome ?: (gameState.active ?: gameState.pets.firstOrNull())?.biome ?: Biome.grassland
        val current = if (wanted.isPlayable) wanted else Biome.grassland
        val unlocked = (gameState.unlockedBiomes + Biome.grassland + current + gameState.pets.map { it.biome }).distinct()
        return gameState.copy(
            pets = pets,
            currentBiome = current,
            unlockedBiomes = unlocked,
            freeEggUsed = gameState.freeEggUsed || gameState.pets.isNotEmpty(),
        )
    }

    /** Aplica [change] a la mascota activa (no hace nada si no hay). */
    fun updateActive(gameState: GameStateEntity, change: (PetEntity) -> PetEntity): GameStateEntity {
        val active = gameState.active ?: return gameState
        return gameState.replace(change(active))
    }

    /** Aplica [change] al estado de la mascota activa. */
    fun updateActiveNeeds(
        gameState: GameStateEntity,
        change: (PetNeedsEntity) -> PetNeedsEntity,
    ) = updateActive(gameState) { it.copy(needs = change(it.needs)) }

    /**
     * Pone al día a **todas** las mascotas hasta [now]: el tiempo es absoluto y común, estén
     * seleccionadas o no. [isNight] dice qué instantes son de noche (de noche descansan).
     */
    fun advanceAll(gameState: GameStateEntity, now: Long, isNight: (Long) -> Boolean = { false }) =
        gameState.copy(pets = gameState.pets.map { pet ->
            // Un huevo aún no vive: sus necesidades empiezan al eclosionar.
            if (pet.isEgg) pet.copy(needs = pet.needs.copy(lastUpdatedAt = now))
            else pet.copy(needs = PetNeedsRules.advanceTo(pet.needs, now, isNight = isNight))
        })

    /** La mascota [petId] dispara en combate: se cansa y se le afilan el pico y las garras. */
    fun petFired(gameState: GameStateEntity, petId: String): GameStateEntity {
        val pet = gameState.byId(petId) ?: return gameState
        return gameState.replace(pet.copy(needs = PetNeedsRules.fire(pet.needs)))
    }

    /** Da [amount] XP a la mascota [petId] (cualquiera, activa o en reposo). */
    fun awardXp(gameState: GameStateEntity, petId: String, amount: Int): GameStateEntity {
        val pet = gameState.byId(petId) ?: return gameState
        return gameState.replace(pet.copy(progression = PetProgression.gainXp(pet.progression, amount)))
    }

    /**
     * La mascota [petId] descansa en la reserva durante un combate: recupera [amount] de
     * energía, sin pasar de [cap] (la energía con la que entró al combate).
     */
    fun restInBattle(gameState: GameStateEntity, petId: String, amount: Double, cap: Double): GameStateEntity {
        val pet = gameState.byId(petId) ?: return gameState
        if (amount <= 0 || pet.needs.energy >= cap) return gameState
        val energy = minOf(cap, pet.needs.energy + amount)
        return gameState.replace(pet.copy(needs = pet.needs.copy(energy = energy)))
    }

    /** La mascota [petId] se cansa por luchar (cualquiera, activa o en reposo). */
    fun spendEnergy(gameState: GameStateEntity, petId: String, amount: Double): GameStateEntity {
        val pet = gameState.byId(petId) ?: return gameState
        return gameState.replace(pet.copy(needs = PetNeedsRules.spendEnergy(pet.needs, amount)))
    }
}
