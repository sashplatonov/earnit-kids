<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import {
        addParentMembership,
        deactivateParentMembership,
        loadParentMemberships,
        reactivateParentMembership,
        startTelegramAccountLink,
        transferParentAdmin,
    } from '$lib/services/api';
    import type { ParentMembership } from '$lib/types/auth';
    import { confirmAction } from '$lib/services/confirm';
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
    let actionBusy = false;

    // EXPLAIN: In the Telegram flow $appStore.permission is not populated (the
    // EXPLAIN: /api/data response carries isAdmin only), so also trust isAdmin
    // EXPLAIN: so parent management buttons (deactivate/reactivate, transfer,
    // EXPLAIN: invite) stay visible for family admins.
    $: isAdmin = $appStore.isAdmin || $appStore.permission === 'family_admin';

    $: if (open) void reload();

    async function reload() {
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
            case 'family_admin': return $i18n.t('app.telegram.roles.permissionFamilyAdmin');
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

    async function toggleActive(parent: ParentMembership) {
        if (actionBusy) return;
        const deactivating = parent.status !== 'inactive';
        const confirmed = await confirmAction({
            title: deactivating
                ? $i18n.t('app.telegram.parents.deactivateTitle')
                : $i18n.t('app.telegram.parents.reactivateTitle'),
            description: deactivating
                ? $i18n.t('app.telegram.parents.deactivateDescription', { email: parent.email })
                : $i18n.t('app.telegram.parents.reactivateDescription', { email: parent.email }),
            confirmLabel: deactivating
                ? $i18n.t('app.telegram.parents.deactivate')
                : $i18n.t('app.telegram.parents.reactivate'),
            cancelLabel: $i18n.t('app.telegram.tasks.cancel'),
            tone: deactivating ? 'danger' : 'neutral',
        });
        if (!confirmed) return;
        actionBusy = true;
        error = '';
        const result = deactivating
            ? await deactivateParentMembership(parent.id)
            : await reactivateParentMembership(parent.id);
        actionBusy = false;
        if (result.ok) {
            await reload();
        } else {
            error = result.error || $i18n.t('app.telegram.parents.error');
        }
    }

    async function transferAdmin(parent: ParentMembership) {
        if (actionBusy) return;
        const confirmed = await confirmAction({
            title: $i18n.t('app.telegram.parents.transferTitle'),
            description: $i18n.t('app.telegram.parents.transferDescription', { email: parent.email }),
            confirmLabel: $i18n.t('app.telegram.parents.transfer'),
            cancelLabel: $i18n.t('app.telegram.tasks.cancel'),
            tone: 'danger',
        });
        if (!confirmed) return;
        actionBusy = true;
        error = '';
        const result = await transferParentAdmin(parent.id);
        actionBusy = false;
        if (result.ok) {
            await reload();
        } else {
            error = result.error || $i18n.t('app.telegram.parents.error');
        }
    }
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="parent-access-title" tabindex="-1">
        <h2 id="parent-access-title">{$i18n.t('app.telegram.parents.title')}</h2>

        {#if view === 'list'}
            {#if loading}
                <p class="muted">{$i18n.t('app.telegram.shell.loading')}</p>
            {:else}
                <h3 class="sheet-subtitle">{$i18n.t('app.telegram.roles.parents')}</h3>
                <div class="flat">
                    {#if !parents.length}
                        <p class="muted">{$i18n.t('app.telegram.family.noChildren')}</p>
                    {:else}
                        {#each parents as parent (parent.id)}
                            <div class="row" class:inactive={parent.status === 'inactive'}>
                                <span class="setting-icon"><TelegramIcon name="users" size={18} label={$i18n.t('app.telegram.roles.parents')} /></span>
                                <span class="grow">
                                    <span class="setting-title">{parent.email}</span>
                                    <span class="chip" class:chip-admin={parent.permission === 'family_admin'} class:chip-pending={parent.status === 'pending'}>{permissionLabel(parent)}</span>
                                </span>
                                {#if isAdmin && parent.permission !== 'family_admin'}
                                    <div class="actions-row">
                                        {#if parent.status !== 'inactive'}
                                            <button class="row-action row-action--danger" type="button" disabled={actionBusy} on:click={() => toggleActive(parent)}>
                                                <TelegramIcon name="pause" size={14} label={$i18n.t('app.telegram.parents.deactivate')} />
                                            </button>
                                            <button class="row-action row-action--transfer" type="button" disabled={actionBusy} on:click={() => transferAdmin(parent)}>
                                                <TelegramIcon name="shield" size={14} label={$i18n.t('app.telegram.parents.transfer')} />
                                            </button>
                                        {:else}
                                            <button class="row-action row-action--reactivate" type="button" disabled={actionBusy} on:click={() => toggleActive(parent)}>
                                                <TelegramIcon name="play" size={14} label={$i18n.t('app.telegram.parents.reactivate')} />
                                            </button>
                                        {/if}
                                    </div>
                                {/if}
                            </div>
                        {/each}
                    {/if}
                </div>

                {#if isAdmin}
                    <button class="primary" type="button" on:click={() => { view = 'choose'; error = ''; }}>{$i18n.t('app.telegram.parents.addParent')}</button>
                {/if}
            {/if}
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
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); max-height:82dvh; overflow:auto; }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .sheet-subtitle { margin:1rem 0 .4rem; color:#4d5870; font-size:.85rem; }
    .flat { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .row { display:flex; align-items:center; gap:.6rem; min-height:3.5rem; padding:.35rem 0; border-bottom:1px solid #edf0f5; }
    .row:last-child { border-bottom:0; }
    .row.inactive { opacity:.6; }
    .grow { flex:1; min-width:0; }
    .setting-icon { display:grid; place-items:center; width:2.1rem; height:2.1rem; flex:0 0 auto; border-radius:.6rem; background:#eef0ff; color:#5b63e9; }
    .setting-title { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-weight:600; }
    .chip { display:inline-flex; align-items:center; gap:.25rem; padding:.15rem .45rem; border-radius:999px; font-size:.72rem; font-weight:700; margin-top:.2rem; background:#f1f3f7; color:#66718a; }
    .chip-admin { background:#eaf7ef; color:#17884b; }
    .chip-pending { background:#fff4e6; color:#b66d21; }
    .actions-row { display:flex; align-items:center; gap:.35rem; flex:0 0 auto; }
    .row-action { width:2.25rem; height:2.25rem; flex:0 0 auto; display:grid; place-items:center; border:1px solid #dfe4ee; border-radius:.5rem; background:#fff; color:#33415f; cursor:pointer; }
    .row-action:disabled { opacity:.5; cursor:not-allowed; }
    .row-action--danger { border-color:#fdd; background:#fff5f5; color:#c63c42; }
    .row-action--transfer { border-color:#c4c8ff; background:#f7f7ff; color:#3867d6; }
    .row-action--reactivate { border-color:#d4edda; background:#f0fff4; color:#17884b; }
    .choice { display:grid; grid-template-columns:minmax(0,1fr); gap:.6rem; }
    .choice button { display:flex; align-items:center; gap:.6rem; width:100%; min-height:3rem; padding:.5rem .7rem; border:1px solid #dfe4ee; border-radius:.75rem; background:#fff; color:#33415f; font:inherit; font-weight:700; cursor:pointer; }
    label { display:block; margin:.6rem 0 .3rem; color:#33415f; font-weight:600; font-size:.85rem; }
    .input { box-sizing:border-box; width:100%; min-height:2.75rem; padding:.6rem .7rem; border:1px solid #cfd6e4; border-radius:.7rem; font:inherit; }
    .confirm-meta { margin:.25rem 0 .9rem; color:#66718a; font-size:.9rem; line-height:1.45; }
    .muted { color:#66718a; }
    .error { color:#a33b3b; }
    .primary { width:100%; min-height:2.75rem; margin-top:.9rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:700; cursor:pointer; }
    .close { width:100%; min-height:2.75rem; margin-top:.5rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
