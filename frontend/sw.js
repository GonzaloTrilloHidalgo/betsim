// Service Worker de BetSim — estrategia App Shell.
// Cachea los estáticos para carga instantánea y resiliencia offline del armazón.
// Las llamadas a la API van siempre a la red (network-only): los datos no se cachean.

const CACHE = 'betsim-shell-v1';
const SHELL = [
  './',
  './index.html',
  './js/app.js',
  './manifest.json',
  './icons/icon.svg'
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // Nunca cachear la API: siempre red.
  if (url.pathname.includes('/api/')) {
    return; // deja pasar a la red por defecto
  }

  // App Shell: cache-first para estáticos.
  event.respondWith(
    caches.match(event.request).then((cached) => cached || fetch(event.request))
  );
});
