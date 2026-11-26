package com.petbattle.design_system.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.petbattle.design_system.GameUi

/** Botón redondo de madera con el engranaje que abre las opciones. */
@Composable
fun OptionsButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    WoodPanel(
        modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = CircleShape,
    ) {
        Icon(
            Icons.Rounded.Settings,
            contentDescription = "Opciones",
            tint = Color.White,
            modifier = Modifier.align(Alignment.Center).size(size * 0.6f),
        )
    }
}

/** Ventana de opciones. De momento solo activa o desactiva el sonido. */
@Composable
fun OptionsDialog(soundEnabled: Boolean, onSoundEnabled: (Boolean) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        WoodPanel {
            Column(Modifier.padding(20.dp).widthIn(max = 280.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Opciones", style = GameUi.text(24.sp))
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    // Se estrecha en pantallas pequeñas (reloj) en vez de recortar el interruptor.
                    modifier = Modifier.fillMaxWidth().clickable { onSoundEnabled(!soundEnabled) },
                ) {
                    Text("Sonido", style = GameUi.text(18.sp), modifier = Modifier.weight(1f))
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = onSoundEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = GameUi.Green),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Box { WoodButton("Cerrar", onDismiss) }
            }
        }
    }
}
