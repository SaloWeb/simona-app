# CONTEXTO_PROYECTO.md — SIMONA (leer esto primero)

> **Propósito de este archivo:** dar a cualquier persona o IA que entre a
> este repo por primera vez todo el contexto necesario para entender el
> proyecto de punta a punta SIN tener que leer los ~20 archivos Kotlin uno
> por uno. Es un documento **vivo**: cada vez que cambie algo importante
> del proyecto (arquitectura, bugs conocidos, features nuevas, decisiones
> de diseño), este archivo se tiene que actualizar en el mismo cambio,
> no después. Si en algún momento este archivo dice algo que el código ya
> no hace, gana el código — pero avisar y corregir el archivo.
>
> Última actualización: 2026-09-13 (revisión a fondo pedida por Ben,
> incluyó lectura completa del código + `git status`/`git diff` +
> validación del bug del asistente de IA contra la doc actual de Gemini).
> **Corrección posterior el mismo día:** una segunda revisión (Claude,
> con acceso a filesystem/terminal + búsqueda web) detectó que la
> sección 8 tenía un dato mal chequeado (el modelo `gemini-3.6-flash`
> SÍ existe) y lo corrigió in situ — ver sección 8.
> **Tercera pasada, mismo día:** se generó `PLAN_MEJORAS_20.md` (20
> mejoras concretas, con evidencia) y se implementaron 4 de las de
> prioridad Alta directamente en el repo — key de Gemini vuelta a leer
> de `secrets.xml`, reglas de backup para excluir `simona_huertas`,
> `HuertaJson.kt` nuevo (serialización pura, testeable) con 6 tests
> unitarios (`app/src/test`, todos pasando), y limpieza de archivos
> sueltos + `repomix-output.xml` fuera de git. Detalle completo en
> `PLAN_MEJORAS_20.md`. Las secciones 4, 6, 8 y 10 de este archivo ya
> reflejan esos cambios.
> **Cuarta pasada, mismo día:** `PLAN_MEJORAS_20.md` quedó **100%
> cerrado** (20/20 puntos, incluida la corrección del punto 18, que
> estaba mal evaluado). Se generó un plan nuevo y separado,
> `PLAN_MEJORAS_UX_20.md` (20 mejoras de frontend/UX con evidencia
> concreta de layouts/estilos/colores), todavía sin implementar — ver
> sección 10.
> **Quinta pasada, mismo día:** al retomar la sesión, `PLAN_MEJORAS_UX_20.md`
> decía "12 de 20" con 8 puntos pendientes, pero una revisión directa del
> código (compilación + tests + `grep` de cada punto marcado como
> pendiente) mostró que 7 de esos 8 (puntos 7, 8, 11, 14, 15, 17 y 19) ya
> estaban implementados en una sesión anterior sin que el plan lo
> reflejara — mismo patrón de docs quedando atrás del código que ya
> había pasado con `PLAN_MEJORAS_20.md`. Se corrigió el plan con la
> evidencia de cada punto. También apareció `ValidacionesHuerta.kt` +
> `ValidacionesHuertaTest.kt` (16 tests nuevos, 24/24 en total) ya
> implementado sin marca, cubriendo el ítem de testing que la sección 10
> daba como pendiente — corregido ahí también. **Queda un solo punto
> real pendiente en todo el proyecto: el 18 de `PLAN_MEJORAS_UX_20.md`**
> (ícono de splash animado — confirmado por búsqueda web que sigue
> haciendo falta un asset vectorial del logo que hoy no existe, no es
> un supuesto viejo). Verificado con `./gradlew compileDebugKotlin` y
> `./gradlew testDebugUnitTest`: ambos BUILD SUCCESSFUL, 24/24 tests.
> **Sexta pasada, mismo día:** se resolvió también el punto 18 —
> `ic_splash_logo.png` se vectorizó con `vtracer` (herramienta de
> trazado automático, instalada en un venv temporal, no en el entorno
> de sistema) probando 3 configuraciones y verificando cada una con
> una captura de pantalla renderizada por Playwright antes de elegir.
> El resultado se convirtió a un `AnimatedVectorDrawable` real de
> Android (`ic_splash_logo_vector.xml` + `ic_splash_logo_animated.xml`
> + `res/animator/splash_icon_scale_in.xml`, scale-in de 650ms) y se
> verificó de la forma más concluyente posible: se instaló un build
> debug en el celular físico conectado por USB
> (`./gradlew installDebug`, BUILD SUCCESSFUL) y se capturó el splash
> real en pantalla (`adb shell monkey` + `adb exec-out screencap`),
> confirmando el logo completo y fiel, sin errores en `adb logcat`.
> **`PLAN_MEJORAS_UX_20.md` queda 100% cerrado (20/20).** Con esto,
> **no queda ningún punto pendiente en ninguno de los dos planes de
> mejoras** (`PLAN_MEJORAS_20.md` y `PLAN_MEJORAS_UX_20.md`).
> **Séptima pasada, mismo día (Claude, a pedido de Ben de "mejorar toda
> la parte visual"):** se generó `PLAN_MEJORAS_VISUAL_2.md`, un tercer
> plan con 14 mejoras visuales nuevas, con una diferencia metodológica
> respecto a los dos anteriores — no se armó solo leyendo layouts XML,
> sino instalando el build actual en el celular físico conectado por
> USB y navegando la app pantalla por pantalla con `adb shell input
> tap`/`uiautomator dump`/`screencap`, para ver el resultado visual real
> (no solo inferido del código). Encontró, entre otras cosas, dos
> pantallas (`TutorialConexionActivity` y el estado inicial de
> `AjustarRangosActivity`) con más de la mitad del alto de pantalla sin
> usar, y el diálogo de opciones de huerta usando el color de superficie
> por defecto de Material3 en vez de la paleta cálida del resto de la
> app.
> **Octava pasada, mismo día (Claude, con conector de terminal real —
> antes solo tenía filesystem):** se retomó `PLAN_MEJORAS_VISUAL_2.md`,
> que decía "8 de 14" al empezar esta pasada. Se implementaron los 6
> puntos que quedaban de una pasada anterior no reflejada en el
> archivo (3, 9, 10, 11, 14) y se cerró el 13 como "evaluado, sin
> cambios" (el propio punto pedía no tocarlo a ciegas). El hallazgo más
> importante del punto 10 (íconos de perfil difíciles de leer): la
> causa real no era solo el dibujo de los íconos, sino que
> `item_perfil_cultivo.xml` forzaba `app:tint="@color/simona_azul"`
> sobre el `ImageView`, aplastando la paleta de colores de los 9
> drawables `ic_perfil_*` a una silueta plana de un solo color — el
> mismo `ic_perfil_fruto` se ve bien SIN tint en
> `activity_ajustar_rangos.xml`. Se sacó ese tint y además se rehicieron
> `ic_perfil_hoja.xml` (antes dos curvas cruzadas, se leía como una X) e
> `ic_perfil_raiz.xml` (antes una curva que se leía como coma/pez), que
> no se arreglaban solo con el color. `PLAN_MEJORAS_VISUAL_2.md` queda
> **13/14 implementado, 1/14 evaluado y cerrado a propósito (punto 13)**
> — no queda ningún punto pendiente. Verificado con `./gradlew
> assembleDebug` completo (`BUILD SUCCESSFUL`, 2m40s) — se usó
> `assembleDebug` en vez de `compileDebugKotlin` porque todos los
> cambios de esta pasada son de recursos XML, no de lógica Kotlin.
> Verificación visual en celular físico completada en esta misma sesión
> tras reconectar el USB: se instaló el APK (`adb install -r`) y se
> confirmó por `screencap` que el badge de chat del FAB en
> `MapaHuertasActivity` y los 9 íconos de `SeleccionarPerfilActivity` se
> ven correctos a color, sin recortes — ver detalle en
> `PLAN_MEJORAS_VISUAL_2.md`.
> Nota aparte sobre herramientas: esta pasada arrancó con un error
> propio — dos drawables nuevos (`ic_badge_chat.xml`, `bg_badge_chat.xml`
> del punto 11) se crearon primero con la herramienta de archivos del
> sandbox interno en vez de escribirse en este repo, lo que hizo fallar
> el primer intento de build (`resource ... not found`); se detectó por
> el propio error de Gradle y se corrigió reescribiendo los archivos
> con la herramienta correcta antes de reintentar.
> **Novena pasada (Claude, a pedido de Ben de mover el dashboard del
> ESP32 a la app):** al revisar el repo para hacer justamente eso, la
> feature **ya estaba implementada por completo** — `DashboardActivity`
> (WebView) ya no existe, `DetalleHuertaActivity` ya cumple el doble rol
> (offline + conectado con polling/riego manual/refill nativos) y
> `servidor_simulado.py` ya es 100% backend (sin servir HTML en `/`).
> Lo que sí estaba desactualizado era la documentación: este archivo
> (secciones 3, 4, 5, 7, 8) seguía describiendo la arquitectura vieja
> con `DashboardActivity`, y el docstring/comentarios de
> `servidor_simulado.py` también mencionaban el dashboard HTML ya
> eliminado — mismo patrón de "docs quedando atrás del código" que ya
> había pasado varias veces antes (ver pasadas anteriores). Se corrigió
> todo eso en esta pasada, sin tocar código Kotlin ni Python (no hacía
> falta). Verificado con `./gradlew compileDebugKotlin`: BUILD
> SUCCESSFUL (up-to-date, sin cambios de lógica). También se encontró
> que el chat IA solo es accesible desde el home (`MapaHuertasActivity`),
> no desde `DetalleHuertaActivity` en modo conectado — la sección 8
> vieja lo daba por hecho con una función (`DashboardActivity.
> armarContextoHuerta()`) que no existe en el código actual.

---

## 1. Qué es SIMONA, en una frase

App Android (Kotlin) que administra una **flota de huertas/canteros**
domésticos, cada uno con su propio dispositivo de riego automatizado
(ESP32 real, o el simulador Python incluido en `simulador/` para hacer
demos sin hardware) — trabajo práctico grupal de un sistema IoT AgTech.

## 2. Idea central de la arquitectura (esto es lo primero que hay que
entender, todo lo demás se deriva de acá)

- **Un dispositivo = una huerta.** Un ESP32 nunca gestiona varias huertas.
  Todos comparten el mismo SSID WiFi (`"SIMONA"`), y se distinguen
  únicamente por la **contraseña** de esa red.
- **La app es la única que conoce la flota completa.** No hay backend ni
  servidor central: cada huerta (nombre, categoría, 8 rangos óptimos,
  contraseña de red, posición en el croquis, foto opcional) se guarda
  **localmente en el celular** (`HuertaRepository`, ver sección 6).
- **La conexión WiFi es acotada y a demanda.** Cuando el usuario abre una
  huerta puntual, la app se conecta *solo* a esa red (nunca la promueve a
  "red por defecto" del celular, así no se pierde el WiFi de internet
  normal) y le hace un `POST /config` para decirle al dispositivo quién
  es antes de mostrarle el dashboard — el dispositivo (real o simulado)
  **no persiste nada entre conexiones**, es la app la que sabe todo.
- **Todo lo demás corre 100% offline**, leyendo siempre la última lectura
  ya guardada localmente: el home, la ficha de detalle, las alertas
  push, y el asistente de IA.

## 3. Recorrido de pantallas (user journey completo)

```
SplashActivity (launcher)
   └─▶ MapaHuertasActivity (HOME — lista de huertas / mapa / chat IA)
         │
         ├─▶ [+ Nueva huerta] SeleccionarPerfilActivity (paso 1: elegir
         │      perfil de cultivo entre 9 predefinidos, grid RecyclerView)
         │        └─▶ AjustarRangosActivity (paso 2: confirmar/ajustar los
         │              8 rangos por slider o por texto, sincronizados)
         │                └─▶ DatosHuertaActivity (paso 3: nombre,
         │                      contraseña de red WPA2 ≥8 chars, foto
         │                      opcional, posición opcional en croquis)
         │                        └─▶ guarda en HuertaRepository
         │                              └─▶ TutorialConexionActivity
         │
         ├─▶ [tocar una huerta / "Conectar"] TutorialConexionActivity
         │      (conecta WiFi a ESA huerta, muestra RSSI, hace
         │      POST /config con reintentos, y recién ahí abre:)
         │        └─▶ DetalleHuertaActivity (modoConectado=true —
         │              MISMA Activity que el modo offline de abajo,
         │              con gauge/tendencia/tanque nativos + polling a
         │              /data + riego manual/refill. Reemplaza a la
         │              vieja DashboardActivity con WebView, eliminada
         │              del proyecto — ver sección 4)
         │
         ├─▶ [tocar "Humedad: X% — estado"] DetalleHuertaActivity
         │      (ficha analítica 100% OFFLINE de una huerta: mini-mapa
         │      multicapa + gauge nativo, sin necesitar WiFi)
         │
         └─▶ [FAB flotante] diálogo de chat con el asistente de IA
                "Simona" (contexto = resumen de TODAS las huertas)
```

`AlertaHuertasWorker` corre en paralelo cada 6 horas (WorkManager,
independiente de toda esta navegación) y notifica si alguna huerta sigue
con sed, releyendo el último dato persistido — nunca se reconecta a un
ESP32 para esto.

## 4. Estructura de archivos Kotlin (qué hace cada uno)

`app/src/main/java/com/simona/app/`

| Archivo | Rol |
|---|---|
| `SimonaApp.kt` | `Application`. Instancias únicas (a nivel proceso) de `WifiConnectionManager` y `HuertaRepository`; crea el canal de notificaciones y programa `AlertaHuertasWorker`. |
| `SplashActivity.kt` | Launcher. Splash Screen API estándar (sin texto libre, solo ícono). Deriva directo a `MapaHuertasActivity`. |
| `MapaHuertasActivity.kt` | **Home.** 368 líneas (refactorizada, PLAN_MEJORAS_20.md puntos 7 y 10 — antes ~800). Usa ViewBinding + RecyclerView (`HuertaCardAdapter.kt`) para la lista de huertas, vista de mapa/croquis con pines (sin tocar, no es una lista), diálogos de editar (`DialogoEditarHuerta.kt`) y chat IA (`DialogoChatAi.kt`) extraídos a sus propias clases, diálogos de eliminar/opciones/aviso con `MaterialAlertDialogBuilder`. |
| `SeleccionarPerfilActivity.kt` | Alta de huerta, paso 1. Grid `RecyclerView` (`GridLayoutManager`, 2 columnas) de los 9 perfiles de cultivo. |
| `AjustarRangosActivity.kt` | Alta de huerta, paso 2. 4 pares de `RangeSlider` + `EditText` sincronizados en ambos sentidos (arrastrar actualiza texto, escribir actualiza slider), con ancho mínimo de 10 puntos para el rango de humedad (única variable con actuador físico real: el riego). |
| `DatosHuertaActivity.kt` | Alta de huerta, paso 3. Nombre, contraseña de red (valida ≥8 caracteres porque `WifiNetworkSpecifier.setWpa2Passphrase()` explota si es más corta), foto opcional, posición opcional en un croquis genérico (se guarda como coordenada RELATIVA 0f–1f, no en píxeles). |
| `PerfilCultivo.kt` | `data class PerfilCultivo` + `object PerfilesCultivo` con los 9 perfiles hardcodeados (Hoja, Fruto, Raíz, Tallo, Flor, Bulbo, Aromáticas, Legumbres, Cactus/Suculenta) + "Personalizado". Valores de referencia, **no validados contra fuente agronómica real** (está anotado explícitamente en el código, ver nota sobre INTA). |
| `PerfilCultivoAdapter.kt` | Adapter simple del grid de perfiles (lista estática, sin DiffUtil). |
| `Huerta.kt` | Modelos de datos: `Huerta` (incluye `historial: List<PuntoHistorial>`, acotado a las últimas ~80 lecturas), `LecturaHuerta`, `PuntoHistorial` (ver sección 9 — ya conectado, no es código muerto). |
| `HuertaRepository.kt` | Persistencia local: `SharedPreferences` + un único blob de JSON manual. CRUD completo (`listar`, `obtener`, `guardar`, `eliminar`, `actualizarLectura`). Tolerante a JSON corrupto/viejo (cae a lista vacía o rellena campos faltantes con default en vez de romper). Delega la serialización en `HuertaJson` (ver abajo). |
| `HuertaJson.kt` | **Nuevo (PLAN_MEJORAS_20.md, punto 16).** `object` con `aJson()`/`desdeJson()`, extraído de `HuertaRepository` para que la serialización sea lógica pura de JVM (sin Context/SharedPreferences) y se pueda testear sin Robolectric. Cubierto por `HuertaJsonTest.kt` (`app/src/test`, 6 tests, todos pasando). |
| `TutorialConexionActivity.kt` | Pantalla de "conectando a esta huerta": llama a `WifiConnectionManager.connect()`, muestra RSSI en vivo, hace `POST /config` con reintentos (3 intentos, 1.5s de por medio) antes de abrir `DetalleHuertaActivity` con `modoConectado=true`. |
| `WifiConnectionManager.kt` | Toda la lógica de `ConnectivityManager.requestNetwork()` + `WifiNetworkSpecifier` acotada al SSID `"SIMONA"`. Sabe A QUÉ huerta está atado el proceso (`huertaIdConectada`/`estaConectadoA()`), resuelve el gateway real de la red (`obtenerIpGateway()`/`resolverUrl()`) para no depender de una IP fija, y expone `rebindProcess()` para cuando el asistente de IA toma prestada la red de internet del celular y hay que devolver el proceso a la red de SIMONA después. |
| `AsistenteGemini.kt` | Llama a la API de Gemini (Interactions API) directo desde la app, pidiendo la red de internet real del celular en paralelo a la red acotada de SIMONA. **Ver sección 8 — el hardcode de la key ya se revirtió, pero sigue sin commitear.** |
| `AlertaHuertasWorker.kt` | `CoroutineWorker` (WorkManager) cada 6 horas. Relee `HuertaRepository`, notifica si alguna huerta sigue con `humedad <= humedadMin`. No se reconecta a ningún ESP32. |
| `DetalleHuertaActivity.kt` | Doble rol según `modoConectado` (extra del intent): **offline** (caso normal, "tocar la humedad" en el home) es ficha analítica 100% offline, lee de `HuertaRepository` y se autorefresca cada 3s por si hay lecturas nuevas en paralelo; **conectado** (`modoConectado=true`, abierta desde `TutorialConexionActivity` tras el `POST /config`) además hace polling propio a `GET /data` cada 3s y habilita riego manual (`POST /riego_manual`) y refill de tanque (`POST /refill_tanque`) — reemplaza por completo a la vieja `DashboardActivity` (WebView contra el HTML que servía el ESP32/simulador, ya eliminado de ambos lados). Muestra `MiniMapaCapasView`, `GaugeHumedadView` y `GraficoTendenciaView` (tarjeta "Tendencia reciente", ver sección 9). |
| `MiniMapaCapasView.kt` | Vista custom (LinearLayout armado por código): mini-mapa multicapa con chips para elegir Humedad/pH/Temp/Luz, una franja de 3 colores (bajo/óptimo/alto) según los rangos configurados de la huerta, un pin con badge del valor actual, y leyenda. Incluye `construirCapasDesdeHuerta()`, la función que arma las 4 capas a partir de `Huerta` + `LecturaHuerta`. |
| `GaugeHumedadView.kt` | Vista custom (Canvas puro): gauge circular de 270° con 3 bandas de color, marcador en la posición del valor actual, porcentaje grande en el centro. Usada en `DetalleHuertaActivity`. |
| `GraficoTendenciaView.kt` | Vista custom para graficar `PuntoHistorial` a lo largo del tiempo. **Conectada** (PLAN_MEJORAS_20.md punto 8, ver sección 9): la usa la tarjeta "Tendencia reciente" de `DetalleHuertaActivity`, con selector Humedad/Temperatura vía `chipTendenciaHumedad`/`chipTendenciaTemp`. |
| `FotoHuertaUtil.kt` | Utilidades compartidas para la foto opcional: persistir permiso de lectura del URI (`takePersistableUriPermission`), decode sampleado (sin Glide/Coil), esquinas redondeadas. |
| `ThemePrefs.kt` | Wrapper mínimo sobre `SharedPreferences` + `AppCompatDelegate.setDefaultNightMode()` para el tema claro/oscuro de la app nativa. |
| `ConnectionState.kt` | `sealed class` con los 4 estados de conexión (Desconectado/Conectando/Conectado/Error) que usa `TutorialConexionActivity` para renderizar la UI. |

## 5. Modelo de datos

```kotlin
data class Huerta(
    val id: String = UUID.randomUUID().toString(),
    val nombre: String,
    val passwordRed: String,       // texto plano hoy (ver sección 10, #3)
    val categoria: String,         // nombre del PerfilCultivo elegido
    val humedadMin: Float, val humedadMax: Float,
    val phMin: Float, val phMax: Float,
    val luzMin: Int, val luzMax: Int,
    val tempMin: Float, val tempMax: Float,
    val ultimaLectura: LecturaHuerta? = null,
    val ultimaActualizacion: Long? = null,
    val historial: List<PuntoHistorial> = emptyList(),  // últimas ~80 lecturas
    val posicionMapaX: Float? = null,  // 0f–1f, relativo al croquis
    val posicionMapaY: Float? = null,
    val fotoUri: String? = null        // content://... como String
)

data class LecturaHuerta(
    val humedad: Float, val temperatura: Float, val luz: Int,
    val ph: Float, val riegoActivo: Boolean, val tanqueAgua: Float
)

data class PuntoHistorial(  // usado por GraficoTendenciaView, ver sección 9
    val timestamp: Long, val humedad: Float, val temperatura: Float
)
```

`HuertaRepository` SÍ guarda un historial acotado de lecturas por huerta
(`historial`, últimas ~80, ver `PLAN_MEJORAS_20.md` punto 8) además de
`ultimaLectura`. El simulador tiene el suyo propio por separado
(`historial_humedad`, 30 puntos en memoria, y el CSV en disco) — son dos
historiales independientes, no se comparten. `GraficoTendenciaView` +
`PuntoHistorial` ya están conectados (tarjeta "Tendencia reciente" en
`DetalleHuertaActivity`), no son código muerto.

## 6. Persistencia (todo local, sin backend)

- **`HuertaRepository`**: `SharedPreferences` (`simona_huertas`) con un
  único string JSON (`huertas_json`, un array con todas las huertas).
  Se reescribe completo en cada `guardar()`/`eliminar()`/`actualizarLectura()`.
  Tolerante a JSON corrupto (cae a lista vacía, sin avisar al usuario —
  ver deuda técnica) y a campos faltantes de versiones viejas del modelo
  (ej. `fotoUri`, agregado después).
- **`ThemePrefs`**: `SharedPreferences` separado (`simona_theme_prefs`),
  un solo booleano.
- **Reglas de backup (PLAN_MEJORAS_20.md, punto 3, ya implementado):**
  `res/xml/backup_rules.xml` (Android <12) y
  `res/xml/data_extraction_rules.xml` (Android 12+), ambos referenciados
  desde `AndroidManifest.xml`, excluyen explícitamente `simona_huertas`
  del backup automático y de la transferencia dispositivo-a-dispositivo
  — antes las contraseñas WiFi en texto plano podían terminar en el
  backup de Google Drive del celular.
- **No hay Room, no hay Gson/Moshi, no hay ninguna dependencia de red
  además de las HTTP nativas del SDK** — decisión de diseño explícita
  documentada en varios comentarios del código ("sin dependencias
  externas de más allá de lo estrictamente necesario").

## 7. El simulador (`simulador/servidor_simulado.py`)

Server HTTP puro (`http.server` de la librería estándar de Python, sin
Flask ni deps externas) que **simula UN solo ESP32 = UNA sola huerta**
(a propósito: un ESP32 real tampoco sabe de otras huertas). Corre en un
hilo secundario que actualiza el estado simulado cada 1 segundo
(fluctuación ambiental + lógica de riego automático con cooldown y corte
de seguridad por tiempo/humedad/tanque vacío).

Endpoints:

| Método | Path | Qué hace |
|---|---|---|
| GET | `/` , `/index.html` | Ya NO sirve HTML de dashboard (se sacó por completo, ver nota de arquitectura antes de `SimonaSimHandler` en el propio archivo) — devuelve un JSON simple de ping/estado-vivo con la lista de endpoints, útil para confirmar a mano desde un navegador que el dispositivo responde. |
| GET | `/data` | Telemetría liviana (humedad, temp, luz, pH, riego, tanque) — la consume el polling de `DetalleHuertaActivity.kt` en modo conectado. |
| GET | `/estado` | Objeto completo (config + estado + historial de 30 puntos) — ya no lo consume ningún dashboard HTML propio (se eliminó); queda como endpoint de debug/inspección manual. |
| GET | `/export_csv` | Descarga el historial en memoria como CSV. |
| POST | `/config` | Recibe nombre + categoría + los 8 rangos desde la app. Valida todo (nombre no vacío, rangos numéricos, min < max, ancho mínimo de humedad ≥10). **Resetea todo el estado simulado** cada vez que se llama — decisión consciente documentada en el código (previsibilidad > continuidad, dado que un ESP32 real sin NVS tampoco distinguiría "primera vez" de "ya configurado"). |
| POST | `/riego_manual` | Prende/apaga riego forzado (15s de duración fija, corte también por humedad ≥80% o tanque vacío). |
| POST | `/refill_tanque` | Rellena el tanque al 100%. |

El historial también se persiste en disco (`historial_simona.csv`, se
crea junto al script) y **ese sí sobrevive** a un restart del script y a
los resets de `/config` — a diferencia del historial en memoria, que se
limpia en cada `POST /config`.

`Access-Control-Allow-Origin: *` en todas las respuestas (útil para
debuggear/probar los endpoints a mano desde un navegador de escritorio;
ya no hay WebView ni dashboard HTML propio contra el que debuggear).

Uso: `python3 simulador/servidor_simulado.py [puerto]` (default 8000).

## 8. El asistente de IA "Simona" — estado actual (✅ probado con dispositivo real, sin errores)

`AsistenteGemini.kt` llama a la **Gemini Interactions API** de Google
(`POST https://generativelanguage.googleapis.com/v1beta/interactions`,
NO la vieja `generateContent`), pidiendo la red de internet real del
celular vía `ConnectivityManager.requestNetwork()` + forzando esa
conexión puntual con `Network.openConnection()` — porque el proceso ya
está atado a la red WiFi de la huerta (sin internet) mientras el
dashboard está abierto. Al terminar, `WifiConnectionManager.rebindProcess()`
vuelve a atar el proceso a SIMONA si seguía conectada.

El formato de request/response (`{"model", "input"}` → `{"steps": [...]}`
con pasos tipo `model_output`) **es correcto** para la Interactions API
vigente desde su GA en junio 2026 (verificado contra la documentación
oficial de Google, que es posterior a mi corte de conocimiento).

**Estado real ahora mismo** (`git status` marca `AsistenteGemini.kt` y
`WifiConnectionManager.kt` como modificados, sin commitear — el punto 1
ya se corrigió en el working tree, pero sigue sin haber commit nuevo):

1. ~~La API key de Gemini está hardcodeada como literal directo~~ —
   **CORREGIDO (PLAN_MEJORAS_20.md, punto 1):** ya vuelve a leerse de
   `context.getString(R.string.gemini_api_key)`. Sigue sin commitear
   (junto con el resto de los cambios de este archivo), pero al menos
   ya no hay riesgo de que la key quede expuesta si se commitea como
   está ahora.
2. ~~El modelo se cambió a `"gemini-3.6-flash"`, que no existe~~ —
   **CORREGIDO 2026-09-13:** esto era un error de esta misma revisión.
   `gemini-3.6-flash` **sí existe y está en GA** (verificado contra el
   changelog oficial de la API de Gemini, posterior a mi corte de
   conocimiento — la familia vigente a esta fecha es bastante más
   amplia de lo asumido: 3.1, 3.5, 3.6, 3.7 e incluso ya salió 3.8
   Flash). El nombre del modelo **no es la causa** del bug de
   "el asistente hace crashear/fallar la app al mandarle un mensaje".
   El sospechoso real es el punto 3 de abajo (la key `AQ.`) — no vale
   la pena tocar `MODELO` sin antes confirmar eso en logcat.
3. Las API keys con prefijo `"AQ."` (Auth Key — la que tiene este
   proyecto) tienen reportes abiertos en el foro oficial de Google
   (discuss.ai.google.dev) de devolver `401 ACCESS_TOKEN_TYPE_UNSUPPORTED`
   contra la Interactions API. **Verificado 2026-09-13 con dispositivo
   real por `adb`:** instalé un build debug con el código actual (esta
   misma key, sin cambios) y le mandé dos preguntas al asistente desde
   la app. Las dos respondieron bien, sin ningún `Log.w("AsistenteGemini",
   ...)` en logcat y sin el 401. **El bug no reprodujo con esta key en
   este momento** — puede ser un problema intermitente del lado de
   Google (los reportes del foro siguen ahí) más que algo permanente
   con esta key puntual. Si vuelve a aparecer, repetir la prueba antes
   de asumir que hace falta una key nueva o un backend propio.
4. El diff sin commitear también le sacó casi todos los comentarios
   explicativos a `WifiConnectionManager.kt` (la lógica en sí no cambió
   ahí, solo perdió documentación) — si se decide commitear ese archivo,
   vale la pena restaurar los comentarios en vez de perderlos.

👉 **Estado actual:** el punto 1 ya está revertido, el modelo (punto 2)
no hacía falta tocarlo, y el bug del punto 3 no reprodujo en la prueba
real del 2026-09-13 (ver arriba) — no hay nada más pendiente de este
asistente salvo volver a probar si el error reaparece.

**Corrección (hallazgo de esta pasada):** este archivo decía que el
contexto de Gemini se armaba desde dos lugares (`DashboardActivity.
armarContextoHuerta()` para una huerta puntual + `MapaHuertasActivity.
abrirChatAi()` para todas), pero `DashboardActivity` ya no existe y esa
función no está en ningún lado del código actual (verificado con
`grep -rn "armarContextoHuerta"`, sin resultados). El único punto de
entrada real hoy es:
- `MapaHuertasActivity.abrirChatAi()` — contexto de TODAS las huertas
  (usado desde el FAB del home).

`DetalleHuertaActivity` (el reemplazo nativo del dashboard, modo
conectado) **no tiene** un botón de chat IA propio — el asistente solo
se abre desde el home. Si en algún momento se quiere charlar con Simona
sobre una huerta puntual mientras está conectada, hoy no hay forma
directa de hacerlo sin volver al home primero; queda anotado como
posible mejora futura, no como bug.

## 9. Código muerto y features diseñadas pero no conectadas

- ~~`GraficoTendenciaView.kt` + `PuntoHistorial`~~: **ya conectados**
  (PLAN_MEJORAS_20.md, punto 8) — `HuertaRepository` ahora guarda un
  historial acotado (últimas 80 lecturas) y `DetalleHuertaActivity`
  tiene una tarjeta "Tendencia reciente" que usa
  `GraficoTendenciaView.setDatos()`.
- ~~`app/src/main/res/layout/dialog_diagnostico_wifi.xml`~~: **ya
  conectado** (PLAN_MEJORAS_20.md, punto 9) — `TutorialConexionActivity`
  lo infla desde un botón "¿Por qué no conecta?" visible en
  `ConnectionState.Error`.
- **`app/src/main/res/layout/dialog_agregar_bitacora.xml` +
  `item_evento_bitacora.xml`**: layouts de una futura "bitácora" de
  eventos por huerta (agregar/listar eventos). **Tampoco están
  conectados a ningún Kotlin** — ni un `EventoBitacora` en el modelo de
  datos, ni una Activity que los use. Es una feature planeada pero sin
  empezar del lado de lógica.
- **`MainActivity.kt` / `activity_main.xml`** (el flujo viejo de una sola
  huerta con contraseña fija `"simona123"`): ya fueron borrados del
  proyecto — el comentario en `SplashActivity.kt` que decía "se pueden
  borrar" está desactualizado, ya no existen.
- **`app/README.md`** (duplicado desactualizado mencionado en el README
  de la raíz): tampoco existe más — ya se limpió.

## 10. Deuda técnica y mejoras pendientes (resumen)

Hay un informe completo y separado con 30 mejoras concretas, agrupadas
por categoría (seguridad, arquitectura, testing, confiabilidad, UX/
accesibilidad, rendimiento, build), generado el 2026-09-13:
`/mnt/user-data/outputs/SIMONA_revision_30_mejoras.md` (fuera de este
repo — si hace falta, pedir que se regenere/copie acá). Los puntos más
urgentes de esa lista, resumidos:

**`PLAN_MEJORAS_20.md` (20 puntos), `PLAN_MEJORAS_UX_20.md` (20 puntos)
y `PLAN_MEJORAS_VISUAL_2.md` (14 puntos) — los tres planes de mejoras
generados hasta ahora — están cerrados: 53/54 puntos implementados y
verificados, más 1 punto (el 13 de `PLAN_MEJORAS_VISUAL_2.md`, salto de
contraste del header en modo oscuro) evaluado y cerrado a propósito
sin cambio de código, porque el propio punto pedía revisión en equipo
antes de tocarlo.** No queda ningún trabajo pendiente de los tres —
cualquier mejora nueva a partir de acá necesita un plan nuevo. Del plan
visual (el más reciente), lo más relevante: la causa de que los 9
íconos de perfil de cultivo (Hoja, Raíz, etc.) se leyeran mal no era
solo el dibujo — `item_perfil_cultivo.xml` forzaba un tint azul plano
sobre `ImageView`, aplastando la paleta de colores de los drawables
`ic_perfil_*`; sacar ese tint arregló 7 de los 9 de una, y solo
`ic_perfil_hoja.xml`/`ic_perfil_raiz.xml` necesitaron rehacerse en
forma además de color.

1. ~~Revertir el hardcode de la API key~~ — **HECHO.** El modelo
   (`gemini-3.6-flash`) no era inválido, ver sección 8.
2. ~~`android:allowBackup="true"` sin excluir `simona_huertas`~~ —
   **HECHO.** `backup_rules.xml` + `data_extraction_rules.xml` ya
   excluyen esa `SharedPreferences` (ver sección 6).
3. ~~`passwordRed` se guarda sin cifrar~~ — **HECHO**, pero NO con
   `EncryptedSharedPreferences` (esa librería está en proceso de
   deprecación por parte de Google, verificado por búsqueda web). En
   cambio: `CryptoUtil.kt` nuevo, AES-256-GCM directo contra Android
   Keystore, enganchado en `HuertaRepository`, con migración perezosa
   de contraseñas viejas en texto plano (ver PLAN_MEJORAS_20.md, punto
   4, para el detalle completo).
4. MapaHuertasActivity.kt mezcla de responsabilidades — HECHO.
   Refactorizada a 368 líneas con RecyclerView y ListAdapter/DiffUtil,
   con los diálogos extraídos a sus propias clases (PLAN_MEJORAS_20.md,
   puntos 7 y 10; detalle en sección 12 y en el propio plan).
5. ~~Cero tests unitarios de JVM~~ — **HECHO.** `app/src/test` tiene
   `HuertaJsonTest.kt` (8 tests, sobre la serialización de
   `HuertaJson.kt`) y `ValidacionesHuertaTest.kt` (16 tests, sobre
   `ValidacionesHuerta.kt`, enganchado en `DatosHuertaActivity` y
   `AjustarRangosActivity`) — **24/24 tests pasando**, verificado con
   `./gradlew testDebugUnitTest`. Playwright contra el dashboard HTML
   del simulador ya se usa — `simulador/tests/smoke_test.js`, 5/5
   tests pasando (ver PLAN_MEJORAS_20.md, punto 17).
6. ~~`isMinifyEnabled = false` en el build de release~~ — **HECHO.**
   Ahora `isMinifyEnabled = true` + `isShrinkResources = true`, con
   regla de ProGuard para `AlertaHuertasWorker` (WorkManager lo
   instancia por reflexión). Verificado con `./gradlew assembleRelease`
   completo: BUILD SUCCESSFUL, APK final de 3.09MB (ver
   PLAN_MEJORAS_20.md, punto 5).

## 11. Decisiones de diseño no triviales (por qué está hecho así)

- **`WifiConnectionManager` y `HuertaRepository` como instancia única en
  `SimonaApp`** (no por Activity): `bindProcessToNetwork()` ata la red a
  nivel de *proceso*, no de Activity — si cada pantalla tuviera su
  propia instancia no podría liberar correctamente la conexión que
  originó otra pantalla.
- **El asistente de IA pide SU PROPIA red de internet** en paralelo a la
  red WiFi acotada de la huerta, porque el proceso ya está atado a una
  red sin internet (la del ESP32) mientras el dashboard está abierto.
- **El simulador representa un solo ESP32/huerta**, sin CRUD
  multi-huerta — fiel al comportamiento de un dispositivo real.
- **Cada `POST /config` resetea el estado simulado por completo**,
  decisión consciente por previsibilidad en la demo, no un bug.
- **`EncryptedSharedPreferences` para `passwordRed`: evaluada y
  pospuesta a propósito** (agrega una dependencia externa nueva para un
  riesgo acotado a acceso físico/root al celular) — no es deuda técnica
  "olvidada", es una decisión documentada que se puede revisitar.
- **`AsistenteGemini` le pega a Gemini directo desde el celular con la
  key embebida en el APK** (decisión consciente para un TP de uso
  personal, no un descuido — ver PLAN_MEJORAS_20.md, punto 6). Cualquier
  key en un APK, cifrada o no, es extraíble por alguien con tiempo. Si
  algún día se distribuye la app más ampliamente, la mejora sería un
  backend liviano (aunque sea una función serverless) que guarde la key
  server-side y la app le pegue a ese backend en vez de a Gemini
  directo.
- **Sin Room, sin Gson/Moshi, sin Glide/Coil**: todo el proyecto evita
  dependencias externas de más allá de lo estrictamente necesario
  (criterio repetido en varios comentarios del código) — Material
  Components, ConstraintLayout, core-splashscreen y WorkManager son las
  únicas agregadas más allá de lo que trae el SDK de Android por
  defecto.
- **`dashboard_url` es una única constante en `strings.xml`** (con las
  dos opciones comentadas al lado: demo `:8000` vs. hardware real sin
  puerto) en vez de product flavors de Gradle — decisión tomada porque
  `app/build.gradle.kts` no estaba disponible en un momento dado del
  desarrollo y no se quiso editar Gradle a ciegas. El **host** de esa URL
  igual se resuelve en tiempo de ejecución (`WifiConnectionManager.resolverUrl()`),
  solo el puerto/esquema quedan fijos en la constante.

## 12. Convenciones del proyecto

- **Idioma**: nombres de clases, funciones, variables, comentarios y
  strings de usuario, todo en **español rioplatense**. Mantener esa
  convención en código nuevo.
- **Comentarios extensos y con referencias a "secciones" de un documento
  de plan externo** (ej. "sección 14.4", "Fase 6.3") — son referencias a
  un documento de planificación que el usuario fue armando fuera del
  repo a medida que pedía features. Esas referencias no están en este
  repo, son solo trazabilidad histórica de por qué se hizo algo así.
- ViewBinding: usado en todas las Activities, incluida
  MapaHuertasActivity desde su refactor (PLAN_MEJORAS_20.md, punto 10)
  ya no hay findViewById manual en el proyecto.
- Diálogos: unificados en MaterialAlertDialogBuilder en todo el
  proyecto desde el refactor de MapaHuertasActivity (mismo punto 10).
- **Sin persistencia de historial de lecturas en la app** (solo
  `ultimaLectura`) — cualquier feature que necesite una serie temporal
  (como `GraficoTendenciaView`) requiere primero agregar esa
  persistencia a `HuertaRepository`.

## 13. Cómo compilar y correr

Ver `README.md` en la raíz del repo — tiene la guía completa paso a paso
(JDK 17, Android SDK Command Line Tools sin Android Studio, Gradle
wrapper, `assembleDebug`/`assembleRelease`, instalación por `adb`) y la
explicación de modo demo vs. modo hardware real. Este archivo
(`CONTEXTO_PROYECTO.md`) es un complemento de contexto/arquitectura, no
reemplaza esa guía operativa.

Para levantar el simulador rápido: `python3 simulador/servidor_simulado.py 8000`.

## 14. Git — estado al momento de esta actualización

- Remoto: `https://github.com/SaloWeb/simona-app.git`
- Últimos commits: `a5a60fa cambio de color`, `d29d8bc detalles`,
  `b149c66 Update README.md`, `cfa9c86 v1`.
- **Cambios sin commitear ahora mismo**: `.gitignore`,
  `app/build.gradle.kts`, `AndroidManifest.xml`, `AsistenteGemini.kt`
  (ya corregido, ver sección 8), `HuertaRepository.kt`,
  `WifiConnectionManager.kt` (sigue con el bug de comentarios perdidos,
  ver sección 8 punto 4) y `simulador/historial_simona.csv` (datos
  generados al correr el simulador, no es código). Archivos nuevos sin
  trackear: `CONTEXTO_PROYECTO.md`, `PLAN_MEJORAS_20.md`,
  `HuertaJson.kt`, `app/src/main/res/xml/` (reglas de backup),
  `app/src/test/` (tests nuevos). `repomix-output.xml` fue sacado del
  tracking (`git rm --cached`) y varios `.txt` sueltos de descargas
  duplicadas del navegador fueron borrados (PLAN_MEJORAS_20.md, punto 20).

---

### Cómo mantener este archivo al día

Cada vez que se agregue una Activity, se cambie una decisión de
arquitectura, se resuelva o aparezca un bug importante, o se conecte una
de las features "diseñadas pero no conectadas" de la sección 9, actualizar
la sección correspondiente de este archivo en el mismo pedido/commit.
Si hay dudas de si algo amerita actualizarlo: si haría que alguien
entendiendo el proyecto por primera vez se confunda o pierda tiempo sin
esta info, sí amerita.
