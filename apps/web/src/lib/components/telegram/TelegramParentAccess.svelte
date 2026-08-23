<script lang="ts">
    import { createParentTelegramInvite } from '$lib/services/api';
    import ParentAccessPanel from '$lib/features/workspace/family/ParentAccessPanel.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import { useI18n } from '$lib/i18n/context';

    export let open = false;
    export let onClose: () => void = () => {};
    const i18n = useI18n();
    let telegramInviteOpen = false;
    let parentName = '';
    let inviteLink = '';
    let error = '';
    let busy = false;
    let copied = false;

    async function createInvite(): Promise<void> {
        if (!parentName.trim()) { error = $i18n.t('app.telegram.parents.nameRequired'); return; }
        busy = true; error = '';
        const result = await createParentTelegramInvite(parentName.trim());
        busy = false;
        if (result?.launchUrl) inviteLink = result.launchUrl;
        else error = $i18n.t('app.telegram.parents.error');
    }

    async function copy(): Promise<void> {
        try { await navigator.clipboard.writeText(inviteLink); copied = true; } catch { copied = false; }
    }
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="parent-access-title" tabindex="-1">
        <h2 id="parent-access-title">{$i18n.t('app.telegram.parents.title')}</h2>
        <ParentAccessPanel />
        <section class="telegram-option" aria-labelledby="telegram-invite-title">
            <h3 id="telegram-invite-title">{$i18n.t('app.telegram.parents.byTelegram')}</h3>
            <p>{$i18n.t('app.telegram.parents.telegramHint')}</p>
            {#if !telegramInviteOpen}<button class="secondary" type="button" on:click={() => telegramInviteOpen = true}><TelegramIcon name="send" size={18} label={$i18n.t('app.telegram.parents.byTelegram')} />{$i18n.t('app.telegram.parents.createLink')}</button>
            {:else if !inviteLink}<label for="telegram-parent-name">{$i18n.t('app.telegram.parents.nameLabel')}</label><input id="telegram-parent-name" bind:value={parentName} autocomplete="name" /><button type="button" disabled={busy} on:click={createInvite}>{busy ? '…' : $i18n.t('app.telegram.parents.createLink')}</button>
            {:else}<p role="status">{$i18n.t('app.telegram.parents.linkReady')}</p><button type="button" on:click={copy}>{copied ? $i18n.t('app.telegram.parents.copied') : $i18n.t('app.telegram.parents.copyLink')}</button>{/if}
            {#if error}<p class="error" role="alert">{error}</p>{/if}
        </section>
        <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.header.close')}</button>
    </div>
{/if}

<style>
    .sheet-backdrop{position:fixed;inset:0;z-index:40;background:rgb(15 24 45 / 35%)}.sheet{position:fixed;inset:auto 0 0;z-index:41;display:grid;gap:1rem;padding:1rem max(1rem,env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom));border-radius:1.1rem 1.1rem 0 0;background:#fff;box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%);max-height:88dvh;overflow:auto}h2,h3{margin:0;color:#18243d}h2{font-size:1.15rem}h3{font-size:.95rem}.telegram-option{display:grid;gap:.5rem;padding-top:.5rem;border-top:1px solid #edf0f5}.telegram-option p{margin:0;color:#66718a;line-height:1.4}.telegram-option button,.close{display:flex;align-items:center;justify-content:center;gap:.5rem;min-height:2.75rem;padding:.5rem .7rem;border:0;border-radius:.7rem;background:#3867d6;color:#fff;font:inherit;font-weight:700}.secondary,.close{background:#fff!important;border:1px solid #dfe4ee!important;color:#33415f!important}label{font-size:.82rem;font-weight:700;color:#33415f}input{box-sizing:border-box;min-height:2.75rem;padding:.5rem;border:1px solid #cfd6e4;border-radius:.65rem;font:inherit}.error{color:#a33b3b}.close{width:100%}
    @media (min-width: 700px) {.sheet{inset:50% auto auto 50%;width:min(38rem,calc(100% - 3rem));max-height:min(82dvh,46rem);padding:1.4rem;border-radius:1.25rem;box-shadow:0 1.5rem 4rem rgb(27 39 73 / 22%);transform:translate(-50%,-50%)}}
</style>
