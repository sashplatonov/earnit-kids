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
            const res = await fetchWithCsrf('/api/admin/families');
            if (!res.ok) { familiesError = 'Ошибка загрузки'; return; }
            const d = await res.json() as Array<Record<string, unknown>>;
            families = d;
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
            const res = await fetchWithCsrf('/api/admin/system');
            if (res.ok) systemInfo = await res.json() as SystemInfo;
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
        const res = await fetchWithCsrf('/api/admin/families/block', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ familyId }),
        });
        if (res.ok) {
            families = families.map(f => f.id === familyId ? { ...f, blocked: true } : f);
        }
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
        return (Number(b.lastActive ?? 0)) - (Number(a.lastActive ?? 0));
    });

    $: filteredFamilies = sortedFamilies.filter(f => {
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

    <nav class="tabs" role="tablist" aria-label="Разделы административной панели">
        {#each [
            ['families', 'Семьи EarnIt Kids'],
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
        <section id="tab-families" class="tab-content active" role="tabpanel" aria-labelledby="tab-btn-families">
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
        {@const type = activeTab === 'catalog-tasks' ? 'tasks' : 'products'}
        {@const items = activeTab === 'catalog-tasks' ? catalogTasks : catalogProducts}
        <section id="tab-{activeTab}" class="tab-content active" role="tabpanel">
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
                                on:click={() => openEditModal(type as 'tasks' | 'products', idx)}>✏️</button>
                            <button class="btn-sm btn-del" type="button"
                                on:click={() => deleteCatalogItem(type as 'tasks' | 'products', idx)}>🗑️</button>
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
        </section>

        <!-- Database tab -->
        {:else if activeTab === 'database'}
        <section id="tab-database" class="tab-content active" role="tabpanel" aria-labelledby="tab-btn-database">
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
        </section>

        <!-- System tab -->
        {:else if activeTab === 'system'}
        <section id="tab-system" class="tab-content active" role="tabpanel" aria-labelledby="tab-btn-system">
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
        </section>
        {/if}
    </main>
</div>

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

