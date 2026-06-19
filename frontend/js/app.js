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

  if (res.status === 401 && auth && retry && store.refresh) {
    const ok = await tryRefresh();
    if (ok) return api(path, { method, body, auth, retry: false });
  }
  const data = res.status === 204 ? null : await res.json().catch(() => null);
  if (!res.ok) throw new Error((data && data.message) || `Error ${res.status}`);
  return data;
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
  if (currentTab === 'cartelera') return renderCartelera();
  if (currentTab === 'apuestas') return renderApuestas();
  if (currentTab === 'billetera') return renderBilletera();
}

/* ---------------- Cartelera ---------------- */
async function renderCartelera() {
  const c = $('#content');
  c.innerHTML = `<div class="text-center text-slate-500 py-10">Cargando partidos…</div>`;
  try {
    const matches = await api('/matches');
    if (!matches.length) { c.innerHTML = `<div class="text-center text-slate-500 py-10">No hay partidos disponibles.</div>`; return; }
    c.innerHTML = matches.map(matchCard).join('');
    c.querySelectorAll('[data-opt]').forEach((btn) => {
      btn.onclick = () => toggleSelection(btn);
    });
    refreshOddButtons();
  } catch (e) {
    c.innerHTML = `<div class="text-center text-red-400 py-10">${e.message}</div>`;
  }
}

function matchCard(m) {
  const mkt = (m.mercados || []).find((x) => x.tipo === 'UNO_X_DOS');
  const opts = mkt ? mkt.opciones : [];
  const oddBtn = (o) => o ? `
    <button data-opt="${o.id}" data-cuota="${o.cuota}" data-label="${m.equipoLocal} vs ${m.equipoVisitante} · ${o.descripcion}"
            class="flex-1 bg-slate-700 rounded-xl py-2 active:bg-slate-600 transition">
      <div class="text-[11px] text-slate-400">${o.codigo === 'LOCAL' ? '1' : o.codigo === 'EMPATE' ? 'X' : '2'}</div>
      <div class="font-bold">${fmt(o.cuota)}</div>
    </button>` : `<div class="flex-1"></div>`;
  const find = (code) => opts.find((o) => o.codigo === code);
  return `
    <div class="bg-slate-800 rounded-2xl p-4 mb-3">
      <div class="flex justify-between items-center mb-3">
        <div class="text-[11px] text-slate-400">${m.fase || ''}</div>
        <div class="text-[11px] text-slate-400">${fmtDate(m.inicioUtc)}</div>
      </div>
      <div class="flex justify-between items-center mb-3 font-semibold">
        <span>${m.equipoLocal}</span><span class="text-slate-500 text-sm">vs</span><span>${m.equipoVisitante}</span>
      </div>
      <div class="flex gap-2">
        ${oddBtn(find('LOCAL'))}${oddBtn(find('EMPATE'))}${oddBtn(find('VISITANTE'))}
      </div>
    </div>`;
}

/* ---------------- Bet slip ---------------- */
function toggleSelection(btn) {
  const id = Number(btn.dataset.opt);
  const idx = slip.findIndex((s) => s.opcionCuotaId === id);
  if (idx >= 0) {
    slip.splice(idx, 1);
  } else {
    slip.push({ opcionCuotaId: id, label: btn.dataset.label, cuota: Number(btn.dataset.cuota) });
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
    <div class="flex bg-slate-800 rounded-xl p-1 mb-4">
      <button id="f-pend" class="flex-1 py-2 rounded-lg text-sm font-semibold">Pendientes</button>
      <button id="f-res" class="flex-1 py-2 rounded-lg text-sm font-semibold">Resueltas</button>
    </div>
    <div id="bets-list"><div class="text-center text-slate-500 py-10">Cargando…</div></div>`;
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
async function renderBilletera() {
  const c = $('#content');
  c.innerHTML = `<div class="text-center text-slate-500 py-10">Cargando…</div>`;
  try {
    const [wallet, txs, board] = await Promise.all([
      api('/wallet'), api('/wallet/transactions'), api('/leaderboard'),
    ]);
    c.innerHTML = `
      <div class="bg-gradient-to-br from-green-700 to-green-600 rounded-2xl p-5 mb-4">
        <div class="text-green-100 text-sm">Saldo disponible</div>
        <div class="text-4xl font-extrabold">${fmt(wallet.saldo)}</div>
        <div class="text-green-100 text-xs mt-1">@${wallet.username}</div>
      </div>
      <div class="grid grid-cols-2 gap-3 mb-5">
        <button id="btn-bonus" class="py-3 rounded-xl bg-slate-800 font-semibold active:bg-slate-700">🎁 Bono diario (+10)</button>
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
      <button id="btn-logout" class="w-full py-3 rounded-xl bg-slate-800 text-red-400 font-semibold">Cerrar sesión</button>`;

    $('#btn-bonus').onclick = async () => {
      try { const w = await api('/wallet/daily-bonus', { method: 'POST' }); flashWallet('¡Bono reclamado! +10', true); updateBalance(w.saldo); }
      catch (e) { flashWallet(e.message, false); }
    };
    $('#btn-reset').onclick = async () => {
      const w = await api('/wallet/reset', { method: 'POST' }); flashWallet('Saldo reiniciado', true); updateBalance(w.saldo); renderBilletera();
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
