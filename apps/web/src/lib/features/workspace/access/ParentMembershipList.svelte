
<script lang="ts">
    import TelegramIcon from '$lib/components/telegram/TelegramIcon.svelte';
    import type { MembershipPermission, ParentMembership } from '$lib/types/auth';
    import { useI18n } from '$lib/i18n/context';

    export let parents: ParentMembership[];
    export let busy = false;
    export let hasAdmin = false;
    export let label: (parent: ParentMembership) => string;
    export let permissionLabel: (permission: MembershipPermission) => string;
    export let statusLabel: (status: string) => string;
    export let shouldShowEmail: (parent: ParentMembership) => boolean;
    export let shouldShowTelegram: (parent: ParentMembership) => boolean;
    export let telegramLabel: (parent: ParentMembership) => string;
    export let onAction: (action: string, parent: ParentMembership) => void;
    export let onTransfer: (parent: ParentMembership) => void;
    export let onRoleEdit: (parent: ParentMembership) => void;
    const i18n = useI18n();
</script>

<div class="parents-list" role="list" aria-label={$i18n.t('app.workspaceAccess.memberships')}>
    {#each parents as parent (parent.id)}
        <div class="parent-row" role="listitem">
            <div class="avatar">{label(parent).charAt(0).toUpperCase()}</div>
            <div class="row-main">
                <div class="topline">
                    <strong class="name" title={label(parent)}>{label(parent)}</strong>
                    <span class:pending={parent.status === 'pending'} class:inactive={parent.status === 'inactive'} class:transfer={parent.transferRequestStatus === 'pending'} class="state">{parent.transferRequestStatus === 'pending' ? $i18n.t('app.telegram.parents.transferPending') : statusLabel(parent.status)}</span>
                </div>
                <div class="row-role">{permissionLabel(parent.permission)}</div>
                <div class="ids">
                    {#if shouldShowEmail(parent)}<span class="id email"><span class="icon"><TelegramIcon name="mail" size={14} /></span><span class="text">{parent.email}</span></span>{/if}
                    {#if shouldShowTelegram(parent)}<span class="id tg"><span class="icon"><TelegramIcon name="send" size={14} /></span><span class="text"><span class="meta-label">{$i18n.t('app.workspaceAccess.telegramLabel')}:</span> {telegramLabel(parent)}</span></span>{/if}
                </div>
            </div>
            <div class="row-actions">
                {#if parent.status === 'pending'}
                    <button type="button" class="icon-btn" disabled={busy} aria-label={$i18n.t('app.workspaceAccess.resend')} on:click={() => onAction('resend', parent)}><TelegramIcon name="send" size={19} /></button>
                    <button type="button" class="icon-btn danger" disabled={busy} aria-label={$i18n.t('app.workspaceAccess.revoke')} on:click={() => onAction('revoke', parent)}><TelegramIcon name="unlink" size={19} /></button>
                {:else if parent.permission === 'family_admin'}
                    {#if parent.transferRequestStatus === 'pending'}
                        <button type="button" class="icon-btn danger" disabled={busy} aria-label={$i18n.t('app.telegram.parents.cancelRequest')} on:click={() => onAction('cancel-transfer', parent)}><TelegramIcon name="unlink" size={19} /></button>
                    {:else}
                        <button type="button" class="icon-btn" aria-label={$i18n.t('app.telegram.parents.transferTitle')} on:click={() => onTransfer(parent)}><TelegramIcon name="refresh" size={19} /></button>
                    {/if}
                {:else if parent.status === 'active'}
                    <button type="button" class="icon-btn danger" disabled={busy} aria-label={$i18n.t('app.workspaceAccess.deactivateParent')} on:click={() => onAction('deactivate', parent)}><TelegramIcon name="unlink" size={19} /></button>
                    <button type="button" class="icon-btn" disabled={busy} aria-label={$i18n.t('app.telegram.parents.changeRole')} on:click={() => onRoleEdit(parent)}><TelegramIcon name="pencil" size={19} /></button>
                {:else if parent.status === 'inactive'}
                    <button type="button" class="icon-btn ok" disabled={busy} aria-label={$i18n.t('app.workspaceAccess.reactivateParent')} on:click={() => onAction('reactivate', parent)}><TelegramIcon name="play" size={19} /></button>
                {/if}
            </div>
        </div>
    {/each}
</div>
{#if hasAdmin}<p class="note"><slot name="admin-note" /></p>{/if}

<style>
    .parents-list { display: grid; gap: .5rem; }
    .parent-row { display: flex; align-items: center; gap: .65rem; min-width: 0; }
    .row-main { min-width: 0; flex: 1; }
    .topline, .ids, .row-actions { display: flex; align-items: center; gap: .4rem; }
    .topline { justify-content: space-between; }
    .topline .name { min-width: 0; flex: 1 1 auto; }
    .state { flex: 0 0 auto; white-space: nowrap; }
    .name, .text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .row-role, .ids { color: #66718a; font-size: .78rem; }
    .row-actions { flex-shrink: 0; }
    .icon-btn { min-width: 44px; min-height: 44px; }
    .avatar { width: 2.625rem; height: 2.625rem; border-radius: .75rem; background: #eef0ff; color: #5b63e9; display: grid; place-items: center; font-weight: 800; }
    .state { display: inline-flex; align-items: center; min-height: 1.7rem; padding: .2rem .5rem; border-radius: 99px; background: #e8f7ef; color: #187847; font-size: .72rem; font-weight: 700; }
    .state.pending { background: #fff1dc; color: #a96720; }
    .state.inactive { background: #eef1f5; color: #66718a; }
    .state.transfer { background: #eef3ff; color: #4d63b9; }
    .id { display: inline-flex; align-items: center; gap: .35rem; max-width: 100%; padding: .22rem .45rem; border: 1px solid #dfe4ef; border-radius: 99px; background: #fafbfe; color: #51607a; font-size: .72rem; }
    .id .icon { display: inline-flex; flex: 0 0 auto; }
    .id .text { min-width: 0; }
    .icon-btn { border-color: transparent; background: #f1f3f7; color: #42506e; transition: background-color .16s ease, opacity .16s ease; }
    .icon-btn:hover:not(:disabled) { background: #e8ecf4; }
    .icon-btn:active:not(:disabled) { opacity: .78; }
    .icon-btn:focus-visible { outline: 3px solid #80aaff; outline-offset: 2px; }
    .icon-btn.danger { border-color: #f1d6d8; background: #fff5f5; color: #b74d4d; }
    .icon-btn.ok { border-color: #b9e1c8; background: #f7fff9; color: #17884b; }
    .note { margin: 0; color: #66718a; font-size: .8rem; }
</style>
