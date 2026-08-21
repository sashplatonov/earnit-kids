<script lang="ts">
    import { onMount } from 'svelte';
    import { useI18n } from '$lib/i18n/context';
    import {
        completeTelegramAccountLink,
        exchangeTelegramInitData,
        initializeTelegramWebApp,
    } from '$lib/services/telegram';
    import { acceptParentTelegramInvite } from '$lib/services/api';
    import TelegramRoleResolver from '$lib/components/telegram/TelegramRoleResolver.svelte';
    import TelegramActionButton from '$lib/components/telegram/TelegramActionButton.svelte';
    import TelegramCoin from '$lib/components/telegram/TelegramCoin.svelte';

    const i18n = useI18n();

    export let data: { publicOrigin?: string };

    type State = 'loading' | 'ready' | 'retry' | 'unavailable' | 'unlinked' | 'non-telegram' | 'parent-invite';
    let state: State = 'loading';
    let message = '';
    let verifiedRole = '';
    let parentInviteToken = '';
    let inviteBusy = false;

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

    // EXPLAIN: Pairing tokens are hex strings (SecureTokenGenerator). The public
    // EXPLAIN: site deep link passes a short command (e.g. "home") as startapp,
    // EXPLAIN: which is not a pairing token — skip the pairing attempt for
    // EXPLAIN: non-hex values so the user still lands in the Mini App and logs
    // EXPLAIN: in normally.
    function isHexToken(value: string): boolean {
        return /^[0-9a-fA-F]+$/.test(value);
    }

    async function authenticate(): Promise<void> {
        const telegram = initializeTelegramWebApp();
        if (!telegram) {
            state = 'non-telegram';
            return;
        }
        if (!telegram.initData) {
            state = 'retry';
            message = $i18n.t('app.telegram.entry.openFromMenu');
            return;
        }
        state = 'loading';
        try {
            const rawStartParam = telegram.initDataUnsafe?.start_param
                ?? new URLSearchParams(window.location.search).get('tgWebAppStartParam');
            const parentInvite = rawStartParam?.startsWith('pi_') ? rawStartParam : '';
            if (parentInvite) {
                parentInviteToken = parentInvite;
                state = 'parent-invite';
                return;
            }
            const pairingToken = rawStartParam && isHexToken(rawStartParam) ? rawStartParam : '';
            let pairingFailed = false;
            const childInviteToken = rawStartParam?.startsWith('ci_') ? rawStartParam : '';
            if (pairingToken && !childInviteToken) {
                const pairing = await completeTelegramAccountLink(pairingToken, telegram.initData);
                pairingFailed = !pairing.ok;
            }
            const response = await exchangeTelegramInitData(telegram.initData, childInviteToken || null);
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
                ? $i18n.t('app.telegram.entry.unavailable')
                : pairingFailed
                    ? $i18n.t('app.telegram.entry.linkingError')
                    : $i18n.t('app.telegram.entry.verifyError');
        } catch {
            state = 'retry';
            message = $i18n.t('app.telegram.entry.networkError');
        }
    }

    async function submitParentInvite(): Promise<void> {
        if (inviteBusy) return;
        inviteBusy = true;
        message = '';
        const telegram = initializeTelegramWebApp();
        const initData = telegram?.initData ?? '';
        const result = await acceptParentTelegramInvite(parentInviteToken, initData);
        inviteBusy = false;
        if (result.ok) {
            parentInviteToken = '';
            state = 'loading';
            await authenticate();
        } else {
            state = 'parent-invite';
            message = result.error || $i18n.t('app.telegram.parents.error');
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
    <TelegramRoleResolver role={verifiedRole} publicOrigin={data.publicOrigin ?? ''} />
{:else}
    <main class="telegram-page" aria-live="polite">
        <div class="telegram-card">
            <div class="telegram-mark" aria-hidden="true"><TelegramCoin size={44} /></div>
            <h1>EarnIt Kids</h1>
            {#if state === 'loading'}
                <p>{$i18n.t('app.telegram.entry.checkingSession')}</p>
            {:else if state === 'non-telegram'}
                <p>{$i18n.t('app.telegram.entry.openInsideTelegram')}</p>
            {:else if state === 'unavailable'}
                <p>{message}</p>
            {:else if state === 'unlinked'}
                <p>{$i18n.t('app.telegram.entry.unlinked')}</p>
                <a class="telegram-action" href="/login">{$i18n.t('app.telegram.entry.signInLink')}</a>
                <p class="telegram-hint">{$i18n.t('app.telegram.entry.childHint')}</p>
            {:else if state === 'parent-invite'}
                <p>{$i18n.t('app.telegram.entry.parentInviteHint')}</p>
                <button class="telegram-action" type="button" disabled={inviteBusy} on:click={() => void submitParentInvite()}>{$i18n.t('app.telegram.entry.acceptInvite')}</button>
                {#if message}<p class="telegram-hint" role="alert">{message}</p>{/if}
            {:else}
                <p>{message || $i18n.t('app.telegram.entry.resolveError')}</p>
                <TelegramActionButton icon="refresh" label={$i18n.t('app.telegram.entry.tryAgain')} on:click={() => void authenticate()} />
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

    .telegram-action:focus-visible {
        outline: 3px solid #80aaff;
        outline-offset: 2px;
    }

    .telegram-hint {
        margin-top: 1rem;
        font-size: 0.875rem;
    }
</style>
