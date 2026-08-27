/*
  dashboard_html.h — Dashboard HTML de análisis de UNA huerta.

  Mismo archivo (byte a byte) que sirve simulador/servidor_simulado.py en
  GET / — así el WebView de DashboardActivity.kt ve exactamente la misma
  interfaz sin importar si está hablando con el simulador o con el ESP32
  real. Se guarda en PROGMEM (flash) para no consumir RAM del ESP32.

  IMPORTANTE: si se actualiza el dashboard en servidor_simulado.py, hay que
  volver a copiar el HTML acá para no romper la paridad simulador/hardware.
*/
#pragma once

const char DASHBOARD_HTML[] PROGMEM = R"rawliteral(
<!DOCTYPE html>
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
  .badge-demo .ic svg { width: 13px; height: 13px; color: var(--marron); }
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
  .badge-demo {
    margin: 12px 0 0; padding: 5px 12px 5px 10px; background: var(--marron-suave); border: 1px solid var(--marron);
    color: var(--marron-oscuro); border-radius: 999px; font-size: 11px; font-weight: 700;
    display: inline-flex; align-items: center; gap: 5px;
  }
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
  <span class="badge-demo"><span class="ic" data-icon="alerta"></span> MODO DEMO — datos simulados</span>

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

function toggleTheme() {
  const cur = document.documentElement.getAttribute('data-theme');
  document.documentElement.setAttribute('data-theme', cur === 'dark' ? 'light' : 'dark');
  actualizarIconoTema();
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

)rawliteral";
