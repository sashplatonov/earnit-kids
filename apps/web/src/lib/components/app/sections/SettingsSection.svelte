<script lang="ts">
    import { afterUpdate, onMount } from 'svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import { adminSaveChildSettings, updateOwnNickname, adminGetChildLink, adminRegenerateChildLink } from '$lib/services/api';
    import { fetchWithCsrf } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';

    const i18n = useI18n();
    const THEME_KEYS = ['mint', 'ocean', 'sun', 'coral', 'cosmos'] as const;

    function tAdmin(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`admin.${key}` as MessageKey, variables);
    }

    $: isAdmin = $appStore.isAdmin;
    $: childNickname = $appStore.childNickname ?? '';
    $: currentChildId = $appStore.currentChildId;

    let childNameInput = childNickname;
    let oldPassword = '';
    let newPassword = '';
    let childLink = '';
    let linkCopied = false;
    let childLinkOwnerId: string | number | null = null;

    $: { childNameInput = $appStore.childNickname ?? ''; }

    onMount(() => {
        if (!isAdmin || currentChildId == null) return;

        childLinkOwnerId = currentChildId;
        void loadChildLink(currentChildId);
    });

    afterUpdate(() => {
        if (!isAdmin || currentChildId == null || String(childLinkOwnerId) === String(currentChildId)) return;

        childLinkOwnerId = currentChildId;
        childLink = '';
        void loadChildLink(currentChildId);
    });

    async function saveProfile() {
        if (isAdmin) {
            await adminSaveChildSettings(currentChildId, { name: childNameInput });
        } else {
            await updateOwnNickname(childNameInput);
        }
        appStore.setState({ childNickname: childNameInput });
        showToast(tAdmin('settings.nameSavedToast'), 'success');
    }

    async function changePassword() {
        if (newPassword.length < 6) { showToast(tAdmin('settings.passwordTooShort'), 'error'); return; }
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

<section class="section" id="settings-section">
    <div class="section__header">
        <h2>{tAdmin('settings.sectionTitle')}</h2>
    </div>

    <div class="cards" id="settings-cards">
        {#if isAdmin}
        <!-- Admin: child name -->
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

        <!-- Admin: password change -->
        <div class="card settings-card admin-only">
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

        {:else}

        <!-- Child: nickname -->
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

        <!-- Child: theme -->
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

{#if isAdmin}
<!-- Child access link section -->
<section class="section" id="child-link-section">
    <div class="section__header">
        <h2>{tAdmin('settings.childAccessTitle')}</h2>
    </div>
    <div class="card admin-only">
        <div class="card__header">
            <h3 class="card__title">{tAdmin('settings.directLinkTitle')}</h3>
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
</section>
{/if}
