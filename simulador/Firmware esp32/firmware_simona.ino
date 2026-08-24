/*
  ============================================================================
  SIMONA — Firmware ESP32 (una huerta por dispositivo)
  ============================================================================
  Contraparte real de simulador/servidor_simulado.py. Expone los MISMOS
  endpoints HTTP, con el mismo "shape" de JSON, para que la app Android
  (WifiConnectionManager, DashboardActivity) y el propio dashboard HTML no
  necesiten saber si están hablando con el simulador o con el hardware real.

  Responsabilidades de este firmware (sección 2 y 5 del Plan de Desarrollo):
    - Levantar un Access Point WiFi propio (SSID "SIMONA", fijo y compartido
      por todas las huertas; lo que distingue a ESTE dispositivo es SU
      contraseña — ver HUERTA_WIFI_PASSWORD más abajo).
    - Servir el dashboard HTML en GET / (mismo archivo que ve el simulador).
    - Medir sensores reales cada 1 segundo y exponerlos en /data y /estado.
    - Ejecutar la lógica de riego automático por histéresis, con corte de
      seguridad por tiempo y cooldown entre ciclos — TOTALMENTE independiente
      del teléfono: si se corta el WiFi, el riego sigue decidiéndose acá.
    - Aceptar POST /config (config push desde la app al conectar) — SIN
      persistencia en NVS, deliberado (ver sección 9 del Plan): cada
      conexión nueva de la app entrega la config de nuevo, así que no hace
      falta guardarla en flash.
    - Aceptar riego manual forzado y relleno de tanque (POST).

  Dependencias (Arduino IDE → Administrador de Librerías):
    - Core "ESP32" de Espressif (WiFi.h, WebServer.h, Preferences.h ya
      incluidas en el core, no hace falta instalar nada aparte para esto).
    - "DHT sensor library" de Adafruit + "Adafruit Unified Sensor"
      (única dependencia externa real, para el sensor de temperatura DHT11 —
      mismo criterio de "cero dependencias externas salvo lo estrictamente
      necesario" que sigue la app Android).

  Hardware esperado (ver README.md para el diagrama de conexionado):
    - Sensor de humedad de suelo capacitivo   -> PIN_HUMEDAD (ADC1, GPIO34)
    - DHT11 (temperatura)                     -> PIN_DHT      (GPIO4)
    - LDR / fotorresistencia (divisor de tensión) -> PIN_LDR  (ADC1, GPIO35)
    - Sensor de pH analógico (módulo genérico) -> PIN_PH      (ADC1, GPIO33)
    - Relé de la bomba de riego               -> PIN_RELE     (GPIO26)
    - LED de estado (opcional, LED interno)   -> PIN_LED      (GPIO2)

  Nota de fidelidad con el simulador: NO hay sensor físico de nivel de
  tanque documentado en el Plan — igual que el simulador, "tanque_agua" es
  un contador de software que baja mientras riega y se resetea a 100 con
  POST /refill_tanque. Si en el futuro se agrega un sensor ultrasónico o de
  flotante, alcanza con reemplazar leerTanqueAgua() por la lectura real.
  ============================================================================
*/

#include <WiFi.h>
#include <WebServer.h>
#include <DHT.h>
#include "dashboard_html.h"

// ============================================================================
// 1) IDENTIDAD DE ESTA HUERTA (lo único que distingue a este ESP32 de otro)
// ============================================================================
// SSID fijo, igual para todas las huertas (así lo espera WifiConnectionManager
// en la app, que arma un WifiNetworkSpecifier con SSID "SIMONA" + la
// contraseña particular de la huerta elegida).
static const char *AP_SSID = "SIMONA";

// Contraseña PROPIA de esta huerta/dispositivo — es lo que la app guarda en
// HuertaRepository al dar de alta esta huerta (Paso 3, DatosHuertaActivity).
// Cambiar este valor es lo único necesario para "clonar" el firmware a otro
// ESP32 de otra huerta distinta.
static const char *AP_PASSWORD = "huerta1234"; // mínimo 8 caracteres (WPA2)

// Canal WiFi fijo del AP (evita saltos de canal que compliquen el escaneo
// desde la app). IP resultante por defecto del modo AP de ESP32: 192.168.4.1
// (coincide con lo documentado en la sección 5.2 del Plan de Desarrollo).
static const int AP_CANAL = 6;
static const int AP_MAX_CONEXIONES = 2; // el celular + margen de reintento

// ============================================================================
// 2) PINES DE HARDWARE
// ============================================================================
#define PIN_HUMEDAD 34   // ADC1_CH6 — sensor capacitivo de humedad de suelo
#define PIN_LDR     35   // ADC1_CH7 — LDR en divisor de tensión
#define PIN_PH      33   // ADC1_CH5 — módulo de sensor de pH
#define PIN_DHT     4    // GPIO digital — DHT11 (temperatura)
#define PIN_RELE    26   // GPIO digital — relé de la bomba de riego
#define PIN_LED     2    // LED de estado (parpadea mientras riega)

#define DHTTIPO DHT11
DHT dht(PIN_DHT, DHTTIPO);

// El relé activo-bajo es común en módulos de 1 canal (LOW = bobina
// energizada = bomba encendida). Si tu módulo es activo-alto, invertir
// estas dos constantes alcanza para adaptar todo el firmware.
#define RELE_ENCENDIDO LOW
#define RELE_APAGADO   HIGH

// ============================================================================
// 3) PARÁMETROS DE SIMULACIÓN/CONTROL (idénticos a servidor_simulado.py,
//    sección 14.3/14.7 del Plan — mismos números, para que una demo se
//    comporte igual sea con el simulador o con el hardware real)
// ============================================================================
static const float ANCHO_MINIMO_HUMEDAD   = 10.0f;  // misma validación que AjustarRangosActivity
static const unsigned long RIEGO_DURACION_MAX_MS   = 30000; // corte de seguridad automático
static const unsigned long RIEGO_MANUAL_DURACION_MS = 15000; // duración fija en modo manual
static const unsigned long RIEGO_COOLDOWN_MS        = 10000; // cooldown para la demo
static const int HISTORIAL_LEN = 30;      // puntos de la sparkline (igual que el simulador)
static const int HISTORIAL_LOG_MAX = 500; // tope del log en RAM (para /export_csv)
static const unsigned long LECTURA_INTERVALO_MS = 1000; // 1 lectura/seg, igual al simulador

// ============================================================================
// 4) ESTRUCTURAS DE ESTADO
// ============================================================================
struct HuertaConfig {
  String nombre    = "Mi Huerta";
  String categoria = "Fruto";
  float humedadMin = 35.0f, humedadMax = 60.0f;
  float phMin      = 6.0f,  phMax      = 6.8f;
  float luzMin     = 500.0f, luzMax    = 850.0f;
  float tempMin    = 18.0f, tempMax    = 28.0f;
};

struct EstadoSimona {
  float humedad_suelo   = 47.5f;
  float temperatura     = 23.0f;
  int   luminosidad     = 675;
  float ph              = 6.4f;
  float tanque_agua     = 85.0f;
  bool  riego_activo    = false;
  bool  riego_manual    = false;
  unsigned long riego_inicio_ms        = 0;
  unsigned long riego_cooldown_hasta_ms = 0;
  float historial_humedad[HISTORIAL_LEN];
};

struct RegistroLog {
  unsigned long timestamp_ms;
  float humedad_suelo;
  float temperatura;
  int   luminosidad;
  float ph;
  float tanque_agua;
  bool  riego_activo;
};

HuertaConfig config;
EstadoSimona estado;
RegistroLog historialLog[HISTORIAL_LOG_MAX];
int historialLogCount = 0; // cantidad ocupada (buffer circular simple)
int historialLogHead  = 0; // próxima posición a escribir

WebServer server(80);
unsigned long ultimaLecturaMs = 0;

// ============================================================================
// 5) LECTURA DE SENSORES REALES
// ============================================================================
// Los sensores analógicos de bajo costo (capacitivo, LDR, pH genérico) varían
// bastante de unidad a unidad. Estas funciones mapean el valor crudo del ADC
// (0-4095, ESP32 = 12 bits) a la escala documentada en la sección 3 del Plan.
// AJUSTAR los valores CAL_* contra tu sensor real antes de un uso productivo
// (mismo llamado de atención que hace el Plan en su nota metodológica).

// Calibración sensor de humedad capacitivo: cuanto más seco, mayor voltaje.
static const int CAL_HUMEDAD_SECO  = 3000; // lectura ADC al aire (0% humedad)
static const int CAL_HUMEDAD_MOJADO = 1200; // lectura ADC en agua (100% humedad)

float leerHumedadSuelo() {
  int crudo = analogRead(PIN_HUMEDAD);
  float pct = (float)(CAL_HUMEDAD_SECO - crudo) * 100.0f / (CAL_HUMEDAD_SECO - CAL_HUMEDAD_MOJADO);
  return constrain(pct, 0.0f, 100.0f);
}

float leerTemperatura() {
  float t = dht.readTemperature();
  if (isnan(t)) return estado.temperatura; // sensor no respondió: conservar último valor válido
  return t;
}

// LDR en escala 0-950 "lux" simplificada (etiquetada así en el dashboard por
// simplicidad de UI, no calibrada contra un luxómetro real — ver sección 3).
int leerLuminosidad() {
  int crudo = analogRead(PIN_LDR); // 0 (oscuridad) .. 4095 (luz plena), según cableado
  long escalado = map(crudo, 0, 4095, 0, 950);
  return (int)constrain(escalado, 0L, 950L);
}

// Sensor de pH genérico: fórmula lineal típica de módulos de bajo costo.
// CAL_PH_OFFSET y CAL_PH_PENDIENTE deben ajustarse con soluciones buffer
// (pH 4.0 / 7.0 / 10.0) para una calibración real.
static const float CAL_PH_PENDIENTE = -0.0018f;
static const float CAL_PH_OFFSET    = 8.6f;

float leerPh() {
  int crudo = analogRead(PIN_PH);
  float ph = CAL_PH_OFFSET + CAL_PH_PENDIENTE * crudo;
  return constrain(ph, 0.0f, 14.0f);
}

// Sin sensor físico de nivel de tanque documentado (ver nota al inicio del
// archivo): se mantiene como contador de software, igual que el simulador.
float leerTanqueAgua() {
  return estado.tanque_agua;
}

// ============================================================================
// 6) LÓGICA DE RIEGO POR HISTÉRESIS (idéntica a actualizar_simulacion() del
//    simulador, pero accionando el relé real en vez de simular el tanque)
// ============================================================================
void actualizarLecturasYRiego() {
  unsigned long ahora = millis();

  estado.temperatura   = leerTemperatura();
  estado.luminosidad   = leerLuminosidad();
  estado.ph            = leerPh();
  estado.humedad_suelo = leerHumedadSuelo();

  if (estado.riego_activo) {
    // Mientras riega: el tanque baja (si hay sensor real, acá se
    // reemplazaría por la lectura directa en vez de restar un estimado).
    if (estado.tanque_agua > 0.0f) {
      estado.tanque_agua = max(0.0f, estado.tanque_agua - 0.6f);
    } else {
      estado.riego_activo = false;
      estado.riego_manual = false;
    }

    unsigned long tiempoRegandoMs = ahora - estado.riego_inicio_ms;
    bool corteHumedad, corteTiempo;

    if (estado.riego_manual) {
      corteHumedad = estado.humedad_suelo >= 80.0f;
      corteTiempo  = tiempoRegandoMs >= RIEGO_MANUAL_DURACION_MS;
    } else {
      corteHumedad = estado.humedad_suelo >= config.humedadMax;
      corteTiempo  = tiempoRegandoMs >= RIEGO_DURACION_MAX_MS;
    }

    if (corteHumedad || corteTiempo || estado.tanque_agua <= 0.0f) {
      apagarRiego(ahora);
    }
  } else {
    if (estado.humedad_suelo <= config.humedadMin
        && ahora >= estado.riego_cooldown_hasta_ms
        && estado.tanque_agua > 0.0f) {
      encenderRiego(ahora, /*manual=*/false);
    }
  }

  // Historial para la sparkline (últimos HISTORIAL_LEN puntos)
  static int histIdx = 0;
  estado.historial_humedad[histIdx] = round2(estado.humedad_suelo);
  histIdx = (histIdx + 1) % HISTORIAL_LEN;

  // Log completo para /export_csv (buffer circular en RAM)
  RegistroLog r;
  r.timestamp_ms   = ahora;
  r.humedad_suelo  = round2(estado.humedad_suelo);
  r.temperatura    = round2(estado.temperatura);
  r.luminosidad    = estado.luminosidad;
  r.ph             = round2(estado.ph);
  r.tanque_agua    = round2(estado.tanque_agua);
  r.riego_activo   = estado.riego_activo;
  historialLog[historialLogHead] = r;
  historialLogHead = (historialLogHead + 1) % HISTORIAL_LOG_MAX;
  if (historialLogCount < HISTORIAL_LOG_MAX) historialLogCount++;

  digitalWrite(PIN_LED, estado.riego_activo ? HIGH : LOW);
}

void encenderRiego(unsigned long ahoraMs, bool manual) {
  estado.riego_activo    = true;
  estado.riego_manual    = manual;
  estado.riego_inicio_ms = ahoraMs;
  digitalWrite(PIN_RELE, RELE_ENCENDIDO);
}

void apagarRiego(unsigned long ahoraMs) {
  estado.riego_activo             = false;
  estado.riego_manual             = false;
  estado.riego_inicio_ms          = 0;
  estado.riego_cooldown_hasta_ms  = ahoraMs + RIEGO_COOLDOWN_MS;
  digitalWrite(PIN_RELE, RELE_APAGADO);
}

float round2(float v) {
  return roundf(v * 100.0f) / 100.0f;
}

// ============================================================================
// 7) SERIALIZACIÓN JSON (a mano, sin librería externa — mismo criterio de
//    "cero dependencias" del resto del proyecto; los payloads son chicos y
//    de forma fija, así que no hace falta ArduinoJson acá)
// ============================================================================
String jsonEstadoCompleto() {
  String j = "{";
  j += "\"nombre\":\"" + escapeJson(config.nombre) + "\",";
  j += "\"categoria\":\"" + escapeJson(config.categoria) + "\",";
  j += "\"humedadMin\":" + String(config.humedadMin, 1) + ",";
  j += "\"humedadMax\":" + String(config.humedadMax, 1) + ",";
  j += "\"phMin\":" + String(config.phMin, 2) + ",";
  j += "\"phMax\":" + String(config.phMax, 2) + ",";
  j += "\"luzMin\":" + String(config.luzMin, 0) + ",";
  j += "\"luzMax\":" + String(config.luzMax, 0) + ",";
  j += "\"tempMin\":" + String(config.tempMin, 1) + ",";
  j += "\"tempMax\":" + String(config.tempMax, 1) + ",";
  j += "\"humedad_suelo\":" + String(estado.humedad_suelo, 1) + ",";
  j += "\"temperatura\":" + String(estado.temperatura, 1) + ",";
  j += "\"luminosidad\":" + String(estado.luminosidad) + ",";
  j += "\"ph\":" + String(estado.ph, 2) + ",";
  j += "\"tanque_agua\":" + String(estado.tanque_agua, 1) + ",";
  j += "\"riego_activo\":" + String(estado.riego_activo ? "true" : "false") + ",";
  j += "\"riego_manual\":" + String(estado.riego_manual ? "true" : "false") + ",";
  j += "\"historial_humedad\":[";
  for (int i = 0; i < HISTORIAL_LEN; i++) {
    j += String(estado.historial_humedad[i], 1);
    if (i < HISTORIAL_LEN - 1) j += ",";
  }
  j += "]}";
  return j;
}

// Telemetría liviana: mismo shape que ya consume el polling nativo de
// DashboardActivity.kt (sección 5.3 del Plan) — subconjunto de /estado.
String jsonDataLiviano() {
  String j = "{";
  j += "\"humedad_suelo\":" + String(estado.humedad_suelo, 1) + ",";
  j += "\"temperatura\":" + String(estado.temperatura, 1) + ",";
  j += "\"luminosidad\":" + String(estado.luminosidad) + ",";
  j += "\"ph\":" + String(estado.ph, 2) + ",";
  j += "\"riego_activo\":" + String(estado.riego_activo ? "true" : "false") + ",";
  j += "\"tanque_agua\":" + String(estado.tanque_agua, 1);
  j += "}";
  return j;
}

String escapeJson(const String &s) {
  String out;
  for (size_t i = 0; i < s.length(); i++) {
    char c = s[i];
    if (c == '"' || c == '\\') out += '\\';
    out += c;
  }
  return out;
}

// Extrae "campo":valor de un JSON plano recibido por POST (parser mínimo,
// suficiente para el body fijo que manda TutorialConexionActivity — no es
// un parser JSON general).
bool extraerNumero(const String &body, const String &campo, float &out) {
  String buscar = "\"" + campo + "\"";
  int idx = body.indexOf(buscar);
  if (idx < 0) return false;
  int dosPuntos = body.indexOf(':', idx);
  if (dosPuntos < 0) return false;
  int fin = dosPuntos + 1;
  while (fin < (int)body.length() && (body[fin] == ' ')) fin++;
  int inicio = fin;
  while (fin < (int)body.length() && (isDigit(body[fin]) || body[fin] == '-' || body[fin] == '.')) fin++;
  if (fin == inicio) return false;
  out = body.substring(inicio, fin).toFloat();
  return true;
}

bool extraerTexto(const String &body, const String &campo, String &out) {
  String buscar = "\"" + campo + "\"";
  int idx = body.indexOf(buscar);
  if (idx < 0) return false;
  int dosPuntos = body.indexOf(':', idx);
  int comilla1 = body.indexOf('"', dosPuntos + 1);
  if (comilla1 < 0) return false;
  int comilla2 = body.indexOf('"', comilla1 + 1);
  if (comilla2 < 0) return false;
  out = body.substring(comilla1 + 1, comilla2);
  return true;
}

// ============================================================================
// 8) HANDLERS HTTP — mismos paths, mismos códigos de error que el simulador
// ============================================================================
void handleCORS() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
}

void handleRoot() {
  handleCORS();
  server.send_P(200, "text/html; charset=utf-8", DASHBOARD_HTML);
}

void handleData() {
  handleCORS();
  server.send(200, "application/json", jsonDataLiviano());
}

void handleEstado() {
  handleCORS();
  server.send(200, "application/json", jsonEstadoCompleto());
}

void handleExportCsv() {
  handleCORS();
  String csv = "Timestamp (ms),Humedad Suelo (%),Temperatura (C),Luminosidad (lux),pH,Tanque Agua (%),Riego Activo\n";
  int idx = (historialLogHead - historialLogCount + HISTORIAL_LOG_MAX) % HISTORIAL_LOG_MAX;
  for (int i = 0; i < historialLogCount; i++) {
    RegistroLog &r = historialLog[idx];
    csv += String(r.timestamp_ms) + "," + String(r.humedad_suelo, 1) + "," +
           String(r.temperatura, 1) + "," + String(r.luminosidad) + "," +
           String(r.ph, 2) + "," + String(r.tanque_agua, 1) + "," +
           (r.riego_activo ? "SI" : "NO") + "\n";
    idx = (idx + 1) % HISTORIAL_LOG_MAX;
  }
  server.sendHeader("Content-Disposition", "attachment; filename=simona_registros.csv");
  server.send(200, "text/csv; charset=utf-8", csv);
}

void responderError(int codigo, const String &mensaje) {
  handleCORS();
  server.send(codigo, "application/json", "{\"ok\":false,\"error\":\"" + escapeJson(mensaje) + "\"}");
}

// POST /config — "config push" del Paso 4 (TutorialConexionActivity).
// Sin persistencia en NVS: se aplica en RAM y se resetea el estado simulado,
// igual criterio que servidor_simulado.py._actualizar_config().
void handleConfig() {
  if (!server.hasArg("plain")) {
    return responderError(400, "cuerpo de la petición vacío");
  }
  String body = server.arg("plain");

  String nombre, categoria;
  if (!extraerTexto(body, "nombre", nombre) || nombre.length() == 0) {
    return responderError(400, "El nombre de la huerta es obligatorio.");
  }
  if (!extraerTexto(body, "categoria", categoria) || categoria.length() == 0) {
    categoria = "Personalizado";
  }

  float humedadMin, humedadMax, phMin, phMax, luzMin, luzMax, tempMin, tempMax;
  bool ok = extraerNumero(body, "humedadMin", humedadMin)
         && extraerNumero(body, "humedadMax", humedadMax)
         && extraerNumero(body, "phMin", phMin)
         && extraerNumero(body, "phMax", phMax)
         && extraerNumero(body, "luzMin", luzMin)
         && extraerNumero(body, "luzMax", luzMax)
         && extraerNumero(body, "tempMin", tempMin)
         && extraerNumero(body, "tempMax", tempMax);
  if (!ok) {
    return responderError(400, "Faltan campos de rango o no son numéricos.");
  }

  if (humedadMin >= humedadMax || phMin >= phMax || luzMin >= luzMax || tempMin >= tempMax) {
    return responderError(400, "Cada rango 'min' debe ser menor que su 'max'.");
  }
  if (humedadMax - humedadMin < ANCHO_MINIMO_HUMEDAD) {
    return responderError(400, "El rango de humedad debe tener al menos 10 puntos de diferencia.");
  }

  config.nombre     = nombre;
  config.categoria  = categoria;
  config.humedadMin = humedadMin; config.humedadMax = humedadMax;
  config.phMin      = phMin;      config.phMax      = phMax;
  config.luzMin     = luzMin;     config.luzMax     = luzMax;
  config.tempMin     = tempMin;   config.tempMax     = tempMax;

  // Reinicio del estado simulado en base a la nueva config, igual que el
  // simulador (previsibilidad > preservar estado entre reconexiones).
  float humedadInicial = (humedadMin + humedadMax) / 2.0f;
  estado.humedad_suelo = humedadInicial;
  estado.temperatura   = (tempMin + tempMax) / 2.0f;
  estado.luminosidad   = (int)((luzMin + luzMax) / 2.0f);
  estado.ph             = (phMin + phMax) / 2.0f;
  estado.tanque_agua    = 85.0f;
  apagarRiego(millis());
  estado.riego_cooldown_hasta_ms = 0; // sin cooldown heredado al recién conectar
  for (int i = 0; i < HISTORIAL_LEN; i++) estado.historial_humedad[i] = humedadInicial;

  historialLogCount = 0;
  historialLogHead  = 0;

  handleCORS();
  server.send(200, "application/json", jsonEstadoCompleto());
}

void handleRiegoManual() {
  unsigned long ahora = millis();
  if (estado.riego_activo) {
    apagarRiego(ahora);
    handleCORS();
    server.send(200, "application/json", "{\"ok\":true,\"riego_activo\":false}");
    return;
  }
  if (estado.tanque_agua <= 0.0f) {
    return responderError(409, "El tanque de agua está vacío.");
  }
  encenderRiego(ahora, /*manual=*/true);
  handleCORS();
  server.send(200, "application/json", "{\"ok\":true,\"riego_activo\":true}");
}

void handleRefillTanque() {
  estado.tanque_agua = 100.0f;
  handleCORS();
  server.send(200, "application/json", "{\"ok\":true,\"tanque_agua\":100.0}");
}

void handleOptions() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.sendHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  server.sendHeader("Access-Control-Allow-Headers", "Content-Type");
  server.send(200);
}

void handleNotFound() {
  handleCORS();
  server.send(404, "text/plain", "No encontrado");
}

// ============================================================================
// 9) SETUP / LOOP
// ============================================================================
void setup() {
  Serial.begin(115200);
  delay(300);

  pinMode(PIN_RELE, OUTPUT);
  digitalWrite(PIN_RELE, RELE_APAGADO);
  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_LED, LOW);

  analogReadResolution(12); // 0-4095, explícito por si el core cambia el default

  dht.begin();

  for (int i = 0; i < HISTORIAL_LEN; i++) estado.historial_humedad[i] = estado.humedad_suelo;

  WiFi.mode(WIFI_AP);
  WiFi.softAP(AP_SSID, AP_PASSWORD, AP_CANAL, /*ssid_hidden=*/0, AP_MAX_CONEXIONES);
  IPAddress ip = WiFi.softAPIP(); // por defecto 192.168.4.1

  Serial.println("========================================");
  Serial.println("SIMONA firmware — un ESP32, una huerta");
  Serial.print("AP SSID: "); Serial.println(AP_SSID);
  Serial.print("IP del dashboard: http://"); Serial.println(ip);
  Serial.println("========================================");

  server.on("/", HTTP_GET, handleRoot);
  server.on("/data", HTTP_GET, handleData);
  server.on("/estado", HTTP_GET, handleEstado);
  server.on("/export_csv", HTTP_GET, handleExportCsv);
  server.on("/config", HTTP_POST, handleConfig);
  server.on("/riego_manual", HTTP_POST, handleRiegoManual);
  server.on("/refill_tanque", HTTP_POST, handleRefillTanque);
  server.on("/", HTTP_OPTIONS, handleOptions);
  server.onNotFound(handleNotFound);
  server.begin();

  ultimaLecturaMs = millis();
}

void loop() {
  server.handleClient();

  unsigned long ahora = millis();
  if (ahora - ultimaLecturaMs >= LECTURA_INTERVALO_MS) {
    ultimaLecturaMs = ahora;
    actualizarLecturasYRiego();
  }
}
