export const WORKSPACE_PWA_SCOPE = '/app';
const LEGACY_WORKSPACE_PWA_SCOPE = '/workspace';
export const PWA_UPDATE_AVAILABLE_EVENT = 'pwa-update-available';

function waitForActive(registration: ServiceWorkerRegistration): Promise<boolean> {
    if (registration.active?.state === 'activated') return Promise.resolve(true);

    return new Promise((resolve) => {
        const workers = [registration.installing, registration.waiting, registration.active].filter(Boolean) as ServiceWorker[];
        let settled = false;
        const finish = (active: boolean) => {
            if (settled) return;
            settled = true;
            workers.forEach((worker) => worker.removeEventListener('statechange', onStateChange));
            clearTimeout(timeout);
            resolve(active);
        };
        const onStateChange = () => {
            if (registration.active?.state === 'activated') finish(true);
            else if (workers.some((worker) => worker.state === 'redundant')) finish(false);
        };
        const timeout = setTimeout(() => finish(false), 10000);

        if (registration.active?.state === 'activated') finish(true);
        else if (workers.length === 0) finish(false);
        else workers.forEach((worker) => worker.addEventListener('statechange', onStateChange));
    });
}

async function unregisterLegacyScope(): Promise<void> {
    const legacyScope = new URL(LEGACY_WORKSPACE_PWA_SCOPE, window.location.origin).href;
    const registrations = await navigator.serviceWorker.getRegistrations();
    const legacyRegistration = registrations.find((registration) => registration.scope === legacyScope);
    if (legacyRegistration) await legacyRegistration.unregister();
}

export function registerServiceWorker(): void {
    if (typeof window === 'undefined' || !('serviceWorker' in navigator)) return;
    window.addEventListener('load', () => {
        void navigator.serviceWorker.register('/sw.js', { scope: WORKSPACE_PWA_SCOPE }).then(async (registration) => {
            if (!await waitForActive(registration)) return;
            await unregisterLegacyScope().catch(() => undefined);

            const announceWaitingWorker = () => {
                if (registration.waiting && navigator.serviceWorker.controller) {
                    window.dispatchEvent(new CustomEvent(PWA_UPDATE_AVAILABLE_EVENT, { detail: registration }));
                }
            };

            announceWaitingWorker();
            registration.addEventListener('updatefound', () => {
                const worker = registration.installing;
                if (!worker) return;
                worker.addEventListener('statechange', () => {
                    if (worker.state === 'installed') announceWaitingWorker();
                });
            });
        }).catch(() => undefined);
    }, { once: true });
}
