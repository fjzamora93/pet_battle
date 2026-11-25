# Reglas del sistema de combate

Este documento recoge las reglas del **combate** **tal y como están implementadas** en el
código. Cada vez que cambie una regla o un valor de balance, hay que actualizarlo aquí (ver
CLAUDE.md). Las reglas de cuidado de la mascota (necesidades, cría, rareza, tienda, ciclo del
día) están en [reglas-de-cuidado.md](reglas-de-cuidado.md). Lo que comparten los dos
sistemas (cómo los cuidados afectan a los stats de combate y cómo el combate desgasta las
necesidades) está en ambos documentos.

Última actualización: 2026-11-25.

Fuentes en el código:
- Combate (modo defensa): `features/defense/domain/DefenseRules.kt` (`DefenseBalance`),
  `features/defense/domain/entities/EnemyEntity.kt`, `features/defense/domain/CombatRules.kt`,
  `features/defense/presentation/game/DefenseGame.kt`.
- Habilidades y niveles: `shared_features/pet_skills/domain/PetSkillsCatalog.kt`,
  `shared_features/pet_skills/domain/PetProgression.kt`.
- Stats de combate: `shared_features/pet_combat/domain/PetCombatStatsRules.kt`
  (`PetCombatBalance`), `PetCombatStats.kt`.
- Recompensas: `shared_features/pet_status/domain/GameRules.kt`.

---

## 1. Desarrollo de una partida (modo defensa)

1. **Preparación.** Se colocan en zonas de hierba las mascotas **adultas**, de **cualquier
   bioma** (los polluelos no combaten), cada una una sola vez. Tocar un pájaro colocado lo recoge
   (se ve semitransparente) y el siguiente toque en hierba lo mueve. No aparecen enemigos.
2. **"Empezar".** Se activa con al menos un pájaro colocado. Al pulsarlo desaparece la
   interfaz de colocación y empieza el combate; ya no se puede mover a nadie.
3. **Combate.** Aparece un enemigo cada **2,2 s**, alternando entre los caminos del mapa.
   Cada enemigo que llega a la salida quita **1 vida** de **10**. Con 0 vidas se pierde.
   No hay victoria: la partida dura lo que se aguante.
4. **Reintentar** vuelve a la preparación con los pájaros donde estaban.

El mapa es siempre el mismo (*jungle camp*), sea cual sea el bioma.

## 2. Enemigos

La dificultad depende solo del **tiempo de combate**:

| Qué | Regla |
|---|---|
| Nivel | Empiezan en nivel 1 y suben **+1 cada 20 s** (solo afecta a los que nacen desde ese momento). |
| Vida | **1,2 × nivel** (nivel 1 = 1,2; nivel 5 = 6). |
| Velocidad | Base 36 px/s, **+20% cada 10 s** (lineal), para todos los del mapa a la vez. |
| Tipo | **Gusanos** hasta el nivel 7. Desde el **nivel 8** (a los 140 s) dejan de salir gusanos y solo salen **mariposas**. |

Gusanos y mariposas siguen las mismas reglas de vida, velocidad y recompensa; la mariposa
solo cambia de aspecto: vuela sobre el camino y, al morir, cae y se posa en la hierba.

Los enemigos no atacan a las mascotas: solo avanzan hacia la salida.

## 3. Stats de combate

Cada mascota tiene cuatro **stats de combate**. No se guardan: se calculan en cada momento.
Daño, velocidad y confianza tienen un **total** (el máximo, según el nivel) y un valor
**actual**, que es el total menos lo que restan **tres necesidades** según lo que les falte
(todas al 100% = rinde el total). El **rango** es fijo por especie.

| Stat de combate | Total | Necesidades que lo suben o bajan | Por cada punto que le falta a una de ellas |
|---|---|---|---|
| **Daño** (por golpe) | Nivel 0 → 1; nivel N → **1,2 × N** | Hambre (saciedad), plumaje, pico y garras | **−0,33 %** del daño total (−0,004 × N de daño; nivel 0: −0,0033) |
| **Velocidad** (ataques/s) | **1 / 0,7 s ≈ 1,43** (no depende del nivel) | Descanso (energía), digestión, clima | Descanso: **−0,5 %** (−0,0071 ataques/s). Digestión y clima: **−0,33 %** (−0,0048 ataques/s). Sin corte: con descanso a 0, la **mitad** |
| **Confianza** (% de acierto) | **100** | Diversión, social, higiene | **−0,33 puntos** de acierto |
| **Rango** (alcance, px de mapa) | Fijo por especie, según su tamaño | — (no depende de las necesidades) | — |

**Cómo se calcula (daño y confianza):** actual = total × (necesidad 1 + necesidad 2 +
necesidad 3) / 300. Las tres pesan lo mismo (un tercio cada una) y el efecto es **lineal**:
cada punto que falta quita **1/3 de punto porcentual** del total, sea cual sea la necesidad
y su nivel.

**Cómo se calcula (velocidad):** actual = total × (1 − 0,5 × lo que falta de descanso −
⅓ × lo que falta de digestión − ⅓ × lo que falta de clima), con lo que falta como fracción
(0 = llena, 1 = a cero) y sin bajar de 0. El descanso pesa más: cada punto que falta quita
**0,5 %**; digestión y clima, **0,33 %**. Con descanso a 0 (y lo demás lleno) ataca a la
**mitad**; no hay corte.
- Hambre a 70 (faltan 30) y lo demás lleno → daño al **90 %**. Con nivel 5 (total 6):
  6 × 270 / 300 = **5,4**.
- Las tres necesidades del daño (o de la confianza) a 50 → al **50 %**. Las tres de la
  velocidad a 50 → al **41,7 %** (1 − 0,25 − 0,167 − 0,167).
- Una necesidad entera a 0 (faltan 100) → el stat pierde **un tercio** (el descanso,
  **la mitad** de la velocidad).
- Descanso a 50 y digestión a 20 → velocidad al 1 − 0,25 − 0,267 = **48,3 %**.
- Social solo vale 0, 50 o 100: estar sola quita **33,3 puntos** de confianza y convivir
  solo con otras especies, **16,7**.

### Rango por especie (las grandes llegan más lejos)
| Especie | Tamaño aprox. | Rango |
|---|---|---|
| Cuervo | 45 cm | 380 |
| Urraca | 45 cm | 370 |
| Cernícalo | 34 cm | 350 |
| Arrendajo | 34 cm | 340 |
| Paloma blanca | 30 cm | 330 |
| Abejaruco | 28 cm | 310 |
| Cardenal | 22 cm | 290 |
| Zorzal | 20 cm | 285 |
| Chucao | 19 cm | 280 |
| Ampelis | 17 cm | 270 |
| Petirrojo | 14 cm | 260 |
| Pinzón | 14 cm | 255 |
| Carbonero | 12 cm | 245 |
| Colibrí | 9 cm | 230 |

- Con el daño total, una mascota de nivel N mata de un golpe a enemigos de nivel ≤ N.
- La confianza **es** la probabilidad de acertar cada disparo.
- Las mascotas disparan solas al enemigo **más adelantado** dentro de **su rango**.

En el juego, el panel de la mascota tiene una pestaña **Combate** con daño, velocidad y
confianza como "actual / total", y el rango (con una barra que lo compara con el de más
alcance). Su botón **i** explica cómo se calcula cada stat.

### Habilidades por elemento (ataque básico, desde el nivel 0)
| Elemento | Habilidad | Efecto |
|---|---|---|
| Fuego | Llamarada | Quema: 120% del daño repartido en 3 s (las quemaduras se acumulan). |
| Agua | Marea | Golpe normal y ralentiza a la mitad durante 2 s (no se acumula: gana el más fuerte). |
| Aire | Ráfaga | Golpe normal y empuja 30 px hacia atrás por su camino. |
| Tierra | Roca | Golpe normal, sin efectos. |

Las tablas de habilidades por tipo y por especie+tipo existen pero están vacías.

## 4. Cuidados y combate (compartido con reglas-de-cuidado.md)

**Cómo afectan los cuidados al combate:**
- Cada stat (salvo el rango) rinde su total por la media de sus tres necesidades (tabla de
  la sección 3): **daño** ← hambre, plumaje, pico y garras; **velocidad** ← descanso,
  digestión, clima; **confianza** ← diversión, social, higiene.
- **Cada punto que falta** en cualquiera de esas necesidades quita **1/3 de punto
  porcentual** del total del stat (lineal: 30 puntos de hambre que faltan = −10 % de daño).
  Excepción: cada punto de **descanso** quita **0,5 %** de velocidad.
- **Sin energía no se agota**: sigue atacando, a la mitad de velocidad (si digestión y clima
  están llenos).
- Una mascota **sola en su bioma** tiene social 0 y su confianza no pasa de ~67 aunque esté
  perfecta; con otra de su especie en el bioma, social vale 100 (con solo otras especies, 50).
- El **clima** vale siempre 100 (pendiente de reglas).

**Cómo afecta el combate a los cuidados:** **cada disparo** gasta **1 de descanso**, resta
**0,5** de higiene y de plumaje, y afila **+0,5** el pico y las garras.

Cómo sube y baja cada necesidad fuera del combate está en
[reglas-de-cuidado.md](reglas-de-cuidado.md).

## 5. Recompensas y progresión

- Cada enemigo derrotado da **1 XP** a la mascota que la remata (también por quemadura) y
  **1 moneda de oro** al jugador (en qué se gasta: ver la tienda en
  [reglas-de-cuidado.md](reglas-de-cuidado.md)).
- Subir del nivel n al n+1 cuesta **10 × (n + 1)** XP (10, 20, 30…). La XP vuelve a 0 al
  subir. Nivel máximo: **10**. El nuevo daño se aplica al instante, en mitad del combate.

## 6. Interferencias conocidas (pendientes de equilibrar)

1. La dificultad del combate solo depende del reloj: siempre se acaba perdiendo.
2. *(Compartida)* El descanso se rellena casi al instante (10 s durmiendo, o al llegar la
   noche): pese a que ahora baja con el tiempo (−4/h), sigue siendo fácil de mantener.
3. *(Compartida)* Una mascota sola en su bioma tiene social 0, así que su confianza no pasa
   de ~67 aunque esté perfecta: conviene tener compañía.
4. *(Compartida)* El clima siempre vale 100: un tercio de la velocidad no se puede perder.
5. *(Compartida)* La digestión solo sube comiendo bien (+8 por comida con hambre) y baja
   −5/h: sin comidas bien medidas tiende a bajar, y con ella la velocidad.
6. *(Compartida)* Limpiar a menudo compensa de sobra lo que el combate ensucia y despeina
   (0,5 por disparo frente a +45 / +20 por limpieza).
