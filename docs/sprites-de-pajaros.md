# Hojas de sprites de pájaros

Cada especie tiene una hoja `composeApp/src/commonMain/composeResources/drawable/pet_<especie>.png`
con la misma estructura, para que se pueda sustituir el arte de una especie sin tocar código.

## Rejilla

- **8 columnas × 6 filas** de frames de **256 × 256 px** (hoja de 2048 × 1536).
- Una **animación por fila**; las filas con menos de 8 frames dejan el resto vacío.
- El pájaro **mira a la izquierda** (el juego lo refleja cuando se gira).
- De pie, los **pies** quedan a ~252 px del borde superior de su casilla y centrados en
  horizontal (~124 px); en vuelo, el cuerpo centrado algo por encima del centro de la casilla.

| Fila | Animación | Frames | Cuándo se usa |
|---|---|---|---|
| 0 | Volar | 8 | Mientras vuela de un sitio a otro |
| 1 | Reposo | 6 | De pie, despierta (también en el modo defensa y en las fichas) |
| 2 | Andar / saltar | 6 | Reservada (aún no se usa) |
| 3 | Mirar arriba | 4 | Al tocarla (mimo) |
| 4 | Descansar | 4 | De noche, despierta |
| 5 | Dormir | 4 | Dormida |

Las constantes están en `BirdSheet` (`PetSpeciesCatalog.kt`).

Una especie puede tener **más frames** por animación, para que se mueva con más fluidez: su hoja
es más ancha (tantas columnas como frames tenga su animación más larga) y sus números van en
`BirdSheet.frames(row, species)`. Cada ciclo dura lo mismo en todas las especies
(`BirdSheet.cycleSeconds`), así que con más frames cada uno dura menos. El test
`BirdSheetsTest` comprueba que cada hoja tiene dibujo en todos los frames que declara el código.

### Despegue y aterrizaje (opcionales)

Detrás de las 6 filas, una especie puede tener **despegue** (`BirdSheet.TAKEOFF`) y
**aterrizaje** (`BirdSheet.LANDING`). Son largas: cada una ocupa varias filas seguidas (el frame
*i* va en la fila base + *i* / columnas, ver `BirdSheet.cell`). En el juego:

- **Despegue**: se agacha y abre las alas sin moverse; desde el frame `LIFTOFF_FRAME` (cuando
  deja el suelo) ya se desplaza, y al acabar sigue con el aleteo normal.
- **Aterrizaje**: en el último tramo del vuelo baja al ras del suelo; al llegar se posa con la
  animación y no hace nada más (picotear, volver a volar) hasta terminarla.

Al montar la hoja, los frames con las patas en el suelo se colocan uno a uno con las patas en la
línea del suelo, y los del aire se llevan poco a poco hasta la postura de vuelo de la fila 0: el
despegue empieza y el aterrizaje acaba en la postura de reposo, y los dos enlazan con el vuelo
sin saltos.

## Estado del arte

- **Arrendajo** (`pet_blue_jay.png`): estilo nuevo, con hoja ampliada de **16 columnas × 10
  filas** (4096 × 2560): 12 frames de vuelo, 16 en cada una de las demás animaciones y 31 de
  despegue (filas 6-7) y de aterrizaje (filas 8-9). Se monta con el modo `frames` a partir de
  `tools/bird_sprites/arrendajo/` (una imagen transparente por animación, más `despegue.png` y
  `aterrizaje.png`). Su reacción al tocarlo (mirar arriba) dura 1,2 s, porque sube la cabeza y la
  vuelve a bajar.
- **Resto de especies**: estilo antiguo pasado a esta rejilla; los frames que no tenían se han
  rellenado con copias del más parecido (ver `OLD_TO_NEW` en `build_grid.py`). Hay que
  sustituirlos cuando exista su arte nuevo.

## Pájaro del modo cuidado (`pet_care_bird_1.png`)

En el modo cuidado se ve **siempre el mismo pájaro** (un pájaro azul), sea cual sea la especie
de la mascota. Su hoja sigue la rejilla de 8 × 6 casillas de 256 px y las mismas filas, pero con
sus propios frames (`BirdLayout.care` en `PetSpeciesCatalog.kt`, objeto `CareBird`):

| Fila | Animación | Frames | Del original |
|---|---|---|---|
| 0 | Volar | 8 | fila 0 |
| 1 | Reposo | 6 | fila 1 |
| 2 | Andar / saltar | 8 | fila 4 (saltitos) |
| 3 | Reacción al mimo | 6 | fila 2 (estira las alas), dura 0,9 s |
| 4 | Descansar | 3 | fila 3, frames 0-2 (sentado, despierto) |
| 5 | Dormir | 5 | fila 3, frames 3-7 (ojos cerrados) |

No tiene despegue ni aterrizaje, y se dibuja a tamaño de adulto aunque la mascota sea un
polluelo. La fila 5 del original (mirar alrededor) aún no se usa.

El original (`tools/bird_sprites/care/pet_care_bird_1_source.png`, 8 × 6 casillas de 192 px
sobre fondo gris opaco) se convierte con:

```bash
/usr/bin/python3 tools/bird_sprites/care_sheet.py \
    tools/bird_sprites/care/pet_care_bird_1_source.png \
    composeApp/src/commonMain/composeResources/drawable/pet_care_bird_1.png
```

El script quita el fondo gris y las sombras del suelo (lo gris unido al borde de cada casilla;
el blanco y el negro de los ojos se conservan), le quita el halo gris al borde de las plumas y
coloca cada animación con los pies en la línea del suelo, como en las demás hojas. Solo necesita
Pillow y numpy. `BirdSheetsTest` comprueba que la hoja tiene dibujo justo en los frames que
declara `BirdLayout.care`.

## Herramientas (`tools/bird_sprites/`)

```bash
# Hoja a partir de una imagen transparente por animación (rejilla de 4 columnas, frames en orden
# de lectura, mirando a la derecha): vuelo.png, reposo.png, andar.png, mirar_arriba.png,
# descansar.png y dormir.png. Conserva la posición de cada pájaro en su casilla, para que el
# movimiento entre frames (saltos, cabeza) se mantenga:
python3 tools/bird_sprites/build_grid.py frames tools/bird_sprites/arrendajo composeApp/src/commonMain/composeResources/drawable/pet_blue_jay.png

# Hoja de 8 columnas a partir de una referencia con el estilo nuevo (pájaros sobre fondo
# oscuro, una animación por fila en el orden de la tabla y mirando a la derecha):
python3 tools/bird_sprites/build_grid.py jay REFERENCIA.jpeg composeApp/src/commonMain/composeResources/drawable/pet_blue_jay.png

# Pasar una hoja antigua de 4 × 4 a esta rejilla:
python3 tools/bird_sprites/build_grid.py convert HOJA_4x4.png SALIDA.png
```

`extract_frames.py` recorta cada pájaro de la referencia y le quita el fondo (los huecos
oscuros dentro del pájaro, como el ojo, se conservan; los de entre las patas, no).
