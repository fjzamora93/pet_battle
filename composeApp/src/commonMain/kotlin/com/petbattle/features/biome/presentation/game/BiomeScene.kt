package com.petbattle.features.biome.presentation.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.core.time.DayPhase
import com.petbattle.core.time.systemNowMillis
import com.petbattle.core.ui.WindPainter
import com.petbattle.core.ui.createWindPainter
import com.petbattle.core.ui.drawSpriteFrame
import com.petbattle.core.ui.hiddenPointer
import com.petbattle.design_system.GameIcon
import com.petbattle.resources.Res
import com.petbattle.resources.pet_egg
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.biomes.presentation.background
import com.petbattle.shared_features.biomes.presentation.backgroundLayers
import com.petbattle.shared_features.biomes.presentation.scenery
import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_food.presentation.icon
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.presentation.BirdLayout
import com.petbattle.shared_features.pet_species.presentation.BirdSheet
import com.petbattle.shared_features.pet_species.presentation.layout
import com.petbattle.shared_features.pet_species.presentation.sheet
import com.petbattle.shared_features.pet_species.presentation.spriteScale
import com.petbattle.shared_features.pet_status.domain.GameRules
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import com.petbattle.shared_features.pet_status.domain.wellbeing
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource

/** Altura (fracción del lienzo) del suelo más cercano, donde se posan los pájaros de delante. */
private const val FRONT_GROUND = 0.965f

/** Margen (fracción del lienzo) por debajo del borde del terreno: ni al fondo pisan fuera de él. */
private const val TERRAIN_MARGIN = 0.012f

/** Fondos sin capas: altura (fracción de la imagen) donde empieza el suelo continuo. */
private const val FALLBACK_TERRAIN_SOLID = 0.82f

/** Cuánto encoge un pájaro por cada fracción del lienzo que se aleja hacia el fondo (perspectiva). */
private const val PERSPECTIVE = 1.6f

/** Muestras a lo ancho del lienzo del borde del suelo en cuesta (ver BiomeSceneState.layout). */
private const val GROUND_EDGE_SAMPLES = 48

/** Distancia mínima en profundidad (0..1) para que dos pájaros posados no ocupen el mismo sitio. */
private const val DEPTH_GAP = 0.35

/**
 * El bioma con todas sus mascotas a la vez. Cada una descansa un rato y luego vuela
 * (batiendo las alas) hasta otro punto al azar del suelo. En vuelo pueden cruzarse, pero
 * al posarse no pueden quedar una encima de otra: si el sitio está ocupado, sigue de largo
 * o se da la vuelta. Tocar una mascota la selecciona ([onPetTapped]). La mascota activa
 * ([activeId]) se dibuja delante y con su nombre resaltado. Según el momento del día
 * ([dayPhase], hora local): por la mañana la imagen tal cual; por la tarde un filtro de
 * atardecer sutil; de noche la pantalla se oscurece y todas descansan (pose de dormir, sin
 * volar), aunque se puede seguir tocándolas. Fuera de la noche solo descansa la que se haya
 * acostado, sin cambiar la pantalla.
 * [walkStartInset] y [walkEndInset] dejan libre a cada lado el espacio que ocupen los botones,
 * y [depthRange] limita a qué profundidades (0 = delante, 1 = al fondo) se posan, vuelan y se
 * deja la comida (el modo cuidado la mantiene cerca para verla grande). Si [flightCeiling] es
 * mayor que 0, al volar ninguna sube (con su nombre) por encima de esa distancia desde arriba
 * del lienzo: el modo cuidado lo usa para que no se salga de lo visible ni pase bajo el HUD.
 * Con [showNames] a false no se dibuja el nombre sobre cada mascota. Con [birdOverride] todas
 * se dibujan con esa hoja (y su disposición), sea cual sea su especie, y a tamaño de adulto: el
 * modo cuidado muestra siempre el mismo pájaro.
 *
 * Con una herramienta en la mano ([handTool]) el cursor se convierte en ella y los toques
 * cambian: con agua, tocar una mascota la limpia ([onPetCleaned]); con comida, tocar el suelo
 * la deja allí ([onFoodDropped]). La comida del suelo ([groundFood]) atrae a todas las
 * mascotas despiertas que la quieran ([wantsFood]): vuelan hasta ella, la primera en llegar la
 * picotea y avisa con [onFoodEaten], y las demás desisten.
 */
@Composable
fun BiomeScene(
    biome: Biome,
    pets: List<PetEntity>,
    activeId: String?,
    onPetTapped: (String) -> Unit,
    modifier: Modifier = Modifier,
    dayPhase: DayPhase = DayPhase.morning,
    walkStartInset: Dp = 0.dp,
    walkEndInset: Dp = 0.dp,
    depthRange: ClosedFloatingPointRange<Double> = 0.0..1.0,
    flightCeiling: Dp = 0.dp,
    showNames: Boolean = true,
    birdOverride: Pair<DrawableResource, BirdLayout>? = null,
    handTool: HandTool? = null,
    groundFood: List<GroundFood> = emptyList(),
    wantsFood: (PetEntity, Food) -> Boolean = { _, _ -> false },
    onPetCleaned: (String) -> Unit = {},
    onFoodDropped: (food: Food, x: Double, depth: Double) -> Unit = { _, _, _ -> },
    onFoodEaten: (foodId: Long, petId: String) -> Unit = { _, _ -> },
) {
    val backdrop = imageResource(biome.background)
    val layers = biome.backgroundLayers?.let {
        val terrain = imageResource(it.terrain)
        // Dónde empieza el suelo continuo de la capa de terreno (se mide una vez por imagen).
        val solid = remember(terrain) { solidRowFraction(terrain) }
        val windMap = it.wind?.let { res -> imageResource(res) }
        val wind = remember(terrain, windMap) { windMap?.let { map -> createWindPainter(terrain, map) } }
        val filter = if (it.smooth) FilterQuality.Medium else FilterQuality.None
        BackdropLayers(imageResource(it.sky), imageResource(it.back), terrain, solid, wind, filter, it.groundEdge)
    }
    // Escena fotográfica (el bosque): recortes que tapan a las mascotas que pasan por detrás.
    val scenery = biome.scenery
    val occluders = scenery?.occluders?.map { imageResource(it.image) to it.rootY }.orEmpty()
    val sheets = PetSpecies.entries.associateWith { imageResource(it.sheet) }
    val overrideSheet = birdOverride?.let { imageResource(it.first) }
    val eggImage = imageResource(Res.drawable.pet_egg)
    val foodImages = Food.entries.associateWith { imageResource(it.icon.res) }
    val waterImage = imageResource(GameIcon.Wash.res)
    val state = remember(biome) { BiomeSceneState() }
    state.depthRange = depthRange
    state.showNames = showNames
    state.layoutOverride = birdOverride?.second
    val currentPets by rememberUpdatedState(pets)
    val currentActive by rememberUpdatedState(activeId)
    val currentPhase by rememberUpdatedState(dayPhase)
    val onTap by rememberUpdatedState(onPetTapped)
    val currentTool by rememberUpdatedState(handTool)
    val currentFood by rememberUpdatedState(groundFood)
    val currentWants by rememberUpdatedState(wantsFood)
    val onCleaned by rememberUpdatedState(onPetCleaned)
    val onDropped by rememberUpdatedState(onFoodDropped)
    val onEaten by rememberUpdatedState(onFoodEaten)
    val textMeasurer = rememberTextMeasurer()
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.0 else (now - last) / 1e9
                last = now
                val eaten = state.update(min(dt, 0.1), currentPets, currentPhase, currentFood, currentWants)
                eaten.forEach { (foodId, petId) -> onEaten(foodId, petId) }
                frame++
            }
        }
    }

    Canvas(
        modifier
            .hiddenPointer(handTool != null)
            // Sigue al puntero (sin consumir nada) para dibujar la herramienta en su sitio.
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        state.pointer = change.position
                        state.pointerIsTouch = change.type != PointerType.Mouse
                        // Con ratón se ve mientras esté sobre la escena; con el dedo, mientras toca.
                        state.pointerVisible = when {
                            event.type == PointerEventType.Exit -> false
                            change.type == PointerType.Mouse -> true
                            else -> change.pressed
                        }
                    }
                }
            }
            .pointerInput(state) {
                detectTapGestures { pos ->
                    when (val tool = currentTool) {
                        HandTool.Water -> {
                            val id = state.petAt(pos, currentActive) ?: return@detectTapGestures
                            if (currentPets.firstOrNull { it.id == id }?.isEgg != false) return@detectTapGestures
                            state.onSplashed(id)
                            onCleaned(id)
                        }
                        is HandTool.FoodItem -> {
                            val (x, depth) = state.groundSpotAt(pos)
                            onDropped(tool.food, x, depth)
                        }
                        null -> {
                            val id = state.petAt(pos, currentActive) ?: return@detectTapGestures
                            // Los huevos no se pueden seleccionar ni mimar.
                            if (currentPets.firstOrNull { it.id == id }?.isEgg == true) return@detectTapGestures
                            state.onTapped(id, currentPets)
                            onTap(id)
                        }
                    }
                }
            },
    ) {
        frame // se lee para que cada frame se redibuje
        if (layers != null) {
            drawLayeredBackdrop(layers, state.time, state.look)
        } else {
            drawBiomeBackdrop(backdrop, state.look, filter = if (scenery != null) SCENERY_FILTER else FilterQuality.None)
        }
        val ground = layers?.terrain ?: backdrop
        // El techo (con el hueco del nombre, si se dibuja) en píxeles de este lienzo.
        state.ceilingPx = if (flightCeiling > 0.dp) (flightCeiling + if (showNames) NAME_ROOM else 0.dp).toPx() else 0f
        state.layout(
            size, walkStartInset.toPx(), walkEndInset.toPx(),
            terrainSolid = layers?.terrainSolid ?: scenery?.farY ?: FALLBACK_TERRAIN_SOLID,
            imageWidth = ground.width, imageHeight = ground.height,
            groundEdge = layers?.groundEdge ?: scenery?.groundEdge,
            groundScale = scenery?.let { s -> { y: Float -> s.scaleAt(y) } },
        )
        // Recorte con el que se dibuja el terreno: para repintar su hierba delante de cada una.
        val wind = layers?.wind
        val crop = layers?.let { layerCrop(it.terrain, zoom = 1f, shift = 0f) }
        val now = systemNowMillis()
        // De atrás adelante por la altura de su apoyo en el suelo (más arriba = más al fondo):
        // mascotas (aunque vuelen cuenta su suelo), comida y recortes que tapan. Lo de más abajo
        // se pinta después y tapa a lo de detrás.
        val ordered = pets.sortedBy { pet -> state.actors[pet.id]?.let { state.groundYAt(it.depth, it.x) } ?: 0f }
        val drawOrder = buildList<Pair<Float, () -> Unit>> {
            for (pet in ordered) {
                val actor = state.actors[pet.id] ?: continue
                add(state.groundYAt(actor.depth, actor.x) to {
                    if (pet.isEgg) drawEgg(state, actor, pet, eggImage, now)
                    else drawActor(state, actor, pet, overrideSheet ?: sheets.getValue(pet.species))
                    // La hierba que nace por delante de ella la tapa (y deja ver entre las briznas).
                    if (wind != null && crop != null) drawGrassInFront(wind, crop, state.time, actor)
                })
            }
            for (item in groundFood) {
                add(state.groundYAt(item.depth, item.x) to { drawGroundFood(state, item, foodImages.getValue(item.food), now) })
            }
            // Un recorte solo hace falta si algo queda detrás de él (el fondo ya lo contiene).
            val farthest = minOfOrNull { it.first } ?: Float.MAX_VALUE
            for ((image, rootY) in occluders) {
                val screenRoot = state.screenYOfImage(rootY)
                if (screenRoot > farthest) add(screenRoot to { drawOccluder(image, state.look) })
            }
        }.sortedBy { it.first }
        drawOrder.forEach { it.second() }
        for (pet in ordered) {
            val actor = state.actors[pet.id] ?: continue
            drawSplash(actor)
            drawHearts(actor)
            drawZzz(textMeasurer, state, actor)
            val label = if (pet.isEgg) "Huevo · ${timeLeft(pet.hatchesAt!! - now)}" else pet.name
            if (showNames) drawName(textMeasurer, actor, label, active = pet.id == activeId)
        }
        // La herramienta en la mano, donde esté el puntero (con el dedo, algo por encima para verla).
        val tool = handTool
        if (tool != null && state.pointerVisible) {
            val image = when (tool) {
                HandTool.Water -> waterImage
                is HandTool.FoodItem -> foodImages.getValue(tool.food)
            }
            val side = 56.dp.toPx()
            val lift = if (state.pointerIsTouch) 52.dp.toPx() else 0f
            val p = state.pointer
            drawImage(
                image,
                dstOffset = IntOffset((p.x - side / 2).roundToInt(), (p.y - side / 2 - lift).roundToInt()),
                dstSize = IntSize(side.roundToInt(), side.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

/** Una mascota en escena: posición, vuelo y animaciones (nunca se persiste). */
private class Actor(var x: Double, var depth: Double, val phase: Double) {
    var target = x

    /** Profundidad a la que vuela (0 = delante, 1 = al fondo) y a qué ritmo cambia. */
    var targetDepth = depth
    var depthSpeed = 0.0
    var moving = false
    var lift = 0.0 // 0 = posada, 1 = altura de vuelo

    /** Su especie tiene animaciones de despegar y aterrizar (BirdSheet.hasTransitions). */
    var transitions = false

    /** Segundos despegando (<0 = no): hasta dejar el suelo no se desplaza. */
    var takeoffT = -1.0

    /** Segundos aterrizando (<0 = no): ya en su sitio, termina de posarse. */
    var landT = -1.0

    /** Despegando o aterrizando: no hace otra cosa hasta terminar. */
    val inTransition get() = takeoffT >= 0 || landT >= 0
    var facingRight = Random.nextBoolean()
    var restFor = 1.0 + Random.nextDouble() * 4
    var nextTurnIn = 2.0 + Random.nextDouble() * 4
    var sleepT = 0.0
    var saturation = 1.0
    var reactT = -1.0 // segundos de reacción al toque, <0 = sin reaccionar
    var bounceT = -1.0
    var splashT = -1.0 // segundos desde que le cayó agua, <0 = sin gotas

    /** Comida del suelo a la que va (o que está comiendo) y dónde está. */
    var foodId: Long? = null
    var foodX = 0.0
    var eatT = -1.0 // segundos picoteando, <0 = no está comiendo
    val hearts = mutableListOf<Heart>()
    var rect = Rect.Zero

    /** Altura en pantalla del suelo bajo ella en el último frame (aunque vuele). */
    var groundY = 0f

    class Heart(var age: Double = 0.0)

    /** Squash & stretch: (1.18, .82) -> (.9, 1.12) -> (1, 1) con overshoot. */
    fun bounceScale(): Pair<Float, Float> {
        val t = bounceT
        if (t < 0) return 1f to 1f
        fun lerpPair(a: Pair<Float, Float>, b: Pair<Float, Float>, k: Double) =
            (a.first + (b.first - a.first) * k.toFloat()) to (a.second + (b.second - a.second) * k.toFloat())
        val squash = 1.18f to 0.82f
        val stretch = 0.9f to 1.12f
        return when {
            t < 0.07 -> lerpPair(1f to 1f, squash, t / 0.07)
            t < 0.17 -> lerpPair(squash, stretch, (t - 0.07) / 0.1)
            else -> {
                val k = ((t - 0.17) / 0.18).coerceIn(0.0, 1.0)
                // Asentamiento elástico.
                val e = 1 - kotlin.math.cos(k * kotlin.math.PI * 2.5) * (1 - k)
                lerpPair(stretch, 1f to 1f, e)
            }
        }
    }
}

/** Estado de toda la escena, guiado por frames. Las posiciones x van de 0 a 1 en la zona de paseo. */
private class BiomeSceneState {
    var time = 0.0
    val actors = mutableMapOf<String, Actor>()

    // Medidas del último frame dibujado (ver layout).
    var slotSide = 0f // lado del sprite de un adulto
    var walkLeft = 0f
    var walkWidth = 1f
    var frontY = 0f // suelo más cercano (profundidad 0)
    var backY = 0f // suelo más lejano (profundidad 1) de referencia: el borde del terreno
    var canvasHeight = 1f

    /**
     * Suelo más lejano a lo ancho del lienzo cuando el terreno está en cuesta: muestras
     * equiespaciadas de borde a borde (vacío = horizontal, a [backY]).
     */
    private var backEdge = FloatArray(0)
    private var canvasWidth = 1f

    /** Suelo más lejano en la x de pantalla [screenX]. */
    private fun backYAt(screenX: Float): Float {
        if (backEdge.isEmpty()) return backY
        val f = (screenX / canvasWidth).coerceIn(0f, 1f) * (backEdge.size - 1)
        val i = f.toInt().coerceAtMost(backEdge.size - 2)
        return backEdge[i] + (backEdge[i + 1] - backEdge[i]) * (f - i)
    }

    /** Centro en pantalla de la plaza de una mascota en la posición [x] (0..1) de la zona de paseo. */
    fun centerXAt(x: Double) = walkLeft + x.toFloat() * walkWidth + slotSide / 2

    /**
     * Altura del suelo a la profundidad [depth] (0 = delante, 1 = al fondo) en la posición [x]:
     * entre el suelo de delante y el borde del fondo en esa x, que sigue la cuesta del terreno.
     */
    fun groundYAt(depth: Double, x: Double) = frontY + (backYAt(centerXAt(x)) - frontY) * depth.toFloat()

    /**
     * Perspectiva propia del bioma (escenas fotográficas): escala de una casilla de sprite con
     * el suelo a una altura de la imagen (fracción). Null = la perspectiva suave por defecto.
     */
    private var groundScale: ((Float) -> Float)? = null

    // Paso de pantalla a imagen del fondo (mismo recorte con el que se dibuja).
    private var imageK = 1f
    private var imageCropTop = 0f
    private var imageHeight = 1f

    /** Altura en la imagen (fracción) del punto de pantalla a la altura [screenY]. */
    private fun imageYOf(screenY: Float) = (screenY / imageK + imageCropTop) / imageHeight

    /** Altura en pantalla de la altura [imageY] (fracción) de la imagen del fondo. */
    fun screenYOfImage(imageY: Float) = (imageY * imageHeight - imageCropTop) * imageK

    /**
     * Escala por perspectiva a la profundidad [depth] en la posición [x]: más lejos, más
     * pequeño. Por defecto depende solo de la profundidad (con el borde de referencia), no de
     * la cuesta: en un prado inclinado, una mascota del fondo se ve igual de lejos a un lado que
     * al otro. Con la perspectiva del bioma ([groundScale], p. ej. el bosque) depende de la
     * altura real del suelo que pisa, relativa al suelo de delante: puede alejarse mucho y
     * hacerse muy pequeña.
     */
    fun scaleAt(depth: Double, x: Double): Float {
        val scale = groundScale
            ?: return (1f - (frontY - backY) * depth.toFloat() / canvasHeight * PERSPECTIVE).coerceAtLeast(0.6f)
        return scale(imageYOf(groundYAt(depth, x))) / scale(imageYOf(frontY))
    }

    /** Profundidades permitidas (ver BiomeScene). */
    var depthRange: ClosedFloatingPointRange<Double> = 0.0..1.0

    /** Si se dibuja el nombre sobre cada mascota (ver BiomeScene). */
    var showNames = true

    /** Disposición de la hoja con la que se dibujan todas, si no es la de su especie (ver BiomeScene). */
    var layoutOverride: BirdLayout? = null

    /** Disposición de la hoja con la que se dibuja [pet]. */
    fun layoutOf(pet: PetEntity): BirdLayout = layoutOverride ?: pet.species.layout

    /** Techo en píxeles del lienzo (0 = sin techo); lo fija el dibujo en cada frame. */
    var ceilingPx = 0f

    /**
     * Profundidad máxima (dentro de [depthRange]) a la que una mascota posada en [x] queda
     * entera por debajo del techo: en pantallas altas el suelo del fondo sube tanto que, posada
     * al fondo, ya pasaría bajo el HUD. Sin [x], la peor x de la zona de paseo.
     */
    private fun maxDepthUnderCeiling(x: Double? = null): Double {
        val end = depthRange.endInclusive
        if (ceilingPx <= 0f || canvasHeight <= 1f) return end
        val xs = x?.let { listOf(it) } ?: (0..10).map { it / 10.0 }
        var d = end
        // De atrás adelante, hasta que la cabeza quede por debajo del techo.
        while (d > depthRange.start) {
            val top = xs.minOf { groundYAt(d, it) - slotSide * scaleAt(d, it) }
            if (top >= ceilingPx) return d
            d -= 0.01
        }
        return depthRange.start
    }

    /** Profundidad al azar dentro de [depthRange] (y bajo el techo, en la posición [x]). */
    private fun randomDepth(x: Double? = null): Double {
        val end = maxDepthUnderCeiling(x)
        return depthRange.start + Random.nextDouble() * (end - depthRange.start)
    }

    /** Distancia mínima (en fracción de la zona de paseo) entre dos mascotas posadas. */
    var gap = 0.15

    /**
     * Medidas de este frame. [terrainSolid] es la altura (fracción de una imagen de
     * [imageWidth] x [imageHeight]) donde empieza el suelo continuo; se lleva a la pantalla con
     * el mismo recorte con el que se dibuja el fondo, y es el límite del fondo: no lo pisan.
     * Si el terreno está en cuesta, [groundEdge] (puntos x, y en fracciones de la imagen) es
     * ese límite a lo ancho, y las mascotas del fondo se posan sobre él.
     */
    fun layout(
        size: Size,
        startInset: Float,
        endInset: Float,
        terrainSolid: Float,
        imageWidth: Int,
        imageHeight: Int,
        groundEdge: List<Pair<Float, Float>>? = null,
        groundScale: ((Float) -> Float)? = null,
    ) {
        this.groundScale = groundScale
        val raw = min(size.width * 0.3f, size.height * 0.24f)
        slotSide = raw
        val margin = slotSide * 0.1f
        walkLeft = startInset + margin
        walkWidth = max(1f, size.width - startInset - endInset - 2 * margin - slotSide)
        canvasHeight = size.height
        canvasWidth = size.width
        frontY = size.height * FRONT_GROUND
        val k = max(size.width / imageWidth, size.height / imageHeight)
        val cropTop = (imageHeight - size.height / k) / 2
        val cropLeft = (imageWidth - size.width / k) / 2
        imageK = k
        imageCropTop = cropTop
        this.imageHeight = imageHeight.toFloat()
        val terrainTop = (terrainSolid * imageHeight - cropTop) * k
        backY = min(frontY, terrainTop + size.height * TERRAIN_MARGIN)
        backEdge = if (groundEdge == null || groundEdge.size < 2) {
            FloatArray(0)
        } else {
            FloatArray(GROUND_EDGE_SAMPLES) { i ->
                val imageX = (size.width * i / (GROUND_EDGE_SAMPLES - 1) / k + cropLeft) / imageWidth
                val edgeY = interpolate(groundEdge, imageX) * imageHeight
                min(frontY, (edgeY - cropTop) * k + size.height * TERRAIN_MARGIN)
            }
        }
        gap = (slotSide * 0.85f / walkWidth).toDouble()
    }

    /** y de la polilínea [points] (ordenada por x) en [x]; fuera de sus extremos, el extremo. */
    private fun interpolate(points: List<Pair<Float, Float>>, x: Float): Float {
        if (x <= points.first().first) return points.first().second
        for (i in 1 until points.size) {
            val (x1, y1) = points[i]
            if (x <= x1) {
                val (x0, y0) = points[i - 1]
                return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
            }
        }
        return points.last().second
    }

    // Puntero sobre la escena: para dibujar la herramienta que se lleva en la mano.
    var pointer = Offset.Zero
    var pointerVisible = false
    var pointerIsTouch = false

    /** Posición (x 0..1, profundidad 0..1) del suelo bajo el punto de pantalla [pos]. */
    fun groundSpotAt(pos: Offset): Pair<Double, Double> {
        val x = ((pos.x - walkLeft - slotSide / 2) / walkWidth).toDouble().coerceIn(0.0, 1.0)
        val back = backYAt(pos.x)
        // Por encima del borde del fondo (el cielo) cae al fondo; por debajo del suelo, delante.
        val depth = if (frontY - back < 1f) 0.0 else ((frontY - pos.y) / (frontY - back)).toDouble().coerceIn(0.0, 1.0)
        return x to depth.coerceIn(depthRange.start, maxDepthUnderCeiling(x))
    }

    /** Si es de noche (lo último que dijo la pantalla): descansan todas. */
    var night = false

    // Aspecto del momento del día, con transición suave al cambiar de franja.
    var look = DayLook.Midday

    /** Dormida o de noche: descansa (pose de dormir, sin volar). */
    fun isResting(pet: PetEntity) = pet.needs.isSleeping || night

    /**
     * Avanza [dt] segundos. Devuelve la comida que se ha terminado de comer en este frame
     * (id de la comida, id de la mascota).
     */
    fun update(
        dt: Double,
        pets: List<PetEntity>,
        phase: DayPhase,
        foods: List<GroundFood> = emptyList(),
        wantsFood: (PetEntity, Food) -> Boolean = { _, _ -> false },
    ): List<Pair<Long, String>> {
        val eaten = mutableListOf<Pair<Long, String>>()
        val foodIds = foods.map { it.id }.toSet()
        time += dt
        night = phase == DayPhase.night
        val ids = pets.map { it.id }.toSet()
        actors.keys.retainAll(ids)
        for (pet in pets) if (pet.id !in actors) {
            val depth = randomDepth()
            actors[pet.id] = Actor(spawnSpot(depth), depth, Random.nextDouble() * 10)
        }

        look = lerp(look, DayLook.of(phase), (dt * 2.5).coerceIn(0.0, 1.0).toFloat())

        // Posadas por encima del techo (aparecieron antes del primer dibujo, o cambió el tamaño
        // de la pantalla): se adelantan lo justo.
        if (ceilingPx > 0f) {
            for (a in actors.values) if (!a.moving && !a.inTransition) {
                val max = maxDepthUnderCeiling(a.x)
                if (a.depth > max) {
                    a.depth = max
                    a.targetDepth = max
                }
            }
        }

        val ease = (dt * 4).coerceIn(0.0, 1.0)
        for (pet in pets) {
            val a = actors.getValue(pet.id)
            val asleep = isResting(pet)
            a.sleepT += ((if (asleep) 1.0 else 0.0) - a.sleepT) * ease
            val targetSat = 0.3 + 0.7 * pet.needs.wellbeing.coerceIn(0.0, 1.0)
            a.saturation += (targetSat - a.saturation) * ease
            val layout = layoutOf(pet)
            a.transitions = BirdSheet.hasTransitions(layout)
            if (a.takeoffT >= 0) {
                a.takeoffT += dt
                // Deja el suelo: empieza a desplazarse mientras termina la animación.
                if (!a.moving && a.takeoffT >= BirdSheet.liftoffSeconds(layout)) a.moving = true
                if (a.takeoffT >= BirdSheet.cycleSeconds(BirdSheet.TAKEOFF, layout)) a.takeoffT = -1.0
            }
            if (a.landT >= 0) {
                a.landT += dt
                if (a.landT >= BirdSheet.cycleSeconds(BirdSheet.LANDING, layout)) a.landT = -1.0
            }
            // Con aterrizaje propio baja al ras del suelo al final del vuelo: la animación de
            // aterrizar empieza justo en la postura de vuelo y lo posa.
            val descending = a.transitions && a.moving && timeToArrive(a) <= DESCENT_LEAD
            a.lift += ((if (a.moving && !descending) 1.0 else 0.0) - a.lift) * (dt * 5).coerceIn(0.0, 1.0)

            // La comida a la que iba ya no está (se la comió otra o caducó).
            if (a.foodId != null && a.foodId !in foodIds) {
                a.foodId = null
                a.eatT = -1.0
            }
            // Otra llegó antes y ya se la está comiendo: sigue su vuelo y se posa donde pueda.
            if (a.foodId != null && a.eatT < 0 && isBeingEatenByOther(a.foodId!!, a)) a.foodId = null
            if (a.eatT >= 0) {
                // Empieza a picotear cuando termina de posarse.
                if (a.landT < 0) a.eatT += dt
                if (a.eatT >= EAT_DURATION) {
                    a.foodId?.let { eaten += it to pet.id }
                    a.foodId = null
                    a.eatT = -1.0
                    a.restFor = 2.0 + Random.nextDouble() * 3
                }
            } else if (pet.isEgg) a.moving = false // los huevos no se mueven
            else if (a.moving) fly(a, dt)
            else if (a.inTransition) Unit // agachándose para despegar o terminando de posarse
            else if (!asleep && a.reactT < 0) {
                a.restFor -= dt
                a.nextTurnIn -= dt
                if (a.nextTurnIn <= 0) {
                    a.nextTurnIn = 2.5 + Random.nextDouble() * 4
                    a.facingRight = !a.facingRight
                }
                if (a.restFor <= 0) takeOff(a)
            }

            if (a.reactT >= 0) a.reactT += dt
            // La reacción dura lo que su animación de mirar arriba (depende de la especie).
            if (a.reactT > BirdSheet.cycleSeconds(BirdSheet.LOOK_UP_ROW, layout) || asleep) a.reactT = -1.0
            if (a.bounceT >= 0) a.bounceT += dt
            if (a.bounceT > BOUNCE_DURATION) a.bounceT = -1.0
            a.hearts.forEach { it.age += dt }
            a.hearts.removeAll { it.age > HEART_LIFE }
            if (a.splashT >= 0) a.splashT += dt
            if (a.splashT > SPLASH_DURATION) a.splashT = -1.0
        }

        // Cada mascota libre va a por la comida que quiera más cercana que nadie esté comiendo.
        // Varias pueden ir a por la misma: se la queda la primera que llega.
        val beingEaten = actors.values.filter { it.eatT >= 0 }.mapNotNull { it.foodId }.toSet()
        val available = foods.filter { it.id !in beingEaten }
        if (available.isNotEmpty()) {
            for (pet in pets) {
                if (isResting(pet)) continue
                val a = actors[pet.id]?.takeIf { it.foodId == null && it.eatT < 0 } ?: continue
                val food = available
                    .filter { wantsFood(pet, it.food) }
                    .minByOrNull { abs(it.x - a.x) + abs(it.depth - a.depth) * 0.3 }
                    ?: continue
                goToFood(a, food)
            }
        }
        return eaten
    }

    /** Alguna mascota distinta de [self] ya está comiéndose la comida [foodId]. */
    private fun isBeingEatenByOther(foodId: Long, self: Actor) =
        actors.values.any { it !== self && it.foodId == foodId && it.eatT >= 0 }

    /** Vuela hasta posarse junto a [food] (al lado por el que llega), mirando hacia ella. */
    private fun goToFood(a: Actor, food: GroundFood) {
        val side = if (food.x >= a.x) -1.0 else 1.0
        a.foodId = food.id
        a.foodX = food.x
        a.reactT = -1.0
        a.target = (food.x + side * gap * 0.35).coerceIn(0.0, 1.0)
        a.targetDepth = food.depth
        val flightTime = max(abs(a.target - a.x) / SPEED, 0.5)
        a.depthSpeed = abs(a.targetDepth - a.depth) / flightTime
        startFlight(a)
        a.facingRight = a.target > a.x
    }

    /**
     * Echa a volar hacia el destino ya elegido. Si su especie tiene despegue propio y está
     * posada, primero lo anima (y no se desplaza hasta dejar el suelo); si ya volaba o estaba
     * despegando, solo cambia de rumbo.
     */
    private fun startFlight(a: Actor) {
        when {
            a.moving || a.takeoffT >= 0 -> Unit
            a.transitions -> {
                a.landT = -1.0
                a.takeoffT = 0.0
            }
            else -> a.moving = true
        }
    }

    /** Se posa donde está; si su especie tiene aterrizaje propio, lo anima. */
    private fun land(a: Actor) {
        a.moving = false
        a.takeoffT = -1.0
        if (a.transitions) a.landT = 0.0
    }

    /** Segundos que le faltan para llegar a su destino (en horizontal y en profundidad). */
    private fun timeToArrive(a: Actor): Double {
        val horizontal = abs(a.target - a.x) / SPEED
        val depth = if (a.depthSpeed > 0) abs(a.targetDepth - a.depth) / a.depthSpeed else 0.0
        return max(horizontal, depth)
    }

    /** Elige un destino al azar (suficientemente lejos) y despega. */
    private fun takeOff(a: Actor) {
        var target = Random.nextDouble()
        repeat(5) { if (abs(target - a.x) < gap) target = Random.nextDouble() }
        a.target = target
        // También cambia de profundidad, al ritmo justo para llegar a la vez que en horizontal.
        a.targetDepth = randomDepth(target)
        val flightTime = max(abs(target - a.x) / SPEED, 0.5)
        a.depthSpeed = abs(a.targetDepth - a.depth) / flightTime
        startFlight(a)
        a.facingRight = target > a.x
    }

    private fun fly(a: Actor, dt: Double) {
        val depthStep = a.depthSpeed * dt
        a.depth = if (abs(a.targetDepth - a.depth) > depthStep) a.depth + sign(a.targetDepth - a.depth) * depthStep else a.targetDepth
        val step = SPEED * dt
        val dir = sign(a.target - a.x)
        if (dir != 0.0) a.facingRight = dir > 0
        if (abs(a.target - a.x) > step) {
            a.x += dir * step
            return
        }
        a.x = a.target
        // Se posa cuando llega también a su profundidad.
        if (a.depth != a.targetDepth) return
        // Si otra llegó antes a su comida, se posa como cualquier otra vez.
        if (a.foodId != null && isBeingEatenByOther(a.foodId!!, a)) a.foodId = null
        // Si venía a por comida, se posa a su lado aunque haya otra cerca, y empieza a picotear.
        if (a.foodId != null) {
            land(a)
            a.facingRight = a.foodX > a.x
            a.eatT = 0.0
            return
        }
        // Al posarse no puede quedar encima de otra: sigue de largo o se da la vuelta.
        if (!isFree(a.x, a.depth, a)) {
            val spot = freeSpotFrom(a.x, a.depth, if (dir == 0.0) 1.0 else dir, a)
            if (spot != null) {
                a.target = spot
                return
            }
        }
        land(a)
        a.restFor = 3.0 + Random.nextDouble() * 5
    }

    /** Nadie posado cerca de ([x], [depth]): ni en horizontal ni en profundidad. */
    private fun isFree(x: Double, depth: Double, self: Actor?) =
        actors.values.none { it !== self && !it.moving && abs(it.x - x) < gap && abs(it.depth - depth) < DEPTH_GAP }

    /** Primer hueco libre (a la profundidad [depth]) avanzando en [dir]; si no hay, el primero en sentido contrario. */
    private fun freeSpotFrom(from: Double, depth: Double, dir: Double, self: Actor?): Double? {
        for (d in listOf(dir, -dir)) {
            var k = 1
            while (true) {
                val c = from + d * k * gap * 0.5
                if (c < 0.0 || c > 1.0) break
                if (isFree(c, depth, self)) return c
                k++
            }
        }
        return null
    }

    private fun spawnSpot(depth: Double): Double {
        repeat(30) {
            val c = Random.nextDouble()
            if (isFree(c, depth, null)) return c
        }
        return Random.nextDouble()
    }

    /** Mascota bajo [pos]: si se solapan varias, la más cercana (es la que se ve delante). */
    @Suppress("UNUSED_PARAMETER")
    fun petAt(pos: Offset, activeId: String?): String? =
        actors.entries.filter { it.value.rect.contains(pos) }.minByOrNull { it.value.depth }?.key

    fun onTapped(id: String, pets: List<PetEntity>) {
        val a = actors[id] ?: return
        a.bounceT = 0.0
        val pet = pets.firstOrNull { it.id == id } ?: return
        // Dormida no se entera; de noche descansando sí recibe el mimo (sin cambiar de pose).
        if (!pet.needs.isSleeping) a.hearts += Actor.Heart()
        if (!isResting(pet)) a.reactT = 0.0
    }

    /** Le cae agua encima (al limpiarla). */
    fun onSplashed(id: String) {
        val a = actors[id] ?: return
        a.splashT = 0.0
        a.bounceT = 0.0
    }

    companion object {
        const val SPEED = 0.16 // fracción de la zona de paseo por segundo
        const val BOUNCE_DURATION = 0.35
        const val HEART_LIFE = 0.9
        const val SPLASH_DURATION = 0.7
        const val EAT_DURATION = 1.6 // segundos picoteando antes de acabarse la comida

        /** Segundos antes de llegar en que baja al ras del suelo (especies con aterrizaje). */
        const val DESCENT_LEAD = 0.45
    }
}

// Salpicaduras de barro en la rejilla de diseño de 64 unidades del frame (pose de pie).
private val DirtSplats = listOf(28 to 40, 36 to 37, 22 to 31, 32 to 45, 41 to 47, 24 to 44, 17 to 27, 45 to 53)
private val SplatShape = listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1, 3 to 1, 1 to 2, 2 to 2, 3 to 2)
private val SplatCore = listOf(1 to 1, 2 to 1, 2 to 2)

/** Tiempo que falta para eclosionar, en horas y minutos ("3 h 20 min", "12 min"). */
private fun timeLeft(ms: Long): String {
    val minutes = (ms.coerceAtLeast(0) + 59_999) / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "$h h $m min" else "$m min"
}

/** Un huevo en su nido: quieto, y en su última hora se balancea porque va a eclosionar. */
private fun DrawScope.drawEgg(state: BiomeSceneState, a: Actor, pet: PetEntity, image: ImageBitmap, now: Long) {
    val side = state.slotSide * 0.55f * state.scaleAt(a.depth, a.x)
    val groundY = state.groundYAt(a.depth, a.x)
    a.groundY = groundY
    val slotLeft = state.walkLeft + a.x.toFloat() * state.walkWidth
    val left = slotLeft + (state.slotSide - side) / 2
    val topY = groundY - side
    a.rect = Rect(left, topY, left + side, topY + side)
    val remaining = pet.hatchesAt!! - now
    val wobble = if (remaining < 3_600_000L) sin((state.time + a.phase) * 9).toFloat() * 7f else 0f
    rotate(wobble, pivot = Offset(left + side / 2, groundY)) {
        drawImage(
            image,
            dstOffset = IntOffset(left.roundToInt(), topY.roundToInt()),
            dstSize = IntSize(side.roundToInt(), side.roundToInt()),
        )
    }
}

private fun DrawScope.drawActor(state: BiomeSceneState, a: Actor, pet: PetEntity, sheet: ImageBitmap) {
    // Los sprites son de alta resolución: se escalan libremente con suavizado.
    // Más lejos (al fondo), más pequeño y más arriba en la pantalla.
    val perspective = state.scaleAt(a.depth, a.x)
    // Con la hoja del modo cuidado, siempre a tamaño de adulto (también los polluelos).
    val spriteScale = if (state.layoutOverride != null) 1f else pet.species.spriteScale
    val side = state.slotSide * spriteScale * perspective
    val slotLeft = state.walkLeft + a.x.toFloat() * state.walkWidth
    val left = slotLeft + (state.slotSide - side) / 2
    val groundY = state.groundYAt(a.depth, a.x)
    a.groundY = groundY
    val flap =if (a.lift > 0.01) sin((state.time + a.phase) * 9).toFloat() * side * 0.03f else 0f
    var liftPx = (a.lift.toFloat() * state.slotSide * perspective * 0.35f) + flap * a.lift.toFloat()
    if (state.ceilingPx > 0f) {
        // Con techo, no sube más allá (el techo ya deja sitio para el nombre, si se dibuja).
        liftPx = liftPx.coerceAtMost(max(0f, groundY - side - state.ceilingPx))
    }
    val layout = state.layoutOf(pet)
    // Hojas con las patas más arriba en la casilla (petirrojo): se baja para que pisen el suelo.
    val topY = groundY - side - liftPx + side * BirdSheet.groundShift(layout)
    a.rect = Rect(left, topY, left + side, topY + side)

    // Sombra en el suelo (más pequeña cuanto más alto vuela).
    val shadow = side * (0.6f - 0.25f * a.lift.toFloat())
    drawOval(Color(0x26000000), Offset(left + side / 2 - shadow / 2, groundY - side * 0.04f), Size(shadow, side * 0.08f))

    val asleep = state.isResting(pet)
    val t = state.time + a.phase
    // Comiendo con animación propia (si la hoja la tiene); si no, picotea inclinando el sprite.
    val eating = a.eatT >= 0 && a.landT < 0
    val eatAnimation = eating && BirdSheet.hasEat(layout)
    val animation = when {
        // Despegando o aterrizando: su animación de transición, de principio a fin.
        a.takeoffT >= 0 -> BirdSheet.TAKEOFF
        a.landT >= 0 -> BirdSheet.LANDING
        a.lift > 0.05 -> BirdSheet.FLY_ROW
        eatAnimation -> BirdSheet.EAT
        // Al tocarla, mira arriba (hacia quien la mima).
        a.reactT >= 0 -> BirdSheet.LOOK_UP_ROW
        // Dormida: su animación de dormir, muy lenta. De noche despierta: descansa.
        pet.needs.isSleeping -> BirdSheet.SLEEP_ROW
        asleep -> BirdSheet.REST_ROW
        else -> BirdSheet.IDLE_ROW
    }
    val frame = when (animation) {
        BirdSheet.TAKEOFF -> BirdSheet.onceFrame(animation, layout, a.takeoffT)
        BirdSheet.LANDING -> BirdSheet.onceFrame(animation, layout, a.landT)
        BirdSheet.LOOK_UP_ROW -> BirdSheet.onceFrame(animation, layout, a.reactT)
        else -> BirdSheet.loopFrame(animation, layout, t)
    }
    val (col, row) = BirdSheet.cell(animation, frame, layout)

    // Respiración idle: más lenta y profunda mientras duerme (no mientras despega o se posa).
    val speed = 2.2 - a.sleepT * 1.2
    val amount = (0.02 + a.sleepT * 0.02) * (1 - a.lift) * (if (a.inTransition) 0.0 else 1.0)
    val b = (sin(t * speed) * amount).toFloat()
    val (bx, by) = a.bounceScale()
    // Desaturado si está mal (el filtro del momento del día solo cae sobre el cielo).
    val filter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(a.saturation.toFloat()) })
    val pivot = Offset(left + side / 2, topY + side)
    val dirtiness = ((60 - pet.needs.hygiene) / 60).coerceIn(0.0, 1.0)

    // Comiendo: picotea inclinándose hacia delante (hacia donde mira) a golpes.
    val peck = if (eating && !eatAnimation) (max(0.0, sin(a.eatT * 2 * kotlin.math.PI * 2.2)) * 28).toFloat() else 0f
    rotate(if (a.facingRight) peck else -peck, pivot) {
    scale(bx * (1 + b), by * (1 - b), pivot) {
        val bounds = Rect(left, topY, left + side, topY + side)
        drawIntoCanvas { it.saveLayer(bounds, Paint()) }
        drawSpriteFrame(sheet, BirdSheet.FRAME_SIZE, col, row, Offset(left, topY), side, flipX = a.facingRight, colorFilter = filter, filterQuality = BirdSheet.FILTER)
        if (dirtiness > 0) {
            // SrcAtop: la suciedad solo cae sobre los píxeles del propio pájaro.
            val texel = side / BirdSheet.DESIGN_GRID
            val alpha = (0.55 + dirtiness * 0.4).toFloat()
            val count = ceil(dirtiness * DirtSplats.size).toInt()
            fun splat(points: List<Pair<Int, Int>>, color: Color, sx: Int, sy: Int) {
                for ((dx, dy) in points) {
                    var x = (sx + dx) * texel
                    if (a.facingRight) x = side - x - texel
                    drawRect(color.copy(alpha = alpha), Offset(left + x, topY + (sy + dy) * texel), Size(texel, texel), blendMode = BlendMode.SrcAtop)
                }
            }
            for (i in 0 until count) {
                val (sx, sy) = DirtSplats[i]
                splat(SplatShape, Color(0xFF8A5E34), sx, sy)
                splat(SplatCore, Color(0xFF5A3A1E), sx, sy)
            }
        }
        drawIntoCanvas { it.restore() }
    }
    }
}

/** Comida en el suelo: cae desde un poco más arriba al dejarla y se desvanece al caducar. */
private fun DrawScope.drawGroundFood(state: BiomeSceneState, food: GroundFood, image: ImageBitmap, now: Long) {
    val age = (now - food.droppedAt).coerceAtLeast(0L)
    val side = state.slotSide * 0.3f * state.scaleAt(food.depth, food.x)
    val groundY = state.groundYAt(food.depth, food.x)
    val fall = (1f - (age / 250f).coerceAtMost(1f)).let { it * it } * side * 1.5f
    val left = state.centerXAt(food.x) - side / 2
    val top = groundY - side * 0.85f - fall
    val remaining = GameRules.GROUND_FOOD_LIFETIME_MS - age
    val alpha = (remaining / 2_000f).coerceIn(0f, 1f) // los dos últimos segundos se desvanece
    drawOval(Color(0x33000000).copy(alpha = 0.2f * alpha), Offset(left + side * 0.1f, groundY - side * 0.08f), Size(side * 0.8f, side * 0.16f))
    drawImage(
        image,
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(side.roundToInt(), side.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Medium,
    )
}

/** Gotas de agua que caen sobre la mascota al limpiarla. */
private fun DrawScope.drawSplash(a: Actor) {
    if (a.splashT < 0) return
    val k = (a.splashT / BiomeSceneState.SPLASH_DURATION).toFloat()
    val r = a.rect
    val drop = 5.dp.toPx()
    for (i in 0 until 7) {
        // Cada gota sale a su altura y con un pequeño retraso, y cae hasta el cuerpo.
        val delay = (i % 3) * 0.12f
        val t = ((k - delay) / (1f - delay)).coerceIn(0f, 1f)
        if (t <= 0f) continue
        val x = r.left + r.width * (0.2f + 0.6f * ((i * 0.37f) % 1f))
        val y = r.top - r.height * 0.2f + t * r.height * 0.7f
        val color = Color(0xFF7FCBF2).copy(alpha = 1f - t * 0.7f)
        drawOval(color, Offset(x - drop / 2, y - drop), Size(drop, drop * 1.6f))
    }
}

private fun DrawScope.drawHearts(a: Actor) {
    for (heart in a.hearts) {
        val k = (heart.age / BiomeSceneState.HEART_LIFE).toFloat()
        val s = 26f * density / 2
        translate(a.rect.center.x - s / 2, a.rect.top - 70f * density / 2 * k - s / 2) {
            val path = Path().apply {
                moveTo(s / 2, s * 0.9f)
                cubicTo(-s * 0.1f, s * 0.45f, s * 0.2f, -s * 0.1f, s / 2, s * 0.28f)
                cubicTo(s * 0.8f, -s * 0.1f, s * 1.1f, s * 0.45f, s / 2, s * 0.9f)
                close()
            }
            drawPath(path, Color(0xFFFF4F79).copy(alpha = 1 - k))
        }
    }
}

private fun DrawScope.drawZzz(textMeasurer: TextMeasurer, state: BiomeSceneState, a: Actor) {
    if (a.sleepT <= 0.5) return
    val side = a.rect.width
    for (i in 0 until 3) {
        val phase = ((state.time * 0.5 + i / 3.0) % 1.0).toFloat()
        drawText(
            textMeasurer, "z",
            topLeft = Offset(a.rect.left + side * (0.7f + phase * 0.25f), a.rect.top + side * 0.15f - phase * side * 0.4f),
            style = TextStyle(color = Color.White, fontSize = (side * 0.18f / density).sp, fontWeight = FontWeight.ExtraBold),
        )
    }
}

/** Nombre sobre cada mascota; el de la activa, resaltado y con una flecha. */
/** Lo que ocupa el nombre (y la flecha de la activa) por encima del frame de la mascota. */
private val NAME_ROOM = 34.dp

private fun DrawScope.drawName(textMeasurer: TextMeasurer, a: Actor, name: String, active: Boolean) {
    val style = TextStyle(
        color = if (active) Color(0xFFFFE066) else Color.White,
        fontSize = 13.sp,
        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
    )
    val layout = textMeasurer.measure(name, style)
    val x = a.rect.center.x - layout.size.width / 2
    // El polluelo es pequeño: la etiqueta va pegada a su cabeza, no en lo alto del frame.
    val y = a.rect.top + a.rect.height * 0.15f - layout.size.height - 6.dp.toPx()
    drawText(textMeasurer, name, Offset(x + 1.5f, y + 1.5f), style.copy(color = Color(0xAA000000)))
    drawText(textMeasurer, name, Offset(x, y), style)
    if (active) {
        val s = 6.dp.toPx()
        val cx = a.rect.center.x
        val ty = y - s - 3.dp.toPx()
        drawPath(Path().apply { moveTo(cx - s, ty); lineTo(cx + s, ty); lineTo(cx, ty + s); close() }, Color(0xFFFFE066))
    }
}

/**
 * Dibuja [image] cubriendo todo el lienzo (recortando lo que sobre, centrado), con el aspecto
 * [look] del momento del día (matriz de color + degradado de cielo). Sin suavizar los píxeles
 * salvo que se pida con [filter] (fotos, ilustraciones).
 */
fun DrawScope.drawBiomeBackdrop(image: ImageBitmap, look: DayLook = DayLook.Midday, filter: FilterQuality = FilterQuality.None) {
    drawBackdropLayer(image, look, filter = filter)
    drawDayGradient(look)
}

/**
 * Repinta un recorte del fondo ([image], del tamaño del fondo; ver BiomeOccluder) encima de lo
 * que ya hay, exactamente igual que en el fondo: con la matriz de color de [look] y, en una
 * capa aparte para que caiga solo sobre el recorte, el degradado del momento del día.
 */
private fun DrawScope.drawOccluder(image: ImageBitmap, look: DayLook) {
    drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint()) }
    drawBackdropLayer(image, look, filter = SCENERY_FILTER)
    drawDayGradient(look, blendMode = BlendMode.SrcAtop)
    drawIntoCanvas { it.restore() }
}

/** Las escenas fotográficas se dibujan suavizadas (fondo y recortes por igual). */
private val SCENERY_FILTER = FilterQuality.Medium

/** Imágenes de un fondo por capas (ver BiomeLayers), de atrás adelante. */
class BackdropLayers(
    val sky: ImageBitmap,
    val back: ImageBitmap,
    val terrain: ImageBitmap,
    /** Altura (fracción de [terrain]) donde empieza el suelo continuo (ver [solidRowFraction]). */
    val terrainSolid: Float,
    /** Viento sobre el terreno (hierba y árboles); null = terreno quieto. */
    val wind: WindPainter? = null,
    /** Suavizado al escalar: None para pixel art, Medium para ilustraciones. */
    val filter: FilterQuality = FilterQuality.None,
    /** Borde del fondo del suelo si está en cuesta (ver BiomeLayers.groundEdge). */
    val groundEdge: List<Pair<Float, Float>>? = null,
)

/**
 * Repinta, sobre la mascota [a] recién dibujada, la hierba del terreno que nace por delante
 * de su suelo y sobresale por encima: así la tapan las briznas que tiene delante (con el mismo
 * vaivén del viento). [crop] es el recorte con el que se dibuja el terreno.
 */
private fun DrawScope.drawGrassInFront(wind: WindPainter, crop: IntRect, time: Double, a: Actor) {
    // Algo más ancho que el sprite: el rebote al tocarla lo ensancha hasta un 18%.
    val rect = a.rect.let { Rect(it.left - it.width * 0.12f, it.top, it.right + it.width * 0.12f, it.bottom + 2f) }
    if (rect.isEmpty) return
    val srcPerDst = crop.width.toFloat() / size.width
    val groundInImage = crop.top + a.groundY * srcPerDst
    drawRect(
        wind.brush(crop.left.toFloat(), crop.top.toFloat(), srcPerDst, time.toFloat(), grassFrontOf = groundInImage),
        topLeft = rect.topLeft,
        size = rect.size,
    )
}

/**
 * Primera fila (fracción de la altura) en la que [image] es casi del todo opaca: donde empieza
 * el suelo continuo de una capa de terreno, por debajo de las puntas de hierba.
 */
fun solidRowFraction(image: ImageBitmap): Float {
    val pixels = image.toPixelMap()
    val columns = (0 until image.width step 8).toList()
    for (y in 0 until image.height) {
        val opaque = columns.count { pixels[it, y].alpha > 0.8f }
        if (opaque >= columns.size * 0.98) return y.toFloat() / image.height
    }
    return 1f
}

/** Cuánto más grande se dibuja la capa de fondo, para tener margen al desplazarse sin ver bordes. */
private const val BACK_ZOOM = 1.02f

/** Parte del margen que recorre la capa de fondo en su vaivén (parallax). */
private const val BACK_DRIFT = 0.6f

/** Segundos que tarda el cielo en recorrer su propio ancho: las nubes avanzan sin parar. */
private const val SKY_LOOP_S = 240.0

/** Segundos que tarda el vaivén lento de las capas del fondo en ir y volver. */
private const val DRIFT_PERIOD_S = 80.0

/** Altura (fracción de la imagen) que se mantiene fija al ampliar una capa: la línea del horizonte. */
private const val HORIZON_ANCHOR = 0.70f

/**
 * Dibuja un fondo por capas según [time] (segundos de escena): el cielo avanza sin parar en
 * horizontal (las nubes se mueven y la imagen se repite al llegar al borde), los elementos de
 * fondo se mecen despacio (parallax) y el terreno queda fijo delante; si tiene viento
 * ([BackdropLayers.wind]), su hierba y sus árboles se mecen.
 * El filtro del momento del día ([look]: matriz de color y degradado del cielo) se aplica
 * **solo al cielo**: el fondo y el terreno conservan siempre sus colores.
 */
fun DrawScope.drawLayeredBackdrop(
    layers: BackdropLayers,
    time: Double,
    look: DayLook = DayLook.Midday,
) {
    val drift = sin(time * 2 * kotlin.math.PI / DRIFT_PERIOD_S).toFloat()
    drawScrollingLayer(layers.sky, look, progress = time / SKY_LOOP_S, filter = layers.filter)
    drawDayGradient(look, ground = false)
    drawBackdropLayer(layers.back, DayLook.Midday, zoom = BACK_ZOOM, shift = drift * BACK_DRIFT, filter = layers.filter)
    val wind = layers.wind
    if (wind != null) {
        val crop = layerCrop(layers.terrain, zoom = 1f, shift = 0f)
        drawRect(wind.brush(crop.left.toFloat(), crop.top.toFloat(), crop.width.toFloat() / size.width, time.toFloat()))
    } else {
        drawBackdropLayer(layers.terrain, DayLook.Midday, filter = layers.filter)
    }
}

/**
 * Dibuja [image] a la altura de una capa sin ampliar, desplazada hacia la izquierda [progress]
 * veces su ancho y repetida a continuación. Las copias se alternan normal / en espejo: así cada
 * junta une un borde consigo mismo y el bucle no tiene corte aunque la imagen no sea cíclica
 * (p. ej. una nube cortada en el borde).
 */
private fun DrawScope.drawScrollingLayer(
    image: ImageBitmap,
    look: DayLook,
    progress: Double,
    filter: FilterQuality = FilterQuality.None,
) {
    val k = max(size.width / image.width, size.height / image.height)
    val w = (image.width * k).roundToInt()
    val h = (image.height * k).roundToInt()
    val top = ((size.height - h) / 2).roundToInt()
    // El patrón (normal + espejo) se repite cada dos anchos.
    val offset = ((progress % 2.0) * w).roundToInt()
    val colorFilter = if (look.isNeutral) null else look.colorFilter()
    var x = -offset
    var mirrored = false
    while (x < size.width) {
        if (x + w > 0) {
            val dst = IntOffset(x, top)
            if (mirrored) {
                scale(-1f, 1f, pivot = Offset(x + w / 2f, top + h / 2f)) {
                    drawImage(image, dstOffset = dst, dstSize = IntSize(w, h), filterQuality = filter, colorFilter = colorFilter)
                }
            } else {
                drawImage(image, dstOffset = dst, dstSize = IntSize(w, h), filterQuality = filter, colorFilter = colorFilter)
            }
        }
        x += w
        mirrored = !mirrored
    }
}

/**
 * Dibuja [image] cubriendo el lienzo sin suavizar los píxeles. Con [zoom] > 1 se amplía
 * (manteniendo el horizonte a la misma altura que una capa sin ampliar) y [shift], de -1 a 1,
 * la desplaza en horizontal dentro del margen que deja la ampliación. Aplica la matriz de
 * color de [look] (el degradado del cielo lo añade quien llama, una vez por fondo).
 */
private fun DrawScope.drawBackdropLayer(
    image: ImageBitmap,
    look: DayLook,
    zoom: Float = 1f,
    shift: Float = 0f,
    filter: FilterQuality = FilterQuality.None,
) {
    val crop = layerCrop(image, zoom, shift)
    drawImage(
        image,
        srcOffset = crop.topLeft,
        srcSize = crop.size,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = filter,
        colorFilter = if (look.isNeutral) null else look.colorFilter(),
    )
}

/**
 * Parte de [image] (en sus píxeles) que cubre el lienzo: centrada, ampliada [zoom] veces
 * manteniendo la línea del horizonte y desplazada [shift] (-1..1) dentro del margen.
 */
private fun DrawScope.layerCrop(image: ImageBitmap, zoom: Float, shift: Float): IntRect {
    val w = size.width
    val h = size.height
    val k0 = max(w / image.width, h / image.height)
    val k = k0 * zoom
    val srcW = (w / k).roundToInt().coerceAtMost(image.width)
    val srcH = (h / k).roundToInt().coerceAtMost(image.height)
    // Altura en pantalla del horizonte con el recorte centrado sin ampliar.
    val baseTop = (image.height - h / k0) / 2
    val horizonY = image.height * HORIZON_ANCHOR
    val screenHorizon = (horizonY - baseTop) * k0
    val top = (horizonY - screenHorizon / k).roundToInt().coerceIn(0, image.height - srcH)
    val slack = (image.width - srcW) / 2
    val left = (slack + shift.coerceIn(-1f, 1f) * slack).roundToInt().coerceIn(0, image.width - srcW)
    return IntRect(IntOffset(left, top), IntSize(srcW, srcH))
}
