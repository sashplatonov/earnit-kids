<script lang="ts">
    import {
        addParentMembership,
        loadParentMemberships,
        resendParentInvitation,
        revokeParentInvitation,
        deactivateParentMembership,
        reactivateParentMembership,
        type ApiActionResult,
    } from '$lib/services/api';
    import type { MembershipPermission, ParentMembership } from '$lib/types/auth';
    import { useI18n } from '$lib/i18n/context';
    import TelegramIcon from '$lib/components/telegram/TelegramIcon.svelte';

    export let showTelegramInvite = false;
    export let onTelegramInvite: () => void = () => {};

    let parents: ParentMembership[] = [];
    let email = '';
    let permission: MembershipPermission = 'editor';
    let loading = true;
    let busy = false;
    let error = '';
    let status = '';
    const i18n = useI18n();

    export async function reload(): Promise<void> {
        loading = true;
        error = '';
        const result = await loadParentMemberships();
        if (result.ok) parents = result.data ?? [];
        else error = result.error;
        loading = false;
    }

    void reload();

    function label(parent: ParentMembership): string {
        return parent.displayName?.trim() || parent.email?.trim() || parent.telegramDisplayName?.trim()
            || (parent.telegramUsername ? `@${parent.telegramUsername}` : $i18n.t('app.workspaceAccess.unknownParent'));
    }

    function permissionLabel(value: MembershipPermission): string {
        return value === 'family_admin' ? $i18n.t('app.workspaceAccess.familyAdmin')
            : value === 'editor' ? $i18n.t('app.workspaceAccess.editor') : $i18n.t('app.workspaceAccess.viewer');
    }

    function statusLabel(status: string): string {
        if (status === 'active') return $i18n.t('app.workspaceAccess.active');
        if (status === 'inactive') return $i18n.t('app.workspaceAccess.inactive');
        return $i18n.t('app.workspaceAccess.pending');
    }

    $: hasAdmin = parents.some((p) => p.status === 'active' && p.permission === 'family_admin');

    async function run(action: Promise<ApiActionResult<unknown>>, success: string): Promise<void> {
        busy = true;
        error = '';
        status = '';
        const result = await action;
        busy = false;
        if (!result.ok) { error = result.error; return; }
        status = success;
        await reload();
    }

    async function invite(): Promise<void> {
        if (!email.trim()) { error = $i18n.t('app.workspaceAccess.emailRequired'); return; }
        await run(addParentMembership({ email: email.trim(), permission }), $i18n.t('app.workspaceAccess.invitationSent'));
        if (!error) email = '';
    }
</script>

<section class="access-flow" aria-labelledby="parent-access-heading">
    <h2 id="parent-access-heading">{$i18n.t('app.workspaceAccess.title')}</h2>

    {#if loading}<p class="hint" aria-live="polite">{$i18n.t('app.workspaceAccess.loading')}</p>
    {:else if parents.length === 0}<p class="empty">{$i18n.t('app.workspaceAccess.empty')}</p>
    {:else}
        <div class="parents-list" role="list" aria-label={$i18n.t('app.workspaceAccess.memberships')}>
            {#each parents as parent (parent.id)}
                <div class="parent-row" role="listitem">
                    <div class="avatar">{label(parent).charAt(0).toUpperCase()}</div>
                    <div class="row-main">
                        <div class="topline">
                            <strong class="name">{label(parent)}</strong>
                            <span class:pending={parent.status === 'pending'} class:inactive={parent.status === 'inactive'} class="state">{statusLabel(parent.status)}</span>
                        </div>
                        <div class="row-role">{permissionLabel(parent.permission)}</div>
                        <div class="ids">
                            {#if parent.email}
                                <span class="id email"><span class="icon"><TelegramIcon name="mail" size={14} /></span><span class="text">{parent.email}</span></span>
                            {/if}
                            {#if parent.telegramUsername}
                                <span class="id tg"><span class="icon"><TelegramIcon name="send" size={14} /></span><span class="text">@{parent.telegramUsername}</span></span>
                            {/if}
                        </div>
                    </div>
                    <div class="row-actions">
                        {#if parent.status === 'pending'}
                            <button type="button" class="icon-btn" disabled={busy} aria-label={$i18n.t('app.workspaceAccess.resend')} on:click={() => run(resendParentInvitation(parent.id), $i18n.t('app.workspaceAccess.invitationResent'))}><TelegramIcon name="send" size={19} /><span class="tip">{$i18n.t('app.workspaceAccess.resend')}</span></button>
                            <button type="button" class="icon-btn danger" disabled={busy} aria-label={$i18n.t('app.workspaceAccess.revoke')} on:click={() => run(revokeParentInvitation(parent.id), $i18n.t('app.workspaceAccess.invitationRevoked'))}><TelegramIcon name="unlink" size={19} /><span class="tip">{$i18n.t('app.workspaceAccess.revoke')}</span></button>
                        {:else if parent.permission === 'family_admin'}
                            <button type="button" class="icon-btn disabled" aria-label={$i18n.t('app.telegram.parents.adminDeactivateTip')} disabled><TelegramIcon name="pause" size={19} /><span class="tip">{$i18n.t('app.telegram.parents.adminDeactivateTip')}</span></button>
                            <button type="button" class="icon-btn" aria-label={$i18n.t('app.telegram.parents.transferTitle')}><TelegramIcon name="refresh" size={19} /><span class="tip">{$i18n.t('app.telegram.parents.transferTitle')}</span></button>
                        {:else if parent.status === 'active'}
                            <button type="button" class="icon-btn danger" disabled={busy} aria-label={$i18n.t('app.workspaceAccess.deactivateParent')} on:click={() => run(deactivateParentMembership(parent.id), $i18n.t('app.workspaceAccess.parentDeactivated'))}><TelegramIcon name="unlink" size={19} /><span class="tip">{$i18n.t('app.workspaceAccess.deactivateParent')}</span></button>
                            <button type="button" class="icon-btn" aria-label={$i18n.t('app.telegram.parents.changeRole')}><TelegramIcon name="pencil" size={19} /><span class="tip">{$i18n.t('app.telegram.parents.changeRole')}</span></button>
                        {:else if parent.status === 'inactive'}
                            <button type="button" class="icon-btn ok" disabled={busy} aria-label={$i18n.t('app.workspaceAccess.reactivateParent')} on:click={() => run(reactivateParentMembership(parent.id), $i18n.t('app.workspaceAccess.parentReactivated'))}><TelegramIcon name="play" size={19} /><span class="tip">{$i18n.t('app.workspaceAccess.reactivateParent')}</span></button>
                        {/if}
                    </div>
                </div>
            {/each}
        </div>
        {#if hasAdmin}<p class="note">{$i18n.t('app.telegram.parents.adminProtectionNote')}</p>{/if}
    {/if}

    <div class="invite-form" aria-label="Invite a parent">
        <label for="workspace-parent-email">{$i18n.t('app.workspaceAccess.emailLabel')}</label>
        <div class="invite-controls">
            <input id="workspace-parent-email" type="email" autocomplete="email" bind:value={email} placeholder={$i18n.t('app.workspaceAccess.emailPlaceholder')} disabled={busy} />
            <select bind:value={permission} aria-label={$i18n.t('app.workspaceAccess.permission')} disabled={busy}>
                <option value="editor">{$i18n.t('app.workspaceAccess.editor')}</option><option value="viewer">{$i18n.t('app.workspaceAccess.viewer')}</option>
            </select>
            <button type="button" disabled={busy} on:click={invite}><TelegramIcon name="mail" size={18} />{busy ? $i18n.t('app.workspaceAccess.saving') : $i18n.t('app.workspaceAccess.sendInvite')}</button>
        </div>
    </div>
    {#if showTelegramInvite}<button class="secondary" type="button" on:click={onTelegramInvite}>Invite through Telegram</button>{/if}
    {#if error}<p class="error" role="alert">{error}</p>{/if}
    {#if status}<p class="success" role="status" aria-live="polite">{status}</p>{/if}
</section>

<style>
    .access-flow { display:grid; gap:.8rem; color:#18243d; }
    h2 { margin:0; font-size:1.2rem; } .hint,.empty { margin:0; color:#66718a; line-height:1.45; }
    .state { display:inline-flex; align-items:center; min-height:1.7rem; padding:.2rem .5rem; border-radius:99px; background:#eef2ff; color:#3867d6; font-size:.72rem; font-weight:700; }
    .state { background:#e8f7ef; color:#187847; } .state.pending { background:#fff1dc; color:#a96720; } .state.inactive { background:#eef1f5; color:#66718a; }
    .parents-list { display:grid; gap:.6rem; }
    .parent-row { display:grid; grid-template-columns:auto minmax(0,1fr) auto; gap:.75rem; align-items:center; padding:.75rem; border:1px solid #dfe4ef; border-radius:1rem; background:#fff; }
    .avatar { width:2.625rem; height:2.625rem; border-radius:.75rem; background:#eef2ff; color:#4d67d7; display:grid; place-items:center; font-weight:800; }
    .row-main { min-width:0; }
    .topline { display:flex; align-items:center; gap:.5rem; flex-wrap:wrap; min-width:0; }
    .name { font-size:.95rem; line-height:1.2; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .row-role { color:#66718a; font-size:.72rem; margin-top:.15rem; }
    .ids { display:flex; gap:.45rem; flex-wrap:wrap; margin-top:.5rem; }
    .id { display:inline-flex; align-items:center; gap:.4rem; max-width:100%; padding:.3rem .5rem; border:1px solid #dfe4ef; border-radius:99px; background:#fafbfe; color:#51607a; font-size:.72rem; }
    .id.email { max-width:20rem; } .id.tg { max-width:15rem; }
    .id .icon { display:inline-flex; flex:0 0 auto; } .id .text { white-space:nowrap; overflow:hidden; text-overflow:ellipsis; min-width:0; }
    .row-actions { display:grid; grid-template-columns:42px 42px; gap:.5rem; align-items:center; }
    .icon-btn { width:42px; height:42px; padding:0; border-radius:.75rem; border:1px solid #dfe4ef; background:#fff; color:#42506e; display:grid; place-items:center; position:relative; cursor:pointer; }
    .icon-btn:hover:not(.disabled) { background:#f8faff; }
    .icon-btn.danger { border-color:#efc9c9; background:#fff5f5; color:#b74d4d; }
    .icon-btn.ok { border-color:#b9e1c8; background:#f2fff5; color:#17884b; }
    .icon-btn.disabled { background:#f6f8fc; color:#a0a9bb; cursor:not-allowed; }
    .icon-btn .tip { display:none; position:absolute; right:0; top:3rem; z-index:10; background:#172036; color:#fff; padding:.4rem .5rem; border-radius:.5rem; font-size:.68rem; white-space:nowrap; box-shadow:0 6px 18px rgba(0,0,0,.14); }
    .icon-btn:hover .tip { display:block; }
    .note { margin:0; font-size:.72rem; line-height:1.45; background:#f7f9fc; color:#66718a; border-radius:.75rem; padding:.6rem .7rem; }
    label { color:#33415f; font-size:.82rem; font-weight:700; } .invite-controls { display:grid; grid-template-columns:minmax(0,1fr) auto auto; gap:.45rem; }
    input,select,button { box-sizing:border-box; min-height:2.75rem; border:1px solid #cfd6e4; border-radius:.65rem; padding:.5rem .65rem; font:inherit; } button { display:inline-flex; align-items:center; justify-content:center; gap:.4rem; border:0; background:#3867d6; color:#fff; font-weight:700; cursor:pointer; } button:disabled{opacity:.55;cursor:wait}
    .secondary { background:#fff; border:1px solid #cfd6e4; color:#33415f; } .error{margin:0;color:#a33b3b}.success{margin:0;color:#17884b} button:focus-visible,input:focus-visible,select:focus-visible{outline:3px solid #80aaff;outline-offset:2px}
    @media(max-width:640px){ .parent-row { grid-template-columns:auto minmax(0,1fr); } .row-actions { grid-column:2; justify-content:end; } .id.email,.id.tg { max-width:100%; } }
    @media(max-width:520px){.invite-controls{grid-template-columns:1fr}.invite-controls button{width:100%}}
</style>
