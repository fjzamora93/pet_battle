# Petirrojo europeo — sprites 3D (versión 3)

Hojas PNG transparentes de **4 columnas de celdas de 224 × 224 px**, vista lateral hacia la
derecha, con el mismo encuadre en todas (no se recorta cada pájaro por su silueta). Frames en
orden de lectura desde el 0.

| Acción | Archivo | Frames | Duración | Reproducción | Fila en `pet_red_robin.png` |
|---|---|---:|---:|---|---|
| Volar | `vuelo.png` | 12 | 556 ms | Bucle | 0 volar |
| Posarse en una rama | `posarse-rama.png` | 16 | 1.000 ms | Bucle | 1 reposo |
| Caminar | `caminar.png` | 16 | 667 ms | Bucle | 2 andar |
| Saltar | `saltar.png` | 16 | 1.067 ms | Una vez | 3 reacción al mimo |
| Dormir | `dormir.png` | 16 | 2.000 ms | Bucle | 4 descansar y 5 dormir |
| Despegar | `despegar.png` | 16 | 1.240 ms (8 × 95 en tierra + 8 × 60 en el aire) | Una vez | 6 despegue |
| Aterrizar | `aterrizar.png` | 16 | 1.336 ms (8 × 60 en el aire + 8 × 107 en tierra) | Una vez | 7 aterrizaje |
| Comer | `comer.png` | 16 | 1.000 ms | Bucle | 8 comer |

La hoja del juego se monta con:

```bash
python3 tools/bird_sprites/build_grid.py atlas tools/bird_sprites/petirrojo composeApp/src/commonMain/composeResources/drawable/pet_red_robin.png
```

Las duraciones por frame están en `BirdSheet` (disposición `BirdLayout.robin`).

## Limitaciones (del autor de los sprites)

- El despegue mezcla brevemente dos modelos en los frames 8–10 (contados desde 1): se suaviza
  el cambio, pero puede verse una silueta doble.
- Al posarse en la rama los dedos se curvan por debajo de la línea del suelo del resto de
  animaciones (agarran una rama que no está dibujada): en el suelo quedan algo hundidos.
- Para comer inclina cabeza y cuerpo; el pico no se abre.
