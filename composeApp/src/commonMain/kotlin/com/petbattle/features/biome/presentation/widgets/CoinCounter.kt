package com.petbattle.features.biome.presentation.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.design_system.GameIcon
import com.petbattle.design_system.GameUi
import com.petbattle.design_system.components.GameIconImage

/**
 * Monedas de oro (arriba a la izquierda): la moneda solapa una barra oscura con la cantidad y
 * un "+" que abre la tienda ([onShop]).
 */
@Composable
fun CoinCounter(gold: Int, onShop: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(modifier.width(170.dp)) {
        Box(
            Modifier
                .padding(start = 24.dp, top = 8.dp, end = 18.dp)
                .width(128.dp)
                .clip(shape)
                .background(GameUi.Inset)
                .border(3.dp, GameUi.Outline, shape)
                .padding(start = 26.dp, end = 22.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Text("$gold", style = GameUi.text(22.sp, GameUi.Gold), modifier = Modifier.align(Alignment.Center))
        }
        GameIconImage(GameIcon.Coin, 52.dp, contentDescription = "Oro")
        GameIconImage(
            GameIcon.Plus, 36.dp,
            Modifier
                .align(Alignment.TopEnd)
                .offset(y = 4.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onShop)
                .size(36.dp),
            contentDescription = "Tienda",
        )
    }
}
