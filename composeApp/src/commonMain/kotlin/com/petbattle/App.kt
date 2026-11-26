package com.petbattle

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.petbattle.core.audio.MusicController
import com.petbattle.core.audio.MusicTrack
import com.petbattle.core.config.AppVariant
import com.petbattle.design_system.components.OptionsDialog
import com.petbattle.features.biome.presentation.BiomeScreen
import com.petbattle.features.care.presentation.CareScreen
import com.petbattle.features.defense.presentation.DefenseScreen
import com.petbattle.shared_features.pet_status.application.PetController

private enum class Screen { Biome, Care, Defense }

private val AppColors = lightColorScheme(
    primary = Color(0xFF1F6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2CB),
    secondaryContainer = Color(0xFFCDE9DA),
    surface = Color(0xFFF5FBF6),
)

/**
 * Raíz: decide qué pantalla se ve (bioma, cuidado o defensa), cada una con su música en bucle.
 * En la variante [AppVariant.watch] solo existe el modo cuidado: es la pantalla de entrada y
 * no se puede salir de él.
 */
@Composable
fun App(controller: PetController, music: MusicController, variant: AppVariant = AppVariant.normal) {
    var screen by remember { mutableStateOf(if (variant == AppVariant.watch) Screen.Care else Screen.Biome) }
    var optionsOpen by remember { mutableStateOf(false) }
    val soundEnabled by music.soundEnabled.collectAsState()

    LaunchedEffect(screen) {
        music.setTrack(if (screen == Screen.Defense) MusicTrack.randomBattle() else MusicTrack.MainTheme)
    }

    MaterialTheme(colorScheme = AppColors) {
        Surface {
            when (screen) {
                Screen.Biome -> BiomeScreen(
                    controller,
                    onDefend = { screen = Screen.Defense },
                    onCare = { screen = Screen.Care },
                    onOptions = { optionsOpen = true },
                )
                Screen.Care -> CareScreen(
                    controller,
                    onBiome = if (variant.canLeaveCare) ({ screen = Screen.Biome }) else null,
                    onOptions = { optionsOpen = true },
                )
                Screen.Defense -> DefenseScreen(controller, onExit = { screen = Screen.Biome })
            }
            if (optionsOpen) {
                OptionsDialog(soundEnabled, music::setSoundEnabled, onDismiss = { optionsOpen = false })
            }
        }
    }
}
