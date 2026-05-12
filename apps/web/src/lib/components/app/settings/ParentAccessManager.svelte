<script lang="ts">
    import { onMount } from 'svelte';
    import { useI18n } from '$lib/i18n/context';
    import {
        addParentMembership,
        loadParentMemberships,
        removeParentMembership,
        updateParentMembership,
    } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';
    import { appStore } from '$lib/stores/app';
    import type { MembershipPermission, ParentMembership } from '$lib/types/auth';

    const i18n = useI18n();

    let memberships: ParentMembership[] = [];
    let isLoading = false;
    let statusMessage = '';
    let statusTone: 'info' | 'success' | 'error' = 'info';
    let isBusy = false;
    let newEmail = '';
    let newPermission: MembershipPermission = 'editor';

    $: familyId = $appStore.familyId;
    $: canManage = $appStore.permission === 'family_admin';

    onMount(() => {
        if (canManage) {
            void refreshMemberships();
        }
    });

    function permissionLabel(permission: MembershipPermission): string {
        switch (permission) {
            case 'viewer':
                return $i18n.t('app.parentAccess.permissionViewer');
            case 'editor':
                return $i18n.t('app.parentAccess.permissionEditor');
            case 'family_admin':
                return $i18n.t('app.parentAccess.permissionFamilyAdmin');
        }
    }

    function statusLabel(status: string): string {
        return status === 'pending'
            ? $i18n.t('app.parentAccess.statusPending')
            : $i18n.t('app.parentAccess.statusActive');
    }

    function errorMessage(errorCode: string | null, fallback: string): string {
        switch (errorCode) {
            case 'PARENT_ALREADY_MEMBER':
                return $i18n.t('app.parentAccess.duplicateError');
            case 'PARENT_INVALID_PERMISSION':
                return $i18n.t('app.parentAccess.invalidPermissionError');
            case 'PARENT_LAST_ADMIN':
                return $i18n.t('app.parentAccess.lastAdminError');
            default:
                return fallback || $i18n.t('app.parentAccess.genericError');
        }
    }

    function showStatus(message: string, tone: 'info' | 'success' | 'error') {
        statusMessage = message;
        statusTone = tone;
    }

    async function refreshMemberships() {
        if (!canManage || !familyId) {
            memberships = [];
            return;
        }

        isLoading = true;
        showStatus($i18n.t('app.parentAccess.loading'), 'info');

        const result = await loadParentMemberships();
        if (result.ok) {
            memberships = result.data ?? [];
            showStatus(memberships.length > 0 ? '' : $i18n.t('app.parentAccess.empty'), 'info');
        } else {
            memberships = [];
            showStatus(errorMessage(result.errorCode, result.error), 'error');
        }

        isLoading = false;
    }

    async function inviteParent() {
        const email = newEmail.trim();
        if (!email) {
            showStatus($i18n.t('app.parentAccess.emailRequired'), 'error');
            return;
        }

        isBusy = true;
        const result = await addParentMembership({ email, permission: newPermission });
        if (result.ok) {
            if (result.data) {
                memberships = [...memberships, result.data];
                newEmail = '';
                newPermission = 'editor';
                showStatus($i18n.t('app.parentAccess.successAdd'), 'success');
                showToast($i18n.t('app.parentAccess.successAdd'), 'success');
            } else {
                const message = $i18n.t('app.parentAccess.genericError');
                showStatus(message, 'error');
                showToast(message, 'error');
            }
        } else {
            const message = errorMessage(result.errorCode, result.error);
            showStatus(message, 'error');
            showToast(message, 'error');
        }

        isBusy = false;
    }

    async function saveMembership(membership: ParentMembership) {
        isBusy = true;
        const result = await updateParentMembership(membership.id, { permission: membership.permission });
        if (result.ok) {
            if (result.data) {
                memberships = memberships.map((entry) => entry.id === membership.id ? result.data as ParentMembership : entry);
                showStatus($i18n.t('app.parentAccess.successUpdate'), 'success');
                showToast($i18n.t('app.parentAccess.successUpdate'), 'success');
            } else {
                const message = $i18n.t('app.parentAccess.genericError');
                showStatus(message, 'error');
                showToast(message, 'error');
            }
        } else {
            const message = errorMessage(result.errorCode, result.error);
            showStatus(message, 'error');
            showToast(message, 'error');
        }
        isBusy = false;
    }

    async function deleteMembership(membership: ParentMembership) {
        if (!window.confirm(`${$i18n.t('app.parentAccess.removeButton')} ${membership.email}?`)) {
            return;
        }

        isBusy = true;
        const result = await removeParentMembership(membership.id);
        if (result.ok) {
            memberships = memberships.filter((entry) => entry.id !== membership.id);
            showStatus($i18n.t('app.parentAccess.successRemove'), 'success');
            showToast($i18n.t('app.parentAccess.successRemove'), 'success');
        } else {
            const message = errorMessage(result.errorCode, result.error);
            showStatus(message, 'error');
            showToast(message, 'error');
        }
        isBusy = false;
    }

    function isLastFamilyAdmin(membership: ParentMembership): boolean {
        return membership.permission === 'family_admin'
            && memberships.filter((entry) => entry.permission === 'family_admin').length === 1;
    }
</script>

<section class="parent-access" aria-labelledby="parent-access-title" id="parent-access-section">
    <div class="card parent-access__card">
        <div class="card__header parent-access__header">
            <div>
                <p class="parent-access__eyebrow">{$i18n.t('app.parentAccess.badge')}</p>
                <h3 class="card__title" id="parent-access-title">{$i18n.t('app.parentAccess.title')}</h3>
                <p class="card__comment">{$i18n.t('app.parentAccess.description')}</p>
            </div>
            <div class="parent-access__count" aria-hidden="true">{memberships.length}</div>
        </div>

        {#if statusMessage}
            <div class="parent-access__status parent-access__status--{statusTone}" role="status" aria-live="polite">
                {statusMessage}
            </div>
        {/if}

        <div class="parent-access__form">
            <div class="form-group">
                <label for="parent-access-email">{$i18n.t('app.parentAccess.emailLabel')}</label>
                <input
                    id="parent-access-email"
                    class="input"
                    type="email"
                    bind:value={newEmail}
                    placeholder={$i18n.t('app.parentAccess.emailPlaceholder')}
                    autocomplete="email"
                    disabled={isBusy}
                />
            </div>
            <div class="form-group">
                <label for="parent-access-permission">{$i18n.t('app.parentAccess.permissionLabel')}</label>
                <select
                    id="parent-access-permission"
                    class="input"
                    bind:value={newPermission}
                    disabled={isBusy}
                >
                    <option value="viewer">{$i18n.t('app.parentAccess.permissionViewer')}</option>
                    <option value="editor">{$i18n.t('app.parentAccess.permissionEditor')}</option>
                    <option value="family_admin">{$i18n.t('app.parentAccess.permissionFamilyAdmin')}</option>
                </select>
            </div>
            <button class="btn btn--primary" id="parent-access-invite" type="button" on:click={inviteParent} disabled={isBusy}>
                {$i18n.t('app.parentAccess.inviteButton')}
            </button>
        </div>

        <div class="parent-access__list" id="parent-access-list" aria-label={$i18n.t('app.parentAccess.title')}>
            {#if isLoading}
                <div class="parent-access__empty">{$i18n.t('app.parentAccess.loading')}</div>
            {:else if memberships.length === 0}
                <div class="parent-access__empty">{$i18n.t('app.parentAccess.empty')}</div>
            {:else}
                {#each memberships as membership (membership.id)}
                    <article class="parent-access__row" data-membership-id={membership.id}>
                        <div class="parent-access__row-main">
                            <div class="parent-access__email">{membership.email}</div>
                            <div class="parent-access__meta">
                                <span class="parent-access__permission">{permissionLabel(membership.permission)}</span>
                                <span class="parent-access__status-tag">{statusLabel(membership.status)}</span>
                            </div>
                        </div>

                        <div class="parent-access__row-actions">
                            <label class="sr-only" for={`parent-access-permission-${membership.id}`}>
                                {$i18n.t('app.parentAccess.permissionLabel')} {membership.email}
                            </label>
                            <select
                                id={`parent-access-permission-${membership.id}`}
                                class="input parent-access__select"
                                bind:value={membership.permission}
                                disabled={isBusy || isLastFamilyAdmin(membership)}
                            >
                                <option value="viewer">{$i18n.t('app.parentAccess.permissionViewer')}</option>
                                <option value="editor">{$i18n.t('app.parentAccess.permissionEditor')}</option>
                                <option value="family_admin">{$i18n.t('app.parentAccess.permissionFamilyAdmin')}</option>
                            </select>

                            <button
                                class="btn btn--secondary btn--small"
                                id={`parent-access-save-${membership.id}`}
                                type="button"
                                on:click={() => saveMembership(membership)}
                                disabled={isBusy || isLastFamilyAdmin(membership)}
                            >
                                {$i18n.t('app.parentAccess.saveButton')}
                            </button>

                            <button
                                class="btn btn--danger btn--small"
                                id={`parent-access-remove-${membership.id}`}
                                type="button"
                                on:click={() => deleteMembership(membership)}
                                disabled={isBusy || isLastFamilyAdmin(membership)}
                            >
                                {$i18n.t('app.parentAccess.removeButton')}
                            </button>
                        </div>
                    </article>
                {/each}
            {/if}
        </div>
    </div>
</section>

<style>
    .parent-access {
        display: grid;
        gap: 1rem;
    }

    .parent-access__card {
        border: 1px solid rgba(0, 0, 0, 0.08);
        background: linear-gradient(180deg, rgba(13, 32, 54, 0.03), rgba(13, 32, 54, 0.02));
    }

    .parent-access__header {
        align-items: flex-start;
    }

    .parent-access__eyebrow {
        margin: 0 0 0.25rem;
        font-size: 0.75rem;
        text-transform: uppercase;
        letter-spacing: 0.12em;
        opacity: 0.72;
    }

    .parent-access__count {
        width: 2.5rem;
        height: 2.5rem;
        display: grid;
        place-items: center;
        border-radius: 999px;
        background: rgba(0, 0, 0, 0.08);
        font-weight: 700;
    }

    .parent-access__status {
        margin-top: 1rem;
        padding: 0.75rem 0.9rem;
        border-radius: 14px;
        font-size: 0.95rem;
    }

    .parent-access__status--success {
        background: rgba(38, 166, 91, 0.12);
        color: #0f5d2a;
    }

    .parent-access__status--error {
        background: rgba(230, 57, 70, 0.12);
        color: #8a1823;
    }

    .parent-access__status--info {
        background: rgba(13, 32, 54, 0.06);
    }

    .parent-access__form {
        display: grid;
        gap: 0.9rem;
        grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr) auto;
        align-items: end;
        margin-top: 1rem;
    }

    .parent-access__list {
        display: grid;
        gap: 0.75rem;
        margin-top: 1.2rem;
    }

    .parent-access__empty {
        padding: 1rem;
        border-radius: 14px;
        background: rgba(255, 255, 255, 0.6);
        border: 1px dashed rgba(0, 0, 0, 0.12);
    }

    .parent-access__row {
        display: grid;
        gap: 0.9rem;
        grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
        align-items: center;
        padding: 0.95rem 1rem;
        border-radius: 16px;
        background: rgba(255, 255, 255, 0.72);
        border: 1px solid rgba(0, 0, 0, 0.08);
    }

    .parent-access__row-main {
        min-width: 0;
    }

    .parent-access__email {
        font-weight: 700;
        overflow: hidden;
        text-overflow: ellipsis;
        word-break: break-word;
    }

    .parent-access__meta {
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem;
        margin-top: 0.35rem;
        font-size: 0.85rem;
        opacity: 0.85;
    }

    .parent-access__permission,
    .parent-access__status-tag {
        padding: 0.28rem 0.65rem;
        border-radius: 999px;
        background: rgba(13, 32, 54, 0.08);
    }

    .parent-access__row-actions {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto auto;
        gap: 0.5rem;
        align-items: center;
    }

    .parent-access__select {
        min-width: 0;
    }

    .sr-only {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        white-space: nowrap;
        border: 0;
    }

    @media (max-width: 900px) {
        .parent-access__form,
        .parent-access__row,
        .parent-access__row-actions {
            grid-template-columns: 1fr;
        }

        .parent-access__row-actions {
            align-items: stretch;
        }
    }
</style>
