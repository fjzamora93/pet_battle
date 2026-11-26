package com.petbattle

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.petbattle.core.audio.DesktopMusicPlayer
import com.petbattle.core.audio.MusicController
import com.petbattle.core.config.AppVariant
import com.petbattle.core.time.SystemClock
import com.petbattle.shared_features.pet_status.application.PetController
import com.petbattle.shared_features.pet_status.data.LocalPetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val store = FileKeyValueStore()
    val repository = LocalPetRepository(store)
    val controller = PetController(repository, SystemClock, scope)
    val music = MusicController(DesktopMusicPlayer(), store, scope)
    scope.launch { controller.load() }
    scope.launch { music.load() }
    // Con -Pwatch (ver build.gradle.kts) se simula el reloj: solo modo cuidado, ventana cuadrada.
    val variant = AppVariant.parse(System.getProperty("petbattle.variant"))
    val windowSize = if (variant == AppVariant.watch) 240.dp to 240.dp else 1280.dp to 720.dp

    application {
        Window(
            onCloseRequest = {
                // Asegura que los últimos cambios lleguen al disco antes de salir.
                controller.gameState.value?.let { runBlocking { repository.save(it) } }
                music.release()
                exitApplication()
            },
            title = "Pet Battle",
            state = rememberWindowState(width = windowSize.first, height = windowSize.second),
        ) {
            App(controller, music, variant)
        }
    }
}
