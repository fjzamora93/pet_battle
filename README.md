# Pet Battle

Port a **Kotlin Multiplatform + Compose Multiplatform** del juego Mini Pou
(Flutter): una mascota virtual con 14 especies de pájaro y un modo
**defender la torre** donde las mascotas que has criado combaten contra
gusanos (que, desde el nivel 8, pasan a ser mariposas) usando la habilidad de su elemento.

Objetivos: **Android** y **escritorio (JVM)**, probado en Ubuntu/Linux.

## Ejecutar

Requisitos: JDK 17 y, para Android, el Android SDK (`local.properties` con `sdk.dir`).

```bash
# Escritorio (Linux/Ubuntu)
./gradlew :composeApp:run

# Paquete de escritorio autónomo (incluye su propio runtime de Java)
./gradlew :composeApp:createDistributable
./composeApp/build/compose/binaries/main/app/pet-battle/bin/pet-battle

# Paquete .deb para Ubuntu
./gradlew :composeApp:packageDeb

# Android (APK de depuración)
./gradlew :composeApp:assembleDebug
# -> composeApp/build/outputs/apk/debug/composeApp-debug.apk

# Tests (dominio + prueba visual que renderiza el modo defensa fuera de pantalla)
./gradlew :composeApp:desktopTest
```

La partida se guarda sola: en Android en SharedPreferences y en escritorio en
`~/.local/share/pet_battle/store.json`.

## Cómo se juega

- **Biomas**: empiezas en la **pradera**, que viene gratis; nunca eliges la especie. De
  momento solo se puede jugar también el **bosque** (20 de oro); el resto (montaña, coníferas,
  prado, tundra, tierras altas y costa) salen como "Próximamente". Los biomas jugables tienen
  el fondo por capas: el cielo avanza sin parar (las nubes se mueven) y el fondo se mece. Varias mascotas conviven en un bioma y puedes
  volver a cualquiera de los comprados cuando quieras.
- **Oro**: cada enemigo derrotado en el modo defensa da **1 moneda de oro**. Un huevo nuevo
  cuesta **10 de oro** (el primero es gratis).
- **Polluelos**: toda mascota nace como polluelo (hay que ponerle nombre). Come de todo y,
  tras 10 comidas, crece en una especie adulta de su bioma que coma su comida favorita (la
  que más veces le has dado). Si hay empate, o ninguna especie del bioma come eso, se elige
  al azar entre las del bioma.
- **Cría**: dos adultos de la misma especie en el mismo bioma, con la satisfacción global
  por encima del 90 %, tienen una vez al día un 50 % de probabilidad de poner un huevo, que
  eclosiona a las 8 h en un polluelo que crecerá a la especie de sus padres.
- **Rareza**: de 1 a 5 estrellas (común, poco común, raro, épico, legendario). Los huevos
  comprados salen con 1 (90 %) o 2 (10 %); las crías heredan de sus padres (ver
  `docs/reglas-de-cuidado.md`).
- **Cuidados**: aliméntala (eligiendo la comida: los adultos rechazan lo que no es de su
  dieta), límpiala, mándala a dormir y tócala para subir su diversión.
- **Defender**: coloca en la hierba tus mascotas adultas, de cualquier bioma (los polluelos no luchan). Los enemigos
  avanzan por el camino y aceleran cada 10 s; suben de nivel cada 20 s.
- **Progresión**: 1 XP por enemigo derrotado; pasar del nivel n al n+1 cuesta
  10·(n+1) XP. Máximo nivel 10.
- **Necesidades y stats de combate**: cada stat de combate rinde su total (según
  el nivel) por la media de tres necesidades: daño ← hambre, plumaje, pico y
  garras; velocidad ← descanso, digestión, clima; confianza (% de acierto) ←
  diversión, social, higiene. El descanso pesa más: sin descanso ataca a la mitad
  de velocidad. Detalle completo en
  `docs/reglas-de-combate.md` (combate) y `docs/reglas-de-cuidado.md` (cuidados).
- **Habilidades por elemento**: fuego quema (120% del daño en 3 s), agua
  ralentiza, aire empuja hacia atrás, tierra golpea normal.

## Arquitectura

```
composeApp/src/
  commonMain/kotlin/com/petbattle/
    core/                      reloj, almacenamiento, utilidades de dibujo
    shared_features/
      pet_species/             especie, catálogo de sprites, BirdFrame
      pet_skills/              domain/entities (PetSkillsEntity, PetSkillEntity,
                               SkillEffectEntity), rasgos, progresión, catálogo
      pet_status/              domain/entities (PetEntity, PetStatusEntity,
                               GameStateEntity), reglas, efectos de combate,
                               repositorio y controlador (StateFlow)
    features/
      pet/                     pantalla de la mascota (escena en Canvas + UI)
      defense/                 domain/entities (EnemyEntity, DefenseMapEntity),
                               reglas de combate, motor del juego y renderizado
  androidMain/                 MainActivity, SharedPreferences, orientación
  desktopMain/                 ventana, almacenamiento en fichero
  commonTest/ desktopTest/     tests
```

Dependencias en un solo sentido: `pet_species ← pet_skills ← pet_status ← features`.
El dominio es Kotlin puro (sin Compose), por eso se prueba sin interfaz.

Equivalencias con la versión Flutter: Flame → bucle de juego propio con
`withFrameNanos` + `Canvas`; Riverpod → controlador con `StateFlow`;
freezed/json → `data class` + kotlinx.serialization.

Los sprites (pájaros, habilidades) y el mapa provienen del proyecto
Flutter, donde se generan con scripts en `tool/`. Los enemigos (gusano y mariposa) se
generan con `tools/enemy_sprites/enemy_sheet.py` a partir de los dibujos de esa carpeta.
