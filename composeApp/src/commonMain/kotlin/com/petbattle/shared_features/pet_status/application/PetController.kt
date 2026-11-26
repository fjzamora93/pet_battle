package com.petbattle.shared_features.pet_status.application

import com.petbattle.core.config.AppConfig
import com.petbattle.core.time.Clock
import com.petbattle.core.time.DayCycle
import com.petbattle.core.time.DayPhase
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_status.domain.BreedingRules
import com.petbattle.shared_features.pet_status.domain.FeedResult
import com.petbattle.shared_features.pet_status.domain.LifeEvent
import com.petbattle.shared_features.pet_status.domain.PetNameError
import com.petbattle.shared_features.pet_status.domain.PetRepository
import com.petbattle.shared_features.pet_status.domain.PetNeedsRules
import com.petbattle.shared_features.pet_status.domain.GameRules
import com.petbattle.shared_features.pet_status.domain.entities.GameStateEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetNeedsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Orquesta todas las mascotas criadas: carga la partida, aplica la pérdida offline a
 * todas, ejecuta el tick en vivo (el tiempo es común: avanzan todas a la vez), traduce las
 * intenciones del jugador en
 * llamadas a [PetNeedsRules] / [GameRules] y guarda automáticamente cada cambio.
 *
 * [gameState] es `null` mientras se carga.
 */
class PetController(
    private val repository: PetRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val autoTick: Boolean = true,
    private val random: Random = Random.Default,
    /** Momento del día de un instante (hora local; sustituible en tests). */
    private val dayPhaseAt: (Long) -> DayPhase = DayCycle::phaseAt,
    /** Día (fecha local) de un instante, para la cría "una vez al día" (sustituible en tests). */
    private val dayAt: (Long) -> Long = DayCycle::dayAt,
) {
    /** Solo de noche descansan solas. */
    private val isNightAt: (Long) -> Boolean = { dayPhaseAt(it) == DayPhase.night }

    private val _gameState = MutableStateFlow<GameStateEntity?>(null)

    private val _dayPhase = MutableStateFlow(dayPhaseAt(clock.nowMillis()))

    /** Momento del día actual: cambia el filtro de la pantalla y, de noche, descansan. */
    val dayPhase: StateFlow<DayPhase> = _dayPhase.asStateFlow()
    val gameState: StateFlow<GameStateEntity?> = _gameState.asStateFlow()

    private val _events = Channel<LifeEvent>(Channel.BUFFERED)

    /** Lo que pasa en la vida de las mascotas (huevos puestos, eclosiones). Cada aviso se entrega una vez. */
    val events: Flow<LifeEvent> = _events.receiveAsFlow()

    private var ticker: Job? = null
    private var pendingSave: Job? = null

    /** Carga la partida (o una partida vacía) e inicia el tick en vivo. */
    suspend fun load() {
        val now = clock.nowMillis()
        val saved = repository.load()
        if (saved == null) {
            // Partida nueva: en la pradera, con el primer huevo gratis.
            _gameState.value = GameStateEntity.newGame
        } else {
            // Puesta al día offline de todas las mascotas (el tiempo es común para todas), y los
            // huevos que hayan eclosionado o las crías del día mientras la app estaba cerrada.
            val (gameState, events) = live(advanceAll(GameRules.migrate(saved), now), now)
            repository.save(gameState)
            _gameState.value = gameState
            events.forEach { _events.trySend(it) }
        }
        if (autoTick) startTicker()
    }

    // --- Intenciones del jugador ----------------------------------------------------

    /**
     * La mascota [petId] se come [food] (p. ej. la que encontró en el suelo); `null` si ahora no
     * puede comer.
     */
    fun feedPet(petId: String, food: Food): FeedResult? {
        val gameState = _gameState.value ?: return null
        val (next, result) = GameRules.feedPet(advanceAll(gameState, clock.nowMillis()), petId, food, random)
        if (result != null) set(next, saveNow = result == FeedResult.grew)
        return result
    }

    fun toggleSleep() = applyToActive(PetNeedsRules::toggleSleep, saveNow = true)

    /** Despierta a la mascota seleccionada si dormía (al elegir otra acción, como comer o limpiar). */
    fun wake() {
        if (_gameState.value?.active?.needs?.isSleeping == true) applyToActive(PetNeedsRules::wake, saveNow = true)
    }

    /** Limpia a la mascota [petId]; `false` si ahora no se puede (dormida o ya limpia). */
    fun clean(petId: String): Boolean {
        val gameState = _gameState.value ?: return false
        val now = clock.nowMillis()
        val current = advanceAll(gameState, now)
        val pet = current.byId(petId)?.takeIf { !it.isEgg } ?: return false
        if (!PetNeedsRules.canClean(pet.needs)) return false
        set(GameRules.updateNeeds(current, petId, PetNeedsRules::clean))
        return true
    }
    fun cuddle() = applyToActive(PetNeedsRules::cuddle)

    /**
     * Compra un huevo y cría un polluelo llamado [name] en el bioma actual; devuelve por qué se
     * rechazó el nombre. Quien llama debe comprobar antes que alcanza el oro (GameRules.canHatch).
     */
    fun hatch(name: String): PetNameError? {
        val gameState = _gameState.value ?: return null
        if (!GameRules.canHatch(gameState)) return null
        GameRules.validateName(gameState, name)?.let { return it }
        val now = clock.nowMillis()
        set(GameRules.hatch(advanceAll(gameState, now), name, now, random), saveNow = true)
        return null
    }

    /** Libera para siempre a la mascota [petId]. */
    fun release(petId: String) {
        val gameState = _gameState.value ?: return
        val now = clock.nowMillis()
        set(GameRules.release(advanceAll(gameState, now), petId), saveNow = true)
    }

    /** Viaja a [biome] si está comprado (vacío si nunca has criado allí). */
    fun enterBiome(biome: Biome) {
        val gameState = _gameState.value ?: return
        val now = clock.nowMillis()
        set(GameRules.enterBiome(advanceAll(gameState, now), biome), saveNow = true)
    }

    /** Vuelve a una mascota ya criada; la actual empieza a descansar. */
    fun switchTo(petId: String) {
        val gameState = _gameState.value ?: return
        val now = clock.nowMillis()
        set(GameRules.switchTo(advanceAll(gameState, now), petId), saveNow = true)
    }

    /** Oro ganado (p. ej. enemigos derrotados). */
    fun awardGold(amount: Int) {
        val gameState = _gameState.value ?: return
        set(GameRules.awardGold(gameState, amount))
    }

    /** Compra [biome] y viaja a él; `false` si no alcanza el oro (o ya estaba comprado). */
    fun buyBiome(biome: Biome): Boolean {
        val gameState = _gameState.value ?: return false
        if (!GameRules.canBuyBiome(gameState, biome)) return false
        val now = clock.nowMillis()
        set(GameRules.enterBiome(GameRules.buyBiome(advanceAll(gameState, now), biome), biome), saveNow = true)
        return true
    }

    /** Experiencia ganada por la mascota [petId] (p. ej. enemigos derrotados). */
    fun awardXp(petId: String, amount: Int) {
        val gameState = _gameState.value ?: return
        set(GameRules.awardXp(gameState, petId, amount))
    }

    /** La mascota [petId] disparó en combate: se cansa y afila el pico y las garras. */
    fun petFired(petId: String) {
        val gameState = _gameState.value ?: return
        set(GameRules.petFired(gameState, petId))
    }

    /** La mascota [petId] descansa en la reserva del combate (ver GameRules.restInBattle). */
    fun restInBattle(petId: String, amount: Double, cap: Double) {
        val gameState = _gameState.value ?: return
        set(GameRules.restInBattle(gameState, petId, amount, cap))
    }

    /** La mascota [petId] se cansó luchando. */
    fun spendEnergy(petId: String, amount: Double) {
        val gameState = _gameState.value ?: return
        set(GameRules.spendEnergy(gameState, petId, amount))
    }

    /** Ayuda de depuración: borra la partida (todas las mascotas criadas). */
    fun reset() {
        scope.launch {
            repository.clear()
            _gameState.value = GameStateEntity.newGame
        }
    }

    /** Escribe ahora cualquier cambio pendiente (app en pausa / ventana cerrándose). */
    fun flush() {
        pendingSave?.cancel()
        pendingSave = null
        val gameState = _gameState.value ?: return
        scope.launch { repository.save(gameState) }
    }

    fun dispose() {
        ticker?.cancel()
        flush()
    }

    // --- Internos -------------------------------------------------------------------

    /** Primero pone al día a todas las mascotas hasta "ahora" y luego aplica [action] a la activa. */
    private fun applyToActive(action: (PetNeedsEntity) -> PetNeedsEntity, saveNow: Boolean = false) {
        val gameState = _gameState.value ?: return
        val now = clock.nowMillis()
        _dayPhase.value = dayPhaseAt(now)
        set(GameRules.updateActiveNeeds(advanceAll(gameState, now), action), saveNow)
    }

    private fun advanceAll(gameState: GameStateEntity, now: Long) =
        GameRules.advanceAll(gameState, now, isNightAt)

    private fun set(next: GameStateEntity, saveNow: Boolean = false) {
        if (next == _gameState.value) return
        _gameState.value = next
        if (saveNow) flush() else scheduleSave()
    }

    /**
     * Los ticks, una vez por segundo, ponen al día a todas las mascotas hasta "ahora",
     * eclosionan los huevos a los que les toca y hacen que las parejas intenten criar.
     */
    fun tick() {
        val gameState = _gameState.value ?: return
        val now = clock.nowMillis()
        _dayPhase.value = dayPhaseAt(now)
        val (next, events) = live(advanceAll(gameState, now), now)
        set(next, saveNow = events.isNotEmpty())
        events.forEach { _events.trySend(it) }
    }

    /** Eclosiones y crías del día (ver BreedingRules). */
    private fun live(gameState: GameStateEntity, now: Long) =
        BreedingRules.step(gameState, now, dayAt(now), random)

    private fun startTicker() {
        if (ticker != null) return
        ticker = scope.launch {
            while (isActive) {
                delay(AppConfig.TICK_INTERVAL_MS)
                tick()
            }
        }
    }

    /** Guardado limitado: como máximo una escritura cada [AppConfig.SAVE_THROTTLE_MS]. */
    private fun scheduleSave() {
        if (pendingSave != null) return
        pendingSave = scope.launch {
            delay(AppConfig.SAVE_THROTTLE_MS)
            pendingSave = null
            _gameState.value?.let { repository.save(it) }
        }
    }
}
