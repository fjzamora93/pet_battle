package com.petbattle.features.defense.presentation.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.core.ui.GameBar
import com.petbattle.core.ui.GameIcon
import com.petbattle.core.ui.GameIconImage
import com.petbattle.core.ui.GameUi
import com.petbattle.core.ui.WoodButton
import com.petbattle.core.ui.WoodIconButton
import com.petbattle.core.ui.WoodPanel
import com.petbattle.features.defense.presentation.game.DefenseHudState
import com.petbattle.features.pet.presentation.widgets.tierLabel
import com.petbattle.features.pet.presentation.widgets.tierShield
import com.petbattle.shared_features.pet_combat.domain.PetCombatStats
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.presentation.label
import com.petbattle.shared_features.pet_species.presentation.widgets.BirdFrame
import com.petbattle.shared_features.pet_status.domain.GameRules
import com.petbattle.shared_features.pet_status.domain.PetNeedsBalance
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import kotlin.math.pow
import kotlin.math.roundToInt

/** Ancho fijo del panel de la reserva: el mapa ocupa siempre el resto de la pantalla. */
val ReservePanelWidth: Dp = 190.dp

/** Lado del botón de la esquina inferior derecha (jugar / acelerar). */
private val CornerButtonSize = 76.dp

/**
 * Pantalla del modo defensa, con el estilo de madera del juego, en dos zonas:
 * - a la izquierda, con ancho fijo ([ReservePanelWidth]), la **reserva**: una tarjeta por
 *   especie que aún tiene mascotas sin colocar (las que están en el mapa desaparecen de ella);
 * - el resto es el mapa ([mapContent]) con, por encima:
 *   - el cajón de la especie tocada, con sus mascotas de la reserva ordenadas por rareza
 *     (escudo de rareza, nivel, stats de combate y energía); tocar una la coge para colocarla;
 *   - arriba a la derecha, los marcadores del combate y la X para salir;
 *   - abajo, un aviso de qué hace el próximo toque y, con una mascota recogida, el botón para
 *     retirarla a descansar;
 *   - en la esquina inferior derecha, el botón de jugar (preparación) que en combate pasa a
 *     ser el de velocidad (x1 -> x2 -> x3 -> x4 -> x1);
 *   - al perder, el resumen en el centro.
 */
@Composable
fun DefenseHud(
    hud: DefenseHudState,
    pets: List<PetEntity>,
    statsOf: (PetEntity) -> PetCombatStats,
    onSelect: (String) -> Unit,
    onWithdraw: (String) -> Unit,
    onClearSelection: () -> Unit,
    onStart: () -> Unit,
    onExit: () -> Unit,
    onRestart: () -> Unit,
    timeScale: Int = 1,
    onCycleSpeed: () -> Unit = {},
    /** Especie con el cajón abierto al empezar (para tests visuales). */
    initialOpenSpecies: PetSpecies? = null,
    mapContent: @Composable BoxScope.() -> Unit,
) {
    var openSpecies by remember { mutableStateOf(initialOpenSpecies) }
    // La reserva: las que no están en el mapa, por especie.
    val reserve = pets.filter { it.id !in hud.placedIds }.groupBy { it.species }
    val speciesList = PetSpecies.entries.filter { it in reserve }
    // Si la especie abierta se queda sin mascotas en la reserva, el cajón se cierra.
    if (openSpecies != null && openSpecies !in reserve) openSpecies = null

    Row(Modifier.fillMaxSize().background(Color(0xFF26301E)).safeDrawingPadding()) {
        ReservePanel(
            speciesList = speciesList,
            reserve = reserve,
            noPets = pets.isEmpty(),
            openSpecies = openSpecies,
            onToggle = { species -> openSpecies = if (openSpecies == species) null else species },
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            mapContent()

            DrawerOverlay(
                openSpecies = openSpecies,
                reserve = reserve,
                hud = hud,
                statsOf = statsOf,
                onPick = { id ->
                    onSelect(id)
                    openSpecies = null
                },
                onClose = { openSpecies = null },
                modifier = Modifier.align(Alignment.TopStart).fillMaxHeight().padding(6.dp),
            )

            Row(
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hud.started && !hud.gameOver) {
                    ScoreChip(icon = Icons.Filled.Favorite, text = "${hud.lives}")
                    ScoreChip(gameIcon = GameIcon.Coin, text = "+${hud.kills * GameRules.GOLD_PER_ENEMY}")
                    ScoreChip(icon = Icons.Filled.Timer, text = "${hud.seconds}s")
                    ScoreChip(icon = Icons.Filled.Speed, text = "x${(hud.speedMultiplier * 10).roundToInt() / 10.0}")
                }
                WoodIconButton(GameIcon.Close, "Salir", onExit, size = 46.dp)
            }

            // Qué hace el próximo toque en el mapa.
            val editing = pets.firstOrNull { it.id == hud.editingId }
            val selected = pets.firstOrNull { it.id == hud.selectedId }
            if (!hud.gameOver) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, end = CornerButtonSize + 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        editing != null -> {
                            HintBanner("Toca la hierba para mover a ${editing.name}", Modifier.weight(1f, fill = false))
                            WoodButton("Descansar", { onWithdraw(editing.id) }, icon = GameIcon.Sleep)
                            WoodIconButton(GameIcon.Close, "Soltar", onClearSelection, size = 44.dp)
                        }
                        selected != null -> {
                            HintBanner("Toca una zona verde para colocar a ${selected.name}", Modifier.weight(1f, fill = false))
                            WoodIconButton(GameIcon.Close, "Soltar", onClearSelection, size = 44.dp)
                        }
                        !hud.started && pets.isNotEmpty() && hud.placedIds.isEmpty() ->
                            HintBanner("Elige una especie de la reserva y coloca a tus pájaros")
                        !hud.started && pets.isNotEmpty() ->
                            HintBanner("Toca un pájaro para moverlo o retirarlo, o pulsa jugar")
                    }
                }
            }

            // Esquina inferior derecha: jugar en la preparación, acelerar en el combate.
            val corner = Modifier.align(Alignment.BottomEnd).padding(8.dp)
            if (!hud.started) {
                WoodIconButton(GameIcon.Play, "Jugar", if (hud.canStart) onStart else null, corner, size = CornerButtonSize)
            } else if (!hud.gameOver) {
                SpeedButton(timeScale, onCycleSpeed, corner)
            }

            if (hud.gameOver) GameOverPanel(hud, onExit, onRestart, Modifier.align(Alignment.Center))
        }
    }
}

/** El cajón de la especie abierta, que entra y sale deslizándose por encima del mapa. */
@Composable
private fun DrawerOverlay(
    openSpecies: PetSpecies?,
    reserve: Map<PetSpecies, List<PetEntity>>,
    hud: DefenseHudState,
    statsOf: (PetEntity) -> PetCombatStats,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    // Se recuerda la última especie para que el cajón no quede vacío mientras se cierra.
    val shown = remember { mutableStateOf(openSpecies) }
    openSpecies?.let { shown.value = it }
    AnimatedVisibility(
        visible = openSpecies != null,
        modifier = modifier,
        enter = slideInHorizontally { -it / 2 } + fadeIn(),
        exit = slideOutHorizontally { -it / 2 } + fadeOut(),
    ) {
        val species = shown.value ?: return@AnimatedVisibility
        SpeciesDrawer(species, reserve[species].orEmpty(), hud, statsOf, onPick, onClose)
    }
}

/** Panel fijo de la reserva: una tarjeta rectangular por especie con mascotas sin colocar. */
@Composable
private fun ReservePanel(
    speciesList: List<PetSpecies>,
    reserve: Map<PetSpecies, List<PetEntity>>,
    noPets: Boolean,
    openSpecies: PetSpecies?,
    onToggle: (PetSpecies) -> Unit,
) {
    WoodPanel(Modifier.width(ReservePanelWidth).fillMaxHeight(), shape = RoundedCornerShape(0.dp, 16.dp, 16.dp, 0.dp)) {
        Column(Modifier.padding(8.dp)) {
            Text("Reserva", style = GameUi.text(18.sp), modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (species in speciesList) {
                    SpeciesCard(species, reserve.getValue(species).size, selected = openSpecies == species) { onToggle(species) }
                }
                when {
                    noPets -> PanelNote("Los polluelos no combaten: aliméntalos para que crezcan")
                    speciesList.isEmpty() -> PanelNote("Todos tus pájaros están en el mapa")
                }
            }
        }
    }
}

/** Tarjeta de una especie en la reserva: su pájaro, nombre y cuántas quedan. */
@Composable
private fun SpeciesCard(species: PetSpecies, count: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(GameUi.WoodDark, GameUi.Inset)))
            .border(if (selected) 3.dp else 2.dp, if (selected) GameUi.Gold else GameUi.Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BirdFrame(species, 44.dp)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(species.label, style = GameUi.text(14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (count == 1) "1 disponible" else "$count disponibles", style = GameUi.text(11.sp, GameUi.Gold))
        }
    }
}

@Composable
private fun PanelNote(text: String) {
    Text(text, style = GameUi.text(13.sp), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
}

/** Botón de jugar que acelera la partida; muestra la velocidad actual (x1..x4). */
@Composable
private fun SpeedButton(timeScale: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        WoodIconButton(GameIcon.Play, "Velocidad x$timeScale", onClick, size = CornerButtonSize, selected = timeScale > 1)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .clip(RoundedCornerShape(50))
                .background(GameUi.Inset)
                .border(2.dp, GameUi.Outline, RoundedCornerShape(50))
                .padding(horizontal = 6.dp),
        ) {
            Text("x$timeScale", style = GameUi.text(14.sp, GameUi.Gold))
        }
    }
}

/** Cajón de una especie: sus mascotas de la reserva, ordenadas por rareza (y nivel), con sus stats. */
@Composable
private fun SpeciesDrawer(
    species: PetSpecies,
    pets: List<PetEntity>,
    hud: DefenseHudState,
    statsOf: (PetEntity) -> PetCombatStats,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val sorted = pets.sortedWith(
        compareByDescending<PetEntity> { it.tier }.thenByDescending { it.progression.level }.thenBy { it.name },
    )
    WoodPanel(Modifier.width(320.dp).fillMaxHeight()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BirdFrame(species, 36.dp)
                Text(species.label, style = GameUi.text(19.sp), modifier = Modifier.weight(1f).padding(start = 6.dp))
                WoodIconButton(GameIcon.Close, "Cerrar", onClose, size = 38.dp)
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (pet in sorted) {
                    PetCard(
                        pet = pet,
                        stats = statsOf(pet),
                        selected = pet.id == hud.selectedId,
                        entryEnergy = hud.entryEnergy[pet.id],
                        onClick = { onPick(pet.id) },
                    )
                }
            }
        }
    }
}

/** Ficha de una mascota en el cajón: rareza, nivel, stats de combate y energía. */
@Composable
private fun PetCard(
    pet: PetEntity,
    stats: PetCombatStats,
    selected: Boolean,
    entryEnergy: Double?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(GameUi.WoodDark, GameUi.Inset)))
            .border(if (selected) 3.dp else 2.dp, if (selected) GameUi.Gold else GameUi.Outline, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BirdFrame(pet.species, 44.dp)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pet.name, style = GameUi.text(16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    GameIconImage(tierShield(pet.tier), 24.dp, Modifier.padding(start = 6.dp), contentDescription = tierLabel(pet.tier))
                    Text(tierLabel(pet.tier), style = GameUi.text(12.sp, GameUi.Gold), maxLines = 1)
                }
                Text(
                    if (entryEnergy != null && pet.needs.energy < entryEnergy) "Nv. ${pet.progression.level} · Descansando"
                    else "Nv. ${pet.progression.level}",
                    style = GameUi.text(12.sp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatValue(GameIcon.Damage, stats.damage.fmt(1))
            StatValue(GameIcon.Speed, "${stats.attacksPerSecond.fmt(2)}/s")
            StatValue(GameIcon.Confidence, "${stats.confidence.fmt(0)}%")
            StatValue(GameIcon.Range, stats.range.fmt(0))
        }
        Spacer(Modifier.height(6.dp))
        // Energía: la que tiene y, si ya combatió, el tope hasta el que recupera descansando.
        val energy = pet.needs.energy
        val fraction = (energy / PetNeedsBalance.MAX_STAT).toFloat()
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameIconImage(GameIcon.Energy, 20.dp)
            GameBar(
                fraction, lerp(Color(0xFFE53935), GameUi.Gold, fraction.coerceIn(0f, 1f)),
                Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            Text(
                if (entryEnergy != null) "${energy.roundToInt()}/${entryEnergy.roundToInt()}" else "${energy.roundToInt()}",
                style = GameUi.text(12.sp),
            )
        }
        if (stats.exhausted) Text("¡Agotado! Necesita descansar", style = GameUi.text(12.sp, Color(0xFFFFB4A8)))
    }
}

@Composable
private fun StatValue(icon: GameIcon, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GameIconImage(icon, 18.dp)
        Text(value, style = GameUi.text(12.sp), modifier = Modifier.padding(start = 2.dp))
    }
}

/** Aviso en una píldora de madera. */
@Composable
private fun HintBanner(text: String, modifier: Modifier = Modifier) {
    WoodPanel(modifier.widthIn(max = 480.dp), shape = RoundedCornerShape(50)) {
        Text(
            text, style = GameUi.text(15.sp), textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}

/** Marcador del combate en una píldora de madera (con icono del juego o de Material). */
@Composable
private fun ScoreChip(text: String, icon: ImageVector? = null, gameIcon: GameIcon? = null) {
    WoodPanel(shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (gameIcon != null) GameIconImage(gameIcon, 22.dp)
            if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(text, style = GameUi.text(16.sp), modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** Resumen al perder la partida, con salir y reintentar. */
@Composable
private fun GameOverPanel(hud: DefenseHudState, onExit: () -> Unit, onRestart: () -> Unit, modifier: Modifier) {
    WoodPanel(modifier) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("¡Te han superado!", style = GameUi.text(24.sp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Enemigos: ${hud.kills}", style = GameUi.text(15.sp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameIconImage(GameIcon.Coin, 22.dp)
                    Text(" +${hud.kills * GameRules.GOLD_PER_ENEMY}", style = GameUi.text(15.sp, GameUi.Gold))
                }
                Text("Tiempo: ${hud.seconds}s", style = GameUi.text(15.sp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WoodButton("Salir", onExit, icon = GameIcon.Close)
                // Vuelve a la preparación con los pájaros donde estaban.
                WoodButton("Reintentar", onRestart, icon = GameIcon.Play)
            }
        }
    }
}

/** [this] con [decimals] decimales (sin depender de String.format, que no existe en común). */
private fun Double.fmt(decimals: Int): String {
    if (decimals == 0) return roundToInt().toString()
    val factor = 10.0.pow(decimals)
    val scaled = (this * factor).roundToInt()
    val whole = scaled / factor.toInt()
    val frac = (kotlin.math.abs(scaled) % factor.toInt()).toString().padStart(decimals, '0')
    return "$whole,$frac"
}
