<script lang="ts">
    import { onMount, tick } from 'svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import {
        getTelegramAccountConnection,
        startTelegramAccountLink,
        unlinkTelegramAccount,
        type TelegramAccountConnection,
    } from '$lib/services/api';

    const i18n = useI18n();
    let connection: TelegramAccountConnection | null = null;
    let loading = true;
    let working = false;
    let error = '';
    let unlinkDialog: HTMLDialogElement | null = null;

    function tSettings(key: string): string {
        return $i18n.t(`admin.settings.${key}` as MessageKey);
    }

    async function refresh() {
        const loaded = await getTelegramAccountConnection();
        if (loaded) {
            connection = loaded;
            error = '';
        } else {
            error = tSettings('accountConnectionsLoadError');
        }
        loading = false;
    }

    async function startLink() {
        const popup = window.open('', '_blank');
        if (popup) popup.opener = null;
        working = true;
        error = '';
        const result = await startTelegramAccountLink();
        if (!result.ok || !result.data?.launchUrl) {
            popup?.close();
            error = result.ok || result.status === 404
                ? tSettings('accountConnectionsUnavailable')
                : result.error;
        } else if (popup) {
            popup.location.replace(result.data.launchUrl);
        } else {
            error = tSettings('accountConnectionsPopupBlocked');
        }
        working = false;
    }

    function openMiniApp() {
        if (!connection?.miniAppUrl) {
            error = tSettings('accountConnectionsUnavailable');
            return;
        }
        const popup = window.open(connection.miniAppUrl, '_blank');
        if (popup) popup.opener = null;
        else error = tSettings('accountConnectionsPopupBlocked');
    }

    async function requestUnlink() {
        await tick();
        unlinkDialog?.showModal();
    }

    function closeUnlink() {
        unlinkDialog?.close();
    }

    async function confirmUnlink() {
        closeUnlink();
        working = true;
        error = '';
        const result = await unlinkTelegramAccount();
        if (!result.ok) error = result.error;
        await refresh();
        working = false;
    }

    onMount(() => {
        void refresh();
        const refreshAfterReturningToSettings = () => {
            if (!working && document.visibilityState === 'visible') {
                void refresh();
            }
        };
        window.addEventListener('focus', refreshAfterReturningToSettings);
        document.addEventListener('visibilitychange', refreshAfterReturningToSettings);
        return () => {
            window.removeEventListener('focus', refreshAfterReturningToSettings);
            document.removeEventListener('visibilitychange', refreshAfterReturningToSettings);
        };
    });
</script>

<section class="account-connections" aria-labelledby="account-connections-title" aria-busy={loading || working}>
    <div class="account-connections__heading">
        <div>
            <p class="account-connections__eyebrow">{$i18n.t('app.shell.profile')}</p>
            <h3 id="account-connections-title">{tSettings('accountConnectionsTitle')}</h3>
        </div>
        <span class="gamified-icon icon-link account-connections__icon" aria-hidden="true"></span>
    </div>
    <p class="account-connections__hint">{tSettings('accountConnectionsHint')}</p>

    {#if error}
        <div class="account-connections__error" role="alert">
            <span>{error}</span>
            <button class="btn btn--secondary btn--small" type="button" disabled={working} on:click={() => void refresh()}>{tSettings('accountConnectionsRetry')}</button>
        </div>
    {/if}

    {#if loading}
        <p class="account-connections__status" role="status">{tSettings('accountConnectionsLoading')}</p>
    {:else if connection}
        <article class="account-connections__row">
            <div class="account-connections__identity">
                <strong>{tSettings('accountConnectionsEmail')}</strong>
                <span>{connection.email}</span>
            </div>
            <div class="account-connections__actions">
                <span class="account-connections__chip account-connections__chip--connected">{tSettings('accountConnectionsConnected')}</span>
                <span class="account-connections__chip account-connections__chip--required" title={tSettings('accountConnectionsEmailRequired')}>{tSettings('accountConnectionsRequired')}</span>
            </div>
        </article>

        <article class="account-connections__row">
            <div class="account-connections__identity">
                <strong>Telegram</strong>
                <span>{connection.telegramConnected ? tSettings('accountConnectionsTelegramLinked') : tSettings('accountConnectionsNotConnected')}</span>
            </div>
            <div class="account-connections__actions">
                {#if connection.telegramConnected}
                    <span class="account-connections__chip account-connections__chip--connected">{tSettings('accountConnectionsConnected')}</span>
                    {#if connection.miniAppUrl}
                        <button class="account-connections__text-button" type="button" on:click={openMiniApp}>{tSettings('accountConnectionsOpenMiniApp')}</button>
                    {/if}
                    <button class="btn btn--danger btn--small" type="button" disabled={working} on:click={() => void requestUnlink()}>{tSettings('accountConnectionsUnlink')}</button>
                {:else}
                    <button class="btn btn--primary btn--small" type="button" disabled={working} on:click={() => void startLink()}>{working ? tSettings('accountConnectionsOpening') : tSettings('accountConnectionsLinkTelegram')}</button>
                {/if}
            </div>
        </article>
    {/if}

    <dialog bind:this={unlinkDialog} class="account-connections__dialog" aria-labelledby="telegram-unlink-title" aria-describedby="telegram-unlink-description">
        <h4 id="telegram-unlink-title">{tSettings('accountConnectionsUnlinkTitle')}</h4>
        <p id="telegram-unlink-description">{tSettings('accountConnectionsUnlinkText')}</p>
        <div class="account-connections__dialog-actions">
            <button class="btn btn--secondary btn--small" type="button" on:click={closeUnlink}>{tSettings('accountConnectionsCancel')}</button>
            <button class="btn btn--danger btn--small" type="button" disabled={working} on:click={() => void confirmUnlink()}>{tSettings('accountConnectionsUnlink')}</button>
        </div>
    </dialog>
</section>

<style>
    .account-connections { display: grid; gap: .85rem; }
    .account-connections__heading { display: flex; align-items: center; justify-content: space-between; gap: .75rem; }
    .account-connections__heading h3 { margin: .2rem 0 0; }
    .account-connections__eyebrow { margin: 0; color: var(--text-muted, #66718a); font-size: .72rem; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
    .account-connections__icon { font-size: 1.5rem; }
    .account-connections__hint, .account-connections__status { margin: 0; color: var(--text-muted, #66718a); line-height: 1.5; }
    .account-connections__row { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: .9rem; border: 1px solid var(--card-border, rgba(0, 0, 0, .1)); border-radius: .9rem; background: rgb(255 255 255 / .35); }
    .account-connections__identity { display: grid; min-width: 0; gap: .25rem; }
    .account-connections__identity span { overflow-wrap: anywhere; color: var(--text-muted, #66718a); font-size: .9rem; }
    .account-connections__actions, .account-connections__dialog-actions { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: .5rem; }
    .account-connections__chip { display: inline-flex; min-height: 1.75rem; align-items: center; border-radius: 999px; padding: 0 .6rem; font-size: .76rem; font-weight: 700; white-space: nowrap; }
    .account-connections__chip--connected { color: #166534; background: #dcfce7; }
    .account-connections__chip--required { color: #92400e; background: #fef3c7; }
    .account-connections__text-button { border: 0; padding: .25rem; color: var(--accent, #2d6cdf); background: transparent; font: inherit; font-size: .86rem; font-weight: 700; cursor: pointer; }
    .account-connections__text-button:hover { text-decoration: underline; }
    .account-connections__error { display: flex; align-items: center; justify-content: space-between; gap: .75rem; color: #a33b3b; }
    .account-connections__dialog { max-width: min(30rem, calc(100vw - 2rem)); border: 1px solid var(--card-border, rgba(0, 0, 0, .12)); border-radius: 1rem; padding: 1.25rem; color: var(--text-primary, #18243d); }
    .account-connections__dialog::backdrop { background: rgb(15 23 42 / .45); }
    .account-connections__dialog h4, .account-connections__dialog p { margin: 0; }
    .account-connections__dialog p { margin-top: .6rem; color: var(--text-muted, #66718a); line-height: 1.5; }
    .account-connections__dialog-actions { margin-top: 1rem; }
    @media (max-width: 560px) { .account-connections__row { align-items: stretch; flex-direction: column; } .account-connections__actions { justify-content: stretch; } .account-connections__actions .btn { flex: 1 1 auto; } .account-connections__error { align-items: stretch; flex-direction: column; } }
</style>
