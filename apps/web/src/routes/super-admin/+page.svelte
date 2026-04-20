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
    let familiesSort = 'created';

    // Family detail state
    type FamilyDetail = {
        familyId: string;
        familyInfo: Record<string, unknown>;
        data: {
            balance?: number;
            tasks?: Array<Record<string, unknown>>;
            shop?: Array<Record<string, unknown>>;
            history?: Array<Record<string, unknown>>;
            requests?: Array<Record<string, unknown>>;
        };
    };
    let familyDetail: FamilyDetail | null = null;
    let familyDetailLoading = false;
    let familyDetailError = '';

    // Catalog state
    type CatalogItem = { id?: number; name: string; group?: string; category?: string; coins?: number; price?: number; age_min?: number; age_max?: number; frequency?: { limit: number; period: string } | null; money_limit?: number | null };
    let catalogTasks: CatalogItem[] = [];
    let catalogProducts: CatalogItem[] = [];
    let catalogLoading = false;
    let catalogError = '';
    let catalogSaveStatus = '';
    // Edit modal
    let editModalOpen = false;
    let editType: 'tasks' | 'products' = 'tasks';
    let editIndex = -1; // -1 = new
    let editName = '';
    let editGroup = '';
    let editCost = 0;
    let editAgeMin = 0;
    let editAgeMax = 18;
    let editFreqLimit = '';
    let editFreqPeriod = 'week';
    let editMoneyLimit = '';

    // Database state
    let dbStatus = '';
    let dbStatusType: 'success' | 'error' | 'info' | '' = '';
    let dbChecking = false;
    let restoreFile: FileList | null = null;

    // System state
    type SystemInfo = { version?: string; uptime?: string; nodeVersion?: string; dbStatus?: string; memoryMB?: number; buildTs?: string; cpu?: string; memory?: string; uptimeSeconds?: number };
    let systemInfo: SystemInfo = {};

    async function loadFamilies() {
        familiesLoading = true; familiesError = '';
        try {
            const res = await fetchWithCsrf('/api/super/families');
            if (!res.ok) { familiesError = 'Ошибка загрузки'; return; }
            const d = await res.json() as { families?: Array<Record<string, unknown>> } | Array<Record<string, unknown>>;
            families = Array.isArray(d) ? d : (d as { families?: Array<Record<string, unknown>> }).families ?? [];
        } catch { familiesError = 'Сеть недоступна'; }
        finally { familiesLoading = false; }
    }

    async function loadCatalog() {
        catalogLoading = true; catalogError = '';
        try {
            const res = await fetchWithCsrf('/api/super/base-data');
            if (!res.ok) { catalogError = 'Ошибка загрузки каталога'; return; }
            const d = await res.json() as { tasks?: CatalogItem[]; products?: CatalogItem[] };
            catalogTasks = d.tasks ?? [];
            catalogProducts = d.products ?? [];
        } catch { catalogError = 'Сеть недоступна'; }
        finally { catalogLoading = false; }
    }

    async function saveCatalog() {
        catalogSaveStatus = 'saving';
        try {
            const res = await fetchWithCsrf('/api/super/base-data', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ tasks: catalogTasks, products: catalogProducts }),
            });
            catalogSaveStatus = res.ok ? 'saved' : 'error';
            setTimeout(() => { catalogSaveStatus = ''; }, 2000);
        } catch { catalogSaveStatus = 'error'; }
    }

    function openEditModal(type: 'tasks' | 'products', idx: number) {
        editType = type;
        editIndex = idx;
        const items = type === 'tasks' ? catalogTasks : catalogProducts;
        const item = idx >= 0 ? items[idx] : null;
        editName = item?.name ?? '';
        editGroup = item?.group ?? item?.category ?? '';
        editCost = item?.coins ?? item?.price ?? 0;
        editAgeMin = item?.age_min ?? 0;
        editAgeMax = item?.age_max ?? 18;
        editFreqLimit = item?.frequency?.limit != null ? String(item.frequency.limit) : '';
        editFreqPeriod = item?.frequency?.period ?? 'week';
        editMoneyLimit = item?.money_limit != null ? String(item.money_limit) : '';
        editModalOpen = true;
    }

    function saveEditModal() {
        if (!editName.trim()) return;
        const fl = parseInt(editFreqLimit) || 0;
        const item: CatalogItem = {
            name: editName.trim(),
            group: editGroup.trim() || undefined,
            coins: editType === 'tasks' ? (Number(editCost) || 0) : undefined,
            price: editType === 'products' ? (Number(editCost) || 0) : undefined,
            age_min: Number(editAgeMin) || 0,
            age_max: Number(editAgeMax) || 18,
            frequency: fl > 0 ? { limit: fl, period: editFreqPeriod } : null,
            money_limit: editMoneyLimit ? (Number(editMoneyLimit) || null) : null,
        };
        if (editType === 'tasks') {
            if (editIndex >= 0) catalogTasks = catalogTasks.map((t, i) => i === editIndex ? { ...t, ...item } : t);
            else catalogTasks = [...catalogTasks, { ...item, id: Date.now() }];
        } else {
            if (editIndex >= 0) catalogProducts = catalogProducts.map((p, i) => i === editIndex ? { ...p, ...item } : p);
            else catalogProducts = [...catalogProducts, { ...item, id: Date.now() }];
        }
        editModalOpen = false;
        void saveCatalog();
    }

    function deleteCatalogItem(type: 'tasks' | 'products', idx: number) {
        if (!confirm('Удалить элемент каталога?')) return;
        if (type === 'tasks') catalogTasks = catalogTasks.filter((_, i) => i !== idx);
        else catalogProducts = catalogProducts.filter((_, i) => i !== idx);
        void saveCatalog();
    }

    async function loadSystem() {
        try {
            const res = await fetchWithCsrf('/api/super/system/overview');
            if (res.ok) {
                const d = await res.json() as { process?: { rssBytes?: number; heapUsedBytes?: number; uptimeSec?: number }; os?: { loadAvg1?: number; availableProcessors?: number }; timestamp?: string };
                const uptimeSec = d?.process?.uptimeSec ?? 0;
                const hours = Math.floor(uptimeSec / 3600);
                const mins = Math.floor((uptimeSec % 3600) / 60);
                const rss = d?.process?.rssBytes ?? 0;
                systemInfo = {
                    memoryMB: rss > 0 ? Math.round(rss / 1048576) : undefined,
                    uptime: uptimeSec > 0 ? `${hours}ч ${mins}мин` : undefined,
                    version: 'Java/Quarkus',
                    nodeVersion: undefined,
                    dbStatus: undefined,
                    cpu: d?.os?.loadAvg1 != null ? String(d.os.loadAvg1.toFixed(2)) : undefined,
                    buildTs: d?.timestamp,
                };
            }
        } catch { /* */ }
    }

    async function checkDbStatus() {
        dbChecking = true; dbStatus = 'Проверка...'; dbStatusType = 'info';
        try {
            const res = await fetchWithCsrf('/api/super/system/db');
            if (res.ok) {
                const d = await res.json() as { db?: { connected?: boolean; pingMs?: number; lastError?: string }; error?: string };
                if (d?.db?.connected) {
                    dbStatus = `Ping: ${d.db.pingMs ?? '—'}мс`;
                    dbStatusType = 'success';
                } else {
                    dbStatus = `Ошибка: ${d?.error || d?.db?.lastError || 'Нет связи'}`;
                    dbStatusType = 'error';
                }
            } else { dbStatus = 'Ошибка запроса'; dbStatusType = 'error'; }
        } catch { dbStatus = 'Сеть недоступна'; dbStatusType = 'error'; }
        finally { dbChecking = false; }
    }

    function triggerBackup() {
        window.location.href = '/api/super/db-backup';
    }

    function triggerRestoreClick() {
        document.getElementById('pg-restore-input')?.click();
    }

    async function handleRestoreChange(e: Event) {
        const input = e.target as HTMLInputElement;
        const file = input?.files?.[0];
        if (!file) return;
        input.value = '';
        if (!confirm('Восстановить базу из файла? Текущие данные будут заменены.')) return;
        dbStatus = 'Восстановление...'; dbStatusType = 'info';
        try {
            const res = await fetchWithCsrf('/api/super/db-restore', {
                method: 'POST',
                body: file,
                headers: { 'Content-Type': 'application/octet-stream' },
            });
            const d = await res.json() as { success?: boolean; error?: string };
            if (res.ok && d.success) {
                dbStatus = 'Успешно! Перезагрузка...'; dbStatusType = 'success';
                setTimeout(() => location.reload(), 2000);
            } else {
                dbStatus = `Ошибка: ${d.error ?? 'Unknown'}`; dbStatusType = 'error';
            }
        } catch { dbStatus = 'Ошибка связи'; dbStatusType = 'error'; }
    }

    async function blockFamily(familyId: unknown) {
        if (!confirm(`Заблокировать семью ${familyId}?`)) return;
        const res = await fetchWithCsrf(`/api/super/family/${familyId}/block`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ isBlocked: true }),
        });
        if (res.ok) {
            families = families.map(f => f.id === familyId ? { ...f, isBlocked: true } : f);
        }
    }

    async function unblockFamily(familyId: unknown) {
        if (!confirm(`Разблокировать семью ${familyId}?`)) return;
        const res = await fetchWithCsrf(`/api/super/family/${familyId}/block`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ isBlocked: false }),
        });
        if (res.ok) {
            families = families.map(f => f.id === familyId ? { ...f, isBlocked: false } : f);
        }
    }

    async function openFamilyDetail(familyId: unknown) {
        familyDetailLoading = true; familyDetailError = ''; familyDetail = null;
        try {
            const res = await fetchWithCsrf(`/api/super/family/${familyId}/data`);
            if (!res.ok) { familyDetailError = 'Ошибка загрузки данных семьи'; familyDetailLoading = false; return; }
            familyDetail = await res.json() as FamilyDetail;
        } catch { familyDetailError = 'Сеть недоступна'; }
        finally { familyDetailLoading = false; }
    }

    function closeFamilyDetail() {
        familyDetail = null; familyDetailError = '';
    }

    function latestFamily(fams: Array<Record<string, unknown>>) {
        return fams.reduce<Record<string, unknown> | null>((best, f) => {
            if (!best) return f;
            const a = new Date(String(f.createdAt ?? f.created_at ?? 0)).getTime();
            const b = new Date(String(best.createdAt ?? best.created_at ?? 0)).getTime();
            return a > b ? f : best;
        }, null);
    }

    $: sortedFamilies = [...families].sort((a, b) => {
        if (familiesSort === 'created') {
            return new Date(String(b.createdAt ?? b.created_at ?? 0)).getTime() - new Date(String(a.createdAt ?? a.created_at ?? 0)).getTime();
        }
        return new Date(String(b.lastActive ?? b.last_activity ?? 0)).getTime() - new Date(String(a.lastActive ?? a.last_activity ?? 0)).getTime();
    });

    $: filteredFamilies = sortedFamilies.filter(f => {
        const q = familiesSearch.toLowerCase();
        const matchSearch = !q || String(f.email ?? '').toLowerCase().includes(q) || String(f.id ?? '').includes(q);
        const isBlocked = Boolean(f.isBlocked ?? f.blocked);
        const matchStatus = familiesStatus === 'all'
            || (familiesStatus === 'blocked' && isBlocked)
            || (familiesStatus === 'active' && !isBlocked);
        return matchSearch && matchStatus;
    });
    $: blockedFamiliesCount = families.filter(f => Boolean(f.isBlocked ?? f.blocked)).length;

    async function logout() {
        await fetchWithCsrf('/api/logout', { method: 'POST' });
        location.href = '/login.html';
    }

    function switchTab(t: TabId) {
        activeTab = t;
        if (t === 'catalog-tasks' || t === 'catalog-products') {
            if (catalogTasks.length === 0 && catalogProducts.length === 0 && !catalogLoading) void loadCatalog();
        }
    }

    onMount(() => {
        void loadFamilies();
        void loadSystem();
    });
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
                Единое рабочее пространство для управления семьями, каталогом и состоянием сайта.
            </p>
        </div>
        <div class="super-admin-header__actions">
            <button class="logout-btn" type="button" on:click={logout}>Выйти</button>
        </div>
    </header>

    <div class="tabs" role="tablist" aria-label="Разделы административной панели">
        {#each [
            ['families', 'Семьи EarnIt Kids'],
            ['catalog-tasks', 'Каталог задач'],
            ['catalog-products', 'Каталог товаров'],
            ['database', 'База данных'],
            ['system', 'Система'],
        ] as [id, label] (id)}
        <button class="tab-btn" class:active={activeTab === id}
            id="tab-btn-{id}" data-tab={id} type="button" role="tab"
            aria-controls="tab-{id}" aria-selected={activeTab === id}
            on:click={() => switchTab(id as TabId)}>
            {label}
        </button>
        {/each}
    </div>

    <main class="super-admin-panels">
        <!-- Families tab -->
        {#if activeTab === 'families'}
        <div id="tab-families" class="tab-content active" role="tabpanel" aria-labelledby="tab-btn-families">
            <article class="panel families-panel">
                <div class="panel__grid">
                    <article class="stat-card">
                        <p class="stat-card__label">Всего семей</p>
                        <p class="stat-card__value" id="total-families">{families.length || '-'}</p>
                    </article>
                    <article class="stat-card stat-card--accent">
                        <p class="stat-card__label">Последняя регистрация</p>
                        <p class="stat-card__value" id="latest-family">
                            {#if families.length > 0}
                                {#if latestFamily(families)}
                                    {String(latestFamily(families)?.email ?? latestFamily(families)?.id ?? '—')}
                                {:else}—{/if}
                            {:else}-{/if}
                        </p>
                    </article>
                    <article class="stat-card">
                        <p class="stat-card__label">Нужно внимания</p>
                        <p class="stat-card__value" id="blocked-families">{blockedFamiliesCount}</p>
                        <p class="stat-card__hint">заблокированных семей</p>
                    </article>
                </div>

                <div class="families-filters" id="families-table-controls">
                    <input id="families-search" class="families-search-input" type="search"
                        placeholder="🔍 Поиск по email или ID..."
                        bind:value={familiesSearch} />
                    <select id="families-status-select" class="families-status-select"
                        bind:value={familiesStatus}>
                        <option value="all">Все статусы</option>
                        <option value="active">Активные</option>
                        <option value="blocked">Заблокированные</option>
                    </select>
                    <div class="chip-row">
                        <button class="filter-chip" class:active={familiesSort === 'active'} data-sort="active" type="button"
                            on:click={() => familiesSort = 'active'}>По активности</button>
                        <button class="filter-chip" class:active={familiesSort === 'created'} data-sort="created" type="button"
                            on:click={() => familiesSort = 'created'}>По созданию</button>
                    </div>
                </div>

                {#if familiesLoading}
                <div class="panel-state panel-state--loading" id="loading">Загрузка...</div>
                {:else if familiesError}
                <div class="panel-state panel-state--error" id="families-error" aria-live="polite">{familiesError}</div>
                {:else}
                <div id="families-list" class="families-list" aria-live="polite">
                    {#each filteredFamilies as family (family.id)}
                    {@const isFamilyBlocked = Boolean(family.isBlocked ?? family.blocked)}
                    <article class="family-row" class:family-row--blocked={isFamilyBlocked}>
                        <div class="family-row__info">
                            <strong>{family.email ?? family.id}</strong>
                            <span class="family-row__meta">ID: {family.id}</span>
                            <div class="family-row__facts">
                                {#if family.childrenCount != null}<span class="chip">{family.childrenCount} детей</span>{/if}
                                {#if family.lastActive ?? family.last_activity}<span class="chip">Активность: {String(family.lastActive ?? family.last_activity).slice(0, 10)}</span>{/if}
                                {#if isFamilyBlocked}<span class="chip chip--danger">Заблокирован</span>{/if}
                            </div>
                        </div>
                        <div class="family-row__actions">
                            <button class="btn btn--ghost btn--small"
                                on:click={() => openFamilyDetail(family.id)}>Детали</button>
                            {#if isFamilyBlocked}
                            <button class="btn btn--success btn--small"
                                on:click={() => unblockFamily(family.id)}>Разблокировать</button>
                            {:else}
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
        </div>

        <!-- Catalog tabs -->
        {:else if activeTab === 'catalog-tasks' || activeTab === 'catalog-products'}
        {@const type = activeTab === 'catalog-tasks' ? 'tasks' : 'products'}
        {@const items = activeTab === 'catalog-tasks' ? catalogTasks : catalogProducts}
        <div id="tab-{activeTab}" class="tab-content active" role="tabpanel" aria-labelledby={`tab-btn-${activeTab}`}>
            <article class="panel catalog-panel">
                <header class="panel__header">
                    <div>
                        <p class="panel__eyebrow">Каталог</p>
                        <h2>{activeTab === 'catalog-tasks' ? 'Базовые задания' : 'Базовые товары'}</h2>
                    </div>
                    <button class="btn btn--ghost" type="button"
                        on:click={() => openEditModal(type as 'tasks' | 'products', -1)}>
                        + {activeTab === 'catalog-tasks' ? 'Добавить задание' : 'Добавить товар'}
                    </button>
                </header>

                {#if catalogLoading}
                <div class="panel-state panel-state--loading" aria-live="polite">
                    Загрузка {activeTab === 'catalog-tasks' ? 'заданий' : 'товаров'}...
                </div>
                {:else if catalogError}
                <div class="panel-state panel-state--error" aria-live="polite">{catalogError}</div>
                {:else if items.length === 0}
                <div class="panel-state panel-state--empty" aria-live="polite">
                    {activeTab === 'catalog-tasks' ? 'Заданий пока нет' : 'Товаров пока нет'}
                </div>
                {:else}
                <div id="{activeTab === 'catalog-tasks' ? 'base-tasks-list' : 'base-products-list'}" class="items-grid" aria-live="polite">
                    {#each items as item, idx (item.id ?? idx)}
                    <div class="item-card">
                        <div class="item-header"><span>{item.name}</span></div>
                        <div class="item-meta" style="color: #6366f1; font-weight: 600;">
                            {item.coins ?? item.price ?? 0} 🪙
                            {#if item.frequency?.limit} ({item.frequency.limit}/{item.frequency.period}){/if}
                            {#if item.money_limit} | Лимит: {item.money_limit} 💶{/if}
                        </div>
                        <div class="item-meta">Возраст: {item.age_min ?? 0}–{item.age_max ?? 18} лет</div>
                        <div class="item-actions">
                            <button class="btn-sm btn-edit" type="button"
                                on:click={() => openEditModal(type as 'tasks' | 'products', idx)}>Изменить</button>
                            <button class="btn-sm btn-del" type="button"
                                on:click={() => deleteCatalogItem(type as 'tasks' | 'products', idx)}>Удалить</button>
                        </div>
                    </div>
                    {/each}
                </div>
                {/if}

                {#if catalogSaveStatus === 'saving'}
                <p class="panel-state panel-state--loading" aria-live="polite">Сохранение...</p>
                {:else if catalogSaveStatus === 'saved'}
                <p class="panel-state" aria-live="polite" style="color: #10b981;">Сохранено ✓</p>
                {:else if catalogSaveStatus === 'error'}
                <p class="panel-state panel-state--error" aria-live="polite">Ошибка сохранения</p>
                {/if}
            </article>
        </div>

        <!-- Database tab -->
        {:else if activeTab === 'database'}
        <div id="tab-database" class="tab-content active" role="tabpanel" aria-labelledby="tab-btn-database">
            <article class="panel db-panel">
                <header class="panel__header">
                    <div>
                        <p class="panel__eyebrow">Инфраструктура</p>
                        <h2>Управление базой данных</h2>
                    </div>
                </header>
                {#if dbChecking}
                <div class="panel-state panel-state--loading" id="db-panel-state">Проверяем доступность базы данных...</div>
                {/if}
                <div class="db-grid">
                    <article class="db-card">
                        <p class="db-card__label">Резервное копирование</p>
                        <p class="db-card__value">📦 Бэкап</p>
                        <p class="db-card__status">Создать полную копию PostgreSQL и скачать файл.</p>
                        <button id="pg-backup-btn" class="btn btn--primary" type="button"
                            on:click={triggerBackup}>Сделать бэкап</button>
                    </article>
                    <article class="db-card">
                        <p class="db-card__label">Восстановление</p>
                        <p class="db-card__value">🔄 Восстановить</p>
                        <p class="db-card__status">Загрузить файл резервной копии (.dump).</p>
                        <button id="pg-restore-btn" class="btn btn--success" type="button"
                            on:click={triggerRestoreClick}>Загрузить файл</button>
                        <input type="file" id="pg-restore-input" class="visually-hidden" accept=".dump"
                            on:change={handleRestoreChange} />
                    </article>
                    <article class="db-card">
                        <p class="db-card__label">Статус базы</p>
                        <p class="db-card__value">🔌 Ping</p>
                        <p class="db-card__status">Backend: {data.appConfig.backendOrigin}</p>
                        <button class="btn btn--ghost" type="button" on:click={checkDbStatus}>Проверить</button>
                    </article>
                </div>
                {#if dbStatus}
                <div class="status-callout" role="status"
                    style="background: {dbStatusType === 'success' ? '#dcfce7' : dbStatusType === 'error' ? '#fee2e2' : '#eff6ff'};
                           color: {dbStatusType === 'success' ? '#10b981' : dbStatusType === 'error' ? '#ef4444' : '#3b82f6'};
                           border: 1px solid {dbStatusType === 'success' ? '#86efac' : dbStatusType === 'error' ? '#fca5a5' : '#93c5fd'};">
                    {dbStatus}
                </div>
                {/if}
            </article>
        </div>

        <!-- System tab -->
        {:else if activeTab === 'system'}
        <div id="tab-system" class="tab-content active" role="tabpanel" aria-labelledby="tab-btn-system">
            <article class="panel system-panel" id="system-panel">
                <header class="panel__header">
                    <div>
                        <p class="panel__eyebrow">Системный дашборд</p>
                        <h2>Система и состояние</h2>
                    </div>
                </header>
                <div class="system-panel__grid" id="system-kpi-grid">
                    <article class="system-card">
                        <p class="system-card__label">Память</p>
                        <p class="system-card__value" id="system-memory-value">{systemInfo.memoryMB != null ? `${systemInfo.memoryMB} МБ` : '—'}</p>
                        <p class="system-card__helper">rss / heap</p>
                    </article>
                    <article class="system-card">
                        <p class="system-card__label">Uptime</p>
                        <p class="system-card__value" id="system-uptime-value">{systemInfo.uptime ?? '—'}</p>
                        <p class="system-card__helper">работает</p>
                    </article>
                    <article class="system-card">
                        <p class="system-card__label">База данных</p>
                        <p class="system-card__value" id="system-db-value">{systemInfo.dbStatus ?? '—'}</p>
                        <p class="system-card__helper">latency</p>
                    </article>
                    <article class="system-card">
                        <p class="system-card__label">Версия</p>
                        <p class="system-card__value">{systemInfo.version ?? '—'}</p>
                        <p class="system-card__helper">Node: {systemInfo.nodeVersion ?? '—'}</p>
                    </article>
                </div>
                <dl style="margin-top: 1.5rem;">
                    <dt>WS путь</dt><dd>{data.appConfig.wsPath}</dd>
                    <dt>Backend</dt><dd>{data.appConfig.backendOrigin}</dd>
                </dl>
            </article>
        </div>
        {/if}
    </main>
</div>

<!-- Family detail modal -->
{#if familyDetailLoading || familyDetail || familyDetailError}
<div class="modal" style="display:flex;" role="dialog" aria-modal="true" aria-label="Детали семьи">
    <div class="modal-content" style="max-width: 720px; max-height: 80vh; overflow-y: auto;">
        <button class="modal-close" type="button" on:click={closeFamilyDetail}>&times;</button>
        {#if familyDetailLoading}
            <div class="panel-state panel-state--loading">Загрузка данных семьи...</div>
        {:else if familyDetailError}
            <div class="panel-state panel-state--error">{familyDetailError}</div>
        {:else if familyDetail}
            {@const info = familyDetail.familyInfo}
            <h2>Семья: {String(info.email ?? familyDetail.familyId)}</h2>
            <dl style="display:grid; grid-template-columns: auto 1fr; gap: 0.25rem 1rem; margin-bottom:1.5rem;">
                <dt>ID</dt><dd>{familyDetail.familyId}</dd>
                <dt>Email</dt><dd>{String(info.email ?? '—')}</dd>
                <dt>Зарегистрирована</dt><dd>{String(info.created_at ?? '—')}</dd>
                <dt>Активность</dt><dd>{String(info.last_activity ?? '—')}</dd>
                <dt>Статус</dt><dd>{info.isBlocked ? '🔒 Заблокирована' : '✅ Активна'}</dd>
                <dt>Детей</dt><dd>{String(info.childrenCount ?? 0)}</dd>
            </dl>

            {#if Array.isArray(info.children) && info.children.length > 0}
            <h3>Дети</h3>
            <table style="width:100%; border-collapse:collapse; margin-bottom:1.5rem; font-size:0.875rem;">
                <thead><tr style="border-bottom:1px solid #e5e7eb;">
                    <th style="text-align:left; padding:0.5rem;">Имя</th>
                    <th style="text-align:right; padding:0.5rem;">Баланс</th>
                    <th style="text-align:right; padding:0.5rem;">Лимит/мес.</th>
                </tr></thead>
                <tbody>
                    {#each info.children as child ((child as Record<string,unknown>).id)}
                    {@const c = child as Record<string, unknown>}
                    <tr style="border-bottom:1px solid #f3f4f6;">
                        <td style="padding:0.5rem;">{String(c.name ?? '—')}</td>
                        <td style="text-align:right; padding:0.5rem;">{String(c.balance ?? 0)} 🪙</td>
                        <td style="text-align:right; padding:0.5rem;">{c.monthly_limit ? String(c.monthly_limit) + ' 💶' : '—'}</td>
                    </tr>
                    {/each}
                </tbody>
            </table>
            {/if}

            {#if familyDetail.data.tasks && familyDetail.data.tasks.length > 0}
            <h3>Задания ({familyDetail.data.tasks.length})</h3>
            <ul style="margin-bottom:1.5rem; padding-left:1.25rem; font-size:0.875rem;">
                {#each familyDetail.data.tasks as task ((task as Record<string,unknown>).id)}
                {@const t = task as Record<string, unknown>}
                <li>{String(t.name ?? '—')} — {String(t.coins ?? 0)} 🪙</li>
                {/each}
            </ul>
            {/if}

            {#if familyDetail.data.history && familyDetail.data.history.length > 0}
            <h3>История транзакций (последние {familyDetail.data.history.length})</h3>
            <table style="width:100%; border-collapse:collapse; margin-bottom:1.5rem; font-size:0.875rem;">
                <thead><tr style="border-bottom:1px solid #e5e7eb;">
                    <th style="text-align:left; padding:0.5rem;">Дата</th>
                    <th style="text-align:left; padding:0.5rem;">Действие</th>
                    <th style="text-align:right; padding:0.5rem;">Сумма</th>
                </tr></thead>
                <tbody>
                    {#each familyDetail.data.history as entry ((entry as Record<string,unknown>).id)}
                    {@const h = entry as Record<string, unknown>}
                    <tr style="border-bottom:1px solid #f3f4f6;">
                        <td style="padding:0.5rem; white-space:nowrap;">{String(h.timestamp ?? '—').slice(0, 10)}</td>
                        <td style="padding:0.5rem;">{String(h.action ?? h.type ?? '—')}</td>
                        <td style="text-align:right; padding:0.5rem;">{String(h.amount ?? 0)} 🪙</td>
                    </tr>
                    {/each}
                </tbody>
            </table>
            {/if}

            {#if familyDetail.data.requests && familyDetail.data.requests.length > 0}
            <h3>Запросы ({familyDetail.data.requests.length})</h3>
            <ul style="margin-bottom:1rem; padding-left:1.25rem; font-size:0.875rem;">
                {#each familyDetail.data.requests as req ((req as Record<string,unknown>).id)}
                {@const r = req as Record<string, unknown>}
                <li>[{String(r.status ?? '—')}] {String(r.taskName ?? r.requestType ?? '—')} — {String(r.coins ?? 0)} 🪙</li>
                {/each}
            </ul>
            {/if}
        {/if}
    </div>
</div>
<div class="modal-backdrop" on:click={closeFamilyDetail} role="presentation"></div>
{/if}

<!-- Catalog edit modal -->
{#if editModalOpen}
<div class="modal" id="edit-modal" style="display:flex;" role="dialog" aria-modal="true">
    <div class="modal-content modal-content--narrow">
        <button class="modal-close" type="button" on:click={() => editModalOpen = false}>&times;</button>
        <h2 id="edit-modal-title">{editIndex >= 0 ? 'Редактирование' : 'Добавить'}</h2>
        <div class="input-group">
            <label for="edit-name">Название</label>
            <input type="text" id="edit-name" bind:value={editName} />
        </div>
        <div class="input-group">
            <label for="edit-group">Группа</label>
            <input type="text" id="edit-group" bind:value={editGroup} />
        </div>
        <div class="input-group">
            <label for="edit-cost">{editType === 'tasks' ? 'Награда' : 'Цена'}</label>
            <input type="number" id="edit-cost" bind:value={editCost} />
        </div>
        <div class="input-group">
            <label for="edit-min">Возраст (мин)</label>
            <input type="number" id="edit-min" bind:value={editAgeMin} />
        </div>
        <div class="input-group">
            <label for="edit-max">Возраст (макс)</label>
            <input type="number" id="edit-max" bind:value={editAgeMax} />
        </div>
        <div style="display: flex; gap: 1rem; border-top: 1px solid #eee; padding-top: 1rem; margin-top: 1rem;">
            <div class="input-group" style="flex: 1">
                <label for="edit-limit">Лимит</label>
                <input type="number" id="edit-limit" bind:value={editFreqLimit} />
            </div>
            <div class="input-group" style="flex: 1">
                <label for="edit-period">Период</label>
                <select id="edit-period" bind:value={editFreqPeriod}>
                    <option value="day">В день</option>
                    <option value="week">В неделю</option>
                    <option value="month">В месяц</option>
                    <option value="year">В год</option>
                </select>
            </div>
        </div>
        <div class="input-group">
            <label for="edit-money-limit">Денежный лимит</label>
            <input type="number" id="edit-money-limit" bind:value={editMoneyLimit} />
        </div>
        <div style="display: flex; gap: 0.75rem; margin-top: 1rem;">
            {#if editIndex >= 0}
            <button class="btn btn--danger" type="button"
                on:click={() => { deleteCatalogItem(editType, editIndex); editModalOpen = false; }}>Удалить</button>
            {/if}
            <button class="save-btn btn btn--primary" type="button" on:click={saveEditModal}>Сохранить</button>
        </div>
    </div>
</div>
<div class="modal-backdrop" on:click={() => editModalOpen = false} role="presentation"></div>
{/if}

