#!/usr/bin/env python3
"""
Servidor Simulado ESP32 - SIMONA (single-device, autocontenido)
================================================================
SOLO PARA DEMO / DESARROLLO. No requiere dependencias externas
(usa http.server de la librería estándar).

Cambio de arquitectura respecto a la versión anterior: este servidor
representa a UN SOLO dispositivo ESP32 (una huerta), tal como sería el
hardware real. Un ESP32 físico nunca gestiona "varias huertas" — cada
cantero tiene su propio ESP32, distinguido únicamente por la contraseña
de su red WiFi (mismo SSID "SIMONA" para todos, ver WifiConnectionManager
en la app). Por eso este simulador ya NO expone un CRUD multi-huerta
(/api/huertas, /api/perfiles, etc.): eso es responsabilidad exclusiva de
la app Android (SeleccionarPerfilActivity, AjustarRangosActivity,
DatosHuertaActivity, HuertaRepository), que gestiona la flota de huertas
en local y decide a cuál ESP32 conectarse en cada momento.

Lo que SÍ hace un ESP32 real (y por lo tanto este simulador):
  - Guardar la config de SU PROPIA huerta (rangos + nombre/categoría),
    recibida una vez desde la app vía POST /config (sección 14, "Config
    push", sin persistencia en NVS — limitación aceptada).
  - Medir (simular) sus sensores y exponerlos en GET /data (telemetría
    liviana, la que ya consume el polling de DashboardActivity.kt) y
    GET /estado (objeto completo, el que consume el dashboard HTML).
  - Ejecutar la lógica de riego automático con sus propios umbrales.
  - Aceptar riego manual forzado (POST /riego_manual).
  - Servir una página HTML simple: el dashboard de análisis de ESTA
    huerta (gauge, tendencia, tanque, riego). Sin menús, sin wizard de
    creación, sin asistente de IA — todo eso vive en la app nativa.

Uso:
    python3 servidor_simulado.py [puerto]
"""

import csv
import io
import json
import os
import random
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# ---------- Campos de configuración de la huerta ----------
CAMPOS_RANGO = ["humedadMin", "humedadMax", "phMin", "phMax", "luzMin", "luzMax", "tempMin", "tempMax"]
PARES_MIN_MAX = [("humedadMin", "humedadMax"), ("phMin", "phMax"), ("luzMin", "luzMax"), ("tempMin", "tempMax")]

# ---------- Parámetros de simulación (sección 14.3 / 14.7 del Plan) ----------
ANCHO_MINIMO_HUMEDAD = 10.0    # misma validación que AjustarRangosActivity
RIEGO_DURACION_MAX_SEG = 30    # corte de seguridad automático
RIEGO_MANUAL_DURACION_SEG = 15 # duración fija en modo manual
RIEGO_COOLDOWN_SEG = 10        # cooldown para la demo
HISTORIAL_MAX = 500            # tope del log en memoria (CSV export)
# Fase 4.3 — además del historial en memoria (que se resetea en cada
# POST /config y tiene un tope de HISTORIAL_MAX registros), se escribe
# cada lectura a este archivo en disco, sin tope y sin resetearse nunca
# automáticamente. Así el historial sobrevive a un restart del script
# (útil si el simulador se cae o se reinicia a mitad de una demo). Se
# crea junto al script si no existe.
CSV_DISCO = "historial_simona.csv"
CSV_DISCO_COLUMNAS = [
    "timestamp", "humedad_suelo", "temperatura", "luminosidad", "ph", "tanque_agua", "riego_activo"
]

lock = threading.Lock()


def estado_por_defecto():
    """Config + estado inicial de demo, para que el simulador funcione
    'out of the box' sin depender de que la app ya haya llamado a
    POST /config (igual que un ESP32 de banco de pruebas, con valores
    de fábrica cargados)."""
    humedad_inicial = 47.5
    return {
        # ---- Config de la huerta (la pisa POST /config) ----
        "nombre": "Mi Huerta",
        "categoria": "Fruto",
        "humedadMin": 35.0, "humedadMax": 60.0,
        "phMin": 6.0, "phMax": 6.8,
        "luzMin": 500, "luzMax": 850,
        "tempMin": 18.0, "tempMax": 28.0,
        # ---- Estado simulado en vivo ----
        "humedad_suelo": humedad_inicial,
        "temperatura": 23.0,
        "luminosidad": 675,
        "ph": 6.4,
        "tanque_agua": 85.0,
        "riego_activo": False,
        "riego_manual": False,
        "riego_inicio": None,
        "riego_cooldown_hasta": 0,
        "historial_humedad": [humedad_inicial] * 30,
    }


estado = estado_por_defecto()
historial_log = []  # lista de lecturas para exportar a CSV


def escribir_historial_a_disco(registro):
    """Fase 4.3 — apéndice de bajo impacto: escribe UNA lectura al CSV en
    disco (CSV_DISCO). Se llama una vez por segundo desde
    actualizar_simulacion(), con el mismo registro que ya se agrega a
    historial_log en memoria.

    Escribe el encabezado solo si el archivo todavía no existe (primera
    vez que corre el script en este directorio) — en runs siguientes se
    abre en modo apéndice ('a'), así el historial se acumula entre
    reinicios en vez de perderse.

    Un fallo acá (disco lleno, sin permisos, etc.) no debe tumbar el hilo
    de simulación — se loguea a stderr y se sigue, igual criterio que el
    resto del simulador con errores no críticos.
    """
    try:
        archivo_existe = os.path.exists(CSV_DISCO)
        with open(CSV_DISCO, mode="a", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=CSV_DISCO_COLUMNAS)
            if not archivo_existe:
                writer.writeheader()
            writer.writerow(registro)
    except OSError as e:
        print(f"[aviso] No se pudo escribir el historial en disco: {e}", file=sys.stderr)


def actualizar_simulacion():
    """Hilo secundario: simula sensores y riego de ESTA huerta, cada 1
    segundo, usando sus propios rangos configurados."""
    while True:
        time.sleep(1)
        with lock:
            ahora = time.time()
            h = estado

            # Fluctuación ambiental (independiente de los rangos
            # configurados; los rangos solo definen alertas y el umbral
            # de riego automático)
            h["temperatura"] = max(10.0, min(40.0, h["temperatura"] + random.uniform(-0.2, 0.2)))
            h["luminosidad"] = max(50, min(950, h["luminosidad"] + random.randint(-15, 15)))
            h["ph"] = max(4.5, min(8.5, h["ph"] + random.uniform(-0.02, 0.02)))

            # Control de riego
            if h["riego_activo"]:
                if h["tanque_agua"] > 0:
                    h["humedad_suelo"] += 1.2
                    h["tanque_agua"] = max(0.0, h["tanque_agua"] - 0.6)
                else:
                    h["riego_activo"] = False
                    h["riego_manual"] = False

                tiempo_regando = ahora - (h["riego_inicio"] or ahora)

                if h["riego_manual"]:
                    corte_por_humedad = h["humedad_suelo"] >= 80.0
                    corte_por_tiempo = tiempo_regando >= RIEGO_MANUAL_DURACION_SEG
                else:
                    corte_por_humedad = h["humedad_suelo"] >= h["humedadMax"]
                    corte_por_tiempo = tiempo_regando >= RIEGO_DURACION_MAX_SEG

                if corte_por_humedad or corte_por_tiempo or h["tanque_agua"] <= 0:
                    h["riego_activo"] = False
                    h["riego_manual"] = False
                    h["riego_inicio"] = None
                    h["riego_cooldown_hasta"] = ahora + RIEGO_COOLDOWN_SEG
            else:
                h["humedad_suelo"] -= 0.25
                if (h["humedad_suelo"] <= h["humedadMin"]
                        and ahora >= h["riego_cooldown_hasta"]
                        and h["tanque_agua"] > 0):
                    h["riego_activo"] = True
                    h["riego_manual"] = False
                    h["riego_inicio"] = ahora

            h["humedad_suelo"] = max(5.0, min(95.0, h["humedad_suelo"]))

            h["historial_humedad"].append(round(h["humedad_suelo"], 1))
            if len(h["historial_humedad"]) > 30:
                h["historial_humedad"].pop(0)

            historial_log.append({
                "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
                "humedad_suelo": round(h["humedad_suelo"], 1),
                "temperatura": round(h["temperatura"], 1),
                "luminosidad": int(h["luminosidad"]),
                "ph": round(h["ph"], 2),
                "tanque_agua": round(h["tanque_agua"], 1),
                "riego_activo": "SI" if h["riego_activo"] else "NO",
            })
            if len(historial_log) > HISTORIAL_MAX:
                historial_log.pop(0)

            # Fase 4.3 — misma lectura, además al CSV en disco (no se
            # resetea con historial_log.clear() en /config, ver ese
            # método). Se pasa el último registro agregado en vez de
            # reconstruirlo, para no duplicar el armado del dict.
            escribir_historial_a_disco(historial_log[-1])


# =========================================================================
# Frontend: dashboard de análisis de UNA huerta (la de este dispositivo).
# Nada de menús, wizard de creación ni chat de IA — eso vive en la app.
# Pensado para correr dentro del WebView de DashboardActivity.
# =========================================================================
DASHBOARD_HTML = r"""<!DOCTYPE html>
<html lang="es" data-theme="light">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no, viewport-fit=cover">
<title>SIMONA</title>
<style>
  :root {
    /* Paleta de marca SIMONA: blanco como base, verde/marrón/azul como
       colores primarios (tierra, agua, cultivo) — sin ámbar ni colores
       "genéricos" de plantilla salvo el rojo reservado a alertas críticas. */
    --azul: #1A6FA8; --azul-oscuro: #0F4C73; --azul-suave: #E7F1F8;
    --azul-header: #12688F; --azul-header-oscuro: #0B4A63;
    --verde: #3F6B17; --verde-claro: #6FA330; --verde-suave: #EEF4E4;
    --marron: #8A5A34; --marron-oscuro: #6B4527; --marron-suave: #F5EDE4;
    --rojo: #C23B3B; --tinta: #24211D; --tinta-suave: #6E685F;
    --linea: #E4DFD6; --blanco: #FFFFFF; --card-bg: #FFFFFF; --radio: 16px;
    --toast-bg: #FFFFFF; --toast-text: #24211D; --toast-border: #E4DFD6;
    --toast-shadow: 0 8px 24px rgba(36,33,29,0.12); --spark-color: #1A6FA8;
  }
  [data-theme="dark"] {
    --blanco: #17150F; --card-bg: #211E17; --tinta: #EDE9E1; --tinta-suave: #A39C8E;
    --linea: #35301F; --verde: #82B23E; --verde-suave: #263118;
    --marron-suave: #2E2115; --toast-bg: #211E17; --toast-text: #EDE9E1;
    --toast-border: #35301F; --toast-shadow: 0 8px 24px rgba(0,0,0,0.5); --spark-color: #4E9FD6;
    --azul-header-oscuro: #1A7AA3;
  }
  * { box-sizing: border-box; }
  svg.icono { width: 1em; height: 1em; flex-shrink: 0; }
  .icono-18 { width: 18px; height: 18px; }
  .ic { display: inline-flex; }
  .ic svg { width: 16px; height: 16px; }
  .metrica-header .ic { color: var(--azul); }
  #btnTema svg { width: 16px; height: 16px; }
  .btn-refill .ic svg { width: 11px; height: 11px; }

  html, body {
    margin: 0; padding-bottom: 40px; background: var(--blanco); color: var(--tinta);
    min-height: 100vh; font-family: Roboto, -apple-system, "Segoe UI", system-ui, sans-serif;
    transition: background 0.3s ease, color 0.3s ease;
  }
  .num { font-family: "Roboto Mono", ui-monospace, monospace; font-variant-numeric: tabular-nums; }

  header {
    background: linear-gradient(135deg, var(--azul-header), var(--azul-header-oscuro));
    padding: 18px 20px 14px; padding-top: max(18px, env(safe-area-inset-top));
    color: #FFFFFF; display: flex; align-items: center; gap: 10px;
  }
  [data-theme="dark"] header { background: #181818; border-bottom: 1px solid var(--linea); }
  header .titulos { flex: 1; min-width: 0; }
  header h1 { margin: 0; font-size: 19px; font-weight: 800; }
  header p { margin: 2px 0 0; font-size: 11px; opacity: 0.85; }
  header .btn-icon {
    background: rgba(255,255,255,0.18); border: 1px solid rgba(255,255,255,0.3); color: #FFF;
    border-radius: 8px; width: 32px; height: 32px; display: flex; align-items: center;
    justify-content: center; cursor: pointer; flex-shrink: 0;
  }

  .contenido { padding: 16px 20px 30px; max-width: 480px; margin: 0 auto; }

  .btn {
    display: block; width: 100%; border: none; border-radius: 10px; padding: 13px;
    font-size: 14px; font-weight: 700; cursor: pointer; text-align: center;
  }
  .btn-secundario { background: transparent; color: var(--azul); border: 1px solid var(--azul); }
  .btn:active { transform: scale(0.98); }

  /* ---- Dashboard (gauge) ---- */
  .panel-gauge { padding: 8px 0 4px; text-align: center; }
  .gauge-eyebrow {
    font-size: 11px; font-weight: 700; letter-spacing: 1.2px; text-transform: uppercase;
    color: var(--tinta-suave); margin: 12px 0 2px;
  }
  svg#gauge { width: 100%; max-width: 300px; height: auto; }
  #needle { stroke: var(--tinta); transition: transform 0.6s cubic-bezier(.4,1.4,.6,1); }
  .pivote-outer { fill: var(--tinta); } .pivote-inner { fill: var(--blanco); }
  #gaugeValue { font-size: 42px; font-weight: 800; fill: var(--tinta); }
  .unidad { font-size: 18px; font-weight: 600; fill: var(--tinta-suave); }
  #gaugeEstado { font-size: 13px; font-weight: 700; letter-spacing: 0.4px; }
  .zona-label { font-size: 10px; font-weight: 700; fill: var(--tinta-suave); }
  .sparkline-container {
    margin: 8px auto 16px; max-width: 300px; background: var(--card-bg); border: 1px solid var(--linea);
    border-radius: 10px; padding: 8px 12px;
  }
  .sparkline-title {
    font-size: 10px; font-weight: 700; color: var(--tinta-suave); text-transform: uppercase;
    display: flex; justify-content: space-between; margin-bottom: 4px;
  }
  svg#sparkline { width: 100%; height: 45px; overflow: visible; }
  #sparkPath { stroke: var(--spark-color); } #sparkDot { fill: var(--spark-color); }
  .riego-status {
    margin: 4px 0 16px; padding: 12px 16px; border-radius: var(--radio); display: flex;
    align-items: center; justify-content: space-between; gap: 12px; background: var(--card-bg); border: 1px solid var(--linea);
  }
  .riego-info { display: flex; align-items: center; gap: 12px; }
  .riego-icono {
    width: 38px; height: 38px; border-radius: 50%; flex-shrink: 0; display: flex; align-items: center;
    justify-content: center; background: var(--linea); color: var(--tinta-suave);
  }
  .riego-on .riego-icono { background: #E8F5E9; color: var(--verde-claro); }
  [data-theme="dark"] .riego-on .riego-icono { background: #1B331E; color: var(--verde-claro); }
  .riego-texto strong { display: block; font-size: 14px; font-weight: 700; color: var(--tinta); }
  .riego-texto span { font-size: 12px; color: var(--tinta-suave); }
  .btn-control {
    background: var(--azul); color: #FFF; border: none; padding: 9px 15px; border-radius: 8px;
    font-size: 12px; font-weight: 700; cursor: pointer; white-space: nowrap; flex-shrink: 0;
  }
  .grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; padding-bottom: 10px; }
  .metrica {
    background: var(--card-bg); border: 1px solid var(--linea); border-radius: 12px; padding: 12px 10px;
    text-align: center; display: flex; flex-direction: column; align-items: center;
  }
  .metrica-header {
    display: flex; align-items: center; gap: 5px; font-size: 10px; font-weight: 700;
    color: var(--tinta-suave); text-transform: uppercase;
  }
  .metrica .value { font-size: 20px; font-weight: 700; margin-top: 4px; color: var(--tinta); }
  .metrica .unit { font-size: 11px; font-weight: 500; color: var(--tinta-suave); margin-left: 1px; }
  .metrica .sub-tag { font-size: 10px; font-weight: 700; margin-top: 4px; display: block; }
  .tanque-bar-bg { width: 100%; height: 6px; background: var(--linea); border-radius: 3px; margin-top: 6px; overflow: hidden; }
  .tanque-bar-fill { height: 100%; background: var(--azul); width: 0%; transition: width 0.5s ease, background-color 0.3s ease; }
  .btn-refill {
    margin-top: 6px; background: transparent; border: 1px solid var(--azul); color: var(--azul);
    border-radius: 6px; font-size: 9px; font-weight: 700; padding: 2px 6px; cursor: pointer;
  }
  .footer { text-align: center; padding: 4px 10px 0; font-size: 11px; color: var(--tinta-suave); }
  .footer .sep { margin: 0 6px; opacity: 0.5; }

  .toast-container {
    position: fixed; bottom: 16px; left: 50%; transform: translateX(-50%); z-index: 1000;
    display: flex; flex-direction: column; gap: 8px; width: 90%; max-width: 360px; pointer-events: none;
  }
  .toast {
    background: var(--toast-bg); color: var(--toast-text); padding: 10px 16px; border-radius: 12px;
    font-size: 12px; font-weight: 600; border: 1px solid var(--toast-border); box-shadow: var(--toast-shadow);
    display: flex; align-items: center; gap: 10px; animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1); pointer-events: auto;
  }
  .toast.warning { background: var(--marron); color: #FFF; border-color: transparent; }
  .toast.danger { background: var(--rojo); color: #FFF; border-color: transparent; }
  @keyframes slideUp { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: translateY(0); } }
</style>
</head>
<body>

<header>
  <div class="titulos">
    <h1 id="dashNombreHuerta">SIMONA</h1>
    <p id="dashCategoriaHuerta">Cargando…</p>
  </div>
  <button class="btn-icon" id="btnTema" onclick="toggleTheme()"></button>
</header>

<div class="contenido">
  <div class="panel-gauge">
    <div class="gauge-eyebrow">Humedad del suelo</div>
    <svg id="gauge" viewBox="0 0 300 240" role="img" aria-label="Gauge de humedad del suelo">
      <g id="bandas"></g>
      <g id="ticks"></g>
      <g id="etiquetasZona"></g>
      <line id="needle" x1="150" y1="125" x2="150" y2="40" stroke-width="4" stroke-linecap="round"></line>
      <circle class="pivote-outer" cx="150" cy="125" r="7"></circle>
      <circle class="pivote-inner" cx="150" cy="125" r="3"></circle>
      <text x="150" y="200" text-anchor="middle" class="num"><tspan id="gaugeValue">--</tspan><tspan class="unidad">%</tspan></text>
      <text id="gaugeEstado" x="150" y="224" text-anchor="middle">verificando…</text>
    </svg>
    <div class="sparkline-container">
      <div class="sparkline-title"><span>Historial reciente</span><span>Tendencia (30s)</span></div>
      <svg id="sparkline" viewBox="0 0 280 45">
        <defs><linearGradient id="sparkGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="var(--spark-color)" stop-opacity="0.4"/>
          <stop offset="100%" stop-color="var(--spark-color)" stop-opacity="0.0"/>
        </linearGradient></defs>
        <path id="sparkArea" fill="url(#sparkGrad)" d="" />
        <path id="sparkPath" fill="none" stroke-width="2" d="" />
        <circle id="sparkDot" cx="0" cy="0" r="3" style="display:none;" />
      </svg>
    </div>
  </div>

  <div id="riego" class="riego-status">
    <div class="riego-info">
      <div class="riego-icono" id="riegoIcono"></div>
      <div class="riego-texto"><strong id="riegoTitulo">Verificando…</strong><span id="riegoSub">Consultando estado</span></div>
    </div>
    <button class="btn-control" id="btnToggleRiego" onclick="toggleRiego()">Forzar Riego</button>
  </div>

  <div class="grid">
    <div class="metrica">
      <div class="metrica-header"><span class="ic" data-icon="termometro"></span><span>Temperatura</span></div>
      <div class="value num"><span id="temperatura">--</span><span class="unit">°C</span></div>
      <span class="sub-tag" id="tagTemp">Normal</span>
    </div>
    <div class="metrica">
      <div class="metrica-header"><span class="ic" data-icon="sol"></span><span>Luminosidad</span></div>
      <div class="value num"><span id="luz">--</span><span class="unit">lux</span></div>
      <span class="sub-tag" id="tagLuz">Óptima</span>
    </div>
    <div class="metrica">
      <div class="metrica-header"><span class="ic" data-icon="matraz"></span><span>pH del suelo</span></div>
      <div class="value num"><span id="ph">--</span></div>
      <span class="sub-tag" id="tagPh">Ideal</span>
    </div>
    <div class="metrica">
      <div class="metrica-header"><span class="ic" data-icon="tanque"></span><span>Depósito de Agua</span></div>
      <div class="value num"><span id="tanque">--</span><span class="unit">%</span></div>
      <div class="tanque-bar-bg"><div id="tanqueFill" class="tanque-bar-fill"></div></div>
      <button class="btn-refill" onclick="refillTanque()"><span class="ic" data-icon="refrescar"></span> Rellenar</button>
    </div>
  </div>

  <div class="footer">
    <span id="ultimaActualizacion">Sin datos todavía</span><span class="sep">·</span><span>esp32-sim</span>
  </div>

  <button class="btn btn-secundario" style="margin-top:14px;" onclick="exportarCSV()"><span class="ic" data-icon="descarga"></span> Exportar historial (CSV)</button>
</div>

<div id="toastContainer" class="toast-container"></div>

<script>
// ================= Set de iconos propios (SVG, sin emojis) =================
// Solo los que usa este dashboard de una única huerta — sin íconos de
// perfiles de cultivo, navegación ni chat (eso vive en la app nativa).
const ICONS = {
  sol: '<circle cx="12" cy="12" r="4.2"/><path d="M12 2.5v2.6M12 18.9v2.6M4.6 4.6l1.8 1.8M17.6 17.6l1.8 1.8M2.5 12h2.6M18.9 12h2.6M4.6 19.4l1.8-1.8M17.6 6.4l1.8-1.8"/>',
  luna: '<path d="M20 14.2A8.2 8.2 0 1 1 9.8 4a6.6 6.6 0 0 0 10.2 10.2Z"/>',
  gotaFull: '<path fill="currentColor" stroke="none" d="M12 2.8s6.5 7.4 6.5 12A6.5 6.5 0 1 1 5.5 14.8C5.5 10.2 12 2.8 12 2.8Z"/>',
  gotaOff: '<path d="M12 2.8s6.5 7.4 6.5 12A6.5 6.5 0 1 1 5.5 14.8C5.5 10.2 12 2.8 12 2.8Z" opacity="0.55"/><path d="M4 4l16 16" stroke-linecap="round"/>',
  tanque: '<path d="M5 8.5C5 6 8.1 4 12 4s7 2 7 4.5v9c0 2.5-3.1 4.5-7 4.5s-7-2-7-4.5v-9Z"/><path d="M5 8.5c0 2.5 3.1 4.5 7 4.5s7-2 7-4.5"/><path d="M8.5 15.5c.9.6 2.1.9 3.5.9s2.6-.3 3.5-.9" opacity="0.6"/>',
  termometro: '<path d="M12 14.5V5a2 2 0 1 0-4 0v9.5a4 4 0 1 0 4 0Z"/><circle cx="10" cy="17.5" r="1.4" fill="currentColor" stroke="none"/>',
  matraz: '<path d="M10 3h4M10.5 3v5.2L6 17a2.4 2.4 0 0 0 2.1 3.5h7.8A2.4 2.4 0 0 0 18 17l-4.5-8.8V3"/><path d="M8.2 14.5h7.6"/>',
  descarga: '<path d="M12 4v11.5M7.5 11l4.5 4.5L16.5 11"/><path d="M5 19.5h14"/>',
  refrescar: '<path d="M19 12a7 7 0 1 1-2.3-5.2"/><path d="M19 3v4.5h-4.5"/>',
  alerta: '<path d="M12 3.5 21 19H3L12 3.5Z"/><path d="M12 9.5v4.2"/><circle cx="12" cy="16.7" r="1" fill="currentColor" stroke="none"/>',
};
function icon(nombre, clase) {
  return `<svg class="icono ${clase || ''}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">${ICONS[nombre] || ''}</svg>`;
}

let UMBRAL_BAJO = 30, UMBRAL_ALTO = 55;
const alertState = { tempHigh: false, tankLow: false, humLow: false };
let pollingHandle = null;

function initIconosEstaticos() {
  document.querySelectorAll('[data-icon]').forEach(el => {
    el.innerHTML = icon(el.dataset.icon);
  });
  actualizarIconoTema();
}

function actualizarIconoTema() {
  const cur = document.documentElement.getAttribute('data-theme');
  document.getElementById('btnTema').innerHTML = icon(cur === 'dark' ? 'sol' : 'luna');
}

// Llamada por la app nativa (Android.setTemaInicial(...)) justo después de
// cargar la página, para que el WebView arranque con el mismo tema que
// tiene guardado ThemePrefs en la app. No dispara aviso de vuelta a la app
// porque no es un cambio iniciado por el usuario acá.
function setTemaInicial(tema) {
  document.documentElement.setAttribute('data-theme', tema === 'dark' ? 'dark' : 'light');
  actualizarIconoTema();
}

function toggleTheme() {
  const cur = document.documentElement.getAttribute('data-theme');
  const nuevo = cur === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', nuevo);
  actualizarIconoTema();
  // Avisa a la app nativa para que guarde la preferencia en ThemePrefs
  // (puente SimonaAndroid, ver DashboardActivity.kt). Si se abre este HTML
  // fuera del WebView de la app (ej. navegador de escritorio para debug),
  // SimonaAndroid no existe y se ignora sin romper nada.
  if (window.SimonaAndroid && window.SimonaAndroid.temaCambiado) {
    window.SimonaAndroid.temaCambiado(nuevo);
  }
}

function showToast(message, type = 'normal', duration = 4000) {
  const container = document.getElementById('toastContainer');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `<span>${message}</span>`;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0'; toast.style.transition = 'opacity 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, duration);
}

const CX = 150, CY = 125, R = 85;
function polar(cx, cy, r, angDeg) {
  const rad = (angDeg - 90) * Math.PI / 180;
  return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
}
function valorAAngulo(v) {
  const clamped = Math.max(0, Math.min(100, v));
  return -135 + (clamped / 100) * 270;
}
function arco(cx, cy, r, a0, a1) {
  const p0 = polar(cx, cy, r, a0), p1 = polar(cx, cy, r, a1);
  const largo = (a1 - a0) <= 180 ? 0 : 1;
  return `M ${p0.x} ${p0.y} A ${r} ${r} 0 ${largo} 1 ${p1.x} ${p1.y}`;
}
function dibujarBandas(umbralBajo, umbralAlto) {
  const bandas = [
    { desde: 0, hasta: umbralBajo, color: '#C97A2B' },
    { desde: umbralBajo, hasta: umbralAlto, color: '#8DC035' },
    { desde: umbralAlto, hasta: 100, color: '#1A75B3' },
  ];
  const g = document.getElementById('bandas'); g.innerHTML = '';
  bandas.forEach(b => {
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', arco(CX, CY, R, valorAAngulo(b.desde), valorAAngulo(b.hasta)));
    path.setAttribute('stroke', b.color); path.setAttribute('stroke-width', '18');
    path.setAttribute('fill', 'none'); path.setAttribute('stroke-linecap', 'butt');
    g.appendChild(path);
  });
  const ticksG = document.getElementById('ticks'); ticksG.innerHTML = '';
  [umbralBajo, umbralAlto].forEach(val => {
    const ang = valorAAngulo(val);
    const p1 = polar(CX, CY, R - 9, ang), p2 = polar(CX, CY, R + 9, ang);
    const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    line.setAttribute('x1', p1.x); line.setAttribute('y1', p1.y);
    line.setAttribute('x2', p2.x); line.setAttribute('y2', p2.y);
    line.setAttribute('stroke', '#FFFFFF'); line.setAttribute('stroke-width', '2');
    ticksG.appendChild(line);
  });
  const et = document.getElementById('etiquetasZona'); et.innerHTML = '';
  [[umbralBajo, 'end'], [umbralAlto, 'start']].forEach(([valor, anchor]) => {
    const p = polar(CX, CY, R + 20, valorAAngulo(valor));
    const t = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    t.setAttribute('x', p.x); t.setAttribute('y', p.y + 4);
    t.setAttribute('text-anchor', anchor === 'end' ? 'end' : 'start');
    t.setAttribute('class', 'zona-label'); t.textContent = Math.round(valor) + '%';
    et.appendChild(t);
  });
}

function renderSparkline(arr) {
  if (!arr || arr.length < 2) return;
  const w = 280, h = 45;
  const max = Math.max(...arr, 60), min = Math.min(...arr, 20);
  const range = (max - min) || 1;
  let lastX = 0, lastY = 0;
  const points = arr.map((v, i) => {
    const x = (i / (arr.length - 1)) * w;
    const y = h - ((v - min) / range) * (h - 8) - 4;
    if (i === arr.length - 1) { lastX = x; lastY = y; }
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });
  const pathD = 'M ' + points.join(' L ');
  document.getElementById('sparkPath').setAttribute('d', pathD);
  document.getElementById('sparkArea').setAttribute('d', `${pathD} L ${w},${h} L 0,${h} Z`);
  const dot = document.getElementById('sparkDot');
  dot.setAttribute('cx', lastX.toFixed(1)); dot.setAttribute('cy', lastY.toFixed(1));
  dot.style.display = 'block';
}

function estadoHumedad(v) {
  if (v <= UMBRAL_BAJO) return { texto: 'suelo seco', color: '#C97A2B' };
  if (v >= UMBRAL_ALTO) return { texto: 'suelo húmedo', color: '#1A75B3' };
  return { texto: 'nivel óptimo', color: 'var(--verde)' };
}

async function toggleRiego() {
  try {
    const r = await fetch('/riego_manual', { method: 'POST' });
    const d = await r.json();
    if (!r.ok) { showToast(d.error || 'No se pudo activar el riego', 'danger'); }
    actualizarDashboard();
  } catch (e) { console.error(e); }
}

async function refillTanque() {
  try {
    await fetch('/refill_tanque', { method: 'POST' });
    showToast('Depósito de agua rellenado');
    alertState.tankLow = false;
    actualizarDashboard();
  } catch (e) { console.error(e); }
}

function exportarCSV() {
  window.location.href = '/export_csv';
}

const needle = document.getElementById('needle');

async function actualizarDashboard() {
  try {
    const r = await fetch('/estado');
    if (!r.ok) throw new Error('sin respuesta del dispositivo');
    const d = await r.json();

    document.getElementById('dashNombreHuerta').textContent = d.nombre;
    document.getElementById('dashCategoriaHuerta').textContent = d.categoria;

    if (d.humedadMin !== UMBRAL_BAJO || d.humedadMax !== UMBRAL_ALTO) {
      UMBRAL_BAJO = d.humedadMin; UMBRAL_ALTO = d.humedadMax;
      dibujarBandas(UMBRAL_BAJO, UMBRAL_ALTO);
    }

    const h = d.humedad_suelo;
    needle.setAttribute('transform', `rotate(${valorAAngulo(h)} ${CX} ${CY})`);
    document.getElementById('gaugeValue').textContent = h.toFixed(1);
    const est = estadoHumedad(h);
    document.getElementById('gaugeEstado').textContent = est.texto;
    document.getElementById('gaugeEstado').style.fill = est.color;

    if (d.historial_humedad) renderSparkline(d.historial_humedad);

    if (h <= UMBRAL_BAJO) {
      if (!alertState.humLow) { showToast('Humedad baja detectada', 'warning'); alertState.humLow = true; }
    } else { alertState.humLow = false; }

    document.getElementById('temperatura').textContent = d.temperatura.toFixed(1);
    document.getElementById('luz').textContent = Math.round(d.luminosidad);
    document.getElementById('ph').textContent = d.ph.toFixed(2);
    document.getElementById('tanque').textContent = d.tanque_agua.toFixed(0);

    const fill = document.getElementById('tanqueFill');
    fill.style.width = `${d.tanque_agua}%`;
    if (d.tanque_agua < 20) {
      fill.style.backgroundColor = 'var(--rojo)';
      if (!alertState.tankLow) { showToast('Depósito de agua bajo', 'danger'); alertState.tankLow = true; }
    } else { fill.style.backgroundColor = 'var(--azul)'; }

    if (d.temperatura > d.tempMax) {
      if (!alertState.tempHigh) { showToast(`Temperatura alta (>${d.tempMax}°C)`, 'warning'); alertState.tempHigh = true; }
    } else { alertState.tempHigh = false; }

    const tagTemp = document.getElementById('tagTemp');
    if (d.temperatura > d.tempMax) { tagTemp.textContent = 'Cálido'; tagTemp.style.color = 'var(--marron)'; }
    else if (d.temperatura < d.tempMin) { tagTemp.textContent = 'Fresco'; tagTemp.style.color = 'var(--azul)'; }
    else { tagTemp.textContent = 'Templado'; tagTemp.style.color = 'var(--verde)'; }

    const tagLuz = document.getElementById('tagLuz');
    if (d.luminosidad > d.luzMax) { tagLuz.textContent = 'Alta'; tagLuz.style.color = 'var(--marron)'; }
    else if (d.luminosidad < d.luzMin) { tagLuz.textContent = 'Baja'; tagLuz.style.color = 'var(--marron)'; }
    else { tagLuz.textContent = 'Adecuada'; tagLuz.style.color = 'var(--verde)'; }

    const tagPh = document.getElementById('tagPh');
    if (d.ph >= d.phMin && d.ph <= d.phMax) { tagPh.textContent = 'Ideal'; tagPh.style.color = 'var(--verde)'; }
    else { tagPh.textContent = 'Desviado'; tagPh.style.color = 'var(--marron)'; }

    const riego = document.getElementById('riego');
    const icono = document.getElementById('riegoIcono');
    const titulo = document.getElementById('riegoTitulo');
    const sub = document.getElementById('riegoSub');
    const btn = document.getElementById('btnToggleRiego');

    if (d.riego_activo) {
      riego.className = 'riego-status riego-on';
      icono.innerHTML = icon('gotaFull');
      titulo.textContent = d.riego_manual ? 'Riego forzado' : 'Riego activo';
      sub.textContent = d.riego_manual ? 'Modo manual (15s)' : 'Regando cultivo';
      btn.textContent = 'Detener'; btn.style.background = 'var(--marron)';
    } else {
      riego.className = 'riego-status';
      icono.innerHTML = icon('gotaOff');
      titulo.textContent = 'Riego apagado';
      sub.textContent = d.tanque_agua <= 0 ? 'Sin agua en depósito' : 'Humedad ok';
      btn.textContent = 'Forzar Riego'; btn.style.background = 'var(--azul)';
    }

    const ahora = new Date();
    document.getElementById('ultimaActualizacion').textContent =
      'Actualizado ' + ahora.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  } catch (e) {
    document.getElementById('ultimaActualizacion').textContent = 'Sin datos del dispositivo…';
  }
}

// ================= Arranque =================
initIconosEstaticos();
dibujarBandas(UMBRAL_BAJO, UMBRAL_ALTO);
actualizarDashboard();
pollingHandle = setInterval(actualizarDashboard, 2000);
</script>
</body>
</html>
"""


class SimonaSimHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(f"[esp32-sim] {self.address_string()} - {fmt % args}")

    # ---------- helpers ----------
    def _leer_json_body(self):
        length = int(self.headers.get("Content-Length", 0))
        if length == 0:
            raise ValueError("cuerpo de la petición vacío")
        return json.loads(self.rfile.read(length).decode("utf-8"))

    def _responder_json(self, payload_dict, codigo=200):
        payload = json.dumps(payload_dict).encode("utf-8")
        self.send_response(codigo)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _responder_error(self, codigo, mensaje):
        self._responder_json({"ok": False, "error": mensaje}, codigo)

    def _responder_html(self, html):
        payload = html.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _responder_csv(self):
        with lock:
            registros = list(historial_log)
        output = io.StringIO()
        writer = csv.writer(output)
        writer.writerow(["Timestamp", "Humedad Suelo (%)", "Temperatura (C)", "Luminosidad (lux)", "pH", "Tanque Agua (%)", "Riego Activo"])
        for reg in registros:
            writer.writerow([reg["timestamp"], reg["humedad_suelo"], reg["temperatura"],
                              reg["luminosidad"], reg["ph"], reg["tanque_agua"], reg["riego_activo"]])
        payload = output.getvalue().encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/csv; charset=utf-8")
        self.send_header("Content-Disposition", "attachment; filename=simona_registros.csv")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    # ---------- GET ----------
    def do_GET(self):
        path = self.path.split("?", 1)[0]

        if path in ("/", "/index.html"):
            return self._responder_html(DASHBOARD_HTML)

        if path == "/data":
            # Telemetría liviana para el polling de DashboardActivity.kt
            # (mismo shape que ya espera el código Kotlin existente).
            with lock:
                h = estado
                data = {
                    "humedad_suelo": round(h["humedad_suelo"], 1),
                    "temperatura": round(h["temperatura"], 1),
                    "luminosidad": int(h["luminosidad"]),
                    "ph": round(h["ph"], 2),
                    "riego_activo": h["riego_activo"],
                    "tanque_agua": round(h["tanque_agua"], 1),
                }
            return self._responder_json(data)

        if path == "/estado":
            # Objeto completo (config + estado + historial) para el
            # dashboard HTML propio de este simulador.
            with lock:
                return self._responder_json(dict(estado))

        if path == "/export_csv":
            return self._responder_csv()

        self.send_response(404)
        self.end_headers()

    # ---------- POST ----------
    def do_POST(self):
        path = self.path.split("?", 1)[0]

        if path == "/config":
            return self._actualizar_config()

        if path == "/riego_manual":
            return self._riego_manual()

        if path == "/refill_tanque":
            return self._refill_tanque()

        self.send_response(404)
        self.end_headers()

    # ---------- lógica de negocio ----------
    def _actualizar_config(self):
        """Recibe la config de la huerta desde la app (nombre, categoría
        y los 8 rangos) — el equivalente a lo que en un ESP32 real sería
        el push de configuración al vincular el dispositivo (sección 14,
        'Config push', sin persistencia en NVS). Reinicializa el estado
        simulado en base a la nueva config, igual que un dispositivo que
        recién arranca a medir con sus rangos recién configurados."""
        try:
            body = self._leer_json_body()
        except Exception as e:
            return self._responder_error(400, f"JSON inválido: {e}")

        if not isinstance(body, dict):
            return self._responder_error(400, "El body debe ser un objeto JSON.")

        nombre = str(body.get("nombre", "")).strip()
        if not nombre:
            return self._responder_error(400, "El nombre de la huerta es obligatorio.")

        categoria = str(body.get("categoria", "")).strip() or "Personalizado"

        faltantes = [c for c in CAMPOS_RANGO if c not in body]
        if faltantes:
            return self._responder_error(400, f"Faltan campos de rango: {', '.join(faltantes)}")

        try:
            rangos = {c: float(body[c]) for c in CAMPOS_RANGO}
        except (TypeError, ValueError):
            return self._responder_error(400, "Los rangos deben ser numéricos.")

        for campo_min, campo_max in PARES_MIN_MAX:
            if rangos[campo_min] >= rangos[campo_max]:
                return self._responder_error(
                    400, f"'{campo_min}' ({rangos[campo_min]}) debe ser menor que '{campo_max}' ({rangos[campo_max]})."
                )

        if rangos["humedadMax"] - rangos["humedadMin"] < ANCHO_MINIMO_HUMEDAD:
            return self._responder_error(
                400, f"El rango de humedad debe tener al menos {ANCHO_MINIMO_HUMEDAD:.0f} puntos de diferencia."
            )

        humedad_inicial = round((rangos["humedadMin"] + rangos["humedadMax"]) / 2, 1)

        with lock:
            # Fase 4.2 — decisión consciente: cada POST /config (cada vez
            # que la app se conecta a esta huerta) resetea por completo el
            # estado simulado a valores derivados de los rangos nuevos,
            # incluso si la config entrante es idéntica a la actual. Es
            # coherente con no tener persistencia real (NVS) acá: un ESP32
            # sin NVS tampoco sabría distinguir "es la primera vez" de "ya
            # tenía esta config" — arranca de cero siempre. Se prefiere
            # esto (opción simple y predecible) a comparar la config
            # entrante contra la actual para decidir si resetear, porque
            # para una demo la previsibilidad ("conectar SIEMPRE deja el
            # estado limpio") vale más que preservar estado entre
            # reconexiones de la misma huerta.
            estado.update({
                "nombre": nombre,
                "categoria": categoria,
                **rangos,
                "humedad_suelo": humedad_inicial,
                "temperatura": round((rangos["tempMin"] + rangos["tempMax"]) / 2, 1),
                "luminosidad": int((rangos["luzMin"] + rangos["luzMax"]) / 2),
                "ph": round((rangos["phMin"] + rangos["phMax"]) / 2, 2),
                "tanque_agua": 85.0,
                "riego_activo": False,
                "riego_manual": False,
                "riego_inicio": None,
                "riego_cooldown_hasta": 0,
                "historial_humedad": [humedad_inicial] * 30,
            })
            # El historial en memoria (para /export.csv en vivo) también se
            # reinicia, coherente con lo anterior. El CSV en disco (Fase
            # 4.3, ver historial_log_a_disco()) NO se borra: queda como
            # registro acumulado de todas las sesiones, sobrevive tanto a
            # este reset como a un restart del script.
            historial_log.clear()

        return self._responder_json(dict(estado), 200)

    def _riego_manual(self):
        with lock:
            h = estado
            if h["riego_activo"]:
                h["riego_activo"] = False
                h["riego_manual"] = False
                h["riego_inicio"] = None
                h["riego_cooldown_hasta"] = time.time() + RIEGO_COOLDOWN_SEG
                return self._responder_json({"ok": True, "riego_activo": False})

            if h["tanque_agua"] <= 0:
                return self._responder_error(409, "El tanque de agua está vacío.")

            h["riego_activo"] = True
            h["riego_manual"] = True
            h["riego_inicio"] = time.time()
            return self._responder_json({"ok": True, "riego_activo": True})

    def _refill_tanque(self):
        with lock:
            estado["tanque_agua"] = 100.0
            return self._responder_json({"ok": True, "tanque_agua": 100.0})


if __name__ == "__main__":
    puerto = int(sys.argv[1]) if len(sys.argv) > 1 else 8000

    hilo = threading.Thread(target=actualizar_simulacion, daemon=True)
    hilo.start()

    ThreadingHTTPServer.allow_reuse_address = True
    server = ThreadingHTTPServer(("0.0.0.0", puerto), SimonaSimHandler)
    print(f"Servidor simulado de SIMONA (single-device) corriendo en http://0.0.0.0:{puerto}")
    print(f"Desde el celular conectado al hotspot: http://10.42.0.1:{puerto}")
    print("Simula UN ESP32 (una huerta). La gestión de múltiples huertas vive en la app Android.")
    print(f"Historial persistente (Fase 4.3): {os.path.abspath(CSV_DISCO)}")
    print("Ctrl+C para detener.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nApagando servidor simulado.")
        server.shutdown()
