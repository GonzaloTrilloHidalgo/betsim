/* BetSim PWA — lógica de cliente (vanilla JS). */

// Base de la API. Cambia esto si el backend corre en otra URL/host.
const API = (location.hostname === 'localhost' || location.hostname === '127.0.0.1')
  ? 'http://localhost:8080/api/v1'
  : `${location.origin}/api/v1`;

const store = {
  get access() { return localStorage.getItem('bs_access'); },
  get refresh() { return localStorage.getItem('bs_refresh'); },
  setTokens(a, r) { localStorage.setItem('bs_access', a); if (r) localStorage.setItem('bs_refresh', r); },
  clear() { localStorage.removeItem('bs_access'); localStorage.removeItem('bs_refresh'); },
};

// Estado en memoria
let slip = [];          // [{opcionCuotaId, label, cuota}]
let currentTab = 'cartelera';

/* ---------------- HTTP ---------------- */
async function api(path, { method = 'GET', body, auth = true, retry = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth && store.access) headers['Authorization'] = `Bearer ${store.access}`;
  const res = await fetch(API + path, { method, headers, body: body ? JSON.stringify(body) : undefined });

  // Sesión inválida/caducada (401, o 403 de configuraciones antiguas): renovar o volver al login.
  if ((res.status === 401 || res.status === 403) && auth && retry) {
    const ok = store.refresh ? await tryRefresh() : false;
    if (ok) return api(path, { method, body, auth, retry: false });
    forceLogin();
    throw new Error('Tu sesión ha caducado. Vuelve a entrar.');
  }
  const data = res.status === 204 ? null : await res.json().catch(() => null);
  if (!res.ok) throw new Error((data && data.message) || `Error ${res.status}`);
  return data;
}

/** Limpia la sesión y muestra la pantalla de login (cuando el token ya no sirve). */
function forceLogin() {
  store.clear();
  slip = [];
  const app = $('#app'); const auth = $('#auth-screen');
  if (app) app.classList.add('hidden');
  if (auth) { auth.classList.remove('hidden'); switchAuth('login'); }
}

async function tryRefresh() {
  try {
    const data = await api('/auth/refresh', { method: 'POST', body: { refreshToken: store.refresh }, auth: false, retry: false });
    store.setTokens(data.accessToken, data.refreshToken);
    return true;
  } catch { store.clear(); return false; }
}

/* ---------------- Utilidades ---------------- */
const $ = (sel) => document.querySelector(sel);
const fmt = (n) => Number(n).toFixed(2);
function fmtDate(iso) {
  const d = new Date(iso);
  return d.toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }) + ' ' +
         d.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
}
const ESTADO_COLOR = { PENDIENTE: 'text-amber-400', GANADA: 'text-green-400', PERDIDA: 'text-red-400', ANULADA: 'text-slate-400' };

/* ---------------- Banderas ---------------- */
// Mapa selección -> código de país (ISO alpha-2; sub-regiones de UK con gb-xxx).
// Incluye nombres en inglés (datos reales de The Odds API) y en español (mock).
const FLAGS = {
  // English (The Odds API)
  'spain': 'es', 'germany': 'de', 'france': 'fr', 'england': 'gb-eng', 'scotland': 'gb-sct',
  'wales': 'gb-wls', 'northern ireland': 'gb-nir', 'argentina': 'ar', 'brazil': 'br', 'portugal': 'pt',
  'netherlands': 'nl', 'italy': 'it', 'croatia': 'hr', 'mexico': 'mx', 'united states': 'us', 'usa': 'us',
  'belgium': 'be', 'uruguay': 'uy', 'morocco': 'ma', 'senegal': 'sn', 'japan': 'jp', 'south korea': 'kr',
  'korea republic': 'kr', 'colombia': 'co', 'ecuador': 'ec', 'canada': 'ca', 'qatar': 'qa',
  'switzerland': 'ch', 'denmark': 'dk', 'poland': 'pl', 'serbia': 'rs', 'ghana': 'gh', 'cameroon': 'cm',
  'nigeria': 'ng', 'australia': 'au', 'saudi arabia': 'sa', 'iran': 'ir', 'tunisia': 'tn',
  'costa rica': 'cr', 'peru': 'pe', 'chile': 'cl', 'norway': 'no', 'sweden': 'se', 'austria': 'at',
  'hungary': 'hu', 'turkey': 'tr', 'turkiye': 'tr', 'greece': 'gr', 'egypt': 'eg', 'algeria': 'dz',
  'ivory coast': 'ci', "cote d'ivoire": 'ci', 'ukraine': 'ua', 'czechia': 'cz', 'czech republic': 'cz',
  'paraguay': 'py', 'venezuela': 've', 'bolivia': 'bo', 'panama': 'pa', 'jamaica': 'jm',
  'new zealand': 'nz', 'south africa': 'za', 'mali': 'ml', 'cape verde': 'cv', "cabo verde": 'cv',
  // Español (mock y por si acaso)
  'españa': 'es', 'alemania': 'de', 'francia': 'fr', 'inglaterra': 'gb-eng', 'escocia': 'gb-sct',
  'gales': 'gb-wls', 'brasil': 'br', 'países bajos': 'nl', 'paises bajos': 'nl', 'holanda': 'nl',
  'italia': 'it', 'croacia': 'hr', 'méxico': 'mx', 'estados unidos': 'us', 'bélgica': 'be',
  'marruecos': 'ma', 'japón': 'jp', 'corea del sur': 'kr', 'suiza': 'ch', 'dinamarca': 'dk',
  'polonia': 'pl', 'camerún': 'cm', 'arabia saudí': 'sa', 'irán': 'ir', 'túnez': 'tn',
  'costa de marfil': 'ci', 'noruega': 'no', 'suecia': 'se', 'turquía': 'tr', 'grecia': 'gr',
  'egipto': 'eg', 'argelia': 'dz', 'ucrania': 'ua', 'chequia': 'cz', 'sudáfrica': 'za',
};

function flag(name) {
  const code = FLAGS[(name || '').trim().toLowerCase()];
  if (!code) return '';
  return `<img src="https://flagcdn.com/24x18/${code}.png" alt="" loading="lazy"
            class="inline-block w-5 h-auto rounded-sm shrink-0 align-middle" />`;
}

/* ---------------- Autenticación ---------------- */
let authMode = 'login';
function setupAuth() {
  $('#tab-login').onclick = () => switchAuth('login');
  $('#tab-register').onclick = () => switchAuth('register');
  $('#auth-submit').onclick = submitAuth;
  $('#auth-password').addEventListener('keydown', (e) => { if (e.key === 'Enter') submitAuth(); });
}
function switchAuth(mode) {
  authMode = mode;
  const login = mode === 'login';
  $('#register-only').classList.toggle('hidden', login);
  $('#auth-submit').textContent = login ? 'Entrar' : 'Crear cuenta';
  $('#tab-login').className = `flex-1 py-2 rounded-lg text-sm font-semibold ${login ? 'bg-green-600' : 'text-slate-300'}`;
  $('#tab-register').className = `flex-1 py-2 rounded-lg text-sm font-semibold ${!login ? 'bg-green-600' : 'text-slate-300'}`;
  $('#auth-error').textContent = '';
}
async function submitAuth() {
  const username = $('#auth-username').value.trim();
  const password = $('#auth-password').value;
  $('#auth-error').textContent = '';
  try {
    let data;
    if (authMode === 'register') {
      const email = $('#reg-email').value.trim();
      data = await api('/auth/register', { method: 'POST', body: { username, email, password }, auth: false });
    } else {
      data = await api('/auth/login', { method: 'POST', body: { usernameOrEmail: username, password }, auth: false });
    }
    store.setTokens(data.accessToken, data.refreshToken);
    enterApp();
  } catch (e) {
    $('#auth-error').textContent = e.message;
  }
}

/* ---------------- Navegación ---------------- */
function setupNav() {
  document.querySelectorAll('.nav-btn').forEach((btn) => {
    btn.onclick = () => selectTab(btn.dataset.tab);
  });
}
function selectTab(tab) {
  currentTab = tab;
  document.querySelectorAll('.nav-btn').forEach((b) => {
    b.classList.toggle('text-green-500', b.dataset.tab === tab);
    b.classList.toggle('text-slate-400', b.dataset.tab !== tab);
  });
  render();
}

async function render() {
  clearLiveTimer();
  if (currentTab === 'cartelera') return renderCartelera();
  if (currentTab === 'apuestas') return renderApuestas();
  if (currentTab === 'cuadro') return renderCuadro();
  if (currentTab === 'billetera') return renderBilletera();
}

/* ---------------- Etiquetas de fase / escudos ---------------- */
const FASE_LABEL = {
  GROUP_STAGE: 'Fase de grupos', LAST_32: 'Dieciseisavos', LAST_16: 'Octavos',
  QUARTER_FINALS: 'Cuartos', SEMI_FINALS: 'Semifinal', THIRD_PLACE: '3.er puesto', FINAL: 'Final',
};
function faseLabel(stage, group) {
  const s = FASE_LABEL[stage] || stage || '';
  if (group) return (s ? s + ' · ' : '') + group.replace('GROUP_', 'Grupo ');
  return s;
}
// Escudo de football-data o, si no hay, nuestra bandera por nombre.
function escudo(url, nombre) {
  if (url) return `<img src="${url}" alt="" loading="lazy" class="inline-block w-5 h-5 object-contain shrink-0 align-middle" />`;
  return flag(nombre);
}

/* ---------------- Cartelera ---------------- */
let carteleraView = 'proximos';
async function renderCartelera() {
  clearLiveTimer();
  const c = $('#content');
  const seg = (id, label, view) =>
    `<button id="${id}" class="flex-1 py-2 rounded-lg text-sm font-semibold ${carteleraView === view ? 'bg-green-600' : 'text-slate-300'}">${label}</button>`;
  c.innerHTML = `
    <div class="mx-auto w-full max-w-5xl">
      <div class="flex bg-slate-800 rounded-xl p-1 mb-4 max-w-lg">
        ${seg('cv-prox', 'Próximos', 'proximos')}
        ${seg('cv-live', '🔴 En vivo', 'envivo')}
        ${seg('cv-res', 'Resultados', 'resultados')}
      </div>
      <div id="cv-body"><div class="text-center text-slate-500 py-10">Cargando…</div></div>
    </div>`;
  $('#cv-prox').onclick = () => { carteleraView = 'proximos'; renderCartelera(); };
  $('#cv-live').onclick = () => { carteleraView = 'envivo'; renderCartelera(); };
  $('#cv-res').onclick = () => { carteleraView = 'resultados'; renderCartelera(); };

  const body = $('#cv-body');
  if (carteleraView === 'proximos') await renderProximos(body);
  else if (carteleraView === 'envivo') { await renderEnVivo(body); liveTimer = setInterval(() => renderEnVivo(body), 45000); }
  else await renderResultados(body);
}

/* ---------------- En vivo ---------------- */
let liveTimer = null;
function clearLiveTimer() { if (liveTimer) { clearInterval(liveTimer); liveTimer = null; } }

async function renderEnVivo(body) {
  if (!document.body.contains(body)) { clearLiveTimer(); return; }
  try {
    const live = await api('/live');
    if (!live.length) {
      body.innerHTML = `<div class="text-center text-slate-500 py-10">No hay partidos en vivo ahora mismo.</div>`;
      return;
    }
    body.innerHTML = `<div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">${live.map(liveCard).join('')}</div>`;
  } catch (e) {
    body.innerHTML = `<div class="text-center text-red-400 py-10">${e.message}</div>`;
  }
}

function liveCard(m) {
  const badge = m.estado === 'PAUSED'
    ? `<span class="text-[10px] font-bold text-amber-400">⏸ DESCANSO</span>`
    : `<span class="text-[10px] font-bold text-red-500 animate-pulse">🔴 EN VIVO</span>`;
  return `
    <div class="bg-slate-800 rounded-xl p-3 ring-1 ring-red-500/30">
      <div class="flex justify-between items-center mb-2">
        <div class="text-[10px] text-slate-400 truncate">${faseLabel(m.fase, m.grupo)}</div>
        ${badge}
      </div>
      <div class="flex items-center justify-between text-sm font-semibold gap-2">
        <span class="flex items-center gap-1 flex-1 min-w-0">${escudo(m.escudoLocal, m.local)}<span class="truncate">${m.local}</span></span>
        <span class="px-2 py-1 rounded-lg bg-slate-700 font-bold tabular-nums">${m.golesLocal ?? 0} : ${m.golesVisitante ?? 0}</span>
        <span class="flex items-center gap-1 flex-1 min-w-0 justify-end"><span class="truncate">${m.visitante}</span>${escudo(m.escudoVisitante, m.visitante)}</span>
      </div>
    </div>`;
}

async function renderProximos(body) {
  try {
    const matches = await api('/matches');
    if (!matches.length) { body.innerHTML = `<div class="text-center text-slate-500 py-10">No hay partidos próximos.</div>`; return; }
    body.innerHTML = `<div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">${matches.map(matchCard).join('')}</div>`;
    body.querySelectorAll('[data-opt]').forEach((btn) => { btn.onclick = () => toggleSelection(btn); });
    body.querySelectorAll('.more-btn').forEach((btn) => {
      btn.onclick = () => {
        const panel = document.getElementById(btn.dataset.target);
        const oculto = panel.classList.toggle('hidden');
        btn.textContent = oculto ? '+ Más apuestas' : '− Menos apuestas';
      };
    });
    refreshOddButtons();
  } catch (e) {
    body.innerHTML = `<div class="text-center text-red-400 py-10">${e.message}</div>`;
  }
}

async function renderResultados(body) {
  try {
    const results = await api('/results');
    if (!results.length) { body.innerHTML = `<div class="text-center text-slate-500 py-10">Aún no hay partidos jugados.</div>`; return; }
    body.innerHTML = `<div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">${results.map(resultCard).join('')}</div>`;
  } catch (e) {
    body.innerHTML = `<div class="text-center text-red-400 py-10">${e.message}</div>`;
  }
}

function resultCard(m) {
  const gl = m.golesLocal, gv = m.golesVisitante;
  const win = (a, b) => a > b ? 'text-green-400' : a < b ? 'text-slate-400' : 'text-slate-200';
  return `
    <div class="bg-slate-800 rounded-xl p-3">
      <div class="flex justify-between items-center mb-2">
        <div class="text-[10px] text-slate-400 truncate">${faseLabel(m.fase, m.grupo)}</div>
        <div class="text-[10px] text-slate-400">${m.fecha ? fmtDate(m.fecha) : ''}</div>
      </div>
      <div class="flex items-center justify-between text-sm font-semibold gap-2">
        <span class="flex items-center gap-1 flex-1 min-w-0 ${win(gl, gv)}">${escudo(m.escudoLocal, m.local)}<span class="truncate">${m.local}</span></span>
        <span class="px-2 py-1 rounded-lg bg-slate-700 font-bold tabular-nums">${gl ?? '-'} : ${gv ?? '-'}</span>
        <span class="flex items-center gap-1 flex-1 min-w-0 justify-end ${win(gv, gl)}"><span class="truncate">${m.visitante}</span>${escudo(m.escudoVisitante, m.visitante)}</span>
      </div>
    </div>`;
}

/* ---------------- Cuadro eliminatorio ---------------- */
// Rondas del cuadro de izquierda a derecha (slots = 16,8,4,2,1).
const BRACKET_ROUNDS = ['LAST_32', 'LAST_16', 'QUARTER_FINALS', 'SEMI_FINALS', 'FINAL'];

async function renderCuadro() {
  const c = $('#content');
  c.innerHTML = `<div class="text-center text-slate-500 py-10">Cargando cuadro…</div>`;
  try {
    const stages = await api('/bracket');
    const map = {};
    stages.forEach((s) => { map[s.fase] = s.partidos || []; });
    const anyData = stages.some((s) => s.partidos && s.partidos.length);
    const n = BRACKET_ROUNDS.length;

    const cols = BRACKET_ROUNDS.map((stage, i) => {
      const slots = Math.pow(2, n - 1 - i);
      const ms = map[stage] || [];
      let cells = '';
      for (let j = 0; j < slots; j++) {
        const bus = (i < n - 1 && j % 2 === 0) ? '<span class="bkt-bus"></span>' : '';
        cells += `<div class="bkt-cell">${bracketCard(ms[j])}${bus}</div>`;
      }
      return `<div class="bkt-round"><div class="bkt-title">${FASE_LABEL[stage] || stage}</div>
                <div class="bkt-list">${cells}</div></div>`;
    }).join('');

    const minH = Math.pow(2, n - 1) * 56;
    const third = (map['THIRD_PLACE'] || [])[0];
    const thirdHtml = third ? `
      <div class="mt-5 max-w-xs">
        <div class="bkt-title text-left">${FASE_LABEL['THIRD_PLACE']}</div>
        ${bracketCard(third)}
      </div>` : '';

    c.innerHTML = `
      <div class="w-full">
        <h2 class="text-lg font-bold mb-1">🏆 Cuadro del Mundial</h2>
        ${anyData ? '' : `<p class="text-slate-500 text-sm mb-3">El cuadro se rellenará automáticamente cuando empiece la fase eliminatoria.</p>`}
        <div class="overflow-x-auto pb-4"><div class="bkt" style="min-height:${minH}px">${cols}</div></div>
        ${thirdHtml}
      </div>`;
  } catch (e) {
    c.innerHTML = `<div class="text-center text-red-400 py-10">${e.message}</div>`;
  }
}

function bracketCard(m) {
  if (!m) {
    return `<div class="bkt-card bg-slate-800/40 border border-dashed border-slate-700 rounded-lg h-12"></div>`;
  }
  const gl = m.golesLocal, gv = m.golesVisitante;
  const jugado = gl != null && gv != null;
  const row = (nombre, escudoUrl, goles, gana) => `
    <div class="flex items-center justify-between gap-1 ${gana ? 'text-green-400 font-semibold' : 'text-slate-200'}">
      <span class="flex items-center gap-1 min-w-0">${escudo(escudoUrl, nombre)}<span class="truncate text-[11px]">${nombre || 'Por definir'}</span></span>
      <span class="text-[11px] tabular-nums">${goles ?? ''}</span>
    </div>`;
  return `
    <div class="bkt-card bg-slate-800 rounded-lg p-1.5 space-y-0.5">
      ${row(m.local, m.escudoLocal, gl, jugado && m.ganador === 'HOME_TEAM')}
      ${row(m.visitante, m.escudoVisitante, gv, jugado && m.ganador === 'AWAY_TEAM')}
    </div>`;
}

// Botón de cuota genérico para cualquier mercado. `group` agrupa mercados correlacionados:
// dentro de un partido solo se admite una selección por grupo (1X2 y Doble oportunidad = "RESULTADO").
function oddBtn(m, o, top, group) {
  if (!o) return `<div class="flex-1"></div>`;
  return `
    <button data-opt="${o.id}" data-match="${m.id}" data-group="${group}" data-cuota="${o.cuota}"
            data-label="${m.equipoLocal} vs ${m.equipoVisitante} · ${o.descripcion}"
            class="flex-1 min-w-0 bg-slate-700 rounded-lg py-1.5 active:bg-slate-600 transition">
      <div class="text-[10px] text-slate-400 truncate px-1">${top}</div>
      <div class="font-bold text-sm">${fmt(o.cuota)}</div>
      ${o.casa ? `<div class="text-[9px] text-slate-500 truncate px-1" title="${o.casa}">${o.casa}</div>` : ''}
    </button>`;
}

// Bloque de un mercado: pequeño título + contenido.
function marketBlock(title, content) {
  return `<div class="mt-2">
      <div class="text-[10px] text-slate-400 mb-1">${title}</div>
      ${content}
    </div>`;
}

// Bloque Over/Under: una fila por línea (0.5, 1.5, 2.5...) con etiquetas claras.
function overUnderBlock(m, mou) {
  const lineas = [...new Set(mou.opciones.map((o) => o.linea))].sort((a, b) => a - b);
  const find = (code) => mou.opciones.find((o) => o.codigo === code);
  const rows = lineas.map((ln) => {
    const l = String(ln).replace(/\.0$/, '');
    return `<div class="flex gap-1.5 mb-1.5">
        ${oddBtn(m, find('OVER_' + l), 'Más de ' + l, 'OVER_UNDER')}
        ${oddBtn(m, find('UNDER_' + l), 'Menos de ' + l, 'OVER_UNDER')}
      </div>`;
  }).join('');
  return marketBlock('Goles (Over/Under)', rows);
}

function matchCard(m) {
  const market = (tipo) => (m.mercados || []).find((x) => x.tipo === tipo);
  const opt = (mkt, code) => mkt && mkt.opciones.find((o) => o.codigo === code);

  const m1x2 = market('UNO_X_DOS');
  const main = `<div class="flex gap-1.5">
      ${oddBtn(m, opt(m1x2, 'LOCAL'), '1', 'RESULTADO')}${oddBtn(m, opt(m1x2, 'EMPATE'), 'X', 'RESULTADO')}${oddBtn(m, opt(m1x2, 'VISITANTE'), '2', 'RESULTADO')}
    </div>`;

  // Mercados adicionales (si existen).
  const mou = market('OVER_UNDER'), mdc = market('DOBLE_OPORTUNIDAD'), mbtts = market('AMBOS_MARCAN');
  let extra = '';
  if (mou && mou.opciones.length) extra += overUnderBlock(m, mou);
  // Doble oportunidad comparte grupo "RESULTADO" con el 1X2 (no se combinan entre sí).
  if (mdc) extra += marketBlock('Doble oportunidad', `<div class="flex gap-1.5">
    ${oddBtn(m, opt(mdc, '1X'), '1X', 'RESULTADO')}${oddBtn(m, opt(mdc, '12'), '12', 'RESULTADO')}${oddBtn(m, opt(mdc, 'X2'), 'X2', 'RESULTADO')}</div>`);
  if (mbtts) extra += marketBlock('Ambos marcan', `<div class="flex gap-1.5">
    ${oddBtn(m, opt(mbtts, 'BTTS_SI'), 'Sí', 'AMBOS_MARCAN')}${oddBtn(m, opt(mbtts, 'BTTS_NO'), 'No', 'AMBOS_MARCAN')}</div>`);

  const more = extra ? `
      <button class="more-btn w-full mt-2 text-[11px] text-green-400" data-target="extra-${m.id}">+ Más apuestas</button>
      <div id="extra-${m.id}" class="hidden">${extra}</div>` : '';

  return `
    <div class="bg-slate-800 rounded-xl p-3">
      <div class="flex justify-between items-center mb-2">
        <div class="text-[10px] text-slate-400">${m.fase || ''}</div>
        <div class="text-[10px] text-slate-400">${fmtDate(m.inicioUtc)}</div>
      </div>
      <div class="flex items-center justify-between mb-2 text-sm font-semibold gap-1">
        <span class="flex items-center gap-1 flex-1 min-w-0">${flag(m.equipoLocal)}<span class="truncate">${m.equipoLocal}</span></span>
        <span class="text-slate-500 text-xs px-1">vs</span>
        <span class="flex items-center gap-1 flex-1 min-w-0 justify-end"><span class="truncate">${m.equipoVisitante}</span>${flag(m.equipoVisitante)}</span>
      </div>
      ${main}
      ${more}
    </div>`;
}

/* ---------------- Bet slip ---------------- */
function toggleSelection(btn) {
  const id = Number(btn.dataset.opt);
  const matchId = Number(btn.dataset.match);
  const group = btn.dataset.group;
  const idx = slip.findIndex((s) => s.opcionCuotaId === id);
  if (idx >= 0) {
    // Ya estaba seleccionada -> quitarla.
    slip.splice(idx, 1);
  } else {
    // Combinada del mismo partido permitida, pero solo UNA por grupo correlacionado: al elegir otra
    // opción del mismo partido+grupo (Más/Menos, 1X/12/X2, Sí/No, o 1X2 vs Doble oportunidad) la sustituye.
    slip = slip.filter((s) => !(s.matchId === matchId && s.group === group));
    slip.push({ opcionCuotaId: id, matchId, group, label: btn.dataset.label, cuota: Number(btn.dataset.cuota) });
  }
  refreshOddButtons();
  renderSlip();
}

function refreshOddButtons() {
  document.querySelectorAll('[data-opt]').forEach((b) => {
    const active = slip.some((s) => s.opcionCuotaId === Number(b.dataset.opt));
    b.classList.toggle('bg-green-600', active);
    b.classList.toggle('bg-slate-700', !active);
  });
}

function totalOdds() { return slip.reduce((acc, s) => acc * s.cuota, 1); }

function renderSlip() {
  const count = slip.length;
  $('#fab-count').textContent = count;
  $('#slip-count').textContent = count ? `(${count})` : '';
  $('#slip-fab').classList.toggle('hidden', count === 0 || isSheetOpen());

  $('#slip-items').innerHTML = slip.map((s, i) => `
    <div class="flex items-center justify-between bg-slate-700 rounded-xl px-3 py-2">
      <div class="text-sm pr-2">${s.label}</div>
      <div class="flex items-center gap-3">
        <span class="font-bold">${fmt(s.cuota)}</span>
        <button data-rm="${i}" class="text-red-400 text-lg leading-none">×</button>
      </div>
    </div>`).join('') || `<div class="text-slate-500 text-sm text-center py-4">Pulsa una cuota para añadirla.</div>`;

  $('#slip-items').querySelectorAll('[data-rm]').forEach((b) => {
    b.onclick = () => { slip.splice(Number(b.dataset.rm), 1); refreshOddButtons(); renderSlip(); };
  });

  const total = totalOdds();
  $('#slip-total').textContent = count ? fmt(total) : '—';
  updateReturn();
  if (count === 0 && isSheetOpen()) closeSheet();
}

function updateReturn() {
  const stake = Number($('#slip-stake').value);
  $('#slip-return').textContent = (stake > 0 && slip.length) ? fmt(stake * totalOdds()) : '—';
}

function isSheetOpen() { return $('#slip').classList.contains('sheet-open'); }
function openSheet() {
  if (!slip.length) return;
  $('#slip').classList.remove('sheet-enter'); $('#slip').classList.add('sheet-open');
  $('#slip-backdrop').classList.remove('hidden');
  $('#slip-fab').classList.add('hidden');
}
function closeSheet() {
  $('#slip').classList.add('sheet-enter'); $('#slip').classList.remove('sheet-open');
  $('#slip-backdrop').classList.add('hidden');
  if (slip.length) $('#slip-fab').classList.remove('hidden');
}

function setupSlip() {
  $('#slip-fab').onclick = openSheet;
  $('#slip-handle').onclick = closeSheet;
  $('#slip-backdrop').onclick = closeSheet;
  $('#slip-clear').onclick = () => { slip = []; refreshOddButtons(); renderSlip(); };
  $('#slip-stake').addEventListener('input', updateReturn);
  $('#slip-confirm').onclick = confirmBet;
}

async function confirmBet() {
  const stake = Number($('#slip-stake').value);
  $('#slip-error').textContent = '';
  if (!slip.length) { $('#slip-error').textContent = 'Añade al menos una selección.'; return; }
  if (!(stake > 0)) { $('#slip-error').textContent = 'Introduce un importe válido.'; return; }
  try {
    await api('/bets', { method: 'POST', body: {
      importe: stake,
      selecciones: slip.map((s) => ({ opcionCuotaId: s.opcionCuotaId })),
    }});
    slip = [];
    $('#slip-stake').value = '';
    closeSheet();
    await refreshBalance();
    selectTab('apuestas');
  } catch (e) {
    $('#slip-error').textContent = e.message;
  }
}

/* ---------------- Mis Apuestas ---------------- */
let apuestasFilter = 'PENDIENTE';
async function renderApuestas() {
  const c = $('#content');
  c.innerHTML = `
    <div class="mx-auto w-full max-w-2xl">
      <div class="flex bg-slate-800 rounded-xl p-1 mb-4">
        <button id="f-pend" class="flex-1 py-2 rounded-lg text-sm font-semibold">Pendientes</button>
        <button id="f-res" class="flex-1 py-2 rounded-lg text-sm font-semibold">Resueltas</button>
      </div>
      <div id="bets-list"><div class="text-center text-slate-500 py-10">Cargando…</div></div>
    </div>`;
  $('#f-pend').onclick = () => { apuestasFilter = 'PENDIENTE'; renderApuestas(); };
  $('#f-res').onclick = () => { apuestasFilter = 'RESUELTO'; renderApuestas(); };
  const pend = apuestasFilter === 'PENDIENTE';
  $('#f-pend').className = `flex-1 py-2 rounded-lg text-sm font-semibold ${pend ? 'bg-green-600' : 'text-slate-300'}`;
  $('#f-res').className = `flex-1 py-2 rounded-lg text-sm font-semibold ${!pend ? 'bg-green-600' : 'text-slate-300'}`;

  try {
    const bets = await api(`/bets?estado=${apuestasFilter}`);
    const list = $('#bets-list');
    if (!bets.length) { list.innerHTML = `<div class="text-center text-slate-500 py-10">Sin apuestas aquí.</div>`; return; }
    list.innerHTML = bets.map(betCard).join('');
  } catch (e) {
    $('#bets-list').innerHTML = `<div class="text-center text-red-400 py-10">${e.message}</div>`;
  }
}

function betCard(b) {
  const sels = b.selecciones.map((s) => `
    <div class="flex justify-between text-sm py-1 border-t border-slate-700/60">
      <span class="text-slate-300 pr-2">${s.descripcion}</span>
      <span class="${ESTADO_COLOR[s.resultado] || 'text-slate-400'} font-semibold">${fmt(s.cuota)}</span>
    </div>`).join('');
  return `
    <div class="bg-slate-800 rounded-2xl p-4 mb-3">
      <div class="flex justify-between items-center mb-1">
        <span class="text-xs px-2 py-0.5 rounded-full bg-slate-700">${b.tipo}</span>
        <span class="font-bold ${ESTADO_COLOR[b.estado]}">${b.estado}</span>
      </div>
      <div class="text-[11px] text-slate-400 mb-2">${fmtDate(b.creadoEn)}</div>
      ${sels}
      <div class="flex justify-between mt-3 text-sm">
        <span class="text-slate-400">Importe <b class="text-slate-200">${fmt(b.importe)}</b></span>
        <span class="text-slate-400">Cuota <b class="text-slate-200">${fmt(b.cuotaTotal)}</b></span>
        <span class="text-slate-400">Gana <b class="text-green-400">${fmt(b.retornoPotencial)}</b></span>
      </div>
    </div>`;
}

/* ---------------- Billetera ---------------- */
let walletFlash = null;   // mensaje a mostrar tras re-renderizar (bono/reset)
let bonusTimer = null;    // intervalo de la cuenta atrás del bono

function clearBonusTimer() { if (bonusTimer) { clearInterval(bonusTimer); bonusTimer = null; } }

function startBonusCountdown(iso) {
  clearBonusTimer();
  const tick = () => {
    const el = document.getElementById('bonus-countdown');
    if (!el) { clearBonusTimer(); return; }           // hemos cambiado de pantalla
    const ms = new Date(iso) - new Date();
    if (ms <= 0) { clearBonusTimer(); renderBilletera(); return; }  // ya disponible -> recargar
    const s = Math.floor(ms / 1000);
    const h = String(Math.floor(s / 3600)).padStart(2, '0');
    const m = String(Math.floor((s % 3600) / 60)).padStart(2, '0');
    const ss = String(s % 60).padStart(2, '0');
    el.textContent = `${h}:${m}:${ss}`;
  };
  tick();
  bonusTimer = setInterval(tick, 1000);
}

async function renderBilletera() {
  clearBonusTimer();
  const c = $('#content');
  c.innerHTML = `<div class="text-center text-slate-500 py-10">Cargando…</div>`;
  try {
    const [wallet, txs, board] = await Promise.all([
      api('/wallet'), api('/wallet/transactions'), api('/leaderboard'),
    ]);
    updateBalance(wallet.saldo);

    const bonoBtn = wallet.bonoDisponible
      ? `<button id="btn-bonus" class="py-3 rounded-xl bg-green-700 font-semibold active:bg-green-800">🎁 Bono diario (+10)</button>`
      : `<button id="btn-bonus" disabled class="py-3 rounded-xl bg-slate-800/60 text-slate-400 font-semibold cursor-not-allowed leading-tight">
           <div>⏳ Próximo bono</div><div id="bonus-countdown" class="text-xs tabular-nums">--:--:--</div></button>`;

    c.innerHTML = `
     <div class="mx-auto w-full max-w-2xl">
      <div class="bg-gradient-to-br from-green-700 to-green-600 rounded-2xl p-5 mb-4">
        <div class="text-green-100 text-sm">Saldo disponible</div>
        <div class="text-4xl font-extrabold">${fmt(wallet.saldo)}</div>
        <div class="text-green-100 text-xs mt-1">@${wallet.username}</div>
      </div>
      <div class="grid grid-cols-2 gap-3 mb-5">
        ${bonoBtn}
        <button id="btn-reset" class="py-3 rounded-xl bg-slate-800 font-semibold active:bg-slate-700">♻️ Reiniciar saldo</button>
      </div>
      <p id="wallet-msg" class="text-center text-sm min-h-[1.25rem] mb-3"></p>

      <h3 class="font-bold mb-2">🏆 Ranking (beneficio neto)</h3>
      <div class="bg-slate-800 rounded-2xl p-2 mb-5">
        ${board.map((r) => `
          <div class="flex justify-between items-center px-3 py-2">
            <span><b class="text-slate-400 mr-2">${r.posicion}</b>${r.username}</span>
            <span class="font-bold ${Number(r.beneficioNeto) >= 0 ? 'text-green-400' : 'text-red-400'}">${fmt(r.beneficioNeto)}</span>
          </div>`).join('')}
      </div>

      <h3 class="font-bold mb-2">Movimientos</h3>
      <div class="bg-slate-800 rounded-2xl p-2 mb-4">
        ${[...txs].reverse().map((t) => `
          <div class="flex justify-between items-center px-3 py-2 text-sm border-b border-slate-700/40 last:border-0">
            <span class="text-slate-300">${t.tipo}</span>
            <span class="${Number(t.importe) >= 0 ? 'text-green-400' : 'text-red-400'} font-semibold">${Number(t.importe) >= 0 ? '+' : ''}${fmt(t.importe)}</span>
          </div>`).join('') || '<div class="text-slate-500 text-sm text-center py-4">Sin movimientos.</div>'}
      </div>
      <button id="btn-logout" class="w-full py-3 rounded-xl bg-slate-800 text-red-400 font-semibold">Cerrar sesión</button>
     </div>`;

    if (walletFlash) { flashWallet(walletFlash.msg, walletFlash.ok); walletFlash = null; }

    if (wallet.bonoDisponible) {
      $('#btn-bonus').onclick = async () => {
        try { await api('/wallet/daily-bonus', { method: 'POST' }); walletFlash = { msg: '¡Bono reclamado! +10 monedas', ok: true }; }
        catch (e) { walletFlash = { msg: e.message, ok: false }; }
        renderBilletera();
      };
    } else if (wallet.proximoBono) {
      startBonusCountdown(wallet.proximoBono);
    }

    $('#btn-reset').onclick = async () => {
      try { await api('/wallet/reset', { method: 'POST' }); walletFlash = { msg: 'Saldo reiniciado', ok: true }; }
      catch (e) { walletFlash = { msg: e.message, ok: false }; }
      renderBilletera();
    };
    $('#btn-logout').onclick = logout;
  } catch (e) {
    c.innerHTML = `<div class="text-center text-red-400 py-10">${e.message}</div>`;
  }
}
function flashWallet(msg, ok) {
  const el = $('#wallet-msg'); if (!el) return;
  el.textContent = msg; el.className = `text-center text-sm min-h-[1.25rem] mb-3 ${ok ? 'text-green-400' : 'text-red-400'}`;
}

/* ---------------- Saldo / arranque ---------------- */
function updateBalance(saldo) { $('#hdr-balance').textContent = fmt(saldo); }
async function refreshBalance() {
  try { const w = await api('/wallet'); updateBalance(w.saldo); } catch {}
}

function enterApp() {
  $('#auth-screen').classList.add('hidden');
  $('#app').classList.remove('hidden');
  refreshBalance();
  selectTab('cartelera');
}
function logout() { store.clear(); slip = []; location.reload(); }

function init() {
  setupAuth(); setupNav(); setupSlip();
  if (store.access) {
    enterApp();
  } else {
    $('#auth-screen').classList.remove('hidden');
    switchAuth('login');
  }
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  }
}
init();
