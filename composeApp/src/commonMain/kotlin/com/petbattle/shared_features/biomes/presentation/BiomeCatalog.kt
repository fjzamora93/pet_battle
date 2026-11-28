package com.petbattle.shared_features.biomes.presentation

import com.petbattle.resources.Res
import com.petbattle.resources.biome_coast
import com.petbattle.resources.biome_conifer_forest
import com.petbattle.resources.biome_grassland
import com.petbattle.resources.biome_grassland_hills
import com.petbattle.resources.biome_grassland_sky
import com.petbattle.resources.biome_grassland_terrain
import com.petbattle.resources.biome_grassland_wind
import com.petbattle.resources.biome_highlands
import com.petbattle.resources.biome_meadow
import com.petbattle.resources.biome_mountain
import com.petbattle.resources.biome_tundra
import com.petbattle.resources.biome_woodland
import com.petbattle.resources.biome_woodland_trunk_center
import com.petbattle.resources.biome_woodland_trunk_center_right
import com.petbattle.resources.biome_woodland_trunk_left_mid
import com.petbattle.resources.biome_woodland_trunk_left_near
import com.petbattle.resources.biome_woodland_trunk_right_near
import com.petbattle.shared_features.biomes.domain.Biome
import org.jetbrains.compose.resources.DrawableResource

/** Datos de presentación de cada bioma: nombre visible y fondo. */
val Biome.label: String
    get() = when (this) {
        Biome.woodland -> "Bosque"
        Biome.grassland -> "Pradera"
        Biome.mountain -> "Montaña"
        Biome.coniferForest -> "Bosque de coníferas"
        Biome.meadow -> "Prado"
        Biome.tundra -> "Tundra"
        Biome.highlands -> "Tierras altas"
        Biome.coast -> "Costa"
    }

val Biome.background: DrawableResource
    get() = when (this) {
        Biome.woodland -> Res.drawable.biome_woodland
        Biome.grassland -> Res.drawable.biome_grassland
        Biome.mountain -> Res.drawable.biome_mountain
        Biome.coniferForest -> Res.drawable.biome_conifer_forest
        Biome.meadow -> Res.drawable.biome_meadow
        Biome.tundra -> Res.drawable.biome_tundra
        Biome.highlands -> Res.drawable.biome_highlands
        Biome.coast -> Res.drawable.biome_coast
    }

/**
 * Fondo partido en capas para el efecto parallax, de atrás adelante: [sky] (cielo y nubes),
 * [back] (elementos de fondo, como las colinas) y [terrain] (el suelo, que no se desplaza).
 */
class BiomeLayers(
    val sky: DrawableResource,
    val back: DrawableResource,
    val terrain: DrawableResource,
    /**
     * Mapa de viento del terreno (rojo = árboles, verde = hierba; ver WindPainter): si lo hay,
     * el terreno se dibuja mecido por el viento.
     */
    val wind: DrawableResource? = null,
    /** Capas ilustradas (no pixel art): se dibujan suavizadas al escalarlas. */
    val smooth: Boolean = false,
    /**
     * Borde del fondo del suelo cuando no es horizontal (un prado en cuesta): puntos (x, y) de
     * izquierda a derecha, en fracciones de la imagen del terreno. Las mascotas del fondo se
     * posan sobre esta línea. Si es null, el borde es la primera fila opaca del terreno.
     */
    val groundEdge: List<Pair<Float, Float>>? = null,
)

/**
 * Borde superior del prado de la pradera (la cuesta sube hacia la izquierda, hasta los
 * árboles). Mismos puntos que MEADOW_EDGE en tools/grassland_layers/grassland_layers.py
 * (píxeles de la imagen de 1284x832).
 */
private val grasslandGroundEdge = listOf(
    0 to 495, 200 to 532, 400 to 560, 560 to 586, 700 to 602, 800 to 632,
    950 to 655, 1100 to 672, 1284 to 690,
).map { (x, y) -> x / 1284f to y / 832f }

/** Capas del fondo si el bioma las tiene; si no, se dibuja [background] entero. */
val Biome.backgroundLayers: BiomeLayers?
    get() = when (this) {
        Biome.grassland -> BiomeLayers(
            sky = Res.drawable.biome_grassland_sky,
            back = Res.drawable.biome_grassland_hills,
            terrain = Res.drawable.biome_grassland_terrain,
            wind = Res.drawable.biome_grassland_wind,
            smooth = true,
            groundEdge = grasslandGroundEdge,
        )
        else -> null
    }

/**
 * Algo del fondo (p. ej. un tronco) recortado para tapar a las mascotas que pasan por detrás:
 * [image] tiene el tamaño y la posición del fondo (solo es opaco el recorte) y [rootY] es la
 * altura (fracción de la imagen) donde toca el suelo. Se pinta encima de las mascotas cuyo suelo
 * queda más arriba (más al fondo). No se mueve: el fondo ya lo contiene.
 */
class BiomeOccluder(val image: DrawableResource, val rootY: Float)

/**
 * Bioma hecho con una foto fija ([background], sin parallax) por la que las mascotas se mueven
 * en profundidad, hasta el fondo:
 * - [groundEdge]: borde del fondo del suelo, puntos (x, y) en fracciones de la foto.
 * - Perspectiva: el tamaño depende de la altura del suelo que pisan; una casilla de sprite mide
 *   [farScale] de su tamaño con el suelo en [farY] y [nearScale] en [nearY] (fracciones de la
 *   altura de la foto), en línea recta entre medias.
 * - [occluders]: recortes que las tapan al pasar por detrás.
 */
class BiomeScenery(
    val groundEdge: List<Pair<Float, Float>>,
    val farY: Float,
    val farScale: Float,
    val nearY: Float,
    val nearScale: Float,
    val occluders: List<BiomeOccluder>,
) {
    /** Escala de perspectiva con el suelo a la altura [imageY] (fracción de la foto). */
    fun scaleAt(imageY: Float): Float {
        val t = ((imageY - farY) / (nearY - farY)).coerceIn(0f, 1f)
        return farScale + (nearScale - farScale) * t
    }
}

/**
 * El bosque: foto de un camino entre hayas (736 x 489, tools/woodland_scene). El camino se aleja
 * hasta y = 326 en el centro; a los lados el suelo acaba antes, contra los arbustos y las raíces.
 * Perspectiva y alturas de las raíces de bosque-capas.json.
 */
private val woodlandScenery = BiomeScenery(
    groundEdge = listOf(
        0 to 345, 100 to 348, 200 to 338, 250 to 328, 290 to 326, 320 to 332, 360 to 372,
        440 to 378, 480 to 362, 560 to 362, 620 to 378, 736 to 388,
    ).map { (x, y) -> x / 736f to y / 489f },
    farY = 330f / 489f,
    farScale = 0.22f,
    nearY = 480f / 489f,
    nearScale = 0.6f,
    occluders = listOf(
        BiomeOccluder(Res.drawable.biome_woodland_trunk_center, 335f / 489f),
        BiomeOccluder(Res.drawable.biome_woodland_trunk_center_right, 357f / 489f),
        BiomeOccluder(Res.drawable.biome_woodland_trunk_left_mid, 364f / 489f),
        BiomeOccluder(Res.drawable.biome_woodland_trunk_left_near, 380f / 489f),
        BiomeOccluder(Res.drawable.biome_woodland_trunk_right_near, 406f / 489f),
    ),
)

/** Escena fotográfica del bioma, si la tiene (ver [BiomeScenery]); va sin capas. */
val Biome.scenery: BiomeScenery?
    get() = when (this) {
        Biome.woodland -> woodlandScenery
        else -> null
    }
