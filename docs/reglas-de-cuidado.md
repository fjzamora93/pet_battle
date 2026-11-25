# Reglas de cuidado de la mascota

Este documento recoge las reglas de **cuidado** de la mascota (necesidades, cría, rareza,
tienda y ciclo del día) **tal y como están implementadas** en el código. Cada vez que cambie
una regla o un valor de balance, hay que actualizarlo aquí (ver CLAUDE.md). Las reglas del
combate están en [reglas-de-combate.md](reglas-de-combate.md). Lo que comparten los dos
sistemas (cómo los cuidados afectan a los stats de combate y cómo el combate desgasta las
necesidades) está en ambos documentos.

Última actualización: 2026-11-25.

Fuentes en el código:
- Necesidades de la mascota: `shared_features/pet_status/domain/PetNeedsBalance.kt`,
  `PetNeedsRules.kt`, `PetDerivedNeeds.kt`, `entities/PetNeedsEntity.kt`.
- Efecto en el combate: `shared_features/pet_combat/domain/PetCombatStatsRules.kt`
  (`PetCombatBalance`).
- Economía: `shared_features/pet_status/domain/GameRules.kt`.
- Ciclo del día: `core/time/DayCycle.kt`.

---

## 1. Necesidades de la mascota

Su mantenimiento. Todas van de 0 a 100. **El tiempo es absoluto y común**: todas las
mascotas viven en tiempo real a la vez, estén seleccionadas o no y en cualquier bioma (y
también mientras la app está cerrada).

### Resumen: qué mueve cada necesidad
| Necesidad | Grupo | Baja con el tiempo | Mejora con | Empeora con | Stat de combate (por punto que falta) |
|---|---|---|---|---|---|
| Hambre (saciedad) | — | −18/h | Comida de su dieta: +25 | El tiempo | Daño: −0,33 % |
| Descanso (energía) | — | −4/h despierta | Dormir o la noche: de 0 a 100 en ~10 s | Combate: −1 por disparo | Velocidad: −0,5 % (a 0: la mitad) |
| Diversión | — | −12/h (+50% por cada necesidad básica descuidada) | Interacción directa (mimos): +10 por toque | El tiempo | Confianza: −0,33 puntos |
| Digestión | — | −5/h | Su comida con hambre: +8 | Comida ajena: −15 · comer sin hambre: −10 | Velocidad: −0,33 % |
| Higiene | **Salud** | −8/h | Limpieza: +45 | Combate: −0,5 por disparo | Confianza: −0,33 puntos |
| Plumaje | **Salud** | −3/h | Limpieza: +20 | Combate: −0,5 por disparo | Daño: −0,33 % |
| Pico y garras | **Salud** | −6/h | Limpieza: +15 · combate: +0,5 por disparo | El tiempo | Daño: −0,33 % |
| Social | **Ambientales** | No (se calcula) | Otra de su especie en el bioma: 100 · solo otras especies: 50 | Estar sola: 0 | Confianza: −0,33 puntos (sola: −33,3) |
| Clima | **Ambientales** | No (se calcula) | — | — (siempre 100, pendiente de reglas) | Velocidad: −0,33 % |

Los porcentajes son sobre el **total** del stat (ver la sección 2).

Las **ambientales** no dependen de la interacción del jugador y no se guardan.

### Reglas de detalle
- **Descansando** (dormida o de noche) todo lo que baja con el tiempo baja al **40%**, y el
  descanso se recupera en vez de bajar.
- **Descuido**: hambre, descanso o higiene por debajo de **20** cuentan como descuidadas;
  cada una hace que la diversión baje un 50% más rápido.
- **Dar de comer**: se elige una comida y se deja en el suelo del bioma tocando la escena (se
  pueden dejar varias). Van a por ella **todas las mascotas despiertas que tengan esa comida en
  su dieta**, tengan hambre o no: cada una vuela a la más cercana que quiera. **Se la queda la
  primera que llega** (aunque esté llena) y las demás desisten. Si nadie se la come, desaparece
  a los **10 segundos**. La comida dejada no se guarda en la partida y se pierde al cambiar de
  bioma.
- **Qué le hace la comida** (quien la come siempre se la come):
  - de su dieta y con hambre (saciedad < 75): sacia +25 y digestión +8;
  - de su dieta sin hambre: sacia +25 y digestión −10;
  - fuera de su dieta: no sacia y digestión −15 (−25 si además no tenía hambre);
  - **comer no ensucia**;
  - el polluelo come de todo (nunca hay comida ajena para él);
  - como solo van a por comida de su dieta, dejándola en el suelo nunca comen comida ajena;
    pero sí pueden **comer sin hambre**: dejar comida de más castiga la digestión de quien
    llegue antes.
- **Limpiar**: con el agua en la mano se toca la mascota que se quiere limpiar (cualquiera del
  bioma, no solo la seleccionada). Mejora a la vez higiene, plumaje y pico y garras; se puede si
  a cualquiera de las tres le falta algo.
- **Dormida** no come, no se limpia ni se deja mimar, y se despierta sola con el descanso
  lleno. De noche descansa pero sigue despierta (se puede interactuar).

### En el panel de la mascota
Plegado solo muestra su nombre. Al desplegarlo aparece su especie y todo lo demás: la
pestaña **Necesidades**
(hambre, descanso, diversión y digestión; **Salud**: higiene, plumaje, pico y garras; y, al
final, **Ambientales**: social y clima), la pestaña **Combate**, el botón **i** y el botón
de liberarla. En la pestaña **Necesidades**, el botón **i** explica qué sube y qué baja cada
necesidad.

## 2. Cuidados y combate (compartido con reglas-de-combate.md)

**Cómo afectan los cuidados al combate.** Cada mascota tiene cuatro stats de combate. Daño,
velocidad y confianza rinden su **total** (según el nivel) menos lo que restan sus tres
necesidades (todas al 100% = rinde el total). El rango es fijo por especie.

| Stat de combate | Necesidades que lo suben o bajan | Por cada punto que le falta a una de ellas |
|---|---|---|
| **Daño** (por golpe) | Hambre (saciedad), plumaje, pico y garras | **−0,33 %** del daño total (−0,004 × nivel de daño; nivel 0: −0,0033) |
| **Velocidad** (ataques/s) | Descanso (energía), digestión, clima | Descanso: **−0,5 %** (−0,0071 ataques/s). Digestión y clima: **−0,33 %** (−0,0048 ataques/s). Sin corte: con descanso a 0, la **mitad** |
| **Confianza** (% de acierto) | Diversión, social, higiene | **−0,33 puntos** de acierto |
| **Rango** (alcance) | — (no depende de las necesidades) | — |

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

- **Sin energía no se agota**: sigue atacando, a la mitad de velocidad (si digestión y clima
  están llenos).
- Una mascota **sola en su bioma** tiene social 0 y su confianza no pasa de ~67 aunque esté
  perfecta; con otra de su especie en el bioma, social vale 100 (con solo otras especies, 50).
- El **clima** vale siempre 100 (pendiente de reglas).

**Cómo afecta el combate a los cuidados:** **cada disparo** gasta **1 de descanso**, resta
**0,5** de higiene y de plumaje, y afila **+0,5** el pico y las garras. Una mascota retirada a la
**reserva** en plena partida **descansa**: recupera **5 de descanso por segundo** de partida,
pero **solo hasta el descanso con el que entró al combate** (la primera vez que se colocó en esa
partida; si entró con 80, ese es su máximo). Reintentar empieza una partida nueva: el tope pasa
a ser el descanso que tenga en ese momento.

Los totales de cada stat, el rango por especie y el resto del combate están en
[reglas-de-combate.md](reglas-de-combate.md).

## 3. Cría

- **Satisfacción global**: la media de **todas** las necesidades de la mascota (hambre,
  descanso, diversión, digestión, higiene, plumaje, pico y garras, social y clima), en %. Se ve
  en el panel de la mascota.
- **Pareja**: dos adultos de la **misma especie** que viven en el **mismo bioma** (si hay más,
  se emparejan de dos en dos; los huevos y los polluelos no cuentan).
- **Una vez al día** (fecha local), la primera vez que **los dos** superan el **90 %** de
  satisfacción, tienen un **50 %** de probabilidad de poner un **huevo**. Salga o no, ese día ya
  no lo vuelven a intentar.
- El **huevo** aparece en el bioma de los padres con un nombre automático ("Cría 1", "Cría 2"…).
  No se puede seleccionar ni cuidar, y sus necesidades no corren hasta que nace. En su última
  hora se balancea.
- A las **8 horas** eclosiona en un **polluelo** con la **especie destinada** de sus padres:
  crecerá a esa especie (a las 10 comidas, como cualquier polluelo) coma lo que coma.
- Los huevos de la tienda siguen igual: sus polluelos crecen según la comida favorita.
- Todo esto ocurre también con la app cerrada (al volver se ponen al día eclosiones y crías).

## 4. Rareza (tier)

Cada mascota tiene una **rareza** de 1 a 5 estrellas, que colorea el escudo de su nivel y se
ve (con sus estrellas) en su ficha. **De momento no tiene efecto en el juego.**

| Estrellas | Rareza | Escudo |
|---|---|---|
| 1 | Común | Gris |
| 2 | Poco común | Verde |
| 3 | Raro | Azul |
| 4 | Épico | Morado |
| 5 | Legendario | Dorado |

- **Huevos comprados**: 90 % de 1 estrella, 10 % de 2.
- **Cría, padres del mismo tier**: 20 % de que la cría nazca con **una estrella más** (máximo 5);
  si no, con el tier de sus padres.
- **Cría, padres de distinto tier**: cualquiera entre el más bajo y el más alto, todos igual de
  probables (2 + 4 → 2, 3 o 4).
- Las mascotas de partidas anteriores tienen 1 estrella.

## 5. Tienda y biomas

- El oro se gana en combate (**1 moneda** por enemigo derrotado; ver
  [reglas-de-combate.md](reglas-de-combate.md)).
- Con el oro: un **huevo** cuesta **10** (el primero es gratis) y un **bioma** **20** (la
  pradera viene gratis).
- **Biomas jugables**: de momento solo la **pradera** (gratis) y el **bosque** (20 de oro),
  los que tienen fondo por capas. Los demás salen como "Próximamente": no se pueden comprar ni
  visitar. Las mascotas de partidas antiguas que vivían en otro bioma se mudan a la pradera.

## 6. Ciclo del día (hora local)

Cuatro franjas de 6 horas. Cada una cambia el aspecto del **cielo** con un filtro (una matriz
de color y un degradado); el fondo, el suelo y los pájaros conservan siempre sus colores. El
cambio entre franjas es gradual.

| Momento | Horas | Aspecto | Mascotas |
|---|---|---|---|
| Noche | 0:00–6:00 | Cielo oscuro y azulado | **Descansan** sin dormirse (recuperan descanso, todo baja al 40%); se puede seguir interactuando con ellas |
| Mañana | 6:00–12:00 | El alba: cielo rosa coral | Activas |
| Mediodía | 12:00–18:00 | El cielo tal cual | Activas |
| Tarde | 18:00–24:00 | Cielo de atardecer anaranjado | Activas |

Dormir a una mascota a mano solo cambia su animación (y la hace descansar); dormida no come,
no se limpia ni se deja mimar, y se despierta sola con el descanso lleno.

Con la app cerrada, al volver se simula lo ocurrido minuto a minuto (sabiendo qué horas
fueron de noche), con un máximo de 7 días.

## 7. Interferencias conocidas (pendientes de equilibrar)

1. Como todas viven a la vez, cuantas más mascotas, más trabajo de cuidado: una mascota
   olvidada en otro bioma sigue pasando hambre.
2. El hambre es lo que más aprieta: de 80 a descuidada en unas 3 h despierta.
3. Entre "tener hambre" (< 75) y alimentar (+25) el margen es estrecho: con 76 ya cuenta como
   comer sin hambre. Una mascota recién nacida (80) siempre empieza "sin hambre".
4. *(Compartida)* El descanso se rellena casi al instante (10 s durmiendo, o al llegar la
   noche): pese a que ahora baja con el tiempo (−4/h), sigue siendo fácil de mantener.
5. *(Compartida)* Una mascota sola en su bioma tiene social 0, así que su confianza no pasa
   de ~67 aunque esté perfecta: conviene tener compañía.
6. *(Compartida)* El clima siempre vale 100: un tercio de la velocidad no se puede perder.
7. *(Compartida)* La digestión solo sube comiendo bien (+8 por comida con hambre) y baja
   −5/h: sin comidas bien medidas tiende a bajar, y con ella la velocidad.
8. *(Compartida)* Limpiar a menudo compensa de sobra lo que el combate ensucia y despeina
   (0,5 por disparo frente a +45 / +20 por limpieza).
