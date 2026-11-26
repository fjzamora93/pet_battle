package com.petbattle.design_system.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.design_system.GameUi

/**
 * Aviso breve en una píldora de madera (p. ej. si le gustó la comida o ha nacido un polluelo).
 * Con [compact] (pantallas de reloj) es más pequeño y casi no deja margen a los lados.
 */
@Composable
fun FeedbackBubble(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    WoodPanel(modifier.padding(horizontal = if (compact) 4.dp else 90.dp), shape = RoundedCornerShape(50)) {
        Text(
            text,
            style = GameUi.text(if (compact) 11.sp else 15.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 18.dp, vertical = if (compact) 4.dp else 8.dp),
        )
    }
}
