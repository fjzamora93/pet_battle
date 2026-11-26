package com.petbattle.design_system

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.resources.Res
import com.petbattle.resources.ui_arrow_down
import com.petbattle.resources.ui_biome
import com.petbattle.resources.ui_clean
import com.petbattle.resources.ui_wash
import com.petbattle.resources.ui_close
import com.petbattle.resources.ui_coin
import com.petbattle.resources.ui_defend
import com.petbattle.resources.ui_egg
import com.petbattle.resources.ui_feed
import com.petbattle.resources.ui_level
import com.petbattle.resources.ui_menu
import com.petbattle.resources.ui_play
import com.petbattle.resources.ui_plus
import com.petbattle.resources.ui_release
import com.petbattle.resources.ui_sleep
import com.petbattle.resources.ui_stat_beak
import com.petbattle.resources.ui_stat_climate
import com.petbattle.resources.ui_stat_digestion
import com.petbattle.resources.ui_stat_energy
import com.petbattle.resources.ui_stat_fun
import com.petbattle.resources.ui_stat_hunger
import com.petbattle.resources.ui_stat_hygiene
import com.petbattle.resources.ui_stat_plumage
import com.petbattle.resources.ui_stat_social
import com.petbattle.resources.ui_stat_confidence
import com.petbattle.resources.ui_info
import com.petbattle.resources.ui_star
import com.petbattle.resources.ui_tier_1
import com.petbattle.resources.ui_tier_2
import com.petbattle.resources.ui_tier_3
import com.petbattle.resources.ui_tier_4
import com.petbattle.resources.ui_tier_5
import com.petbattle.resources.ui_food_carrion
import com.petbattle.resources.ui_food_fruit
import com.petbattle.resources.ui_food_insects
import com.petbattle.resources.ui_food_nectar
import com.petbattle.resources.ui_food_nuts
import com.petbattle.resources.ui_food_seeds
import com.petbattle.resources.ui_food_worms
import com.petbattle.resources.ui_element_air
import com.petbattle.resources.ui_element_earth
import com.petbattle.resources.ui_element_fire
import com.petbattle.resources.ui_element_water
import com.petbattle.resources.ui_type_darkness
import com.petbattle.resources.ui_type_elemental
import com.petbattle.resources.ui_type_light
import com.petbattle.resources.ui_combat_damage
import com.petbattle.resources.ui_combat_speed
import com.petbattle.resources.ui_combat_range
import com.petbattle.resources.ui_wake
import org.jetbrains.compose.resources.DrawableResource

/**
 * Estilo común de la interfaz del juego: paneles y botones de madera con contorno oscuro y
 * texto blanco con sombra, a juego con los iconos cartoon (tools/ui_icons/generate_ui.py).
 */
object GameUi {
    val Outline = Color(0xFF3A2416)
    val WoodLight = Color(0xFFDDA05C)
    val WoodDark = Color(0xFFAF6E35)
    val Wood = Brush.verticalGradient(listOf(WoodLight, WoodDark))
    val Inset = Color(0xCC2E1E14) // fondo oscuro de contadores y barras
    val Gold = Color(0xFFFFC93C)
    val Green = Color(0xFF5DBB46)
    val Purple = Color(0xFF9B62DA)
    val PanelShape = RoundedCornerShape(16.dp)

    /** Texto blanco grueso con sombra, legible sobre cualquier fondo. */
    fun text(size: TextUnit = 16.sp, color: Color = Color.White) = TextStyle(
        color = color,
        fontSize = size,
        fontWeight = FontWeight.ExtraBold,
        shadow = Shadow(Color(0xCC000000), Offset(1.5f, 2f), 2f),
    )
}


/** Iconos de la interfaz (ui_*.png). */
enum class GameIcon(val res: DrawableResource) {
    Coin(Res.drawable.ui_coin), Egg(Res.drawable.ui_egg), Biome(Res.drawable.ui_biome),
    Menu(Res.drawable.ui_menu), Level(Res.drawable.ui_level), Feed(Res.drawable.ui_feed),
    Sleep(Res.drawable.ui_sleep), Wake(Res.drawable.ui_wake), Clean(Res.drawable.ui_clean),
    Defend(Res.drawable.ui_defend), Wash(Res.drawable.ui_wash), Release(Res.drawable.ui_release), Plus(Res.drawable.ui_plus),
    ArrowDown(Res.drawable.ui_arrow_down), Close(Res.drawable.ui_close), Play(Res.drawable.ui_play),
    Hunger(Res.drawable.ui_stat_hunger), Energy(Res.drawable.ui_stat_energy), Fun(Res.drawable.ui_stat_fun),
    Hygiene(Res.drawable.ui_stat_hygiene), Social(Res.drawable.ui_stat_social), Climate(Res.drawable.ui_stat_climate),
    Plumage(Res.drawable.ui_stat_plumage), Digestion(Res.drawable.ui_stat_digestion), Beak(Res.drawable.ui_stat_beak),
    Confidence(Res.drawable.ui_stat_confidence), Info(Res.drawable.ui_info),
    Damage(Res.drawable.ui_combat_damage), Speed(Res.drawable.ui_combat_speed), Range(Res.drawable.ui_combat_range),
    Fire(Res.drawable.ui_element_fire), Air(Res.drawable.ui_element_air),
    Water(Res.drawable.ui_element_water), Earth(Res.drawable.ui_element_earth),
    Light(Res.drawable.ui_type_light), Darkness(Res.drawable.ui_type_darkness),
    Elemental(Res.drawable.ui_type_elemental),
    Seeds(Res.drawable.ui_food_seeds), Nuts(Res.drawable.ui_food_nuts), Fruit(Res.drawable.ui_food_fruit),
    Insects(Res.drawable.ui_food_insects), Worms(Res.drawable.ui_food_worms), Nectar(Res.drawable.ui_food_nectar),
    Carrion(Res.drawable.ui_food_carrion),
    Star(Res.drawable.ui_star),
    Tier1(Res.drawable.ui_tier_1), Tier2(Res.drawable.ui_tier_2), Tier3(Res.drawable.ui_tier_3),
    Tier4(Res.drawable.ui_tier_4), Tier5(Res.drawable.ui_tier_5),
}
