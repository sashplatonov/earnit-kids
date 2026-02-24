const SW_VERSION = new URL(self.location.href).searchParams.get('v') || 'dev';
const CACHE_NAME = `coin-shop-${SW_VERSION}`;
const ASSETS_TO_CACHE = [
    '/css/style.css',
    '/js/modules/main.js',
    '/img/favicon-32x32.png'
];

const cacheInstall = async () => {
    await self.skipWaiting();
    const cache = await caches.open(CACHE_NAME);
    try {
        await cache.addAll(ASSETS_TO_CACHE);
    } catch (err) {
        console.warn('Service Worker: Some assets failed to cache during install', err);
    }
};

self.addEventListener('install', (event) => {
    event.waitUntil(cacheInstall());
});

self.addEventListener('message', (event) => {
    if (event.data && event.data.type === 'SKIP_WAITING') {
        self.skipWaiting();
    }
});

self.addEventListener('fetch', (event) => {
    if (event.request.method !== 'GET') return;
    const requestUrl = new URL(event.request.url);

    if (requestUrl.pathname === '/sw.js' || requestUrl.pathname === '/manifest.json') {
        return;
    }

    if (event.request.url.includes('/api/')) {
        event.respondWith(
            fetch(event.request).catch(() => new Response(JSON.stringify({ error: 'Вы находитесь оффлайн. Данные недоступны.' }), {
                status: 503,
                headers: { 'Content-Type': 'application/json' }
            }))
        );
        return;
    }

    if (event.request.mode === 'navigate') {
        event.respondWith(
            fetch(event.request).then((response) => {
                if (response.status === 200) {
                    const copy = response.clone();
                    caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy));
                }
                return response;
            }).catch(() => caches.match(event.request))
        );
        return;
    }

    event.respondWith(
        caches.match(event.request).then((response) => {
            return response || fetch(event.request).then((fetchResponse) => {
                if (fetchResponse.status === 200) {
                    const cacheCopy = fetchResponse.clone();
                    caches.open(CACHE_NAME).then((cache) => {
                        cache.put(event.request, cacheCopy);
                    });
                }
                return fetchResponse;
            });
        }).catch(() => {
            /* Optional offline page */
        })
    );
});

self.addEventListener('activate', (event) => {
    event.waitUntil(Promise.all([
        self.clients.claim(),
        caches.keys().then((cacheNames) => Promise.all(
            cacheNames
                .filter((cacheName) => cacheName !== CACHE_NAME)
                .map((cacheName) => caches.delete(cacheName))
        ))
    ]));
});
