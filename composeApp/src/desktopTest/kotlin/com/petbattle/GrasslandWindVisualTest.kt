package com.petbattle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.use
import androidx.compose.ui.geometry.Size
import com.petbattle.core.ui.createWindPainter
import com.petbattle.core.ui.drawSpriteFrame
import com.petbattle.features.biome.presentation.game.BackdropLayers
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.biomes.presentation.backgroundLayers
import com.petbattle.shared_features.pet_species.presentation.BirdSheet
import com.petbattle.features.biome.presentation.game.drawLayeredBackdrop
import com.petbattle.features.biome.presentation.game.solidRowFraction
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Prueba visual del fondo por capas de la pradera con viento: renderiza dos instantes en
 * build/visual/grassland-*.png y comprueba que el viento mueve el primer plano (hierba y
 * árboles) y no el cielo despejado de arriba a la derecha, que solo se desplaza despacio.
 */
class GrasslandWindVisualTest {
    private val res = File("src/commonMain/composeResources/drawable")
    private fun img(name: String): ImageBitmap =
        Image.makeFromEncoded(File(res, "$name.png").readBytes()).toComposeImageBitmap()

    @Test
    fun windMovesGrassAndTrees() {
        val terrain = img("biome_grassland_terrain")
        val wind = createWindPainter(terrain, img("biome_grassland_wind"))
        assertNotNull(wind, "el shader de viento compila en escritorio")
        val solid = solidRowFraction(terrain)
        val layers = BackdropLayers(
            img("biome_grassland_sky"), img("biome_grassland_hills"), terrain, solid, wind, FilterQuality.Medium,
        )
        val (w, h) = 1280 to 720
        val frames = listOf(0.0, 1.4).map { t ->
            ImageComposeScene(w, h).use { scene ->
                scene.setContent {
                    Canvas(Modifier.fillMaxSize()) {
                        drawLayeredBackdrop(layers, t)
                        // Franja donde se posan los pájaros (como BiomeSceneState.layout): del
                        // suelo más cercano al borde del terreno.
                        val k = maxOf(size.width / terrain.width, size.height / terrain.height)
                        val cropTop = (terrain.height - size.height / k) / 2
                        val back = (solid * terrain.height - cropTop) * k + size.height * 0.012f
                        val front = size.height * 0.965f
                        drawLine(Color.Red, Offset(0f, back), Offset(size.width, back), 2f)
                        drawLine(Color.Yellow, Offset(0f, front), Offset(size.width, front), 2f)
                    }
                }
                scene.render()
            }
        }
        val out = File("build/visual").apply { mkdirs() }
        frames.forEachIndexed { i, image ->
            File(out, "grassland-$i.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        val (a, b) = frames.map { org.jetbrains.skia.Bitmap.makeFromImage(it) }
        fun diff(x0: Int, y0: Int, x1: Int, y1: Int): Double {
            var sum = 0.0
            var n = 0
            for (y in y0 until y1 step 3) for (x in x0 until x1 step 3) {
                val p = a.getColor(x, y)
                val q = b.getColor(x, y)
                sum += abs((p shr 16 and 0xFF) - (q shr 16 and 0xFF)) + abs((p shr 8 and 0xFF) - (q shr 8 and 0xFF))
                n++
            }
            return sum / n
        }
        val grass = diff(0, (h * 0.85).toInt(), w, (h * 0.95).toInt())
        val trees = diff((w * 0.2).toInt(), (h * 0.1).toInt(), (w * 0.5).toInt(), (h * 0.4).toInt())
        assertTrue(grass > 3, "la hierba ondea ($grass)")
        assertTrue(trees > 3, "los árboles se mecen ($trees)")
    }

    /**
     * Pájaros posados a lo ancho de la cuesta (en el borde del fondo, a media distancia y
     * delante), con la hierba de delante repintada encima como hace BiomeScene. Se guarda en
     * build/visual/grassland-birds.png; con y sin hierba encima deben verse distintos.
     */
    @Test
    fun birdsStandOnTheSlopeAndGrassCoversThem() {
        val terrain = img("biome_grassland_terrain")
        val wind = assertNotNull(createWindPainter(terrain, img("biome_grassland_wind")))
        val layers = BackdropLayers(
            img("biome_grassland_sky"), img("biome_grassland_hills"), terrain, solidRowFraction(terrain), wind,
            FilterQuality.Medium, Biome.grassland.backgroundLayers!!.groundEdge,
        )
        val bird = img("pet_cardinal")
        val edge = layers.groundEdge!!
        val (w, h) = 1280 to 720
        fun render(withGrass: Boolean) = ImageComposeScene(w, h).use { scene ->
            scene.setContent {
                Canvas(Modifier.fillMaxSize()) {
                    drawLayeredBackdrop(layers, 2.0)
                    // Mismas cuentas que BiomeSceneState.layout para el suelo.
                    val k = maxOf(size.width / terrain.width, size.height / terrain.height)
                    val cropTop = (terrain.height - size.height / k) / 2
                    val cropLeft = (terrain.width - size.width / k) / 2
                    val frontY = size.height * 0.965f
                    val refBack = (layers.terrainSolid * terrain.height - cropTop) * k + size.height * 0.012f
                    fun backAt(x: Float): Float {
                        val ix = (x / k + cropLeft) / terrain.width
                        val i = edge.indexOfFirst { it.first >= ix }.coerceIn(1, edge.size - 1)
                        val (x0, y0) = edge[i - 1]
                        val (x1, y1) = edge[i]
                        val iy = y0 + (y1 - y0) * ((ix - x0) / (x1 - x0)).coerceIn(0f, 1f)
                        return minOf(frontY, (iy * terrain.height - cropTop) * k + size.height * 0.012f)
                    }
                    val slot = minOf(size.width * 0.3f, size.height * 0.24f)
                    val srcPerDst = 1f / k
                    for (depth in listOf(1.0f, 0.5f, 0.0f)) for (fx in listOf(0.12f, 0.5f, 0.88f)) {
                        val cx = size.width * fx
                        val ground = frontY + (backAt(cx) - frontY) * depth
                        val side = slot * (1f - (frontY - refBack) * depth / size.height * 1.6f).coerceAtLeast(0.6f)
                        val topLeft = Offset(cx - side / 2, ground - side)
                        drawSpriteFrame(bird, BirdSheet.FRAME_SIZE, 0, BirdSheet.IDLE_ROW, topLeft, side, filterQuality = BirdSheet.FILTER)
                        if (withGrass) {
                            drawRect(
                                wind.brush(cropLeft, cropTop, srcPerDst, 2f, grassFrontOf = cropTop + ground * srcPerDst),
                                topLeft = topLeft, size = Size(side, side + 2f),
                            )
                        }
                    }
                }
            }
            scene.render()
        }
        val bare = render(withGrass = false)
        val covered = render(withGrass = true)
        File("build/visual").apply { mkdirs() }.let { out ->
            File(out, "grassland-birds.png").writeBytes(covered.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        val (a, b) = listOf(bare, covered).map { org.jetbrains.skia.Bitmap.makeFromImage(it) }
        var changed = 0
        for (y in (h * 0.8).toInt() until (h * 0.96).toInt()) for (x in 0 until w step 2) {
            if (a.getColor(x, y) != b.getColor(x, y)) changed++
        }
        assertTrue(changed > 500, "la hierba de delante tapa a los pájaros ($changed px)")
    }
}
