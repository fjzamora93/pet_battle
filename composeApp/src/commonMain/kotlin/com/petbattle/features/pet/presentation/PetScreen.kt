package com.petbattle.features.pet.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.core.time.DayPhase
import com.petbattle.core.time.systemNowMillis
import com.petbattle.core.ui.GameIcon
import com.petbattle.core.ui.GameIconImage
import com.petbattle.core.ui.GameUi
import com.petbattle.core.ui.OptionsButton
import com.petbattle.core.ui.PlatformBackHandler
import com.petbattle.core.ui.WoodButton
import com.petbattle.core.ui.WoodIconButton
import com.petbattle.core.ui.WoodPanel
import com.petbattle.features.pet.presentation.game.BiomeScene
import com.petbattle.features.pet.presentation.game.GroundFood
import com.petbattle.features.pet.presentation.game.HandTool
import com.petbattle.features.pet.presentation.widgets.ActionBar
import com.petbattle.features.pet.presentation.widgets.ActionBarWidth
import com.petbattle.features.pet.presentation.widgets.CoinCounter
import com.petbattle.features.pet.presentation.widgets.FoodPickerSheet
import com.petbattle.features.pet.presentation.widgets.GameMenuDrawer
import com.petbattle.features.pet.presentation.widgets.LevelBadge
import com.petbattle.features.pet.presentation.widgets.MenuTab
import com.petbattle.features.pet.presentation.widgets.PetNameDialog
import com.petbattle.features.pet.presentation.widgets.StatsPanel
import com.petbattle.shared_features.biomes.domain.isPlayable
import com.petbattle.shared_features.biomes.presentation.label
import com.petbattle.shared_features.pet_combat.domain.PetCombatStatsRules
import com.petbattle.shared_features.pet_food.presentation.label
import com.petbattle.shared_features.pet_species.presentation.label
import com.petbattle.shared_features.pet_status.application.PetController
import com.petbattle.shared_features.pet_status.domain.FeedResult
import com.petbattle.shared_features.pet_status.domain.GameRules
import com.petbattle.shared_features.pet_status.domain.LifeEvent
import com.petbattle.shared_features.pet_status.domain.PetDerivedNeeds
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import kotlinx.coroutines.delay

/** Lo que ocupa el botón del menú de juego (la escena deja ese hueco libre a la derecha). */
private val MenuButtonSize = 68.dp

/**
 * Pantalla principal. El jugador empieza en la pradera y compra huevos y biomas con el oro de
 * los combates (nunca elige especie); los polluelos crecen según lo que comen.
 *
 * Distribución del HUD sobre la escena del bioma:
 * - arriba a la izquierda, el engranaje de opciones y las monedas;
 * - arriba en el centro, el nivel de la mascota seleccionada;
 * - arriba a la derecha, sus estados (desplegable, con scroll);
 * - abajo a la izquierda, la columna de acciones (comida y agua se llevan en la mano y se usan
 *   tocando la escena: el agua limpia a la mascota tocada y la comida se deja en el suelo);
 * - abajo a la derecha, el botón del menú de juego (cajón con "Huevos" y "Biomas").
 *
 * El único lugar donde se encuentran el controlador y la escena.
 */
@Composable
fun PetScreen(controller: PetController, onDefend: () -> Unit, onOptions: () -> Unit) {
    val gameState by controller.gameState.collectAsState()
    val dayPhase by controller.dayPhase.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var menuTab by remember { mutableStateOf(MenuTab.eggs) }
    var naming by remember { mutableStateOf(false) }
    var choosingFood by remember { mutableStateOf(false) }
    var confirmRelease by remember { mutableStateOf<PetEntity?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) } // aviso breve (comida, compras...)
    var handTool by remember { mutableStateOf<HandTool?>(null) }
    val groundFood = remember { mutableStateListOf<GroundFood>() }
    var nextFoodId by remember { mutableStateOf(0L) }

    // Huevos puestos y eclosiones: se cuentan con el mismo aviso.
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            feedback = when (event) {
                is LifeEvent.EggLaid -> "¡${event.parents.first} y ${event.parents.second} han puesto un huevo!"
                is LifeEvent.Hatched -> "¡Ha nacido ${event.name}!"
            }
        }
    }

    // El aviso desaparece solo al cabo de un rato.
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2_500)
            feedback = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val current = gameState
        val biome = current?.currentBiome
        val pet = current?.active
        if (current == null || biome == null) {
            // Sin bioma solo mientras carga (la partida nueva y las migradas siempre tienen uno).
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@BoxWithConstraints
        }

        // La comida dejada en un bioma no viaja con el jugador.
        LaunchedEffect(biome) { groundFood.clear() }
        // Cada comida desaparece sola si nadie se la come a tiempo.
        for (item in groundFood) {
            key(item.id) {
                LaunchedEffect(item.id) {
                    delay(GameRules.GROUND_FOOD_LIFETIME_MS)
                    groundFood.removeAll { it.id == item.id }
                }
            }
        }
        // Sin mascota seleccionada no hay barra de acciones: nada en la mano.
        LaunchedEffect(pet == null) { if (pet == null) handTool = null }
        // Atrás suelta la herramienta antes que nada.
        if (handTool != null) PlatformBackHandler { handTool = null }

        fun openMenu(tab: MenuTab) {
            handTool = null
            menuTab = tab
            menuOpen = true
        }

        // Compra un huevo si alcanza el oro; si no, avisa de cuánto falta.
        fun requestEgg() {
            val missing = GameRules.goldMissingForEgg(current)
            if (missing == 0) {
                menuOpen = false
                naming = true
            } else {
                feedback = "Te faltan $missing de oro para un huevo (cuesta ${GameRules.EGG_PRICE})"
            }
        }

        BiomeScene(
            biome = biome,
            pets = current.biomePets,
            activeId = current.activeId,
            // Tocar una mascota la selecciona y la mima.
            onPetTapped = { id ->
                if (id != current.activeId) controller.switchTo(id)
                controller.cuddle()
            },
            modifier = Modifier.fillMaxSize(),
            dayPhase = dayPhase,
            walkStartInset = if (pet != null) ActionBarWidth else 0.dp,
            walkEndInset = MenuButtonSize,
            handTool = handTool,
            groundFood = groundFood,
            wantsFood = GameRules::wantsGroundFood,
            onPetCleaned = { id ->
                val target = current.byId(id) ?: return@BiomeScene
                if (!controller.clean(id)) {
                    feedback = if (target.needs.isSleeping) "${target.name} está durmiendo" else "${target.name} no necesita un baño"
                }
            },
            onFoodDropped = { food, x, depth ->
                groundFood += GroundFood(nextFoodId++, food, x, depth, systemNowMillis())
            },
            onFoodEaten = { foodId, petId ->
                val item = groundFood.firstOrNull { it.id == foodId } ?: return@BiomeScene
                groundFood.remove(item)
                val eater = current.byId(petId) ?: return@BiomeScene
                feedback = when (controller.feedPet(petId, item.food)) {
                    FeedResult.eaten -> "¡A ${eater.name} le encanta! Ñam ñam"
                    FeedResult.overate -> "${eater.name} come sin hambre: le sienta mal"
                    FeedResult.disliked -> "${eater.name} se come ${item.food.label.lowercase()} a disgusto"
                    FeedResult.grew -> {
                        val adult = controller.gameState.value?.byId(petId)?.species
                        "¡${eater.name} ha crecido! Ahora es: ${adult?.label ?: "?"}"
                    }
                    null -> null
                }
            },
        )

        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
            Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
                OptionsButton({
                    handTool = null
                    onOptions()
                })
                CoinCounter(current.gold, onShop = { openMenu(MenuTab.eggs) }, Modifier.padding(start = 6.dp))
            }
            if (pet != null) {
                LevelBadge(pet, Modifier.align(Alignment.TopCenter))
                val social = PetDerivedNeeds.socialization(pet, current)
                StatsPanel(
                    pet = pet,
                    socialization = social,
                    satisfaction = PetDerivedNeeds.satisfaction(pet, current),
                    combat = PetCombatStatsRules.statsFor(pet, social),
                    onRelease = { confirmRelease = pet },
                    // Acaba por encima del botón del menú; si no cabe, hace scroll.
                    modifier = Modifier.align(Alignment.TopEnd).heightIn(max = screenHeight - MenuButtonSize - 40.dp),
                )
                ActionBar(
                    pet = pet,
                    handTool = handTool,
                    // Cada opción suelta lo que hubiera en la mano: solo hay una seleccionada a la vez.
                    // Con comida en la mano, el botón la suelta; si no, abre el menú de comidas.
                    onFeed = {
                        if (handTool is HandTool.FoodItem) {
                            handTool = null
                        } else {
                            handTool = null
                            choosingFood = true
                        }
                    },
                    onToggleSleep = {
                        handTool = null
                        controller.toggleSleep()
                    },
                    onClean = { handTool = if (handTool == HandTool.Water) null else HandTool.Water },
                    onDefend = {
                        handTool = null
                        onDefend()
                    },
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            } else {
                EmptyBiomeCard(
                    eggPrice = GameRules.eggPrice(current),
                    onBuyEgg = ::requestEgg,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            WoodIconButton(
                GameIcon.Menu, "Menú de juego", { openMenu(menuTab) },
                Modifier.align(Alignment.BottomEnd), size = MenuButtonSize,
            )
            feedback?.let { FeedbackBubble(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) }
        }

        GameMenuDrawer(
            open = menuOpen,
            tab = menuTab,
            gameState = current,
            onTab = { menuTab = it },
            onBuyEgg = ::requestEgg,
            onPickBiome = { picked ->
                menuOpen = false
                when {
                    !picked.isPlayable -> feedback = "${picked.label} estará disponible próximamente"
                    current.isUnlocked(picked) -> controller.enterBiome(picked)
                    controller.buyBiome(picked) -> feedback = "¡Has desbloqueado ${picked.label.lowercase()}!"
                    else -> feedback = "Te faltan ${GameRules.BIOME_PRICE - current.gold} de oro para " +
                        "desbloquear ${picked.label.lowercase()} (cuesta ${GameRules.BIOME_PRICE})"
                }
            },
            onClose = { menuOpen = false },
        )

        if (choosingFood) {
            FoodPickerSheet(
                onPicked = { food ->
                    choosingFood = false
                    handTool = HandTool.FoodItem(food)
                },
                onDismiss = { choosingFood = false },
            )
        }
        if (naming) {
            PetNameDialog(
                biome = biome,
                price = GameRules.eggPrice(current),
                submit = { name -> controller.hatch(name) },
                onDismiss = { naming = false },
            )
        }
        confirmRelease?.let { target ->
            AlertDialog(
                onDismissRequest = { confirmRelease = null },
                title = { Text("¿Liberar a ${target.name}?") },
                text = { Text("Volará libre y no volverá. No se puede deshacer.") },
                confirmButton = {
                    Button(
                        onClick = {
                            confirmRelease = null
                            controller.release(target.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("Liberar") }
                },
                dismissButton = { TextButton(onClick = { confirmRelease = null }) { Text("Cancelar") } },
            )
        }
    }
}

/** Aviso breve en la parte de abajo (p. ej. si le gustó la comida). */
@Composable
private fun FeedbackBubble(text: String, modifier: Modifier = Modifier) {
    WoodPanel(modifier.padding(horizontal = 90.dp), shape = RoundedCornerShape(50)) {
        Text(text, style = GameUi.text(15.sp), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
    }
}

/** Bioma sin mascotas: invita a comprar (o estrenar gratis) un huevo. */
@Composable
private fun EmptyBiomeCard(eggPrice: Int, onBuyEgg: () -> Unit, modifier: Modifier = Modifier) {
    WoodPanel(modifier.padding(24.dp)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GameIconImage(GameIcon.Egg, 72.dp)
            Text("Este bioma está vacío", style = GameUi.text(22.sp))
            Spacer(Modifier.height(6.dp))
            Text(
                "Cría un polluelo: lo que le des de comer decidirá en qué pájaro se convierte.",
                style = GameUi.text(14.sp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            WoodButton(
                if (eggPrice == 0) "Nueva mascota (gratis)" else "Nueva mascota · $eggPrice de oro",
                onBuyEgg,
                icon = GameIcon.Egg,
            )
        }
    }
}
