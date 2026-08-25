<script lang="ts">
    import {
        addParentMembership,
        loadParentMemberships,
        resendParentInvitation,
        revokeParentInvitation,
        deactivateParentMembership,
        reactivateParentMembership,
        createParentTelegramInvite,
        updateParentMembership,
        transferParentAdmin,
        acceptAdminTransfer,
        declineAdminTransfer,
        cancelAdminTransfer,
        type ApiActionResult,
    } from '$lib/services/api';
    import type { MembershipPermission, ParentMembership } from '$lib/types/auth';
    import { useI18n } from '$lib/i18n/context';
    import TelegramIcon from '$lib/components/telegram/TelegramIcon.svelte';
    import ParentMembershipList from './ParentMembershipList.svelte';

    export let hideTitle = false;
    export let compact = false;

    let parents: ParentMembership[] = [];
    let loading = true;
    let busy = false;
    let error = '';
    let status = '';
    const i18n = useI18n();

    // Focus management: remember the element that opened a sheet so we can restore focus on close.
    let lastFocused: HTMLElement | null = null;
    function captureLastFocused(): HTMLElement | null {
        const el = document.activeElement;
        return el instanceof HTMLElement ? el : null;
    }
    function restoreFocus(): void {
        lastFocused?.focus();
        lastFocused = null;
    }
    // Auto-focus the element the action is bound to when it mounts (sheet container).
    function focusOnMount(node: HTMLElement): void {
        node.focus();
    }
    // Focus the transfer-approval accept button, remembering the previously focused element so
    // focus can be restored after the sheet closes.
    function focusAccept(node: HTMLElement): void {
        if (lastFocused === null) lastFocused = captureLastFocused();
        node.focus();
    }

    // Admin-transfer sheet state
    let transferOpen = false;
    let transferTarget: ParentMembership | null = null;
    let transferBusy = false;
    let transferError = '';

    // Wizard state
    let wizardOpen = false;
    let wizardStep: 1 | 2 | 3 = 1;
    let wizardName = '';
    let wizardRole: MembershipPermission = 'editor';
    let wizardMethod: 'email' | 'telegram' = 'email';
    let wizardEmail = '';
    let wizardLink = '';
    let wizardBusy = false;
    let wizardError = '';
    let wizardCopied = false;

    // Change-role sheet state
    let roleEditOpen = false;
    let roleEditParent: ParentMembership | null = null;
    let roleEditValue: MembershipPermission = 'editor';
    let roleEditBusy = false;
    let roleEditError = '';

    function openRoleEdit(parent: ParentMembership): void {
        lastFocused = captureLastFocused();
        roleEditParent = parent;
        roleEditValue = parent.permission;
        roleEditBusy = false;
        roleEditError = '';
        roleEditOpen = true;
    }

    function closeRoleEdit(): void {
        roleEditOpen = false;
        roleEditError = '';
        roleEditBusy = false;
        roleEditParent = null;
        restoreFocus();
    }

    // Eligible parents are active, confirmed (non-pending) non-admin memberships.
    function transferEligible(): ParentMembership[] {
        return parents.filter((p) =>
            p.status === 'active'
            && p.permission !== 'family_admin'
            && p.transferRequestStatus !== 'pending'
            && p.invitationStatus !== 'pending');
    }

    // Pending-invitation parents appear as disabled cards in the transfer sheet.
    function transferDisabled(): ParentMembership[] {
        return parents.filter((p) =>
            p.status !== 'active'
            && p.permission !== 'family_admin'
            && p.transferRequestStatus !== 'pending'
            && (p.invitationStatus === 'pending' || p.status === 'pending'));
    }

    function openTransfer(parent: ParentMembership): void {
        lastFocused = captureLastFocused();
        transferTarget = parent;
        transferBusy = false;
        transferError = '';
        transferOpen = true;
    }

    function closeTransfer(): void {
        transferOpen = false;
        transferTarget = null;
        transferError = '';
        transferBusy = false;
        restoreFocus();
    }

    async function submitTransfer(): Promise<void> {
        if (!transferTarget) return;
        transferBusy = true;
        transferError = '';
        const result = await transferParentAdmin(transferTarget.id);
        transferBusy = false;
        if (!result.ok) { transferError = result.error; return; }
        closeTransfer();
        status = $i18n.t('app.telegram.parents.transferRequested');
        await reload();
    }

    // True when the current account is the target of a pending transfer (approval sheet).
    $: isTransferTarget = parents.some((p) => p.transferRequestStatus === 'pending' && p.transferRequestRole === 'target');

    // Precise row identification: the target row drives the approval sheet, the actor row the cancel action.
    $: pendingTransferTargetRow = parents.find((p) => p.transferRequestStatus === 'pending' && p.transferRequestRole === 'target') ?? null;
    $: pendingTransferActorRow = parents.find((p) => p.transferRequestStatus === 'pending' && p.transferRequestRole === 'actor') ?? null;

    async function cancelPendingTransfer(): Promise<void> {
        const requestId = pendingTransferActorRow?.transferRequestId;
        if (requestId == null) return;
        await run(cancelAdminTransfer(requestId), $i18n.t('app.telegram.parents.transferCancelled'));
    }

    async function acceptPendingTransfer(): Promise<void> {
        const requestId = pendingTransferTargetRow?.transferRequestId;
        if (requestId == null) return;
        await run(acceptAdminTransfer(requestId), $i18n.t('app.telegram.parents.transferAccepted'));
        restoreFocus();
    }

    async function declinePendingTransfer(): Promise<void> {
        const requestId = pendingTransferTargetRow?.transferRequestId;
        if (requestId == null) return;
        await run(declineAdminTransfer(requestId), $i18n.t('app.telegram.parents.transferDeclined'));
        restoreFocus();
    }

    async function submitRoleEdit(): Promise<void> {
        if (!roleEditParent) return;
        roleEditBusy = true;
        roleEditError = '';
        const result = await updateParentMembership(roleEditParent.id, { permission: roleEditValue });
        roleEditBusy = false;
        if (!result.ok) { roleEditError = result.error; return; }
        closeRoleEdit();
        status = $i18n.t('app.parentAccess.saveButton');
        await reload();
    }

    function openWizard(): void {
        lastFocused = captureLastFocused();
        wizardOpen = true;
        wizardStep = 1;
        wizardName = '';
        wizardRole = 'editor';
        wizardMethod = 'email';
        wizardEmail = '';
        wizardLink = '';
        wizardBusy = false;
        wizardError = '';
        wizardCopied = false;
    }

    function closeWizard(): void {
        wizardOpen = false;
        wizardError = '';
        wizardBusy = false;
        restoreFocus();
    }

    function selectWizardMethod(method: 'email' | 'telegram'): void {
        wizardMethod = method;
    }

    function onWizardTabsKeydown(event: KeyboardEvent): void {
        if (wizardMethod !== 'email' && wizardMethod !== 'telegram') return;
        const tabs = ['email', 'telegram'] as const;
        const index = tabs.indexOf(wizardMethod);
        if (event.key === 'ArrowRight') {
            event.preventDefault();
            const next = tabs[(index + 1) % tabs.length];
            selectWizardMethod(next);
            document.getElementById(next === 'email' ? 'wizard-tab-email' : 'wizard-tab-telegram')?.focus();
        } else if (event.key === 'ArrowLeft') {
            event.preventDefault();
            const prev = tabs[(index - 1 + tabs.length) % tabs.length];
            selectWizardMethod(prev);
            document.getElementById(prev === 'email' ? 'wizard-tab-email' : 'wizard-tab-telegram')?.focus();
        }
    }

    async function copyWizardLink(): Promise<void> {
        try { await navigator.clipboard.writeText(wizardLink); wizardCopied = true; } catch { wizardCopied = false; }
    }

    function openWizardLink(): void { window.open(wizardLink, '_blank', 'noopener'); }

    $: wizardNameValid = wizardName.trim().length > 0;
    $: wizardEmailValid = wizardEmail.trim().length > 0;

    async function submitWizard(): Promise<void> {
        wizardBusy = true;
        wizardError = '';
        if (wizardMethod === 'email') {
            if (!wizardEmail.trim()) {
                wizardError = $i18n.t('app.workspaceAccess.emailRequired');
                wizardBusy = false;
                return;
            }
            const result = await addParentMembership({ email: wizardEmail.trim(), permission: wizardRole });
            wizardBusy = false;
            if (!result.ok) { wizardError = result.error; return; }
            wizardStep = 3;
            status = $i18n.t('app.workspaceAccess.invitationSent');
            await reload();
        } else {
            const result = await createParentTelegramInvite(wizardName.trim());
            wizardBusy = false;
            if (result?.launchUrl) { wizardLink = result.launchUrl; wizardStep = 3; await reload(); }
            else wizardError = $i18n.t('app.telegram.parents.error');
        }
    }

    export async function reload(): Promise<void> {
        loading = true;
        error = '';
        const result = await loadParentMemberships();
        if (result.ok) parents = result.data ?? [];
        else error = result.error;
        loading = false;
    }

    void reload();

    function accountLabel(parent: ParentMembership): string {
        return parent.email?.trim() || parent.telegramDisplayName?.trim()
            || (parent.telegramUsername ? `@${parent.telegramUsername}` : $i18n.t('app.workspaceAccess.unknownParent'));
    }

    function label(parent: ParentMembership): string {
        return parent.displayName?.trim() || accountLabel(parent);
    }

    function shouldShowEmail(parent: ParentMembership): boolean {
        return Boolean(parent.email?.trim() && parent.email.trim() !== label(parent));
    }

    function shouldShowTelegram(parent: ParentMembership): boolean {
        const telegram = telegramLabel(parent);
        return Boolean(telegram && telegram !== label(parent));
    }

    function telegramLabel(parent: ParentMembership): string {
        const displayName = parent.telegramDisplayName?.trim();
        const username = parent.telegramUsername?.trim();
        if (username) return `@${username}`;
        return displayName || '';
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

    function handleMembershipAction(action: string, parent: ParentMembership): void {
        if (action === 'resend') void run(resendParentInvitation(parent.id), $i18n.t('app.workspaceAccess.invitationResent'));
        if (action === 'revoke') void run(revokeParentInvitation(parent.id), $i18n.t('app.workspaceAccess.invitationRevoked'));
        if (action === 'deactivate') void run(deactivateParentMembership(parent.id), $i18n.t('app.workspaceAccess.parentDeactivated'));
        if (action === 'reactivate') void run(reactivateParentMembership(parent.id), $i18n.t('app.workspaceAccess.parentReactivated'));
        if (action === 'cancel-transfer') void cancelPendingTransfer();
    }
</script>

<section class:compact class="access-flow" aria-labelledby="parent-access-heading">
    <div class="access-header">
        {#if !hideTitle}<h2 id="parent-access-heading">{$i18n.t('app.workspaceAccess.title')}</h2>{:else}<span id="parent-access-heading" class="sr-only">{$i18n.t('app.workspaceAccess.title')}</span>{/if}
        <button type="button" class="btn add-parent" aria-label={$i18n.t('app.telegram.parents.addParent')} on:click={openWizard}><TelegramIcon name="add" size={18} /><span class="add-parent-label">{$i18n.t('app.telegram.parents.addParent')}</span></button>
    </div>

    {#if loading}<p class="hint" aria-live="polite">{$i18n.t('app.workspaceAccess.loading')}</p>
    {:else if parents.length === 0}<p class="empty">{$i18n.t('app.workspaceAccess.empty')}</p>
    {:else}
        <ParentMembershipList
            {parents}
            {busy}
            {hasAdmin}
            {label}
            {permissionLabel}
            {statusLabel}
            {shouldShowEmail}
            {shouldShowTelegram}
            {telegramLabel}
            onAction={handleMembershipAction}
            onTransfer={openTransfer}
            onRoleEdit={openRoleEdit}
        >
            <span slot="admin-note">{$i18n.t('app.telegram.parents.adminProtectionNote')}</span>
        </ParentMembershipList>
    {/if}

    {#if error}<p class="error" role="alert">{error}</p>{/if}
    {#if status}<p class="success" role="status" aria-live="polite">{status}</p>{/if}
</section>

{#if wizardOpen}
    <div class="sheet-backdrop" role="presentation" on:click={closeWizard}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="parent-wizard-title" tabindex="-1" use:focusOnMount>
        <h2 id="parent-wizard-title">{$i18n.t('app.telegram.parents.addTitle')}</h2>

        <div class="stepper" role="list" aria-label={$i18n.t('app.telegram.parents.wizardStep1')}>
            <span class:active={wizardStep === 1} class="step" role="listitem">1. {$i18n.t('app.telegram.parents.wizardStep1')}</span>
            <span class:active={wizardStep === 2} class="step" role="listitem">2. {$i18n.t('app.telegram.parents.wizardStep2')}</span>
            <span class:active={wizardStep === 3} class="step" role="listitem">3. {$i18n.t('app.telegram.parents.wizardStep3')}</span>
        </div>

        {#if wizardStep === 1}
            <p class="screen-sub">{$i18n.t('app.telegram.parents.profileStep')}</p>
            <div class="stack">
                <div>
                    <label for="parent-wizard-name">{$i18n.t('app.telegram.parents.parentName')}</label>
                    <input id="parent-wizard-name" class="input" type="text" autocomplete="name" bind:value={wizardName} disabled={wizardBusy} />
                </div>
                <div>
                    <span class="label">{$i18n.t('app.workspaceAccess.permission')}</span>
                    <div class="role-grid">
                        <button type="button" class:active={wizardRole === 'editor'} class="role-card" on:click={() => wizardRole = 'editor'}>
                            <span class="role-icon"><TelegramIcon name="pencilLine" size={18} /></span>
                            <span class="role-text"><span class="role-name">{$i18n.t('app.telegram.parents.roleEditor')}</span><span class="role-desc">{$i18n.t('app.telegram.parents.roleEditorDesc')}</span></span>
                        </button>
                        <button type="button" class:active={wizardRole === 'viewer'} class="role-card" on:click={() => wizardRole = 'viewer'}>
                            <span class="role-icon"><TelegramIcon name="eye" size={18} /></span>
                            <span class="role-text"><span class="role-name">{$i18n.t('app.telegram.parents.roleViewer')}</span><span class="role-desc">{$i18n.t('app.telegram.parents.roleViewerDesc')}</span></span>
                        </button>
                    </div>
                </div>
                <p class="warn">{$i18n.t('app.telegram.parents.adminRoleNotSelectable')}</p>
            </div>
            <div class="action-grid">
                <button type="button" class="cancel" on:click={closeWizard}><TelegramIcon name="close" size={16} />{$i18n.t('app.telegram.parents.cancel')}</button>
                <button type="button" class="btn" disabled={!wizardNameValid || wizardBusy} on:click={() => wizardStep = 2}><TelegramIcon name="arrowRight" size={16} />{$i18n.t('app.telegram.parents.next')}</button>
            </div>
        {:else if wizardStep === 2}
            <p class="screen-sub">{$i18n.t('app.telegram.parents.accountStep')}</p>
            <div class="tabs" role="tablist" aria-label={$i18n.t('app.telegram.parents.methodEmail')} tabindex="0" on:keydown={onWizardTabsKeydown}>
                <button type="button" id="wizard-tab-email" class:active={wizardMethod === 'email'} class="tab" role="tab" aria-selected={wizardMethod === 'email'} aria-controls="wizard-panel-email" tabindex={wizardMethod === 'email' ? 0 : -1} on:click={() => selectWizardMethod('email')}><TelegramIcon name="mail" size={16} />{$i18n.t('app.telegram.parents.methodEmail')}</button>
                <button type="button" id="wizard-tab-telegram" class:active={wizardMethod === 'telegram'} class="tab" role="tab" aria-selected={wizardMethod === 'telegram'} aria-controls="wizard-panel-telegram" tabindex={wizardMethod === 'telegram' ? 0 : -1} on:click={() => selectWizardMethod('telegram')}><TelegramIcon name="send" size={16} />{$i18n.t('app.telegram.parents.methodTelegram')}</button>
            </div>
            {#if wizardMethod === 'email'}
                <div id="wizard-panel-email" role="tabpanel" aria-labelledby="wizard-tab-email" tabindex="0">
                <div class="stack">
                    <div><label class="label" for="parent-wizard-email">{$i18n.t('app.telegram.parents.emailLabel')}</label><input id="parent-wizard-email" class="input" type="email" autocomplete="email" bind:value={wizardEmail} placeholder={$i18n.t('app.workspaceAccess.emailPlaceholder')} disabled={wizardBusy} /></div>
                    <p class="info">{$i18n.t('app.workspaceAccess.pendingHint')}</p>
                </div>
                <div class="action-grid">
                    <button type="button" class="ghost" disabled={wizardBusy} on:click={() => wizardStep = 1}><TelegramIcon name="back" size={16} />{$i18n.t('app.telegram.parents.back')}</button>
                    <button type="button" class="btn" disabled={!wizardEmailValid || wizardBusy} on:click={submitWizard}><TelegramIcon name="add" size={16} />{wizardBusy ? $i18n.t('app.workspaceAccess.saving') : $i18n.t('app.telegram.parents.createParent')}</button>
                </div>
                </div>
            {:else}
                <div id="wizard-panel-telegram" role="tabpanel" aria-labelledby="wizard-tab-telegram" tabindex="0">
                <div class="stack">
                    <div><label class="label" for="parent-wizard-name-ro">{$i18n.t('app.telegram.parents.nameLabel')}</label><input id="parent-wizard-name-ro" class="input" type="text" value={wizardName} readonly disabled /></div>
                    <p class="info">{$i18n.t('app.telegram.parents.telegramHint')}</p>
                    <p class="warn">{$i18n.t('app.telegram.parents.telegramWarn')}</p>
                </div>
                <div class="action-grid">
                    <button type="button" class="ghost" disabled={wizardBusy} on:click={() => wizardStep = 1}><TelegramIcon name="back" size={16} />{$i18n.t('app.telegram.parents.back')}</button>
                    <button type="button" class="btn" disabled={wizardBusy} on:click={submitWizard}><TelegramIcon name="link" size={16} />{wizardBusy ? $i18n.t('app.workspaceAccess.saving') : $i18n.t('app.telegram.parents.createLink')}</button>
                </div>
                </div>
            {/if}
        {:else if wizardStep === 3}
            <p class="screen-sub">{$i18n.t('app.telegram.parents.doneStep')}</p>
            {#if wizardMethod === 'email'}
                <div class="preview">
                    <div class="preview-head">
                        <div class="avatar">{wizardName.trim().charAt(0).toUpperCase() || $i18n.t('app.telegram.parents.unknownParent').charAt(0)}</div>
                        <div class="preview-main"><div class="preview-name">{wizardName.trim()}</div><div class="preview-role">{wizardRole === 'editor' ? $i18n.t('app.telegram.parents.roleEditor') : $i18n.t('app.telegram.parents.roleViewer')}</div></div>
                        <span class="state pending">{$i18n.t('app.workspaceAccess.pending')}</span>
                    </div>
                    <div class="account-grid">
                        <div class="box"><div class="box-title">{$i18n.t('app.telegram.parents.emailLabel')}</div><div class="box-value">{wizardEmail.trim()}</div></div>
                    </div>
                </div>
                <p class="success" role="status" aria-live="polite">{$i18n.t('app.workspaceAccess.invitationSent')}</p>
            {:else}
                <div class="link-card">
                    <div class="label">{$i18n.t('app.telegram.parents.linkReady')}</div>
                    <div class="link-box">
                        <div class="link-value">{wizardLink}</div>
                        <button type="button" class="icon-btn" aria-label={$i18n.t('app.telegram.parents.copyLink')} on:click={copyWizardLink}><TelegramIcon name="copy" size={19} /></button>
                    </div>
                    <div class="qr-row">
                        <div class="qr" aria-hidden="true"></div>
                        <p class="small">{$i18n.t('app.telegram.parents.linkExpiryNote')}</p>
                    </div>
                    {#if wizardCopied}<p class="success" role="status" aria-live="polite">{$i18n.t('app.telegram.parents.copied')}</p>{/if}
                </div>
            {/if}
            <div class="action-grid">
                <button type="button" class="cancel" on:click={closeWizard}><TelegramIcon name="close" size={16} />{$i18n.t('app.telegram.parents.close')}</button>
                <button type="button" class="btn" on:click={openWizardLink}><TelegramIcon name="send" size={16} />{$i18n.t('app.telegram.parents.openInTelegram')}</button>
            </div>
        {/if}
        {#if wizardError}<p class="error" role="alert">{wizardError}</p>{/if}
    </div>
{/if}

{#if roleEditOpen && roleEditParent}
    <div class="sheet-backdrop" role="presentation" on:click={closeRoleEdit}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="role-edit-title" tabindex="-1" use:focusOnMount>
        <h2 id="role-edit-title">{$i18n.t('app.telegram.parents.changeRole')}</h2>
        <p class="screen-sub">{label(roleEditParent)}</p>
        <div class="stack">
            <div>
                <span class="label">{$i18n.t('app.workspaceAccess.permission')}</span>
                <div class="role-grid">
                    <button type="button" class:active={roleEditValue === 'editor'} class="role-card" on:click={() => roleEditValue = 'editor'}>
                        <span class="role-icon"><TelegramIcon name="pencilLine" size={18} /></span>
                        <span class="role-text"><span class="role-name">{$i18n.t('app.telegram.parents.roleEditor')}</span><span class="role-desc">{$i18n.t('app.telegram.parents.roleEditorDesc')}</span></span>
                    </button>
                    <button type="button" class:active={roleEditValue === 'viewer'} class="role-card" on:click={() => roleEditValue = 'viewer'}>
                        <span class="role-icon"><TelegramIcon name="eye" size={18} /></span>
                        <span class="role-text"><span class="role-name">{$i18n.t('app.telegram.parents.roleViewer')}</span><span class="role-desc">{$i18n.t('app.telegram.parents.roleViewerDesc')}</span></span>
                    </button>
                </div>
            </div>
        </div>
        {#if roleEditError}<p class="error" role="alert">{roleEditError}</p>{/if}
        <div class="action-grid">
            <button type="button" class="cancel" disabled={roleEditBusy} on:click={closeRoleEdit}><TelegramIcon name="close" size={16} />{$i18n.t('app.parentAccess.cancelButton')}</button>
            <button type="button" class="btn" disabled={roleEditBusy} on:click={submitRoleEdit}>{roleEditBusy ? $i18n.t('app.workspaceAccess.saving') : $i18n.t('app.parentAccess.saveButton')}</button>
        </div>
    </div>
{/if}

{#if transferOpen}
    <div class="sheet-backdrop" role="presentation" on:click={closeTransfer}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="transfer-title" tabindex="-1" use:focusOnMount>
        <h2 id="transfer-title">{$i18n.t('app.telegram.parents.transferTitle')}</h2>
        <p class="screen-sub">{$i18n.t('app.telegram.parents.transferSelectSub')}</p>
        <p class="warn">{$i18n.t('app.telegram.parents.transferWarn')}</p>
        <div class="stack">
            {#if transferEligible().length === 0 && transferDisabled().length === 0}
                <p class="empty">{$i18n.t('app.workspaceAccess.empty')}</p>
            {:else}
                {#each transferEligible() as candidate (candidate.id)}
                    <div class="eligible-card">
                        <div class="eligible-row">
                            <div class="eligible-main">
                                <div class="avatar">{label(candidate).charAt(0).toUpperCase()}</div>
                                <div class="main"><div class="name">{label(candidate)}</div><div class="role">{$i18n.t('app.telegram.parents.transferEligible')}</div></div>
                            </div>
                            <button type="button" class="btn select-btn" disabled={transferBusy} on:click={() => transferTarget = candidate}><TelegramIcon name="check" size={16} />{$i18n.t('app.telegram.parents.transferSelect')}</button>
                        </div>
                    </div>
                {/each}
                {#each transferDisabled() as candidate (candidate.id)}
                    <div class="eligible-card disabled-card">
                        <div class="eligible-row">
                            <div class="eligible-main">
                                <div class="avatar">{label(candidate).charAt(0).toUpperCase()}</div>
                                <div class="main"><div class="name">{label(candidate)}</div><div class="role">{$i18n.t('app.telegram.parents.transferIneligible')}</div></div>
                            </div>
                            <button type="button" class="disabled-btn" disabled><TelegramIcon name="shield" size={16} />{$i18n.t('app.telegram.parents.transferUnavailable')}</button>
                        </div>
                    </div>
                {/each}
            {/if}
        </div>
        {#if transferError}<p class="error" role="alert">{transferError}</p>{/if}
        <div class="action-grid">
            <button type="button" class="cancel" disabled={transferBusy} on:click={closeTransfer}><TelegramIcon name="close" size={16} />{$i18n.t('app.parentAccess.cancelButton')}</button>
            <button type="button" class="btn" disabled={!transferTarget || transferBusy} on:click={submitTransfer}>{transferBusy ? $i18n.t('app.workspaceAccess.saving') : $i18n.t('app.telegram.parents.sendRequest')}</button>
        </div>
    </div>
{/if}

{#if isTransferTarget && pendingTransferTargetRow}
    <div class="sheet-backdrop" role="presentation" on:click={() => {}}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="transfer-approve-title" tabindex="-1">
        <h2 id="transfer-approve-title">{$i18n.t('app.telegram.parents.acceptAdmin')}</h2>
        <div class="preview">
            <div class="preview-head">
                <div class="avatar">{label(pendingTransferTargetRow).charAt(0).toUpperCase()}</div>
                <div class="preview-main"><div class="preview-name">{label(pendingTransferTargetRow)}</div><div class="preview-role">{$i18n.t('app.telegram.parents.becomesAdminAfterConfirm')}</div></div>
                <span class="state transfer">{$i18n.t('app.telegram.parents.transferRequestBadge')}</span>
            </div>
            <div class="account-grid">
                <div class="box"><div class="box-title">{$i18n.t('app.telegram.parents.transferFrom')}</div><div class="box-value">{pendingTransferTargetRow.transferRequestActorName ?? $i18n.t('app.workspaceAccess.unknownParent')}</div><div class="box-sub">{$i18n.t('app.telegram.parents.transferFromSub')}</div></div>
                <div class="box"><div class="box-title">{$i18n.t('app.telegram.parents.transferWhen')}</div><div class="box-value">{$i18n.t('app.telegram.parents.transferWhenValue')}</div><div class="box-sub">{$i18n.t('app.telegram.parents.transferWhenSub')}</div></div>
            </div>
            <div class="preview-actions">
                <button type="button" class="cancel" disabled={busy} on:click={declinePendingTransfer}><TelegramIcon name="close" size={16} />{$i18n.t('app.telegram.parents.declineAdmin')}</button>
                <button type="button" class="btn ok-btn" disabled={busy} on:click={acceptPendingTransfer} use:focusAccept>{busy ? $i18n.t('app.workspaceAccess.saving') : $i18n.t('app.telegram.parents.acceptAdmin')}</button>
            </div>
        </div>
        <p class="warn">{$i18n.t('app.telegram.parents.transferConfirmWarn')}</p>
    </div>
{/if}

<style>
    .access-flow { display:grid; gap:.75rem; color:#18243d; }
    .access-flow.compact { gap:.5rem; }
    .access-header{display:flex;align-items:center;justify-content:space-between;gap:.75rem}
    h2 { margin:0; font-size:1.2rem; } .hint,.empty { margin:0; color:#66718a; line-height:1.45; }
    .state { display:inline-flex; align-items:center; min-height:1.7rem; padding:.2rem .5rem; border-radius:99px; background:#eef2ff; color:#3867d6; font-size:.72rem; font-weight:700; }
    .state { background:#e8f7ef; color:#187847; } .state.pending { background:#fff1dc; color:#a96720; } .state.inactive { background:#eef1f5; color:#66718a; } .state.transfer { background:#eef3ff; color:#4d63b9; }
    .eligible-card{display:grid;border:1px solid #dfe4ef;border-radius:.9rem;background:#fff;overflow:hidden}
    .eligible-card.disabled-card{background:#f6f8fc;border-color:#e3e8f0}
    .eligible-row{display:flex;align-items:center;justify-content:space-between;gap:.6rem;padding:.65rem .7rem}
    .eligible-main{display:flex;align-items:center;gap:.6rem;min-width:0}
    .eligible-main .avatar{width:2.4rem;height:2.4rem}
    .eligible-main .main{min-width:0}
    .eligible-main .name{font-size:.85rem;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .eligible-main .role{font-size:.7rem;color:#66718a;margin-top:.1rem}
    .select-btn{min-height:2.3rem;padding:.35rem .6rem;display:inline-flex;align-items:center;gap:.35rem;font-size:.74rem;border-radius:.6rem;background:#3867d6;border-color:#3867d6;color:#fff;font-weight:700;border:1px solid transparent;cursor:pointer}
    .disabled-btn{min-height:2.3rem;padding:.35rem .6rem;display:inline-flex;align-items:center;gap:.35rem;font-size:.74rem;border-radius:.6rem;background:#eef1f5;border:1px solid #dfe4ef;color:#99a2b4;cursor:not-allowed}
    .box-sub{font-size:.66rem;color:#66718a;margin-top:.3rem}
    .preview-actions{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.5rem;border-top:1px solid #dfe4ef;padding:.7rem .85rem}
    .ok-btn{background:#17884b;border-color:#17884b}
    :global(.parents-list) { display:grid; gap:0; overflow:hidden; }
    :global(.parent-row) { display:grid; grid-template-columns:2.625rem minmax(0,1fr) 5.75rem; gap:.75rem; align-items:center; padding:.75rem 0; border:0; border-bottom:1px solid #e5e9f1; background:transparent; }
    :global(.parent-row:last-child) { border-bottom:0; }
    :global(.avatar) { width:2.625rem; height:2.625rem; border-radius:.75rem; background:#eef2ff; color:#4d67d7; display:grid; place-items:center; font-weight:800; }
    :global(.row-main) { min-width:0; }
    :global(.topline) { display:flex; align-items:center; gap:.5rem; flex-wrap:wrap; min-width:0; }
    :global(.name) { font-size:.95rem; line-height:1.2; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    :global(.row-role) { color:#66718a; font-size:.75rem; margin-top:.18rem; }
    :global(.meta-label) { color:#7a8498; }
    :global(.ids) { display:flex; gap:.35rem; flex-wrap:wrap; margin-top:.4rem; }
    :global(.id) { display:inline-flex; align-items:center; gap:.35rem; max-width:100%; padding:.22rem .45rem; border:1px solid #dfe4ef; border-radius:99px; background:#fafbfe; color:#51607a; font-size:.72rem; }
    .id.email { max-width:20rem; } .id.tg { max-width:15rem; }
    .id .icon { display:inline-flex; flex:0 0 auto; } .id .text { white-space:nowrap; overflow:hidden; text-overflow:ellipsis; min-width:0; }
    .row-actions { display:grid; grid-template-columns:repeat(2,44px); justify-content:end; gap:.5rem; align-items:center; width:5.75rem; }
    .icon-btn { width:44px; height:44px; padding:0; border-radius:.75rem; border:1px solid #dfe4ef; background:#fff; color:#42506e; display:grid; place-items:center; position:relative; cursor:pointer; }
    .icon-btn:hover:not(.disabled) { background:#f8faff; }
    .icon-btn.danger { border-color:#efc9c9; background:#fff5f5; color:#b74d4d; }
    .icon-btn.ok { border-color:#b9e1c8; background:#f2fff5; color:#17884b; }
    .icon-btn.disabled { background:#f6f8fc; color:#a0a9bb; cursor:not-allowed; }
    .icon-btn .tip { display:none; position:absolute; right:0; top:3rem; z-index:10; background:#172036; color:#fff; padding:.4rem .5rem; border-radius:.5rem; font-size:.68rem; white-space:nowrap; box-shadow:0 6px 18px rgba(0,0,0,.14); }
    .icon-btn:hover .tip { display:block; }
    .note { margin:0; font-size:.72rem; line-height:1.45; background:#f7f9fc; color:#66718a; border-radius:.75rem; padding:.6rem .7rem; }
    .btn.add-parent { min-height:2.75rem; white-space:nowrap; }
    .error{margin:0;color:#a33b3b}.success{margin:0;color:#17884b} button:focus-visible,input:focus-visible{outline:3px solid #80aaff;outline-offset:2px}

    .sheet-backdrop{position:fixed;inset:0;z-index:40;background:rgb(15 24 45 / 35%)}
    .sheet{position:fixed;inset:auto 0 0;z-index:41;display:grid;gap:.9rem;padding:1rem max(1rem,env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom));border-radius:1.1rem 1.1rem 0 0;background:#fff;box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%);max-height:88dvh;overflow:auto}
    .sheet h2{margin:0;color:#18243d;font-size:1.15rem}
    .screen-sub{margin:0;color:#66718a;font-size:.78rem;line-height:1.4}
    .stepper{display:flex;gap:.4rem;flex-wrap:wrap}
    .step{padding:.35rem .55rem;border-radius:999px;font-size:.68rem;background:#f6f8fc;border:1px solid #dfe4ef;color:#65718a}
    .step.active{background:#eef2ff;border-color:#cad5ff;color:#3867d6}
    .stack{display:grid;gap:.6rem}
    .label{color:#33415f;font-size:.82rem;font-weight:700}
    label{display:block;margin-bottom:.35rem;color:#33415f;font-size:.82rem;font-weight:700}
    .input{box-sizing:border-box;width:100%;min-height:2.75rem;padding:.5rem .65rem;border:1px solid #cfd6e4;border-radius:.65rem;font:inherit}
    .input:disabled{opacity:.55}
    .role-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.5rem}
    .role-card{display:flex;gap:.55rem;align-items:center;min-width:0;padding:.6rem;border:1px solid #dfe4ef;border-radius:.8rem;background:#fff;text-align:left;color:#18243d}
    .role-card.active{border-color:#9baef0;background:#eef2ff}
    .role-icon{display:inline-flex;flex:0 0 auto;color:#5b63e9}
    .role-text{display:grid;gap:.1rem;min-width:0}
    .role-name{font-size:.82rem;font-weight:800}
    .role-desc{font-size:.68rem;color:#66718a;line-height:1.25}
    .tabs{display:grid;grid-template-columns:1fr 1fr;gap:.35rem;padding:.3rem;background:#f4f6fb;border-radius:.75rem}
    .tab{display:inline-flex;align-items:center;justify-content:center;gap:.4rem;min-height:2.5rem;border:0;border-radius:.55rem;background:transparent;font:inherit;font-weight:800;color:#68738d;padding:0 .5rem;cursor:pointer}
    .tab.active{background:#fff;color:#18243d;box-shadow:0 2px 8px rgb(34 44 80 / 8%)}
    .warn,.info{font-size:.74rem;line-height:1.45;border-radius:.75rem;padding:.55rem .65rem}
    .warn{background:#fff8e6;color:#7e6512;border:1px solid #f0e1a6}
    .info{background:#eef3ff;color:#5164b8;border:1px solid #d8e0ff}
    .small{font-size:.7rem;color:#66718a;line-height:1.35;margin:.6rem 0 0}
    .action-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.5rem}
    .btn,.ghost,.cancel{min-height:2.75rem;border-radius:.7rem;padding:.5rem .7rem;border:1px solid #dfe4ef;font:inherit;font-weight:700;cursor:pointer}
    .btn{background:#3867d6;border-color:#3867d6;color:#fff}
    .btn:disabled{opacity:.55;cursor:wait}
    .ghost{background:#fff;color:#33415f}
    .cancel{background:#fff7f7;border-color:#efcccc;color:#a84a4a}
    .preview{border:1px solid #dfe4ef;border-radius:1rem;background:#fff;overflow:hidden}
    .preview-head{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:.6rem;padding:.75rem .85rem;background:#fbfcff}
    .preview .avatar{width:2.4rem;height:2.4rem}
    .preview-main{min-width:0}
    .preview-name{font-size:.9rem;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .preview-role{font-size:.72rem;color:#66718a;margin-top:.15rem}
    .account-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));border-top:1px solid #dfe4ef}
    .box{padding:.7rem .85rem;min-width:0}
    .box-title{font-size:.66rem;color:#66718a;margin-bottom:.35rem}
    .box-value{font-size:.82rem;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .link-card{border:1px solid #dfe4ef;border-radius:.9rem;padding:.75rem;background:#fbfcff}
    .link-card .label{display:block;margin-bottom:.5rem;color:#33415f;font-size:.78rem;font-weight:700}
    .link-box{display:grid;grid-template-columns:minmax(0,1fr) 42px;gap:.5rem}
    .link-value{height:2.625rem;border:1px solid #dfe4ef;border-radius:.65rem;background:#fff;display:flex;align-items:center;padding:0 .6rem;font-size:.74rem;color:#46516c;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
    .link-box .icon-btn{width:42px;height:42px;padding:0;border-radius:.65rem;border:1px solid #dfe4ef;background:#fff;color:#42506e;display:grid;place-items:center;cursor:pointer}
    .qr-row{display:flex;gap:.75rem;align-items:center;margin-top:.75rem}
    .qr{width:94px;height:94px;flex:0 0 auto;border:1px solid #dfe4ef;border-radius:.75rem;background:linear-gradient(90deg,#172036 10px,transparent 10px) 0 0/24px 24px,linear-gradient(#172036 10px,transparent 10px) 0 0/24px 24px,#fff}
    .qr-row .small{margin:0;flex:1;min-width:0}

    .sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
    .access-flow.compact .access-header{justify-content:space-between}
    .access-flow.compact .add-parent{width:44px;min-height:44px;padding:0;border-radius:.75rem}
    .access-flow.compact .add-parent-label{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
    .access-flow.compact .parent-row{grid-template-columns:2.5rem minmax(0,1fr) 5.75rem;padding:.55rem 0;gap:.6rem}
    .access-flow.compact .avatar{width:2.5rem;height:2.5rem}
    .access-flow.compact .ids{margin-top:.25rem}
    .access-flow.compact .note{padding:.45rem .55rem}
    @media(max-width:640px){.access-flow.compact .access-header{align-items:center;flex-direction:row}.access-flow.compact .row-actions{grid-column:3;justify-content:end;margin-top:0}.access-flow.compact .icon-btn{width:44px;height:44px}}
    @media(max-width:640px){ .access-header{align-items:stretch;flex-direction:column;gap:.5rem}.access-header .add-parent{width:100%}.parent-row { grid-template-columns:2.5rem minmax(0,1fr) 5.75rem; gap:.6rem; } .row-actions { grid-column:3; justify-content:end; margin-top:0; } .id.email,.id.tg { max-width:100%; } }
    @media(max-width:390px){ .role-grid{grid-template-columns:1fr}.tabs{grid-template-columns:1fr}.action-grid{grid-template-columns:1fr}.sheet{width:100%}.preview-actions{grid-template-columns:1fr} }
    @media (min-width: 700px) {.sheet{inset:50% auto auto 50%;width:min(38rem,calc(100% - 3rem));max-height:min(82dvh,46rem);padding:1.4rem;border-radius:1.25rem;box-shadow:0 1.5rem 4rem rgb(27 39 73 / 22%);transform:translate(-50%,-50%)}}
</style>
