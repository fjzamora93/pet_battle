package com.petbattle.design_system.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import com.petbattle.core.ui.drawSpriteFrame
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.presentation.BirdSheet
import com.petbattle.shared_features.pet_species.presentation.sheet
import com.petbattle.shared_features.pet_species.presentation.spriteScale
import org.jetbrains.compose.resources.imageResource

/**
 * Un frame nítido y ampliado de un spritesheet de pájaro (pose de reposo por defecto). Las
 * especies pequeñas (el polluelo) se dibujan más pequeñas, apoyadas abajo y centradas.
 */
@Composable
fun BirdFrame(
    species: PetSpecies,
    size: Dp,
    modifier: Modifier = Modifier,
    row: Int = BirdSheet.IDLE_ROW,
    column: Int = 0,
) {
    val image = imageResource(species.sheet)
    Canvas(modifier.size(size)) {
        val side = this.size.width * species.spriteScale
        val origin = Offset((this.size.width - side) / 2, this.size.height - side)
        drawSpriteFrame(image, BirdSheet.FRAME_SIZE, column, row, origin, side, filterQuality = BirdSheet.FILTER)
    }
}
