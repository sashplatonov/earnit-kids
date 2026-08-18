<script lang="ts">
    import { afterUpdate, onMount } from 'svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import {
        adminGetChildLink,
        adminRegenerateChildLink,
        adminSaveChildSettings,
        fetchWithCsrf,
        updateOwnNickname,
    } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';
    import ParentAccessManager from '$lib/components/app/settings/ParentAccessManager.svelte';
    import TelegramAccountConnections from '$lib/components/app/settings/TelegramAccountConnections.svelte';
    import type { SessionSnapshot } from '$lib/types/session';

    export let session: SessionSnapshot | null = null;

    const i18n = useI18n();
    const THEME_KEYS = ['mint', 'ocean', 'sun', 'coral', 'cosmos'] as const;

    function tAdmin(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`admin.${key}` as MessageKey, variables);
    }

    // EXPLAIN: Derive isAdmin from session.role first (available at SSR time),
    // EXPLAIN: then sync with appStore once client-side data loads. This ensures
    // EXPLAIN: the Dashboard card is visible on the very first SSR render.
    $: sessionIsAdmin = session?.role === 'admin' || session?.role === 'parent' || session?.role === 'super_admin';
    $: isAdmin = $appStore.isAdmin || sessionIsAdmin;

    // EXPLAIN: Diagnostic log forwarded to backend container logs to trace
    // EXPLAIN: admin visibility. Only logs on the client (browser).
    $: if (typeof window !== 'undefined') {
        console.info('[SettingsSection] isAdmin:', isAdmin, 'sessionIsAdmin:', sessionIsAdmin,
            'role:', session?.role, 'appStoreIsAdmin:', $appStore.isAdmin);
    }
    $: isLoading = $appStore.isLoading;
    $: childNickname = $appStore.childNickname ?? '';
    $: currentChildId = $appStore.currentChildId;
    $: membershipPermission = $appStore.permission;
    $: canEditFamilyData = membershipPermission === 'editor' || membershipPermission === 'family_admin';
    $: canManageParentAccess = membershipPermission === 'family_admin';
    $: isReadonlyParent = isAdmin && membershipPermission === 'viewer';

    let childNameInput = childNickname;
    let oldPassword = '';
    let newPassword = '';
    let childLink = '';
    let linkCopied = false;
    let childLinkOwnerId: string | number | null = null;

    $: { childNameInput = $appStore.childNickname ?? ''; }

    onMount(() => {
        if (!canEditFamilyData || currentChildId == null) return;

        childLinkOwnerId = currentChildId;
        void loadChildLink(currentChildId);
    });

    afterUpdate(() => {
        if (!canEditFamilyData || currentChildId == null || String(childLinkOwnerId) === String(currentChildId)) return;

        childLinkOwnerId = currentChildId;
        childLink = '';
        void loadChildLink(currentChildId);
    });

    async function saveProfile() {
        if (isAdmin) {
            if (!canEditFamilyData) {
                showToast(tAdmin('settings.passwordHint'), 'error');
                return;
            }
            await adminSaveChildSettings(currentChildId, { name: childNameInput });
        } else {
            await updateOwnNickname(childNameInput);
        }
        appStore.setState({ childNickname: childNameInput });
        showToast(tAdmin('settings.nameSavedToast'), 'success');
    }

    async function changePassword() {
        if (newPassword.length < 6) {
            showToast(tAdmin('settings.passwordTooShort'), 'error');
            return;
        }
        const res = await fetchWithCsrf('/api/change-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ oldPassword, newPassword }),
        });
        if (res.ok) {
            showToast(tAdmin('settings.passwordUpdated'), 'success');
            oldPassword = '';
            newPassword = '';
        } else {
            showToast(tAdmin('settings.passwordUpdateFailed'), 'error');
        }
    }

    async function loadChildLink(targetChildId = currentChildId) {
        const res = await adminGetChildLink(targetChildId) as { link: string } | null;
        if (res?.link && String(currentChildId) === String(targetChildId)) childLink = res.link;
    }

    async function regenerateLink() {
        const res = await adminRegenerateChildLink(currentChildId) as { link: string } | null;
        if (res?.link) {
            childLink = res.link;
            showToast(tAdmin('settings.linkUpdated'), 'success');
        }
    }

    async function copyLink() {
        if (!childLink) await loadChildLink();
        if (!childLink) {
            showToast(tAdmin('settings.linkFetchFailed'), 'error');
            return;
        }
        await navigator.clipboard.writeText(childLink).catch(() => { /* */ });
        linkCopied = true;
        setTimeout(() => { linkCopied = false; }, 2000);
    }
</script>

{#if isLoading}
<section class="section" id="settings-section">
    <div class="section__header">
        <h2>{tAdmin('settings.sectionTitle')}</h2>
    </div>
    <div class="cards cards--skeleton-settings" id="settings-skeleton">
        {#each { length: 2 } as _, i (i)}
        <div class="card settings-card card--skeleton-settings">
            <div class="card__header">
                <div class="skel-sett skel-sett--title">&nbsp;</div>
                <div class="skel-sett skel-sett--icon">&nbsp;</div>
            </div>
            <div class="form-group" style="margin-top: 1rem;">
                <div class="skel-sett skel-sett--label">&nbsp;</div>
                <div class="skel-sett skel-sett--input">&nbsp;</div>
            </div>
            <div class="card__actions" style="margin-top: 1rem;">
                <div class="skel-sett skel-sett--button">&nbsp;</div>
            </div>
        </div>
        {/each}
    </div>
</section>
{:else}
<section class="section" id="settings-section">
    <div class="section__header">
        <h2>{tAdmin('settings.sectionTitle')}</h2>
    </div>
    <div class="cards" id="settings-cards">
        {#if isAdmin}
            {#if isReadonlyParent}
                <div class="card settings-card">
                    <div class="card__header">
                        <h3 class="card__title">{$i18n.t('app.parentAccess.readOnlyTitle')}</h3>
                        <div class="card__icon">
                            <span class="gamified-icon icon-lock" aria-hidden="true"></span>
                        </div>
                    </div>
                    <p class="card__comment" style="margin-top: 0.5rem;">{$i18n.t('app.parentAccess.readOnlyText')}</p>
                </div>
            {/if}

            {#if canEditFamilyData}
                <div class="card settings-card admin-only requires-child">
                    <div class="card__header">
                        <h3 class="card__title">{tAdmin('settings.childSettingsTitle')}</h3>
                        <div class="card__icon">
                            <span class="gamified-icon icon-child" aria-hidden="true"></span>
                        </div>
                    </div>
                    <div class="form-group" style="margin-top: 1rem;">
                        <label for="settings-child-name-inline">{tAdmin('settings.childNameLabel')}</label>
                        <input type="text" class="input" id="settings-child-name-inline"
                            placeholder={tAdmin('settings.childNamePlaceholder')} bind:value={childNameInput} />
                    </div>
                    <p class="hint" style="margin-top: 0.5rem;">{tAdmin('settings.childNameHint')}</p>
                    <div class="card__actions" style="margin-top: 1rem;">
                        <button class="btn btn--primary btn--small" id="settings-save-profile-btn"
                            on:click={saveProfile}>{tAdmin('settings.saveInfo')}</button>
                    </div>
                </div>

                <div class="card settings-card admin-only">
                    <div class="card__header">
                        <h3 class="card__title">{tAdmin('settings.childAccessTitle')}</h3>
                        <div class="card__icon">
                            <span class="gamified-icon icon-link" aria-hidden="true"></span>
                        </div>
                    </div>
                    <p class="card__comment" style="margin-bottom: 1.5rem;">
                        {tAdmin('settings.directLinkHint')}
                    </p>
                    <div class="form-group">
                        <div style="display: flex; gap: 0.5rem; flex-direction: column;">
                            <input type="text" class="input" id="settings-child-link-input-inline"
                                readonly value={childLink}
                                style="background: rgba(0,0,0,0.2); font-family: monospace; font-size: 0.9rem;" />
                            <div style="display: flex; gap: 0.5rem; margin-top: 0.5rem;">
                                <button class="btn btn--primary" id="settings-copy-link-btn"
                                    style="flex: 2;" on:click={copyLink}>
                                    {linkCopied ? tAdmin('settings.copied') : tAdmin('settings.copyLink')}
                                </button>
                                <button class="btn btn--danger btn--small" id="settings-regenerate-link-btn"
                                    style="flex: 1;" on:click={regenerateLink}>
                                    {tAdmin('settings.regenerate')}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            {/if}

            <div class="card settings-card">
                <div class="card__header">
                    <h3 class="card__title">{tAdmin('settings.securityTitle')}</h3>
                    <div class="card__icon">
                        <span class="gamified-icon icon-key" aria-hidden="true"></span>
                    </div>
                </div>
                <div class="form-group" style="margin-top: 1rem;">
                    <label for="settings-old-password-inline">{tAdmin('settings.oldPassword')}</label>
                    <input type="password" class="input" id="settings-old-password-inline"
                        placeholder="••••••" autocomplete="current-password" bind:value={oldPassword} />
                </div>
                <div class="form-group">
                    <label for="settings-new-password-inline">{tAdmin('settings.newPassword')}</label>
                    <input type="password" class="input" id="settings-new-password-inline"
                        placeholder="••••••" autocomplete="new-password" bind:value={newPassword} />
                    <p class="hint" style="text-align: left; margin-top: 0.25rem;">{tAdmin('settings.passwordHint')}</p>
                </div>
                <div class="card__actions">
                    <button class="btn btn--secondary" id="settings-save-password-btn"
                        style="width: 100%;" on:click={changePassword}>{tAdmin('settings.updatePassword')}</button>
                </div>
            </div>

            <div class="card settings-card">
                <TelegramAccountConnections />
            </div>

            {#if isAdmin}
                <a href="/telegram/dashboard" class="card settings-card admin-only" style="text-decoration: none; color: inherit; display: block;">
                    <div class="card__header">
                        <h3 class="card__title">{$i18n.t('admin.settings.dashboardTitle')}</h3>
                        <div class="card__icon">
                            <span class="gamified-icon" aria-hidden="true">📊</span>
                        </div>
                    </div>
                    <p class="card__comment" style="margin-top: 0.5rem;">
                        {$i18n.t('admin.settings.dashboardDesc')}
                    </p>
                    <div class="card__actions" style="margin-top: 1rem;">
                        <span style="color: var(--primary, #5c6fe7); font-weight: 600;">
                            {$i18n.t('common.actions.open')} →
                        </span>
                    </div>
                </a>
            {/if}

            {#if canManageParentAccess}
                <ParentAccessManager />
            {/if}
        {:else}
            <div class="card settings-card child-only">
                <div class="card__header">
                    <h3 class="card__title">{tAdmin('settings.profileTitle')}</h3>
                    <div class="card__icon">
                        <span class="gamified-icon icon-profile" aria-hidden="true"></span>
                    </div>
                </div>
                <div class="form-group" style="margin-top: 1rem;">
                    <label for="settings-nickname">{tAdmin('settings.nicknameLabel')}</label>
                    <input type="text" class="input" id="settings-nickname"
                        placeholder={tAdmin('settings.nicknamePlaceholder')} bind:value={childNameInput} />
                </div>
                <div class="card__actions">
                    <button class="btn btn--primary" id="settings-save-nickname-btn"
                        style="width: 100%;" on:click={saveProfile}>{tAdmin('settings.saveNickname')}</button>
                </div>
            </div>

            <div class="card settings-card child-only">
                <div class="card__header">
                    <h3 class="card__title">{tAdmin('settings.themeTitle')}</h3>
                    <div class="card__icon">
                        <span class="gamified-icon icon-palette" aria-hidden="true"></span>
                    </div>
                </div>
                <p class="hint" style="margin-top: 0.5rem;">{tAdmin('settings.themeHint')}</p>
                <div class="age-theme-switch" id="age-theme-switch" role="group" aria-label={tAdmin('settings.themeAria')}>
                    {#each THEME_KEYS as theme (theme)}
                        <button class="btn btn--secondary btn--small age-theme-switch__btn"
                            data-theme={theme} type="button"
                            on:click={() => document.documentElement.setAttribute('data-theme', theme)}>
                            <span class="age-theme-switch__swatch age-theme-switch__swatch--{theme}" aria-hidden="true"></span>
                            <span>{tAdmin(`settings.themes.${theme}`)}</span>
                        </button>
                    {/each}
                </div>
            </div>
        {/if}
    </div>
</section>
{/if}

<style>
    .cards--skeleton-settings {
        pointer-events: none;
        user-select: none;
    }

    .card--skeleton-settings {
        background: var(--card-bg, #ffffff) !important;
        border-color: var(--card-border, rgba(0, 0, 0, 0.06)) !important;
    }

    .skel-sett {
        display: block;
        background: linear-gradient(90deg, #e8e8e8 25%, #f5f5f5 50%, #e8e8e8 75%);
        background-size: 200% 100%;
        animation: skel-sett-shimmer 1.5s ease-in-out infinite;
        border-radius: 6px;
        color: transparent !important;
    }

    .skel-sett--title {
        width: 60%;
        height: 1.2rem;
    }

    .skel-sett--icon {
        width: 2rem;
        height: 2rem;
        border-radius: 50%;
    }

    .skel-sett--label {
        width: 40%;
        height: 0.85rem;
        margin-bottom: 0.4rem;
    }

    .skel-sett--input {
        width: 100%;
        height: 2.4rem;
        border-radius: 8px;
    }

    .skel-sett--button {
        width: 8rem;
        height: 2.2rem;
        border-radius: 8px;
    }

    .card__header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 0.75rem;
    }

    @keyframes skel-sett-shimmer {
        0% { background-position: 200% 0; }
        100% { background-position: -200% 0; }
    }
</style>
