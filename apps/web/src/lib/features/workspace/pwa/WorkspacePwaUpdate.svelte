<script lang="ts">
    import { onMount } from 'svelte';
    import { PWA_UPDATE_AVAILABLE_EVENT } from './registerServiceWorker';
    import { useI18n } from '$lib/i18n/context';

    const i18n = useI18n();

    let registration: ServiceWorkerRegistration | null = null;
    let updating = false;
    let updateError = false;
    let reloadScheduled = false;
    let activationTimeout: ReturnType<typeof setTimeout> | undefined;

    onMount(() => {
        if (!/(^|\/)app(?:\/|$)/.test(window.location.pathname)) return;

        const announceUpdate = (event: Event) => {
            const candidate = (event as CustomEvent<ServiceWorkerRegistration>).detail;
            if (candidate?.waiting) {
                registration = candidate;
                updateError = false;
            }
        };
        const handleControllerChange = () => {
            if (!updating || reloadScheduled) return;
            reloadScheduled = true;
            window.location.reload();
        };

        window.addEventListener(PWA_UPDATE_AVAILABLE_EVENT, announceUpdate);
        navigator.serviceWorker.addEventListener('controllerchange', handleControllerChange);

        return () => {
            window.removeEventListener(PWA_UPDATE_AVAILABLE_EVENT, announceUpdate);
            navigator.serviceWorker.removeEventListener('controllerchange', handleControllerChange);
            if (activationTimeout) clearTimeout(activationTimeout);
        };
    });

    function activateUpdate(): void {
        const waitingWorker = registration?.waiting;
        if (!waitingWorker || updating) return;

        updating = true;
        updateError = false;
        try {
            waitingWorker.postMessage({ type: 'SKIP_WAITING' });
            activationTimeout = setTimeout(() => {
                if (!reloadScheduled) {
                    updating = false;
                    updateError = true;
                }
            }, 10000);
        } catch {
            updating = false;
            updateError = true;
        }
    }
</script>

{#if registration?.waiting}
    <aside class="update-notice" aria-live="polite" aria-label={$i18n.t('app.pwaUpdate.ariaLabel')}>
        <div class="update-copy">
            <strong>{$i18n.t('app.pwaUpdate.available')}</strong>
            {#if updateError}
                <span role="status">{$i18n.t('app.pwaUpdate.error')}</span>
            {:else if updating}
                <span role="status">{$i18n.t('app.pwaUpdate.updating')}</span>
            {:else}
                <span>{$i18n.t('app.pwaUpdate.description')}</span>
            {/if}
        </div>
        <button type="button" onclick={activateUpdate} disabled={updating} aria-label={$i18n.t('app.pwaUpdate.button')}>
            {updating ? $i18n.t('app.pwaUpdate.updating') : $i18n.t('app.pwaUpdate.button')}
        </button>
    </aside>
{/if}

<style>
    .update-notice {
        position: fixed;
        right: 1rem;
        bottom: 1rem;
        z-index: 1000;
        display: flex;
        align-items: center;
        gap: 0.75rem;
        max-width: min(28rem, calc(100vw - 2rem));
        padding: 0.75rem 1rem;
        color: #2b211b;
        background: #f8fafc;
        border: 1px solid #d9b88e;
        border-radius: 0.75rem;
        box-shadow: 0 0.5rem 1.5rem rgb(43 33 27 / 18%);
    }

    .update-copy {
        display: grid;
        gap: 0.15rem;
        min-width: 0;
        font-size: 0.875rem;
    }

    .update-copy span { color: #68584c; }

    button {
        flex: 0 0 auto;
        min-height: 2.75rem;
        padding: 0.5rem 0.8rem;
        color: #fff;
        background: #6b4eff;
        border: 0;
        border-radius: 0.5rem;
        font: inherit;
        font-weight: 700;
        cursor: pointer;
    }

    button:disabled { cursor: wait; opacity: 0.7; }
    button:focus-visible { outline: 3px solid #80aaff; outline-offset: 2px; }

    @media (max-width: 360px) {
        .update-notice { right: 0.5rem; bottom: 0.5rem; left: 0.5rem; }
    }
</style>
