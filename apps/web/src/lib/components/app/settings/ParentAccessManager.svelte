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
            case 'PARENT_ADMIN_DELETE_FORBIDDEN':
                return $i18n.t('app.parentAccess.adminDeleteForbiddenError');
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

    function cannotRemoveMembership(membership: ParentMembership): boolean {
        return membership.permission === 'family_admin';
    }
</script>

<section class="parent-access" aria-labelledby="parent-access-title" id="parent-access-section">
    <div class="card parent-access__card">
        <div class="card__header parent-access__header">
            <div class="parent-access__heading">
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
            <div class="form-group parent-access__field">
                <label for="parent-access-email">{$i18n.t('app.parentAccess.emailLabel')}</label>
                <input
                    id="parent-access-email"
                    class="input parent-access__input"
                    type="email"
                    bind:value={newEmail}
                    placeholder={$i18n.t('app.parentAccess.emailPlaceholder')}
                    autocomplete="email"
                    disabled={isBusy}
                />
            </div>
            <div class="form-group parent-access__field">
                <label for="parent-access-permission">{$i18n.t('app.parentAccess.permissionLabel')}</label>
                <select
                    id="parent-access-permission"
                    class="input parent-access__select parent-access__select--form"
                    bind:value={newPermission}
                    disabled={isBusy}
                >
                    <option value="viewer">{$i18n.t('app.parentAccess.permissionViewer')}</option>
                    <option value="editor">{$i18n.t('app.parentAccess.permissionEditor')}</option>
                    <option value="family_admin">{$i18n.t('app.parentAccess.permissionFamilyAdmin')}</option>
                </select>
            </div>
            <div class="parent-access__actions">
                <button
                    class="parent-access__icon-action parent-access__icon-action--invite"
                    id="parent-access-invite"
                    type="button"
                    on:click={inviteParent}
                    disabled={isBusy}
                    title={$i18n.t('app.parentAccess.inviteButton')}
                    aria-label={$i18n.t('app.parentAccess.inviteButton')}
                >
                    <svg viewBox="0 0 24 24" class="parent-access__icon-svg" aria-hidden="true">
                        <path d="M3 11.5 20.5 4 14 21l-3.2-6.3L3 11.5Z"></path>
                        <path d="M10.8 14.7 20.5 4"></path>
                    </svg>
                </button>
            </div>
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
                            <div class="parent-access__email" title={membership.email}>{membership.email}</div>
                            <div class="parent-access__meta">
                                <span class="parent-access__status-tag">{statusLabel(membership.status)}</span>
                            </div>
                        </div>

                        <div class="parent-access__row-controls">
                            <label class="sr-only" for={`parent-access-permission-${membership.id}`}>
                                {$i18n.t('app.parentAccess.permissionLabel')} {membership.email}
                            </label>
                            <div class="parent-access__permission-control parent-access__permission-control--row">
                                <span class="parent-access__permission-icon" aria-hidden="true" title={permissionLabel(membership.permission)}>
                                    {#if membership.permission === 'viewer'}
                                        <svg viewBox="0 0 24 24" class="parent-access__icon-svg">
                                            <path d="M2.5 12s3.4-6 9.5-6 9.5 6 9.5 6-3.4 6-9.5 6-9.5-6-9.5-6Z"></path>
                                            <circle cx="12" cy="12" r="3"></circle>
                                        </svg>
                                    {:else if membership.permission === 'editor'}
                                        <svg viewBox="0 0 24 24" class="parent-access__icon-svg">
                                            <path d="m4 20 4.2-1 9.4-9.4-3.2-3.2L5 15.8 4 20Z"></path>
                                            <path d="m13.9 5 3.2 3.2"></path>
                                        </svg>
                                    {:else}
                                        <svg viewBox="0 0 24 24" class="parent-access__icon-svg">
                                            <path d="M12 3.5 5.5 6v5.2c0 4.2 2.7 8 6.5 9.3 3.8-1.3 6.5-5.1 6.5-9.3V6L12 3.5Z"></path>
                                            <path d="M9.5 12.2 11.3 14l3.7-4"></path>
                                        </svg>
                                    {/if}
                                </span>
                                <select
                                    id={`parent-access-permission-${membership.id}`}
                                    class="input parent-access__select"
                                    bind:value={membership.permission}
                                    disabled={isBusy || isLastFamilyAdmin(membership)}
                                    title={permissionLabel(membership.permission)}
                                    on:change={() => saveMembership(membership)}
                                >
                                    <option value="viewer">{$i18n.t('app.parentAccess.permissionViewer')}</option>
                                    <option value="editor">{$i18n.t('app.parentAccess.permissionEditor')}</option>
                                    <option value="family_admin">{$i18n.t('app.parentAccess.permissionFamilyAdmin')}</option>
                                </select>
                            </div>
                        </div>

                        <div class="parent-access__row-actions">
                            <button
                                class="parent-access__icon-action parent-access__icon-action--danger"
                                id={`parent-access-remove-${membership.id}`}
                                type="button"
                                on:click={() => deleteMembership(membership)}
                                disabled={isBusy || cannotRemoveMembership(membership)}
                                title={`${$i18n.t('app.parentAccess.removeButton')} ${membership.email}`}
                                aria-label={`${$i18n.t('app.parentAccess.removeButton')} ${membership.email}`}
                            >
                                <svg viewBox="0 0 24 24" class="parent-access__icon-svg" aria-hidden="true">
                                    <path d="M4.5 7h15"></path>
                                    <path d="M9.5 3.8h5l.7 2.2"></path>
                                    <path d="M8 10.2v6.5"></path>
                                    <path d="M12 10.2v6.5"></path>
                                    <path d="M16 10.2v6.5"></path>
                                    <path d="M6.3 7 7 19.2c.1.9.8 1.6 1.7 1.6h6.6c.9 0 1.6-.7 1.7-1.6L17.7 7"></path>
                                </svg>
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
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        align-items: flex-start;
        gap: 1rem;
    }

    .parent-access__heading {
        min-width: 0;
    }

    .parent-access__eyebrow {
        margin: 0 0 0.25rem;
        font-size: 0.75rem;
        text-transform: uppercase;
        letter-spacing: 0.12em;
        opacity: 0.72;
    }

    .parent-access__count {
        flex-shrink: 0;
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
        gap: 1rem;
        margin-top: 1rem;
    }

    .parent-access__field {
        min-width: 0;
    }

    .parent-access__input {
        width: 100%;
        min-height: 3.15rem;
    }

    .parent-access__actions {
        display: flex;
        justify-content: flex-end;
        margin-top: -0.25rem;
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
        display: flex;
        align-items: center;
        gap: 0.9rem;
        padding: 0.95rem 1rem;
        border-radius: 16px;
        background: rgba(255, 255, 255, 0.72);
        border: 1px solid rgba(0, 0, 0, 0.08);
    }

    .parent-access__row-main {
        min-width: 0;
        flex: 1 1 auto;
    }

    .parent-access__email {
        font-weight: 700;
        line-height: 1.35;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .parent-access__meta {
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem;
        margin-top: 0.35rem;
        font-size: 0.85rem;
        opacity: 0.85;
    }

    .parent-access__status-tag {
        padding: 0.28rem 0.65rem;
        border-radius: 999px;
        background: rgba(13, 32, 54, 0.08);
    }

    .parent-access__permission-control {
        display: flex;
        align-items: center;
        gap: 0.6rem;
        min-width: 0;
    }

    .parent-access__permission-control--row {
        flex: 0 0 auto;
    }

    .parent-access__permission-icon {
        width: 2.35rem;
        height: 2.35rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: 999px;
        background: linear-gradient(135deg, rgba(87, 121, 206, 0.12), rgba(84, 179, 160, 0.18));
        color: #28405d;
        flex: none;
    }

    .parent-access__icon-svg {
        width: 1.1rem;
        height: 1.1rem;
        fill: none;
        stroke: currentColor;
        stroke-width: 1.8;
        stroke-linecap: round;
        stroke-linejoin: round;
    }

    .parent-access__row-controls {
        min-width: 0;
        flex: 0 0 auto;
    }

    .parent-access__row-actions {
        display: flex;
        justify-content: center;
        align-items: center;
        flex: 0 0 auto;
    }

    .parent-access__select {
        min-width: 0;
        width: 11rem;
        padding-left: 0.95rem;
        padding-right: 2.2rem;
    }

    .parent-access__select--form {
        min-height: 3rem;
        width: 100%;
        flex: 1 1 auto;
    }

    .parent-access__icon-action {
        width: 2.8rem;
        height: 2.8rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border: 0;
        border-radius: 999px;
        background: rgba(13, 32, 54, 0.08);
        color: #203550;
        cursor: pointer;
        transition: transform 140ms ease, background-color 140ms ease, box-shadow 140ms ease, color 140ms ease;
    }

    .parent-access__icon-action:hover:not(:disabled) {
        transform: translateY(-1px);
        background: rgba(13, 32, 54, 0.12);
        box-shadow: 0 8px 18px rgba(32, 53, 80, 0.14);
    }

    .parent-access__icon-action:disabled {
        opacity: 0.5;
        cursor: not-allowed;
        box-shadow: none;
    }

    .parent-access__icon-action--invite {
        background: linear-gradient(135deg, #2f6cf2, #3cb7a0);
        color: #fff;
    }

    .parent-access__icon-action--invite:hover:not(:disabled) {
        background: linear-gradient(135deg, #2658c5, #309987);
    }

    .parent-access__icon-action--danger {
        width: 2.7rem;
        height: 2.7rem;
        background: rgba(230, 57, 70, 0.12);
        color: #8a1823;
    }

    .parent-access__icon-action--danger:hover:not(:disabled) {
        background: rgba(230, 57, 70, 0.18);
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
        .parent-access__header {
            grid-template-columns: 1fr;
            margin-bottom: 1rem;
        }

        .parent-access__count {
            justify-self: start;
        }

        .parent-access__status {
            margin-top: 0.75rem;
            padding: 0.7rem 0.85rem;
        }

        .parent-access__form {
            gap: 0.8rem;
            margin-top: 0.85rem;
        }

        .parent-access__input,
        .parent-access__select--form {
            min-height: 2.9rem;
        }

        .parent-access__row-actions {
            justify-content: flex-end;
        }

        .parent-access__list {
            gap: 0.6rem;
            margin-top: 0.95rem;
        }

        .parent-access__row {
            gap: 0.7rem;
            padding: 0.8rem 0.85rem;
        }

        .parent-access__email {
            font-size: 0.95rem;
        }

        .parent-access__meta {
            margin-top: 0.25rem;
        }

        .parent-access__select {
            width: 7.5rem;
        }

        .parent-access__actions {
            margin-top: -0.4rem;
        }

        .parent-access__icon-action--danger {
            width: 2.5rem;
            height: 2.5rem;
        }
    }
</style>
