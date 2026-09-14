# SIMONA — Firmware ESP32

Firmware real para el dispositivo de **una huerta**. Es la contraparte de
`simulador/servidor_simulado.py`: expone los mismos endpoints HTTP, con el
mismo formato de JSON, para que la app Android no necesite saber si está
hablando con el simulador o con el hardware real. Es 100% backend — no
sirve ningún HTML de dashboard (ese cambio de arquitectura ya se hizo
tanto acá como en el simulador; el análisis visual y los controles viven
100% en `DetalleHuertaActivity`, nativa en la app Android).

## Archivos

| Archivo | Contenido |
|---|---|
| `firmware_simona.ino` | Lógica principal: WiFi AP, sensores, riego, servidor HTTP |
| `README.md` | Este archivo |

## Requisitos

- **Arduino IDE** (2.x recomendado) con el **core de ESP32** instalado
  (Herramientas → Placa → Gestor de placas → buscar "esp32" → instalar el
  de Espressif Systems).
- Librerías (Herramientas → Administrar Bibliotecas):
  - **DHT sensor library** (Adafruit)
  - **Adafruit Unified Sensor** (dependencia de la anterior)
- Placa: cualquier ESP32 DevKit genérico (ESP32-WROOM-32 típico).

No hace falta instalar nada más — `WiFi.h` y `WebServer.h` vienen con el
core de ESP32, siguiendo el mismo criterio de "cero dependencias externas
salvo lo estrictamente necesario" que sigue la app Android.

## Conexionado (pines por defecto, editables al inicio del .ino)

| Señal | Pin ESP32 | Notas |
|---|---|---|
| Sensor de humedad de suelo (capacitivo) | GPIO34 (ADC1) | Salida analógica del módulo |
| DHT11 (temperatura) | GPIO4 | Con resistencia pull-up de 10kΩ si el módulo no la trae |
| LDR (luz, en divisor de tensión) | GPIO35 (ADC1) | LDR + resistencia de 10kΩ a GND |
| Sensor de pH (módulo genérico) | GPIO33 (ADC1) | Requiere calibración, ver abajo |
| Relé de la bomba de riego | GPIO26 | Activo-bajo (LOW = bomba encendida) por defecto |
| LED de estado | GPIO2 | Encendido mientras riega (opcional, LED interno de la placa) |

**Importante:** los pines analógicos usados son todos de **ADC1**
(GPIO32-39). Los pines de **ADC2** (GPIO0, 2, 4, 12-15, 25-27) no se pueden
leer de forma confiable mientras el WiFi está activo en modo AP — por eso
se evitaron para los 3 sensores analógicos.

Si tu módulo de relé es **activo-alto** en vez de activo-bajo, invertí estas
dos líneas al principio del `.ino`:

```cpp
#define RELE_ENCENDIDO LOW
#define RELE_APAGADO   HIGH
```

## Calibración (antes de un uso productivo)

El firmware arranca con valores de calibración de referencia que **hay que
ajustar contra el sensor físico real** — igual advertencia que hace el Plan
de Desarrollo en la sección 3 (matriz de perfiles de cultivo):

- **Humedad de suelo:** `CAL_HUMEDAD_SECO` / `CAL_HUMEDAD_MOJADO` — sumergir
  el sensor en agua y anotar la lectura cruda (`analogRead`), después
  dejarlo al aire y anotar la otra lectura.
- **pH:** `CAL_PH_PENDIENTE` / `CAL_PH_OFFSET` — calibrar con soluciones
  buffer de pH 4.0, 7.0 y 10.0, y ajustar la recta.
- **Luminosidad:** la escala 0-950 es simplificada (etiquetada "lux" en el
  dashboard por simplicidad de UI, no calibrada contra un luxómetro real) —
  ver nota metodológica de la sección 3 del Plan de Desarrollo.

## Identidad de la huerta

Cada ESP32 físico representa **una sola huerta**. Lo que lo distingue de
otro ESP32 con el mismo firmware es únicamente su contraseña de WiFi:

```cpp
static const char *AP_SSID     = "SIMONA";       // igual en todos los dispositivos
static const char *AP_PASSWORD = "huerta1234";   // ÚNICA por dispositivo/huerta
```

Para preparar el ESP32 de una segunda huerta, alcanza con cambiar
`AP_PASSWORD` y volver a cargar el firmware. La contraseña debe tener
**8 caracteres como mínimo** (requisito de WPA2).

## Compilar y cargar

1. Abrir `firmware_simona.ino` en Arduino IDE (los otros archivos de la
   carpeta se cargan solos como pestañas del mismo sketch).
2. Herramientas → Placa → elegir tu modelo de ESP32 (ej. "ESP32 Dev Module").
3. Herramientas → Puerto → elegir el puerto USB del ESP32.
4. Subir (▶). La IP del dashboard queda impresa en el Monitor Serie
   (115200 baudios) al arrancar — por defecto `192.168.4.1`.

## Verificación rápida sin la app

Con el ESP32 encendido y conectado por WiFi a la red `SIMONA` (contraseña
`AP_PASSWORD`), desde el navegador del celular o la compu:

- `http://192.168.4.1/` → JSON de ping/estado-vivo con la lista de
  endpoints (ya no hay dashboard visual acá, ver nota de arquitectura
  al principio de este README).
- `http://192.168.4.1/data` → JSON de telemetría liviana.
- `http://192.168.4.1/estado` → JSON completo (config + estado + historial).

## Diferencias respecto al simulador

- El **tanque de agua** sigue siendo un contador de software (no hay sensor
  de nivel documentado en el Plan) — se resetea a 100 con `POST
  /refill_tanque`, igual que en `servidor_simulado.py`.
- El **historial para `/export_csv`** vive en RAM (buffer circular de hasta
  500 lecturas) y se pierde al reiniciar el ESP32 — el simulador además
  escribe a disco (Fase 4.3); portar eso al ESP32 implicaría sumar
  `LittleFS`, queda como posible ítem de roadmap si hace falta persistencia
  entre reinicios.
- **Sin persistencia de la config en NVS**, igual que documenta la sección 9
  del Plan de Desarrollo: cada `POST /config` (cada conexión nueva de la
  app) resetea el estado, es la app la única fuente de verdad persistente
  de la flota.
