<script lang="ts">
    import { afterUpdate, onMount } from 'svelte';
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
    const permissionOptions: MembershipPermission[] = ['viewer', 'editor', 'family_admin'];

    let memberships: ParentMembership[] = [];
    let isLoading = false;
    let isBusy = false;
    let newEmail = '';
    let newPermission: MembershipPermission = 'editor';
    let lastLoadedFamilyId: string | null = null;
    let permissionPickerTarget: { mode: 'invite' } | { mode: 'membership'; membershipId: number } | null = null;

    $: familyId = $appStore.familyId;
    $: canManage = $appStore.permission === 'family_admin';

    onMount(() => {
        syncMembershipsWithSession();
    });

    afterUpdate(() => {
        syncMembershipsWithSession();
    });

    function syncMembershipsWithSession() {
        if (!canManage || !familyId) {
            memberships = [];
            lastLoadedFamilyId = null;
            return;
        }

        if (lastLoadedFamilyId === familyId || isLoading) {
            return;
        }

        lastLoadedFamilyId = familyId;
        void refreshMemberships(familyId);
    }

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

    function pickerTitle(): string {
        return permissionPickerTarget?.mode === 'invite'
            ? $i18n.t('app.parentAccess.permissionLabel')
            : $i18n.t('app.parentAccess.successUpdate');
    }

    function currentPickerPermission(): MembershipPermission {
        if (!permissionPickerTarget) {
            return newPermission;
        }

        if (permissionPickerTarget.mode === 'invite') {
            return newPermission;
        }

        const membershipId = permissionPickerTarget.membershipId;
        return memberships.find((entry) => entry.id === membershipId)?.permission ?? 'viewer';
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

    async function refreshMemberships(targetFamilyId = familyId) {
        if (!canManage || !targetFamilyId) {
            memberships = [];
            lastLoadedFamilyId = null;
            return;
        }

        isLoading = true;

        const result = await loadParentMemberships();
        if (result.ok) {
            memberships = result.data ?? [];
        } else {
            memberships = [];
            lastLoadedFamilyId = null;
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
                lastLoadedFamilyId = familyId ?? null;
                newEmail = '';
                newPermission = 'editor';
                showToast($i18n.t('app.parentAccess.successAdd'), 'success');
            } else {
                showToast($i18n.t('app.parentAccess.genericError'), 'error');
            }
        } else {
            showToast(errorMessage(result.errorCode, result.error), 'error');
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
                showToast($i18n.t('app.parentAccess.genericError'), 'error');
            }
        } else {
            showToast(errorMessage(result.errorCode, result.error), 'error');
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
            showToast(errorMessage(result.errorCode, result.error), 'error');
        }
        isBusy = false;
    }

    async function applyPermissionSelection(permission: MembershipPermission) {
        if (!permissionPickerTarget) {
            return;
        }

        if (permissionPickerTarget.mode === 'invite') {
            newPermission = permission;
            permissionPickerTarget = null;
            return;
        }

        const membershipId = permissionPickerTarget.membershipId;
        const membership = memberships.find((entry) => entry.id === membershipId);
        if (!membership || membership.permission === permission) {
            permissionPickerTarget = null;
            return;
        }

        membership.permission = permission;
        permissionPickerTarget = null;
        await saveMembership(membership);
    }

    function isLastFamilyAdmin(membership: ParentMembership): boolean {
        return membership.permission === 'family_admin'
            && memberships.filter((entry) => entry.permission === 'family_admin').length === 1;
    }

    function cannotRemoveMembership(membership: ParentMembership): boolean {
        return membership.permission === 'family_admin';
    }

    function openInvitePermissionPicker() {
        if (isBusy) {
            return;
        }
        permissionPickerTarget = { mode: 'invite' };
    }

    function openMembershipPermissionPicker(membership: ParentMembership) {
        if (isBusy || isLastFamilyAdmin(membership)) {
            return;
        }
        permissionPickerTarget = { mode: 'membership', membershipId: membership.id };
    }

    function closePermissionPicker() {
        permissionPickerTarget = null;
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

            <button
                class="parent-access__action-tile parent-access__action-tile--permission"
                type="button"
                on:click={openInvitePermissionPicker}
                disabled={isBusy}
                aria-haspopup="dialog"
                aria-expanded={permissionPickerTarget?.mode === 'invite'}
            >
                <span class="parent-access__action-tile-icon" aria-hidden="true">
                    {#if newPermission === 'viewer'}
                        <svg viewBox="0 0 24 24" class="parent-access__icon-svg">
                            <path d="M2.5 12s3.4-6 9.5-6 9.5 6 9.5 6-3.4 6-9.5 6-9.5-6-9.5-6Z"></path>
                            <circle cx="12" cy="12" r="3"></circle>
                        </svg>
                    {:else if newPermission === 'editor'}
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
                <span class="parent-access__action-tile-copy">
                    <span class="parent-access__action-tile-label">{$i18n.t('app.parentAccess.permissionLabel')}</span>
                    <span class="parent-access__action-tile-value">{permissionLabel(newPermission)}</span>
                </span>
            </button>

            <button
                class="parent-access__action-tile parent-access__action-tile--invite"
                id="parent-access-invite"
                type="button"
                on:click={inviteParent}
                disabled={isBusy}
                title={$i18n.t('app.parentAccess.inviteButton')}
                aria-label={$i18n.t('app.parentAccess.inviteButton')}
            >
                <span class="parent-access__action-tile-icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24" class="parent-access__icon-svg">
                        <path d="M3 11.5 20.5 4 14 21l-3.2-6.3L3 11.5Z"></path>
                        <path d="M10.8 14.7 20.5 4"></path>
                    </svg>
                </span>
                <span class="parent-access__action-tile-copy">
                    <span class="parent-access__action-tile-label">{$i18n.t('app.parentAccess.inviteButton')}</span>
                    <span class="parent-access__action-tile-value">{permissionLabel(newPermission)}</span>
                </span>
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
                        <div class="parent-access__row-top">
                            <div class="parent-access__email" title={membership.email}>{membership.email}</div>
                            <div class="parent-access__row-actions">
                                <button
                                    class="parent-access__icon-button parent-access__icon-button--permission"
                                    type="button"
                                    on:click={() => openMembershipPermissionPicker(membership)}
                                    disabled={isBusy || isLastFamilyAdmin(membership)}
                                    title={permissionLabel(membership.permission)}
                                    aria-label={`${$i18n.t('app.parentAccess.permissionLabel')} ${membership.email}`}
                                    aria-haspopup="dialog"
                                >
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
                                </button>

                                <button
                                    class="parent-access__icon-button parent-access__icon-button--danger"
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
                        </div>
                        <div class="parent-access__row-meta">
                            <span class="parent-access__status-tag">{statusLabel(membership.status)}</span>
                            <span class="parent-access__permission-name">{permissionLabel(membership.permission)}</span>
                        </div>
                    </article>
                {/each}
            {/if}
        </div>
    </div>

    {#if permissionPickerTarget}
        <div class="parent-access__picker" role="dialog" aria-modal="true" aria-labelledby="parent-access-picker-title">
            <button class="parent-access__picker-backdrop" type="button" aria-label="Close" on:click={closePermissionPicker}></button>
            <div class="parent-access__picker-sheet">
                <div class="parent-access__picker-header">
                    <div>
                        <p class="parent-access__picker-eyebrow">{$i18n.t('app.parentAccess.permissionLabel')}</p>
                        <h4 class="parent-access__picker-title" id="parent-access-picker-title">{pickerTitle()}</h4>
                    </div>
                    <button class="parent-access__picker-close" type="button" on:click={closePermissionPicker} aria-label="Close">
                        <svg viewBox="0 0 24 24" class="parent-access__icon-svg" aria-hidden="true">
                            <path d="M6 6 18 18"></path>
                            <path d="M18 6 6 18"></path>
                        </svg>
                    </button>
                </div>

                <div class="parent-access__picker-list">
                    {#each permissionOptions as permission (permission)}
                        <button
                            class="parent-access__picker-option"
                            class:parent-access__picker-option--active={currentPickerPermission() === permission}
                            type="button"
                            on:click={() => applyPermissionSelection(permission)}
                        >
                            <span class="parent-access__action-tile-icon" aria-hidden="true">
                                {#if permission === 'viewer'}
                                    <svg viewBox="0 0 24 24" class="parent-access__icon-svg">
                                        <path d="M2.5 12s3.4-6 9.5-6 9.5 6 9.5 6-3.4 6-9.5 6-9.5-6-9.5-6Z"></path>
                                        <circle cx="12" cy="12" r="3"></circle>
                                    </svg>
                                {:else if permission === 'editor'}
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
                            <span class="parent-access__picker-option-copy">
                                <span class="parent-access__picker-option-title">{permissionLabel(permission)}</span>
                                <span class="parent-access__picker-option-subtitle">{$i18n.t('app.parentAccess.permissionLabel')}</span>
                            </span>
                        </button>
                    {/each}
                </div>
            </div>
        </div>
    {/if}
</section>

<style>
    .parent-access {
        position: relative;
        display: grid;
        gap: 0.95rem;
        font-size: 0.92rem;
    }

    .parent-access__card {
        border: 1px solid rgba(0, 0, 0, 0.08);
        background: linear-gradient(180deg, rgba(13, 32, 54, 0.03), rgba(13, 32, 54, 0.02));
    }

    .parent-access__header {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        align-items: flex-start;
        gap: 0.9rem;
    }

    .parent-access__heading {
        min-width: 0;
    }

    .parent-access__eyebrow {
        margin: 0 0 0.22rem;
        font-size: 0.66rem;
        text-transform: uppercase;
        letter-spacing: 0.12em;
        opacity: 0.72;
    }

    .parent-access__count {
        width: 2.35rem;
        height: 2.35rem;
        display: grid;
        place-items: center;
        border-radius: 999px;
        background: rgba(0, 0, 0, 0.08);
        font-weight: 700;
    }

    .parent-access__form {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 0.55rem;
        margin-top: 0.7rem;
    }

    .parent-access__field {
        min-width: 0;
    }

    .parent-access__field--email {
        grid-column: 1 / -1;
    }

    .parent-access__input {
        width: 100%;
        min-height: 2.85rem;
    }

    .parent-access__action-tile {
        min-width: 0;
        min-height: 3.05rem;
        display: flex;
        align-items: center;
        gap: 0.68rem;
        padding: 0.72rem 0.88rem;
        border: 1px solid rgba(0, 0, 0, 0.08);
        border-radius: 16px;
        background: rgba(255, 255, 255, 0.82);
        color: #203550;
        text-align: left;
        transition: transform 140ms ease, background-color 140ms ease, box-shadow 140ms ease;
    }

    .parent-access__action-tile:hover:not(:disabled),
    .parent-access__picker-option:hover,
    .parent-access__picker-close:hover,
    .parent-access__icon-button:hover:not(:disabled) {
        transform: translateY(-1px);
        box-shadow: 0 8px 18px rgba(32, 53, 80, 0.12);
    }

    .parent-access__action-tile:disabled,
    .parent-access__icon-button:disabled {
        opacity: 0.55;
        cursor: not-allowed;
        box-shadow: none;
    }

    .parent-access__action-tile--invite {
        background: linear-gradient(135deg, rgba(47, 108, 242, 0.12), rgba(60, 183, 160, 0.18));
    }

    .parent-access__action-tile-icon {
        width: 2.08rem;
        height: 2.08rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: 999px;
        flex: none;
        background: rgba(13, 32, 54, 0.08);
    }

    .parent-access__action-tile--invite .parent-access__action-tile-icon {
        background: linear-gradient(135deg, #2f6cf2, #3cb7a0);
        color: #fff;
    }

    .parent-access__action-tile-copy {
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 0.1rem;
    }

    .parent-access__action-tile-label {
        font-size: 0.66rem;
        line-height: 1.1;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        opacity: 0.66;
    }

    .parent-access__action-tile-value {
        font-size: 0.9rem;
        font-weight: 600;
        line-height: 1.18;
    }

    .parent-access__list {
        display: grid;
        gap: 0.48rem;
        margin-top: 0.82rem;
    }

    .parent-access__empty {
        padding: 1rem;
        border-radius: 14px;
        background: rgba(255, 255, 255, 0.6);
        border: 1px dashed rgba(0, 0, 0, 0.12);
    }

    .parent-access__row {
        display: grid;
        gap: 0.3rem;
        padding: 0.66rem 0.8rem;
        border-radius: 16px;
        background: rgba(255, 255, 255, 0.74);
        border: 1px solid rgba(0, 0, 0, 0.08);
    }

    .parent-access__row-top {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        align-items: start;
        gap: 0.5rem;
        min-width: 0;
    }

    .parent-access__email {
        min-width: 0;
        font-size: 0.9rem;
        font-weight: 600;
        line-height: 1.28;
        overflow-wrap: anywhere;
        word-break: break-word;
    }

    .parent-access__row-actions {
        display: inline-flex;
        align-items: center;
        gap: 0.34rem;
        flex: none;
    }

    .parent-access__row-meta {
        display: flex;
        align-items: center;
        gap: 0.42rem;
        flex-wrap: wrap;
    }

    .parent-access__status-tag,
    .parent-access__permission-name {
        display: inline-flex;
        align-items: center;
        width: fit-content;
        padding: 0.14rem 0.48rem;
        border-radius: 999px;
        background: rgba(13, 32, 54, 0.08);
        font-size: 0.7rem;
        line-height: 1.15;
        white-space: nowrap;
    }

    .parent-access__permission-name {
        background: rgba(47, 108, 242, 0.08);
    }

    .parent-access__icon-button {
        width: 1.95rem;
        height: 1.95rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border: 0;
        border-radius: 999px;
        background: rgba(13, 32, 54, 0.08);
        color: #28405d;
        transition: transform 140ms ease, background-color 140ms ease, box-shadow 140ms ease;
    }

    .parent-access__icon-button--permission {
        background: linear-gradient(135deg, rgba(87, 121, 206, 0.12), rgba(84, 179, 160, 0.18));
    }

    .parent-access__icon-button--danger {
        background: rgba(230, 57, 70, 0.12);
        color: #8a1823;
    }

    .parent-access__icon-svg {
        width: 0.98rem;
        height: 0.98rem;
        fill: none;
        stroke: currentColor;
        stroke-width: 1.8;
        stroke-linecap: round;
        stroke-linejoin: round;
    }

    .parent-access__picker {
        position: fixed;
        inset: 0;
        z-index: 60;
        display: grid;
        place-items: end center;
        padding: 1rem;
    }

    .parent-access__picker-backdrop {
        position: absolute;
        inset: 0;
        border: 0;
        background: rgba(11, 17, 26, 0.45);
    }

    .parent-access__picker-sheet {
        position: relative;
        z-index: 1;
        width: min(28rem, 100%);
        padding: 1rem;
        border-radius: 22px;
        background: #fffaf7;
        box-shadow: 0 24px 50px rgba(20, 28, 40, 0.22);
        display: grid;
        gap: 0.85rem;
    }

    .parent-access__picker-header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 0.75rem;
    }

    .parent-access__picker-eyebrow {
        margin: 0 0 0.18rem;
        font-size: 0.66rem;
        text-transform: uppercase;
        letter-spacing: 0.1em;
        opacity: 0.68;
    }

    .parent-access__picker-title {
        margin: 0;
        font-size: 1rem;
        line-height: 1.2;
    }

    .parent-access__picker-close {
        width: 2rem;
        height: 2rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border: 0;
        border-radius: 999px;
        background: rgba(13, 32, 54, 0.08);
        color: #203550;
        transition: transform 140ms ease, box-shadow 140ms ease;
    }

    .parent-access__picker-list {
        display: grid;
        gap: 0.5rem;
    }

    .parent-access__picker-option {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        width: 100%;
        padding: 0.8rem 0.85rem;
        border: 1px solid rgba(0, 0, 0, 0.08);
        border-radius: 16px;
        background: rgba(255, 255, 255, 0.94);
        color: #203550;
        text-align: left;
        transition: transform 140ms ease, box-shadow 140ms ease, background-color 140ms ease;
    }

    .parent-access__picker-option--active {
        border-color: rgba(47, 108, 242, 0.28);
        background: linear-gradient(135deg, rgba(47, 108, 242, 0.08), rgba(60, 183, 160, 0.1));
    }

    .parent-access__picker-option-copy {
        display: flex;
        flex-direction: column;
        gap: 0.08rem;
    }

    .parent-access__picker-option-title {
        font-size: 0.92rem;
        font-weight: 600;
        line-height: 1.18;
    }

    .parent-access__picker-option-subtitle {
        font-size: 0.72rem;
        opacity: 0.65;
    }

    @media (max-width: 900px) {
        .parent-access {
            gap: 0.8rem;
            font-size: 0.88rem;
        }

        .parent-access__header {
            grid-template-columns: 1fr;
            gap: 0.7rem;
            margin-bottom: 0.45rem;
        }

        .parent-access__count {
            justify-self: start;
            width: 2.2rem;
            height: 2.2rem;
            font-size: 0.94rem;
        }

        .parent-access__form {
            gap: 0.45rem;
            margin-top: 0.55rem;
        }

        .parent-access__input,
        .parent-access__action-tile {
            min-height: 2.7rem;
        }

        .parent-access__action-tile {
            gap: 0.55rem;
            padding: 0.62rem 0.72rem;
        }

        .parent-access__action-tile-icon {
            width: 1.92rem;
            height: 1.92rem;
        }

        .parent-access__action-tile-value,
        .parent-access__email {
            font-size: 0.84rem;
            line-height: 1.22;
        }

        .parent-access__list {
            gap: 0.42rem;
            margin-top: 0.72rem;
        }

        .parent-access__row {
            padding: 0.6rem 0.68rem;
        }

        .parent-access__row-top {
            gap: 0.42rem;
        }

        .parent-access__row-actions {
            gap: 0.3rem;
        }

        .parent-access__icon-button {
            width: 2.05rem;
            height: 2.05rem;
        }

        .parent-access__status-tag,
        .parent-access__permission-name {
            font-size: 0.67rem;
        }

        .parent-access__picker {
            padding: 0.75rem;
        }

        .parent-access__picker-sheet {
            width: 100%;
            border-radius: 20px;
        }
    }
</style>
