# Bioma bosque: foto con troncos que tapan

Material original de la escena del bosque (foto de 736 × 489 px) y cómo lo usa el juego.

| Original | Recurso del juego | Raíz (y) |
|---|---|---:|
| `bosque-fondo.png` | `biome_woodland.png` (fondo fijo, sin parallax) | — |
| `tronco-centro.png` | `biome_woodland_trunk_center.png` | 335 |
| `tronco-centro-derecha.png` | `biome_woodland_trunk_center_right.png` | 357 |
| `tronco-izquierda-medio.png` | `biome_woodland_trunk_left_mid.png` | 364 |
| `tronco-izquierda-cerca.png` | `biome_woodland_trunk_left_near.png` | 380 |
| `tronco-derecha-cerca.png` | `biome_woodland_trunk_right_near.png` | 406 |

Los datos están en `bosque-capas.json` y, en el juego, en `woodlandScenery` (`BiomeCatalog.kt`).

En el juego (`BiomeScene`):

- **Suelo**: las mascotas se mueven entre el borde de delante y un borde del fondo que sigue el
  terreno (`groundEdge`): el camino se aleja hasta y = 326 en el centro; a los lados el suelo
  acaba antes, contra los arbustos y las raíces.
- **Perspectiva**: el tamaño depende de la altura del suelo que pisan, de 0,22 de la casilla en
  y = 330 a 0,6 en y = 480 (relativo al suelo de delante, donde miden como en los demás biomas).
- **Troncos**: mascotas, comida y troncos se dibujan ordenados por la altura de su punto de apoyo
  en el suelo; un tronco cuya raíz queda por delante se pinta encima y tapa a la mascota (también
  en vuelo: cuenta su suelo, no su altura). Los recortes llevan el mismo filtro y degradado del
  momento del día que el fondo, para que no se noten.

Los contornos de los recortes son aproximados, y las ramas, hojas y demás árboles no están
separados: no tapan.
