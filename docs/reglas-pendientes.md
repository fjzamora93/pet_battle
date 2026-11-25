# Reglas pendientes de definir

Ideas y sistemas **aún no implementados**. A diferencia de
[reglas-de-combate.md](reglas-de-combate.md) y [reglas-de-cuidado.md](reglas-de-cuidado.md),
que describen lo que hace el código, aquí se apunta lo planeado. Cuando algo se implemente,
se pasa al documento que corresponda y se quita de aquí.

Última actualización: 2026-11-25.

---

## 1. Rotación de mascotas durante el combate (la reserva)

Poder cambiar las mascotas que están en el mapa por otras que esperan en **la reserva**.

- Estar en la reserva **recupera el cansancio temporal** provocado durante el combate.
- **Límite de la recuperación**: lo que marcan las necesidades con las que entró al combate.
  Si entró con el descanso al 80 %, en la reserva puede recuperar como máximo hasta el 80 %,
  no más.

Por definir:
- A qué ritmo se recupera en la reserva.
- Si "cansancio temporal" es solo el descanso o también otras necesidades que el combate
  desgasta (higiene y plumaje).
- Cuántas mascotas caben en el mapa y en la reserva, y si el cambio tiene coste o tiempo de
  espera.
- Si la reserva solo existe dentro de la partida o deja rastro en las necesidades guardadas.

## 2. Combate entre jugadores (PvP)

Diseñar y crear una mecánica de combate entre varios usuarios.

Por definir:
- Formato del enfrentamiento (en tiempo real o por turnos, defensa contra ataque, simétrico…).
- Cómo se emparejan los jugadores y qué recompensas da.
- Qué se necesita en el servidor (hoy el juego no tiene backend ni cuentas: todo se guarda en
  el dispositivo).
- Cómo influyen las necesidades y el nivel de cada mascota en el combate PvP.

## 3. Clima real (API meteorológica)

Hoy la necesidad **clima** vale siempre 100. La idea es calcularla con el tiempo real.

- **Fuente**: Open-Meteo, consultada por *polling* **cada 30 minutos**:
  `https://api.open-meteo.com/v1/forecast?latitude=43.2630&longitude=-2.9350&current=weather_code`
  (coordenadas fijas de Bilbao).
- **Interpretación** del `weather_code` (códigos WMO) en tres estados:

  | Código | Significado | Estado |
  |---|---|---|
  | 0, 1 | Despejado, mayormente despejado | Soleado |
  | 2, 3, 45, 48 | Nublado, niebla | Nublado |
  | Cualquier otro (51 en adelante) | Llovizna, lluvia, nieve o tormenta | Lluvia |

  Referencia (pseudocódigo):

  ```
  enum WeatherState { soleado, nublado, lluvia }

  WeatherState parseWeatherCode(int code) {
    if (code == 0 || code == 1) {
      return WeatherState.soleado; // 0: Despejado, 1: Mayormente despejado
    } else if (code == 2 || code == 3 || code == 45 || code == 48) {
      return WeatherState.nublado; // 2-3: Nublado, 45-48: Niebla
    } else {
      return WeatherState.lluvia;  // 51+: Llovizna, lluvia, nieve o tormenta
    }
  }
  ```

Por definir:
- Cuánto vale la necesidad clima en cada estado (y si depende de la especie o del bioma).
- Si el clima afecta a algo más que a la velocidad (aspecto del cielo, comportamiento de las
  mascotas…).
- Qué pasa sin conexión o si la API falla (último valor conocido, 100 por defecto…).
- Cómo se trata el clima durante la puesta al día con la app cerrada (la API solo da el
  tiempo actual).
- Si la ubicación siempre es fija o se usa la del dispositivo.
