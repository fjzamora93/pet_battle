package com.petbattle

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.petbattle.core.time.DayPhase
import com.petbattle.features.biome.presentation.game.BiomeScene
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Prueba de humo del bosque (escena fotográfica con troncos que tapan): varias mascotas vuelan
 * y se posan por todo el suelo, también al fondo del camino, durante unos segundos simulados.
 * Guarda capturas en build/visual/woodland-*.png para revisar a ojo la perspectiva (al fondo,
 * pequeñas) y que los troncos tapen a las que pasan por detrás.
 */
class WoodlandVisualTest {
    @Test
    fun petsWalkTheWholeForest() {
        val species = listOf(
            PetSpecies.redRobin, PetSpecies.blueJay, PetSpecies.cardinal, PetSpecies.crow,
            PetSpecies.magpie, PetSpecies.woodThrush, PetSpecies.chickadee, PetSpecies.whiteDove,
        )
        val pets = species.mapIndexed { i, s -> PetEntity.newborn("P$i", s, 0) }
        val out = File("build/visual").apply { mkdirs() }
        ImageComposeScene(1280, 720, Density(1f)).use { scene ->
            scene.setContent {
                BiomeScene(Biome.woodland, pets, activeId = pets.first().id, onPetTapped = {}, modifier = Modifier.fillMaxSize(), dayPhase = DayPhase.morning)
            }
            // 60 fps simulados; capturas a los 4, 9 y 15 s (y, con WOODLAND_ALL_SHOTS, cada segundo).
            var t = 0L
            val every = System.getenv("WOODLAND_ALL_SHOTS") != null
            val shots = if (every) (1..30).associate { it * 60 to it } else mapOf(240 to 0, 540 to 1, 900 to 2)
            for (f in 1..shots.keys.max()) {
                t += 16_666_667L
                val image = scene.render(t)
                shots[f]?.let { n -> File(out, "woodland-$n.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes) }
                if (f < 20) Thread.sleep(25) // los recursos se cargan de forma asíncrona
            }
        }
    }
}
