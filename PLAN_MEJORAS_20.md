# PLAN_MEJORAS_20.md — 20 mejoras para SIMONA

> **Estado (2026-09-13, décima pasada):** los 20 puntos revisados. El
> punto 2 se verificó con dispositivo conectado por `adb` y el bug **no
> reprodujo** (ver detalle en el punto). Con esto se cierra la primera
> pasada completa del plan.

> Generado el 2026-09-13 a partir de una revisión directa del repo:
> lectura del código Kotlin completo, `git log`/`git status`/`git diff`,
> `build.gradle.kts`, `AndroidManifest.xml`, el firmware ESP32 y
> `HuertaRepository.kt`, más una búsqueda web para chequear datos de
> Gemini posteriores a mi corte de conocimiento. Cada punto cita la
> evidencia concreta encontrada, no es una lista genérica de buenas
> prácticas. Complementa a `CONTEXTO_PROYECTO.md` (que tiene el mapa
> completo del proyecto) — mirar ahí primero si algo no se entiende.

Convención: **Prioridad** = Alta (rompe algo o es un riesgo real hoy) /
Media (deuda técnica que conviene resolver antes de que crezca) / Baja
(pulido, no urgente).

---

## Seguridad

### 1. Revertir el hardcode de la API key de Gemini — ✅ HECHO
**Prioridad: Alta**
_Corrección (2026-09-13): al revisar el código para actualizar este
punto, resultó que ya estaba corregido de una pasada anterior sin
marca en este plan — `AsistenteGemini.kt` línea 94 ya lee
`context.getString(R.string.gemini_api_key)`, no el literal hardcodeado.
Sigue sin commitear junto con el resto de los cambios de este plan (ver
`CONTEXTO_PROYECTO.md`, sección 14), pero el riesgo de que la key quede
expuesta en un futuro commit ya no existe. No hacía falta ningún cambio
de código, solo corregir el estado de este punto._
`AsistenteGemini.kt` tiene hoy la key de Gemini como literal directo en
el código (`val apiKey = "AQ.Ab8RN6IZ..."`), sin commitear todavía
(`git status` la marca modificada). La versión en `HEAD` la lee bien de
`context.getString(R.string.gemini_api_key)`, que a su vez lee de
`secrets.xml` (correctamente listado en `.gitignore`). Si se commitea
como está ahora, la key queda expuesta para siempre en el historial de
git, incluso si después se revierte.
**Acción:** volver a `context.getString(R.string.gemini_api_key)` antes
de cualquier commit.

### 2. Confirmar la causa real del bug del asistente de IA antes de tocar el modelo — ✅ VERIFICADO (no reproduce)
**Prioridad: Alta**
_Actualización (2026-09-13): conecté el celular por USB con depuración
habilitada, instalé un build debug fresco (`./gradlew installDebug`,
BUILD SUCCESSFUL) con el código actual (key leída de
`context.getString(R.string.gemini_api_key)`, modelo
`gemini-3.6-flash`, la key `AQ.` real de `secrets.xml` sin cambios), y
le mandé DOS preguntas distintas al asistente desde la app (por UI,
usando `adb input`/`screencap` para interactuar y verificar). Las DOS
respondieron con `Log.w("AsistenteGemini", ...)` completamente ausente
de `adb logcat -d` (limpiado con `logcat -c` antes de cada envío) y con
una respuesta de Gemini coherente y contextual en la UI — sin ningún
`401 ACCESS_TOKEN_TYPE_UNSUPPORTED` ni ningún otro error. El bug **no
reprodujo** con esta key, este modelo y esta conexión de red en este
momento. Esto no descarta que sea un problema intermitente del lado de
Google (los reportes del foro oficial siguen existiendo), pero sí
descarta que sea un problema sistemático/permanente con esta key
puntual ahora mismo — no hace falta generar una key nueva ni mover la
llamada a un backend propio (punto 6) a menos que el error reaparezca._
El diff sin commitear también cambió `MODELO` a `"gemini-3.6-flash"`.
Verifiqué contra el changelog oficial de la API de Gemini: ese modelo
**sí existe y está en GA**, así que el nombre del modelo no es la causa
del crash. El sospechoso real es la key con prefijo `AQ.` (la que tiene
`secrets.xml`): hay decenas de reportes abiertos en el foro oficial de
Google de esas keys devolviendo `401 ACCESS_TOKEN_TYPE_UNSUPPORTED`,
un bug del lado de Google sin solución confirmada.
**Acción (cumplida):** se miró el comportamiento real de la app con
dispositivo conectado — no hubo error que investigar en logcat. Si el
error reaparece en el futuro, repetir esta misma prueba y, si es el
401, recién ahí evaluar key nueva o backend propio (punto 6).

### 3. Excluir `simona_huertas` del backup automático — ✅ HECHO
**Prioridad: Alta**
_Corrección (2026-09-13): al revisar el código, resultó que ya estaba
implementado de una pasada anterior sin marca en este plan —
`AndroidManifest.xml` ya tiene `android:fullBackupContent="@xml/backup_rules"`
y `android:dataExtractionRules="@xml/data_extraction_rules"` (líneas 17-18),
y ambos archivos existen en `app/src/main/res/xml/` excluyendo
`simona_huertas` (ver `CONTEXTO_PROYECTO.md`, sección 6, para el detalle
de qué excluyen exactamente). No hacía falta ningún cambio de código,
solo corregir el estado de este punto._
`AndroidManifest.xml` tiene `android:allowBackup="true"` sin ningún
`android:fullBackupContent` que excluya preferencias. Eso significa que
`simona_huertas` (que guarda las contraseñas WiFi de cada huerta en
texto plano, confirmado en `HuertaRepository.kt`) puede terminar en el
backup automático a Google Drive del celular del usuario.
**Acción:** agregar un `res/xml/backup_rules.xml` que excluya el
`SharedPreferences` `simona_huertas` (o directamente pasar
`allowBackup="false"` si no hace falta backup de nada más).

### 4. Revisitar el cifrado de `passwordRed` — ✅ HECHO
**Prioridad: Media**
_Implementado, pero NO con `EncryptedSharedPreferences` como sugería
esta evaluación original: esa librería (`androidx.security:security-crypto`)
resultó estar en proceso de deprecación por parte de Google en favor del
uso directo de Android Keystore (confirmado vía búsqueda web, dato
posterior a mi corte de conocimiento) — agregarla ahora habría sido sumar
una dependencia externa ya en camino de desaparecer. En cambio: nuevo
`CryptoUtil.kt` (AES-256-GCM directo contra Android Keystore, sin
librerías externas), enganchado en `HuertaRepository.persistir()`/`listar()`
(cifra/descifra, `HuertaJson` queda intacto y sigue siendo puro/testeable).
Migración perezosa: las contraseñas viejas en texto plano se detectan por
la ausencia del prefijo `"enc:v1:"` y se re-guardan cifradas la próxima
vez que se llama a `guardar()`, sin paso de migración explícito. Compila
y los 6 tests de `HuertaJsonTest` siguen pasando (verificado)._
Confirmado en `HuertaRepository.kt`: las contraseñas de red se guardan
tal cual, sin cifrar, en el JSON de `SharedPreferences`. El propio
proyecto documenta que evaluó `EncryptedSharedPreferences` y lo pospuso
a propósito (riesgo acotado a acceso físico/root al celular). Vale la
pena revisar esa decisión ahora que el proyecto está más maduro,
sobre todo si se corrige el punto 3 (ya no alcanza con que no viaje al
backup si igual queda legible en el propio dispositivo).
**Acción:** evaluar migrar `HuertaRepository` a
`EncryptedSharedPreferences` (una sola dependencia de
`androidx.security:security-crypto`, consistente con el criterio de
"pocas dependencias" que ya sigue el proyecto).

### 5. Habilitar shrink/R8 en el build de release — ✅ HECHO
**Prioridad: Media**
_Implementado: `isMinifyEnabled = true` + `isShrinkResources = true`.
Se agregó una regla explícita en `proguard-rules.pro` para
`androidx.work.ListenableWorker` (WorkManager instancia
`AlertaHuertasWorker` por reflexión, invisible para el análisis estático
de R8). Verificado con `./gradlew assembleRelease` completo:
**`BUILD SUCCESSFUL` en 26m36s**, APK final de 3.09MB
(`app-release-unsigned.apk`), y el mapping de R8 confirma que
`AlertaHuertasWorker` conservó su nombre original (no lo tocó el
ofuscador)._
`app/build.gradle.kts` tiene `isMinifyEnabled = false` en `release`.
Sin shrink ni ofuscación, el `.apk` final es más pesado de lo necesario
y cualquiera que lo decompile ve el código (y, si se llega a cometer el
error del punto 1, la key) sin ningún esfuerzo.
**Acción:** activar `isMinifyEnabled = true` + `isShrinkResources =
true` en `release`, probar que `AsistenteGemini` y las vistas custom
(que usan reflexión mínima, si acaso) sigan andando, y ajustar
`proguard-rules.pro` si hace falta.

### 6. Sacar la llamada a Gemini del cliente Android — ✅ HECHO (documentación)
**Prioridad: Baja (mejora estructural, no urgente para el TP)**
_Nota (2026-09-13): la versión anterior de este punto decía que el
trade-off ya estaba anotado en CONTEXTO_PROYECTO.md como decisión
consciente, pero eso confundía esto con la decisión de
EncryptedSharedPreferences (otra cosa distinta). No estaba
documentado. Se agregó una entrada nueva en la sección de decisiones
de diseño de CONTEXTO_PROYECTO.md con el trade-off explícito. El
backend en sí sigue sin implementarse (fuera de alcance del TP); esto
cierra solo la parte de documentación que pedía la acción original._
Hoy la app llama a Gemini directo desde el celular con una key embebida
en el APK — cualquier key ahí, cifrada o no, es extraíble por alguien
con tiempo. Es una decisión razonable para un proyecto escolar de uso
personal, pero si en algún momento se piensa distribuir la app más
ampliamente, conviene un backend liviano (aunque sea una función
serverless) que guarde la key server-side y la app le pegue a ese
backend en vez de a Gemini directo.
**Acción (original, ya cumplida):** dejarlo documentado como mejora
futura — ver la nota de corrección arriba y la entrada nueva en
`CONTEXTO_PROYECTO.md`, sección 11.

---

## Arquitectura y código

### 7. Refactorizar `MapaHuertasActivity` — ✅ HECHO
**Prioridad: Media**
_Implementado junto con el punto 10 (mismo refactor): la lista de
tarjetas pasó de armarse a mano por código a RecyclerView +
ListAdapter/DiffUtil (HuertaCardAdapter.kt + item_huerta_card.xml, con
la misma lógica de colores/estado que tenía renderizarTarjetas(),
ahora en ViewHolder.bind()). El diálogo de "editar huerta" y el de
chat IA se extrajeron a sus propias clases (DialogoEditarHuerta.kt,
DialogoChatAi.kt); el picker de fotos sigue en la Activity porque
ActivityResultContracts exige registrarse antes de STARTED. El
croquis/mapa con pines (renderizarPines) no se tocó a propósito: no es
una lista (no hay DiffUtil/adapter que aplicarle) y queda fuera del
alcance de este punto. MapaHuertasActivity.kt quedó en 368 líneas
(era 796). Verificado con ./gradlew compileDebugKotlin y
./gradlew testDebugUnitTest: ambos BUILD SUCCESSFUL en esta máquina._
Confirmado con `wc -l`: tiene **796 líneas**, más del doble que la
siguiente Activity más grande (`DashboardActivity`, 431). Es la única
que arma las tarjetas de huertas a mano en vez de `RecyclerView`, y
mezcla lista, mapa/croquis, diálogo de editar, diálogo de eliminar,
diálogo de chat IA y gestión de foto en una sola clase.
**Acción:** migrar la lista de tarjetas a `RecyclerView` +
`ListAdapter`/`DiffUtil`, y separar el diálogo de chat IA y el de
editar huerta en sus propias clases (`DialogFragment` o helpers
dedicados) para que la Activity quede solo orquestando.

### 8. Agregar historial de lecturas a `HuertaRepository` — ✅ HECHO
**Prioridad: Media**
_Implementado: nuevo campo `historial: List<PuntoHistorial>` en `Huerta`,
serializado/deserializado en `HuertaJson.kt` (huertas guardadas antes de
este cambio no tienen la clave `"historial"` en su JSON — se detecta con
`has()`/`isNull()` y quedan con lista vacía, mismo criterio que ya se
usaba para `fotoUri`). `HuertaRepository.actualizarLectura()` ahora
acumula un `PuntoHistorial` por cada lectura nueva, acotado a las últimas
`MAX_PUNTOS_HISTORIAL = 80` (descartando las más viejas con `takeLast()`).
Se conectó `GraficoTendenciaView` en `DetalleHuertaActivity`/
`activity_detalle_huerta.xml`: tarjeta nueva "Tendencia reciente" con dos
chips (Humedad/Temp., reusando los drawables `bg_chip_capa`/
`bg_chip_capa_seleccionado` que ya existían para el mini-mapa) que alternan
`GraficoTendenciaView.setDatos(huerta.historial, esHumedad)`; la tarjeta
queda oculta si hay menos de 2 puntos acumulados. La selección de métrica
se conserva entre refrescos del polling de 3s. Se agregaron 2 tests a
`HuertaJsonTest.kt` (round-trip del historial preservando orden/valores, y
compatibilidad con JSON viejo sin la clave). Verificado con
`./gradlew compileDebugKotlin` (`BUILD SUCCESSFUL`, tras una compilación
limpia — un primer intento incremental dio un falso
`Unresolved reference` por caché de Kotlin corrupta, resuelto con
`./gradlew --stop` + `--rerun-tasks`, no era un error real de código) y
`./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL, 24/24 tests pasando**
(8 de `HuertaJsonTest` + 16 de `ValidacionesHuertaTest`, confirmado en
`app/build/test-results/testDebugUnitTest/`)._
`GraficoTendenciaView.kt` (173 líneas) y el modelo `PuntoHistorial` están
completos y bien implementados, pero **no están conectados a ningún
layout ni Activity** — confirmado, no aparecen referenciados fuera de su
propio archivo. La razón, según el propio código, es que
`HuertaRepository` solo guarda `ultimaLectura`, no una serie temporal.
**Acción:** agregar una lista acotada (ej. últimas 50-100 lecturas) por
huerta al modelo persistido, alimentarla desde
`actualizarLectura()`, y conectar `GraficoTendenciaView` en
`DetalleHuertaActivity`. Si se decide que no vale la pena, la
alternativa igual de válida es borrar `GraficoTendenciaView.kt` y
`PuntoHistorial` para no dejar código muerto en el repo.

### 9. Conectar `dialog_diagnostico_wifi.xml` — ✅ HECHO
**Prioridad: Media**
_Implementado: nuevo botón "¿Por qué no conecta?" en
`activity_tutorial_conexion.xml`, visible solo en `ConnectionState.Error`,
que infla el diálogo existente. "Reintentar conexión" dentro del diálogo
llama a `iniciarConexion()` de nuevo. Compila y no rompe nada (verificado)._
El layout completo (4 pasos, iconografía, botón "Reintentar conexión")
existe en `res/layout/`, junto con todos los strings necesarios, pero
ninguna Activity lo infla — confirmado, no aparece en ningún `.kt`.
Es la ayuda natural para cuando `TutorialConexionActivity` dispara
`onError` y el usuario no entiende por qué no conecta.
**Acción:** inflar ese diálogo desde `TutorialConexionActivity` cuando
`ConnectionState` pasa a `Error`, en vez de (o adicionalmente a)
cualquier mensaje corto que se muestre hoy.

### 10. Unificar convenciones de UI dentro del proyecto — ✅ HECHO
**Prioridad: Baja**
_Implementado junto con el punto 7 (mismo refactor, ver ahí el detalle
completo): MapaHuertasActivity pasó de findViewById manual a
ViewBinding (ActivityMapaHuertasBinding), y de AlertDialog.Builder a
MaterialAlertDialogBuilder en los tres diálogos que quedaron
(opciones, eliminar, aviso de datos corruptos) más los dos que se
extrajeron (editar, chat IA). Verificado con ./gradlew
compileDebugKotlin y ./gradlew testDebugUnitTest: ambos BUILD
SUCCESSFUL en esta máquina._
Confirmado: `MapaHuertasActivity` sigue usando `findViewById` manual
mientras el resto de las Activities nuevas usa ViewBinding; los
diálogos alternan entre `AlertDialog.Builder`
(`MapaHuertasActivity`) y `MaterialAlertDialogBuilder`
(`DashboardActivity`). Ninguna de las dos formas está mal, pero la
mezcla dificulta mantenimiento para quien no conoce ya el proyecto
(útil pensando en que sos 4 en el equipo).
**Acción:** al tocar `MapaHuertasActivity` (punto 7), aprovechar para
migrarla a ViewBinding y `MaterialAlertDialogBuilder` de una vez.

### 11. Decidir el destino de las features "diseñadas pero no conectadas" — ✅ HECHO
**Prioridad: Baja**
_Corrección (2026-09-13): al revisar CONTEXTO_PROYECTO.md (sección 9),
resultó que esto ya estaba anotado de una pasada anterior sin marca en
este plan — dice explícitamente "Es una feature planeada pero sin
empezar del lado de lógica" sobre `dialog_agregar_bitacora.xml` +
`item_evento_bitacora.xml`. Es justo lo que pedía la acción de este
punto (dejar registrado que es una feature futura sin empezar, para
que nadie del equipo pierda tiempo buscando dónde se conecta). No
hacía falta ningún cambio de código ni de documentación, solo corregir
el estado de este punto._
Además del punto 9, `dialog_agregar_bitacora.xml` +
`item_evento_bitacora.xml` (una futura "bitácora" de eventos por
huerta) existen como layout pero no tienen ni modelo de datos ni
Activity que los use — confirmado, no hay `EventoBitacora` en el
código.
**Acción:** si no entra en el alcance del TP, está bien dejarlos, pero
conviene anotarlo explícitamente como "feature futura, sin empezar"
(ya lo dice `CONTEXTO_PROYECTO.md`) para que nadie del equipo pierda
tiempo buscando dónde se conecta.

---

## Confiabilidad

### 12. Avisar al usuario cuando el JSON de `HuertaRepository` está corrupto — ✅ HECHO
**Prioridad: Media**
_Implementado: `listar()` ahora loguea (`Log.e`), guarda el JSON crudo que
falló en una key de backup separada (`huertas_json_backup_corrupto`) y deja
una bandera de una sola lectura (`consumirAvisoDeCorrupcion()`).
`MapaHuertasActivity` la chequea después de la primera carga y muestra un
AlertDialog explicativo una única vez. Compila y no rompe nada (verificado)._
Confirmado en el código: `listar()` atrapa cualquier excepción de
parseo y devuelve `emptyList()` en silencio. Si eso pasa y después el
usuario da de alta una huerta nueva, `persistir()` sobreescribe el
JSON entero — **se pueden perder todas las huertas guardadas sin que
nadie se entere de qué pasó.**
**Acción:** cuando el catch de `listar()` se dispare, loguearlo
(`Log.e`) y, antes de la primera escritura siguiente, hacer un backup
del JSON corrupto a un archivo aparte (o mostrar un aviso no bloqueante
la próxima vez que se abra `MapaHuertasActivity`) en vez de perderlo
silenciosamente.

### 13. Revisar el doble polling de `DashboardActivity` — ✅ HECHO
**Prioridad: Baja**
_Implementado: se sacó el segundo polling nativo (Thread + Handler cada
3s a `/data`). Ahora `actualizarDashboard()` en el JS del dashboard
(que ya hacía su propio fetch a `/estado` cada 2s) le pasa esos mismos
datos a Kotlin vía un puente nuevo, `SimonaAndroid.datosActualizados(json)`,
espejado en `simulador/servidor_simulado.py` Y en
`simulador/Firmware esp32/dashboard_html.h` (mismo criterio que el
puente de tema ya existente, `temaCambiado`). `DashboardActivity.kt`
implementa `PuenteAsistente.datosActualizados()`, que parsea el JSON y
llama a `HuertaRepository.actualizarLectura()` — mismo shape de campos
que consumía el polling viejo. Se eliminó todo el código del segundo
polling (función, Handler, Thread, constante `POLLING_INTERVAL_MS`,
imports de `HttpURLConnection`/`URL` sin uso) y las llamadas a
`onPause()`/`onResume()`, que ahora solo pausan/reanudan el propio
`WebView` (su `pauseTimers()`/`resumeTimers()` ya cortan el
`setInterval()` del JS, sin necesidad de nada adicional del lado
nativo). Trade-off aceptado y documentado en el código: si el WebView
no cargó o el JS no corrió, ya no hay un Thread nativo de respaldo
persistiendo lecturas — a cambio de no duplicar la llamada de red.
Verificado con `./gradlew compileDebugKotlin` (`BUILD SUCCESSFUL`) y
con el smoke test de Playwright del punto 17
(`NODE_PATH=/home/benja/.pw-tools/node_modules node simulador/tests/smoke_test.js`,
**5/5 tests pasaron**), que ejercita el JS modificado del dashboard._
Confirmado y ya documentado en el propio código: `DashboardActivity`
hace su propio polling a `/data` cada 3s en Kotlin, en paralelo al
polling de 2s que ya hace el JS del dashboard HTML — duplicación
aceptada a propósito, pero cuesta batería y datos si el celular no
tiene wifi de verdad (aunque sea la LAN local del ESP32, sigue siendo
radio prendida).
**Acción:** evaluar si alcanza con que el WebView le pase los datos que
ya está leyendo el JS a Kotlin vía `JavascriptInterface`, eliminando
el segundo polling nativo.

### 14. Notificar fallos silenciosos de `AlertaHuertasWorker` — ✅ HECHO
**Prioridad: Baja**
_Corrección (2026-09-13): al revisar el código para este punto,
resultó que ya estaba implementado de una pasada anterior —
`AlertaHuertasWorker.doWork()` ya tiene logging explícito en cada rama
(`Log.d` en el chequeo normal y en cuántas huertas tienen sed, `Log.w`
si falta el permiso `POST_NOTIFICATIONS`, `Log.e` + `Result.retry()` si
`HuertaRepository.listar()` u otra parte del chequeo explota). No hacía
falta ningún cambio de código, solo corregir el estado de este punto._
El worker relee `HuertaRepository` cada 6 horas y notifica si hay sed,
pero no hay evidencia en el código de qué pasa si el propio
`HuertaRepository.listar()` falla (ver punto 12) durante esa lectura:
probablemente el worker simplemente no notifica nada, sin dejar rastro.
**Acción:** agregar logging explícito en el `CoroutineWorker` para que,
si alguna vez el usuario reporta "dejó de avisarme", haya algo que
revisar en logcat/logs persistentes.

### 15. Confirmación visible cuando `POST /config` agota los 3 reintentos — ✅ HECHO
**Prioridad: Media**
_Corrección (2026-09-13): al revisar el código para este punto, también
resultó ya implementado (probablemente junto con el punto 9) —
`error_timeout` ("No se encontró la red SIMONA…") se muestra vía
`ConnectionState.Error` cuando falla la conexión WiFi en sí, y
`error_sincronizar_config` ("No se pudo sincronizar la configuración,
mostrando datos del dispositivo") se muestra como `Toast` en
`TutorialConexionActivity.empujarConfiguracionAlDispositivo()` cuando
se agotan los 3 reintentos de `POST /config` pero el WiFi sí conectó —
son dos mensajes distintos para las dos causas distintas, exactamente
lo que pedía este punto. No hacía falta ningún cambio de código._
`TutorialConexionActivity` reintenta la config 3 veces con 1.5s de por
medio antes de abrir el dashboard, según `CONTEXTO_PROYECTO.md` y el
propio flujo de `ConnectionState`. No verifiqué en detalle el mensaje
final al usuario si los 3 reintentos fallan — vale la pena confirmar
que el estado `Error` muestre algo más específico que un mensaje
genérico, sobre todo combinado con el punto 9 (diagnóstico de WiFi).
**Acción:** al conectar el diálogo de diagnóstico, asegurarse de que el
mensaje de error distinga "no se pudo conectar al WiFi" de "conectó
pero `/config` nunca respondió" — son causas y soluciones distintas.

---

## Testing

### 16. Agregar tests unitarios de JVM — ✅ HECHO
**Prioridad: Alta**
_Implementado en dos partes, ambas ya en el working tree: (a)
`HuertaJson.kt` (serialización pura extraída de `HuertaRepository`,
punto separado del plan) con `HuertaJsonTest.kt`, 6 tests; (b)
`ValidacionesHuerta.kt`, nuevo — las validaciones de `nombreValido`,
`passwordValida` (≥8 caracteres WPA2), `rangoValido` (min < max) y
`anchoHumedadValido`/`rangoConAnchoMinimoValido` (ancho mínimo de 10
puntos) se extrajeron de `DatosHuertaActivity` y `AjustarRangosActivity`
a funciones puras de JVM, sin tocar la lógica en sí — las Activities
ahora delegan ahí (confirmado con `grep`). Cubierto por
`ValidacionesHuertaTest.kt`, 16 tests (casos límite: ancho exacto de 10,
min == max, password de exactamente 8 caracteres, nombre solo con
espacios, etc.). No se hizo el test de `HuertaRepository` CRUD completo
con Robolectric que sugería la acción original porque `HuertaJson` ya
cubre toda la lógica de serialización/parseo que tenía valor testear
como JVM puro; el resto de `HuertaRepository` (leer/escribir
`SharedPreferences`, cifrado vía `CryptoUtil`) necesita un `Context`
real y queda mejor como test instrumentado si se decide agregarlo.
Verificado con `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL**,
**22/22 tests pasando** (6 + 16, confirmado leyendo los XML de
resultados en `app/build/test-results/testDebugUnitTest/`)._
Confirmado: `app/src/test` no existe (solo `androidTest`, con un único
`InstrumentedFlowsTest.kt`). `HuertaRepository` es el candidato ideal
para arrancar: es lógica pura (serialización JSON, tolerancia a
corrupción, CRUD) sin dependencias de Android más allá de
`SharedPreferences`, fácil de testear con Robolectric o mockeando el
`Context`.
**Acción:** agregar `app/src/test/java/com/simona/app/` con tests para
`HuertaRepository` (guardar/leer/eliminar, JSON corrupto, campos
faltantes de versiones viejas) y para las validaciones de
`DatosHuertaActivity` (contraseña ≥8 caracteres) y `AjustarRangosActivity`
(ancho mínimo de 10 puntos en humedad, min < max en los 4 rangos).

### 17. Aprovechar Playwright contra el simulador — ✅ HECHO
**Prioridad: Media**
_Implementado: `simulador/tests/smoke_test.js`. Levanta
`servidor_simulado.py` en el puerto 8123, corre 5 escenarios (config
válida, config rechazada por rango de humedad angosto, gauge
actualizándose solo, riego manual on/off, refill de tanque) usando la
instalación de Playwright ya presente en `~/.pw-tools`. **5/5 tests
pasaron**, corrida completa en ~30s. Uso:
`NODE_PATH=/home/benja/.pw-tools/node_modules node simulador/tests/smoke_test.js`._
Tenés Playwright disponible en el entorno y hoy no se usa contra nada
del proyecto. El dashboard HTML que sirve `simulador/servidor_simulado.py`
es HTML+JS puro corriendo en un puerto local — un target perfecto para
tests end-to-end repetibles sin necesitar un celular ni un ESP32 real:
levantar el simulador, abrir el dashboard con Playwright, y verificar
que el gauge se actualiza, que el botón de riego manual prende el relé
simulado, que `/refill_tanque` sube el tanque a 100, etc.
**Acción:** armar un script de Playwright (Python o Node, lo que uses)
que levante `python3 simulador/servidor_simulado.py` como proceso
hijo, corra unos escenarios contra `http://localhost:8000`, y lo deje
como smoke test repetible antes de cada entrega o demo.

---

## UX y accesibilidad

### 18. Manejar el estado vacío de `MapaHuertasActivity` — ❌ CORREGIDO: ya estaba hecho
**Prioridad: Baja**
_Corrección (2026-09-13): esta evaluación original estaba mal —
`activity_mapa_huertas.xml` ya tiene un `estadoVacio` completo con
CTA (`btnNuevaHuertaVacio`, texto "Crear mi primera huerta") y
`MapaHuertasActivity.cargarDatos()` ya lo muestra/oculta según
corresponda. No hacía falta ningún cambio acá._
No encontré evidencia en el código de un estado explícito para "todavía
no hay huertas cargadas" en el home — la primera vez que se abre la app
(o después de eliminar la última huerta), la pantalla probablemente
queda con la lista/mapa vacíos sin ninguna guía.
**Acción:** agregar un estado vacío con call-to-action directo a
"Nueva huerta" (`SeleccionarPerfilActivity`) cuando `listar()` devuelve
lista vacía.

### 19. Accesibilidad (TalkBack) en los íconos custom — ✅ HECHO
**Prioridad: Baja**
_Corrección (2026-09-13): al revisar el código para este punto, resultó
que ya estaba implementado de una pasada anterior sin dejar marca en este
plan — `MapaHuertasActivity.kt` tiene `importantForAccessibility =
View.IMPORTANT_FOR_ACCESSIBILITY_NO` en los íconos puramente decorativos
(chevron, gota de estado, ícono de "sin lecturas", foto/pin del mapa) y
`contentDescription` real en el ícono interactivo (`ic_more_vert`, vía el
string `opciones_huerta_cd`); `GaugeHumedadView.kt` (un `View` de Canvas
puro, sin texto real que TalkBack pueda leer) setea su propio
`contentDescription` en cada `setValor()` con el string
`gauge_humedad_descripcion` ("Humedad X por ciento, <estado>"). Los
layouts XML ya tenían `contentDescription` en el 100% de sus `ImageView`
(confirmado con un script que revisa cada `<ImageView>.../>` de
`res/layout/*.xml` — cero resultados sin ese atributo). No hacía falta
ningún cambio de código, solo corregir el estado de este punto._
El proyecto tiene bastantes íconos vectoriales propios
(`ic_gota_full`, `ic_termometro`, `ic_sol`, etc.) usados como
indicadores de estado en tarjetas y en `MiniMapaCapasView`/
`GaugeHumedadView`. No verifiqué `contentDescription` sistemático en
esos usos — si faltan, un usuario con TalkBack no puede saber qué
significa cada ícono/gauge.
**Acción:** pasar una revisión rápida con el escáner de accesibilidad
de Android Studio sobre las pantallas principales y completar los
`contentDescription` que falten, en particular en `MapaHuertasActivity`
y `DetalleHuertaActivity`.

---

## Housekeeping del repo

### 20. Limpiar archivos sueltos y revisar `repomix-output.xml` — ✅ HECHO
**Prioridad: Baja**
_Corrección (2026-09-13): al revisar `git status`, resultó que esto ya
estaba hecho de una pasada anterior sin marca en este plan — los `.txt`
sueltos (`gradle.properties(1).txt`, `local.properties(1).txt`, etc.)
ya están borrados del working tree, `repomix-output.xml` ya está en
`.gitignore` y su baja ya está stageada (`git status` lo marca `D` en
el índice). Solo falta el `git add -A` normal de un commit para
stagear las bajas de los `.txt` sueltos junto con el resto de los
cambios de esta sesión — no es un paso extra de este punto puntual. No
hacía falta ningún cambio nuevo, solo corregir el estado de este
punto._
Confirmé dos cosas en la raíz del repo:
- Hay duplicados de descargas de navegador sin trackear en git
  (`gradle.properties(1).txt`, `gradle.properties(2).txt`,
  `local.properties(1)(1).txt`, `local.properties(1).txt`,
  `local.properties.txt`) que no aportan nada y ensucian el explorador
  de archivos.
- `repomix-output.xml` (336 KB, un dump completo del repo pensado para
  pegarle a una IA) **está trackeado en git** (`git ls-files` lo
  confirma). No contiene la key real hoy porque `secrets.xml` está bien
  excluido, pero es un archivo generado que se desactualiza solo y no
  debería vivir versionado — y cualquier cambio futuro en qué incluye
  podría filtrar algo sensible sin que nadie se dé cuenta en el commit.
**Acción:** borrar los `.txt` sueltos, agregar `repomix-output.xml` (o
`repomix-output.*`) a `.gitignore` y sacarlo del tracking con
`git rm --cached repomix-output.xml`.

---

### Cómo usar este archivo
No hace falta resolver los 20 puntos en orden — las de **Prioridad
Alta** (1, 2, 3, 16) son las que conviene mirar primero: dos son
seguridad real (key expuesta, backup de contraseñas WiFi), una es el
bug activo del asistente, y una es la ausencia total de tests unitarios
sobre la única pieza de persistencia que tiene el proyecto.
