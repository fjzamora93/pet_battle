package com.petbattle.core.audio

import com.petbattle.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.min

/**
 * Música en escritorio con javax.sound (OGG gracias a vorbisspi). La pista se decodifica entera
 * en memoria en segundo plano y un hilo propio la va enviando en bucle a un [SourceDataLine] con
 * un búfer grande: en Linux, un `Clip` (búfer pequeño sobre ALSA) se queda sin datos y la música
 * se entrecorta. Mientras carga, [play]/[pause] solo anotan lo que se quiere.
 */
class DesktopMusicPlayer : MusicPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stream: LoopingStream? = null
    private var track: MusicTrack? = null
    private var loading: Job? = null
    private var wantPlaying = false

    override fun play(track: MusicTrack) {
        wantPlaying = true
        if (this.track != track) {
            closeStream()
            this.track = track
            loading = scope.launch {
                val pcm = withContext(Dispatchers.IO) { decode(track) } ?: return@launch
                // La línea se abre ya en el hilo principal: si la carga se cancela, no queda abierta.
                val opened = try {
                    LoopingStream(pcm.format, pcm.readBytes())
                } catch (e: Exception) {
                    // Equipos sin dispositivo de audio: el juego sigue sin música.
                    println("No se pudo abrir el audio para ${track.path}: $e")
                    return@launch
                }
                stream = opened
                opened.begin()
                if (!wantPlaying) opened.pause()
            }
        } else {
            stream?.resume()
        }
    }

    override fun pause() {
        wantPlaying = false
        stream?.pause()
    }

    override fun release() {
        closeStream()
        track = null
    }

    private fun closeStream() {
        loading?.cancel()
        loading = null
        stream?.close()
        stream = null
    }

    private suspend fun decode(track: MusicTrack): AudioInputStream? = try {
        decodeToPcm(Res.readBytes(track.path))
    } catch (e: Exception) {
        println("No se pudo cargar la música ${track.path}: $e")
        null
    }
}

/**
 * Reproduce [pcm] en bucle sin fin. Pausar para la línea al instante (el hilo se queda esperando
 * en `write` hasta que se reanuda) y cerrar la libera y termina el hilo.
 */
private class LoopingStream(private val format: AudioFormat, private val pcm: ByteArray) {
    private val line: SourceDataLine = AudioSystem.getSourceDataLine(format).apply {
        // Un segundo de búfer: aguanta pausas del recolector de basura y del sistema sin cortes.
        open(format, format.frameSize * format.sampleRate.toInt())
    }

    @Volatile private var closed = false

    private val feeder = Thread({
        // Trozos de ~50 ms, siempre en frames enteros.
        val chunk = format.frameSize * (format.sampleRate.toInt() / 20)
        var position = 0
        try {
            while (!closed) {
                val n = min(chunk, pcm.size - position)
                line.write(pcm, position, n)
                position += n
                if (position >= pcm.size) position = 0
            }
        } catch (_: Exception) {
            // La línea se cerró mientras escribía: fin del hilo.
        }
    }, "música").apply {
        isDaemon = true
        priority = Thread.MAX_PRIORITY
    }

    fun begin() {
        line.start()
        feeder.start()
    }

    fun pause() = line.stop()

    fun resume() {
        if (!closed) line.start()
    }

    fun close() {
        closed = true
        line.stop()
        line.flush() // desbloquea el write pendiente
        line.close()
    }
}

/**
 * Decodifica un OGG Vorbis (vorbisspi) a PCM de 16 bits entero en memoria. Falla con
 * frecuencias que el decodificador no admite (p. ej. 96 kHz), por eso las pistas van a 44,1 kHz.
 */
internal fun decodeToPcm(oggBytes: ByteArray): AudioInputStream =
    AudioSystem.getAudioInputStream(ByteArrayInputStream(oggBytes)).use { encoded ->
        val src = encoded.format
        val pcm = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, src.sampleRate, 16, src.channels,
            src.channels * 2, src.sampleRate, false,
        )
        val decoded = AudioSystem.getAudioInputStream(pcm, encoded).use { it.readBytes() }
        AudioInputStream(ByteArrayInputStream(decoded), pcm, decoded.size.toLong() / pcm.frameSize)
    }
