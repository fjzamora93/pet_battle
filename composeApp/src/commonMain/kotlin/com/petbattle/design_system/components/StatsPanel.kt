package com.petbattle.design_system.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.design_system.GameIcon
import com.petbattle.design_system.GameUi
import com.petbattle.shared_features.pet_combat.domain.PetCombatBalance
import com.petbattle.shared_features.pet_combat.domain.PetCombatStats
import com.petbattle.shared_features.pet_combat.domain.PetCombatStatsRules
import com.petbattle.shared_features.pet_species.presentation.label
import com.petbattle.shared_features.pet_status.domain.BreedingRules
import com.petbattle.shared_features.pet_status.domain.PetDerivedNeeds
import com.petbattle.shared_features.pet_status.domain.PetNeedsBalance
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import kotlin.math.roundToInt

private enum class StatsTab { needs, combat }

/**
 * Panel de la mascota seleccionada (arriba a la derecha). Plegado solo muestra su nombre, para
 * dejar la pantalla limpia; desplegado muestra también su especie y todo lo demás, en dos
 * pestañas:
 * - Necesidades: hambre, descanso, diversión y digestión; "Salud" (higiene, plumaje, pico y
 *   garras) y "Ambientales" (social y clima, que no dependen de la interacción).
 * - Combate: daño, velocidad y confianza, cada uno "actual / total", y el rango (fijo por especie).
 * El botón "i" explica la pestaña abierta, y abajo está el botón de liberarla (solo si hay
 * [onRelease]: el modo cuidado no lo ofrece). Hace scroll si no cabe. [socialization] y
 * [combat] se calculan fuera (dependen del bioma).
 *
 * Con [arrowButtonSize], plegado es solo un botón redondo con la flecha, de ese tamaño (el
 * modo cuidado); desplegado es el panel de siempre.
 */
@Composable
fun StatsPanel(
    pet: PetEntity,
    socialization: Double,
    /** Satisfacción global (0..100): media de todas sus necesidades (PetDerivedNeeds.satisfaction). */
    satisfaction: Double,
    combat: PetCombatStats,
    onRelease: (() -> Unit)?,
    modifier: Modifier = Modifier,
    arrowButtonSize: Dp? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(StatsTab.needs) }
    var showInfo by remember { mutableStateOf(false) }
    if (arrowButtonSize != null && !expanded) {
        WoodIconButton(GameIcon.ArrowDown, "Ver estados", { expanded = true }, modifier, size = arrowButtonSize)
        return
    }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f)
    // Plegado solo se ve la cabecera (más estrecha); desplegado, todo.
    WoodPanel(modifier.width(if (expanded) 250.dp else 170.dp)) {
        Column(
            Modifier
                .animateContentSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pet.name,
                    style = GameUi.text(15.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                GameIconImage(GameIcon.ArrowDown, 22.dp, Modifier.rotate(arrow), if (expanded) "Ocultar estados" else "Ver estados")
            }
            if (expanded) {
                Text(pet.species.label, style = GameUi.text(12.sp, Color(0xFFF3E3C8)))
                // Por encima del umbral, con otra de su especie en el bioma, puede criar.
                val canBreedNow = satisfaction > BreedingRules.SATISFACTION_TO_BREED
                StatLine(
                    GameIcon.Egg, "Satisfacción global", "${satisfaction.format(0)} %",
                    (satisfaction / PetNeedsBalance.MAX_STAT).toFloat(), "Satisfacción global ${satisfaction.format(0)}%",
                )
                Text(
                    if (canBreedNow) "Puede criar con otra de su especie" else "Cría con más de ${BreedingRules.SATISFACTION_TO_BREED.format(0)} %",
                    style = GameUi.text(10.sp, if (canBreedNow) GameUi.Gold else Color(0xFFE8DCC8)),
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TabChip("Necesidades", tab == StatsTab.needs, { tab = StatsTab.needs }, Modifier.weight(1f))
                    TabChip("Combate", tab == StatsTab.combat, { tab = StatsTab.combat }, Modifier.weight(1f))
                    GameIconImage(
                        GameIcon.Info, 26.dp,
                        Modifier.clip(RoundedCornerShape(50)).clickable { showInfo = !showInfo },
                        "Cómo funciona esta pestaña",
                    )
                }
                if (showInfo) InfoBox(tab)
                Spacer(Modifier.height(4.dp))
                when (tab) {
                    StatsTab.needs -> {
                        NeedRow(GameIcon.Hunger, "Hambre", pet.needs.satiety)
                        NeedRow(GameIcon.Energy, "Descanso", pet.needs.energy)
                        NeedRow(GameIcon.Fun, "Diversión", pet.needs.happiness)
                        NeedRow(GameIcon.Digestion, "Digestión", pet.needs.digestion)
                        SectionTitle("Salud")
                        NeedRow(GameIcon.Hygiene, "Higiene", pet.needs.hygiene)
                        NeedRow(GameIcon.Plumage, "Plumaje", pet.needs.plumage)
                        NeedRow(GameIcon.Beak, "Pico y garras", pet.needs.beakAndClaws)
                        // Lo que no depende de la interacción del jugador.
                        SectionTitle("Ambientales")
                        NeedRow(GameIcon.Social, "Social", socialization)
                        NeedRow(GameIcon.Climate, "Clima", PetDerivedNeeds.climate(pet))
                    }
                    StatsTab.combat -> {
                        CombatRow(GameIcon.Damage, "Daño", combat.damage, combat.maxDamage, decimals = 1)
                        CombatRow(GameIcon.Speed, "Velocidad", combat.attacksPerSecond, combat.maxAttacksPerSecond, decimals = 2, unit = " /s")
                        CombatRow(GameIcon.Confidence, "Confianza", combat.confidence, combat.maxConfidence, decimals = 0, unit = " %")
                        // El rango es fijo por especie: la barra lo compara con la especie de más alcance.
                        StatLine(GameIcon.Range, "Rango", combat.range.format(0), (combat.range / combat.maxRange).toFloat(), "Rango ${combat.range.format(0)}")
                        if (combat.exhausted) Text("¡Agotado! Necesita descansar", style = GameUi.text(12.sp, Color(0xFFFFB4A8)))
                    }
                }
                if (onRelease != null) {
                    Spacer(Modifier.height(8.dp))
                    WoodButton("Liberar", onRelease, Modifier.fillMaxWidth(), icon = GameIcon.Release)
                }
            }
        }
    }
}

/** Título de un grupo de necesidades. */
@Composable
private fun SectionTitle(text: String) {
    Text(text, style = GameUi.text(12.sp, GameUi.Gold), modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

/** Explicación del botón "i": distinta según la pestaña abierta. */
@Composable
private fun InfoBox(tab: StatsTab) {
    Column(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GameUi.Inset)
            .padding(8.dp),
    ) {
        when (tab) {
            StatsTab.needs -> NeedsInfo()
            StatsTab.combat -> CombatInfo()
        }
    }
}

/** Cómo suben (+) y bajan (−) las necesidades. */
@Composable
private fun NeedsInfo() {
    Text("Todas bajan con el tiempo, cada una a su ritmo (descansando, más despacio):", style = GameUi.text(11.sp))
    InfoLine(GameIcon.Hunger, "Hambre", "+ comida de su dieta")
    InfoLine(GameIcon.Energy, "Descanso", "+ dormir y la noche · − combatir")
    InfoLine(GameIcon.Fun, "Diversión", "+ mimos (tocarla)")
    InfoLine(GameIcon.Digestion, "Digestión", "+ su comida con hambre · − comida ajena o sin hambre")
    InfoLine(GameIcon.Hygiene, "Salud", "+ limpiar (higiene, plumaje, pico y garras) · combatir ensucia y despeina, pero afila pico y garras")
    InfoLine(GameIcon.Social, "Ambientales", "no dependen de ti: social según quién viva en su bioma; clima, de momento siempre lleno")
}

/** Cómo se calcula cada stat de combate. */
@Composable
private fun CombatInfo() {
    Text("Daño y confianza: actual = total × la media de sus tres necesidades (−0,33 % por punto que falta).", style = GameUi.text(11.sp))
    InfoLine(GameIcon.Damage, "Daño", "total ${PetCombatBalance.DAMAGE_PER_LEVEL.format(1)} × nivel (${PetCombatBalance.LEVEL_ZERO_DAMAGE.format(0)} en nivel 0) · hambre, plumaje, pico y garras")
    InfoLine(GameIcon.Speed, "Velocidad", "total ${PetCombatStatsRules.maxAttacksPerSecond.format(2)} ataques/s · por punto que falta: descanso −0,5 %, digestión y clima −0,33 %")
    InfoLine(GameIcon.Confidence, "Confianza", "total ${PetCombatBalance.MAX_CONFIDENCE.format(0)} % de acierto · diversión, social, higiene")
    InfoLine(GameIcon.Range, "Rango", "alcance fijo de su especie: cuanto más grande el pájaro, más lejos llega")
    Text("Sin descanso ataca a la mitad de velocidad.", style = GameUi.text(11.sp, Color(0xFFE8DCC8)), modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun InfoLine(icon: GameIcon, stat: String, needs: String) {
    // [stat] en dorado y [needs] a continuación, ajustándose al ancho.
    Row(Modifier.padding(top = 4.dp)) {
        GameIconImage(icon, 18.dp)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = GameUi.Gold)) { append("$stat: ") }
                append(needs)
            },
            style = GameUi.text(11.sp),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text,
        style = GameUi.text(11.sp),
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(shape)
            .background(if (selected) GameUi.WoodLight else GameUi.Inset)
            .border(2.dp, GameUi.Outline, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
    )
}

/** Necesidad: icono + nombre + barra de color (rojo vacío -> verde lleno). */
@Composable
private fun NeedRow(icon: GameIcon, label: String, value: Double) {
    val t = (value / PetNeedsBalance.MAX_STAT).coerceIn(0.0, 1.0).toFloat()
    StatLine(icon, label, null, t, "$label ${value.toInt()}%")
}

/** Stat de combate: icono + nombre + "actual / total" + barra (actual sobre total). */
@Composable
private fun CombatRow(icon: GameIcon, label: String, current: Double, max: Double, decimals: Int, unit: String = "") {
    val t = if (max <= 0) 0f else (current / max).coerceIn(0.0, 1.0).toFloat()
    StatLine(icon, label, "${current.format(decimals)} / ${max.format(decimals)}$unit", t, "$label ${current.format(decimals)} de ${max.format(decimals)}$unit")
}

@Composable
private fun StatLine(icon: GameIcon, label: String, value: String?, fraction: Float, description: String) {
    val color = lerp(Color(0xFFE5484D), Color(0xFF5DBB46), fraction * fraction)
    val animated by animateFloatAsState(fraction)
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        GameIconImage(icon, 26.dp, contentDescription = description)
        Column(Modifier.padding(start = 6.dp)) {
            Row {
                Text(label, style = GameUi.text(11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (value != null) Text(value, style = GameUi.text(11.sp, GameUi.Gold), maxLines = 1)
            }
            GameBar(animated, color, Modifier.fillMaxWidth(), height = 10.dp)
        }
    }
}

/** Número con [decimals] decimales (coma decimal, como en español). */
private fun Double.format(decimals: Int): String {
    if (decimals <= 0) return roundToInt().toString()
    var factor = 1
    repeat(decimals) { factor *= 10 }
    val scaled = (this * factor).roundToInt()
    val whole = scaled / factor
    val frac = (scaled % factor).toString().padStart(decimals, '0')
    return "$whole,$frac"
}
