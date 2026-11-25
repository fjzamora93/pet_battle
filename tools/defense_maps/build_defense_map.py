#!/usr/bin/env python3
"""
Genera los datos de un mapa del modo defensa a partir de su imagen.

Uso:
    python3 tools/defense_maps/build_defense_map.py tools/defense_maps/map_defense_1.json

Una imagen de mapa solo son píxeles de colores; el juego necesita dos cosas que la imagen no
dice por sí sola:

1. **La ruta de los enemigos**: una polilínea ordenada (lista de puntos) desde la entrada hasta
   la salida. Los enemigos la recorren a velocidad constante (RoutePath en Kotlin).
2. **Dónde se pueden colocar las mascotas**: una rejilla de celdas ('#' = se puede).

Cómo se saca la ruta (pasos de este script):

  a) Máscara del camino: se pasa la imagen a HSV y se marcan los píxeles cuyo color cae en el
     rango del camino de tierra (`path_hsv` en el JSON). Se limpia con apertura/cierre
     morfológico, se queda la mancha conectada más grande (el camino entero, puente incluido)
     y se suavizan los bordes.
  b) Esqueleto: se adelgaza la máscara hasta una línea de 1 píxel por el centro del camino
     (algoritmo de Zhang-Suen). Es la "línea central" de todos los tramos, con sus cruces.
  c) Puntos guía (`guides` en el JSON, puestos a mano mirando la imagen): marcan el ORDEN en
     que se recorren los tramos. El color no puede decidir, en un cruce o en un anillo, por
     dónde sigue el enemigo; los puntos guía sí. Cada guía se pega al píxel de esqueleto más
     cercano y entre cada par de guías consecutivas se busca el camino más corto POR EL
     ESQUELETO (Dijkstra). Así basta con unas pocas guías: en un tramo sin cruces no hace
     falta ninguna, y en un anillo se ponen un par para decir en qué sentido se rodea.
  d) Se unen los trozos, se simplifica la polilínea (Ramer-Douglas-Peucker, `simplify_px`) y se
     añaden la aparición y la salida fuera de la imagen (`spawn` y `exit`), para que los
     enemigos entren y salgan de pantalla caminando.
  e) Todo se multiplica por `scale`: las coordenadas del juego son "unidades de mapa" y la
     imagen se estira para cubrir width x height. Con scale > 1 una imagen pequeña tiene el
     mismo tamaño lógico que los demás mapas (los sprites miden lo mismo en todos).

Cómo se saca la rejilla de colocación:

  Por cada celda de `cell_size` unidades se mira la imagen: se puede colocar ('#') si ningún
  píxel de la celda es camino (ampliado `path_margin_px`), agua (`water_hsv`) ni roca
  (`rock_hsv`). Árboles y arbustos sí valen: son pájaros.

Salidas:
  - El fichero Kotlin `kotlin_out` con un `DefenseMapEntity` (GENERADO: no se edita a mano).
  - Una imagen de comprobación `debug_out` con la ruta numerada y la rejilla, para revisar a
    ojo que la ruta va por donde debe antes de jugar.

Para un mapa nuevo: copiar el JSON de otro mapa, cambiar imagen y nombres, ajustar los rangos
de color si el camino es de otro color, poner las guías siguiendo el recorrido y ejecutar.
"""
import json
import math
import sys
from pathlib import Path

import cv2
import numpy as np
from scipy.sparse import coo_matrix
from scipy.sparse.csgraph import dijkstra

ROOT = Path(__file__).resolve().parents[2]


def hsv_mask(hsv, ranges):
    """Píxeles dentro de alguno de los rangos [[h0,s0,v0],[h1,s1,v1]] (OpenCV: H 0-179)."""
    mask = np.zeros(hsv.shape[:2], np.uint8)
    for lo, hi in ranges:
        mask |= cv2.inRange(hsv, np.array(lo, np.uint8), np.array(hi, np.uint8))
    return mask


def path_mask(hsv, cfg):
    """Máscara limpia del camino: color, limpieza, mancha mayor y bordes suaves."""
    m = hsv_mask(hsv, cfg["path_hsv"])
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN, np.ones((5, 5), np.uint8))
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
    n, labels, stats, _ = cv2.connectedComponentsWithStats(m)
    biggest = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    m = ((labels == biggest) * 255).astype(np.uint8)
    # Tapa agujeros (tablas del puente, piedrecitas) y redondea los bordes: esqueleto más limpio.
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, np.ones((21, 21), np.uint8))
    m = cv2.GaussianBlur(m, (15, 15), 0)
    return (m > 127).astype(np.uint8)


def thin(mask):
    """Esqueleto de 1 píxel (Zhang-Suen, vectorizado con numpy)."""
    img = mask.copy()
    while True:
        changed = False
        for step in (0, 1):
            p = np.pad(img, 1)
            p2, p3, p4 = p[:-2, 1:-1], p[:-2, 2:], p[1:-1, 2:]
            p5, p6, p7 = p[2:, 2:], p[2:, 1:-1], p[2:, :-2]
            p8, p9 = p[1:-1, :-2], p[:-2, :-2]
            neighbours = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9
            ring = [p2, p3, p4, p5, p6, p7, p8, p9, p2]
            transitions = sum(((ring[i] == 0) & (ring[i + 1] == 1)).astype(np.uint8) for i in range(8))
            if step == 0:
                cond = (p2 * p4 * p6 == 0) & (p4 * p6 * p8 == 0)
            else:
                cond = (p2 * p4 * p8 == 0) & (p2 * p6 * p8 == 0)
            remove = (img == 1) & (neighbours >= 2) & (neighbours <= 6) & (transitions == 1) & cond
            if remove.any():
                img[remove] = 0
                changed = True
        if not changed:
            return img


def skeleton_graph(skel):
    """Grafo de los píxeles del esqueleto (vecindad de 8, peso = distancia)."""
    ys, xs = np.nonzero(skel)
    index = -np.ones(skel.shape, np.int64)
    index[ys, xs] = np.arange(len(xs))
    rows, cols, weights = [], [], []
    h, w = skel.shape
    for dy, dx in ((0, 1), (1, -1), (1, 0), (1, 1)):
        ny, nx = ys + dy, xs + dx
        ok = (ny >= 0) & (ny < h) & (nx >= 0) & (nx < w)
        a = np.nonzero(ok)[0]
        b = index[ny[ok], nx[ok]]
        linked = b >= 0
        a, b = a[linked], b[linked]
        dist = math.hypot(dx, dy)
        rows += [a, b]
        cols += [b, a]
        weights += [np.full(len(a), dist)] * 2
    n = len(xs)
    graph = coo_matrix((np.concatenate(weights), (np.concatenate(rows), np.concatenate(cols))), shape=(n, n)).tocsr()
    return graph, xs, ys


def trace_route(skel, guides):
    """Une las guías por el camino más corto sobre el esqueleto; devuelve los píxeles en orden."""
    graph, xs, ys = skeleton_graph(skel)
    nodes = []
    for gx, gy in guides:
        i = int(np.argmin((xs - gx) ** 2 + (ys - gy) ** 2))
        snap = math.hypot(xs[i] - gx, ys[i] - gy)
        if snap > 40:
            sys.exit(f"La guía ({gx}, {gy}) está a {snap:.0f} px del camino: ¿está fuera de él?")
        nodes.append(i)
    route = []
    for a, b in zip(nodes, nodes[1:]):
        dist, pred = dijkstra(graph, indices=a, return_predecessors=True)
        if math.isinf(dist[b]):
            sys.exit(f"No hay camino entre las guías {(xs[a], ys[a])} y {(xs[b], ys[b])}")
        piece = [b]
        while piece[-1] != a:
            piece.append(pred[piece[-1]])
        piece.reverse()
        route += piece if not route else piece[1:]
    return [(float(xs[i]), float(ys[i])) for i in route]


def simplify(points, eps):
    """Ramer-Douglas-Peucker: quita puntos que no se apartan más de [eps] de la recta."""
    if len(points) < 3:
        return points
    (x0, y0), (x1, y1) = points[0], points[-1]
    length = math.hypot(x1 - x0, y1 - y0) or 1e-9
    worst, at = 0.0, 0
    for i in range(1, len(points) - 1):
        px, py = points[i]
        d = abs((x1 - x0) * (y0 - py) - (x0 - px) * (y1 - y0)) / length
        if d > worst:
            worst, at = d, i
    if worst <= eps:
        return [points[0], points[-1]]
    return simplify(points[: at + 1], eps)[:-1] + simplify(points[at:], eps)


def placement_grid(img_hsv, path, cfg, size_units, scale):
    """Una cadena por fila de celdas: '#' si la celda está libre de camino, agua y rocas."""
    margin = cfg.get("path_margin_px", 6)
    blocked = cv2.dilate(path, np.ones((2 * margin + 1, 2 * margin + 1), np.uint8)) > 0
    blocked |= hsv_mask(img_hsv, cfg["water_hsv"]) > 0
    # Las rocas son manchas grandes: la apertura quita flores y brillos sueltos de color parecido.
    rocks = cv2.morphologyEx(hsv_mask(img_hsv, cfg["rock_hsv"]), cv2.MORPH_OPEN, np.ones((9, 9), np.uint8))
    blocked |= rocks > 0
    cell = cfg["cell_size"]
    cols, rows = math.ceil(size_units[0] / cell), math.ceil(size_units[1] / cell)
    grid = []
    for r in range(rows):
        line = ""
        for c in range(cols):
            # Celda en unidades de mapa -> píxeles de imagen.
            x0, y0 = int(c * cell / scale), int(r * cell / scale)
            x1, y1 = math.ceil((c + 1) * cell / scale), math.ceil((r + 1) * cell / scale)
            line += "." if blocked[y0:y1, x0:x1].any() else "#"
        grid.append(line)
    return grid


def kotlin(cfg, size, route, grid):
    pts = ", ".join(f"MapPoint({x:.1f}, {y:.1f})" for x, y in route)
    rows = "\n".join(f'        "{row}",' for row in grid)
    return f"""// GENERADO por tools/defense_maps/build_defense_map.py a partir de {cfg['config_name']}: no editar a mano.
package com.petbattle.features.defense.domain.maps

import com.petbattle.features.defense.domain.entities.DefenseMapEntity
import com.petbattle.features.defense.domain.entities.MapPoint
import com.petbattle.features.defense.domain.entities.MapRoute

/** {cfg['description']} */
val {cfg['kotlin_val']} = DefenseMapEntity(
    width = {size[0]},
    height = {size[1]},
    cellSize = {cfg['cell_size']},
    routes = listOf(
        MapRoute("{cfg['route_name']}", listOf({pts})),
    ),
    placementGrid = listOf(
{rows}
    ),
)
"""


def debug_image(img, route, grid, guides, cfg, scale, out):
    """Imagen de comprobación: celdas colocables en verde, ruta con flechas y guías numeradas."""
    vis = img.copy()
    overlay = vis.copy()
    cell = cfg["cell_size"] / scale
    for r, row in enumerate(grid):
        for c, ch in enumerate(row):
            if ch == "#":
                p0 = (int(c * cell), int(r * cell))
                p1 = (int((c + 1) * cell) - 1, int((r + 1) * cell) - 1)
                cv2.rectangle(overlay, p0, p1, (60, 255, 60), -1)
    vis = cv2.addWeighted(overlay, 0.25, vis, 0.75, 0)
    pts = [(int(x / scale), int(y / scale)) for x, y in route]
    for a, b in zip(pts, pts[1:]):
        cv2.line(vis, a, b, (255, 0, 255), 3, cv2.LINE_AA)
    # Una flecha cada cierta distancia para ver el sentido.
    walked, next_arrow = 0.0, 60.0
    for a, b in zip(pts, pts[1:]):
        seg = math.dist(a, b)
        while seg > 0 and walked + seg >= next_arrow:
            t = (next_arrow - walked) / seg
            p = (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
            d = ((b[0] - a[0]) / seg * 14, (b[1] - a[1]) / seg * 14)
            cv2.arrowedLine(vis, (int(p[0] - d[0]), int(p[1] - d[1])), (int(p[0] + d[0]), int(p[1] + d[1])),
                            (255, 255, 255), 3, cv2.LINE_AA, tipLength=0.6)
            next_arrow += 120
        walked += seg
    for i, (gx, gy) in enumerate(guides, 1):
        cv2.circle(vis, (gx, gy), 13, (0, 200, 255), -1)
        cv2.putText(vis, str(i), (gx - 8 if i < 10 else gx - 12, gy + 6), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 0, 0), 2)
    cv2.imwrite(str(out), vis, [cv2.IMWRITE_JPEG_QUALITY, 85])


def main(config_path):
    config_path = Path(config_path).resolve()
    cfg = json.loads(config_path.read_text())
    cfg["config_name"] = config_path.relative_to(ROOT).as_posix() if config_path.resolve().is_relative_to(ROOT) else config_path.name
    img = cv2.imread(str(ROOT / cfg["image"]))
    if img is None:
        sys.exit(f"No se puede leer {cfg['image']}")
    scale = cfg.get("scale", 1.0)
    size = (round(img.shape[1] * scale), round(img.shape[0] * scale))
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)

    path = path_mask(hsv, cfg)
    skel = thin(path)
    guides = [tuple(g) for g in cfg["guides"]]
    pixels = trace_route(skel, guides)
    line = simplify(pixels, cfg.get("simplify_px", 2.0))
    route = [tuple(cfg["spawn"])] + line + [tuple(cfg["exit"])]
    route_units = [(x * scale, y * scale) for x, y in route]
    length = sum(math.dist(a, b) for a, b in zip(route_units, route_units[1:]))

    grid = placement_grid(hsv, path, cfg, size, scale)
    out = ROOT / cfg["kotlin_out"]
    out.write_text(kotlin(cfg, size, route_units, grid))
    debug_image(img, route_units, grid, guides, cfg, scale, ROOT / cfg["debug_out"])
    print(f"{out.relative_to(ROOT)}: {size[0]}x{size[1]} unidades, ruta de {len(route_units)} puntos "
          f"y {length:.0f} unidades de largo, rejilla {len(grid[0])}x{len(grid)}")
    print(f"Comprobación visual: {cfg['debug_out']}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
