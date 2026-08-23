export const WORKSPACE_PWA_SCOPE = '/workspace';
export const PWA_UPDATE_AVAILABLE_EVENT = 'pwa-update-available';

export function registerServiceWorker(): void {
    if (typeof window === 'undefined' || !('serviceWorker' in navigator)) return;
    window.addEventListener('load', () => {
        void navigator.serviceWorker.register('/sw.js', { scope: WORKSPACE_PWA_SCOPE }).then((registration) => {
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
