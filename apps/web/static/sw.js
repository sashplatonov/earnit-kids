const SW_VERSION = new URL(self.location.href).searchParams.get('v') || 'dev';
const CACHE_NAME = `earnit-static-${SW_VERSION}`;
const ASSETS_TO_CACHE = [
    '/manifest.json',
    '/public/favicon-32x32.png',
    '/public/apple-touch-icon.png',
    '/public/assets/icons/app-icon.png'
];

const cacheInstall = async () => {
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

    if (requestUrl.origin !== self.location.origin || requestUrl.pathname.startsWith('/api/') || requestUrl.pathname.startsWith('/invite/parent/') || requestUrl.pathname.startsWith('/login-child/') || requestUrl.pathname.startsWith('/oauth/')) return;

    if (event.request.mode === 'navigate') {
        event.respondWith(
            fetch(event.request)
                .then((response) => {
                    return response;
                })
                .catch(() => new Response('<!doctype html><title>Offline</title><main>You are offline. Protected data is unavailable.</main>', { status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' } }))
        );
        return;
    }

    if (!['style', 'script', 'image', 'font'].includes(event.request.destination)) return;
    event.respondWith(
        caches.match(event.request)
            .then((response) => response || fetch(event.request).then((fetchResponse) => {
                if (fetchResponse.status === 200) {
                    const cacheCopy = fetchResponse.clone();
                    caches.open(CACHE_NAME).then((cache) => {
                        cache.put(event.request, cacheCopy);
                    });
                }
                return fetchResponse;
            }))
            .catch(() => new Response('', { status: 503 }))
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
    let payload;
    try {
        payload = event.data ? event.data.json() : {};
    } catch {
        payload = { title: 'EarnIt Kids', body: event.data ? event.data.text() : 'Новое уведомление' };
    }

    const title = payload.title || 'EarnIt Kids';
    const options = {
        body: payload.body || 'Новое уведомление',
        icon: '/public/favicon-32x32.png',
        badge: '/public/favicon-32x32.png',
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
            const rawTarget = event.notification.data?.url || event.notification.data?.deepLink || '/workspace';
            let target = '/workspace';
            try {
                const targetUrl = new URL(rawTarget, self.location.origin);
                if (targetUrl.origin === self.location.origin && !targetUrl.pathname.startsWith('/api/')) target = `${targetUrl.pathname}${targetUrl.search}${targetUrl.hash}`;
            } catch { /* keep safe fallback */ }
            const client = clientList.find((candidate) => new URL(candidate.url).origin === self.location.origin);
            if (client) { void client.focus(); return client.navigate(target); }
            return self.clients.openWindow(target);
        })
    );
});
