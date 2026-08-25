<script lang="ts">
    import { onMount } from 'svelte';
    import { updateI18n, useI18n } from '$lib/i18n/context';
    import { getI18nPayloadForPath } from '$lib/i18n';
    import { initializeTelegramWebApp } from '$lib/services/telegram';
    import { bootstrapTelegramWorkspace } from '$lib/features/telegram/TelegramWorkspaceBootstrap';
    import { acceptParentTelegramInvite } from '$lib/services/api';
    import TelegramRoleResolver from '$lib/components/telegram/TelegramRoleResolver.svelte';
    import TelegramActionButton from '$lib/components/telegram/TelegramActionButton.svelte';
    import LocaleSwitcher from '$lib/components/LocaleSwitcher.svelte';

    const i18n = useI18n();

    export let data: { publicOrigin?: string };

    type State = 'loading' | 'ready' | 'retry' | 'unavailable' | 'unlinked' | 'non-telegram' | 'parent-invite' | 'language-setup';
    let state: State = 'loading';
    let message = '';
    let verifiedRole = '';
    let parentInviteToken = '';
    let inviteBusy = false;

    // EXPLAIN: Pairing tokens are hex strings (SecureTokenGenerator). The public
    // EXPLAIN: site deep link passes a short command (e.g. "home") as startapp,
    // EXPLAIN: which is not a pairing token — skip the pairing attempt for
    // EXPLAIN: non-hex values so the user still lands in the Mini App and logs
    // EXPLAIN: in normally.
    async function authenticate(skipParentInvite = false): Promise<void> {
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
            if (parentInvite && !skipParentInvite) {
                parentInviteToken = parentInvite;
                state = 'parent-invite';
                return;
            }
            const result = await bootstrapTelegramWorkspace();
            if (result.state === 'ready') {
                state = 'ready';
                verifiedRole = result.role ?? '';
                if (result.locale) {
                    updateI18n(i18n, await getI18nPayloadForPath('/telegram', result.locale));
                }
                if (result.languageSetupRequired && result.role === 'admin') {
                    state = 'language-setup';
                }
                return;
            }
            state = result.state === 'unavailable'
                ? 'unavailable' : result.state === 'unlinked' ? 'unlinked' : result.state === 'non-telegram' ? 'non-telegram' : 'retry';
            message = result.state === 'unavailable'
                ? $i18n.t('app.telegram.entry.unavailable')
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
            await authenticate(true);
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
            <img class="telegram-mark" src="/img/public/app-icon.png" alt="" width="88" height="88" />
            <h1>EarnIt Kids</h1>
            {#if state === 'loading'}
                <p>{$i18n.t('app.telegram.entry.checkingSession')}</p>
            {:else if state === 'non-telegram'}
                <p>{$i18n.t('app.telegram.entry.openInsideTelegram')}</p>
            {:else if state === 'unavailable'}
                <p>{message}</p>
            {:else if state === 'unlinked'}
                <p>{$i18n.t('app.telegram.entry.unlinked')}</p>
                <a class="telegram-action" href="/public/index.html">{$i18n.t('app.telegram.entry.signInLink')}</a>
                <p class="telegram-hint">{$i18n.t('app.telegram.entry.childHint')}</p>
            {:else if state === 'parent-invite'}
                <p>{$i18n.t('app.telegram.entry.parentInviteHint')}</p>
                <button class="telegram-action" type="button" disabled={inviteBusy} on:click={() => void submitParentInvite()}>{$i18n.t('app.telegram.entry.acceptInvite')}</button>
                {#if message}<p class="telegram-hint" role="alert">{message}</p>{/if}
            {:else if state === 'language-setup'}
                <p>{$i18n.t('app.telegram.entry.checkingSession')}</p>
                <LocaleSwitcher familyManaged />
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
        width: 5.5rem;
        height: 5.5rem;
        margin: 0 auto 0.5rem;
        border-radius: 1.35rem;
        box-shadow: 0 0.65rem 1.25rem rgb(39 95 214 / 15%);
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
