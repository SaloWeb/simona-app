/**
 * Smoke test end-to-end del dashboard HTML del simulador (PLAN_MEJORAS_20.md,
 * punto 17). Levanta servidor_simulado.py en un puerto de prueba, abre el
 * dashboard con Playwright, y verifica los flujos principales sin
 * necesitar un celular ni un ESP32 real:
 *   1. El gauge de humedad se actualiza solo (polling de /estado).
 *   2. "Forzar Riego" prende y apaga el relé simulado.
 *   3. "Rellenar" el tanque lo deja en 100%.
 *   4. POST /config valida correctamente (ancho mínimo de humedad).
 *
 * Uso:
 *   NODE_PATH=/home/benja/.pw-tools/node_modules node simulador/tests/smoke_test.js
 *
 * (NODE_PATH apunta a la instalación de Playwright que ya tiene los
 * navegadores descargados en este equipo — ver ~/.pw-tools y
 * ~/.cache/ms-playwright. Si se corre en otra máquina, alcanza con un
 * `npm install playwright` normal en cualquier carpeta y ajustar esa ruta.)
 */

const { chromium } = require('playwright');
const { spawn } = require('child_process');
const path = require('path');
const http = require('http');

const PUERTO = 8123;
const BASE_URL = `http://127.0.0.1:${PUERTO}`;
const SCRIPT_SIMULADOR = path.join(__dirname, '..', 'servidor_simulado.py');

function httpJson(method, urlPath, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const req = http.request(
      `${BASE_URL}${urlPath}`,
      {
        method,
        headers: data
          ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) }
          : {},
      },
      (res) => {
        let raw = '';
        res.on('data', (chunk) => (raw += chunk));
        res.on('end', () => {
          let parsed = null;
          try { parsed = JSON.parse(raw); } catch (_) { /* ignorar */ }
          resolve({ status: res.statusCode, body: parsed, raw });
        });
      }
    );
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

async function esperarServidorListo(intentos = 30) {
  for (let i = 0; i < intentos; i++) {
    try {
      const r = await httpJson('GET', '/data');
      if (r.status === 200) return;
    } catch (_) { /* todavía no levantó */ }
    await new Promise((r) => setTimeout(r, 300));
  }
  throw new Error('El simulador no respondió a tiempo en ' + BASE_URL);
}

function assert(cond, mensaje) {
  if (!cond) throw new Error('FALLÓ: ' + mensaje);
  console.log('  OK - ' + mensaje);
}

async function main() {
  console.log(`Levantando ${SCRIPT_SIMULADOR} en el puerto ${PUERTO}...`);
  const proc = spawn('python3', [SCRIPT_SIMULADOR, String(PUERTO)], { stdio: 'ignore' });

  let browser;
  try {
    await esperarServidorListo();
    console.log('Simulador arriba.\n');

    // ---- Config determinística para el resto del test ----
    console.log('Test 1: POST /config con datos válidos');
    const configOk = await httpJson('POST', '/config', {
      nombre: 'Huerta de prueba (Playwright)',
      categoria: 'Fruto',
      humedadMin: 30, humedadMax: 60,
      phMin: 5.5, phMax: 7.0,
      luzMin: 200, luzMax: 800,
      tempMin: 15, tempMax: 28,
    });
    assert(configOk.status === 200, 'POST /config válido devuelve 200');
    assert(configOk.body.nombre === 'Huerta de prueba (Playwright)', 'el nombre configurado se refleja en /config');

    console.log('\nTest 2: POST /config rechaza rango de humedad angosto');
    const configMal = await httpJson('POST', '/config', {
      nombre: 'Huerta angosta',
      categoria: 'Fruto',
      humedadMin: 40, humedadMax: 45, // ancho 5 < ANCHO_MINIMO_HUMEDAD (10)
      phMin: 5.5, phMax: 7.0,
      luzMin: 200, luzMax: 800,
      tempMin: 15, tempMax: 28,
    });
    assert(configMal.status === 400, 'rango de humedad angosto devuelve 400');
    assert(/al menos/.test(configMal.body.error || ''), 'el mensaje de error explica el motivo');

    // Volver a dejar la config válida para el resto del test (la anterior
    // llamada, aunque rechazada, no debería haber tocado el estado — lo
    // re-confirmamos reconfigurando).
    await httpJson('POST', '/config', {
      nombre: 'Huerta de prueba (Playwright)', categoria: 'Fruto',
      humedadMin: 30, humedadMax: 60, phMin: 5.5, phMax: 7.0,
      luzMin: 200, luzMax: 800, tempMin: 15, tempMax: 28,
    });

    // ---- Dashboard HTML con el navegador real ----
    browser = await chromium.launch();
    const page = await browser.newPage();
    await page.goto(BASE_URL + '/');

    console.log('\nTest 3: el gauge de humedad se actualiza solo');
    await page.waitForFunction(
      () => document.getElementById('gaugeValue').textContent !== '--',
      null,
      { timeout: 8000 }
    );
    const valorInicial = await page.textContent('#gaugeValue');
    assert(valorInicial !== '--' && !Number.isNaN(parseFloat(valorInicial)), `#gaugeValue muestra un número (${valorInicial}%)`);

    console.log('\nTest 4: "Forzar Riego" prende y apaga el riego simulado');
    await page.click('#btnToggleRiego');
    await page.waitForFunction(
      () => document.getElementById('riegoTitulo').textContent.includes('forzado'),
      null,
      { timeout: 5000 }
    );
    assert(true, 'el riego pasa a estado "forzado" tras tocar el botón');

    await page.click('#btnToggleRiego');
    await page.waitForFunction(
      () => document.getElementById('riegoTitulo').textContent.includes('apagado'),
      null,
      { timeout: 5000 }
    );
    assert(true, 'el riego vuelve a "apagado" al tocar el botón de nuevo');

    console.log('\nTest 5: "Rellenar" deja el tanque en 100%');
    await page.click('button.btn-refill');
    await page.waitForFunction(
      () => document.getElementById('tanque').textContent === '100',
      null,
      { timeout: 5000 }
    );
    assert(true, 'el tanque queda en 100% tras rellenar');

    console.log('\n✅ Todos los tests pasaron.');
  } finally {
    if (browser) await browser.close();
    proc.kill();
  }
}

main().catch((err) => {
  console.error('\n❌ ' + err.message);
  process.exitCode = 1;
});
