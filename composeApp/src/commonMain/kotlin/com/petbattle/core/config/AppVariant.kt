package com.petbattle.core.config

/**
 * Para qué dispositivo se ha compilado la app. En Android lo fija el flavor de Gradle
 * (`normal` o `watch`); en escritorio es [normal] salvo que se arranque con `-Pwatch`.
 */
enum class AppVariant {
    /** Móvil, tableta o escritorio: todas las pantallas (bioma, cuidado y defensa). */
    normal,

    /** Reloj: solo el modo cuidado, que es además la pantalla de entrada. */
    watch;

    /** Si desde esta variante se puede salir del modo cuidado al bioma (y de ahí a defensa). */
    val canLeaveCare: Boolean get() = this != watch

    companion object {
        /** Lee el nombre de la variante ("normal" o "watch"); cualquier otra cosa es [normal]. */
        fun parse(name: String?): AppVariant = entries.firstOrNull { it.name == name } ?: normal
    }
}
