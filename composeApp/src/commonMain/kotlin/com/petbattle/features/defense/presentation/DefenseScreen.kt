package com.petbattle.features.defense.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.petbattle.core.ui.PlatformBackHandler
import com.petbattle.core.ui.hiddenPointer
import com.petbattle.features.defense.domain.entities.DefenseMapEntity
import com.petbattle.features.defense.domain.entities.EnemyKind
import com.petbattle.features.defense.domain.maps.forestMap
import com.petbattle.features.defense.presentation.game.DefenseGame
import com.petbattle.features.defense.presentation.game.DefenseHudState
import com.petbattle.features.defense.presentation.game.DefenseSprites
import com.petbattle.features.defense.presentation.game.TextPainter
import com.petbattle.features.defense.presentation.game.drawDefense
import com.petbattle.features.defense.presentation.widgets.DefenseHud
import com.petbattle.resources.Res
import com.petbattle.resources.enemy_butterfly
import com.petbattle.resources.enemy_slime
import com.petbattle.resources.map_defense_1
import com.petbattle.resources.skill_air
import com.petbattle.resources.skill_earth
import com.petbattle.resources.skill_fire
import com.petbattle.resources.skill_water
import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_skills.domain.PetProgression
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.domain.isChick
import com.petbattle.shared_features.pet_species.presentation.sheet
import com.petbattle.shared_features.pet_status.application.PetController
import com.petbattle.shared_features.pet_status.domain.GameRules
import com.petbattle.shared_features.pet_status.domain.PetDerivedNeeds
import org.jetbrains.compose.resources.imageResource
import kotlin.math.max
import kotlin.math.min

/**
 * Partida de tower defense con todas las mascotas adultas, de cualquier bioma, que esperan en
 * la reserva agrupadas por especie (ver DefenseHud). El mapa ocupa todo lo que deja libre la
 * reserva: llena ese hueco (sin bandas) y lo que no cabe se ve arrastrándolo ([MapCamera]).
 * La mascota que se lleva en la mano sigue al puntero, a modo de cursor. La XP, el cansancio
 * del combate y el descanso en la reserva van directamente a [PetController], que los guarda solo.
 */
@Composable
fun DefenseScreen(controller: PetController, onExit: () -> Unit, map: DefenseMapEntity = forestMap) {
    PlatformBackHandler(onExit)

    val gameState by controller.gameState.collectAsState()
    // Combaten todos los adultos, vivan en el bioma que vivan: los polluelos aún no saben luchar.
    val pets = gameState?.pets.orEmpty().filter { !it.species.isChick }
    val game = remember {
        DefenseGame(
            map = map,
            onEnemyKilledBy = {
                controller.awardXp(it, PetProgression.XP_PER_ENEMY)
                controller.awardGold(GameRules.GOLD_PER_ENEMY)
            },
            onPetFired = { controller.petFired(it) },
            onPetRested = controller::restInBattle,
        )
    }
    LaunchedEffect(pets) {
        val state = gameState
        val social = if (state == null) emptyMap() else pets.associate { it.id to PetDerivedNeeds.socialization(it, state) }
        game.updatePets(pets, social)
    }

    val sprites = rememberDefenseSprites()
    val text = TextPainter(rememberTextMeasurer())
    var hud by remember { mutableStateOf(DefenseHudState()) }
    var frame by remember { mutableLongStateOf(0L) }
    // Velocidad de la partida (x1..x4): cada fotograma avanza la simulación tantos pasos.
    var timeScale by remember { mutableStateOf(1) }
    // Tamaño de la zona del mapa y desplazamiento de la cámara (null = centrada).
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var pan by remember { mutableStateOf<Offset?>(null) }
    fun camera() = MapCamera.of(viewport, map, pan)

    LaunchedEffect(game) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.0 else (now - last) / 1e9
                last = now
                // Varios pasos normales en vez de uno largo: los proyectiles no atraviesan enemigos.
                repeat(timeScale) { game.update(min(dt, 0.1)) }
                hud = game.hud
                frame++
            }
        }
    }

    DefenseHud(
        hud = hud,
        pets = pets,
        statsOf = game::statsOf,
        onSelect = game::selectPet,
        onWithdraw = game::withdraw,
        onClearSelection = game::clearSelection,
        onStart = game::start,
        onExit = onExit,
        onRestart = game::restart,
        timeScale = timeScale,
        onCycleSpeed = { timeScale = if (timeScale >= MAX_TIME_SCALE) 1 else timeScale + 1 },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                // Con una mascota en la mano, ella es el cursor: se oculta la flecha del ratón.
                .hiddenPointer(hud.selectedId != null || hud.editingId != null)
                // Sigue al puntero (sin consumir nada) para dibujar la mascota de la mano y, con la
                // rueda del ratón, desplaza el mapa.
                .pointerInput(game) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue
                            if (event.type == PointerEventType.Scroll) {
                                val cam = camera()
                                pan = cam.offset - change.scrollDelta * SCROLL_STEP
                            }
                            val visible = when {
                                event.type == PointerEventType.Exit -> false
                                change.type == PointerType.Mouse -> true
                                else -> change.pressed // con el dedo, mientras toca
                            }
                            game.pointer = if (visible) camera().toMap(change.position) else null
                        }
                    }
                }
                // Arrastrar desplaza el mapa (lo que no cabe en pantalla).
                .pointerInput(game) {
                    detectDragGestures { _, drag -> pan = camera().offset + drag }
                }
                .pointerInput(game) {
                    detectTapGestures { pos -> game.tapAt(camera().toMap(pos)) }
                },
        ) {
            frame // redibuja en cada frame
            val cam = camera()
            translate(cam.offset.x, cam.offset.y) {
                scale(cam.scale, cam.scale, pivot = Offset.Zero) { drawDefense(game, sprites, text) }
            }
        }
    }
}

/** Velocidad máxima de la partida: se acelera hasta 3 veces (x2, x3, x4) y vuelve a x1. */
private const val MAX_TIME_SCALE = 4

/** Píxeles que desplaza el mapa cada paso de la rueda del ratón. */
private const val SCROLL_STEP = 60f

/**
 * Cámara del mapa: lo escala para **llenar** la zona disponible (sin bandas; lo que sobra queda
 * fuera) y lo desplaza [offset] dentro de los límites, para que nunca se vea fuera del mapa.
 */
private class MapCamera(val scale: Float, val offset: Offset) {
    /** Punto de pantalla -> coordenadas de mapa. */
    fun toMap(p: Offset) = (p - offset) / scale

    companion object {
        fun of(viewport: IntSize, map: DefenseMapEntity, pan: Offset?): MapCamera {
            val w = viewport.width.toFloat()
            val h = viewport.height.toFloat()
            if (w <= 0f || h <= 0f) return MapCamera(1f, Offset.Zero)
            val s = max(w / map.width, h / map.height)
            // Desplazamientos posibles: de 0 (borde izquierdo/superior) a lo que sobra (negativo).
            val minX = w - map.width * s
            val minY = h - map.height * s
            val wanted = pan ?: Offset(minX / 2, minY / 2)
            return MapCamera(s, Offset(wanted.x.coerceIn(minX, 0f), wanted.y.coerceIn(minY, 0f)))
        }
    }
}

@Composable
private fun rememberDefenseSprites(): DefenseSprites {
    val pets: Map<PetSpecies, ImageBitmap> = PetSpecies.entries.associateWith { imageResource(it.sheet) }
    return DefenseSprites(
        map = imageResource(Res.drawable.map_defense_1),
        enemies = mapOf(
            EnemyKind.worm to imageResource(Res.drawable.enemy_slime),
            EnemyKind.butterfly to imageResource(Res.drawable.enemy_butterfly),
        ),
        skills = mapOf(
            PetElement.fire to imageResource(Res.drawable.skill_fire),
            PetElement.water to imageResource(Res.drawable.skill_water),
            PetElement.air to imageResource(Res.drawable.skill_air),
            PetElement.earth to imageResource(Res.drawable.skill_earth),
        ),
        pets = pets,
    )
}
