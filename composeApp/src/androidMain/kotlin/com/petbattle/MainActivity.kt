package com.petbattle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.petbattle.core.audio.AndroidMusicPlayer
import com.petbattle.core.audio.MusicController
import com.petbattle.core.config.AppVariant
import com.petbattle.core.time.SystemClock
import com.petbattle.shared_features.pet_status.application.PetController
import com.petbattle.shared_features.pet_status.data.LocalPetRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var controller: PetController
    private lateinit var music: MusicController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val store = AndroidKeyValueStore(applicationContext)
        controller = PetController(LocalPetRepository(store), SystemClock, lifecycleScope)
        music = MusicController(AndroidMusicPlayer(applicationContext), store, lifecycleScope)
        lifecycleScope.launch { controller.load() }
        lifecycleScope.launch { music.load() }
        // El flavor (normal o watch) decide qué pantallas hay.
        val variant = AppVariant.parse(BuildConfig.APP_VARIANT)
        setContent { App(controller, music, variant) }
    }

    override fun onPause() {
        controller.flush()
        music.setInBackground(true) // la música calla con la app en segundo plano
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::controller.isInitialized) controller.tick() // pone al día el tiempo pasado en segundo plano
        if (::music.isInitialized) music.setInBackground(false)
    }

    override fun onDestroy() {
        if (::music.isInitialized) music.release()
        super.onDestroy()
    }
}
