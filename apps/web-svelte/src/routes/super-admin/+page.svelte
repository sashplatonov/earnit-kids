<script lang="ts">
    import type { PageData } from './$types';
    import { onMount } from 'svelte';
    import { fetchWithCsrf } from '$lib/services/api';

    export let data: PageData;

    type TabId = 'families' | 'catalog-tasks' | 'catalog-products' | 'database' | 'system';
    let activeTab: TabId = 'families';

    // Families state
    let families: Array<Record<string, unknown>> = [];
    let familiesLoading = false;
    let familiesError = '';
    let familiesSearch = '';
    let familiesStatus = 'all';

    // System state
    type SystemInfo = { version?: string; uptime?: string; nodeVersion?: string; dbStatus?: string; memoryMB?: number; buildTs?: string };
    let systemInfo: SystemInfo = {};

    async function loadFamilies() {
        familiesLoading = true; familiesError = '';
        try {
            const res = await fetchWithCsrf('/api/admin/families');
            if (!res.ok) { familiesError = 'Ошибка загрузки'; return; }
            const data = await res.json() as Array<Record<string, unknown>>;
            families = data;
        } catch { familiesError = 'Сеть недоступна'; }
        finally { familiesLoading = false; }
    }

    async function loadSystem() {
        try {
            const res = await fetchWithCsrf('/api/admin/system');
            if (res.ok) systemInfo = await res.json() as SystemInfo;
        } catch { /* */ }
    }

    async function blockFamily(familyId: unknown) {
        if (!confirm(`Заблокировать семью ${familyId}?`)) return;
        const res = await fetchWithCsrf('/api/admin/families/block', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ familyId }),
        });
        if (res.ok) {
            families = families.map(f => f.id === familyId ? { ...f, blocked: true } : f);
        }
    }

    $: filteredFamilies = families.filter(f => {
        const q = familiesSearch.toLowerCase();
        const matchSearch = !q || String(f.email ?? '').toLowerCase().includes(q) || String(f.id ?? '').includes(q);
        const matchStatus = familiesStatus === 'all'
            || (familiesStatus === 'blocked' && f.blocked)
            || (familiesStatus === 'active' && !f.blocked);
        return matchSearch && matchStatus;
    });

    async function logout() {
        await fetchWithCsrf('/api/logout', { method: 'POST' });
        location.href = '/login.html';
    }

    onMount(() => {
        void loadFamilies();
        void loadSystem();
    });

    function switchTab(t: TabId) { activeTab = t; }
</script>

<svelte:head>
    <title>Административная панель — EarnIt Kids</title>
    <meta name="robots" content="noindex, nofollow" />
</svelte:head>

<div class="super-admin-shell">
    <header class="super-admin-header">
        <div>
            <p class="super-admin-header__eyebrow">EarnIt Kids — служебная зона</p>
            <h1 class="super-admin-header__title">Административная панель</h1>
            <p class="super-admin-header__subtitle">
                Управление семьями, каталогом и состоянием сайта.
            </p>
        </div>
        <div class="super-admin-header__actions">
            <button class="logout-btn" type="button" on:click={logout}>Выйти</button>
        </div>
    </header>

    <nav class="tabs" role="tablist" aria-label="Разделы административной панели">
        {#each [
            ['families', 'Семьи'],
            ['catalog-tasks', 'Каталог задач'],
            ['catalog-products', 'Каталог товаров'],
            ['database', 'База данных'],
            ['system', 'Система'],
        ] as [id, label] (id)}
        <button class="tab-btn" class:active={activeTab === id}
            data-tab={id} type="button" role="tab"
            aria-controls="tab-{id}" aria-selected={activeTab === id}
            on:click={() => switchTab(id as TabId)}>
            {label}
        </button>
        {/each}
    </nav>

    <main class="super-admin-panels">
        <!-- Families tab -->
        {#if activeTab === 'families'}
        <section id="tab-families" class="tab-content active" role="tabpanel">
            <article class="panel families-panel">
                <div class="panel__grid">
                    <article class="stat-card">
                        <p class="stat-card__label">Всего семей</p>
                        <p class="stat-card__value" id="total-families">{families.length || '-'}</p>
                    </article>
                </div>

                <div class="families-filters">
                    <input id="families-search" class="families-search-input" type="search"
                        placeholder="🔍 Поиск по email или ID..."
                        bind:value={familiesSearch} />
                    <select id="families-status-select" class="families-status-select"
                        bind:value={familiesStatus}>
                        <option value="all">Все статусы</option>
                        <option value="active">Активные</option>
                        <option value="blocked">Заблокированные</option>
                    </select>
                </div>

                {#if familiesLoading}
                <div class="panel-state panel-state--loading">Загрузка...</div>
                {:else if familiesError}
                <div class="panel-state panel-state--error" aria-live="polite">{familiesError}</div>
                {:else}
                <div id="families-list" class="families-list" aria-live="polite">
                    {#each filteredFamilies as family (family.id)}
                    <article class="family-row" class:family-row--blocked={family.blocked}>
                        <div class="family-row__info">
                            <strong>{family.email ?? family.id}</strong>
                            <span class="family-row__meta">ID: {family.id}</span>
                            {#if family.blocked}<span class="chip chip--danger">Заблокирован</span>{/if}
                        </div>
                        <div class="family-row__actions">
                            {#if !family.blocked}
                            <button class="btn btn--danger btn--small"
                                on:click={() => blockFamily(family.id)}>Заблокировать</button>
                            {/if}
                        </div>
                    </article>
                    {/each}
                    {#if filteredFamilies.length === 0}
                    <p class="panel-state">Ничего не найдено.</p>
                    {/if}
                </div>
                {/if}
            </article>
        </section>

        <!-- Catalog tabs -->
        {:else if activeTab === 'catalog-tasks' || activeTab === 'catalog-products'}
        <section id="tab-{activeTab}" class="tab-content active" role="tabpanel">
            <article class="panel catalog-panel">
                <p class="panel__eyebrow">Каталог</p>
                <h2>{activeTab === 'catalog-tasks' ? 'Задания' : 'Товары'}</h2>
                <p class="hint">Полное редактирование каталога реализуется в дальнейших обновлениях.</p>
            </article>
        </section>

        <!-- Database tab -->
        {:else if activeTab === 'database'}
        <section id="tab-database" class="tab-content active" role="tabpanel">
            <article class="panel">
                <h2>База данных</h2>
                <dl>
                    <dt>Backend</dt><dd>{data.appConfig.backendOrigin}</dd>
                    <dt>DB Status</dt><dd>{systemInfo.dbStatus ?? '—'}</dd>
                </dl>
            </article>
        </section>

        <!-- System tab -->
        {:else if activeTab === 'system'}
        <section id="tab-system" class="tab-content active" role="tabpanel">
            <article class="panel">
                <h2>Система</h2>
                <dl>
                    <dt>Версия</dt><dd>{systemInfo.version ?? '—'}</dd>
                    <dt>Uptime</dt><dd>{systemInfo.uptime ?? '—'}</dd>
                    <dt>Node</dt><dd>{systemInfo.nodeVersion ?? '—'}</dd>
                    <dt>Память</dt><dd>{systemInfo.memoryMB != null ? `${systemInfo.memoryMB} MB` : '—'}</dd>
                    <dt>WS путь</dt><dd>{data.appConfig.wsPath}</dd>
                </dl>
            </article>
        </section>
        {/if}
    </main>
</div>

