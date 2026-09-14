# PLAN_MEJORAS_UX_20.md — 20 mejoras de frontend/UX para SIMONA

> **Estado (2026-09-13, cuarta pasada): 20 de 20 — plan cerrado.** La
> tercera pasada había dejado 19/20 (7 puntos encontrados ya
> implementados sin marca, más el 18 confirmado como genuinamente
> pendiente por falta de un asset vectorial). Esta pasada resolvió
> justamente eso: vectorizó `ic_splash_logo.png` con una herramienta de
> trazado automático (`vtracer`), lo convirtió a `AnimatedVectorDrawable`
> real de Android, y lo verificó instalando un build debug en el
> celular conectado por USB y capturando el splash en pantalla (ver
> punto 18 para el detalle completo). Generado a partir de una revisión
> directa de los layouts XML, estilos, colores y comentarios de código
> ("evidencia concreta contra el código", mismo criterio que
> `PLAN_MEJORAS_20.md`, que ya está 100% cerrado — este es un plan
> nuevo y separado). Complementa a `CONTEXTO_PROYECTO.md`.

Convención: **Prioridad** = Alta (bloquea o confunde al usuario hoy) /
Media (deuda de consistencia/perf que conviene resolver) / Baja
(pulido, no urgente).

---

## Accesibilidad / áreas de toque

### 1. Botón "⋮" de opciones en cada tarjeta de huerta — ✅ HECHO
**Prioridad: Alta**
_Implementado: `item_huerta_card.xml`, `ivMas` pasó de 32×32dp (padding
4dp) a 48×48dp (padding 12dp) — el ícono visual queda del mismo tamaño
(24dp), solo creció el área de toque. Verificado con
`./gradlew compileDebugKotlin`._
`item_huerta_card.xml`: 32×32dp, por debajo del mínimo de 48dp de
Material, y es un botón de uso frecuente (abre el menú de
editar/eliminar por huerta).
**Acción:** subir a 48×48dp (el ícono interno puede seguir siendo más
chico, el área de toque es lo que tiene que crecer).

### 2. Toggle claro/oscuro del header — ✅ HECHO
**Prioridad: Media**
_Implementado: `activity_mapa_huertas.xml`, `btnThemeToggle` de 40×40dp
(padding 8dp) a 48×48dp (padding 12dp); comentario "Bug UI/UX #1" que
mencionaba el tamaño viejo actualizado en el mismo cambio._
40×40dp, mismo problema que el punto 1, algo menos crítico por ser de
uso menos frecuente.
**Acción:** subir a 48×48dp.

### 3. Botones de tabs "Lista"/"Mapa" — ✅ HECHO
**Prioridad: Media**
_Implementado: `tabBtnLista`/`tabBtnMapa` de 40dp a 48dp de alto._
40dp de alto, por debajo de 48dp.
**Acción:** subir a 48dp de alto.

---

## Formularios

### 4. Sin toggle de mostrar/ocultar contraseña de WiFi — ✅ HECHO
**Prioridad: Alta**
_Implementado junto con el punto 5: `DatosHuertaActivity` (alta de
huerta), campo de contraseña migrado a `TextInputLayout` con
`app:endIconMode="password_toggle"`. El mismo campo en el diálogo de
editar huerta (`dialog_editar_huerta.xml`) también migrado, aunque ahí
ya mostraba la contraseña en texto plano (`textVisiblePassword`) por
diseño previo — se mantiene visible por defecto pero ahora con
`TextInputLayout` consistente. Verificado con
`./gradlew compileDebugKotlin`._
El campo es `inputType="textPassword"` plano, sin ícono para verificar
lo tipeado — las contraseñas WiFi suelen ser largas y un typo ahí
rompe la conexión más adelante en `TutorialConexionActivity`.
**Acción:** `TextInputLayout` con `app:passwordToggleEnabled="true"`
(implica resolver el punto 5 para este campo al menos).

### 5. `EditText` plano en vez de `TextInputLayout`/`TextInputEditText` — ✅ HECHO (parcial)
**Prioridad: Media**
_Implementado en los dos formularios de texto libre: `DatosHuertaActivity`
(nombre + contraseña) y `dialog_editar_huerta.xml` (nombre + contraseña),
ambos migrados a `TextInputLayout`/`TextInputEditText`. Los `EditText` de
`AjustarRangosActivity` y de los rangos numéricos de
`dialog_editar_huerta.xml` (`RangoEditText`, campos de 64dp junto a un
`RangeSlider`) se dejaron como están a propósito: son campos numéricos
angostos sincronizados con un slider, no formularios de texto libre, y
un floating label ahí no aporta (no hay espacio y no tienen hint propio).
Verificado con `./gradlew compileDebugKotlin`._
Pierden floating labels y estado de error nativo (borde rojo),
inconsistente con el resto de la app (100% Material en botones/cards/
diálogos).
**Acción:** migrar los formularios (`DatosHuertaActivity`,
`AjustarRangosActivity` donde aplique) a `TextInputLayout` +
`TextInputEditText`.

### 6. `ScrollView` de altura fija (360dp) en el diálogo de editar huerta — ✅ HECHO
**Prioridad: Media**
_Implementado: el `ScrollView` (ahora con id `scrollEditarContenido`)
pasa a `wrap_content`. `DialogoEditarHuerta.kt` mide el contenido en
`dialog.setOnShowListener` (antes de mostrarse, `height` todavía es 0)
y solo lo acota a ~55% de la altura de pantalla si el contenido medido
realmente no entra — en pantallas donde el contenido es más chico que
eso, ya no queda espacio vacío de más. Verificado con
`./gradlew compileDebugKotlin`._
En pantallas chicas o con teclado abierto puede recortar contenido o
dejar espacio de más.
**Acción:** `layout_height="wrap_content"` con `maxHeight` vía código,
o `NestedScrollView` con `layout_constraintHeight_max`.

---

## Sistema de diseño

### 7. Sin escala tipográfica sistematizada — ✅ HECHO (parcial)
**Prioridad: Baja**
_Encontrado ya implementado en el código al revisar este punto
(2026-09-13, tercera pasada), sin marca en este plan: dos estilos
nuevos en `themes.xml`, `TextAppearance.Simona.HeaderTitle` (22sp,
bold) y `TextAppearance.Simona.HeaderSubtitle` (13sp), aplicados a los
4 headers que repetían el mismo patrón título+subtítulo
(`activity_mapa_huertas.xml`, `activity_seleccionar_perfil.xml`,
`activity_ajustar_rangos.xml`, `activity_datos_huerta.xml` —
confirmado por `grep`). Eso cubre "los usos más repetidos" que pedía
la acción original. El resto de los ~10 tamaños (11–24sp) sigue
hardcodeado a propósito: son usos puntuales (chips, badges, textos de
diálogos chicos) sin un patrón repetido claro que amerite su propio
nombre en la escala — sistematizar esos de más sería forzar una
categoría donde no la hay, no completar la escala. Verificado con
`./gradlew compileDebugKotlin`: BUILD SUCCESSFUL._
10 tamaños de texto distintos (11–24sp) usados directo en layouts;
solo 2 `TextAppearance` custom definidos y casi sin uso.
**Acción:** definir una escala (ej. caption/body/subtitle/title) en
`themes.xml` y migrar los usos más repetidos.

### 8. Sin escala de espaciado sistematizada — ✅ HECHO
**Prioridad: Baja**
_Encontrado ya implementado en el código al revisar este punto
(2026-09-13, tercera pasada), sin marca en este plan: `dimens.xml`
tiene ahora una escala de 6 escalones (`simona_space_xxs` 4dp hasta
`simona_space_xl` 24dp, con comentario propio citando este punto y el
relevamiento por `grep` que le dio origen), y los usos en layouts que
coinciden EXACTO con alguno de esos 6 valores fueron migrados a
`@dimen`. Los valores impares/intermedios (2, 3, 6, 10, 14, 18, 22,
32, 96dp) se dejaron hardcodeados a propósito, documentado en el
propio comentario del archivo: forzarlos a un escalón de la escala
cambiaría el layout visual real, no solo le pondría nombre. Verificado
con `./gradlew compileDebugKotlin`: BUILD SUCCESSFUL._
`dimens.xml` define 3 valores (16/12/8dp) pero los layouts usan al
menos 10 valores hardcodeados sin pasar por ahí.
**Acción:** ampliar `dimens.xml` con la escala real usada y reemplazar
los hardcodeados más repetidos.

### 9. Dos azules casi idénticos para "húmedo" — ✅ HECHO
**Prioridad: Baja**
_Implementado: `simona_estado_humedo` pasó de `#1A75B3` (hardcodeado) a
`@color/simona_azul` (alias). Se confirmó por `grep` que se usa en
`MiniMapaCapasView.kt`, `GaugeHumedadView.kt` y `bg_pin_humedo.xml`, y
que `HuertaCardAdapter.kt` ya usaba `simona_azul` directo para este
mismo estado — unificar no cambia ningún comportamiento visual
(colores casi idénticos), solo la fuente de verdad. `values-night` no
lo redefine, así que el alias también aplica en modo oscuro sin tocar
nada ahí. Verificado con `./gradlew compileDebugKotlin`._
`simona_azul` (#1A6FA8) vs `simona_estado_humedo` (#1A75B3), definidos
por separado.
**Acción:** unificar en un solo color, chequeando que ninguna de las
dos referencias dependa de que sean valores distintos.

### 10. Textos hardcodeados directo en layouts — ✅ HECHO
**Prioridad: Media**
_Implementado: 10 strings nuevos en `strings.xml`
(`detalle_sin_lecturas_titulo/desc`, `detalle_ir_dashboard`,
`detalle_mapa_metricas`, `detalle_deposito_agua`,
`detalle_tendencia_reciente`, `detalle_chip_humedad/temp`,
`tutorial_conexion_titulo`, `diagnostico_titulo_dialogo`) y los 4
layouts del punto migrados. Caso aparte:
`dialog_editar_huerta.xml` tenía el título "Editar Huerta" hardcodeado,
pero `DialogoEditarHuerta.kt` lo pisa siempre en runtime
(`tvEditarTitulo.text = getString(R.string.editar_huerta_titulo,
huerta.nombre)`) — para ese caso la corrección correcta es
`tools:text` (placeholder de diseño, nunca se ve en la app real), no
un string real sin uso. Verificado con `./gradlew compileDebugKotlin`._
En `activity_detalle_huerta.xml`, `activity_tutorial_conexion.xml`,
`dialog_editar_huerta.xml`, `dialog_diagnostico_wifi.xml` — el resto
del proyecto sí usa `strings.xml`.
**Acción:** extraer a `strings.xml` con nombres consistentes con el
resto del proyecto.

---

## Performance/arquitectura visual

### 11. `RecyclerView` de huertas anidado en `NestedScrollView` (wrap_content) — ✅ HECHO
**Prioridad: Media**
_Implementado: se sacó el único `NestedScrollView` que envolvía TODO
el contenido (header incluido). `activity_mapa_huertas.xml` pasa a un
`LinearLayout` raíz vertical: header fijo (wrap_content), chips+tabs
fijos (wrap_content), y un `FrameLayout` (id `contenidoHome`, antes en
el `LinearLayout` de padding) con `layout_height="0dp"` +
`layout_weight="1"` que aloja las tres alternativas ya existentes
(mismos ids, misma lógica de visibilidad en
`MapaHuertasActivity.kt` — sin cambios ahí): `estadoVacio` y
`seccionMapa` van cada uno en su propio `NestedScrollView` (contenido
corto, no necesitan reciclado), y `seccionLista` aloja el
`RecyclerView` con `layout_height="0dp"` + `layout_weight="1"` (ya no
`wrap_content` ni `nestedScrollingEnabled="false"`) — ahora sí scrollea
y recicla por sí mismo. `contenidoHome` sigue siendo el ancestro común
de las tres alternativas, así que el `Fade` del punto 20
(`TransitionManager.beginDelayedTransition(binding.contenidoHome, ...)`)
sigue funcionando sin tocar ese código. Verificado con
`./gradlew compileDebugKotlin`: BUILD SUCCESSFUL._
Anulaba el reciclado de vistas — inflaba todas las tarjetas de una vez,
el mismo problema que el refactor del punto 7 de `PLAN_MEJORAS_20.md`
buscaba resolver.
**Acción:** sacar el `RecyclerView` del `NestedScrollView` (altura
`match_parent`/`0dp` con peso, o rediseñar el layout para que solo el
`RecyclerView` scrollee).

---

## Consistencia de código/docs

### 12. Comentario desactualizado en `values-night/colors.xml` — ✅ HECHO
**Prioridad: Baja**
_Implementado: comentario corregido, saca `azul_suave` de la lista de
"se mantienen iguales en ambos modos" y agrega una aclaración de por
qué (referencia a este mismo punto del plan)._
Dice que `simona_azul_suave` se mantiene igual en ambos modos, pero el
archivo lo redefine.
**Acción:** corregir o borrar el comentario.

---

## Responsive / dispositivos

### 13. Sin bloqueo de orientación ni layouts landscape — ✅ HECHO
**Prioridad: Baja**
_Implementado: `android:screenOrientation="portrait"` agregado a las 7
`<activity>` de `AndroidManifest.xml` (opción "barata" que menciona la
acción original, en vez de adaptar cada layout con elementos de altura
fija a landscape). Verificado con `./gradlew compileDebugKotlin`._
Pantallas con elementos de altura fija (croquis 220–280dp, ScrollView
de 360dp) no están pensadas para landscape.
**Acción:** decidir entre bloquear orientación (`screenOrientation`)
o adaptar los layouts críticos — lo primero es mucho más barato para
un TP.

---

## Estados y feedback

### 14. Sin loading/skeleton al cargar el WebView del dashboard — ✅ HECHO
**Prioridad: Media**
_Implementado: nuevo `loadingOverlay` en `activity_dashboard.xml`
(`ProgressBar` + texto "Cargando panel…", visible por defecto en el
XML para la primera carga). `onPageStarted` lo muestra en cada recarga
(reintentos automáticos incluidos) y `onPageFinished` lo oculta junto
con `waitingOverlay`; `mostrarEsperandoDispositivo()` (caso "no
responde") también lo oculta al mostrar su propio overlay, para que no
queden los dos superpuestos. Verificado con
`./gradlew compileDebugKotlin`: BUILD SUCCESSFUL._
Solo había overlay para "no responde", no para la carga inicial normal.
**Acción:** `ProgressBar`/skeleton simple mientras `onPageFinished` no
disparó.

### 15. Sin pull-to-refresh en la lista de huertas — ✅ HECHO
**Prioridad: Baja**
_Implementado: `RecyclerView` (`rvHuertas`) envuelto en un
`SwipeRefreshLayout` (id `swipeRefreshHuertas`), posible ahora que el
punto 11 le dio su propio `layout_height="0dp"`+`weight` en vez de
`wrap_content`. Nueva dependencia `androidx.swiperefreshlayout:
swiperefreshlayout:1.1.0` (librería oficial de AndroidX, un solo
widget, misma familia que el resto de dependencias del proyecto).
`setOnRefreshListener` llama a `cargarDatos()` (100% local/sincrónico,
sin red) y corta `isRefreshing` en el mismo hilo, sin delay artificial.
Verificado con `./gradlew compileDebugKotlin`: BUILD SUCCESSFUL._
Única forma de refrescar era volver a entrar a la pantalla
(`onResume`).
**Acción:** `SwipeRefreshLayout` envolviendo el `RecyclerView`.

### 16. Chips de resumen (Total/Con sed/Óptimas) no clickeables — ✅ HECHO
**Prioridad: Media**
_Implementado: los 3 chips (`chipTotal`/`chipConSed`/`chipOptimas`,
ids nuevos) ahora filtran `rvHuertas` al tocarlos (Total = sin filtro,
Con sed / Óptimas = filtran por ese estado), con ripple
(`?attr/selectableItemBackground`) y un borde de acento
(`bg_chip_resumen_seleccionado.xml`, drawable nuevo) marcando cuál está
activo. Tocar el mismo chip que ya está activo lo desactiva. El
contador de huertas (`tvContadorHuertas`) respeta el filtro; el
croquis/mapa sigue mostrando todas las huertas (filtrar pines queda
fuera de este punto). Verificado con `./gradlew compileDebugKotlin`._
Confirmado: no tienen click listener, son puramente informativos, pero
visualmente parecen tarjetas de estado tocables (affordance ambigua).
**Acción:** o agregar filtro real al tocarlos, o ajustar el estilo
visual para que no parezcan interactivos (sombra/elevación de botón).

---

## Pulido visual

### 17. Salto vertical brusco entre título y botón "Nueva huerta" — ✅ HECHO
**Prioridad: Baja**
_Encontrado ya implementado en el código al revisar este punto
(2026-09-13, tercera pasada), sin marca en este plan:
`activity_mapa_huertas.xml` unificó todo en una sola fila horizontal
(logo + columna título/subtítulo con weight=1 + columna angosta con
el toggle de tema arriba y el botón "Nueva huerta" apilado debajo),
en vez de la fila extra a todo el ancho que había antes — hay un
comentario en el propio layout citando este punto con el detalle.
Verificado con `./gradlew compileDebugKotlin`: BUILD SUCCESSFUL._
Ya se corrigió el corte de texto (comentario "Bug UI/UX #1" en el
código), pero quedó una fila extra que podría integrarse mejor.
**Acción:** revisar el layout del header y unificar en una sola fila
o alinear mejor el espaciado.

### 18. Ícono del splash screen es un PNG estático — ✅ HECHO
**Prioridad: Baja**
_Implementado (2026-09-13, tercera pasada, después de la nota de "confirmado,
no es un supuesto viejo" de más abajo): el bloqueo real era la falta de un
asset vectorial del logo, no la dificultad de la animación en sí — se
resolvió generando ese vector en vez de descartar el punto. Proceso:
`ic_splash_logo.png` vectorizado con `vtracer` (Python, instalado en un
venv temporal, sin tocar el entorno del sistema), probando 3 configuraciones
distintas y verificando cada una por captura de pantalla con Playwright
antes de elegir (269 paths con ruido de anti-aliasing → 11 paths pero
perdía el degradado/color del suelo → **58 paths, el punto justo**: conserva
el degradado azul-a-verde de la gota, el marrón del suelo y los nodos del
circuito). Ese SVG se convirtió a mano a `ic_splash_logo_vector.xml`
(VectorDrawable real de Android, 432dp, solo comandos M/C/Z — sin ninguna
feature de SVG no soportada por Android), envolviendo cada `<path>` en su
propio `<group>` con `translateX/Y` para preservar el posicionamiento sin
tener que recalcular coordenadas a mano. Encima de eso, `ic_splash_logo_animated.xml`
(`animated-vector`) anima el grupo raíz `logo_group` con un scale-in 0.7→1.0
(`splash_icon_scale_in.xml`, `res/animator/`, 650ms, `decelerate_cubic` —
se prefirió sobre `overshoot` para que quede serio, no "playful").
`themes.xml` (`Theme.Simona.Splash`) actualizado para usar el ícono nuevo
+ `windowSplashScreenAnimationDuration="650"`. Verificado en 3 niveles:
`./gradlew compileDebugKotlin` (BUILD SUCCESSFUL, valida el XML vía AAPT2),
`./gradlew installDebug` en el celular real conectado por USB (BUILD
SUCCESSFUL), y una captura de pantalla real del splash en el dispositivo
(`adb shell monkey` para relanzar + `adb exec-out screencap`) que confirma
el logo vectorizado se ve completo y fiel al original, sin distorsión ni
paths faltantes; `adb logcat` sin ningún error/crash relacionado al
inflar el drawable. El PNG original (`ic_splash_logo.png`) queda en el
repo sin uso directo (era la fuente del trazado) — se puede borrar más
adelante, R8/shrinkResources ya lo descarta solo del APK de release._
La Splash Screen API soporta `AnimatedVectorDrawable`.
**Acción:** evaluar si vale la pena el esfuerzo de crear un
`AnimatedVectorDrawable` para un remate más pulido (bajo impacto,
esfuerzo no trivial) — resuelto vectorizando el PNG existente con una
herramienta de trazado automático en vez de recrearlo a mano.

### 19. Sin onboarding para el FAB del asistente de IA — ✅ HECHO
**Prioridad: Baja**
_Encontrado ya implementado en el código al revisar este punto
(2026-09-13, tercera pasada), sin marca en este plan: `OnboardingPrefs.kt`
nuevo (mismo patrón que `ThemePrefs`, un solo booleano en
`SharedPreferences`) + `popup_onboarding_asistente.xml` nuevo
(`MaterialCardView` con título/texto/botón "Entendido"), mostrado como
`PopupWindow` anclado al FAB desde
`MapaHuertasActivity.mostrarOnboardingAsistenteSiHaceFalta()`, llamado
una sola vez si `OnboardingPrefs.fabIaYaVisto()` es falso, y marcado
como visto tanto al tocar "Entendido" como al descartar el popup de
cualquier otra forma (`setOnDismissListener`). Verificado con
`./gradlew compileDebugKotlin`: BUILD SUCCESSFUL._
Es fácil que un usuario nuevo no note el FAB la primera vez.
**Acción:** un tooltip/`ShowcaseView` simple la primera vez que se abre
`MapaHuertasActivity` (persistiendo un flag en `SharedPreferences` para
no repetirlo).

### 20. Transición abrupta entre estado "vacío" y "con lista" — ✅ HECHO
**Prioridad: Baja**
_Implementado: `TransitionManager.beginDelayedTransition` con `Fade`
(180ms) sobre el contenedor `contenidoHome` (id nuevo), disparado solo
cuando el estado vacío/con-huertas efectivamente cambia (no en cada
refresh ni en cada click de filtro del punto 16, para no interferir
con las animaciones propias del `DiffUtil` del `RecyclerView`).
Verificado con `./gradlew compileDebugKotlin`._
No hay animación/fade más allá del `animateLayoutChanges` genérico del
contenedor raíz.
**Acción:** `TransitionManager.beginDelayedTransition` con un fade
simple al alternar visibilidad.

---

### Cómo usar este archivo
**20 de 20 — cerrado.** El único punto que había quedado
genuinamente pendiente (el 18, ícono de splash animado) se resolvió
vectorizando el logo con una herramienta de trazado automático en vez
de darlo por imposible — ver el detalle completo en ese punto. No
queda ningún trabajo de este plan por hacer.
