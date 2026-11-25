package com.petbattle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.use
import com.petbattle.features.defense.domain.RoutePath
import com.petbattle.features.defense.domain.entities.EnemyEntity
import com.petbattle.features.defense.domain.entities.EnemyKind
import com.petbattle.features.defense.domain.maps.forestMap
import com.petbattle.features.defense.presentation.game.AnimEffect
import com.petbattle.features.defense.presentation.game.DefenseGame
import com.petbattle.features.defense.presentation.game.Enemy
import com.petbattle.features.defense.presentation.game.DefenseSprites
import com.petbattle.features.defense.presentation.game.TextPainter
import com.petbattle.features.defense.presentation.game.drawDefense
import com.petbattle.features.defense.presentation.widgets.DefenseHud
import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test de humo de la escena de defensa: juega unos segundos con una mascota de cada
 * elemento y renderiza el frame fuera de pantalla en build/visual/defense-*.png,
 * de modo que se ejecute todo el renderer (sprites, overlays, proyectiles, texto).
 */
class DefenseVisualTest {
    private val res = File("src/commonMain/composeResources/drawable")
    private fun img(name: String): ImageBitmap =
        Image.makeFromEncoded(File(res, "$name.png").readBytes()).toComposeImageBitmap()

    private val sprites = DefenseSprites(
        map = img("map_defense_1"),
        enemies = mapOf(EnemyKind.worm to img("enemy_slime"), EnemyKind.butterfly to img("enemy_butterfly")),
        skills = PetElement.entries.associateWith { img("skill_${it.name}") },
        pets = PetSpecies.adults.associateWith { sp ->
            img("pet_" + sp.name.replace(Regex("([A-Z])")) { "_" + it.value.lowercase() })
        },
    )

    @Test
    fun petsArePlacedFromTheReserveAndCanBeMovedAtAnyTime() {
        val game = DefenseGame(forestMap, {}, {}, Random(1))
        val (a, b) = listOf(PetSpecies.crow to "A", PetSpecies.cardinal to "B").map { (s, n) -> PetEntity.newborn(n, s, 0) }
        game.updatePets(listOf(a, b))
        assertFalse(game.canStart)
        // Nadie está elegido de entrada: tocar el mapa no coloca nada.
        game.tapAt(Offset(380f, 420f))
        assertTrue(game.defenders.isEmpty())
        game.selectPet(a.id)
        game.tapAt(Offset(380f, 420f)) // coloca A
        game.selectPet(b.id)
        game.tapAt(Offset(830f, 530f)) // coloca B
        assertEquals(2, game.defenders.size)
        assertNull(game.selectedId)
        assertTrue(game.canStart)
        // Tocar a A la recoge; tocar hierba la mueve; tocarla otra vez sin mover la suelta.
        game.tapAt(Offset(380f, 381f))
        assertEquals(a.id, game.editingId)
        game.tapAt(Offset(650f, 1000f))
        assertNull(game.editingId)
        assertEquals(Offset(650f, 1000f), game.defenders.getValue(a.id).foot)
        game.tapAt(Offset(650f, 960f))
        game.tapAt(Offset(650f, 960f))
        assertNull(game.editingId)
        // Antes de empezar no aparecen enemigos.
        repeat(120) { game.update(1.0 / 60) }
        assertTrue(game.enemies.isEmpty())
        game.start()
        assertTrue(game.running)
        // En combate también se puede recoger y mover.
        game.tapAt(Offset(650f, 960f))
        assertEquals(a.id, game.editingId)
        game.tapAt(Offset(380f, 420f))
        assertEquals(Offset(380f, 420f), game.defenders.getValue(a.id).foot)
        repeat(120) { game.update(1.0 / 60) }
        assertTrue(game.enemies.isNotEmpty())
        // Reintentar vuelve a la preparación con los pájaros donde estaban.
        game.restart()
        assertFalse(game.started)
        assertEquals(Offset(380f, 420f), game.defenders.getValue(a.id).foot)
    }

    @Test
    fun withdrawnPetsRestOnlyUpToTheEnergyTheyEnteredWith() {
        val rested = mutableListOf<Triple<String, Double, Double>>()
        val game = DefenseGame(forestMap, {}, {}, Random(1), onPetRested = { id, amount, cap -> rested += Triple(id, amount, cap) })
        val pet = PetEntity.newborn("A", PetSpecies.crow, 0).let { it.copy(needs = it.needs.copy(energy = 80.0)) }
        game.updatePets(listOf(pet))
        game.selectPet(pet.id)
        game.placePetAt(Offset(380f, 420f))
        assertEquals(80.0, game.entryEnergy[pet.id])
        game.start()
        // Ha gastado energía combatiendo y se retira a la reserva.
        game.updatePets(listOf(pet.copy(needs = pet.needs.copy(energy = 50.0))))
        game.withdraw(pet.id)
        assertTrue(pet.id !in game.defenders)
        repeat(60) { game.update(1.0 / 60) }
        assertTrue(rested.isNotEmpty(), "descansa en la reserva")
        assertTrue(rested.all { it.first == pet.id && it.third == 80.0 }, "el tope es la energía de entrada")
        // Ya en su tope, deja de descansar.
        rested.clear()
        game.updatePets(listOf(pet.copy(needs = pet.needs.copy(energy = 80.0))))
        repeat(60) { game.update(1.0 / 60) }
        assertTrue(rested.isEmpty())
    }

    @Test
    fun rendersAFullRun() {
        var killed = 0
        var shots = 0
        val game = DefenseGame(forestMap, { killed++ }, { shots++ }, Random(7))
        val pets = listOf(
            PetSpecies.cardinal to "Fuego", PetSpecies.woodThrush to "Agua",
            PetSpecies.hummingbird to "Aire", PetSpecies.crow to "Tierra",
        ).map { (s, n) ->
            val p = PetEntity.newborn(n, s, 0)
            p.copy(progression = p.progression.copy(level = 1))
        }
        game.updatePets(pets)
        val spots = listOf(Offset(380f, 420f), Offset(830f, 530f), Offset(650f, 1000f), Offset(1340f, 780f))
        pets.zip(spots).forEach { (p, at) -> game.selectPet(p.id); game.placePetAt(at) }
        assertTrue(game.defenders.size == 4, "all four pets placed")
        game.start()

        for ((i, seconds) in listOf(6, 14, 22).withIndex()) {
            while (game.time < seconds) game.update(1.0 / 60)
            render(game, "defense-$i.png")
        }
        assertTrue(shots > 0, "pets fired")
        assertTrue(killed > 0, "enemies were defeated")
    }

    @Test
    fun rendersButterfliesFlyingAndDying() {
        val game = DefenseGame(forestMap, {}, {}, Random(3))
        val pet = PetEntity.newborn("Fuego", PetSpecies.cardinal, 0)
        game.updatePets(listOf(pet.copy(progression = pet.progression.copy(level = 1))))
        game.selectPet(pet.id)
        game.placePetAt(Offset(380f, 420f))
        game.start()
        // Mariposas de nivel 8 soltadas cada poco, para que queden repartidas por la ruta
        // junto a los gusanos que salen solos.
        for (route in forestMap.routes.map(::RoutePath)) {
            repeat(3) { i ->
                game.enemies += Enemy(route, EnemyEntity.spawn(8, EnemyKind.butterfly), i)
                repeat(70) { game.update(1.0 / 60) }
            }
        }
        // Una muriendo en el suelo, a mitad de animación.
        game.effects += AnimEffect.enemyDeath(EnemyKind.butterfly, Offset(732f, 700f), flip = false)
        repeat(12) { game.update(1.0 / 60) }

        val butterfly = game.enemies.first { it.entity.kind == EnemyKind.butterfly }
        val worm = game.enemies.first { it.entity.kind == EnemyKind.worm }
        // Vuela: su cuerpo (adonde apuntan los disparos) queda más alto que el del gusano.
        val flyHeight = butterfly.position.y - butterfly.bodyCenter.y
        val wormHeight = worm.position.y - worm.bodyCenter.y
        assertTrue(flyHeight > wormHeight + 20, "la mariposa vuela ($flyHeight vs $wormHeight)")
        render(game, "defense-butterflies.png")
    }

    @Test
    fun rendersTheBattleHudWithASpeciesDrawerOpenAndAPetInHand() {
        val game = DefenseGame(forestMap, {}, {}, Random(5))
        val pets = listOf(
            Triple(PetSpecies.crow, "Cuervo", 3), Triple(PetSpecies.crow, "Grajo", 1), Triple(PetSpecies.crow, "Negrito", 5),
            Triple(PetSpecies.cardinal, "Fuego", 2), Triple(PetSpecies.hummingbird, "Zumbi", 1),
        ).map { (s, n, tier) -> PetEntity.newborn(n, s, 0, id = n).copy(tier = tier) }
        game.updatePets(pets)
        game.selectPet(pets[0].id)
        game.placePetAt(Offset(380f, 420f))
        game.start()
        repeat(240) { game.update(1.0 / 60) }
        game.selectPet(pets[1].id) // un cuervo de la reserva en la mano...
        game.pointer = Offset(830f, 530f) // ...bajo el puntero, a modo de cursor
        val out = File("build/visual").apply { mkdirs() }
        ImageComposeScene(1280, 600).use { scene ->
            scene.setContent {
                val text = TextPainter(rememberTextMeasurer())
                DefenseHud(
                    hud = game.hud, pets = pets, statsOf = game::statsOf,
                    onSelect = {}, onWithdraw = {}, onClearSelection = {}, onStart = {}, onExit = {}, onRestart = {},
                    timeScale = 2, initialOpenSpecies = PetSpecies.crow,
                ) {
                    // Como en la pantalla real: el mapa llena el hueco (centrado) y lo que sobra queda fuera.
                    Canvas(Modifier.fillMaxSize()) {
                        val s = maxOf(size.width / game.map.width, size.height / game.map.height)
                        translate((size.width - game.map.width * s) / 2, (size.height - game.map.height * s) / 2) {
                            scale(s, s, pivot = Offset.Zero) { drawDefense(game, sprites, text) }
                        }
                    }
                }
            }
            scene.render() // primer fotograma: arranca la animación del cajón
            val png = scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "defense-hud.png").writeBytes(png)
        }
    }

    private fun render(game: DefenseGame, name: String) {
        val out = File("build/visual").apply { mkdirs() }
        ImageComposeScene(game.map.width / 2, game.map.height / 2).use { scene ->
            scene.setContent {
                val text = TextPainter(rememberTextMeasurer())
                Canvas(Modifier.fillMaxSize()) {
                    scale(size.width / game.map.width, Offset.Zero) { drawDefense(game, sprites, text) }
                }
            }
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, name).writeBytes(png)
        }
    }
}
