# SIMONA — App Android

Sistema IoT AgTech de riego automatizado (trabajo práctico grupal). Cada
huerta/cantero tiene su propio dispositivo (ESP32 real o el simulador
incluido en `simulador/`), y la app Android es quien administra **la
flota completa de huertas**, se conecta a demanda a cada dispositivo por
WiFi y muestra tanto su dashboard en vivo como una vista analítica
offline.

## Arquitectura general

- **Un dispositivo = una huerta.** Un ESP32 real nunca gestiona "varias
  huertas" a la vez — cada cantero tiene el suyo, todos bajo el mismo SSID
  WiFi `SIMONA`, distinguidos únicamente por su contraseña de red.
- **La app es la única que conoce la flota completa.** Guarda localmente
  (sin backend) el nombre, categoría, rangos óptimos, contraseña y
  posición/foto de cada huerta (`HuertaRepository`, SharedPreferences +
  JSON manual — sin Room ni dependencias externas).
- **Conexión acotada y a demanda.** Al abrir una huerta puntual, la app se
  conecta *solo* a esa red WiFi (`WifiConnectionManager`, vía
  `ConnectivityManager.requestNetwork()` + `WifiNetworkSpecifier`, sin
  promoverla a red por defecto del celular) y le empuja su configuración
  (`POST /config`) antes de mostrar el dashboard — el dispositivo no
  persiste nada entre conexiones.
- **Todo lo demás es 100% offline.** El home (`MapaHuertasActivity`), la
  ficha de detalle, las alertas y el asistente de IA funcionan sin estar
  conectado al WiFi de ninguna huerta, leyendo siempre la última lectura
  guardada localmente.

## Funcionalidades principales

- **Gestión multi-huerta offline** (`MapaHuertasActivity`, home de la
  app): resumen con chips (total / con sed / óptimas), lista en tarjetas
  y vista de mapa/croquis del predio con pines por huerta; crear, editar,
  eliminar, foto opcional por huerta.
- **Alta de huerta guiada en 3 pasos**: elegir perfil de cultivo de
  partida (`SeleccionarPerfilActivity`, 9 perfiles predefinidos) → ajustar
  los 8 rangos por slider o texto (`AjustarRangosActivity`) → nombre,
  contraseña de red, posición en el croquis y foto opcional
  (`DatosHuertaActivity`).
- **Dashboard en vivo** (`DashboardActivity`, WebView a pantalla completa
  contra el ESP32/simulador de esa huerta): gauges, tendencia, riego
  manual, refill de tanque; con reintentos automáticos si el dispositivo
  recién está reiniciando y confirmación antes de salir (para no cortar
  la conexión sin querer).
- **Ficha de detalle offline** (`DetalleHuertaActivity`): vista analítica
  de una huerta sin necesitar estar conectado, con un mini-mapa
  multicapa (`MiniMapaCapasView`: Humedad / pH / Temperatura / Luz, con
  colores y umbrales reales, pin dinámico y etiqueta de ubicación libre).
- **Asistente de IA "Simona"** (`AsistenteGemini`, chat flotante): llama a
  la API de Gemini directo desde la app, usando la conexión a internet
  normal del celular en paralelo a la red WiFi acotada de la huerta (sin
  servidor intermedio).
- **Alertas de huertas con sed** (`AlertaHuertasWorker`, WorkManager cada
  6 hs): notifica si alguna huerta sigue por debajo de su humedad mínima,
  releyendo el último dato persistido, sin reconectarse a ningún ESP32.

## Estructura

```
app/src/main/java/com/simona/app/
├── SimonaApp.kt                 (Application: instancias únicas de
│                                  WifiConnectionManager y HuertaRepository,
│                                  canal de notificaciones, worker periódico)
├── SplashActivity.kt             (launcher, Splash Screen API estándar)
├── MapaHuertasActivity.kt        (home: lista + mapa + gestión de huertas)
├── DetalleHuertaActivity.kt      (ficha offline con mini-mapa multicapa)
├── MiniMapaCapasView.kt          (vista custom del mini-mapa multicapa)
├── SeleccionarPerfilActivity.kt  (alta de huerta, paso 1: perfil)
├── AjustarRangosActivity.kt      (alta de huerta, paso 2: rangos)
├── DatosHuertaActivity.kt        (alta de huerta, paso 3: nombre/pass/foto)
├── PerfilCultivo.kt              (9 perfiles de cultivo predefinidos)
├── PerfilCultivoAdapter.kt       (adapter del grid de perfiles)
├── Huerta.kt                     (modelo Huerta + LecturaHuerta)
├── HuertaRepository.kt           (persistencia local en SharedPreferences)
├── TutorialConexionActivity.kt   (conexión WiFi a una huerta + push config)
├── WifiConnectionManager.kt      (conexión acotada por huerta, RSSI)
├── DashboardActivity.kt          (WebView del dashboard + puente al chat IA)
├── AsistenteGemini.kt            (llamadas a la API de Gemini)
├── AlertaHuertasWorker.kt        (chequeo periódico de huertas con sed)
├── FotoHuertaUtil.kt             (utilidades de la foto opcional)
└── ConnectionState.kt            (sealed class de estados de conexión)

simulador/servidor_simulado.py   (simula UN ESP32/huerta — demo/desarrollo)
```

## Cómo compilar (sin Android Studio, con VS Code + terminal)

Este proyecto no depende del IDE para nada — Android Studio es solo una capa
visual sobre Gradle y el SDK de línea de comandos. Con lo siguiente alcanza:

### 1. Instalar lo necesario

- **JDK 17** (temurin/openjdk). Verificar con `java -version`.
- **Android SDK Command Line Tools** (sin el IDE completo):
  - Descargar el zip "Command line tools only" desde
    https://developer.android.com/studio#command-line-tools-only
  - Descomprimir en, por ejemplo, `~/Android/Sdk/cmdline-tools/latest/`
    (la carpeta `latest` debe contener directamente `bin/`, `lib/`, etc.)
- **Gradle** (no hace falta wrapper si tenés Gradle instalado globalmente):
  Linux: `sdk install gradle` (con [SDKMAN](https://sdkman.io/)) o el paquete
  de tu distro. Verificar con `gradle -v` (necesitás Gradle 8.5+).

### 2. Instalar los paquetes del SDK que pide este proyecto

```bash
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

### 3. Configurar el proyecto

```bash
cp local.properties.example local.properties
# Editar local.properties y poner la ruta real a tu SDK (sdk.dir=...)
```

**Antes de compilar**, completar tu API key real de Gemini en
`app/src/main/res/values/secrets.xml` (`gemini_api_key`) — ver la
sección **Seguridad** más abajo, es importante hacerlo bien.

Las contraseñas de red ya **no** se configuran a nivel de proyecto: cada
huerta guarda la suya propia al crearse (`Huerta.kt` /
`DatosHuertaActivity`), no hay ningún valor fijo que editar en el código.

### 4. Generar el wrapper de Gradle (una sola vez, recomendado)

Así el proyecto queda con una versión de Gradle fija y no dependés de la que
tengas instalada globalmente cada vez:

```bash
gradle wrapper --gradle-version 8.7
```

Esto crea `gradlew`, `gradlew.bat` y `gradle/wrapper/gradle-wrapper.jar`.
De ahí en adelante, usá `./gradlew` en vez de `gradle` en los comandos de
abajo.

### 5. Compilar el APK de debug

```bash
./gradlew assembleDebug
# o, sin wrapper generado: gradle assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

### 6. Instalar en el celular por USB (depuración habilitada)

```bash
adb devices          # confirmar que el celular aparece listado
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Los emuladores no manejan bien las conexiones WiFi reales, así que para
probar el flujo de conexión con el ESP32 necesitás sí o sí un dispositivo
físico.

### 7. APK firmado (release), cuando esté listo para entregar

```bash
./gradlew assembleRelease
```

Sale sin firmar por defecto; para firmarlo se puede usar `apksigner`
(incluido en `build-tools`) con un keystore generado con `keytool`.

## Modo demo vs. modo hardware real

La app no habla directo con una IP fija: `resolverUrl()`
(`WifiConnectionManager.kt`, usado desde `DashboardActivity.kt` y
`TutorialConexionActivity.kt`) arma la URL del dashboard tomando el
**esquema y el puerto** de `dashboard_url` (`res/values/strings.xml`) y
reemplazando el **host** en tiempo de ejecución por el gateway real de la
red WiFi a la que se conectó la app — así la misma app funciona sin
recompilar sin importar si el gateway resultó ser `10.42.0.1`,
`192.168.4.1` u otra IP (notebook, hotspot, ESP32).

Lo que sí depende del entorno es el **puerto**, y eso está en una única
constante en `strings.xml`, comentada con instrucciones:

- **Modo demo** (`simulador/servidor_simulado.py`, corre con puerto
  explícito): `dashboard_url = http://10.42.0.1:8000`
- **Modo hardware real** (ESP32, sirve por HTTP estándar, puerto 80
  implícito): `dashboard_url = http://192.168.4.1` (sin `:puerto`)

Antes de compilar, dejar activa (sin comentar) la línea que corresponda al
entorno de esa build — la otra queda comentada al lado, a modo de
referencia. No hace falta tocar ningún archivo `.kt` para este cambio.

Para probar en modo demo, levantar el simulador (representa un solo
ESP32/huerta, sin dependencias externas):

```bash
python3 simulador/servidor_simulado.py 8000
```


## Decisiones de diseño no triviales

- **`WifiConnectionManager` y `HuertaRepository` como instancia única a
  nivel de `Application`** (`SimonaApp.kt`), no por Activity:
  `bindProcessToNetwork()` ata la red a nivel de *proceso*, no de
  Activity — si cada pantalla tuviera su propia instancia, no podría
  liberar correctamente la conexión que otra originó. Mismo criterio para
  el repositorio: todas las pantallas deben leer/escribir siempre el
  mismo estado persistido.
- **El asistente de IA fuerza su propia red de internet** en paralelo a la
  red WiFi acotada de la huerta (`ConnectivityManager.requestNetwork()` +
  `Network.openConnection()` en `AsistenteGemini.kt`), porque el proceso
  ya está atado a la red de la huerta (sin internet) mientras el
  dashboard está abierto.
- **El simulador representa un solo ESP32/huerta**, sin CRUD multi-huerta:
  la flota completa la gestiona únicamente la app (fiel al comportamiento
  de un dispositivo real, que nunca "sabe" de otras huertas).
- **Cada `POST /config` resetea el estado simulado por completo**, sin
  comparar contra la config anterior — decisión consciente por
  simplicidad y previsibilidad en la demo (ver comentario en
  `servidor_simulado.py`, función `_actualizar_config`).

---

**Nota sobre este documento:** existe también un `app/README.md` con
contenido parecido pero desactualizado (todavía describe el flujo viejo de
una sola huerta con `MainActivity`). Conviene mantener un único README en
la raíz del proyecto — se puede borrar `app/README.md` o dejarlo como un
enlace corto a este archivo, para no tener dos versiones que se
desincronizan entre sí.
