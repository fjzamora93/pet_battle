package com.petbattle.features.biome.presentation.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.design_system.GameIcon
import com.petbattle.design_system.GameUi
import com.petbattle.design_system.components.GameIconImage
import com.petbattle.features.biome.presentation.game.drawBiomeBackdrop
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.biomes.domain.isPlayable
import com.petbattle.shared_features.biomes.presentation.background
import com.petbattle.shared_features.biomes.presentation.label
import com.petbattle.shared_features.pet_status.domain.GameRules
import com.petbattle.shared_features.pet_status.domain.entities.GameStateEntity
import org.jetbrains.compose.resources.imageResource

/**
 * Cuadrícula de biomas (pestaña "Biomas" del menú de juego). Los comprados muestran cuántas
 * mascotas viven allí y elegirlos viaja a ellos; los bloqueados muestran el candado y su
 * precio, y elegirlos intenta comprarlos. Los que aún no se pueden jugar (Biome.isPlayable)
 * salen al final como "Próximamente".
 */
@Composable
fun BiomeGrid(gameState: GameStateEntity, onPicked: (Biome) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        // Primero los que se pueden jugar; los "próximamente", al final.
        items(Biome.entries.sortedBy { !it.isPlayable }) { biome ->
            BiomeTile(
                biome = biome,
                petCount = gameState.petsIn(biome).size,
                selected = gameState.currentBiome == biome,
                playable = biome.isPlayable,
                unlocked = gameState.isUnlocked(biome) && biome.isPlayable,
                affordable = gameState.gold >= GameRules.BIOME_PRICE,
                onClick = { onPicked(biome) },
            )
        }
    }
}

@Composable
private fun BiomeTile(
    biome: Biome,
    petCount: Int,
    selected: Boolean,
    playable: Boolean,
    unlocked: Boolean,
    affordable: Boolean,
    onClick: () -> Unit,
) {
    val image = imageResource(biome.background)
    // Madera oscura; el bioma actual, con borde dorado.
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = GameUi.Inset,
        border = BorderStroke(3.dp, if (selected) GameUi.Gold else GameUi.Outline),
    ) {
        Column(Modifier.padding(8.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
                    drawBiomeBackdrop(image)
                    // Los bloqueados se ven apagados.
                    if (!unlocked) drawRect(Color(0x99000000))
                }
                if (!unlocked) Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Text(biome.label, style = GameUi.text(15.sp), modifier = Modifier.padding(top = 6.dp))
            if (unlocked) {
                Text(
                    when (petCount) {
                        0 -> "Vacío"
                        1 -> "1 mascota"
                        else -> "$petCount mascotas"
                    },
                    style = GameUi.text(12.sp, if (petCount == 0) Color(0xFFCFC3B4) else GameUi.Gold),
                )
            } else if (!playable) {
                Text("Próximamente", style = GameUi.text(12.sp, Color(0xFFCFC3B4)))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameIconImage(GameIcon.Coin, 16.dp)
                    Text(
                        " Comprar por ${GameRules.BIOME_PRICE}",
                        style = GameUi.text(12.sp, if (affordable) GameUi.Gold else Color(0xFFCFC3B4)),
                    )
                }
            }
        }
    }
}
