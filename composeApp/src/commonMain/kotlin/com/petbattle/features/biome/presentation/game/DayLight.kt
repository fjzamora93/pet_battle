package com.petbattle.features.biome.presentation.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.petbattle.core.time.DayPhase

/**
 * Cómo se ve el bioma en un momento del día. Dos capas:
 * - una matriz de color sobre la imagen: multiplica cada canal ([red], [green], [blue]),
 *   cambia la [saturation] y suma [haze] (0..255) para dar neblina o luz difusa;
 * - un degradado encima: [sky] arriba (luz del cielo) y [ground] abajo (sombra del suelo).
 * Entre franjas se interpola con [lerp] para que el cambio sea gradual.
 */
data class DayLook(
    val red: Float = 1f,
    val green: Float = 1f,
    val blue: Float = 1f,
    val saturation: Float = 1f,
    val haze: Float = 0f,
    val sky: Color = Color.Transparent,
    val ground: Color = Color.Transparent,
) {
    /**
     * Matriz de color de este aspecto. Con [strength] < 1 se queda a medio camino de la imagen
     * original.
     */
    fun colorFilter(strength: Float = 1f): ColorFilter = ColorFilter.colorMatrix(colorMatrix(strength))

    /** La matriz de [colorFilter], para combinarla con otras (p. ej. la desaturación de los pájaros). */
    fun colorMatrix(strength: Float = 1f): ColorMatrix {
        val l = lerp(Midday, this, strength)
        val m = ColorMatrix().apply { setToSaturation(l.saturation) }
        m.timesAssign(
            ColorMatrix(
                floatArrayOf(
                    l.red, 0f, 0f, 0f, l.haze,
                    0f, l.green, 0f, 0f, l.haze,
                    0f, 0f, l.blue, 0f, l.haze,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        return m
    }

    /** Si este aspecto deja la imagen tal cual (no hace falta filtrarla). */
    val isNeutral get() = this == Midday

    companion object {
        /** Mediodía: la imagen original, sin filtro. */
        val Midday = DayLook()

        /** Mañana: el alba. Cielo rosa coral, luz dorada baja, algo de neblina y colores suaves. */
        val Morning = DayLook(
            red = 1.12f, green = 0.94f, blue = 0.86f,
            saturation = 0.8f, haze = 12f,
            sky = Color(0xFFFF7F9A).copy(alpha = 0.56f),
            ground = Color(0xFFFFBE5C).copy(alpha = 0.24f),
        )

        /** Tarde: atardecer anaranjado, cálido, con el suelo hacia el violeta. */
        val Evening = DayLook(
            red = 1.1f, green = 0.9f, blue = 0.76f,
            saturation = 1.08f,
            sky = Color(0xFFFF7A33).copy(alpha = 0.3f),
            ground = Color(0xFF66204E).copy(alpha = 0.2f),
        )

        /** Noche: oscura y azulada, poco color. */
        val Night = DayLook(
            red = 0.28f, green = 0.36f, blue = 0.68f,
            saturation = 0.42f,
            sky = Color(0xFF050822).copy(alpha = 0.52f),
            ground = Color(0xFF02030B).copy(alpha = 0.5f),
        )

        fun of(phase: DayPhase) = when (phase) {
            DayPhase.night -> Night
            DayPhase.morning -> Morning
            DayPhase.midday -> Midday
            DayPhase.evening -> Evening
        }
    }
}

/** Interpolación entre dos aspectos ([t] = 0 -> [a], 1 -> [b]). */
fun lerp(a: DayLook, b: DayLook, t: Float) = DayLook(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    saturation = a.saturation + (b.saturation - a.saturation) * t,
    haze = a.haze + (b.haze - a.haze) * t,
    sky = lerp(a.sky, b.sky, t),
    ground = lerp(a.ground, b.ground, t),
)

/**
 * Degradado de cielo (arriba) y, si [ground], de suelo (abajo) de [look], sobre todo el
 * lienzo. Los fondos por capas solo usan el del cielo (el filtro no toca el suelo). Con
 * [blendMode] SrcAtop cae solo sobre lo ya pintado en la capa (p. ej. un recorte del fondo).
 */
fun DrawScope.drawDayGradient(look: DayLook, ground: Boolean = true, blendMode: BlendMode = BlendMode.SrcOver) {
    val h = size.height
    if (look.sky.alpha > 0.01f) {
        drawRect(
            Brush.verticalGradient(listOf(look.sky, look.sky.copy(alpha = 0f)), 0f, h * 0.65f),
            size = Size(size.width, h * 0.65f),
            blendMode = blendMode,
        )
    }
    if (ground && look.ground.alpha > 0.01f) {
        drawRect(
            Brush.verticalGradient(listOf(look.ground.copy(alpha = 0f), look.ground), h * 0.45f, h),
            topLeft = Offset(0f, h * 0.45f),
            size = Size(size.width, h * 0.55f),
            blendMode = blendMode,
        )
    }
}
