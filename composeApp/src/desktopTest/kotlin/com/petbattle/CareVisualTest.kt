package com.petbattle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.petbattle.core.persistence.InMemoryKeyValueStore
import com.petbattle.core.time.DayPhase
import com.petbattle.features.care.presentation.CareScreen
import com.petbattle.shared_features.pet_status.application.PetController
import com.petbattle.shared_features.pet_status.data.LocalPetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Prueba de humo del modo cuidado: renderiza la pantalla fuera de pantalla con un polluelo,
 * en tamaño de móvil, en cuadrado (con botón de volver al bioma) y de reloj redondo y rectangular (sin él), y la guarda en
 * build/visual/care-*.png para revisarla a ojo (en el reloj, con la esfera redonda marcada).
 */
class CareVisualTest {
    @Test
    fun rendersOnPhoneAndWatch() {
        val controller = PetController(
            LocalPetRepository(InMemoryKeyValueStore()),
            { System.currentTimeMillis() },
            CoroutineScope(Dispatchers.Unconfined),
            autoTick = false,
            dayPhaseAt = { DayPhase.morning },
        )
        runBlocking { controller.load() }
        controller.hatch("Kiwi")
        assertNotNull(controller.gameState.value?.active)

        val out = File("build/visual").apply { mkdirs() }
        // (nombre, ancho x alto en dp, con botón de bioma, pantalla redonda)
        data class Case(val name: String, val w: Int, val h: Int, val onBiome: (() -> Unit)?, val round: Boolean)
        for ((name, w, h, onBiome, round) in listOf(
            Case("phone", 800, 400, {}, false),
            Case("square", 420, 420, {}, false),
            Case("watch", 220, 220, null, true),
            // Reloj rectangular y vertical, como el SaveWatch 2 (410 x 502 px a densidad 1,25).
            Case("watch-rect", 328, 402, null, false),
        )) {
            // Densidad 2: la escena se pinta a doble resolución, como en un dispositivo real.
            ImageComposeScene(w * 2, h * 2, Density(2f)).use { scene ->
                scene.setContent {
                    CareScreen(controller, onBiome = onBiome, onOptions = {}, roundScreen = round)
                    // En el reloj redondo, lo que queda fuera de la esfera se oscurece.
                    if (round) {
                        Canvas(Modifier.fillMaxSize()) {
                            val round = Path().apply { addOval(Rect(Offset.Zero, size)) }
                            clipPath(round, ClipOp.Difference) { drawRect(Color(0xCC000000)) }
                        }
                    }
                }
                // Varios frames: los recursos se cargan de forma asíncrona y la escena se anima.
                var t = 0L
                repeat(40) {
                    scene.render(t)
                    t += 50_000_000L
                    Thread.sleep(25)
                }
                val image = scene.render(t)
                File(out, "care-$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }
}
