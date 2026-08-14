<script lang="ts">
    import { onMount } from 'svelte';
    import { useI18n } from '$lib/i18n/context';
    import { getAccountConnection, type AccountConnection } from '$lib/services/api';
    import TelegramIcon from './TelegramIcon.svelte';

    export let open = false;
    export let onClose: () => void = () => {};
    export let onOpenEmail: () => void = () => {};

    const i18n = useI18n();

    let account: AccountConnection | null = null;
    let loading = false;
    let error = '';

    onMount(() => {
        void reload();
    });

    async function reload() {
        if (!open) return;
        loading = true;
        account = await getAccountConnection();
        if (!account) error = $i18n.t('app.telegram.emailSettings.error');
        loading = false;
    }

    function maskEmail(email: string): string {
        const at = email.indexOf('@');
        if (at <= 1) return email;
        return `${email[0]}***${email.slice(at)}`;
    }
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="my-account-title" tabindex="-1">
        <h2 id="my-account-title">{$i18n.t('app.telegram.myAccount.title')}</h2>

        <h3 class="sheet-subtitle">{$i18n.t('app.telegram.myAccount.linkedAccounts')}</h3>
        <div class="flat">
            <div class="row"><span class="setting-icon"><TelegramIcon name="send" size={18} label={$i18n.t('app.telegram.myAccount.telegram')} /></span><span class="grow"><span class="setting-title">{$i18n.t('app.telegram.myAccount.telegram')}</span></span><span class:badge-active={account?.telegramLinked} class="manage-badge">{account?.telegramLinked ? $i18n.t('app.telegram.myAccount.linked') : $i18n.t('app.telegram.myAccount.notLinked')}</span></div>
            <button class="row" type="button" on:click={onOpenEmail}><span class="setting-icon"><TelegramIcon name="mail" size={18} label={$i18n.t('app.telegram.myAccount.email')} /></span><span class="grow"><span class="setting-title">{$i18n.t('app.telegram.myAccount.email')}</span><span class="setting-meta">{account ? $i18n.t('app.telegram.myAccount.emailMeta', { email: maskEmail(account.email), status: $i18n.t('app.telegram.myAccount.linked') }) : ''}</span></span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('app.telegram.myAccount.openEmail')} /></button>
        </div>

        <p class="hint">{$i18n.t('app.telegram.myAccount.hint')}</p>
        {#if error}<p class="error" role="alert">{error}</p>{/if}
        <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.header.close')}</button>
    </div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .sheet-subtitle { margin:1rem 0 .4rem; color:#4d5870; font-size:.85rem; }
    .flat { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .row { display:flex; align-items:center; gap:.6rem; width:100%; min-height:3rem; padding:.35rem 0; border:0; border-bottom:1px solid #edf0f5; background:transparent; color:#33415f; font:inherit; text-align:left; }
    .row:last-child { border-bottom:0; }
    button.row { cursor:pointer; }
    .grow { flex:1; min-width:0; }
    .setting-icon { display:grid; place-items:center; width:2.1rem; height:2.1rem; flex:0 0 auto; border-radius:.6rem; background:#eef0ff; color:#5b63e9; }
    .setting-title { display:block; font-weight:600; }
    .setting-meta { display:block; margin-top:.1rem; color:#66718a; font-size:.78rem; }
    .manage-badge { padding:.2rem .55rem; border-radius:999px; background:#f1f3f7; color:#66718a; font-size:.78rem; font-weight:700; white-space:nowrap; }
    .badge-active { background:#eaf7ef; color:#17884b; }
    .hint { margin:.7rem 0 0; color:#8a93a8; font-size:.8rem; line-height:1.4; }
    .error { color:#a33b3b; }
    .close { width:100%; min-height:2.75rem; margin-top:.6rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
</style>
