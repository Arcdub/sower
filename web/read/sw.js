// Offline support. The shell is cached on install; book files are cached as they
// are read, so the Bible grows offline as you use it (or all at once from the
// reading options sheet).
var SHELL = 'sower-shell-v1';
var DATA = 'sower-data-v1';
var SHELL_FILES = [
  './',
  'index.html',
  'styles.css',
  'app.js',
  'manifest.webmanifest',
  '../icon.png'
];

self.addEventListener('install', function (e) {
  e.waitUntil(
    caches.open(SHELL).then(function (c) { return c.addAll(SHELL_FILES); })
      .then(function () { return self.skipWaiting(); })
  );
});

self.addEventListener('activate', function (e) {
  e.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.map(function (k) {
        if (k !== SHELL && k !== DATA) return caches['delete'](k);
      }));
    }).then(function () { return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function (e) {
  var req = e.request;
  if (req.method !== 'GET') return;
  var url = new URL(req.url);
  if (url.origin !== location.origin) return;

  var isBook = /\/(bible|bible_bsb)\//.test(url.pathname);
  var cacheName = isBook ? DATA : SHELL;

  e.respondWith(
    caches.match(req).then(function (hit) {
      if (hit) {
        // Refresh the shell quietly in the background; scripture does not change.
        if (!isBook) {
          fetch(req).then(function (res) {
            if (res && res.ok) caches.open(SHELL).then(function (c) { c.put(req, res.clone()); });
          }).catch(function () {});
        }
        return hit;
      }
      return fetch(req).then(function (res) {
        if (res && res.ok) {
          var copy = res.clone();
          caches.open(cacheName).then(function (c) { c.put(req, copy); });
        }
        return res;
      });
    })
  );
});
