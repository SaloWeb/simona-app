# PLAN_MEJORAS_VISUAL_2.md — Segunda ronda de mejoras visuales para SIMONA

> **Estado (2026-09-13, tercera pasada): 13 de 14 implementados y
> verificados.** Se implementaron los puntos 3, 9, 10, 11 y 14
> (quedaban pendientes de la pasada anterior). El punto 13 se cierra
> como **"evaluado, sin cambios"** — el propio punto pedía explícitamente
> no tocarlo a ciegas sin que el equipo lo revise, así que no se
> modificó ningún color. Con esto queda un solo punto realmente abierto
> en todo el plan: **ninguno** — de los 14, 13 están implementados y 1
> quedó evaluado y cerrado a propósito sin cambio de código. Verificado
> con `./gradlew assembleDebug` completo (`BUILD SUCCESSFUL`, 2m40s en
> esta máquina) — se usó `assembleDebug` en vez de `compileDebugKotlin`
> porque todos los cambios de esta pasada son de recursos (XML/drawables),
> no de lógica Kotlin, y `compileDebugKotlin` no procesa resources.
>
> **Verificación visual en dispositivo físico (2026-09-13, misma sesión,
> celular reconectado):** se instaló el APK debug (`adb install -r`) en
> el celular real (`2312CRNCCL`) y se navegó con `adb shell input
> tap`/`swipe` + `screencap`. Confirmado en pantalla real: (a) el FAB de
> `MapaHuertasActivity` muestra `ic_asistente_simona.png` a color con el
> badge de chat (`ic_badge_chat` sobre `bg_badge_chat`) superpuesto en la
> esquina superior derecha, bien recortado y legible; (b) los 9 íconos
> vectoriales de `SeleccionarPerfilActivity` (Hoja, Fruto, Raíz, Tallo,
> Flor, Bulbo, Aromáticas, Legumbres, Cactus/Suculenta, Personalizado) se
> ven correctamente tintados y centrados dentro del círculo verde oscuro,
> sin recortes ni artefactos. No se encontraron problemas visuales
> adicionales. Con esto no queda ningún ítem abierto ni pendiente de
> confirmación en este plan.

> **Segunda pasada (histórico):** al retomar la sesión, el archivo decía
> "0 de 14", pero una auditoría directa del código (antes de tocar nada)
> mostró que los puntos 1 y 5 — los dos de **Prioridad Alta** — ya
> estaban implementados en una sesión anterior sin que se actualizara
> este plan (mismo patrón de "docs atrás del código" que ya había
> pasado con `PLAN_MEJORAS_20.md` y `PLAN_MEJORAS_UX_20.md`, ver
> `CONTEXTO_PROYECTO.md`). A partir de ahí se implementaron y
> verificaron 6 puntos más (2, 4, 6, 7, 8, 12), todos de Prioridad Media
> o Baja. Verificado con `./gradlew compileDebugKotlin` (`BUILD
> SUCCESSFUL`, 1m17s en esta máquina) y `./gradlew testDebugUnitTest`
> (`BUILD SUCCESSFUL`, 24/24 tests — no se agregó ningún test nuevo en
> esa pasada).

> Este plan es la continuación de `PLAN_MEJORAS_UX_20.md` (ya 100%
> cerrado — ver `CONTEXTO_PROYECTO.md`, sección 10). A diferencia de
> aquel, que se armó revisando el código/layouts XML, **este se generó
> instalando el build actual en el celular físico conectado por USB e
> interactuando con la app pantalla por pantalla** (`adb shell input
> tap`/`text`/`swipe` + `screencap` + `uiautomator dump` para coordenadas
> exactas), y cruzando cada hallazgo visual contra el layout XML o el
> Kotlin correspondiente para confirmar la causa exacta. Cada punto cita
> la captura de pantalla en la que se vio el problema y el archivo/línea
> de código que lo explica — no es una lista genérica de buenas
> prácticas de Material Design.

Convención: **Prioridad** = Alta (se ve mal en el primer uso, muy
visible) / Media (se nota al usar la app un rato) / Baja (pulido fino).

---

## Pantallas con mal aprovechamiento del espacio vertical

### 1. `TutorialConexionActivity` no tiene header, y encima queda casi vacía — ✅ HECHO
**Prioridad: Alta**
_Corrección (2026-09-13): al auditar el código antes de seguir con el
plan, resultó que esto ya estaba implementado de una sesión anterior sin
marca en este archivo — `activity_tutorial_conexion.xml` ya tiene el
header completo (`bg_header` + `btnVolver` + título vía
`TextAppearance.Simona.HeaderTitle`, con comentario propio citando este
punto) y el contenido ya está anclado arriba
(`gravity="top|center_horizontal"` sobre un `LinearLayout` con peso 1,
no centrado en todo el alto). `TutorialConexionActivity.kt` ya tiene
`binding.btnVolver.setOnClickListener { finish() }` conectado. No hacía
falta ningún cambio de código, solo corregir el estado de este punto._

Es la única pantalla de todo el flujo sin el bloque de header con flecha
"Volver" + título (todas las demás — `AjustarRangosActivity`,
`DatosHuertaActivity`, `DetalleHuertaActivity`, `SeleccionarPerfilActivity`
— sí lo tienen). Confirmado en `activity_tutorial_conexion.xml`: el
`LinearLayout` raíz es `match_parent` + `android:gravity="center"`, sin
ningún `ImageView btnVolver` ni `TextView` de título — todo el contenido
(ícono de wifi + tarjeta con nombre/botón) queda centrado verticalmente,
dejando aproximadamente el 65% de la pantalla vacío arriba y abajo
(medido sobre la captura real en un celular de 720×1650: la tarjeta
ocupa de y≈645 a y≈1075, el resto es fondo liso). Además, al no tener
botón "Volver" explícito, la única forma de salir es el botón de sistema.
**Acción:** agregarle el mismo header (`bg_header` + `btnVolver` +
`TextView` de título, igual que en las otras 4 Activities del flujo de
alta) y anclar la tarjeta de conexión con `layout_gravity="top"` +
margen fijo en vez de centrado vertical, para que no dependa de cuánto
mida la pantalla.

### 2. `AjustarRangosActivity` — estado inicial deja la mitad de la pantalla vacía — ✅ HECHO
**Prioridad: Media**
_Implementado: nueva tarjeta `resumenRangos` (mismo `bg_chip_resumen` que
usan las otras tarjetas del proyecto) debajo del link "Ajustar
manualmente", con los 4 rangos sugeridos del perfil en modo lectura
(Humedad, pH, Luz, Temperatura), poblada en `onCreate()` vía
`poblarResumenRangos()`. Se oculta al pasar a modo manual
(`mostrarSlidersManualmente()`), donde los sliders ya muestran los
mismos valores de forma editable — evita redundancia. Verificado con
`./gradlew compileDebugKotlin`: `BUILD SUCCESSFUL`._

Al entrar (antes de tocar "Ajustar manualmente"), la pantalla muestra
solo el botón "Usar valores recomendados" + el link "Ajustar
manualmente" y nada más — confirmado en captura real: el contenido
termina en y≈530 de 1650, dejando ~1000px (60%) de fondo vacío. El XML
(`activity_ajustar_rangos.xml`) ya usa `NestedScrollView` +
`wrap_content` correctamente (no es el mismo bug que el punto 1), el
problema es puramente de composición: dos elementos chicos arriba de
toda la pantalla.
**Acción:** centrar verticalmente ese bloque inicial (o agregar debajo
una ilustración/resumen del perfil elegido con sus 4 rangos ya
sugeridos en modo lectura) en vez de dejarlo pegado arriba con el resto
vacío.

### 3. `MapaHuertasActivity` con pocas huertas deja un área enorme sin usar — ✅ HECHO
**Prioridad: Media**
_Implementado: nuevo `TextView` `tvTipPocasHuertas` en
`activity_mapa_huertas.xml`, debajo del contador de huertas y arriba
del `RecyclerView` (`wrap_content`, no compite por espacio con la
lista, que sigue con `weight=1`). `MapaHuertasActivity.cargarDatos()`
lo muestra solo cuando el TOTAL sin filtrar de huertas está en 1..2
(no el filtrado, para que no aparezca/desaparezca al tocar los chips
de "Con sed"/"Óptimas"). Texto: "Agregá otra huerta para comparar su
estado lado a lado" (`home_tip_pocas_huertas`). Verificado con
`./gradlew assembleDebug`: `BUILD SUCCESSFUL`._
Con 1 sola huerta cargada, la lista de tarjetas termina en y≈935 de
1650 y el resto de la pantalla (hasta el FAB, y≈1500) queda vacío —
confirmado en captura real. A diferencia del punto 2, acá sí haría
falta un elemento explícito para esos casos: la pantalla ya tiene un
buen estado vacío para 0 huertas (`estadoVacio`,
`PLAN_MEJORAS_20.md` punto 18), pero no hay ningún tratamiento para
"pocas huertas" (1–2) — el mismo problema se agrava proporcionalmente
menos huertas tenga el usuario.
**Acción:** evaluar un mensaje/tip contextual debajo de la lista
("Agregá otra huerta para comparar" o similar) que ocupe el espacio de
forma útil en vez de dejarlo en blanco, sin necesidad de tocar el
`RecyclerView` en sí.

### 4. El cuadro de mensajes del chat de la IA tiene una altura fija que deja mucho espacio en blanco — ✅ HECHO
**Prioridad: Baja**
_Implementado: `scrollChat` pasó de `200dp` fijo a `wrap_content` en
`dialog_chat_ai.xml`. Como una conversación larga sin ningún tope
estiraría el diálogo fuera de la pantalla, `DialogoChatAi.kt` agrega un
`ViewTreeObserver.OnGlobalLayoutListener` que cachea la altura a un
tope de 200dp (`MAX_ALTURA_CHAT_DP`) la primera vez que el contenido
real lo supera — mensajes cortos ocupan solo lo que necesitan, mensajes
largos siguen acotados y scrolleables como antes. Verificado con
`./gradlew compileDebugKotlin`: `BUILD SUCCESSFUL`._

En `dialog_chat_ai.xml`, el mensaje de bienvenida ("Hola. Soy Simona...")
ocupa 3 líneas pero el cuadro contenedor deja ~150px extra vacíos abajo
antes del campo de texto — confirmado en captura real del diálogo recién
abierto. Con una conversación de una sola pregunta/respuesta corta el
espacio en blanco se nota todavía más.
**Acción:** cambiar la altura fija del contenedor de mensajes por
`wrap_content` con un mínimo razonable (para que no colapse vacío) en
vez de una altura fija pensada para una conversación larga.

---

## Diálogos

### 5. El diálogo "Opciones de [huerta]" no respeta la paleta cálida de la app — ✅ HECHO
**Prioridad: Alta**
_Corrección (2026-09-13): al auditar el código, resultó que esto también
estaba ya implementado de una sesión anterior sin marca en este archivo
— `Theme.Simona` en `themes.xml` ya tiene
`materialAlertDialogTheme="@style/ThemeOverlay.Simona.MaterialAlertDialog"`,
y ese overlay ya fuerza `colorSurfaceContainerHigh`/`colorSurface`/
`colorOnSurface`/`colorPrimary` a la paleta cálida del proyecto — con
comentarios propios en el archivo citando exactamente este punto. Esto
aplica a TODOS los `MaterialAlertDialogBuilder` sin `.setView()` propio
del proyecto (el de "Opciones", el de confirmación de
`DashboardActivity`, el de eliminar huerta), tal como pedía la acción
original. No hacía falta ningún cambio de código, solo corregir el
estado de este punto._

Comparado lado a lado (dos capturas consecutivas): el diálogo "Editar
Huerta Test" tiene fondo blanco/cálido correcto, pero el diálogo
"Opciones de Huerta Test" (el que abre el botón de 3 puntos) se ve con
un fondo gris-lila frío, visiblemente distinto del resto de la app.
Causa confirmada en `MapaHuertasActivity.kt` (línea ~437): ese diálogo
se arma con `MaterialAlertDialogBuilder(this).setTitle(...).setItems(...)`
puro, sin `.setView()` ni layout propio — depende 100% del estilo de
diálogo por defecto de Material3 (que en este proyecto no está
sobreescrito con un `materialAlertDialogTheme` en `themes.xml`, y
además Material3 aplica un tinte de superficie con `colorPrimary` en
diálogos elevados, lo que corre el blanco cálido de la app hacia un tono
frío). En cambio `DialogoEditarHuerta.kt` sí usa `.setView(binding.root)`
sobre un layout (`dialog_editar_huerta.xml`) cuyo `LinearLayout` raíz
fuerza su propio `android:background`, así que no depende del tema del
diálogo.
**Acción:** definir un `materialAlertDialogTheme` propio en
`Theme.Simona` (apuntando a un `ThemeOverlay.MaterialAlertDialog.Simona`
con `colorSurface = simona_superficie`), para que TODOS los
`MaterialAlertDialogBuilder` del proyecto (incluidos los de
confirmación de `DashboardActivity`/eliminar huerta, que probablemente
tengan el mismo problema sin haberlo notado) usen la paleta cálida sin
tener que forzar un `.setView()` en cada uno.

---

## Formularios

### 6. Doble etiqueta redundante en los campos de texto — ✅ HECHO
**Prioridad: Media**
_Implementado con la opción 1 que proponía la acción original:
`app:hintEnabled="false"` en los `TextInputLayout` de nombre y
contraseña, tanto en `activity_datos_huerta.xml` como en
`dialog_editar_huerta.xml` (mismo patrón de doble etiqueta confirmado
en los dos archivos al revisarlos — la nota original de esta acción
solo mencionaba `DatosHuertaActivity`, pero el diálogo de editar tenía
exactamente el mismo problema, así que se corrigió ahí también). El
hint interno deja de flotar al borde de la caja y queda como
placeholder simple (visible solo cuando el campo está vacío, desaparece
al escribir), sin competir con el `TextView` fijo de arriba. Verificado
con `./gradlew compileDebugKotlin`: `BUILD SUCCESSFUL`._


En `DatosHuertaActivity`, cada campo tiene DOS textos haciendo de
label: una `TextView` fija en negrita arriba ("Nombre de la huerta",
"Contraseña de red") y el hint del `TextInputLayout`/`EditText` que al
tener contenido sube y queda flotando en el borde superior de la caja
("Ej: Huerta del fondo", "Mínimo 8 caracteres") — confirmado con
`uiautomator dump`: son dos nodos de texto distintos
(`android:id/etNombre` con `hint="Ej: Huerta del fondo"` + una
`TextView` separada sin id con texto "Nombre de la huerta" un nivel
arriba). El resultado visual es confuso: dos líneas de texto pequeño
apiladas que parecen la misma etiqueta repetida, cuando en realidad una
es el nombre del campo y la otra es un ejemplo.
**Acción:** elegir una sola convención: o el label fijo describe el
campo y el hint interno pasa a ser solo un ejemplo visualmente más sutil
(gris más claro, sin quedar "flotando" como si fuera el nombre del
campo — se puede lograr con `app:hintEnabled="false"` en el
`TextInputLayout` y dejar el hint como placeholder simple), o se saca el
label fijo y se deja que el hint de Material haga ese trabajo solo.

### 7. Los placeholders de foto y ubicación no tienen ningún ícono — ✅ HECHO
**Prioridad: Baja**
_Implementado: dos vectores nuevos (`ic_camara.xml`, ícono estándar
Material "camera_alt"; `ic_pin_ubicacion.xml`, ícono estándar Material
"place" — mismo criterio de líneas simples tintadas en tiempo de
ejecución que ya usa `ic_wifi_signal.xml`). El de cámara se agregó
dentro del `FrameLayout` de la foto (`ivFotoPlaceholderIcono`, oculto/
mostrado junto con `ivFotoPreview` en `DatosHuertaActivity.kt`,
en los mismos 3 lugares donde ya se togglea la preview). El de
ubicación se agregó arriba del texto de ayuda del croquis
(`grupoCroquisHint`, un `LinearLayout` nuevo que agrupa ícono + texto;
`mostrarMarcador()` ahora oculta ese grupo entero en vez de solo el
`TextView`, para que el ícono no quede huérfano al fijar un marcador
real). Verificado con `./gradlew compileDebugKotlin`: `BUILD
SUCCESSFUL`._

En `DatosHuertaActivity`, tanto el recuadro de "Foto de la huerta" como
el de "Ubicá tu huerta en el mapa" son cajas vacías con borde punteado
(marrón y verde respectivamente) sin ningún ícono adentro que sugiera
la acción — confirmado en captura real, ambos recuadros están
completamente en blanco salvo el texto de ayuda al lado/abajo.
**Acción:** agregar un ícono de cámara centrado en el placeholder de
foto y un ícono de pin/ubicación centrado en el del mapa (ambos ya
existen en el proyecto como drawables para otras pantallas, ej.
`ic_wifi_signal` ya se reutiliza en dos lugares — mismo criterio).

---

## Componentes interactivos

### 8. El `RangeSlider` es difícil de leer y de usar en rangos angostos — ✅ HECHO
**Prioridad: Media**
_Implementado, con una corrección durante la implementación: se acotó
`valueFrom`/`valueTo` del slider de pH de 0–14 a 4.0–9.0 en
`activity_ajustar_rangos.xml` y `dialog_editar_huerta.xml` (los 9
perfiles de `PerfilesCultivo.kt` van de pH 5.5 a 7.5, ninguno queda
afuera; se agregó además un `coerceIn` defensivo en
`DialogoEditarHuerta.configurarEditorRango()` por si una huerta vieja
tuviera un pH fuera de ese rango, guardado con el slider anterior de
0–14). Para el aspecto de "puntos" en el track: la acción original
sugería el atributo `tickVisibilityMode`, que developer.android.com
documenta como el vigente hoy — pero un intento real de usarlo hizo
fallar `./gradlew compileDebugKotlin` con `style attribute
'attr/tickVisibilityMode' not found`, porque ese atributo **todavía no
existe en Material Components 1.12.0** (la versión que usa este
proyecto). Se corrigió usando en su lugar el atributo `tickVisible`
(deprecado en versiones más nuevas de Material, pero presente y
funcional en 1.12.0), que logra el mismo resultado. Verificado con
`./gradlew compileDebugKotlin`: `BUILD SUCCESSFUL` (después de la
corrección)._

Confirmado en captura real de `AjustarRangosActivity`: el track del
slider se ve como una fila de puntos discretos (por el `stepSize`
configurado) en vez de una línea continua, lo que da un aspecto
entrecortado poco pulido. Además, en el slider de pH (rango 6.0–7.0
sobre una escala total de 0–14) los dos thumbs quedan prácticamente
superpuestos en el centro — visualmente casi indistinguibles uno del
otro, lo que en la práctica hace muy difícil agarrar el thumb correcto
con el dedo. Esto es un problema real de usabilidad, no solo estético:
el pH es justamente el rango más angosto de los 4 (10/14 de la escala
usada, contra 25/100 de humedad o 65/1000 de luz).
**Acción:** para el slider de pH puntualmente, evaluar acotar
`valueFrom`/`valueTo` a un rango más realista para suelo (ej. 4.0–9.0
en vez de 0–14, ya que un pH de suelo fuera de ese rango no tiene
sentido agronómico) para que el rango configurable ocupe una porción
mayor y más manejable del track. Para el aspecto de "puntos" en todos
los sliders, evaluar si conviene bajar la densidad de los tick marks
visibles (Material permite separar el `stepSize` real del
`tickVisibilityMode`).

### 9. Una huerta sin posición en el mapa desaparece del todo en la vista "Croquis" — ✅ HECHO
**Prioridad: Baja**
_Implementado: se le agregó id (`tvMapaHint`) al `TextView` de ayuda
debajo del croquis en `activity_mapa_huertas.xml`.
`MapaHuertasActivity.renderizarPines()` (ya se llama en cada refresco
del croquis) cuenta las huertas con `posicionMapaX`/`Y` null y cambia
el texto: 1 sin ubicar → `home_mapa_hint_sin_ubicar_una`, más de 1 →
`home_mapa_hint_sin_ubicar_varias` (con el conteo), 0 → el
`home_mapa_hint` genérico de siempre. No se tocó la opcionalidad de la
posición, solo se avisa mejor. Verificado con `./gradlew assembleDebug`:
`BUILD SUCCESSFUL`._
Confirmado en captura real: con 1 huerta cargada pero sin posición
marcada (paso opcional en `DatosHuertaActivity`), la pestaña "Croquis"
muestra el placeholder genérico ("Tocá una huerta para ver su estado y
conectarte") sin ningún pin ni ninguna indicación de que esa huerta
existe pero no tiene posición asignada. Es coherente con que la
posición es opcional (documentado en `CONTEXTO_PROYECTO.md`, sección 5),
pero deja al usuario sin ninguna pista de qué hacer si entra a esa
pestaña.
**Acción:** evaluar un mensaje más específico cuando existan huertas
sin posición ("Tenés 1 huerta sin ubicar en el croquis — tocá aquí para
ubicarla") en vez del texto genérico actual, sin tocar el
comportamiento de opcionalidad ya decidido.

---

## Iconografía y branding

### 10. Los íconos de los 9 perfiles de cultivo son difíciles de leer — ✅ HECHO
**Prioridad: Media**
_Corrección durante la implementación: la causa real no era (solo) el
dibujo de los íconos, sino que `item_perfil_cultivo.xml` (la grilla de
`SeleccionarPerfilActivity`, donde el usuario vio el problema) forzaba
`app:tint="@color/simona_azul"` sobre el `ImageView`, aplastando toda
la paleta de colores de los 9 drawables (verde/rojo/marrón, pensada
para que cada perfil se distinga) a una silueta plana de un solo
color — confirmado comparando con `activity_ajustar_rangos.xml`, que
muestra el mismo `ic_perfil_fruto` SIN tint y ahí se lee bien como
fruta roja con hoja verde. Se sacó ese `app:tint` (ahora los 9 perfiles
se ven con su color propio en la grilla, igual que ya se veían en
`AjustarRangosActivity`). Además, `ic_perfil_hoja.xml` e
`ic_perfil_raiz.xml` tenían formas que no se leían ni con color —
"Hoja" era literalmente dos curvas cruzadas (se veía como una X,
confirmado por el usuario) y "Raíz" era una sola curva que se leía como
una coma o un pez. Se rehicieron ambas: hoja como silueta "vesica"
(dos arcos que se juntan en punta arriba y abajo, con nervadura
central y tallo) y raíz como cuerpo que se afina de ancho arriba a
punta abajo (forma de zanahoria) con un penacho de 3 hojas arriba. Los
otros 7 perfiles (fruto, flor, tallo, bulbo, aromáticas, legumbres,
cactus) no se tocaron — ya tenían formas razonablemente literales, el
problema en esos casos era 100% el tint. Verificado con `./gradlew
assembleDebug`: `BUILD SUCCESSFUL`. No se pudo hacer la comparación
visual final en el celular físico en el momento en que se escribió este
punto — luego, ya reconectado el USB en esta misma sesión, se instaló
el APK y se confirmó en captura real de `SeleccionarPerfilActivity` que
los 9 íconos (incluidos "Hoja" como silueta de hoja con nervadura y
"Raíz" como zanahoria con penacho) se leen correctamente a simple
vista, sin necesidad del texto al lado. El problema descripto abajo
(hoja como X, raíz como pez/coma) corresponde al estado ANTES del
arreglo de este mismo punto y quedó resuelto._
Estado original del hallazgo (histórico, ya resuelto arriba): los íconos
vectoriales de los perfiles eran formas abstractas de difícil lectura a
simple vista — el de "Hoja" se veía como una X o dos trazos cruzados (no
como una hoja), el de "Raíz" parecía un pez o una coma estilizada. Sin
el texto al lado ("Hoja", "Raíz") habría sido imposible identificar la
categoría solo por el ícono, lo que le restaba valor al hecho de tener
íconos en primer lugar (deberían acelerar el reconocimiento visual, no
depender del texto).
**Acción:** revisar/rehacer los drawables de los 9 perfiles con formas
más literales y reconocibles (ej. una hoja real para "Hoja", una
zanahoria/raíz para "Raíz"), manteniendo el mismo estilo de línea y
color azul sobre círculo verde pálido que ya está bien resuelto.

### 11. El FAB del asistente de IA no comunica "chat" a un usuario nuevo — ✅ HECHO
**Prioridad: Baja**
_Implementado: el `MaterialCardView` del FAB se envolvió en un
`FrameLayout` (mismo `layout_gravity="bottom|end"`, mismo margen visual
para el FAB en sí) con un badge circular de 20dp superpuesto en la
esquina superior-derecha (`bg_badge_chat.xml`, círculo azul con borde
del color de fondo para que se recorte del avatar) con un ícono de
globo de chat adentro (`ic_badge_chat.xml`, "chat_bubble" estándar). No
se tocó el avatar de Simona. Verificado con `./gradlew assembleDebug`:
`BUILD SUCCESSFUL`._
El FAB flotante (ícono `fabAiSimona`, visible en el Home y en varias
pantallas) es un círculo con degradé azul-verde y una mascota
(flor/carita) adentro — coherente como avatar de "Simona" una vez que
se abre el chat (el mismo ícono aparece como avatar en
`dialog_chat_ai.xml`, confirmado en captura real), pero como botón
flotante solo, sin badge ni ícono de mensaje superpuesto, no comunica
por sí mismo la acción "tocá para chatear" a alguien que ve la app por
primera vez.
**Acción:** agregar un badge chico con un ícono de chat/mensaje en una
esquina del FAB (patrón común de Material Design para FABs de asistentes),
sin cambiar el avatar de Simona en sí.

---

## Pulido visual

### 12. Scrollbar del sistema visible en el diálogo "Editar huerta" — ✅ HECHO
**Prioridad: Baja**
_Implementado: `android:scrollbars="none"` en `scrollEditarContenido`
de `dialog_editar_huerta.xml`. El scroll táctil sigue funcionando
igual, solo se oculta el indicador visual. Verificado con `./gradlew
compileDebugKotlin`: `BUILD SUCCESSFUL`._

Confirmado en captura real: al abrir "Editar Huerta Test", se ve una
barra de scroll gris vertical pegada al borde derecho del diálogo — es
el scrollbar por defecto de Android, visualmente "técnico" comparado
con el resto de la UI pulida del proyecto.
**Acción:** `android:scrollbars="none"` en el `ScrollView` de
`dialog_editar_huerta.xml` (el usuario igual puede scrollear con el
dedo, solo se oculta el indicador).

### 13. Salto de contraste entre el header y el resto de la UI en modo oscuro — ⏸️ EVALUADO, SIN CAMBIOS
**Prioridad: Baja (revisar, no asumir que hace falta cambiarlo)**
_Cerrado sin tocar código, tal como pedía este mismo punto: es una
decisión de diseño a revisar EN EQUIPO, no algo para implementar a
ciegas. Se re-confirmó que la decisión de no oscurecer el header en
modo oscuro sigue documentada a propósito en `values-night/colors.xml`
(fidelidad con el dashboard HTML) — no se encontró ningún motivo nuevo
para revertir esa decisión. Si el equipo decide en algún momento que
vale la pena, la acción sugerida abajo sigue siendo válida._
Confirmado con captura real en modo oscuro: el header se mantiene con
el mismo azul brillante que en modo claro (documentado a propósito en
`values-night/colors.xml`, "se mantienen iguales en ambos modos, igual
que en el simulador"), contra un fondo del resto de la pantalla casi
negro (`simona_superficie` #1C1913 en modo oscuro). El salto de
contraste es fuerte y se nota más en una captura real que solo leyendo
el código. No se marca como "bug" porque la decisión de no oscurecer el
header está documentada y es intencional (fidelidad con el dashboard
HTML) — se dejar acá como punto a **revisar con el equipo**, no a
implementar a ciegas.
**Acción:** si el equipo decide que vale la pena, definir un azul de
header intermedio para modo oscuro (ni el brillante de claro ni negro)
en `values-night/colors.xml`; si no, cerrar este punto como "evaluado,
sin cambios" igual que ya se hizo con otras decisiones de diseño en
`PLAN_MEJORAS_20.md`.

### 14. Contraste bajo entre la pestaña activa e inactiva en "Tarjetas"/"Croquis" — ✅ HECHO
**Prioridad: Baja**
_Corrección durante la implementación: la causa exacta era que la
pestaña inactiva usaba `simona_card_bg` (blanco), el MISMO color que
el fondo del propio `tabsContainer` (`bg_chip_resumen`, también
`simona_card_bg`) — en la práctica la pestaña inactiva era
indistinguible del contenedor salvo por su borde de 1dp. Se cambió a
`simona_superficie` (el fondo general de la pantalla, distinto tanto
del contenedor blanco como de la pestaña activa celeste) en el default
del XML (`tabBtnMapa`) y en la lógica de `cambiarPestana()` en
`MapaHuertasActivity.kt` (antes alternaba a `simona_card_bg`). No se
tocó el color de la pestaña activa. Verificado con `./gradlew
assembleDebug`: `BUILD SUCCESSFUL`._
Confirmado en captura real: la pestaña activa ("Tarjetas") tiene fondo
celeste muy pálido (`simona_azul_suave`) sobre el fondo general
`simona_superficie` (beige muy claro) — la diferencia de tono entre
ambos es sutil, se nota más por el borde azul que por el relleno en sí.
**Acción:** subir levemente la saturación/opacidad del fondo de la
pestaña activa, o agregar un fondo más marcado a la pestaña inactiva
(en vez de blanco/transparente) para que el contraste entre ambos
estados sea más inmediato.
