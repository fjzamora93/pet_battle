# Mapas del modo defensa

Última actualización: 2026-11-25.

Cómo se convierte la imagen de un mapa en los datos que usa el juego, y qué hacer para añadir
uno nuevo.

## Qué necesita el juego de un mapa

Una imagen de mapa solo son píxeles de colores. El juego necesita dos datos más, que viven en
un `DefenseMapEntity` (`features/defense/domain/maps/`):

1. **La ruta** (`MapRoute`): una lista **ordenada** de puntos, de la entrada a la salida. Los
   enemigos la recorren a velocidad constante (`RoutePath`), segmento a segmento.
2. **La rejilla de colocación** (`placementGrid`): una cadena por fila de celdas; `#` = ahí se
   puede poner una mascota, `.` = no.

Ninguno de los dos se escribe a mano: los genera `tools/defense_maps/build_defense_map.py` a
partir de la imagen y de un pequeño JSON por mapa.

## Unidades

Las coordenadas del juego son **unidades de mapa**, no píxeles de la imagen. El campo `scale`
del JSON las relaciona: unidades = píxeles × `scale`. La imagen se estira para cubrir todo el
mapa. Así una imagen de 1024 px con `scale: 1.5` mide 1536 unidades, del mismo orden que los
demás mapas, y las mascotas (128 unidades) y los enemigos se ven del mismo tamaño en todos.

Las coordenadas del JSON (`guides`, `spawn`, `exit`) van en **píxeles de la imagen**, que es
lo que se ve al abrirla en un editor. El script aplica la escala.

## Cómo se traza la ruta

1. **Máscara del camino.** Se pasa la imagen a HSV y se marcan los píxeles con el color del
   camino (`path_hsv`: rangos de tono, saturación y brillo, con el tono de OpenCV de 0 a 179).
   Se limpia el ruido, se queda la mancha conectada más grande (el camino entero, puente
   incluido) y se suavizan los bordes.
2. **Línea central.** La máscara se adelgaza hasta una línea de 1 píxel por el centro del
   camino (algoritmo de Zhang-Suen). Es un "esqueleto" con todos los tramos y sus cruces.
3. **Puntos guía.** El color no puede decidir el **orden** del recorrido: en un cruce o en un
   anillo hay varias salidas posibles. Por eso cada mapa lleva unas pocas guías puestas a mano
   (`guides`), en el orden en que se recorre. Cada guía se pega al punto de la línea central
   más cercano y, entre cada dos guías seguidas, se busca el **camino más corto por la línea
   central** (Dijkstra). Así basta con pocas guías:
   - en un tramo sin cruces no hace falta ninguna (el camino más corto ya va por él);
   - en una bifurcación, una guía en la rama que toca;
   - en un anillo, dos o tres guías alrededor, que marcan en qué sentido se rodea;
   - para ir y volver por el mismo tramo (un ramal en piruleta), se repite la guía del cruce.
4. **Polilínea final.** Se unen los trozos, se quitan los puntos que sobran
   (Ramer-Douglas-Peucker, tolerancia `simplify_px`) y se añaden la aparición (`spawn`) y la
   salida (`exit`), **fuera de la imagen**, para que los enemigos entren y salgan de pantalla
   andando.

## Cómo se decide dónde se puede colocar

Para cada celda (`cell_size` unidades de lado) se mira la imagen. Es colocable si no toca:

- el camino, ampliado `path_margin_px` píxeles (para que nadie se ponga encima del borde);
- agua (`water_hsv`);
- rocas (`rock_hsv`; solo manchas grandes, para que las flores claras no cuenten).

Hierba, arbustos y árboles sí valen. El juego exige además que todo el círculo de la huella
de la mascota caiga en celdas `#` (`DefenseMapEntity.canPlace`).

## Añadir un mapa nuevo

1. Copiar la imagen a `composeApp/src/commonMain/composeResources/drawable/map_defense_N.png`.
2. Copiar `tools/defense_maps/map_defense_1.json` como `map_defense_N.json` y cambiar:
   - `image`, `kotlin_out`, `debug_out`, `kotlin_val`, `route_name` y `description`;
   - `scale`, para que el lado mayor quede en unas 1500 unidades;
   - los rangos de color, si el camino, el agua o las rocas son de otro color (se pueden
     muestrear con cualquier editor de imágenes o con OpenCV);
   - `spawn` y `exit`, justo fuera de la imagen frente a la entrada y la salida;
   - `guides`: la primera en la entrada, la última en la salida, y las intermedias que
     hagan falta según el apartado anterior.
3. Ejecutar `python3 tools/defense_maps/build_defense_map.py tools/defense_maps/map_defense_N.json`
   (necesita numpy, scipy y OpenCV).
4. Abrir la imagen de comprobación (`debug_out`): la ruta va en magenta con flechas blancas
   que marcan el sentido, las guías numeradas en amarillo y las celdas colocables en verde.
   Si algo va por donde no debe, se ajustan las guías o los colores y se repite.
5. Usar el `DefenseMapEntity` generado y su imagen en `DefenseScreen`.

Si el camino tiene **varias entradas**, hoy el script genera una sola ruta: habría que
ampliarlo para aceptar una lista de guías por ruta (el juego ya admite varias rutas).

## Mapas

| Imagen | Datos | Ruta |
|---|---|---|
| `map_defense_1.png` (1024 px, `scale` 1,5) | `ForestMap.kt` (`forestMap`) | 1 camino de ~6165 unidades, con dos anillos y un puente |
