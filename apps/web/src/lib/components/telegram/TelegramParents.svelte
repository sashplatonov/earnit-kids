<script lang="ts">
    import { onMount } from 'svelte';
    import { useI18n } from '$lib/i18n/context';
    import {
        addParentMembership,
        loadParentMemberships,
        startTelegramAccountLink,
    } from '$lib/services/api';
    import type { ParentMembership } from '$lib/types/auth';
    import TelegramIcon from './TelegramIcon.svelte';

    export let open = false;
    export let onClose: () => void = () => {};

    const i18n = useI18n();

    let parents: ParentMembership[] = [];
    let loading = false;
    let error = '';
    let view: 'list' | 'choose' | 'email' | 'telegram' = 'list';
    let email = '';
    let busy = false;
    let inviteLink = '';
    let copied = false;

    onMount(() => {
        void reload();
    });

    async function reload() {
        if (!open) return;
        loading = true;
        error = '';
        const result = await loadParentMemberships();
        parents = result.ok ? result.data ?? [] : [];
        if (!result.ok) error = $i18n.t('app.telegram.parents.loadError');
        loading = false;
    }

    function permissionLabel(parent: ParentMembership): string {
        if (parent.status === 'pending') return $i18n.t('app.telegram.parents.pending');
        switch (parent.permission) {
            case 'family_admin': return $i18n.t('app.telegram.parents.owner');
            case 'editor': return $i18n.t('app.telegram.roles.permissionEditor');
            default: return $i18n.t('app.telegram.roles.permissionViewer');
        }
    }

    async function sendEmailInvite() {
        if (!email.trim()) { error = $i18n.t('app.telegram.parents.error'); return; }
        busy = true; error = '';
        const result = await addParentMembership({ email: email.trim(), permission: 'editor' });
        busy = false;
        if (result.ok) {
            email = '';
            view = 'list';
            await reload();
        } else {
            error = result.error || $i18n.t('app.telegram.parents.error');
        }
    }

    async function createTelegramLink() {
        busy = true; error = '';
        const result = await startTelegramAccountLink();
        busy = false;
        if (result.ok && result.data?.launchUrl) {
            inviteLink = result.data.launchUrl;
        } else {
            error = $i18n.t('app.telegram.parents.error');
        }
    }

    async function copyLink() {
        try {
            await navigator.clipboard.writeText(inviteLink);
            copied = true;
        } catch {
            copied = false;
        }
    }
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="parents-title" tabindex="-1">
        <h2 id="parents-title">{$i18n.t('app.telegram.parents.title')}</h2>

        {#if view === 'list'}
            {#if loading}
                <p class="muted">{$i18n.t('app.telegram.shell.loading')}</p>
            {:else}
                <div class="flat">
                    {#each parents as parent (parent.id)}
                        <div class="row"><span class="setting-icon"><TelegramIcon name="users" size={18} label={$i18n.t('app.telegram.parents.title')} /></span><span class="grow"><span class="setting-title">{parent.email}</span></span><span class:badge-active={parent.permission === 'family_admin'} class="manage-badge">{permissionLabel(parent)}</span></div>
                    {/each}
                </div>
            {/if}
            <button class="primary" type="button" on:click={() => { view = 'choose'; error = ''; }}>{$i18n.t('app.telegram.parents.addParent')}</button>
        {:else if view === 'choose'}
            <p class="confirm-meta">{$i18n.t('app.telegram.parents.howToInvite')}</p>
            <div class="choice">
                <button type="button" on:click={() => { view = 'email'; error = ''; }}><span class="setting-icon"><TelegramIcon name="mail" size={20} label={$i18n.t('app.telegram.parents.byEmail')} /></span><span>{$i18n.t('app.telegram.parents.byEmail')}</span></button>
                <button type="button" on:click={() => { view = 'telegram'; error = ''; }}><span class="setting-icon"><TelegramIcon name="send" size={20} label={$i18n.t('app.telegram.parents.byTelegram')} /></span><span>{$i18n.t('app.telegram.parents.byTelegram')}</span></button>
            </div>
            <button class="close" type="button" on:click={() => view = 'list'}>{$i18n.t('app.telegram.emailSettings.cancel')}</button>
        {:else if view === 'email'}
            <label for="parent-email">{$i18n.t('app.telegram.parents.emailLabel')}</label>
            <input id="parent-email" class="input" type="email" bind:value={email} placeholder="name@example.com" />
            <button class="primary" type="button" disabled={busy} on:click={sendEmailInvite}>{$i18n.t('app.telegram.parents.sendInvite')}</button>
            <button class="close" type="button" on:click={() => view = 'choose'}>{$i18n.t('app.telegram.emailSettings.cancel')}</button>
        {:else}
            <p class="confirm-meta">{$i18n.t('app.telegram.parents.telegramHint')}</p>
            {#if !inviteLink}
                <button class="primary" type="button" disabled={busy} on:click={createTelegramLink}>{$i18n.t('app.telegram.parents.createLink')}</button>
            {:else}
                <p class="confirm-meta"><strong>{$i18n.t('app.telegram.parents.linkReady')}</strong></p>
                <button class="primary" type="button" on:click={copyLink}>{copied ? $i18n.t('app.telegram.parents.copied') : $i18n.t('app.telegram.parents.copyLink')}</button>
                <button class="close" type="button" on:click={() => { inviteLink = ''; copied = false; }}>{$i18n.t('app.telegram.parents.createNew')}</button>
            {/if}
            <button class="close" type="button" on:click={() => view = 'choose'}>{$i18n.t('app.telegram.emailSettings.cancel')}</button>
        {/if}

        {#if error}<p class="error" role="alert">{error}</p>{/if}
        <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.header.close')}</button>
    </div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .flat { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .row { display:flex; align-items:center; gap:.6rem; min-height:3rem; padding:.35rem 0; border-bottom:1px solid #edf0f5; }
    .row:last-child { border-bottom:0; }
    .grow { flex:1; min-width:0; }
    .setting-icon { display:grid; place-items:center; width:2.1rem; height:2.1rem; flex:0 0 auto; border-radius:.6rem; background:#eef0ff; color:#5b63e9; }
    .setting-title { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-weight:600; }
    .manage-badge { padding:.2rem .55rem; border-radius:999px; background:#f1f3f7; color:#66718a; font-size:.78rem; font-weight:700; white-space:nowrap; }
    .badge-active { background:#eaf7ef; color:#17884b; }
    .choice { display:grid; gap:.6rem; }
    .choice button { display:flex; align-items:center; gap:.6rem; width:100%; min-height:3rem; padding:.5rem .7rem; border:1px solid #dfe4ee; border-radius:.75rem; background:#fff; color:#33415f; font:inherit; font-weight:700; cursor:pointer; }
    label { display:block; margin:.6rem 0 .3rem; color:#33415f; font-weight:600; font-size:.85rem; }
    .input { box-sizing:border-box; width:100%; min-height:2.75rem; padding:.6rem .7rem; border:1px solid #cfd6e4; border-radius:.7rem; font:inherit; }
    .confirm-meta { margin:.25rem 0 .9rem; color:#66718a; font-size:.9rem; line-height:1.45; }
    .muted { color:#66718a; }
    .error { color:#a33b3b; }
    .primary { width:100%; min-height:2.75rem; margin-top:.9rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:700; cursor:pointer; }
    .close { width:100%; min-height:2.75rem; margin-top:.5rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
</style>
