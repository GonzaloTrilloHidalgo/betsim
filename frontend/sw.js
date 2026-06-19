// Service Worker de BetSim — estrategia App Shell.
// Sube el número de versión al cambiar el frontend para invalidar la caché anterior.

const CACHE = 'betsim-shell-v5';
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
  if (url.pathname.includes('/api/')) return;

  // App shell (HTML y JS propios): network-first, para que los cambios se vean siempre.
  // Si no hay red, se sirve la copia cacheada (resiliencia offline).
  const isShell = event.request.mode === 'navigate'
    || url.pathname.endsWith('/app.js')
    || url.pathname.endsWith('/index.html')
    || url.pathname.endsWith('/');

  if (isShell) {
    event.respondWith(
      fetch(event.request)
        .then((res) => {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(event.request, copy));
          return res;
        })
        .catch(() => caches.match(event.request).then((c) => c || caches.match('./index.html')))
    );
    return;
  }

  // Resto de estáticos: cache-first.
  event.respondWith(caches.match(event.request).then((cached) => cached || fetch(event.request)));
});
