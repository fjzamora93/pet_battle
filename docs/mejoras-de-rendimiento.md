# Mejoras de rendimiento

Última actualización: 2026-11-26.

Evaluación del rendimiento de la app y de lo que se podría mejorar. **Nada de esto está
implementado todavía**: es un análisis para decidir qué atacar. Las mejoras se separan en las
que sirven para **todas las plataformas** (móvil, reloj y escritorio) y las que son
**exclusivas del reloj**.

## 1. Situación medida (SaveWatch 2, modo cuidado)

Medido con `adb shell dumpsys gfxinfo` y `dumpsys meminfo` durante 15 s en el modo cuidado,
con la build de depuración (`watchDebug`).

**El dispositivo**: 4 núcleos Cortex-A55 a 1,5 GHz, GPU PowerVR BXE-2-32 (gama muy baja),
2 GB de RAM, Android 8.1 marcado como de poca memoria (`ro.config.low_ram=true`), pantalla
rectangular de 410 × 502 px. La caché de texturas de la GPU es de 56 MB.

| Dato | Valor |
|---|---|
| Fotogramas con tirones | **100 %** (334 de 334) |
| Tiempo por fotograma | mediana 81 ms; el 10 % peor, 200 ms (≈ 11 fps reales; para ir fluido hacen falta 16 ms) |
| Preparar el dibujo en el hilo principal (CPU) | ~90 ms por fotograma |
| Dibujar en la GPU | ~93 ms por fotograma |
| Subir imágenes a la GPU | ~10 ms por fotograma; lenta en el 69 % de ellos |
| CPU de la app | 98 % (un núcleo entero ocupado sin parar) |
| Memoria de la app | 221 MB, de ellos **176 MB** de imágenes descomprimidas |

**Conclusión**: el peso de las imágenes en disco **no** es la causa principal de los tirones.
Los cuellos de botella son la CPU y la GPU, que tardan cada una unos 90 ms por fotograma. El
tamaño de las imágenes sí es un problema de **memoria** (y probablemente de subidas de texturas
a la GPU).

### Lo que falta por confirmar

- **Reparto de los 90 ms de CPU** entre cada causa: los datos de fotogramas no lo separan. Hace
  falta una traza de Perfetto (no requiere tocar código).
- **Cuánto pesa la build de depuración**: hay que medir una build *release* (requiere
  configurar su firma).
- **Por qué se vuelven a subir imágenes a la GPU**: la hipótesis es que la caché de texturas
  (56 MB) se queda corta; se confirma con la traza.

## 2. Causas encontradas

1. **La escena se redibuja entera a 60 fps sin parar.** El bucle de `BiomeScene`
   (`withFrameNanos`) repinta en cada refresco de pantalla aunque nada se mueva: cielo, colinas,
   terreno, degradado del momento del día, mascota, sombra y hierba.
2. **Build de depuración.** Compose sin R8 ni perfiles de arranque es varias veces más lento en
   CPU; encaja con los ~90 ms del hilo principal.
3. **Una capa fuera de pantalla por mascota y fotograma.** `drawActor` hace `saveLayer` para
   pintar la suciedad encima (`BlendMode.SrcAtop`), aunque la mascota esté limpia. En GPUs como
   PowerVR cada cambio de capa obliga a vaciar el trabajo pendiente.
4. **Imágenes mucho más grandes de lo que se ven.** El pájaro sale de una hoja de 2048 × 1536
   (casillas de 256 px) y los fondos son de 1284 × 832; todo se reduce al vuelo en cada
   fotograma, sin versiones reducidas.
5. **Se cargan imágenes que no se dibujan.** `BiomeScene` carga las **15 hojas de especies**
   (10 MB en disco, **218 MB descomprimidas**) aunque en el modo cuidado solo se dibuja el
   pájaro azul, y en el bioma solo las especies presentes.
6. **Zoom sobre la escena completa** (modo cuidado): se procesa la escena entera con
   `graphicsLayer` ×1,8 / ×2,2 aunque solo se vea un trozo.
7. **Objetos nuevos en cada fotograma**: filtro de color de saturación por mascota, listas
   ordenadas, medición de textos… Poco cada uno, pero suma en CPUs lentas.

## 3. Mejoras comunes a todas las plataformas

Mejoran el móvil, el reloj y el escritorio, porque el código de la escena es compartido.

| Mejora | Causa | Impacto esperado | Esfuerzo |
|---|---|---|---|
| **No redibujar sin necesidad**: pausar el bucle cuando nada se mueve (mascotas posadas, sin comida, sin animación en curso) o limitar los fps de la escena | 1 | Alto: menos CPU, GPU y batería en todas | Medio |
| **Cargar solo las hojas que se dibujan**: las especies presentes en el bioma; en el modo cuidado, solo la del pájaro de cuidado | 5 | Alto en memoria (de ~218 MB a ~13–50 MB de imágenes) | Bajo |
| **Builds release con R8** (y perfiles de arranque) para Android | 2 | Alto en CPU (móvil y reloj) | Bajo-medio (firma y reglas de R8) |
| **Quitar el `saveLayer` cuando la mascota no está sucia**, o pintar la suciedad sin capa aparte | 3 | Medio en GPU | Bajo |
| **No crear objetos en cada fotograma** (reutilizar el filtro de saturación, las listas y los textos medidos) | 7 | Bajo-medio en CPU | Bajo |
| **Medir antes y después** con una traza de Perfetto y `gfxinfo`, y repetir las mismas medidas tras cada cambio | — | Sirve para saber qué ha funcionado | Bajo |

## 4. Mejoras exclusivas del reloj

Solo tienen sentido en el flavor `watch` (pantalla pequeña, GPU y RAM muy limitadas).

| Mejora | Causa | Impacto esperado | Esfuerzo |
|---|---|---|---|
| **Limitar la escena a 20–30 fps** en el reloj (el móvil sigue a 60) | 1 | Alto: reduce a la mitad o más el trabajo por segundo | Bajo |
| **Imágenes reducidas para el reloj**: hoja del pájaro de cuidado con casillas de 128 px en vez de 256 (¼ de memoria) y fondos a la resolución de su pantalla, como recursos propios del flavor `watch` | 4 | Alto en GPU y memoria | Medio (el script `care_sheet.py` puede generar las dos versiones) |
| **Recortar el fondo antes de dibujarlo**: dibujar solo la parte visible de cada capa ya ampliada, en vez de la escena completa con `graphicsLayer` | 6 | Medio en GPU | Medio |
| **Fondo más simple**: en el reloj, prescindir del cielo que se desplaza y del degradado del día (o dibujarlos en una sola imagen) | 1, 4 | Medio en GPU | Bajo |
| **Tener en cuenta la poca RAM** (`low_ram`): no cargar nada que no sea del modo cuidado (comida solo al elegirla, sin hojas de especies) | 5 | Medio: menos riesgo de que Android cierre la app | Bajo |

## 5. Orden propuesto

1. Medir con Perfetto y con una build release, para conocer el reparto real.
2. Comunes de bajo esfuerzo: cargar solo las hojas necesarias, quitar el `saveLayer` cuando no
   hace falta y no crear objetos por fotograma.
3. Reloj: limitar a 20–30 fps y generar la hoja reducida del pájaro de cuidado.
4. Común: pausar el bucle cuando nada se mueve.
5. Volver a medir en el SaveWatch 2 con las mismas herramientas y actualizar este documento.
