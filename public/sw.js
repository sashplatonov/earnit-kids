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
        }).catch(() => new Response('', { status: 503 }))
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

self.addEventListener('push', (event) => {
    let payload = {};
    try {
        payload = event.data ? event.data.json() : {};
    } catch (_) {
        payload = { title: 'EarnIt Kids', body: event.data ? event.data.text() : 'Новое уведомление' };
    }

    const title = payload.title || 'EarnIt Kids';
    const options = {
        body: payload.body || 'Новое уведомление',
        icon: '/img/favicon-32x32.png',
        badge: '/img/favicon-32x32.png',
        data: payload.data || {},
        tag: payload.data?.eventType || 'push',
        renotify: true
    };

    event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    event.waitUntil(
        self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
            for (const client of clientList) {
                if ('focus' in client) return client.focus();
            }
            return self.clients.openWindow('/');
        })
    );
});
