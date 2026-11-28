package com.petbattle.shared_features.pet_species.presentation

import androidx.compose.ui.graphics.FilterQuality
import com.petbattle.resources.Res
import com.petbattle.resources.pet_bee_eater
import com.petbattle.resources.pet_blue_jay
import com.petbattle.resources.pet_care_bird_1
import com.petbattle.resources.pet_cardinal
import com.petbattle.resources.pet_cedar_waxwing
import com.petbattle.resources.pet_chucao
import com.petbattle.resources.pet_chickadee
import com.petbattle.resources.pet_crow
import com.petbattle.resources.pet_house_finch
import com.petbattle.resources.pet_hummingbird
import com.petbattle.resources.pet_kestrel
import com.petbattle.resources.pet_magpie
import com.petbattle.resources.pet_red_robin
import com.petbattle.resources.pet_vanilla_chick
import com.petbattle.resources.pet_white_dove
import com.petbattle.resources.pet_wood_thrush
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.floor
import kotlin.math.min

/**
 * Cómo está organizada una hoja de pájaro: cuántas columnas tiene, cuántos frames hay en cada
 * animación y si trae despegue y aterrizaje. Las filas son siempre las mismas (ver [BirdSheet]).
 */
enum class BirdLayout {
    /** Rejilla de 8 columnas: 8 frames de vuelo, 6 de reposo y de andar, 4 en el resto. */
    classic,

    /** Hoja ampliada del arrendajo: 16 columnas, con despegue y aterrizaje. */
    extended,

    /**
     * El pájaro del modo cuidado ([CareBird]): 8 frames de vuelo, 6 de reposo, 8 de saltar, 6 de
     * reacción al mimo (estira las alas), 3 de descansar sentado y 5 de dormir.
     */
    care,

    /**
     * Petirrojo 3D (tools/bird_sprites/petirrojo): 16 columnas, 12 frames de vuelo y 16 en el
     * resto, con despegue, aterrizaje y comer (fila [BirdSheet.EAT]). Todas las animaciones
     * comparten encuadre, y las patas quedan en [BirdSheet.feetLine] (más arriba que en las
     * demás hojas, para que quepan los dedos al posarse).
     */
    robin,
}

/** Disposición de la hoja de cada especie. */
val PetSpecies.layout: BirdLayout
    get() = when (this) {
        PetSpecies.blueJay -> BirdLayout.extended
        PetSpecies.redRobin -> BirdLayout.robin
        else -> BirdLayout.classic
    }

/**
 * El pájaro que se ve siempre en el modo cuidado, sea cual sea la especie de la mascota. Se monta
 * con tools/bird_sprites/care_sheet.py (ver docs/sprites-de-pajaros.md).
 */
object CareBird {
    val sheet: DrawableResource = Res.drawable.pet_care_bird_1
    val layout = BirdLayout.care
}

/** Disposición común a todos los spritesheets de pájaros: una animación por fila, frames de 256x256. */
object BirdSheet {
    /** Lado de un frame en píxeles de la hoja (sprites de alta resolución). */
    const val FRAME_SIZE = 256

    /**
     * Rejilla lógica de diseño de un frame: las coordenadas de detalles superpuestos (p. ej.
     * las manchas de suciedad) se expresan en esta rejilla de 64 unidades.
     */
    const val DESIGN_GRID = 64

    /** Los sprites de pájaro se escalan con suavizado (no son pixel art de texel grande). */
    val FILTER = FilterQuality.Medium

    /**
     * Rejilla de la hoja: 8 columnas x 6 filas, una animación por fila (cada una con su número
     * de frames, ver [frames]). Una especie con más frames (más fluida) tiene la hoja más ancha.
     * Las hojas se montan con tools/bird_sprites/build_grid.py.
     */
    const val COLUMNS = 8

    /** Índice de fila de cada animación en la hoja. */
    const val FLY_ROW = 0 // volar (8 frames)
    const val IDLE_ROW = 1 // reposo de pie (6)
    const val HOP_ROW = 2 // andar / saltar (6)
    const val LOOK_UP_ROW = 3 // mirar arriba: su reacción al tocarla (4)
    const val REST_ROW = 4 // descansar despierta, de noche (4)
    const val SLEEP_ROW = 5 // dormir (4)

    /** Filas normales (una por animación); detrás pueden ir las transiciones. */
    const val ROWS = 6

    /**
     * Transiciones entre estar posado y volar (solo algunas especies, ver [hasTransitions]).
     * Son animaciones largas: cada una ocupa varias filas seguidas detrás de las normales (ver
     * [cell]). Se identifican como las filas normales, con estos números.
     */
    const val TAKEOFF = 6 // despegar: de pie -> agacharse, abrir alas -> en el aire
    const val LANDING = 7 // aterrizar: en el aire -> posar las patas -> de pie

    /** Comer en el suelo (solo algunas hojas, ver [hasEat]); va detrás de las transiciones. */
    const val EAT = 8

    /** Línea de las patas (px desde arriba de la casilla) en las hojas normales. */
    const val FEET_LINE = 252f

    // Cada función tiene dos versiones: por disposición de hoja ([BirdLayout]) y, por comodidad,
    // por especie (con la disposición de su hoja).

    /** Si la hoja tiene despegue y aterrizaje propios (si no, despega y se posa sin más). */
    fun hasTransitions(layout: BirdLayout) = layout == BirdLayout.extended || layout == BirdLayout.robin
    fun hasTransitions(species: PetSpecies) = hasTransitions(species.layout)

    /** Si la hoja tiene animación de comer (si no, picotea inclinando el sprite). */
    fun hasEat(layout: BirdLayout) = frames(EAT, layout) > 0

    /** Columnas de la hoja. */
    fun columns(layout: BirdLayout) = when (layout) {
        BirdLayout.extended, BirdLayout.robin -> 16
        else -> COLUMNS
    }
    fun columns(species: PetSpecies) = columns(species.layout)

    /**
     * Línea de las patas en la casilla (px de 256 desde arriba). El petirrojo la tiene más
     * arriba porque al posarse curva los dedos por debajo de ella (build_grid.py, modo atlas).
     */
    fun feetLine(layout: BirdLayout) = if (layout == BirdLayout.robin) 236.3f else FEET_LINE

    /**
     * Cuánto bajar el sprite (en fracción de su lado) para que sus patas pisen el suelo igual
     * que las de una hoja normal.
     */
    fun groundShift(layout: BirdLayout) = (FEET_LINE - feetLine(layout)) / FRAME_SIZE
    fun groundShift(species: PetSpecies) = groundShift(species.layout)

    /** Frame del despegue en que el pájaro deja el suelo: desde ahí ya se desplaza. */
    fun liftoffFrame(layout: BirdLayout) = if (layout == BirdLayout.robin) 8 else 16

    /** Número de frames de la animación [row] (fila normal o transición). */
    fun frames(row: Int, layout: BirdLayout): Int = when (layout) {
        // Hoja ampliada (tools/bird_sprites/arrendajo): 12 frames de vuelo, 16 en las demás
        // filas y 31 en cada transición.
        BirdLayout.extended -> when (row) {
            FLY_ROW -> 12
            TAKEOFF, LANDING -> 31
            EAT -> 0
            else -> 16
        }
        // Petirrojo 3D: 12 de vuelo y 16 en todo lo demás (también despegue, aterrizaje y comer).
        BirdLayout.robin -> if (row == FLY_ROW) 12 else 16
        // Pájaro del modo cuidado (tools/bird_sprites/care_sheet.py).
        BirdLayout.care -> when (row) {
            FLY_ROW, HOP_ROW -> 8
            IDLE_ROW, LOOK_UP_ROW -> 6
            REST_ROW -> 3
            SLEEP_ROW -> 5
            else -> 0
        }
        BirdLayout.classic -> when (row) {
            FLY_ROW -> 8
            IDLE_ROW, HOP_ROW -> 6
            TAKEOFF, LANDING, EAT -> 0
            else -> 4
        }
    }

    /**
     * Duración de cada frame (s) cuando no son todos iguales; null = iguales (ver
     * [cycleSeconds]). En el petirrojo, la fase en tierra y la del aire van a ritmos distintos.
     */
    fun frameDurations(row: Int, layout: BirdLayout): DoubleArray? = when {
        layout != BirdLayout.robin -> null
        row == TAKEOFF -> DoubleArray(16) { if (it < 8) 0.095 else 0.060 }
        row == LANDING -> DoubleArray(16) { if (it < 8) 0.060 else 0.107 }
        else -> null
    }
    fun frames(row: Int, species: PetSpecies): Int = frames(row, species.layout)

    /**
     * Casilla (columna, fila) de la hoja del frame [frame] de la animación [row]: en las filas
     * normales, (frame, row); las transiciones siguen en la fila de abajo al llegar al final.
     */
    fun cell(row: Int, frame: Int, layout: BirdLayout): Pair<Int, Int> {
        if (row < ROWS) return frame to row
        val cols = columns(layout)
        var base = ROWS
        for (r in TAKEOFF until row) base += (frames(r, layout) + cols - 1) / cols
        return frame % cols to base + frame / cols
    }
    fun cell(row: Int, frame: Int, species: PetSpecies) = cell(row, frame, species.layout)

    /**
     * Segundos que dura un ciclo de la animación de la fila [row]. En bucle es igual para todas
     * las especies: con más frames, cada uno dura menos y el movimiento es más fluido. Mirar
     * arriba (la reacción al tocarla, de una sola vez) dura más en el arrendajo, cuya animación
     * sube la cabeza y la vuelve a bajar, y en el pájaro del modo cuidado, que estira las alas;
     * las demás acaban mirando arriba.
     */
    fun cycleSeconds(row: Int, layout: BirdLayout): Double = frameDurations(row, layout)?.sum() ?: when {
        // Petirrojo 3D: las duraciones de sus sprites (tools/bird_sprites/petirrojo/LEEME.md).
        layout == BirdLayout.robin -> when (row) {
            FLY_ROW -> 0.556
            HOP_ROW -> 0.667
            LOOK_UP_ROW -> 1.067 // salta
            REST_ROW, SLEEP_ROW -> 2.0
            else -> 1.0 // reposo (posado) y comer
        }
        else -> uniformCycle(row, layout)
    }

    private fun uniformCycle(row: Int, layout: BirdLayout): Double = when (row) {
        FLY_ROW -> 0.48
        IDLE_ROW, HOP_ROW -> 1.8
        LOOK_UP_ROW -> when (layout) {
            BirdLayout.extended -> 1.2
            BirdLayout.care -> 0.9
            else -> 0.4
        }
        REST_ROW -> 2.8
        TAKEOFF, LANDING -> 1.2
        EAT -> 1.0
        else -> 3.6 // dormir
    }
    fun cycleSeconds(row: Int, species: PetSpecies) = cycleSeconds(row, species.layout)

    /** Segundos desde que empieza a despegar hasta que deja el suelo ([liftoffFrame]). */
    fun liftoffSeconds(layout: BirdLayout): Double {
        val n = frames(TAKEOFF, layout)
        if (n == 0) return 0.0
        val liftoff = liftoffFrame(layout).coerceAtMost(n)
        return frameDurations(TAKEOFF, layout)?.take(liftoff)?.sum()
            ?: (cycleSeconds(TAKEOFF, layout) * liftoff / n)
    }
    fun liftoffSeconds(species: PetSpecies) = liftoffSeconds(species.layout)

    /** Frame en el instante [t] (s) dentro de un ciclo, respetando las duraciones por frame. */
    private fun frameAt(row: Int, layout: BirdLayout, t: Double): Int {
        val n = frames(row, layout)
        val durations = frameDurations(row, layout)
            ?: return floor(t / cycleSeconds(row, layout) * n).toInt()
        var acc = 0.0
        for (i in 0 until n) {
            acc += durations[i]
            if (t < acc) return i
        }
        return n
    }

    /** Frame de la animación en bucle de la fila [row] en el instante [time] (s). */
    fun loopFrame(row: Int, layout: BirdLayout, time: Double): Int {
        val n = frames(row, layout)
        return frameAt(row, layout, time.mod(cycleSeconds(row, layout))).mod(n)
    }
    fun loopFrame(row: Int, species: PetSpecies, time: Double) = loopFrame(row, species.layout, time)

    /** Frame de una animación de una sola vez (mirar arriba) tras [elapsed] s: se queda en el último. */
    fun onceFrame(row: Int, layout: BirdLayout, elapsed: Double): Int {
        val n = frames(row, layout)
        return min(n - 1, frameAt(row, layout, elapsed)).coerceAtLeast(0)
    }
    fun onceFrame(row: Int, species: PetSpecies, elapsed: Double) = onceFrame(row, species.layout, elapsed)
}

/** Datos de presentación de cada especie: nombre visible y spritesheet. */
val PetSpecies.label: String
    get() = when (this) {
        PetSpecies.chick -> "Polluelo"
        PetSpecies.cardinal -> "Cardenal"
        PetSpecies.blueJay -> "Arrendajo"
        PetSpecies.cedarWaxwing -> "Ampelis"
        PetSpecies.chickadee -> "Carbonero"
        PetSpecies.crow -> "Cuervo"
        PetSpecies.houseFinch -> "Pinzón"
        PetSpecies.hummingbird -> "Colibrí"
        PetSpecies.magpie -> "Urraca"
        PetSpecies.redRobin -> "Petirrojo"
        PetSpecies.beeEater -> "Abejaruco"
        PetSpecies.whiteDove -> "Paloma blanca"
        PetSpecies.woodThrush -> "Zorzal"
        PetSpecies.kestrel -> "Cernícalo"
        PetSpecies.chucao -> "Chucao"
    }

val PetSpecies.sheet: DrawableResource
    get() = when (this) {
        PetSpecies.chick -> Res.drawable.pet_vanilla_chick
        PetSpecies.cardinal -> Res.drawable.pet_cardinal
        PetSpecies.blueJay -> Res.drawable.pet_blue_jay
        PetSpecies.cedarWaxwing -> Res.drawable.pet_cedar_waxwing
        PetSpecies.chickadee -> Res.drawable.pet_chickadee
        PetSpecies.crow -> Res.drawable.pet_crow
        PetSpecies.houseFinch -> Res.drawable.pet_house_finch
        PetSpecies.hummingbird -> Res.drawable.pet_hummingbird
        PetSpecies.magpie -> Res.drawable.pet_magpie
        PetSpecies.redRobin -> Res.drawable.pet_red_robin
        PetSpecies.beeEater -> Res.drawable.pet_bee_eater
        PetSpecies.whiteDove -> Res.drawable.pet_white_dove
        PetSpecies.woodThrush -> Res.drawable.pet_wood_thrush
        PetSpecies.kestrel -> Res.drawable.pet_kestrel
        PetSpecies.chucao -> Res.drawable.pet_chucao
    }

/** Tamaño relativo al dibujarla: el polluelo es más pequeño que los adultos. */
val PetSpecies.spriteScale: Float get() = if (this == PetSpecies.chick) 0.6f else 1f
