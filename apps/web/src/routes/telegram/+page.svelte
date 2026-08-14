<script lang="ts">
    import { onMount } from 'svelte';
    import {
        completeTelegramAccountLink,
        exchangeTelegramInitData,
        initializeTelegramWebApp,
    } from '$lib/services/telegram';
    import TelegramRoleResolver from '$lib/components/telegram/TelegramRoleResolver.svelte';
    import TelegramActionButton from '$lib/components/telegram/TelegramActionButton.svelte';
    import TelegramCoin from '$lib/components/telegram/TelegramCoin.svelte';

    type State = 'loading' | 'ready' | 'retry' | 'unavailable' | 'unlinked' | 'non-telegram';
    let state: State = 'loading';
    let message = '';
    let verifiedRole = '';

    async function errorCode(response: Response): Promise<string | null> {
        try {
            const payload: unknown = await response.clone().json();
            if (payload && typeof payload === 'object'
                && typeof (payload as { errorCode?: unknown }).errorCode === 'string') {
                return (payload as { errorCode: string }).errorCode;
            }
        } catch {
            // The endpoint may intentionally return an empty unavailable response.
        }
        return null;
    }

    async function authenticate(): Promise<void> {
        const telegram = initializeTelegramWebApp();
        if (!telegram) {
            state = 'non-telegram';
            return;
        }
        if (!telegram.initData) {
            state = 'retry';
            message = 'Open this page from the Telegram Mini App menu.';
            return;
        }
        state = 'loading';
        try {
            const pairingToken = telegram.initDataUnsafe?.start_param
                ?? new URLSearchParams(window.location.search).get('tgWebAppStartParam');
            let pairingFailed = false;
            if (pairingToken) {
                const pairing = await completeTelegramAccountLink(pairingToken, telegram.initData);
                pairingFailed = !pairing.ok;
            }
            const response = await exchangeTelegramInitData(telegram.initData);
            if (response.ok) {
                state = 'ready';
                try {
                    const payload = await response.clone().json() as { role?: unknown };
                    verifiedRole = typeof payload.role === 'string' ? payload.role : '';
                } catch {
                    verifiedRole = '';
                }
                return;
            }
            const code = await errorCode(response);
            state = response.status === 404
                ? 'unavailable'
                : pairingFailed ? 'retry'
                    : code === 'TELEGRAM_IDENTITY_UNLINKED' ? 'unlinked' : 'retry';
            message = response.status === 404
                ? 'Telegram access is not enabled for this environment yet.'
                : pairingFailed
                    ? 'We could not link this Telegram account. Return to Settings and try again.'
                    : 'We could not verify this Telegram session.';
        } catch {
            state = 'retry';
            message = 'Network error. Try again.';
        }
    }

    onMount(() => {
        void authenticate();
    });
</script>

<svelte:head>
    <title>EarnIt Kids · Telegram</title>
</svelte:head>

{#if state === 'ready' && verifiedRole}
    <TelegramRoleResolver role={verifiedRole} />
{:else}
    <main class="telegram-page" aria-live="polite">
        <div class="telegram-card">
            <div class="telegram-mark" aria-hidden="true"><TelegramCoin size={44} /></div>
            <h1>EarnIt Kids</h1>
            {#if state === 'loading'}
                <p>Checking your Telegram session…</p>
            {:else if state === 'non-telegram'}
                <p>Open this page inside Telegram to continue.</p>
            {:else if state === 'unavailable'}
                <p>{message}</p>
            {:else if state === 'unlinked'}
                <p>This Telegram account is not linked to a family yet.</p>
                <a class="telegram-action" href="/login">Sign in as a parent to link it</a>
                <p class="telegram-hint">For a child account, ask a parent to send an invitation.</p>
            {:else}
                <p>{message || 'Could not resolve your Telegram role. Try again.'}</p>
                <TelegramActionButton icon="refresh" label="Try again" on:click={() => void authenticate()} />
            {/if}
        </div>
    </main>
{/if}

<style>
    .telegram-page {
        display: grid;
        min-height: calc(100dvh - env(safe-area-inset-top) - env(safe-area-inset-bottom));
        place-items: center;
        padding: 1rem 0;
    }

    .telegram-card {
        box-sizing: border-box;
        width: min(100%, 28rem);
        padding: clamp(1.5rem, 7vw, 3rem);
        border: 1px solid #dfe4ee;
        border-radius: 1.25rem;
        background: #fff;
        text-align: center;
        box-shadow: 0 1rem 3rem rgb(27 39 73 / 10%);
    }

    .telegram-mark {
        display: grid;
        place-items: center;
    }

    h1 {
        margin: 0.5rem 0;
        color: #18243d;
        font-size: clamp(1.5rem, 7vw, 2rem);
    }

    p {
        margin: 0;
        color: #5c6780;
        line-height: 1.5;
    }

    .telegram-action {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 7rem;
        min-height: 2.75rem;
        margin-top: 1.25rem;
        padding: 0.65rem 1rem;
        border: 0;
        border-radius: 0.75rem;
        background: #3867d6;
        color: #fff;
        font: inherit;
        text-decoration: none;
        cursor: pointer;
    }

    .telegram-hint {
        margin-top: 1rem;
        font-size: 0.875rem;
    }
</style>
