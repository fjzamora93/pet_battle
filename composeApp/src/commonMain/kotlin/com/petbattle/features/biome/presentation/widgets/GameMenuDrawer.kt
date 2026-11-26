package com.petbattle.features.biome.presentation.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.petbattle.design_system.GameIcon
import com.petbattle.design_system.GameUi
import com.petbattle.design_system.components.GameIconImage
import com.petbattle.design_system.components.WoodButton
import com.petbattle.design_system.components.WoodIconButton
import com.petbattle.design_system.components.WoodPanel
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.biomes.presentation.label
import com.petbattle.shared_features.pet_status.domain.GameRules
import com.petbattle.shared_features.pet_status.domain.entities.GameStateEntity

/** Pestañas del menú de juego. */
enum class MenuTab { eggs, biomes }

/**
 * Menú de juego: un panel que entra por la derecha (como un cajón) con dos pestañas, comprar
 * huevo y comprar bioma. Tocar fuera del panel lo cierra.
 */
@Composable
fun GameMenuDrawer(
    open: Boolean,
    tab: MenuTab,
    gameState: GameStateEntity,
    onTab: (MenuTab) -> Unit,
    onBuyEgg: () -> Unit,
    onPickBiome: (Biome) -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x88000000))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            open,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            WoodPanel(
                Modifier
                    .fillMaxHeight()
                    .width(min(maxWidth * 0.62f, 520.dp))
                    .safeDrawingPadding()
                    .padding(8.dp)
                    // Los toques dentro del panel no lo cierran.
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Column(Modifier.fillMaxSize().padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TabButton("Huevos", GameIcon.Egg, tab == MenuTab.eggs, { onTab(MenuTab.eggs) }, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        TabButton("Biomas", GameIcon.Biome, tab == MenuTab.biomes, { onTab(MenuTab.biomes) }, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        WoodIconButton(GameIcon.Close, "Cerrar", onClose, size = 44.dp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x55000000))
                            .padding(10.dp),
                    ) {
                        when (tab) {
                            MenuTab.eggs -> EggShop(gameState, onBuyEgg)
                            MenuTab.biomes -> BiomeGrid(gameState, onPickBiome, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

/** Pestaña: madera clara si está activa, oscura si no. */
@Composable
private fun TabButton(text: String, icon: GameIcon, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) GameUi.WoodLight else GameUi.Inset)
            .border(3.dp, GameUi.Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        GameIconImage(icon, 30.dp)
        Text(text, style = GameUi.text(16.sp), modifier = Modifier.padding(start = 6.dp))
    }
}

/** Pestaña "Huevos": un huevo para el bioma actual, con su precio. */
@Composable
private fun EggShop(gameState: GameStateEntity, onBuyEgg: () -> Unit) {
    val price = GameRules.eggPrice(gameState)
    val missing = GameRules.goldMissingForEgg(gameState)
    val biome = gameState.currentBiome
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GameIconImage(GameIcon.Egg, 72.dp)
        Text(
            if (biome != null) "Huevo para ${biome.label.lowercase()}" else "Huevo",
            style = GameUi.text(20.sp),
        )
        Text(
            "Lo que le des de comer decidirá en qué pájaro se convierte.",
            style = GameUi.text(13.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameIconImage(GameIcon.Coin, 30.dp)
            Text(if (price == 0) "Gratis" else "$price", style = GameUi.text(20.sp, GameUi.Gold), modifier = Modifier.padding(start = 6.dp))
        }
        Spacer(Modifier.height(6.dp))
        WoodButton("Comprar huevo", if (missing == 0) onBuyEgg else null, icon = GameIcon.Plus)
        if (missing > 0) {
            Text("Te faltan $missing de oro", style = GameUi.text(13.sp, Color(0xFFFFB4A8)), modifier = Modifier.padding(top = 6.dp))
        }
    }
}
