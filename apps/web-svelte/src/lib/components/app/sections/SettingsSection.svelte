<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { adminSaveChildSettings, updateOwnNickname, adminGetChildLink, adminRegenerateChildLink } from '$lib/services/api';
    import { fetchWithCsrf } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';

    $: isAdmin = $appStore.isAdmin;
    $: childNickname = $appStore.childNickname ?? '';
    $: currentChildId = $appStore.currentChildId;

    let childNameInput = childNickname;
    let oldPassword = '';
    let newPassword = '';
    let childLink = '';
    let linkCopied = false;

    $: { childNameInput = $appStore.childNickname ?? ''; }

    async function saveProfile() {
        if (isAdmin) {
            await adminSaveChildSettings(currentChildId, { name: childNameInput });
        } else {
            await updateOwnNickname(childNameInput);
        }
        appStore.setState({ childNickname: childNameInput });
        showToast('Имя сохранено', 'success');
    }

    async function changePassword() {
        if (newPassword.length < 6) { showToast('Пароль минимум 6 символов', 'error'); return; }
        const res = await fetchWithCsrf('/api/change-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ oldPassword, newPassword }),
        });
        if (res.ok) {
            showToast('Пароль обновлён', 'success');
            oldPassword = '';
            newPassword = '';
        } else {
            showToast('Не удалось обновить пароль', 'error');
        }
    }

    async function loadChildLink() {
        const res = await adminGetChildLink(currentChildId) as { link: string } | null;
        if (res?.link) childLink = res.link;
    }

    async function regenerateLink() {
        const res = await adminRegenerateChildLink(currentChildId) as { link: string } | null;
        if (res?.link) {
            childLink = res.link;
            showToast('Ссылка обновлена', 'success');
        }
    }

    async function copyLink() {
        if (!childLink) await loadChildLink();
        await navigator.clipboard.writeText(childLink).catch(() => { /* */ });
        linkCopied = true;
        setTimeout(() => { linkCopied = false; }, 2000);
    }

    // Load child link on mount if admin
    import { onMount } from 'svelte';
    onMount(() => { if (isAdmin && currentChildId) void loadChildLink(); });
</script>

<section class="section" id="settings-section">
    <div class="section__header">
        <h2>Настройки</h2>
    </div>

    <div class="cards" id="settings-cards">
        {#if isAdmin}
        <!-- Admin: child name -->
        <div class="card settings-card admin-only requires-child">
            <div class="card__header">
                <h3 class="card__title">Настройки ребенка</h3>
                <div class="card__icon">
                    <span class="gamified-icon icon-child" aria-hidden="true"></span>
                </div>
            </div>
            <div class="form-group" style="margin-top: 1rem;">
                <label for="settings-child-name-inline">Имя ребенка</label>
                <input type="text" class="input" id="settings-child-name-inline"
                    placeholder="Имя" bind:value={childNameInput} />
            </div>
            <p class="hint" style="margin-top: 0.5rem;">Имя используется в личном кабинете и уведомлениях.</p>
            <div class="card__actions" style="margin-top: 1rem;">
                <button class="btn btn--primary btn--small" id="settings-save-profile-btn"
                    on:click={saveProfile}>Сохранить информацию</button>
            </div>
        </div>

        <!-- Admin: password change -->
        <div class="card settings-card admin-only">
            <div class="card__header">
                <h3 class="card__title">Безопасность (Смена пароля)</h3>
                <div class="card__icon">
                    <span class="gamified-icon icon-key" aria-hidden="true"></span>
                </div>
            </div>
            <div class="form-group" style="margin-top: 1rem;">
                <label for="settings-old-password-inline">Старый пароль</label>
                <input type="password" class="input" id="settings-old-password-inline"
                    placeholder="••••••" autocomplete="current-password" bind:value={oldPassword} />
            </div>
            <div class="form-group">
                <label for="settings-new-password-inline">Новый пароль (мин. 6 символов)</label>
                <input type="password" class="input" id="settings-new-password-inline"
                    placeholder="••••••" autocomplete="new-password" bind:value={newPassword} />
                <p class="hint" style="text-align: left; margin-top: 0.25rem;">Минимум 6 символов</p>
            </div>
            <div class="card__actions">
                <button class="btn btn--secondary" id="settings-save-password-btn"
                    style="width: 100%;" on:click={changePassword}>Обновить пароль</button>
            </div>
        </div>

        {:else}

        <!-- Child: nickname -->
        <div class="card settings-card child-only">
            <div class="card__header">
                <h3 class="card__title">Мой профиль</h3>
                <div class="card__icon">
                    <span class="gamified-icon icon-profile" aria-hidden="true"></span>
                </div>
            </div>
            <div class="form-group" style="margin-top: 1rem;">
                <label for="settings-nickname">Твой ник (уникальный, от 3 символов)</label>
                <input type="text" class="input" id="settings-nickname"
                    placeholder="Придумай крутой ник" bind:value={childNameInput} />
            </div>
            <div class="card__actions">
                <button class="btn btn--primary" id="settings-save-nickname-btn"
                    style="width: 100%;" on:click={saveProfile}>Сохранить ник</button>
            </div>
        </div>

        <!-- Child: theme -->
        <div class="card settings-card child-only">
            <div class="card__header">
                <h3 class="card__title">Тема интерфейса</h3>
                <div class="card__icon">
                    <span class="gamified-icon icon-palette" aria-hidden="true"></span>
                </div>
            </div>
            <p class="hint" style="margin-top: 0.5rem;">Выбери цветовую тему: можно менять в любой момент.</p>
            <div class="age-theme-switch" id="age-theme-switch" role="group" aria-label="Выбор возрастной темы">
                {#each ['mint', 'ocean', 'sun', 'coral', 'cosmos'] as theme (theme)}
                <button class="btn btn--secondary btn--small age-theme-switch__btn"
                    data-theme={theme} type="button"
                    on:click={() => document.documentElement.setAttribute('data-theme', theme)}>
                    <span class="age-theme-switch__swatch age-theme-switch__swatch--{theme}" aria-hidden="true"></span>
                    <span>{{mint: 'Мята', ocean: 'Океан', sun: 'Солнце', coral: 'Коралл', cosmos: 'Космос'}[theme]}</span>
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
        <h2>Доступ для ребенка</h2>
    </div>
    <div class="card admin-only">
        <div class="card__header">
            <h3 class="card__title">Прямая ссылка для входа</h3>
            <div class="card__icon">
                <span class="gamified-icon icon-link" aria-hidden="true"></span>
            </div>
        </div>
        <p class="card__comment" style="margin-bottom: 1.5rem;">
            Отправьте эту ссылку ребенку. По ней можно войти без пароля.
        </p>
        <div class="form-group">
            <div style="display: flex; gap: 0.5rem; flex-direction: column;">
                <input type="text" class="input" id="settings-child-link-input-inline"
                    readonly value={childLink}
                    style="background: rgba(0,0,0,0.2); font-family: monospace; font-size: 0.9rem;" />
                <div style="display: flex; gap: 0.5rem; margin-top: 0.5rem;">
                    <button class="btn btn--primary" id="settings-copy-link-btn"
                        style="flex: 2;" on:click={copyLink}>
                        {linkCopied ? 'Скопировано!' : 'Скопировать ссылку'}
                    </button>
                    <button class="btn btn--danger btn--small" id="settings-regenerate-link-btn"
                        style="flex: 1;" on:click={regenerateLink}>
                        Обновить
                    </button>
                </div>
            </div>
        </div>
    </div>
</section>
{/if}
