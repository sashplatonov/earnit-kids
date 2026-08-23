<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import {
        changeAccountEmail,
        changePassword,
        getAccountConnection,
        unlinkAccountEmail,
        type AccountConnection,
    } from '$lib/services/api';
    import TelegramIcon from './TelegramIcon.svelte';

    export let open = false;
    export let onClose: () => void = () => {};

    const i18n = useI18n();

    let account: AccountConnection | null = null;
    let error = '';
    let busy = false;
    let view: 'main' | 'change' | 'password' = 'main';
    let newEmail = '';
    let currentPassword = '';
    let newPassword = '';
    let confirmUnlink = false;

    $: if (open) void reload();

    async function reload() {
        account = await getAccountConnection();
        if (!account) error = $i18n.t('app.telegram.emailSettings.error');
    }

    function maskEmail(email: string): string {
        const at = email.indexOf('@');
        if (at <= 1) return email;
        return `${email[0]}***${email.slice(at)}`;
    }

    async function submitChange() {
        if (!newEmail.trim()) { error = $i18n.t('app.telegram.emailSettings.error'); return; }
        busy = true; error = '';
        const result = await changeAccountEmail(newEmail.trim());
        busy = false;
        if (result.ok) {
            view = 'main';
            newEmail = '';
            await reload();
        } else {
            error = result.error || $i18n.t('app.telegram.emailSettings.error');
        }
    }

    async function submitPassword() {
        if (!currentPassword || !newPassword) { error = $i18n.t('app.telegram.emailSettings.error'); return; }
        busy = true; error = '';
        const result = await changePassword(currentPassword, newPassword);
        busy = false;
        if (result.ok) {
            view = 'main';
            currentPassword = '';
            newPassword = '';
        } else {
            error = result.error || $i18n.t('app.telegram.emailSettings.error');
        }
    }

    async function submitUnlink() {
        busy = true; error = '';
        const result = await unlinkAccountEmail();
        busy = false;
        if (result.ok) {
            confirmUnlink = false;
            await reload();
        } else {
            error = result.error || $i18n.t('app.telegram.emailSettings.error');
        }
    }
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="email-settings-title" tabindex="-1">
        <h2 id="email-settings-title">{$i18n.t('app.telegram.emailSettings.title')}</h2>

        {#if view === 'main'}
            {#if account}
                <div class="current-email"><TelegramIcon name="mail" size={18} label={$i18n.t('app.telegram.emailSettings.title')} /><span class="grow"><span class="setting-title">{maskEmail(account.email)}</span><span class="setting-meta">{$i18n.t('app.telegram.emailSettings.linked')}</span></span></div>
            {/if}
            <div class="flat">
                <button class="row" type="button" on:click={() => { view = 'change'; error = ''; }}><span class="setting-icon"><TelegramIcon name="edit" size={18} label={$i18n.t('app.telegram.emailSettings.change')} /></span><span class="grow">{$i18n.t('app.telegram.emailSettings.change')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
                <button class="row" type="button" on:click={() => { confirmUnlink = true; error = ''; }}><span class="setting-icon"><TelegramIcon name="unlink" size={18} label={$i18n.t('app.telegram.emailSettings.unlink')} /></span><span class="grow">{$i18n.t('app.telegram.emailSettings.unlink')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
                <button class="row" type="button" on:click={() => { view = 'password'; error = ''; }}><span class="setting-icon"><TelegramIcon name="key" size={18} label={$i18n.t('app.telegram.emailSettings.changePassword')} /></span><span class="grow">{$i18n.t('app.telegram.emailSettings.changePassword')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
            </div>

            {#if confirmUnlink}
                <div class="confirm">
                    <p class="confirm-title">{$i18n.t('app.telegram.emailSettings.unlinkConfirm')}</p>
                    <p class="confirm-meta">{$i18n.t('app.telegram.emailSettings.unlinkDescription')}</p>
                    <button class="deactivate" type="button" disabled={busy} on:click={submitUnlink}>{$i18n.t('app.telegram.emailSettings.unlinkConfirmLabel')}</button>
                    <button class="close" type="button" disabled={busy} on:click={() => confirmUnlink = false}>{$i18n.t('app.telegram.emailSettings.cancel')}</button>
                </div>
            {/if}
        {:else if view === 'change'}
            <label for="email-new">{$i18n.t('app.telegram.emailSettings.newEmailLabel')}</label>
            <input id="email-new" class="input" type="email" bind:value={newEmail} placeholder="name@example.com" />
            <button class="primary" type="button" disabled={busy} on:click={submitChange}>{$i18n.t('app.telegram.emailSettings.continue')}</button>
            <button class="close" type="button" on:click={() => { view = 'main'; error = ''; }}>{$i18n.t('app.telegram.emailSettings.cancel')}</button>
        {:else}
            <label for="password-current">{$i18n.t('app.telegram.emailSettings.currentPasswordLabel')}</label>
            <input id="password-current" class="input" type="password" bind:value={currentPassword} />
            <label for="password-new">{$i18n.t('app.telegram.emailSettings.newPasswordLabel')}</label>
            <input id="password-new" class="input" type="password" bind:value={newPassword} />
            <button class="primary" type="button" disabled={busy} on:click={submitPassword}>{$i18n.t('app.telegram.emailSettings.changePassword')}</button>
            <button class="close" type="button" on:click={() => { view = 'main'; error = ''; }}>{$i18n.t('app.telegram.emailSettings.cancel')}</button>
        {/if}

        {#if error}<p class="error" role="alert">{error}</p>{/if}
        <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.header.close')}</button>
    </div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    @media (min-width: 700px) { .sheet { inset:50% auto auto 50%; width:min(34rem,calc(100% - 3rem)); max-height:min(82dvh,46rem); padding:1.4rem; border-radius:1.25rem; box-shadow:0 1.5rem 4rem rgb(27 39 73 / 22%); transform:translate(-50%,-50%); } }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .current-email { display:flex; align-items:center; gap:.6rem; padding:.6rem; border:1px solid #e6e9f0; border-radius:.8rem; background:#f8f9fc; color:#5b63e9; }
    .grow { flex:1; min-width:0; }
    .setting-title { display:block; color:#33415f; font-weight:600; }
    .setting-meta { display:block; margin-top:.1rem; color:#66718a; font-size:.78rem; }
    .flat { margin-top:.7rem; border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .row { display:flex; align-items:center; gap:.6rem; width:100%; min-height:3rem; padding:.35rem 0; border:0; border-bottom:1px solid #edf0f5; background:transparent; color:#33415f; font:inherit; text-align:left; cursor:pointer; }
    .row:last-child { border-bottom:0; }
    .setting-icon { display:grid; place-items:center; width:2.1rem; height:2.1rem; flex:0 0 auto; border-radius:.6rem; background:#eef0ff; color:#5b63e9; }
    label { display:block; margin:.6rem 0 .3rem; color:#33415f; font-weight:600; font-size:.85rem; }
    .input { box-sizing:border-box; width:100%; min-height:2.75rem; padding:.6rem .7rem; border:1px solid #cfd6e4; border-radius:.7rem; font:inherit; }
    .confirm { margin-top:.7rem; padding:.6rem; border:1px solid #f3cfd2; border-radius:.8rem; background:#fff0f1; }
    .confirm-title { margin:0; color:#c63c42; font-weight:700; }
    .confirm-meta { margin:.3rem 0 .6rem; color:#8a5a5e; font-size:.85rem; line-height:1.4; }
    .error { margin:.6rem 0 0; color:#a33b3b; }
    .primary { width:100%; min-height:2.75rem; margin-top:.9rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:700; cursor:pointer; }
    .deactivate { width:100%; min-height:2.75rem; border:0; border-radius:.7rem; background:#c63c42; color:#fff; font:inherit; font-weight:700; cursor:pointer; }
    .close { width:100%; min-height:2.75rem; margin-top:.5rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
</style>
