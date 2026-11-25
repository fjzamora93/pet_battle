package com.petbattle.features.defense.presentation.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.petbattle.core.ui.drawSpriteFrame
import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.presentation.BirdSheet
import com.petbattle.shared_features.pet_status.domain.PetNeedsBalance
import com.petbattle.features.defense.domain.DefenseBalance
import com.petbattle.shared_features.pet_combat.domain.PetCombatBalance
import com.petbattle.features.defense.domain.entities.EnemyKind
import kotlin.math.floor
import kotlin.math.sin

/** Todas las imágenes que necesita la escena de defensa, cargadas una vez por la pantalla. */
class DefenseSprites(
    val map: ImageBitmap,
    val enemies: Map<EnemyKind, ImageBitmap>,
    val skills: Map<PetElement, ImageBitmap>,
    val pets: Map<PetSpecies, ImageBitmap>,
)

/**
 * Disposición de la hoja de un enemigo (`enemy_slime.png` para el gusano y
 * `enemy_butterfly.png`; se generan con tools/enemy_sprites): frames de [FRAME] px mirando a
 * la izquierda, con la misma escala y línea del suelo en todas las poses.
 * Fila [MOVE_ROW]: movimiento en bucle; [DEATH_ROW]: muerte; [HURT_ROW]: cara de dolor,
 * que se muestra mientras parpadea tras recibir un golpe.
 */
class EnemySheet(
    val moveFrames: Int,
    /** Segundos por frame de movimiento a la velocidad base. */
    val moveStep: Double,
    val deathFrames: Int,
    val deathStep: Double,
    /** Línea del suelo dentro del frame. */
    val groundY: Float,
    /** Lo más alto del sprite al moverse (la barra de vida va encima). */
    val bodyTop: Float,
    /** Centro del cuerpo dentro del frame: adonde apuntan los disparos y van los efectos. */
    val bodyCenterY: Float,
    /** Altura extra a la que se dibuja al moverse, en px de mapa (los que vuelan). */
    val lift: Float = 0f,
) {
    /** Altura del centro del cuerpo sobre el suelo al moverse, en px de mapa. */
    val bodyHeight get() = (groundY - bodyCenterY) * SCALE + lift

    companion object {
        const val FRAME = 128
        const val MOVE_ROW = 0
        const val DEATH_ROW = 1
        const val HURT_ROW = 2
        const val SCALE = 0.5f
        const val SIZE = FRAME * SCALE

        /** Los dibujos no son pixel art a escala 1: se suavizan al reducirlos, como los pájaros. */
        val FILTER = FilterQuality.Medium

        /**
         * Centro del cuerpo para el que están pensadas las capas de estado de las habilidades
         * (quemado, ralentizado...): el del slime original, que coincide con el del gusano.
         */
        const val OVERLAY_BODY_CENTER_Y = 84f

        val worm = EnemySheet(
            moveFrames = 5, moveStep = 0.13, deathFrames = 4, deathStep = 0.14,
            groundY = 116f, bodyTop = 52f, bodyCenterY = 84f,
        )
        val butterfly = EnemySheet(
            moveFrames = 5, moveStep = 0.09, deathFrames = 3, deathStep = 0.16,
            groundY = 120f, bodyTop = 4f, bodyCenterY = 64f, lift = 14f,
        )

        fun of(kind: EnemyKind) = when (kind) {
            EnemyKind.worm -> worm
            EnemyKind.butterfly -> butterfly
        }
    }
}

/** Disposición de `skill_<element>.png`: 4x3 frames de 32x32. */
object SkillSheet {
    const val FRAME = 32
    const val PROJECTILE_ROW = 0
    const val IMPACT_ROW = 1
    const val STATUS_ROW = 2
    const val SIZE = FRAME * 2f
}

/** Dibuja texto en píxeles de mapa, cacheando los layouts (las etiquetas rara vez cambian). */
class TextPainter(private val measurer: TextMeasurer) {
    private val cache = HashMap<Pair<String, Long>, TextLayoutResult>()

    private fun layout(text: String, color: Long, sizePx: Float, density: Float): TextLayoutResult {
        if (cache.size > 200) cache.clear()
        return cache.getOrPut(text to (color * 31 + sizePx.toLong())) {
            measurer.measure(
                text,
                TextStyle(
                    color = Color(color),
                    fontSize = (sizePx / density).sp,
                    fontWeight = FontWeight.ExtraBold,
                    shadow = Shadow(Color.Black, Offset(1.5f, 1.5f), 0f),
                ),
            )
        }
    }

    /** [at] es el centro inferior del texto. */
    fun draw(scope: DrawScope, text: String, at: Offset, color: Long = 0xFFFFFFFF, sizePx: Float = 18f, alpha: Float = 1f) {
        val l = layout(text, color, sizePx, scope.density)
        scope.drawText(l, topLeft = Offset(at.x - l.size.width / 2f, at.y - l.size.height), alpha = alpha)
    }
}


/** Dibuja toda la escena en coordenadas de mapa (quien llama fija la transformación). */
fun DrawScope.drawDefense(game: DefenseGame, sprites: DefenseSprites, text: TextPainter) {
    val map = game.map
    drawImage(
        sprites.map,
        dstSize = IntSize(map.width, map.height),
        filterQuality = FilterQuality.Medium,
    )

    if (game.showPlacement) {
        val a = 0.18f + 0.1f * sin(game.time * 3).toFloat()
        val tint = Color(0xFF7CFF6B).copy(alpha = a)
        for (cell in game.placeableCells) drawRect(tint, cell.topLeft, cell.size)
    }

    // Orden de profundidad: más abajo en el mapa = más al frente.
    val drawables = buildList<Pair<Float, () -> Unit>> {
        game.defenders.values.forEach { d -> add(d.position.y to { drawDefender(d, game, sprites, text) }) }
        game.enemies.forEach { e -> add(e.position.y to { drawEnemy(e, sprites, text) }) }
        game.effects.filter { it.kind == AnimEffect.Kind.EnemyDeath }.forEach { fx ->
            add(fx.position.y to { drawEnemyDeath(fx, sprites) })
        }
    }.sortedBy { it.first }
    drawables.forEach { it.second() }

    for (p in game.projectiles) {
        val image = sprites.skills.getValue(p.element)
        val col = floor(p.age / 0.07).toInt() % 4
        rotate(Math_toDegrees(p.angle), pivot = p.position) {
            drawSpriteFrame(
                image, SkillSheet.FRAME, col, SkillSheet.PROJECTILE_ROW,
                p.position - Offset(SkillSheet.SIZE / 2, SkillSheet.SIZE / 2), SkillSheet.SIZE,
            )
        }
    }
    for (fx in game.effects) {
        if (fx.kind != AnimEffect.Kind.SkillImpact) continue
        drawSpriteFrame(
            sprites.skills.getValue(fx.element!!), SkillSheet.FRAME, fx.frame, SkillSheet.IMPACT_ROW,
            fx.position - Offset(SkillSheet.SIZE / 2, SkillSheet.SIZE / 2), SkillSheet.SIZE,
        )
    }
    drawHandPet(game, sprites)
    for (t in game.texts) {
        text.draw(this, t.text, t.position, t.color, t.sizePx, alpha = (1 - t.age / t.life).toFloat().coerceIn(0f, 1f))
    }
    for (m in game.markers) {
        val a = (m.age / 0.3).let { 1 - (m.age / InvalidMarker.LIFE) }.toFloat().coerceIn(0f, 1f)
        val c = Color(0xFFE53935).copy(alpha = a)
        val p = m.position
        drawLine(c, p + Offset(-12f, -12f), p + Offset(12f, 12f), 6f, StrokeCap.Round)
        drawLine(c, p + Offset(12f, -12f), p + Offset(-12f, 12f), 6f, StrokeCap.Round)
    }
}

/**
 * La mascota que se lleva en la mano, bajo el puntero (hace de cursor): su alcance y su huella
 * en verde si se puede soltar ahí o en rojo si no.
 */
private fun DrawScope.drawHandPet(game: DefenseGame, sprites: DefenseSprites) {
    val pet = game.handPet ?: return
    val at = game.pointer ?: return
    val ok = game.canDropAt(at)
    val size = Defender.SIZE
    // Mismas medidas que un Defender colocado en [at].
    val feet = at + Offset(0f, (DefenseBalance.PET_FOOTPRINT_RADIUS * 0.6).toFloat())
    val chest = feet - Offset(0f, size * 0.45f)
    val range = PetCombatBalance.range(pet.species).toFloat()
    drawCircle(Color(0x18FFFFFF), range, chest)
    drawCircle(Color(0x88FFFFFF), range, chest, style = Stroke(2f))
    val foot = if (ok) Color(0xFF7CFF6B) else Color(0xFFE53935)
    drawCircle(foot.copy(alpha = 0.45f), DefenseBalance.PET_FOOTPRINT_RADIUS.toFloat(), at)
    drawCircle(foot, DefenseBalance.PET_FOOTPRINT_RADIUS.toFloat(), at, style = Stroke(3f))
    val col = BirdSheet.loopFrame(BirdSheet.IDLE_ROW, pet.species, game.time)
    drawSpriteFrame(
        sprites.pets.getValue(pet.species), BirdSheet.FRAME_SIZE, col, BirdSheet.IDLE_ROW,
        Offset(feet.x - size / 2, feet.y - size), size, alpha = if (ok) 0.85f else 0.5f,
        filterQuality = BirdSheet.FILTER,
    )
}

private fun Math_toDegrees(rad: Float) = rad * 180f / kotlin.math.PI.toFloat()

private fun DrawScope.drawDefender(d: Defender, game: DefenseGame, sprites: DefenseSprites, text: TextPainter) {
    val size = Defender.SIZE
    val feet = d.position
    val chest = d.muzzle
    // Alcance.
    // Alcance de su especie.
    drawCircle(Color(0x14FFFFFF), d.range.toFloat(), chest)
    drawCircle(Color(0x55FFFFFF), d.range.toFloat(), chest, style = Stroke(2f))
    // Sombra.
    drawOval(Color(0x40000000), Offset(feet.x - size * 0.25f, feet.y - 6f), Size(size * 0.5f, 12f))

    // Efecto de aparición + aplastamiento al disparar.
    val pop = if (d.age < 0.3) 1 + 0.15f * sin(d.age / 0.3 * kotlin.math.PI).toFloat() else 1f
    val squash = if (d.shotAge < 0.16) sin(d.shotAge / 0.16 * kotlin.math.PI).toFloat() else 0f
    val col = BirdSheet.loopFrame(BirdSheet.IDLE_ROW, d.pet.species, game.time)
    // Recogida para recolocarla: semitransparente.
    val alpha = if (game.editingId == d.pet.id) 0.45f else 1f
    scale(pop * (1 + 0.08f * squash), pop * (1 - 0.08f * squash), pivot = feet) {
        drawSpriteFrame(
            sprites.pets.getValue(d.pet.species), BirdSheet.FRAME_SIZE, col, BirdSheet.IDLE_ROW,
            Offset(feet.x - size / 2, feet.y - size), size, flipX = d.facingRight,
            alpha = alpha, filterQuality = BirdSheet.FILTER,
        )
    }
    // Nombre · nivel (o agotada) y barra de energía.
    val top = feet.y - size
    val label = if (d.exhausted) "${d.pet.name} · ¡Agotado!" else "${d.pet.name} · Nv.${d.pet.progression.level}"
    text.draw(this, label, Offset(feet.x, top + size * 0.16f))
    val energy = (d.pet.needs.energy / PetNeedsBalance.MAX_STAT).coerceIn(0.0, 1.0).toFloat()
    val w = 56f
    val bar = Offset(feet.x - w / 2, top + size * 0.16f + 2)
    drawRect(Color(0xAA000000), bar, Size(w, 6f))
    drawRect(lerp(Color(0xFFE53935), Color(0xFFFFD54F), energy), bar + Offset(1f, 1f), Size((w - 2) * energy, 4f))
}

private fun DrawScope.drawEnemy(e: Enemy, sprites: DefenseSprites, text: TextPainter) {
    val sheet = EnemySheet.of(e.entity.kind)
    val size = EnemySheet.SIZE
    // Los que vuelan se dibujan por encima del suelo (más su vaivén); la sombra se queda abajo.
    val topLeft = Offset(e.position.x - size / 2, e.position.y - sheet.groundY * EnemySheet.SCALE - sheet.lift - e.hover)
    val shadowW = if (sheet.lift > 0) size * 0.45f else size * 0.8f
    drawOval(Color(0x40000000), Offset(e.position.x - shadowW / 2, e.position.y - 5f), Size(shadowW, 10f))
    // Al recibir un golpe muestra la cara de dolor en lugar del ciclo de movimiento.
    val hurt = e.flash > 0
    val col = if (hurt) 0 else floor(e.animT / sheet.moveStep).toInt().mod(sheet.moveFrames)
    drawSpriteFrame(
        sprites.enemies.getValue(e.entity.kind), EnemySheet.FRAME, col,
        if (hurt) EnemySheet.HURT_ROW else EnemySheet.MOVE_ROW, topLeft, size,
        flipX = e.facingRight, filterQuality = EnemySheet.FILTER,
    )
    // Las capas de efectos ocupan el mismo frame que el enemigo; se desplazan para que su
    // centro caiga sobre el cuerpo de cada tipo.
    val overlayTopLeft = topLeft + Offset(0f, (sheet.bodyCenterY - EnemySheet.OVERLAY_BODY_CENTER_Y) * EnemySheet.SCALE)
    fun overlay(element: PetElement, step: Double) = drawSpriteFrame(
        sprites.skills.getValue(element), SkillSheet.FRAME, floor(e.effectT / step).toInt() % 4,
        SkillSheet.STATUS_ROW, overlayTopLeft, size, flipX = e.facingRight,
    )
    if (e.entity.isSlowed) overlay(PetElement.water, 0.15)
    if (e.entity.isBurning) overlay(PetElement.fire, 0.1)
    if (e.gust > 0) overlay(PetElement.air, 0.08)

    // Nivel + barra de salud sobre la cabeza.
    val barW = 40f
    val bar = Offset(e.position.x - barW / 2, topLeft.y + sheet.bodyTop * EnemySheet.SCALE - 10)
    drawRect(Color(0xAA000000), bar, Size(barW, 6f))
    drawRect(Color(0xFF6CE07A), bar + Offset(1f, 1f), Size((barW - 2) * e.entity.healthFraction.toFloat(), 4f))
    text.draw(this, "Nv.${e.entity.level}", Offset(e.position.x, bar.y - 1), sizePx = 13f)
}

private fun DrawScope.drawEnemyDeath(fx: AnimEffect, sprites: DefenseSprites) {
    val kind = fx.enemyKind ?: return
    val sheet = EnemySheet.of(kind)
    val size = EnemySheet.SIZE
    // La muerte ocurre en el suelo: la mariposa cae y se posa en la hierba.
    val topLeft = Offset(fx.position.x - size / 2, fx.position.y - sheet.groundY * EnemySheet.SCALE)
    // El último frame se desvanece en vez de desaparecer de golpe.
    val fade = ((fx.age - (fx.frames - 1) * fx.stepTime) / fx.stepTime).toFloat().coerceIn(0f, 1f)
    drawSpriteFrame(
        sprites.enemies.getValue(kind), EnemySheet.FRAME, fx.frame, EnemySheet.DEATH_ROW, topLeft, size,
        flipX = fx.flipX, alpha = 1f - fade, filterQuality = EnemySheet.FILTER,
    )
}
