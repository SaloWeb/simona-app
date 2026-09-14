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
    liviana, la que consume el polling de DetalleHuertaActivity.kt en
    modo conectado) y GET /estado (objeto completo; ya no lo consume
    ningún dashboard HTML propio, se mantiene como endpoint de debug/
    inspección manual).
  - Ejecutar la lógica de riego automático con sus propios umbrales.
  - Aceptar riego manual forzado (POST /riego_manual).
  - Ser SOLO backend: exponer datos (/data, /estado, /export_csv) y
    aceptar comandos (/config, /riego_manual, /refill_tanque). Ya NO
    sirve ningún HTML de dashboard — esa nota quedó desactualizada tras
    el cambio de arquitectura de CONTEXTO_PROYECTO.md (secciones 2/3):
    el análisis visual y los controles ahora viven 100% en
    DetalleHuertaActivity, nativa en la app Android (ver el bloque de
    notas antes de SimonaSimHandler, más abajo en este mismo archivo).

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
# NOTA (cambio de arquitectura, ver CONTEXTO_PROYECTO.md secciones 2/3):
# acá abajo vivía un dashboard HTML completo servido en "/", pensado para
# abrirse en un WebView desde la app. Se eliminó por completo: este
# dispositivo ahora es 100% backend — solo expone datos (/data, /estado,
# /export_csv) y recibe comandos (/config, /riego_manual,
# /refill_tanque). La UI de análisis y control vive ahora en
# DetalleHuertaActivity, pantalla 100% nativa de la app Android.
# =========================================================================
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
            # El dashboard visual se sacó de acá (ver nota más arriba):
            # esto queda como un ping simple de estado, útil para
            # confirmar a mano desde un navegador que el dispositivo
            # está vivo, sin servir HTML de UI.
            return self._responder_json({
                "dispositivo": "SIMONA",
                "ok": True,
                "endpoints": ["/data", "/estado", "/export_csv", "/config", "/riego_manual", "/refill_tanque"],
            })

        if path == "/data":
            # Telemetría liviana para el polling de DetalleHuertaActivity.kt
            # en modo conectado (mismo shape que ya espera el código Kotlin
            # existente).
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
