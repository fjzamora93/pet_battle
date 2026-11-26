# Arquitectura del proyecto

Última actualización: 2026-11-26.

## En resumen

- **Un solo código para dos apps.** El juego está escrito en **Kotlin Multiplatform** con
  **Compose Multiplatform** para la interfaz. El mismo código genera la app de **Android** y la
  de **escritorio** (JVM: Linux, macOS, Windows). Casi todo vive en `commonMain`; cada
  plataforma solo aporta un puñado de piezas propias (arranque, guardado, audio, shader).
- **Un único módulo Gradle**, `composeApp`, organizado por **funcionalidades** (`features/`,
  `shared_features/`) y, dentro de cada una, por **capas**: `domain` (reglas del juego en
  Kotlin puro), `application` (controlador con estado), `data` (persistencia) y `presentation`
  (pantallas Compose).
- **El estado de la partida es un solo objeto** (`GameStateEntity`) que expone un
  controlador (`PetController`) como `StateFlow`. La interfaz lo observa y le manda las
  acciones del jugador. Cada cambio se guarda solo, como un documento JSON.
- **Dos compilaciones de Android.** El *flavor* `normal` (móvil y tableta, con bioma,
  cuidado y defensa) y el `watch` (reloj Wear OS, solo el modo cuidado). El código es el
  mismo: la variante llega a `App()` como `AppVariant`.
- **Sin motor de juego.** Las escenas (el bioma, el modo cuidado y el modo defensa) se dibujan en un `Canvas`
  de Compose con un bucle propio por frame (`withFrameNanos`).
- **Pocas dependencias.** Compose, corrutinas y kotlinx.serialization. No hay inyección de
  dependencias, librería de navegación ni base de datos.

---

## 1. Cómo se construyen las apps

Todo sale del módulo `composeApp` (ver `composeApp/build.gradle.kts`), que declara dos
*targets*:

| Target | Qué genera | Comando |
|---|---|---|
| `androidTarget` | APK de Android para móvil/tableta (minSdk 24, targetSdk 35, JVM 17) | `./gradlew :composeApp:assembleNormalDebug` |
| | APK de Android para reloj (Wear OS o Android normal, minSdk 24) | `./gradlew :composeApp:assembleWatchDebug` |
| `jvm("desktop")` | App de escritorio | `./gradlew :composeApp:run` |
| | La misma, simulando el reloj (ventana pequeña, solo cuidado) | `./gradlew :composeApp:run -Pwatch` |
| | Ejecutable autónomo (con su propio Java) | `./gradlew :composeApp:createDistributable` |
| | Paquete `.deb` para Ubuntu | `./gradlew :composeApp:packageDeb` |

Requisitos: JDK 17 y, para Android, el Android SDK (`local.properties` con `sdk.dir`).

### Flavors de Android: `normal` y `watch`

La app Android tiene una dimensión de *flavor*, `device`, con dos valores:

| Flavor | Dispositivo | Pantallas | Manifiesto propio |
|---|---|---|---|
| `normal` | Móvil y tableta | Bioma (entrada), cuidado y defensa | `src/androidNormal/AndroidManifest.xml`: siempre en horizontal |
| `watch` | Reloj: Wear OS o Android normal, como el SaveWatch 2 (Android 8.1); minSdk 24 | Solo cuidado (entrada) | `src/androidWatch/AndroidManifest.xml`: `uses-feature android.hardware.type.watch` y app independiente (`com.google.android.wearable.standalone`) |

Cada flavor fija `BuildConfig.APP_VARIANT` (`"normal"` o `"watch"`); `MainActivity` lo
convierte en `AppVariant` (`core/config/AppVariant.kt`) y se lo pasa a `App()`. Con Kotlin
Multiplatform los manifiestos de los flavors se indican a mano en `build.gradle.kts`
(`sourceSets["watch"].manifest.srcFile(...)`): si no, AGP no los fusiona. Los dos flavors
comparten `applicationId` (`com.petbattle`), como pide Google Play para publicar la versión
de reloj en la misma ficha.

### Source sets

El código se reparte en *source sets*. Cada uno se compila solo para los targets que le
corresponden:

| Source set | Para | Qué contiene |
|---|---|---|
| `commonMain` | Ambas | El juego entero: dominio, controlador, pantallas, recursos |
| `androidMain` | Android | `MainActivity`, guardado en SharedPreferences, audio con MediaPlayer, shader AGSL |
| `androidNormal` / `androidWatch` | Cada flavor de Android | De momento solo su `AndroidManifest.xml` |
| `desktopMain` | Escritorio | `main()` con la ventana, guardado en fichero, audio con javax.sound, shader SkSL |
| `commonTest` | Ambas | Tests del dominio y del controlador |
| `desktopTest` | Escritorio | Tests visuales (renderizan fuera de pantalla) y de recursos |

Las versiones de todo están centralizadas en `gradle/libs.versions.toml` (*version catalog*).
`gradle.properties` activa la caché de compilación y la *configuration cache* de Gradle.

## 2. Estructura de carpetas

```
composeApp/src/
  commonMain/
    kotlin/com/petbattle/
      App.kt                    raíz de la interfaz: tema, pantalla actual, música
      core/                     piezas técnicas sin reglas de juego
        audio/                  MusicController, MusicTrack, interfaz MusicPlayer
        config/                 AppConfig (intervalo del tick, frecuencia de guardado),
                                AppVariant (normal o reloj)
        persistence/            interfaz KeyValueStore
        time/                   Clock, DayCycle (momento del día)
        ui/                     utilidades de dibujo, código de cada plataforma, shader de viento
      design_system/            el aspecto común de la interfaz
        GameUi.kt               colores, estilo de texto e iconos (GameIcon)
        components/             componentes compartidos entre pantallas: paneles y botones de
                                madera, barra, opciones, panel de estados, selector de comida,
                                diálogo de nombre, insignias de nivel y rango, BirdFrame, avisos
      shared_features/          lo que usan varias pantallas
        biomes/                 Biome (dominio) + catálogo de imágenes
        pet_food/               Food (dominio) + catálogo de iconos
        pet_species/            especies, catálogo de sprites
        pet_skills/             habilidades, rasgos, progresión (XP y niveles)
        pet_combat/             stats de combate calculados desde las necesidades
        pet_status/             partida, necesidades, cría, rareza, economía,
                                repositorio y PetController
      features/                 las pantallas
        biome/                  pantalla del bioma (escena con todas sus mascotas + paneles)
        care/                   modo cuidado: una mascota de cerca (reutiliza la escena
                                del bioma, ampliada); en el reloj es la única pantalla
        defense/                modo defensa (reglas, mapa, bucle, renderizado); los mapas
                                se generan con tools/defense_maps (ver mapas-de-defensa.md)
    composeResources/
      drawable/                 sprites, fondos, iconos (PNG)
      files/sounds/             música (OGG)
  androidMain/                  MainActivity, AndroidManifest, implementaciones Android
  androidNormal/ androidWatch/  manifiesto propio de cada flavor (móvil / reloj)
  desktopMain/                  main.kt, implementaciones de escritorio
  commonTest/ desktopTest/      tests

tools/                          scripts Python que generan sprites y fondos
docs/                           documentación de diseño (reglas, arquitectura, sprites)
```

## 3. Capas y dependencias

Cada funcionalidad se divide en capas con un sentido de dependencia claro:

```
presentation  ──►  application  ──►  domain  ◄──  data
 (Compose)         (controlador)     (Kotlin       (repositorio
                                      puro)         JSON)
```

| Capa | Qué hay | Reglas |
|---|---|---|
| `domain` | Entidades (`*Entity`), reglas (`*Rules`), valores de balance (`*Balance`), contratos (`PetRepository`) | **Kotlin puro**: no importa nada de Compose ni de la plataforma. Funciones puras: reciben un estado y devuelven el siguiente. Se prueba sin interfaz. |
| `application` | `PetController` | Orquesta: aplica el tiempo, llama a las reglas, guarda y publica el estado. |
| `data` | `LocalPetRepository` | Implementa `PetRepository` sobre el `KeyValueStore`. |
| `presentation` | Pantallas, widgets, escenas y catálogos de recursos | Compose. Lee el estado y llama al controlador. Los **catálogos** (`PetSpeciesCatalog`, `BiomeCatalog`, `FoodCatalog`…) asocian cada valor del dominio con su imagen; así el dominio no sabe nada de recursos. |

Entre funcionalidades, las dependencias también van en un solo sentido:

```
biomes, pet_food  ◄──  pet_species  ◄──  pet_skills  ◄──  pet_status  ◄──  pet_combat
                                                                  ▲
                                  design_system (componentes compartidos; usa core y shared_features)
                                                                  ▲
                                  features/biome, features/care, features/defense (usan todas)
```

- **Todo componente de interfaz que use más de una pantalla va en
  `design_system/components/`** (paquete `com.petbattle.design_system.components`); lo que
  solo usa una pantalla se queda en su `features/<pantalla>/presentation/widgets`. Los colores,
  el estilo de texto y los iconos están en `design_system/GameUi.kt`.
- `design_system` nunca importa nada de `features`.

- `shared_features` y `core` nunca importan nada de `features`.
- `core` no conoce el juego: no importa nada de `features` ni de `shared_features`.

## 4. Arranque: cómo se monta cada app

No hay framework de inyección de dependencias: cada plataforma crea las piezas a mano en su
punto de entrada y se las pasa a `App()`, que es común.

| | Android (`MainActivity.kt`) | Escritorio (`main.kt`) |
|---|---|---|
| Almacén | `AndroidKeyValueStore` (SharedPreferences) | `FileKeyValueStore` (`~/.local/share/pet_battle/store.json`) |
| Audio | `AndroidMusicPlayer` | `DesktopMusicPlayer` |
| Ámbito de corrutinas | `lifecycleScope` de la Activity | `CoroutineScope(SupervisorJob() + Dispatchers.Main)` |
| Al salir o pausar | `onPause` → `controller.flush()` (guarda ya) y silencia la música | Al cerrar la ventana guarda de forma síncrona y libera el audio |
| Al volver | `onResume` → `controller.tick()` (pone al día el tiempo pasado) | — |

Con eso las dos plataformas hacen lo mismo:

```
store       = KeyValueStore de la plataforma
repository  = LocalPetRepository(store)
controller  = PetController(repository, SystemClock, scope)
music       = MusicController(MusicPlayer de la plataforma, store, scope)
controller.load(); music.load()
App(controller, music, variant)   // variant: AppVariant.normal o .watch
```

`App()` aplica el tema Material 3 y decide qué pantalla se ve (`BiomeScreen`, `CareScreen`
o `DefenseScreen`) con un simple `enum` y `mutableStateOf`: no hay librería de navegación.
También elige la música de cada pantalla. Con `AppVariant.watch` arranca en `CareScreen` y
no ofrece salir de ella; en escritorio la variante sale de `-Pwatch` (propiedad
`petbattle.variant`).

## 5. Estado y flujo de datos

```
        acciones del jugador (feedPet, clean, hatch, buyBiome…)
  UI  ───────────────────────────────────────────────────────►  PetController
   ▲                                                                  │
   │  gameState: StateFlow<GameStateEntity?>                          │ reglas del dominio
   │  dayPhase:  StateFlow<DayPhase>                                  │ (funciones puras)
   │  events:    Flow<LifeEvent> (huevos, eclosiones)                 ▼
   └───────────────────────────────────────────────────── nuevo GameStateEntity
                                                                      │
                                                         guardado automático (JSON)
```

- **Un único estado**: `GameStateEntity` (mascotas, oro, biomas comprados, mascota activa…)
  es un `data class` inmutable. Cada acción produce uno nuevo.
- **El tiempo manda.** Antes de cada acción, el controlador avanza a **todas** las mascotas
  hasta "ahora" (`advanceAll`). Así el resultado es el mismo con la app abierta o cerrada.
- **Tick en vivo**: cada `AppConfig.TICK_INTERVAL_MS` (1 s) el controlador avanza el estado.
- **Puesta al día offline**: al cargar se simula todo el tiempo transcurrido desde el último
  guardado (minuto a minuto, con un máximo de 7 días), incluidas las eclosiones y la cría.
- **Guardado limitado**: como mucho una escritura cada `AppConfig.SAVE_THROTTLE_MS` (3 s).
  Los cambios importantes se guardan al momento: crecer, dormir, nacer un huevo, liberar,
  cambiar de bioma o de mascota, comprar un bioma y los eventos de cría y eclosión.
- **Eventos de una sola vez** (huevo puesto, eclosión): salen por un `Channel`, para que la
  interfaz los muestre una vez.
- **Reloj inyectable**: el controlador recibe un `Clock` y funciones de "momento del día";
  los tests usan relojes falsos.
- La interfaz lee el estado con `collectAsState()`.

El estado del **combate** (enemigos, proyectiles, vidas) no pasa por el controlador: vive en
`DefenseGame`, cambia en cada frame y no se guarda. Solo los efectos que deben persistir se
mandan al controlador: XP (`awardXp`), oro (`awardGold`) y el desgaste de cada disparo
(`petFired`).

## 6. Persistencia

- **`KeyValueStore`** (en `core/persistence`): interfaz mínima `getString` / `setString` /
  `remove`. Hay una implementación por plataforma y una en memoria para los tests.
  - Android: `SharedPreferences` (`pet_battle`).
  - Escritorio: un objeto JSON en `$XDG_DATA_HOME/pet_battle/store.json` (por defecto
    `~/.local/share/...`). Escribe primero en un `.tmp` y luego lo renombra, para no dejar
    el fichero a medias.
- **`LocalPetRepository`** guarda la partida entera como **un solo documento JSON** con
  versión (`{"v": 1, "roster": {...}}`) bajo la clave `pet_state`. La partida es pequeña y
  siempre se lee y se escribe entera, así que no hace falta base de datos.
- **Compatibilidad**: `ignoreUnknownKeys` y `coerceInputValues` toleran campos nuevos o
  retirados; las especies renombradas se migran antes de decodificar (`RENAMED_SPECIES`); y
  `GameRules.migrate` corrige partidas antiguas (p. ej. mascotas en biomas no jugables). Una
  partida ilegible se descarta y se empieza de cero en lugar de fallar.
- La opción de sonido se guarda en el mismo almacén (`sound_enabled`).

## 7. Dibujo y bucles de juego

No se usa un motor de juego. Las dos escenas siguen el mismo patrón:

1. Un `LaunchedEffect` con un bucle `withFrameNanos { … }` calcula el tiempo transcurrido
   desde el frame anterior y avanza la simulación.
2. Un `Canvas` de Compose dibuja el resultado: imágenes recortadas de *spritesheets*,
   formas y texto (`TextMeasurer`).
3. Los toques se traducen a coordenadas del mapa y se mandan a la simulación.

| Escena | Simulación | Dibujo |
|---|---|---|
| Bioma (`features/biome/presentation/game/BiomeScene.kt`) | Estado interno de la escena: las mascotas andan, vuelan, comen y duermen; comida en el suelo | Fondo por capas (cielo que se desplaza + paisaje), mascotas, filtro de color según el momento del día (`DayLight.kt`) |
| Cuidado (`features/care/presentation/CareScreen.kt`) | La misma `BiomeScene` con una sola mascota, la zona de paseo recortada a lo visible, un rango de profundidad (`depthRange`) cercano, un techo de vuelo (`flightCeiling`) bajo el HUD y sin nombres (`showNames = false`) | La escena ampliada con `graphicsLayer` (escala desde el centro de abajo); los toques se transforman solos a coordenadas de la escena |
| Defensa (`features/defense/presentation/game/DefenseGame.kt`) | `DefenseGame.update(dt)`: aparición de enemigos, movimiento por rutas (`RoutePath`), disparos, habilidades | `DefenseRenderer.kt`. El HUD es Compose normal, encima del `Canvas`, alimentado con `DefenseHudState` |

**Shader de viento.** La hierba y los árboles del bioma se mecen con un shader escrito una
vez (`WIND_SHADER_SOURCE` en `core/ui/WindShader.kt`). El mismo texto vale como **SkSL**
(escritorio, `RuntimeEffect` de Skia) y como **AGSL** (Android 13+, `RuntimeShader`). En
Android anterior a 13 la imagen se dibuja quieta.

## 8. Código específico de cada plataforma

Lo que cambia por plataforma se resuelve de dos maneras:

- **`expect` / `actual`**, para funciones pequeñas:

  | `expect` (commonMain) | Android | Escritorio |
  |---|---|---|
  | `systemNowMillis()` (`core/time/Clock.kt`) | `System.currentTimeMillis()` | igual |
  | `localUtcOffsetMillis()` (`core/time/DayCycle.kt`) | `TimeZone` de Java | igual |
  | `PlatformBackHandler` (`core/ui/Platform.kt`) | Botón/gesto de atrás | No hace nada |
  | `Modifier.hiddenPointer` (`core/ui/Platform.kt`) | No hace nada | Oculta el ratón |
  | `isRoundScreen()` (`core/ui/Platform.kt`) | `Configuration.isScreenRound` | Siempre `false` |
  | `createWindPainter` (`core/ui/WindShader.kt`) | AGSL (`RuntimeShader`) | SkSL (Skia `RuntimeEffect`) |

- **Interfaces + implementación inyectada en el arranque**, para piezas con estado:
  `KeyValueStore` y `MusicPlayer`. Así los tests pueden usar `InMemoryKeyValueStore` y
  `SilentMusicPlayer`.

## 9. Recursos

- Están en `commonMain/composeResources/` y se acceden con la clase generada
  `com.petbattle.resources.Res` (`Res.drawable.*` para imágenes, `Res.readBytes(...)` para
  ficheros). Compose los empaqueta en los *assets* del APK y en el JAR de escritorio.
- **Imágenes** (`drawable/`): *spritesheets* de pájaros y enemigos, fondos de los biomas
  (por capas en pradera y bosque, más el mapa de pesos del viento) e iconos de la interfaz.
- **Música** (`files/sounds/`): un tema principal y tres de batalla, en OGG Vorbis.
  - Android: `MediaPlayer`, leyendo el OGG de los *assets*.
  - Escritorio: `Clip` de `javax.sound`. Java no lee OGG por sí mismo; por eso se añade
    `vorbisspi`.
- **Generación de sprites** (`tools/`, fuera de la compilación): scripts de Python con
  **Pillow**, **NumPy** y **SciPy** que montan las *spritesheets* de pájaros a partir de los
  dibujos (`tools/bird_sprites`), los enemigos (`tools/enemy_sprites/enemy_sheet.py`), las
  capas de la pradera y los iconos. Ver `docs/sprites-de-pajaros.md`.

## 10. Librerías

Versiones en `gradle/libs.versions.toml`.

### Herramientas de compilación

| Plugin | Versión | Para qué |
|---|---|---|
| Kotlin Multiplatform (`org.jetbrains.kotlin.multiplatform`) | 2.2.20 | Compilar el mismo código para Android y JVM |
| Compilador de Compose (`org.jetbrains.kotlin.plugin.compose`) | 2.2.20 | Transforma las funciones `@Composable` |
| kotlinx.serialization (`org.jetbrains.kotlin.plugin.serialization`) | 2.2.20 | Genera los serializadores de las `@Serializable` |
| Compose Multiplatform (`org.jetbrains.compose`) | 1.9.0 | Interfaz común, recursos (`Res`), empaquetado de escritorio |
| Android Gradle Plugin (`com.android.application`) | 8.11.1 | Compilar y empaquetar el APK |

### Dependencias

| Librería | Versión | Dónde | Para qué |
|---|---|---|---|
| `compose.runtime`, `foundation`, `ui` | 1.9.0 | común | Estado de Compose, `Canvas`, gestos, layout |
| `compose.material3` | 1.9.0 | común | Tema, botones, diálogos, paneles |
| `compose.components.resources` | 1.9.0 | común | Acceso a imágenes y ficheros con `Res` |
| `material-icons-extended` | 1.7.3 | común | Iconos de Material |
| `kotlinx-coroutines-core` | 1.10.2 | común | Tick, guardado diferido, `StateFlow`, `Channel` |
| `kotlinx-serialization-json` | 1.9.0 | común | Guardar y cargar la partida en JSON |
| `androidx.activity:activity-compose` | 1.10.1 | Android | `ComponentActivity.setContent`, *edge-to-edge*, botón atrás |
| `kotlinx-coroutines-android` | 1.10.2 | Android | `Dispatchers.Main` en Android |
| `compose.desktop.currentOs` | 1.9.0 | escritorio | Ventana y Skia nativo del sistema operativo |
| `kotlinx-coroutines-swing` | 1.10.2 | escritorio | `Dispatchers.Main` sobre el hilo de Swing |
| `com.googlecode.soundlibs:vorbisspi` | 1.0.3.3 | escritorio | Leer OGG Vorbis con `javax.sound` |
| `kotlin("test")` | 2.2.20 | tests | Aserciones y anotaciones de test |
| `kotlinx-coroutines-test` | 1.10.2 | tests | `runTest` para probar código `suspend` (repositorio, almacén) |

### Lo que *no* usamos (a propósito)

| Necesidad | Solución en lugar de una librería |
|---|---|
| Motor de juego | Bucle con `withFrameNanos` + `Canvas` de Compose |
| Inyección de dependencias | Construcción manual en `MainActivity` / `main.kt` |
| Navegación | `enum Screen` + `mutableStateOf` en `App.kt` |
| Base de datos | Un documento JSON en un almacén clave/valor |
| ViewModel / gestión de estado | `PetController` con `StateFlow` |
| Fechas y zonas horarias | `Long` de epoch + `localUtcOffsetMillis` (`expect`) |

## 11. Tests

```bash
./gradlew :composeApp:desktopTest
```

Ejecuta `commonTest` y `desktopTest` en la JVM.

- **`commonTest`**: reglas del dominio (necesidades, combate, cría, rareza, progresión) y
  el guardado y la migración de partidas (`LocalPetRepository` sobre
  `InMemoryKeyValueStore`, con `kotlinx-coroutines-test`).
- **`desktopTest`**: comprobaciones de recursos (que las *spritesheets* y las pistas de
  música existen y tienen el formato esperado) y **tests visuales** que renderizan el modo
  defensa, el viento de la pradera y el modo cuidado (móvil y reloj, con la esfera redonda
  marcada) fuera de pantalla, en `build/visual/`.

## 12. Convenciones

- Comentarios en **español**; identificadores en **inglés** (ver `CLAUDE.md`).
- Los valores de balance van en objetos `*Balance` (solo datos) y la lógica en `*Rules`.
- Al cambiar una regla o un valor de balance, actualiza `docs/reglas-de-combate.md` o
  `docs/reglas-de-cuidado.md` (ver `CLAUDE.md`).
