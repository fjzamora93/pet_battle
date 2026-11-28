package com.petbattle

import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.presentation.BirdLayout
import com.petbattle.shared_features.pet_species.presentation.BirdSheet
import com.petbattle.shared_features.pet_species.presentation.layout
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cada hoja de pájaro casa con lo que declara BirdSheet: tiene dibujo en todos los frames de
 * cada animación (si no, el pájaro parpadearía al llegar a un frame vacío) y mide lo justo.
 */
class BirdSheetsTest {
    private val res = File("src/commonMain/composeResources/drawable")

    private fun sheetOf(species: PetSpecies): Bitmap {
        val name = "pet_" + species.name.replace(Regex("([A-Z])")) { "_" + it.value.lowercase() }
        return Bitmap.makeFromImage(Image.makeFromEncoded(File(res, "$name.png").readBytes()))
    }

    /** Si la casilla ([col], [row]) tiene algún píxel visible (muestreando cada 4 px). */
    private fun Bitmap.hasDrawing(col: Int, row: Int): Boolean {
        val s = BirdSheet.FRAME_SIZE
        for (y in row * s until (row + 1) * s step 4) for (x in col * s until (col + 1) * s step 4) {
            if ((getColor(x, y) ushr 24) > 40) return true
        }
        return false
    }

    @Test
    fun everyDeclaredFrameHasDrawing() {
        for (species in PetSpecies.adults) {
            val sheet = sheetOf(species)
            val rows = listOf(
                BirdSheet.FLY_ROW, BirdSheet.IDLE_ROW, BirdSheet.HOP_ROW,
                BirdSheet.LOOK_UP_ROW, BirdSheet.REST_ROW, BirdSheet.SLEEP_ROW,
            )
            val columns = BirdSheet.columns(species)
            assertEquals(rows.maxOf { BirdSheet.frames(it, species) }, columns, "$species: columnas")
            assertEquals(columns * BirdSheet.FRAME_SIZE, sheet.width, "$species: ancho de la hoja")
            val animations = rows + listOf(BirdSheet.TAKEOFF, BirdSheet.LANDING, BirdSheet.EAT)
            var lastRow = BirdSheet.ROWS - 1
            for (animation in animations) for (frame in 0 until BirdSheet.frames(animation, species)) {
                val (col, row) = BirdSheet.cell(animation, frame, species)
                lastRow = maxOf(lastRow, row)
                assertTrue(sheet.hasDrawing(col, row), "$species: frame $frame de la animación $animation vacío")
            }
            assertEquals((lastRow + 1) * BirdSheet.FRAME_SIZE, sheet.height, "$species: alto de la hoja")
        }
    }

    @Test
    fun robinUsesItsOwnTimingAndRows() {
        val robin = PetSpecies.redRobin
        assertTrue(BirdSheet.hasTransitions(robin))
        assertTrue(BirdSheet.hasEat(robin.layout))
        // Despegue, aterrizaje y comer: una fila de 16 cada uno, detrás de las 6 normales.
        assertEquals(0 to 6, BirdSheet.cell(BirdSheet.TAKEOFF, 0, robin))
        assertEquals(15 to 7, BirdSheet.cell(BirdSheet.LANDING, 15, robin))
        assertEquals(3 to 8, BirdSheet.cell(BirdSheet.EAT, 3, robin))
        // Duraciones de sus sprites: despegue 8 x 95 ms en tierra + 8 x 60 en el aire.
        assertEquals(1.24, BirdSheet.cycleSeconds(BirdSheet.TAKEOFF, robin), 1e-9)
        assertEquals(1.336, BirdSheet.cycleSeconds(BirdSheet.LANDING, robin), 1e-9)
        assertEquals(0.76, BirdSheet.liftoffSeconds(robin), 1e-9) // deja el suelo en el frame 8
        assertEquals(7, BirdSheet.onceFrame(BirdSheet.TAKEOFF, robin, 0.75))
        assertEquals(8, BirdSheet.onceFrame(BirdSheet.TAKEOFF, robin, 0.77))
        assertEquals(15, BirdSheet.onceFrame(BirdSheet.LANDING, robin, 9.0))
        // Bucle de 0,556 s del vuelo: a mitad de ciclo, frame 6 de 12.
        assertEquals(6, BirdSheet.loopFrame(BirdSheet.FLY_ROW, robin, 0.556 * 2.5 + 1e-6))
        // Sus patas están más arriba en la casilla: hay que bajarlo un poco.
        assertTrue(BirdSheet.groundShift(robin) > 0.05f)
        assertEquals(0f, BirdSheet.groundShift(PetSpecies.cardinal))
    }

    @Test
    fun careBirdSheetHasEveryDeclaredFrame() {
        // La hoja del modo cuidado (BirdLayout.care): mismas filas, sus propios frames.
        val layout = BirdLayout.care
        val sheet = Bitmap.makeFromImage(Image.makeFromEncoded(File(res, "pet_care_bird_1.png").readBytes()))
        assertEquals(BirdSheet.columns(layout) * BirdSheet.FRAME_SIZE, sheet.width, "ancho de la hoja")
        assertEquals(BirdSheet.ROWS * BirdSheet.FRAME_SIZE, sheet.height, "alto de la hoja")
        assertTrue(!BirdSheet.hasTransitions(layout))
        for (row in 0 until BirdSheet.ROWS) {
            val n = BirdSheet.frames(row, layout)
            assertTrue(n > 0, "fila $row sin frames")
            for (col in 0 until BirdSheet.columns(layout)) {
                assertEquals(col < n, sheet.hasDrawing(col, row), "fila $row, columna $col")
            }
        }
    }

    @Test
    fun jayTakeOffAndLandingWrapAcrossRows() {
        val jay = PetSpecies.blueJay
        assertTrue(BirdSheet.hasTransitions(jay))
        assertTrue(!BirdSheet.hasTransitions(PetSpecies.cardinal))
        // 31 frames en 16 columnas: el despegue ocupa las filas 6-7 y el aterrizaje las 8-9.
        assertEquals(15 to 6, BirdSheet.cell(BirdSheet.TAKEOFF, 15, jay))
        assertEquals(0 to 7, BirdSheet.cell(BirdSheet.TAKEOFF, 16, jay))
        assertEquals(0 to 8, BirdSheet.cell(BirdSheet.LANDING, 0, jay))
        assertEquals(14 to 9, BirdSheet.cell(BirdSheet.LANDING, 30, jay))
        // Deja el suelo a la mitad del despegue, aproximadamente.
        val liftoff = BirdSheet.liftoffSeconds(jay)
        assertTrue(liftoff > 0.5 && liftoff < BirdSheet.cycleSeconds(BirdSheet.TAKEOFF, jay))
    }

    @Test
    fun jayHasTheSmootherAnimations() {
        assertEquals(12, BirdSheet.frames(BirdSheet.FLY_ROW, PetSpecies.blueJay))
        assertEquals(16, BirdSheet.frames(BirdSheet.IDLE_ROW, PetSpecies.blueJay))
        // Mismo ciclo que las demás especies, con frames más cortos.
        val t = BirdSheet.cycleSeconds(BirdSheet.IDLE_ROW, PetSpecies.blueJay) / 2
        assertEquals(8, BirdSheet.loopFrame(BirdSheet.IDLE_ROW, PetSpecies.blueJay, t + 1e-6))
        assertEquals(3, BirdSheet.loopFrame(BirdSheet.IDLE_ROW, PetSpecies.cardinal, t + 1e-6))
        // Mirar arriba: de una sola vez, se queda en el último frame.
        assertEquals(15, BirdSheet.onceFrame(BirdSheet.LOOK_UP_ROW, PetSpecies.blueJay, 99.0))
    }
}
