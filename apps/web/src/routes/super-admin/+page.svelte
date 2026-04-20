<script lang="ts">
    import type { PageData } from './$types';
    import { onMount } from 'svelte';
    import { fetchWithCsrf } from '$lib/services/api';

    export let data: PageData;

    type TabId = 'dashboard' | 'families' | 'catalog-tasks' | 'catalog-products' | 'database' | 'system';
    type StatusTone = 'success' | 'error' | 'info' | '';
    let activeTab: TabId = 'dashboard';

    const shortDateFormatter = new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: 'short' });
    const dateTimeFormatter = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short' });

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
    type CatalogItem = { id?: number; name: string; group?: string; category?: string; comment?: string; coins?: number; price?: number; age_min?: number; age_max?: number; frequency?: { limit: number; period: string } | null; money_limit?: number | null };
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
    let dbStatusType: StatusTone = '';
    let dbChecking = false;

    // System state
    type SystemInfo = { version?: string; uptime?: string; nodeVersion?: string; dbStatus?: string; memoryMB?: number; buildTs?: string; cpu?: string; memory?: string; uptimeSeconds?: number };
    let systemInfo: SystemInfo = {};
    let familyPassword = '';
    let familyPasswordConfirm = '';
    let familyPasswordStatus = '';
    let familyPasswordStatusType: StatusTone = '';
    let familyPasswordSaving = false;
    let superAdminOldPassword = '';
    let superAdminNewPassword = '';
    let superAdminConfirmPassword = '';
    let superAdminPasswordStatus = '';
    let superAdminPasswordStatusType: StatusTone = '';
    let superAdminPasswordSaving = false;

    function asObjectArray(value: unknown): Array<Record<string, unknown>> {
        return Array.isArray(value)
            ? value.filter((item): item is Record<string, unknown> => item != null && typeof item === 'object')
            : [];
    }

    function parseNumber(value: unknown): number {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : 0;
    }

    function toDate(value: unknown): Date | null {
        if (typeof value !== 'string' && !(value instanceof Date)) {
            return null;
        }
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? null : date;
    }

    function formatShortDate(value: unknown): string {
        const date = toDate(value);
        return date ? shortDateFormatter.format(date) : '—';
    }

    function formatDateTime(value: unknown): string {
        const date = toDate(value);
        return date ? dateTimeFormatter.format(date) : '—';
    }

    function familyLabel(family: Record<string, unknown>): string {
        return String(family.email ?? family.id ?? '—');
    }

    function previewChildren(family: Record<string, unknown>): string {
        const children = asObjectArray(family.children)
            .map((child) => String(child.name ?? '').trim())
            .filter(Boolean);
        if (children.length === 0) {
            return 'Профили ещё не добавлены';
        }
        if (children.length <= 2) {
            return children.join(', ');
        }
        return `${children.slice(0, 2).join(', ')} +${children.length - 2}`;
    }

    function messageFromPayload(payload: unknown, fallback: string): string {
        if (payload && typeof payload === 'object') {
            const detail = 'detail' in payload ? payload.detail : undefined;
            if (typeof detail === 'string' && detail.trim()) {
                return detail;
            }
            const error = 'error' in payload ? payload.error : undefined;
            if (typeof error === 'string' && error.trim()) {
                return error;
            }
        }
        return fallback;
    }

    function resetFamilyPasswordState() {
        familyPassword = '';
        familyPasswordConfirm = '';
        familyPasswordStatus = '';
        familyPasswordStatusType = '';
        familyPasswordSaving = false;
    }

    function resetSuperAdminPasswordState() {
        superAdminOldPassword = '';
        superAdminNewPassword = '';
        superAdminConfirmPassword = '';
        superAdminPasswordStatus = '';
        superAdminPasswordStatusType = '';
        superAdminPasswordSaving = false;
    }

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

    async function updateFamilyPassword() {
        if (!familyDetail) return;
        if (familyPassword.length < 6) {
            familyPasswordStatus = 'Пароль должен быть не короче 6 символов';
            familyPasswordStatusType = 'error';
            return;
        }
        if (familyPassword !== familyPasswordConfirm) {
            familyPasswordStatus = 'Подтверждение пароля не совпадает';
            familyPasswordStatusType = 'error';
            return;
        }

        familyPasswordSaving = true;
        familyPasswordStatus = 'Сохраняем пароль семьи...';
        familyPasswordStatusType = 'info';

        try {
            const res = await fetchWithCsrf(`/api/super/family/${familyDetail.familyId}/password`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ password: familyPassword }),
            });
            const payload = await res.json().catch(() => null);
            if (res.ok) {
                familyPasswordStatus = 'Пароль семьи обновлён';
                familyPasswordStatusType = 'success';
                familyPassword = '';
                familyPasswordConfirm = '';
            } else {
                familyPasswordStatus = messageFromPayload(payload, 'Не удалось обновить пароль семьи');
                familyPasswordStatusType = 'error';
            }
        } catch {
            familyPasswordStatus = 'Сеть недоступна';
            familyPasswordStatusType = 'error';
        } finally {
            familyPasswordSaving = false;
        }
    }

    async function updateSuperAdminPassword() {
        if (superAdminNewPassword.length < 6) {
            superAdminPasswordStatus = 'Новый пароль должен быть не короче 6 символов';
            superAdminPasswordStatusType = 'error';
            return;
        }
        if (superAdminNewPassword !== superAdminConfirmPassword) {
            superAdminPasswordStatus = 'Подтверждение нового пароля не совпадает';
            superAdminPasswordStatusType = 'error';
            return;
        }

        superAdminPasswordSaving = true;
        superAdminPasswordStatus = 'Обновляем пароль супер-админа...';
        superAdminPasswordStatusType = 'info';

        try {
            const res = await fetchWithCsrf('/api/super/system/password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ oldPassword: superAdminOldPassword, newPassword: superAdminNewPassword }),
            });
            const payload = await res.json().catch(() => null);
            if (res.ok) {
                superAdminPasswordStatus = 'Пароль супер-админа обновлён';
                superAdminPasswordStatusType = 'success';
                superAdminOldPassword = '';
                superAdminNewPassword = '';
                superAdminConfirmPassword = '';
            } else {
                superAdminPasswordStatus = messageFromPayload(payload, 'Не удалось обновить пароль супер-админа');
                superAdminPasswordStatusType = 'error';
            }
        } catch {
            superAdminPasswordStatus = 'Сеть недоступна';
            superAdminPasswordStatusType = 'error';
        } finally {
            superAdminPasswordSaving = false;
        }
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
        resetFamilyPasswordState();
        try {
            const res = await fetchWithCsrf(`/api/super/family/${familyId}/data`);
            if (!res.ok) { familyDetailError = 'Ошибка загрузки данных семьи'; familyDetailLoading = false; return; }
            familyDetail = await res.json() as FamilyDetail;
        } catch { familyDetailError = 'Сеть недоступна'; }
        finally { familyDetailLoading = false; }
    }

    function closeFamilyDetail() {
        familyDetail = null; familyDetailError = '';
        resetFamilyPasswordState();
    }

    function latestFamily(fams: Array<Record<string, unknown>>) {
        return fams.reduce<Record<string, unknown> | null>((best, f) => {
            if (!best) return f;
            const a = new Date(String(f.createdAt ?? f.created_at ?? 0)).getTime();
            const b = new Date(String(best.createdAt ?? best.created_at ?? 0)).getTime();
            return a > b ? f : best;
        }, null);
    }

    $: dashboardStats = (() => {
        if (families.length === 0) return null;
        const now = Date.now();
        const todayMidnight = new Date(); todayMidnight.setHours(0, 0, 0, 0);
        const weekAgoMs = now - 7 * 86400000;
        const monthAgoMs = now - 30 * 86400000;
        const actMs = (f: Record<string, unknown>) => toDate(f.lastActive ?? f.last_activity)?.getTime() ?? 0;
        const crtMs = (f: Record<string, unknown>) => toDate(f.createdAt ?? f.created_at)?.getTime() ?? 0;
        const activeToday = families.filter(f => actMs(f) >= todayMidnight.getTime()).length;
        const activeWeek = families.filter(f => actMs(f) >= weekAgoMs).length;
        const newWeek = families.filter(f => crtMs(f) >= weekAgoMs).length;
        const newMonth = families.filter(f => crtMs(f) >= monthAgoMs).length;
        const totalChildren = families.reduce((s, f) => s + parseNumber(f.childrenCount), 0);
        const totalTasks = families.reduce((s, f) => s + parseNumber(f.tasksCount), 0);
        const totalShop = families.reduce((s, f) => s + parseNumber(f.shopCount), 0);
        const withTasks = families.filter(f => parseNumber(f.tasksCount) > 0).length;
        const withShop = families.filter(f => parseNumber(f.shopCount) > 0).length;
        const avgTasks = (totalTasks / families.length).toFixed(1);
        const avgShop = (totalShop / families.length).toFixed(1);
        const recent = [...families]
            .filter(f => crtMs(f) > 0)
            .sort((a, b) => crtMs(b) - crtMs(a))
            .slice(0, 8);
        const topEngaged = [...families]
            .sort((a, b) => (parseNumber(b.tasksCount) + parseNumber(b.shopCount)) - (parseNumber(a.tasksCount) + parseNumber(a.shopCount)))
            .slice(0, 8);
        return { total: families.length, activeToday, activeWeek, newWeek, newMonth,
            blocked: blockedFamiliesCount, totalChildren, totalTasks, totalShop,
            withTasks, withShop, avgTasks, avgShop, recent, topEngaged };
    })();

    $: sortedFamilies = [...families].sort((a, b) => {
        if (familiesSort === 'created') {
            return new Date(String(b.createdAt ?? b.created_at ?? 0)).getTime() - new Date(String(a.createdAt ?? a.created_at ?? 0)).getTime();
        }
        return new Date(String(b.lastActive ?? b.last_activity ?? 0)).getTime() - new Date(String(a.lastActive ?? a.last_activity ?? 0)).getTime();
    });

    $: filteredFamilies = sortedFamilies.filter(f => {
        const q = familiesSearch.toLowerCase();
        const matchSearch = !q || String(f.email ?? '').toLowerCase().includes(q) || String(f.id ?? '').includes(q);
        const isBlocked = (f.isBlocked ?? f.blocked) === true;
        const matchStatus = familiesStatus === 'all'
            || (familiesStatus === 'blocked' && isBlocked)
            || (familiesStatus === 'active' && !isBlocked);
        return matchSearch && matchStatus;
    });
    $: blockedFamiliesCount = families.filter(f => (f.isBlocked ?? f.blocked) === true).length;

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
        <div class="super-admin-header__brand">
            <span class="super-admin-header__wordmark">EarnIt Kids</span>
            <span class="super-admin-header__badge">Admin</span>
        </div>
        <div class="super-admin-header__actions">
            {#if data.session.email}
            <span class="super-admin-header__identity">{data.session.email}</span>
            {/if}
            <button class="logout-btn" type="button" on:click={logout}>Выйти</button>
        </div>
    </header>

    <div class="tabs" role="tablist" aria-label="Разделы административной панели">
        {#each [
            ['dashboard', 'Обзор'],
            ['families', 'Семьи'],
            ['catalog-tasks', 'Задачи'],
            ['catalog-products', 'Товары'],
            ['database', 'БД'],
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
        <!-- Dashboard tab -->
        {#if activeTab === 'dashboard'}
        <div id="tab-dashboard" class="tab-content active" role="tabpanel" aria-labelledby="tab-btn-dashboard">
            {#if familiesLoading}
            <div class="panel-state panel-state--loading">Загрузка данных...</div>
            {:else if dashboardStats}
            <div class="sa-dashboard">
                <section class="sa-kpi-row">
                    <article class="sa-kpi-card">
                        <span class="sa-kpi-value">{dashboardStats.total}</span>
                        <span class="sa-kpi-label">Всего семей</span>
                    </article>
                    <article class="sa-kpi-card sa-kpi-card--active">
                        <span class="sa-kpi-value">{dashboardStats.activeToday}</span>
                        <span class="sa-kpi-label">Активны сегодня</span>
                    </article>
                    <article class="sa-kpi-card sa-kpi-card--week">
                        <span class="sa-kpi-value">{dashboardStats.activeWeek}</span>
                        <span class="sa-kpi-label">Активны за 7 дней</span>
                    </article>
                    <article class="sa-kpi-card sa-kpi-card--new">
                        <span class="sa-kpi-value">+{dashboardStats.newWeek}</span>
                        <span class="sa-kpi-label">Новых за 7 дней</span>
                        {#if dashboardStats.newMonth > dashboardStats.newWeek}
                        <span class="sa-kpi-delta">+{dashboardStats.newMonth} за 30 дн.</span>
                        {/if}
                    </article>
                    <article class="sa-kpi-card" class:sa-kpi-card--danger={dashboardStats.blocked > 0}>
                        <span class="sa-kpi-value">{dashboardStats.blocked}</span>
                        <span class="sa-kpi-label">Заблокировано</span>
                    </article>
                    <article class="sa-kpi-card sa-kpi-card--children">
                        <span class="sa-kpi-value">{dashboardStats.totalChildren}</span>
                        <span class="sa-kpi-label">Профилей детей</span>
                    </article>
                </section>

                <div class="sa-activity-grid">
                    <section class="sa-section">
                        <h3 class="sa-section__title">Последние регистрации</h3>
                        <ul class="sa-reg-list">
                            {#each dashboardStats.recent as fam (fam.id)}
                            <li class="sa-reg-item">
                                <div class="sa-reg-item__info">
                                    <strong>{familyLabel(fam)}</strong>
                                    <span>{previewChildren(fam)}</span>
                                </div>
                                <span class="sa-reg-item__date">{formatShortDate(fam.createdAt ?? fam.created_at)}</span>
                            </li>
                            {/each}
                        </ul>
                    </section>
                    <section class="sa-section">
                        <h3 class="sa-section__title">Топ по контенту</h3>
                        <ul class="sa-reg-list">
                            {#each dashboardStats.topEngaged as fam (fam.id)}
                            <li class="sa-reg-item">
                                <div class="sa-reg-item__info">
                                    <strong>{familyLabel(fam)}</strong>
                                    <span>{parseNumber(fam.childrenCount)} детей · активна {formatShortDate(fam.lastActive ?? fam.last_activity)}</span>
                                </div>
                                <span class="sa-reg-item__stats">
                                    <span class="sa-stat-chip sa-stat-chip--tasks">📋 {parseNumber(fam.tasksCount)}</span>
                                    <span class="sa-stat-chip sa-stat-chip--shop">🛒 {parseNumber(fam.shopCount)}</span>
                                </span>
                            </li>
                            {/each}
                        </ul>
                    </section>
                </div>

                <section class="sa-section">
                    <h3 class="sa-section__title">Использование платформы</h3>
                    <div class="sa-adoption-grid">
                        <div class="sa-adoption-row">
                            <span class="sa-adoption-label">Используют задания</span>
                            <div class="sa-adoption-bar-wrap">
                                <div class="sa-adoption-bar" style="width: {families.length > 0 ? (dashboardStats.withTasks / families.length * 100).toFixed(0) : 0}%"></div>
                            </div>
                            <span class="sa-adoption-pct">{families.length > 0 ? (dashboardStats.withTasks / families.length * 100).toFixed(0) : 0}% <span class="sa-adoption-count">({dashboardStats.withTasks}/{dashboardStats.total})</span></span>
                        </div>
                        <div class="sa-adoption-row">
                            <span class="sa-adoption-label">Используют магазин</span>
                            <div class="sa-adoption-bar-wrap">
                                <div class="sa-adoption-bar sa-adoption-bar--shop" style="width: {families.length > 0 ? (dashboardStats.withShop / families.length * 100).toFixed(0) : 0}%"></div>
                            </div>
                            <span class="sa-adoption-pct">{families.length > 0 ? (dashboardStats.withShop / families.length * 100).toFixed(0) : 0}% <span class="sa-adoption-count">({dashboardStats.withShop}/{dashboardStats.total})</span></span>
                        </div>
                        <div class="sa-adoption-meta">
                            <span>Ср. заданий / семья: <strong>{dashboardStats.avgTasks}</strong></span>
                            <span>Ср. товаров / семья: <strong>{dashboardStats.avgShop}</strong></span>
                            <span>Всего заданий: <strong>{dashboardStats.totalTasks}</strong></span>
                            <span>Всего товаров: <strong>{dashboardStats.totalShop}</strong></span>
                        </div>
                    </div>
                </section>
            </div>
            {:else}
            <div class="panel-state">Нет данных для отображения. <button class="btn btn--ghost btn--small" type="button" on:click={loadFamilies}>Обновить</button></div>
            {/if}
        </div>

        <!-- Families tab -->
        {:else if activeTab === 'families'}
        <div id="tab-families" class="tab-content active" role="tabpanel" aria-labelledby="tab-btn-families">
            <div class="ft-toolbar">
                <div class="ft-toolbar__search">
                    <input id="families-search" class="ft-search" type="search"
                        placeholder="Email или ID…"
                        bind:value={familiesSearch} />
                </div>
                <div class="ft-toolbar__filters">
                    <select id="families-status-select" class="ft-select" bind:value={familiesStatus}>
                        <option value="all">Все ({families.length})</option>
                        <option value="active">Активные ({families.length - blockedFamiliesCount})</option>
                        <option value="blocked">Заблок. ({blockedFamiliesCount})</option>
                    </select>
                    <button class="ft-sort-btn" class:ft-sort-btn--active={familiesSort === 'created'} type="button"
                        on:click={() => familiesSort = 'created'}>↓ Дата</button>
                    <button class="ft-sort-btn" class:ft-sort-btn--active={familiesSort === 'active'} type="button"
                        on:click={() => familiesSort = 'active'}>↓ Активность</button>
                </div>
                <span class="ft-toolbar__count">{filteredFamilies.length} из {families.length}</span>
            </div>

            {#if familiesLoading}
            <div class="panel-state panel-state--loading">Загрузка...</div>
            {:else if familiesError}
            <div class="panel-state panel-state--error" aria-live="polite">{familiesError}</div>
            {:else}
            <div class="ft-wrap">
                <table class="ft" aria-label="Список семей">
                    <thead>
                        <tr>
                            <th class="ft__th ft__th--email">Email / ID</th>
                            <th class="ft__th ft__th--status">Статус</th>
                            <th class="ft__th ft__th--num">Дети</th>
                            <th class="ft__th ft__th--num">Задания</th>
                            <th class="ft__th ft__th--num">Магазин</th>
                            <th class="ft__th ft__th--profiles">Профили</th>
                            <th class="ft__th ft__th--date">Создана</th>
                            <th class="ft__th ft__th--date">Активность</th>
                            <th class="ft__th ft__th--actions">Действия</th>
                        </tr>
                    </thead>
                    <tbody>
                        {#each filteredFamilies as family (family.id)}
                        {@const isFamilyBlocked = (family.isBlocked ?? family.blocked) === true}
                        <tr class="ft__row" class:ft__row--blocked={isFamilyBlocked}>
                            <td class="ft__td ft__td--email">
                                <span class="ft__email">{familyLabel(family)}</span>
                            </td>
                            <td class="ft__td ft__td--status">
                                {#if isFamilyBlocked}
                                <span class="ft__badge ft__badge--blocked">Заблокирована</span>
                                {:else}
                                <span class="ft__badge ft__badge--active">Активна</span>
                                {/if}
                            </td>
                            <td class="ft__td ft__td--num ft__td--center">{parseNumber(family.childrenCount)}</td>
                            <td class="ft__td ft__td--num ft__td--center">
                                <span class:ft__num--zero={parseNumber(family.tasksCount) === 0}>{parseNumber(family.tasksCount)}</span>
                            </td>
                            <td class="ft__td ft__td--num ft__td--center">
                                <span class:ft__num--zero={parseNumber(family.shopCount) === 0}>{parseNumber(family.shopCount)}</span>
                            </td>
                            <td class="ft__td ft__td--profiles">{previewChildren(family)}</td>
                            <td class="ft__td ft__td--date">{formatShortDate(family.createdAt ?? family.created_at)}</td>
                            <td class="ft__td ft__td--date">{formatShortDate(family.lastActive ?? family.last_activity)}</td>
                            <td class="ft__td ft__td--actions">
                                <button class="ft__action-btn ft__action-btn--open" type="button"
                                    on:click={() => openFamilyDetail(family.id)}>Открыть</button>
                                {#if isFamilyBlocked}
                                <button class="ft__action-btn ft__action-btn--unblock" type="button"
                                    on:click={() => unblockFamily(family.id)}>Разблокировать</button>
                                {:else}
                                <button class="ft__action-btn ft__action-btn--block" type="button"
                                    on:click={() => blockFamily(family.id)}>Заблокировать</button>
                                {/if}
                            </td>
                        </tr>
                        {/each}
                        {#if filteredFamilies.length === 0}
                        <tr><td colspan="9" class="ft__empty">Ничего не найдено</td></tr>
                        {/if}
                    </tbody>
                </table>
            </div>
            {/if}
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
                        <p class="db-card__value">Скачать дамп</p>
                        <p class="db-card__status">Создать полную копию PostgreSQL и скачать файл.</p>
                        <button id="pg-backup-btn" class="btn btn--primary" type="button"
                            on:click={triggerBackup}>Скачать бэкап</button>
                    </article>
                    <article class="db-card">
                        <p class="db-card__label">Восстановление</p>
                        <p class="db-card__value">Загрузить файл</p>
                        <p class="db-card__status">Загрузить файл резервной копии (.dump).</p>
                        <button id="pg-restore-btn" class="btn btn--success" type="button"
                            on:click={triggerRestoreClick}>Загрузить файл</button>
                        <input type="file" id="pg-restore-input" hidden accept=".dump"
                            on:change={handleRestoreChange} />
                    </article>
                    <article class="db-card">
                        <p class="db-card__label">Статус базы</p>
                        <p class="db-card__value">Проверка</p>
                        <p class="db-card__status">Backend: {data.appConfig.backendOrigin}</p>
                        <button class="btn btn--ghost" type="button" on:click={checkDbStatus}>Проверить</button>
                    </article>
                </div>
                {#if dbStatus}
                <div class="status-callout"
                    class:status-callout--success={dbStatusType === 'success'}
                    class:status-callout--error={dbStatusType === 'error'}
                    class:status-callout--info={dbStatusType === 'info'}
                    role="status">
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
                <div class="system-panel__details">
                    <article class="system-card system-card--form">
                        <p class="system-card__label">Безопасность</p>
                        <h3 class="system-card__heading">Смена пароля супер-админа</h3>
                        <div class="password-form-grid">
                            <div class="input-group">
                                <label for="super-admin-old-password">Текущий пароль</label>
                                <input id="super-admin-old-password" type="password" bind:value={superAdminOldPassword} autocomplete="current-password" />
                            </div>
                            <div class="input-group">
                                <label for="super-admin-new-password">Новый пароль</label>
                                <input id="super-admin-new-password" type="password" bind:value={superAdminNewPassword} autocomplete="new-password" />
                            </div>
                            <div class="input-group">
                                <label for="super-admin-confirm-password">Подтверждение</label>
                                <input id="super-admin-confirm-password" type="password" bind:value={superAdminConfirmPassword} autocomplete="new-password" />
                            </div>
                        </div>
                        {#if superAdminPasswordStatus}
                        <div class="status-callout"
                            class:status-callout--success={superAdminPasswordStatusType === 'success'}
                            class:status-callout--error={superAdminPasswordStatusType === 'error'}
                            class:status-callout--info={superAdminPasswordStatusType === 'info'}
                            role="status">
                            {superAdminPasswordStatus}
                        </div>
                        {/if}
                        <div class="password-panel__actions">
                            <button class="btn btn--primary" type="button" disabled={superAdminPasswordSaving}
                                on:click={updateSuperAdminPassword}>
                                {superAdminPasswordSaving ? 'Сохраняем...' : 'Сменить пароль'}
                            </button>
                        </div>
                    </article>
                    <article class="system-card system-card--details">
                        <p class="system-card__label">Подключение</p>
                        <h3 class="system-card__heading">Точки интеграции</h3>
                        <dl class="system-detail-list">
                            <div>
                                <dt>WS путь</dt>
                                <dd>{data.appConfig.wsPath}</dd>
                            </div>
                            <div>
                                <dt>Backend</dt>
                                <dd>{data.appConfig.backendOrigin}</dd>
                            </div>
                            <div>
                                <dt>Сервер</dt>
                                <dd>{systemInfo.version ?? '—'}</dd>
                            </div>
                            <div>
                                <dt>Обновлено</dt>
                                <dd>{formatDateTime(systemInfo.buildTs)}</dd>
                            </div>
                        </dl>
                    </article>
                </div>
            </article>
        </div>
        {/if}
    </main>
</div>

<!-- Family detail modal -->
{#if familyDetailLoading || familyDetail || familyDetailError}
<div class="modal" style="display:flex;" role="dialog" aria-modal="true" aria-label="Детали семьи">
    <div class="modal-content family-detail-modal">
        <button class="modal-close" type="button" on:click={closeFamilyDetail}>&times;</button>
        {#if familyDetailLoading}
            <div class="panel-state panel-state--loading">Загрузка данных семьи...</div>
        {:else if familyDetailError}
            <div class="panel-state panel-state--error">{familyDetailError}</div>
        {:else if familyDetail}
            {@const info = familyDetail.familyInfo}
            {@const children = asObjectArray(info.children)}
            {@const tasks = familyDetail.data.tasks ?? []}
            {@const shopItems = familyDetail.data.shop ?? []}
            {@const historyItems = familyDetail.data.history ?? []}
            {@const requestItems = familyDetail.data.requests ?? []}

            <header class="family-detail-header">
                <div class="family-detail-header__meta">
                    <strong class="family-detail-header__email">{String(info.email ?? familyDetail.familyId)}</strong>
                    <div class="family-detail-header__pills">
                        <span class="ft__badge" class:ft__badge--blocked={info.isBlocked === true} class:ft__badge--active={info.isBlocked !== true}>
                            {info.isBlocked === true ? 'Заблокирована' : 'Активна'}
                        </span>
                        <span class="fdc__chip">Создана {formatShortDate(info.created_at)}</span>
                        <span class="fdc__chip">Активность {formatShortDate(info.last_activity)}</span>
                        <span class="fdc__chip">Баланс: <strong>{parseNumber(familyDetail.data.balance)} монет</strong></span>
                        <span class="fdc__chip">Заданий: <strong>{tasks.length}</strong></span>
                        <span class="fdc__chip">Наград: <strong>{shopItems.length}</strong></span>
                    </div>
                </div>
            </header>

            <section class="family-detail-section password-panel">
                <div class="fdc__section-head">
                    <span class="fdc__section-label">Пароль семьи</span>
                    <span class="fdc__section-hint">Обновится сразу при следующем входе родителя.</span>
                </div>
                <div class="password-form-grid">
                    <div class="input-group">
                        <label for="family-password">Новый пароль</label>
                        <input id="family-password" type="password" bind:value={familyPassword} autocomplete="new-password" />
                    </div>
                    <div class="input-group">
                        <label for="family-password-confirm">Подтверждение</label>
                        <input id="family-password-confirm" type="password" bind:value={familyPasswordConfirm} autocomplete="new-password" />
                    </div>
                </div>
                {#if familyPasswordStatus}
                <div class="status-callout"
                    class:status-callout--success={familyPasswordStatusType === 'success'}
                    class:status-callout--error={familyPasswordStatusType === 'error'}
                    class:status-callout--info={familyPasswordStatusType === 'info'}
                    role="status">
                    {familyPasswordStatus}
                </div>
                {/if}
                <div class="password-panel__actions">
                    <button class="btn btn--primary" type="button" disabled={familyPasswordSaving}
                        on:click={updateFamilyPassword}>
                        {familyPasswordSaving ? 'Сохраняем...' : 'Установить пароль'}
                    </button>
                </div>
            </section>

            <div class="family-detail-columns">
                <section class="family-detail-section">
                    <div class="fdc__section-head">
                        <span class="fdc__section-label">Профили детей ({children.length})</span>
                    </div>
                    {#if children.length > 0}
                    <div class="fdc__children">
                        {#each children as child ((child as Record<string, unknown>).id)}
                        {@const c = child as Record<string, unknown>}
                        <div class="fdc__child-row">
                            <strong class="fdc__child-name">{String(c.name ?? '—')}</strong>
                            <span class="fdc__child-stat">{parseNumber(c.balance)} мон.</span>
                            <span class="fdc__child-stat">{parseNumber(c.monthly_limit)} EUR/мес</span>
                            <span class="fdc__child-stat">{parseNumber(c.daily_coin_limit)} мон./день</span>
                        </div>
                        {/each}
                    </div>
                    {:else}
                    <p class="panel-state">Профили не добавлены.</p>
                    {/if}
                </section>

                <section class="family-detail-section">
                    <div class="fdc__section-head">
                        <span class="fdc__section-label">Задания и награды</span>
                    </div>
                    <div class="family-detail-collections">
                        <div class="family-detail-collection">
                            <h4>Задания</h4>
                            {#if tasks.length > 0}
                            <ul class="family-detail-list">
                                {#each tasks.slice(0, 6) as task ((task as Record<string, unknown>).id)}
                                {@const t = task as Record<string, unknown>}
                                <li class="family-detail-list__item">
                                    <div>
                                        <strong>{String(t.name ?? '—')}</strong>
                                        <span>{String(t.group ?? 'Без группы')}</span>
                                    </div>
                                    <span>{parseNumber(t.coins)} мон.</span>
                                </li>
                                {/each}
                            </ul>
                            {:else}
                            <p class="panel-state">Задания не добавлены.</p>
                            {/if}
                        </div>
                        <div class="family-detail-collection">
                            <h4>Награды</h4>
                            {#if shopItems.length > 0}
                            <ul class="family-detail-list">
                                {#each shopItems.slice(0, 6) as item ((item as Record<string, unknown>).id)}
                                {@const shopItem = item as Record<string, unknown>}
                                <li class="family-detail-list__item">
                                    <div>
                                        <strong>{String(shopItem.name ?? '—')}</strong>
                                        <span>{String(shopItem.group ?? 'Без группы')}</span>
                                    </div>
                                    <span>{parseNumber(shopItem.price)} мон.</span>
                                </li>
                                {/each}
                            </ul>
                            {:else}
                            <p class="panel-state">Награды не добавлены.</p>
                            {/if}
                        </div>
                    </div>
                </section>
            </div>

            <div class="family-detail-columns">
                <section class="family-detail-section">
                    <div class="fdc__section-head">
                        <span class="fdc__section-label">Транзакции</span>
                    </div>
                    {#if historyItems.length > 0}
                    <ul class="family-detail-list">
                        {#each historyItems.slice(0, 8) as entry ((entry as Record<string, unknown>).id)}
                        {@const historyEntry = entry as Record<string, unknown>}
                        <li class="family-detail-list__item">
                            <div>
                                <strong>{String(historyEntry.action ?? historyEntry.type ?? '—')}</strong>
                                <span>{formatDateTime(historyEntry.timestamp ?? historyEntry.createdAt)}</span>
                            </div>
                            <span>{parseNumber(historyEntry.amount)} мон.</span>
                        </li>
                        {/each}
                    </ul>
                    {:else}
                    <p class="panel-state">Транзакций пока нет.</p>
                    {/if}
                </section>

                <section class="family-detail-section">
                    <div class="fdc__section-head">
                        <span class="fdc__section-label">Запросы</span>
                    </div>
                    {#if requestItems.length > 0}
                    <ul class="family-detail-list">
                        {#each requestItems.slice(0, 8) as req ((req as Record<string, unknown>).id)}
                        {@const requestItem = req as Record<string, unknown>}
                        <li class="family-detail-list__item">
                            <div>
                                <strong>{String(requestItem.taskName ?? requestItem.requestType ?? '—')}</strong>
                                <span>{String(requestItem.status ?? '—')}</span>
                            </div>
                            <span>{parseNumber(requestItem.coins)} мон.</span>
                        </li>
                        {/each}
                    </ul>
                    {:else}
                    <p class="panel-state">Открытых запросов нет.</p>
                    {/if}
                </section>
            </div>
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

