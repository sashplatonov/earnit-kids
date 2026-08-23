<script lang="ts">
    import {
        addParentMembership,
        loadParentMemberships,
        resendParentInvitation,
        revokeParentInvitation,
        type ApiActionResult,
    } from '$lib/services/api';
    import type { MembershipPermission, ParentMembership } from '$lib/types/auth';

    export let showTelegramInvite = false;
    export let onTelegramInvite: () => void = () => {};

    let parents: ParentMembership[] = [];
    let email = '';
    let permission: MembershipPermission = 'editor';
    let loading = true;
    let busy = false;
    let error = '';
    let status = '';

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
            || (parent.telegramUsername ? `@${parent.telegramUsername}` : 'Unknown parent');
    }

    function permissionLabel(value: MembershipPermission): string {
        return value === 'family_admin' ? 'Family admin' : value === 'editor' ? 'Editor' : 'Viewer';
    }

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
        if (!email.trim()) { error = 'Enter an email address.'; return; }
        await run(addParentMembership({ email: email.trim(), permission }), 'Invitation sent.');
        if (!error) email = '';
    }
</script>

<section class="access-flow" aria-labelledby="parent-access-heading">
    <div class="heading-row">
        <div><p class="eyebrow">Workspace access</p><h2 id="parent-access-heading">Parents and invitations</h2></div>
        <span class="badge">Server-authorized</span>
    </div>
    <p class="hint">Pending invitations are not active members until the recipient accepts them.</p>

    {#if loading}<p class="hint" aria-live="polite">Loading access…</p>
    {:else if parents.length === 0}<p class="empty">No parent memberships yet.</p>
    {:else}
        <div class="members" role="list" aria-label="Parent memberships">
            {#each parents as parent (parent.id)}
                <div class="member" role="listitem">
                    <div class="member-main"><strong>{label(parent)}</strong><span>{parent.email ?? 'Telegram only'} · {permissionLabel(parent.permission)}</span></div>
                    <span class:pending={parent.status === 'pending'} class="state">{parent.status}</span>
                    {#if parent.status === 'pending'}
                        <button type="button" disabled={busy} on:click={() => run(resendParentInvitation(parent.id), 'Invitation resent.')}>Resend</button>
                        <button type="button" class="quiet-danger" disabled={busy} on:click={() => run(revokeParentInvitation(parent.id), 'Invitation revoked.')}>Revoke</button>
                    {/if}
                </div>
            {/each}
        </div>
    {/if}

    <div class="invite-form" aria-label="Invite a parent">
        <label for="workspace-parent-email">Parent email</label>
        <div class="invite-controls">
            <input id="workspace-parent-email" type="email" autocomplete="email" bind:value={email} placeholder="name@example.com" disabled={busy} />
            <select bind:value={permission} aria-label="Parent permission" disabled={busy}>
                <option value="editor">Editor</option><option value="viewer">Viewer</option>
            </select>
            <button type="button" disabled={busy} on:click={invite}>{busy ? 'Saving…' : 'Send invite'}</button>
        </div>
    </div>
    {#if showTelegramInvite}<button class="secondary" type="button" on:click={onTelegramInvite}>Invite through Telegram</button>{/if}
    {#if error}<p class="error" role="alert">{error}</p>{/if}
    {#if status}<p class="success" role="status" aria-live="polite">{status}</p>{/if}
</section>

<style>
    .access-flow { display:grid; gap:.8rem; color:#18243d; }
    .heading-row { display:flex; align-items:flex-start; justify-content:space-between; gap:.75rem; }
    .eyebrow { margin:0 0 .2rem; color:#3867d6; font-size:.72rem; font-weight:800; letter-spacing:.08em; text-transform:uppercase; }
    h2 { margin:0; font-size:1.2rem; } .hint,.empty { margin:0; color:#66718a; line-height:1.45; }
    .badge,.state { display:inline-flex; align-items:center; min-height:1.7rem; padding:.2rem .5rem; border-radius:99px; background:#eef2ff; color:#3867d6; font-size:.72rem; font-weight:700; }
    .state { background:#eef1f5; color:#66718a; } .state.pending { background:#fff1dc; color:#a96720; }
    .members { display:grid; border:1px solid #e1e6ef; border-radius:.8rem; overflow:hidden; }
    .member { display:grid; grid-template-columns:minmax(0,1fr) auto auto; align-items:center; gap:.5rem; padding:.7rem; border-bottom:1px solid #edf0f5; } .member:last-child{border-bottom:0}
    .member-main { min-width:0; display:grid; gap:.15rem; } .member-main strong,.member-main span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; } .member-main span { color:#66718a; font-size:.78rem; }
    label { color:#33415f; font-size:.82rem; font-weight:700; } .invite-controls { display:grid; grid-template-columns:minmax(0,1fr) auto auto; gap:.45rem; }
    input,select,button { box-sizing:border-box; min-height:2.75rem; border:1px solid #cfd6e4; border-radius:.65rem; padding:.5rem .65rem; font:inherit; } button { border:0; background:#3867d6; color:#fff; font-weight:700; cursor:pointer; } button:disabled{opacity:.55;cursor:wait}
    .quiet-danger { background:#fff5f5; border:1px solid #f0caca; color:#a33b3b; } .secondary { background:#fff; border:1px solid #cfd6e4; color:#33415f; } .error{margin:0;color:#a33b3b}.success{margin:0;color:#17884b} button:focus-visible,input:focus-visible,select:focus-visible{outline:3px solid #80aaff;outline-offset:2px}
    @media(max-width:520px){.heading-row{display:grid}.invite-controls{grid-template-columns:1fr}.member{grid-template-columns:minmax(0,1fr) auto}.member button{grid-column:2}.badge{justify-self:start}}
</style>
