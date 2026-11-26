package com.petbattle.features.care.presentation

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.core.time.systemNowMillis
import com.petbattle.core.ui.PlatformBackHandler
import com.petbattle.core.ui.isRoundScreen
import com.petbattle.design_system.GameIcon
import com.petbattle.design_system.GameUi
import com.petbattle.design_system.components.FeedbackBubble
import com.petbattle.design_system.components.FoodPickerSheet
import com.petbattle.design_system.components.GameIconImage
import com.petbattle.design_system.components.OptionsButton
import com.petbattle.design_system.components.PetNameDialog
import com.petbattle.design_system.components.StatsPanel
import com.petbattle.design_system.components.WoodButton
import com.petbattle.design_system.components.WoodIconButton
import com.petbattle.design_system.components.WoodPanel
import com.petbattle.features.biome.presentation.cleanRefusedMessage
import com.petbattle.features.biome.presentation.feedAndDescribe
import com.petbattle.features.biome.presentation.game.BiomeScene
import com.petbattle.features.biome.presentation.game.GroundFood
import com.petbattle.features.biome.presentation.game.HandTool
import com.petbattle.features.biome.presentation.lifeEventMessage
import com.petbattle.shared_features.pet_combat.domain.PetCombatStatsRules
import com.petbattle.shared_features.pet_food.presentation.icon
import com.petbattle.shared_features.pet_species.presentation.CareBird
import com.petbattle.shared_features.pet_status.application.PetController
import com.petbattle.shared_features.pet_status.domain.GameRules
import com.petbattle.shared_features.pet_status.domain.PetDerivedNeeds
import kotlinx.coroutines.delay

/** Aumento de la escena del bioma en el modo cuidado (desde el centro del suelo). */
private const val CARE_ZOOM = 1.8f

/** En el reloj, algo más: la pantalla es muy pequeña. */
private const val COMPACT_CARE_ZOOM = 2.2f

/**
 * Profundidades a las que se mueve la mascota (0 = delante, 1 = al fondo): cerca, para verla
 * grande. En pantallas pequeñas (reloj) algo más al fondo, para que la fila de botones de abajo
 * no le tape las patas.
 */
private val CareDepths = 0.0..0.45
private val CompactCareDepths = 0.45..0.85

/** Engranaje y flecha de los estados: en pantalla grande y en el reloj. */
private val TopButton = 60.dp
private val CompactTopButton = 44.dp

/** Botones de acción de abajo: en pantalla grande, en reloj rectangular y en reloj redondo. */
private val ActionButton = 68.dp
private val CompactActionButton = 58.dp
private val RoundActionButton = 46.dp

/** Por debajo de este ancho o alto la pantalla se trata como la de un reloj. */
private val CompactScreen = 360.dp

/**
 * Modo cuidado ("tamagotchi"): la mascota seleccionada sola, más grande, sobre una ampliación
 * del fondo de su bioma, dibujada siempre con el mismo pájaro ([CareBird]) sea cual sea su especie. Se mueve por un trozo pequeño del suelo (lo que se ve) y cerca del
 * frente. Es una versión simplificada del bioma: solo alimentar, limpiar, dormir y, si
 * [onBiome] no es null, volver al bioma. Arriba están el engranaje de opciones y el panel
 * desplegable de estados (sin liberar, ni huevos, ni cambio de bioma).
 *
 * En la variante de reloj [onBiome] es null: es la única pantalla de la app. En pantallas
 * pequeñas los botones van en fila abajo, centrados; si además es redonda ([roundScreen]), los
 * márgenes dejan todo dentro del círculo.
 */
@Composable
fun CareScreen(
    controller: PetController,
    onBiome: (() -> Unit)?,
    onOptions: () -> Unit,
    /** Pantalla redonda: se dejan libres sus esquinas (sustituible en tests). */
    roundScreen: Boolean = isRoundScreen(),
) {
    val gameState by controller.gameState.collectAsState()
    val dayPhase by controller.dayPhase.collectAsState()
    var choosingFood by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var handTool by remember { mutableStateOf<HandTool?>(null) }
    val groundFood = remember { mutableStateListOf<GroundFood>() }
    var nextFoodId by remember { mutableStateOf(0L) }

    LaunchedEffect(controller) {
        controller.events.collect { event -> feedback = lifeEventMessage(event) }
    }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2_500)
            feedback = null
        }
    }

    // Atrás: primero suelta la herramienta; después vuelve al bioma (en el reloj, sale de la app).
    if (handTool != null) {
        PlatformBackHandler { handTool = null }
    } else if (onBiome != null) {
        PlatformBackHandler(onBiome)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val compact = screenWidth < CompactScreen || screenHeight < CompactScreen
        val topButton = if (compact) CompactTopButton else TopButton
        val buttonSize = when {
            // En columna: hasta su tamaño, sin pasar del hueco que queda bajo el engranaje
            // (4 botones, sus separaciones y los márgenes).
            !compact -> minOf(ActionButton, (screenHeight - topButton - 16.dp - 16.dp - 8.dp - 6.dp * 3) / 4)
            roundScreen -> RoundActionButton // en fila abajo, dentro del círculo
            else -> CompactActionButton
        }
        val current = gameState
        val biome = current?.currentBiome
        if (current == null || biome == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@BoxWithConstraints
        }
        val pet = current.active?.takeIf { !it.isEgg }

        // Cada comida desaparece sola si nadie se la come a tiempo.
        for (item in groundFood) {
            key(item.id) {
                LaunchedEffect(item.id) {
                    delay(GameRules.GROUND_FOOD_LIFETIME_MS)
                    groundFood.removeAll { it.id == item.id }
                }
            }
        }
        LaunchedEffect(pet == null) { if (pet == null) handTool = null }

        // La escena va ampliada desde el centro de abajo: solo se ve su parte central, así que
        // la zona de paseo se recorta a lo visible (y, con la columna de botones, a su derecha).
        val zoom = if (compact) COMPACT_CARE_ZOOM else CARE_ZOOM
        val hidden = screenWidth * (1f - 1f / zoom) / 2f
        val columnInset = if (compact) 0.dp else actionColumnWidth(buttonSize) / zoom
        // Techo de vuelo: por debajo de la fila de arriba (engranaje y estados plegados), pasado
        // a coordenadas de la escena (que está ampliada desde abajo). Pensado sobre todo para la
        // pantalla cuadrada del reloj, donde el alto es escaso.
        // En el reloj redondo se deja margen para la esfera (las esquinas no se ven); en uno
        // rectangular basta un margen pequeño.
        val topPad = when {
            compact && roundScreen -> screenHeight * 0.13f
            compact -> 6.dp
            else -> 8.dp
        }
        val bottomPad = if (compact && roundScreen) screenHeight * 0.09f else 8.dp
        val sidePad = if (compact && roundScreen) screenWidth * 0.08f else 8.dp
        val hudBottom = topPad + topButton + 4.dp
        val flightCeiling = screenHeight - (screenHeight - hudBottom) / zoom
        Box(Modifier.fillMaxSize().clipToBounds()) {
            BiomeScene(
                biome = biome,
                pets = listOfNotNull(pet),
                activeId = pet?.id,
                onPetTapped = { controller.cuddle() },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
                dayPhase = dayPhase,
                walkStartInset = hidden + columnInset,
                walkEndInset = hidden,
                depthRange = if (compact) CompactCareDepths else CareDepths,
                flightCeiling = flightCeiling,
                // Su nombre ya está en el panel de estados (y ampliado taparía media pantalla).
                showNames = false,
                // Siempre el mismo pájaro, sea cual sea la especie de la mascota.
                birdOverride = CareBird.sheet to CareBird.layout,
                handTool = handTool,
                groundFood = groundFood,
                wantsFood = GameRules::wantsGroundFood,
                onPetCleaned = { id ->
                    val target = current.byId(id) ?: return@BiomeScene
                    if (!controller.clean(id)) feedback = cleanRefusedMessage(target)
                },
                onFoodDropped = { food, x, depth ->
                    groundFood += GroundFood(nextFoodId++, food, x, depth, systemNowMillis())
                },
                onFoodEaten = { foodId, petId ->
                    val item = groundFood.firstOrNull { it.id == foodId } ?: return@BiomeScene
                    groundFood.remove(item)
                    val eater = current.byId(petId) ?: return@BiomeScene
                    feedback = feedAndDescribe(controller, eater, item.food)
                },
            )
        }

        val edge = Modifier.padding(start = sidePad, end = sidePad, top = topPad, bottom = bottomPad)
        Box(Modifier.fillMaxSize().safeDrawingPadding().then(edge)) {
            val gear = @Composable { mod: Modifier ->
                OptionsButton({
                    handTool = null
                    onOptions()
                }, mod, size = topButton)
            }
            if (pet != null) {
                val social = PetDerivedNeeds.socialization(pet, current)
                val stats = @Composable { mod: Modifier ->
                    StatsPanel(
                        pet = pet,
                        socialization = social,
                        satisfaction = PetDerivedNeeds.satisfaction(pet, current),
                        combat = PetCombatStatsRules.statsFor(pet, social),
                        onRelease = null,
                        modifier = mod,
                        // Plegado, solo la flecha (el nombre ya no hace falta en pantalla).
                        arrowButtonSize = topButton,
                    )
                }
                val actions = @Composable { mod: Modifier ->
                    CareActions(
                        sleeping = pet.needs.isSleeping,
                        handTool = handTool,
                        horizontal = compact,
                        buttonSize = buttonSize,
                        // Elegir comer o limpiar despierta a la mascota (dormida no haría nada).
                        onFeed = {
                            controller.wake()
                            val holdingFood = handTool is HandTool.FoodItem
                            handTool = null
                            if (!holdingFood) choosingFood = true
                        },
                        onClean = {
                            controller.wake()
                            handTool = if (handTool == HandTool.Water) null else HandTool.Water
                        },
                        onToggleSleep = {
                            handTool = null
                            controller.toggleSleep()
                        },
                        onBiome = onBiome?.let {
                            {
                                handTool = null
                                it()
                            }
                        },
                        modifier = mod,
                    )
                }
                if (compact) {
                    // Reloj: engranaje y estados arriba, en el centro; acciones en fila abajo.
                    Row(
                        Modifier.align(Alignment.TopCenter),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        gear(Modifier)
                        stats(Modifier.widthIn(max = screenWidth * 0.5f).heightIn(max = screenHeight * 0.65f))
                    }
                    actions(Modifier.align(Alignment.BottomCenter))
                } else {
                    // Como en el bioma: engranaje arriba a la izquierda, estados arriba a la
                    // derecha y la columna de acciones abajo a la izquierda.
                    gear(Modifier.align(Alignment.TopStart))
                    stats(Modifier.align(Alignment.TopEnd).heightIn(max = screenHeight - 24.dp))
                    actions(Modifier.align(Alignment.BottomStart))
                }
            } else {
                gear(Modifier.align(if (compact) Alignment.TopCenter else Alignment.TopStart))
                NoPetCard(
                    compact = compact,
                    eggPrice = GameRules.eggPrice(current),
                    // En el reloj no hay bioma al que volver: se puede criar desde aquí.
                    onBuyEgg = if (onBiome == null) ({
                        val missing = GameRules.goldMissingForEgg(current)
                        if (missing == 0) naming = true
                        else feedback = "Te faltan $missing de oro para un huevo"
                    }) else null,
                    onBiome = onBiome,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            feedback?.let {
                FeedbackBubble(
                    it,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = if (compact) buttonSize + 8.dp else 12.dp),
                    compact = compact,
                )
            }
        }

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
    }
}

/** Ancho de la columna de acciones (botón más su margen). */
private fun actionColumnWidth(buttonSize: Dp): Dp = buttonSize + 16.dp

/**
 * Botones del modo cuidado: alimentar, limpiar, dormir/despertar y, si hay [onBiome], volver
 * al bioma. Comida y agua se llevan en la mano como en el bioma (aro dorado mientras tanto).
 * En columna (pantallas grandes) o en fila ([horizontal], reloj).
 */
@Composable
private fun CareActions(
    sleeping: Boolean,
    handTool: HandTool?,
    horizontal: Boolean,
    buttonSize: Dp,
    onFeed: () -> Unit,
    onClean: () -> Unit,
    onToggleSleep: () -> Unit,
    onBiome: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val food = (handTool as? HandTool.FoodItem)?.food
    val buttons = @Composable {
        WoodIconButton(food?.icon ?: GameIcon.Feed, "Alimentar", onFeed, size = buttonSize, selected = food != null)
        WoodIconButton(GameIcon.Wash, "Limpiar", onClean, size = buttonSize, selected = handTool == HandTool.Water)
        WoodIconButton(if (sleeping) GameIcon.Wake else GameIcon.Sleep, if (sleeping) "Despertar" else "Dormir", onToggleSleep, size = buttonSize)
        if (onBiome != null) WoodIconButton(GameIcon.Biome, "Bioma", onBiome, size = buttonSize)
    }
    val gap = if (horizontal) 8.dp else 6.dp
    if (horizontal) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) { buttons() }
    } else {
        Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(gap)) { buttons() }
    }
}

/** Sin mascota seleccionada: en el reloj invita a criar una; si no, a volver al bioma. */
@Composable
private fun NoPetCard(
    compact: Boolean,
    eggPrice: Int,
    onBuyEgg: (() -> Unit)?,
    onBiome: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    WoodPanel(modifier) {
        Column(Modifier.padding(if (compact) 10.dp else 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GameIconImage(GameIcon.Egg, if (compact) 36.dp else 64.dp)
            Text(
                "No hay ninguna mascota que cuidar",
                style = GameUi.text(if (compact) 13.sp else 18.sp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            if (onBuyEgg != null) {
                WoodButton(if (eggPrice == 0) "Nueva (gratis)" else "Nueva · $eggPrice", onBuyEgg, icon = GameIcon.Egg)
            }
            if (onBiome != null) {
                WoodButton("Volver al bioma", onBiome, icon = GameIcon.Biome)
            }
        }
    }
}
