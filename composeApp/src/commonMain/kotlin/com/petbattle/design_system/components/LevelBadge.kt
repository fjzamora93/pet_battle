package com.petbattle.design_system.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.design_system.GameIcon
import com.petbattle.design_system.GameUi
import com.petbattle.shared_features.biomes.presentation.label
import com.petbattle.shared_features.pet_food.presentation.label
import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_skills.domain.PetProgression
import com.petbattle.shared_features.pet_skills.domain.PetSkillsCatalog
import com.petbattle.shared_features.pet_skills.domain.PetType
import com.petbattle.shared_features.pet_skills.presentation.label
import com.petbattle.shared_features.pet_species.domain.biomes
import com.petbattle.shared_features.pet_species.domain.diet
import com.petbattle.shared_features.pet_species.domain.isChick
import com.petbattle.shared_features.pet_species.presentation.label
import com.petbattle.shared_features.pet_status.domain.GrowthRules
import com.petbattle.shared_features.pet_status.domain.TierRules
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity

/**
 * Nivel de la mascota seleccionada (arriba en el centro): insignia con el número y barra de
 * experiencia (su nombre está en el panel de estados). Un polluelo muestra en su lugar el
 * huevo y cuánto le falta para crecer. El escudo toma el color de su rareza (tier). La
 * flechita de al lado despliega su ficha: especie, rareza (estrellas),
 * elemento, tipo, habilidad, bioma, biomas posibles y dieta.
 */
@Composable
fun LevelBadge(pet: PetEntity, modifier: Modifier = Modifier) {
    var showInfo by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (showInfo) 180f else 0f)
    val chick = pet.species.isChick
    val progression = pet.progression
    val (fraction, caption) = if (chick) {
        val eaten = GrowthRules.mealsEaten(pet)
        eaten.toFloat() / GrowthRules.MEALS_TO_GROW to "Crecimiento $eaten / ${GrowthRules.MEALS_TO_GROW}"
    } else if (PetProgression.isMaxLevel(progression)) {
        1f to "Nivel máximo"
    } else {
        PetProgression.levelProgress(progression).toFloat() to "${progression.xp} / ${PetProgression.xpToNextLevel(progression.level)} XP"
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                // El escudo toma el color de su rareza (tier).
                GameIconImage(if (chick) GameIcon.Egg else tierShield(pet.tier), 50.dp)
                if (!chick) Text("${progression.level}", style = GameUi.text(20.sp), modifier = Modifier.offset(y = (-2).dp))
            }
            Column(Modifier.offset(x = (-6).dp)) {
                GameBar(fraction, if (chick) GameUi.Green else GameUi.Purple, Modifier.width(170.dp), height = 16.dp)
                Text(caption, style = GameUi.text(11.sp), modifier = Modifier.padding(start = 8.dp, top = 1.dp))
            }
            GameIconImage(
                GameIcon.ArrowDown, 26.dp,
                Modifier.clip(RoundedCornerShape(50)).clickable { showInfo = !showInfo }.rotate(arrow),
                if (showInfo) "Ocultar ficha" else "Ver ficha",
            )
        }
        AnimatedVisibility(showInfo) { PetSheet(pet) }
    }
}

/**
 * Ficha de la mascota: especie, elemento, tipo, habilidad, dónde vive, sus biomas posibles y
 * su dieta (o, si es polluelo, cómo va a crecer).
 */
@Composable
private fun PetSheet(pet: PetEntity) {
    // Altura limitada con scroll: en un móvil en horizontal no cabe entera.
    WoodPanel(Modifier.padding(top = 6.dp).width(300.dp).heightIn(max = 250.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp)) {
            SheetLine(null, "Especie", pet.species.label)
            TierLine(pet.tier)
            if (pet.species.isChick) {
                val destined = pet.destinedSpecies
                if (destined != null) {
                    // Hijo de una pareja: su especie ya está decidida, coma lo que coma.
                    Text(
                        "Nació de una pareja: aún no tiene elemento ni tipo, pero al crecer será como sus padres.",
                        style = GameUi.text(12.sp, Color(0xFFF3E3C8)),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    SheetLine(null, "Crecerá a", destined.label)
                    SheetLine(null, "Comidas", "${GrowthRules.mealsEaten(pet)} / ${GrowthRules.MEALS_TO_GROW}")
                } else {
                    val favourites = pet.meals.filterValues { it > 0 && it == pet.meals.values.maxOrNull() }.keys
                    Text(
                        "Aún no tiene elemento ni tipo: al crecer será una especie de su bioma que coma su comida favorita.",
                        style = GameUi.text(12.sp, Color(0xFFF3E3C8)),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    SheetLine(null, "Comidas", "${GrowthRules.mealsEaten(pet)} / ${GrowthRules.MEALS_TO_GROW}")
                    SheetLine(null, "Favorita", favourites.joinToString { it.label.lowercase() }.ifEmpty { "todavía ninguna" })
                }
            } else {
                val skills = pet.progression
                val skill = PetSkillsCatalog.basicAttackFor(skills)
                SheetLine(skills.element.gameIcon, "Elemento", skills.element.label)
                SheetLine(skills.type.gameIcon, "Tipo", skills.type.label)
                SheetLine(skills.element.gameIcon, "Habilidad", "${skill.name}: ${skill.description}")
            }
            SheetLine(GameIcon.Biome, "Vive en", pet.biome.label.lowercase())
            if (pet.species.isChick) {
                SheetLine(GameIcon.Biome, "Biomas posibles", "cualquiera")
                SheetLine(GameIcon.Feed, "Come", "de todo")
            } else {
                SheetLine(GameIcon.Biome, "Biomas posibles", pet.species.biomes.joinToString { it.label.lowercase() })
                SheetLine(GameIcon.Feed, "Come", pet.species.diet.joinToString { it.label.lowercase() })
            }
        }
    }
}

/** Rareza: su nombre y las estrellas (doradas las que tiene, apagadas las que no). */
@Composable
private fun TierLine(tier: Int) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = GameUi.Gold)) { append("Rareza: ") }
                append(tierLabel(tier))
            },
            style = GameUi.text(13.sp),
            modifier = Modifier.padding(end = 6.dp),
        )
        for (i in TierRules.MIN_TIER..TierRules.MAX_TIER) {
            GameIconImage(GameIcon.Star, 18.dp, Modifier.alpha(if (i <= tier) 1f else 0.25f))
        }
    }
}

/** Nombre de cada rareza. */
fun tierLabel(tier: Int) = when (tier) {
    1 -> "Común"
    2 -> "Poco común"
    3 -> "Raro"
    4 -> "Épico"
    else -> "Legendario"
}

/** Escudo del nivel del color de cada rareza. */
fun tierShield(tier: Int) = when (tier) {
    1 -> GameIcon.Tier1
    2 -> GameIcon.Tier2
    3 -> GameIcon.Tier3
    4 -> GameIcon.Tier4
    else -> GameIcon.Tier5
}

/** Una línea de la ficha: icono opcional, título en dorado y valor. */
@Composable
private fun SheetLine(icon: GameIcon?, title: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) GameIconImage(icon, 22.dp, Modifier.padding(end = 6.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = GameUi.Gold)) { append("$title: ") }
                append(value)
            },
            style = GameUi.text(13.sp),
        )
    }
}

/** Icono del juego de cada elemento. */
private val PetElement.gameIcon get() = when (this) {
    PetElement.fire -> GameIcon.Fire
    PetElement.air -> GameIcon.Air
    PetElement.water -> GameIcon.Water
    PetElement.earth -> GameIcon.Earth
}

/** Icono del juego de cada tipo. */
private val PetType.gameIcon get() = when (this) {
    PetType.light -> GameIcon.Light
    PetType.darkness -> GameIcon.Darkness
    PetType.elemental -> GameIcon.Elemental
}
