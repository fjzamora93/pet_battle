# Pet Battle

Kotlin Multiplatform + Compose Multiplatform (Android y escritorio JVM). Ver README.md
para comandos de compilación y reglas del juego.

## Convenciones

- **Todos los comentarios del código deben ir en español** (`//`, `/* */`, KDoc `/** */`,
  `#` en ficheros de configuración y `<!-- -->` en XML). Los identificadores (clases,
  funciones, variables) se mantienen en inglés.

## Documentación

- La carpeta `docs/` guarda la documentación del diseño del juego.
- **`docs/reglas-de-combate.md` y `docs/reglas-de-cuidado.md` deben mantenerse al día**:
  cada vez que se cambie una regla o un valor de balance hay que actualizar el documento que
  corresponda en el mismo cambio (y su fecha de "Última actualización"). Deben describir lo
  que hace el código, no lo planeado.
  - **Combate** (`reglas-de-combate.md`): partida, enemigos, stats de combate, habilidades,
    recompensas y progresión (XP y niveles).
  - **Cuidado** (`reglas-de-cuidado.md`): necesidades de la mascota, cría, rareza, tienda y
    biomas, y ciclo del día.
  - **Compartido**: cómo afectan los cuidados a los stats de combate (y cómo el combate
    desgasta las necesidades) va en **los dos** documentos, aunque quede duplicado.
- `docs/mapas-de-defensa.md` explica cómo se generan la ruta de los enemigos y las zonas
  colocables de cada mapa de defensa a partir de su imagen (`tools/defense_maps/`). Si cambia
  ese proceso o se añade un mapa, hay que actualizarlo.
- `docs/reglas-pendientes.md` recoge lo planeado pero aún no implementado. Al implementar
  algo de ahí, pásalo al documento de reglas que corresponda y quítalo de pendientes.
