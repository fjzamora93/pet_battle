package com.petbattle.features.biome.presentation.widgets

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.petbattle.shared_features.pet_status.domain.PetNeedsBalance

@Composable
fun StatBar(icon: ImageVector, label: String, value: Double, modifier: Modifier = Modifier) {
    val t = (value / PetNeedsBalance.MAX_STAT).coerceIn(0.0, 1.0).toFloat()
    val color = lerp(Color(0xFFE5484D), Color(0xFF46A758), t * t)
    val animated by animateFloatAsState(t, tween(400, easing = FastOutSlowInEasing))
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = "$label ${value.toInt()}%", tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
            color = color,
            trackColor = Color.Black.copy(alpha = 0.12f),
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
