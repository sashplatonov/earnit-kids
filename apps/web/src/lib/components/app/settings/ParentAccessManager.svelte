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
            case 'PARENT_PRIMARY_ADMIN':
                return $i18n.t('app.parentAccess.primaryAdminError');
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

    async function refreshMemberships() {
        if (!canManage || !familyId) {
            memberships = [];
            return;
        }

        isLoading = true;

        const result = await loadParentMemberships();
        if (result.ok) {
            memberships = result.data ?? [];
        } else {
            memberships = [];
            showToast(errorMessage(result.errorCode, result.error), 'error');
        }

        isLoading = false;
    }

    async function inviteParent() {
        const email = newEmail.trim();
        if (!email) {
            showToast($i18n.t('app.parentAccess.emailRequired'), 'error');
            return;
        }

        isBusy = true;
        const result = await addParentMembership({ email, permission: newPermission });
        if (result.ok) {
            if (result.data) {
                memberships = [...memberships, result.data];
                newEmail = '';
                newPermission = 'editor';
                showToast($i18n.t('app.parentAccess.successAdd'), 'success');
            } else {
                const message = $i18n.t('app.parentAccess.genericError');
                showToast(message, 'error');
            }
        } else {
            const message = errorMessage(result.errorCode, result.error);
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
                showToast($i18n.t('app.parentAccess.successUpdate'), 'success');
            } else {
                const message = $i18n.t('app.parentAccess.genericError');
                showToast(message, 'error');
            }
        } else {
            const message = errorMessage(result.errorCode, result.error);
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
            showToast($i18n.t('app.parentAccess.successRemove'), 'success');
        } else {
            const message = errorMessage(result.errorCode, result.error);
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

        <div class="parent-access__form">
            <div class="form-group parent-access__field parent-access__field--email">
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
            <div class="form-group parent-access__field parent-access__field--permission">
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
                            <span class="parent-access__status-tag">{statusLabel(membership.status)}</span>
                        </div>

                        <div class="parent-access__row-controls">
                            <label class="sr-only" for={`parent-access-permission-${membership.id}`}>
                                {$i18n.t('app.parentAccess.permissionLabel')} {membership.email}
                            </label>
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
                                class="input parent-access__select parent-access__select--row"
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

    .parent-access__form {
        display: flex;
        flex-wrap: wrap;
        gap: 0.65rem;
        margin-top: 0.85rem;
        align-items: end;
    }

    .parent-access__field {
        min-width: 0;
    }

    .parent-access__field--email {
        flex: 1 1 14rem;
        min-width: 12rem;
    }

    .parent-access__field--permission {
        flex: 0 0 auto;
        min-width: 8.5rem;
    }

    .parent-access__input {
        width: 100%;
        min-height: 3.15rem;
    }

    .parent-access__select {
        min-width: 0;
        width: 100%;
        padding-left: 0.95rem;
        padding-right: 2.2rem;
    }

    .parent-access__select--form {
        min-height: 3rem;
        width: 100%;
    }

    .parent-access__select--row {
        width: auto;
        max-width: 10rem;
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
        grid-template-columns: minmax(0, 1fr) auto auto;
        align-items: center;
        gap: 0.75rem;
        padding: 0.75rem 1rem;
        border-radius: 16px;
        background: rgba(255, 255, 255, 0.72);
        border: 1px solid rgba(0, 0, 0, 0.08);
    }

    .parent-access__row-main {
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
    }

    .parent-access__email {
        font-weight: 700;
        line-height: 1.35;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        min-width: 0;
    }

    .parent-access__status-tag {
        display: inline-block;
        width: fit-content;
        padding: 0.18rem 0.55rem;
        border-radius: 999px;
        background: rgba(13, 32, 54, 0.08);
        font-size: 0.8rem;
        white-space: nowrap;
    }

    .parent-access__row-controls {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        flex-shrink: 0;
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
        flex-shrink: 0;
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
        flex-shrink: 0;
        align-self: end;
    }

    .parent-access__icon-action--invite:hover:not(:disabled) {
        background: linear-gradient(135deg, #2658c5, #309987);
    }

    .parent-access__icon-action--danger {
        width: 2.4rem;
        height: 2.4rem;
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
            margin-bottom: 0.8rem;
        }

        .parent-access__count {
            justify-self: start;
        }

        .parent-access__form {
            gap: 0.55rem;
            margin-top: 0.7rem;
        }

        .parent-access__field--email {
            flex: 1 1 100%;
            min-width: 100%;
        }

        .parent-access__field--permission {
            flex: 1 1 auto;
        }

        .parent-access__input,
        .parent-access__select--form {
            min-height: 2.9rem;
        }

        .parent-access__list {
            gap: 0.6rem;
            margin-top: 0.95rem;
        }

        .parent-access__row {
            grid-template-columns: minmax(0, 1fr) auto;
            gap: 0.5rem;
            padding: 0.7rem 0.75rem;
        }

        .parent-access__row-controls {
            gap: 0.35rem;
        }

        .parent-access__email {
            font-size: 0.95rem;
        }

        .parent-access__select--row {
            max-width: 8rem;
        }

        .parent-access__icon-action--danger {
            width: 2.4rem;
            height: 2.4rem;
        }
    }
</style>
