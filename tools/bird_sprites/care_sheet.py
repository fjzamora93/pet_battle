#!/usr/bin/env python3
"""Monta la hoja del pájaro del modo cuidado (siempre el mismo, sea cual sea la mascota) a partir
de su hoja original: 8 columnas x 6 filas de casillas de 192 px sobre un fondo gris opaco,
mirando a la izquierda.

Filas del original:
    0 volar (8)   1 reposo (6)   2 estirar las alas (6)   3 sentarse y dormirse (8)
    4 saltar (8)   5 mirar alrededor (6, de momento no se usa)

Se quita el fondo gris (y las sombras del suelo, también grises) y se coloca en la rejilla de
las demás hojas (casillas de 256 px, ver BirdSheet en PetSpeciesCatalog.kt), con la disposición
BirdLayout.care:

    0 volar (8)   1 reposo (6)   2 saltar (8)   3 reacción al mimo: estirar las alas (6)
    4 descansar despierto: sentado (3)   5 dormir: con los ojos cerrados (5)

Uso:
    /usr/bin/python3 tools/bird_sprites/care_sheet.py \\
        tools/bird_sprites/care/pet_care_bird_1_source.png \\
        composeApp/src/commonMain/composeResources/drawable/pet_care_bird_1.png
"""
import sys

import numpy as np
from PIL import Image

# Colocación en la casilla, igual que en build_grid.py (no se importa de allí porque necesita
# scipy y este script no): pies casi en el borde inferior; en vuelo, algo por encima del centro.
FRAME = 256
FEET_Y = 252
FEET_X = 124
FLY_CENTER = (128, 115)
STANDING_WIDTH = 185  # ancho de un pájaro de pie en una casilla de 256

SOURCE_COLUMNS = 8
SOURCE_ROWS = 6

# Fondo: gris casi neutro. Por debajo de esta saturación (máx - mín de los canales) un píxel
# puede ser fondo; solo lo es si está unido al borde de su casilla (así el blanco y el negro de
# los ojos, rodeados de plumas, se conservan).
BG_SATURATION = 16
# Borde suave: de esta saturación hacia arriba ya es pájaro del todo.
SOLID_SATURATION = 46

# (fila del original, columnas) de cada animación, en el orden de las filas de la hoja final.
ANIMATIONS = [
    (0, range(8)),     # volar
    (1, range(6)),     # reposo
    (4, range(8)),     # saltar
    (2, range(6)),     # reacción al mimo
    (3, range(0, 3)),  # descansar despierto (sentado, ojos abiertos)
    (3, range(3, 8)),  # dormir (ojos cerrados)
]
FLY = 0
IDLE = 1


def feet_x(im):
    """Posición horizontal media de las patas (última franja con píxeles opacos)."""
    a = np.asarray(im.split()[3]) > 128
    ys = np.where(a.any(axis=1))[0]
    band = a[max(ys[-1] - max(3, im.height // 14), 0): ys[-1] + 1]
    xs = np.where(band.any(axis=0))[0]
    return float(xs.mean())


def connected_to_border(mask):
    """Píxeles de [mask] unidos (4-vecindad) al borde de la imagen."""
    seed = np.zeros_like(mask)
    seed[0, :], seed[-1, :], seed[:, 0], seed[:, -1] = mask[0, :], mask[-1, :], mask[:, 0], mask[:, -1]
    while True:
        grown = seed.copy()
        grown[1:, :] |= seed[:-1, :]
        grown[:-1, :] |= seed[1:, :]
        grown[:, 1:] |= seed[:, :-1]
        grown[:, :-1] |= seed[:, 1:]
        grown &= mask
        if (grown == seed).all():
            return seed
        seed = grown


def cut_out(cell):
    """Casilla RGB del original -> RGBA sin el fondo gris (con el borde suavizado y sin el halo gris)."""
    rgb = np.asarray(cell.convert("RGB")).astype(np.float32)
    saturation = rgb.max(axis=2) - rgb.min(axis=2)
    background = connected_to_border(saturation < BG_SATURATION)
    gray = np.median(rgb[background], axis=0)
    # Alrededor del fondo (la franja de plumas mezcladas con el gris), opacidad según la saturación.
    near = background.copy()
    for _ in range(3):
        near[1:, :] |= near[:-1, :]
        near[:-1, :] |= near[1:, :]
        near[:, 1:] |= near[:, :-1]
        near[:, :-1] |= near[:, 1:]
    soft = np.clip((saturation - BG_SATURATION) / (SOLID_SATURATION - BG_SATURATION), 0, 1)
    alpha = np.where(background, 0.0, np.where(near, soft, 1.0))
    # Quita el gris del color de esa franja: color = (mezcla - (1 - a) * gris) / a.
    a = np.maximum(alpha, 1e-3)[..., None]
    clean = np.where(near[..., None] & ~background[..., None], (rgb - (1 - a) * gray) / a, rgb)
    rgba = np.dstack([np.clip(clean, 0, 255), alpha * 255]).astype(np.uint8)
    im = Image.fromarray(rgba)  # 4 canales uint8: RGBA
    # Restos sueltos (motas de la sombra): fuera todo lo que quede por debajo de un umbral bajo.
    al = np.asarray(im.split()[3]).copy()
    al[al < 24] = 0
    im.putalpha(Image.fromarray(al))
    return im


def build(src, out):
    img = Image.open(src).convert("RGB")
    cell = img.width // SOURCE_COLUMNS
    if img.height // cell != SOURCE_ROWS:
        raise SystemExit(f"{src}: se esperaba una rejilla de {SOURCE_COLUMNS}x{SOURCE_ROWS} y mide {img.size}")

    def source_frame(r, c):
        return cut_out(img.crop((c * cell, r * cell, (c + 1) * cell, (r + 1) * cell)))

    rows = [[source_frame(r, c) for c in cols] for r, cols in ANIMATIONS]
    # Una sola escala para todos: de pie mide como los pájaros de las demás hojas.
    scale = STANDING_WIDTH / float(np.median([f.getbbox()[2] - f.getbbox()[0] for f in rows[IDLE]]))
    size = round(cell * scale)
    if size > FRAME:
        raise SystemExit(f"la casilla escalada ({size} px) no cabe en {FRAME} px")
    columns = max(len(r) for r in rows)
    sheet = Image.new("RGBA", (FRAME * columns, FRAME * len(rows)), (0, 0, 0, 0))
    for r, frames in enumerate(rows):
        frames = [f.resize((size, size), Image.LANCZOS) for f in frames]
        boxes = [f.getbbox() for f in frames]
        union = (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))
        # Toda la animación se mueve a la vez (se conservan los saltos y el balanceo de cada frame).
        if r == FLY:
            dx = FLY_CENTER[0] - (union[0] + union[2]) / 2
            dy = FLY_CENTER[1] - (union[1] + union[3]) / 2
        else:
            dx = FEET_X - float(np.median([feet_x(f.crop(b)) + b[0] for f, b in zip(frames, boxes)]))
            dy = FEET_Y - union[3]
        dx = min(max(dx, -union[0]), FRAME - union[2])
        dy = min(max(dy, -union[1]), FRAME - union[3])
        for c, f in enumerate(frames):
            sheet.alpha_composite(f, (c * FRAME + round(dx), r * FRAME + round(dy)))
    sheet.save(out, optimize=True)
    print(f"{out}: {sheet.width}x{sheet.height}, frames por fila {[len(r) for r in rows]}, escala {scale:.3f}")


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2])
