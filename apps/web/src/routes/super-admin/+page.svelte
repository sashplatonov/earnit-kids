<script lang="ts">
    import type { PageData } from './$types';
    import { onDestroy, onMount } from 'svelte';
    import { useI18n } from '$lib/i18n/context';
    import { fetchWithCsrf } from '$lib/services/api';
    import { startOfTodayTimestamp, toDate } from '$lib/utils/date';

    export let data: PageData;

    const i18n = useI18n();

    type TabId = 'dashboard' | 'families' | 'catalog-tasks' | 'catalog-products' | 'database' | 'system';
    type CatalogType = 'tasks' | 'products';
    type StatusTone = 'success' | 'error' | 'info' | '';
    type ResponsePayload = { success?: boolean; error?: string; detail?: string };

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

    type CatalogItem = {
        id?: number;
        name: string;
        group?: string;
        category?: string;
        comment?: string;
        coins?: number;
        price?: number;
        age_min?: number;
        age_max?: number;
        frequency?: { limit: number; period: string } | null;
        money_limit?: number | null;
    };

    type SystemInfo = {
        version?: string;
        uptime?: string;
        nodeVersion?: string;
        dbStatus?: string;
        memoryMB?: number;
        buildTs?: string;
        cpu?: string;
        memory?: string;
    };

    const EMPTY_VALUE = '—';

    let activeTab: TabId = 'dashboard';

    // Restore the previously active tab from session storage (survives page reloads)
    if (typeof sessionStorage !== 'undefined') {
        const saved = sessionStorage.getItem('superAdminActiveTab') as TabId | null;
        if (saved && ['dashboard', 'families', 'catalog-tasks', 'catalog-products', 'database', 'system'].includes(saved)) {
            activeTab = saved;
        }
    }

    let families: Array<Record<string, unknown>> = [];
    let familiesLoading = false;
    let familiesError = '';
    let familiesSearch = '';
    let familiesStatus = 'all';
    let familiesSort = 'created';

    let familyDetail: FamilyDetail | null = null;
    let familyDetailLoading = false;
    let familyDetailError = '';

    let catalogTasks: CatalogItem[] = [];
    let catalogProducts: CatalogItem[] = [];
    let catalogLoading = false;
    let catalogError = '';
    let catalogSaveStatus = '';

    let editModalOpen = false;
    let editType: CatalogType = 'tasks';
    let editIndex = -1;
    let editName = '';
    let editGroup = '';
    let editCost = 0;
    let editAgeMin = 0;
    let editAgeMax = 18;
    let editFreqLimit = '';
    let editFreqPeriod = 'week';
    let editMoneyLimit = '';

    let dbStatus = '';
    let dbStatusType: StatusTone = '';
    let dbChecking = false;
    let telegramScheduleMode: 'on' | 'off' = 'off';
    let telegramChatId = '';
    let telegramIntervalHours = '24';
    let telegramBackupRetentionCount = '20';
    let telegramBotToken = '';
    let telegramHasBotToken = false;
    let telegramConfigured = false;
    let telegramLastAttemptAt: string | null = null;
    let telegramLastSentAt: string | null = null;
    let telegramLastError: string | null = null;
    let telegramSettingsLoading = false;
    let backupHistoryLoading = false;
    let telegramSettingsSaving = false;
    let telegramBackupSending = false;
    let telegramSettingsStatus = '';
    let telegramSettingsStatusType: StatusTone = '';
    let backupHistory: Array<{ filename: string; sizeBytes: number; createdAt: string | null }> = [];

    let systemInfo: SystemInfo = {};
    let familyPassword = '';
    let familyPasswordConfirm = '';
    let familyPasswordStatus = '';
    let familyPasswordStatusType: StatusTone = '';
    let familyPasswordSaving = false;
    onMount(() => {
        if (typeof document !== 'undefined') {
            document.body.classList.add('super-admin-page');
        }

        void loadFamilies();
        void loadSystem();
        void loadTelegramBackupSettings();
        void loadBackupHistory();
    });

    onDestroy(() => {
        if (typeof document !== 'undefined') {
            document.body.classList.remove('super-admin-page');
        }
    });

    $: alternates = $i18n.alternates('/super-admin');

    $: tabItems = [
        ['dashboard', $i18n.t('superadmin.tabs.dashboard')],
        ['families', $i18n.t('superadmin.tabs.families')],
        ['catalog-tasks', $i18n.t('superadmin.tabs.catalogTasks')],
        ['catalog-products', $i18n.t('superadmin.tabs.catalogProducts')],
        ['database', $i18n.t('superadmin.tabs.database')],
        ['system', $i18n.t('superadmin.tabs.system')],
    ] as Array<[TabId, string]>;

    function asObjectArray(value: unknown): Array<Record<string, unknown>> {
        return Array.isArray(value)
            ? value.filter((item): item is Record<string, unknown> => item != null && typeof item === 'object')
            : [];
    }

    function parseNumber(value: unknown): number {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : 0;
    }

    function formatShortDate(value: unknown): string {
        const date = toDate(value);
        return date ? $i18n.formatShortDate(date) : EMPTY_VALUE;
    }

    function formatDateTime(value: unknown): string {
        const date = toDate(value);
        return date ? $i18n.formatDateTime(date) : EMPTY_VALUE;
    }

    function formatUptime(seconds: number): string {
        if (seconds <= 0) {
            return EMPTY_VALUE;
        }

        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        return $i18n.t('superadmin.system.uptimeValue', { hours, minutes });
    }

    function formatCatalogPeriod(period: string | undefined): string {
        switch (period) {
            case 'day':
                return $i18n.t('superadmin.catalog.periodDay');
            case 'week':
                return $i18n.t('superadmin.catalog.periodWeek');
            case 'month':
                return $i18n.t('superadmin.catalog.periodMonth');
            case 'year':
                return $i18n.t('superadmin.catalog.periodYear');
            default:
                return period ?? EMPTY_VALUE;
        }
    }

    function familyLabel(family: Record<string, unknown>): string {
        return String(family.email ?? family.id ?? EMPTY_VALUE);
    }

    function previewChildren(family: Record<string, unknown>): string {
        const children = asObjectArray(family.children)
            .map((child) => String(child.name ?? '').trim())
            .filter(Boolean);

        if (children.length === 0) {
            return $i18n.t('superadmin.families.previewEmpty');
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

    function catalogTitle(type: CatalogType): string {
        return type === 'tasks'
            ? $i18n.t('superadmin.catalog.baseTasks')
            : $i18n.t('superadmin.catalog.baseProducts');
    }

    function catalogAddLabel(type: CatalogType): string {
        return type === 'tasks'
            ? $i18n.t('superadmin.catalog.addTask')
            : $i18n.t('superadmin.catalog.addProduct');
    }

    function catalogLoadingLabel(type: CatalogType): string {
        return type === 'tasks'
            ? $i18n.t('superadmin.catalog.loadingTasks')
            : $i18n.t('superadmin.catalog.loadingProducts');
    }

    function catalogEmptyLabel(type: CatalogType): string {
        return type === 'tasks'
            ? $i18n.t('superadmin.catalog.emptyTasks')
            : $i18n.t('superadmin.catalog.emptyProducts');
    }

    function catalogCostLabel(type: CatalogType): string {
        return type === 'tasks'
            ? $i18n.t('superadmin.catalog.reward')
            : $i18n.t('superadmin.catalog.price');
    }

    function catalogModalTitle(): string {
        return editIndex >= 0
            ? $i18n.t('superadmin.catalog.editTitle')
            : $i18n.t('superadmin.catalog.addTitle');
    }

    function familyStatusLabel(isBlocked: boolean): string {
        return isBlocked
            ? $i18n.t('superadmin.families.statusBlocked')
            : $i18n.t('superadmin.families.statusActive');
    }

    async function loadFamilies() {
        familiesLoading = true;
        familiesError = '';

        try {
            const res = await fetchWithCsrf('/api/super/families');
            if (!res.ok) {
                familiesError = $i18n.t('superadmin.families.loadError');
                return;
            }

            const payload = await res.json() as { families?: Array<Record<string, unknown>> } | Array<Record<string, unknown>>;
            families = Array.isArray(payload) ? payload : payload.families ?? [];
        } catch {
            familiesError = $i18n.t('superadmin.states.networkUnavailable');
        } finally {
            familiesLoading = false;
        }
    }

    async function loadCatalog() {
        catalogLoading = true;
        catalogError = '';

        try {
            const res = await fetchWithCsrf('/api/super/base-data');
            if (!res.ok) {
                catalogError = $i18n.t('superadmin.states.requestError');
                return;
            }

            const payload = await res.json() as { tasks?: CatalogItem[]; products?: CatalogItem[] };
            catalogTasks = payload.tasks ?? [];
            catalogProducts = payload.products ?? [];
        } catch {
            catalogError = $i18n.t('superadmin.states.networkUnavailable');
        } finally {
            catalogLoading = false;
        }
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
            setTimeout(() => {
                catalogSaveStatus = '';
            }, 2000);
        } catch {
            catalogSaveStatus = 'error';
        }
    }

    function openEditModal(type: CatalogType, index: number) {
        editType = type;
        editIndex = index;

        const items = type === 'tasks' ? catalogTasks : catalogProducts;
        const item = index >= 0 ? items[index] : null;

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
        if (!editName.trim()) {
            return;
        }

        const frequencyLimit = parseInt(editFreqLimit, 10) || 0;
        const item: CatalogItem = {
            name: editName.trim(),
            group: editGroup.trim() || undefined,
            coins: editType === 'tasks' ? Number(editCost) || 0 : undefined,
            price: editType === 'products' ? Number(editCost) || 0 : undefined,
            age_min: Number(editAgeMin) || 0,
            age_max: Number(editAgeMax) || 18,
            frequency: frequencyLimit > 0 ? { limit: frequencyLimit, period: editFreqPeriod } : null,
            money_limit: editMoneyLimit ? Number(editMoneyLimit) || null : null,
        };

        if (editType === 'tasks') {
            if (editIndex >= 0) {
                catalogTasks = catalogTasks.map((task, currentIndex) => currentIndex === editIndex ? { ...task, ...item } : task);
            } else {
                catalogTasks = [...catalogTasks, { ...item, id: Date.now() }];
            }
        } else if (editIndex >= 0) {
            catalogProducts = catalogProducts.map((product, currentIndex) => currentIndex === editIndex ? { ...product, ...item } : product);
        } else {
            catalogProducts = [...catalogProducts, { ...item, id: Date.now() }];
        }

        editModalOpen = false;
        void saveCatalog();
    }

    function deleteCatalogItem(type: CatalogType, index: number) {
        if (!confirm($i18n.t('superadmin.catalog.deleteConfirm'))) {
            return;
        }

        if (type === 'tasks') {
            catalogTasks = catalogTasks.filter((_, currentIndex) => currentIndex !== index);
        } else {
            catalogProducts = catalogProducts.filter((_, currentIndex) => currentIndex !== index);
        }

        void saveCatalog();
    }

    async function loadSystem() {
        try {
            const res = await fetchWithCsrf('/api/super/system/overview');
            if (!res.ok) {
                return;
            }

            const payload = await res.json() as {
                process?: { rssBytes?: number; heapUsedBytes?: number; uptimeSec?: number };
                os?: { loadAvg1?: number; availableProcessors?: number };
                timestamp?: string;
            };
            const uptimeSec = payload?.process?.uptimeSec ?? 0;
            const rss = payload?.process?.rssBytes ?? 0;

            systemInfo = {
                memoryMB: rss > 0 ? Math.round(rss / 1048576) : undefined,
                uptime: formatUptime(uptimeSec),
                version: 'Java/Quarkus',
                nodeVersion: undefined,
                dbStatus: undefined,
                cpu: payload?.os?.loadAvg1 != null ? String(payload.os.loadAvg1.toFixed(2)) : undefined,
                buildTs: payload?.timestamp,
            };
        } catch {
            // ignored: the panel keeps empty placeholders when the overview request fails
        }
    }

    async function checkDbStatus() {
        dbChecking = true;
        dbStatus = $i18n.t('superadmin.states.loading');
        dbStatusType = 'info';

        try {
            const res = await fetchWithCsrf('/api/super/system/db');
            if (!res.ok) {
                dbStatus = $i18n.t('superadmin.states.requestError');
                dbStatusType = 'error';
                return;
            }

            const payload = await res.json() as { db?: { connected?: boolean; pingMs?: number; lastError?: string }; error?: string };
            if (payload?.db?.connected) {
                dbStatus = $i18n.t('superadmin.database.pingStatus', { ping: payload.db.pingMs ?? EMPTY_VALUE });
                dbStatusType = 'success';
                return;
            }

            dbStatus = $i18n.t('superadmin.states.errorPrefix', {
                message: payload?.error || payload?.db?.lastError || $i18n.t('superadmin.database.noConnection'),
            });
            dbStatusType = 'error';
        } catch {
            dbStatus = $i18n.t('superadmin.states.networkUnavailable');
            dbStatusType = 'error';
        } finally {
            dbChecking = false;
        }
    }

    function applyTelegramBackupSettings(payload: unknown) {
        if (!payload || typeof payload !== 'object') {
            telegramScheduleMode = 'off';
            telegramChatId = '';
            telegramIntervalHours = '24';
            telegramBackupRetentionCount = '20';
            telegramHasBotToken = false;
            telegramConfigured = false;
            telegramLastAttemptAt = null;
            telegramLastSentAt = null;
            telegramLastError = null;
            return;
        }

        const record = payload as Record<string, unknown>;
        telegramScheduleMode = record.enabled === true ? 'on' : 'off';
        telegramChatId = typeof record.chatId === 'string' ? record.chatId : '';
        telegramIntervalHours = String(parseNumber(record.intervalHours) || 24);
        telegramBackupRetentionCount = String(parseNumber(record.backupRetentionCount) || 20);
        telegramHasBotToken = record.hasBotToken === true;
        telegramConfigured = record.configured === true;
        telegramLastAttemptAt = typeof record.lastAttemptAt === 'string' ? record.lastAttemptAt : null;
        telegramLastSentAt = typeof record.lastSentAt === 'string' ? record.lastSentAt : null;
        telegramLastError = typeof record.lastError === 'string' ? record.lastError : null;
    }

    async function loadBackupHistory() {
        backupHistoryLoading = true;

        try {
            const res = await fetchWithCsrf('/api/super/db-backup/history');
            const payload = await res.json().catch(() => null) as { backups?: Array<{ filename?: string; sizeBytes?: number; createdAt?: string | null }> } | null;
            if (res.ok) {
                backupHistory = (payload?.backups ?? []).map((item) => ({
                    filename: String(item.filename ?? ''),
                    sizeBytes: parseNumber(item.sizeBytes),
                    createdAt: typeof item.createdAt === 'string' ? item.createdAt : null,
                }));
                return;
            }

            telegramSettingsStatus = messageFromPayload(payload, $i18n.t('superadmin.database.loadSettingsError'));
            telegramSettingsStatusType = 'error';
        } catch {
            telegramSettingsStatus = $i18n.t('superadmin.states.networkUnavailable');
            telegramSettingsStatusType = 'error';
        } finally {
            backupHistoryLoading = false;
        }
    }

    async function loadTelegramBackupSettings() {
        telegramSettingsLoading = true;

        try {
            const res = await fetchWithCsrf('/api/super/db-backup/telegram-settings');
            const payload = await res.json().catch(() => null);
            if (res.ok) {
                applyTelegramBackupSettings(payload);
                return;
            }

            telegramSettingsStatus = messageFromPayload(payload, $i18n.t('superadmin.database.loadSettingsError'));
            telegramSettingsStatusType = 'error';
        } catch {
            telegramSettingsStatus = $i18n.t('superadmin.states.networkUnavailable');
            telegramSettingsStatusType = 'error';
        } finally {
            telegramSettingsLoading = false;
        }
    }

    async function saveTelegramBackupSettings() {
        const intervalHours = parseInt(telegramIntervalHours, 10) || 0;
        const backupRetentionCount = parseInt(telegramBackupRetentionCount, 10) || 0;
        if (intervalHours < 1 || intervalHours > 720) {
            telegramSettingsStatus = $i18n.t('superadmin.database.intervalError');
            telegramSettingsStatusType = 'error';
            return;
        }
        if (backupRetentionCount < 1 || backupRetentionCount > 500) {
            telegramSettingsStatus = $i18n.t('superadmin.states.errorPrefix', { message: '1..500' });
            telegramSettingsStatusType = 'error';
            return;
        }

        telegramSettingsSaving = true;
        telegramSettingsStatus = $i18n.t('superadmin.database.settingsSaving');
        telegramSettingsStatusType = 'info';

        try {
            const body: Record<string, unknown> = {
                enabled: telegramScheduleMode === 'on',
                chatId: telegramChatId,
                intervalHours,
                backupRetentionCount,
            };
            if (telegramBotToken !== '') {
                body.botToken = telegramBotToken;
            }

            const res = await fetchWithCsrf('/api/super/db-backup/telegram-settings', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            });
            const payload = await res.json().catch(() => null);

            if (res.ok) {
                applyTelegramBackupSettings(payload);
                telegramBotToken = '';
                telegramSettingsStatus = $i18n.t('superadmin.database.settingsSaved');
                telegramSettingsStatusType = 'success';
                return;
            }

            telegramSettingsStatus = messageFromPayload(payload, $i18n.t('superadmin.database.settingsSaveError'));
            telegramSettingsStatusType = 'error';
        } catch {
            telegramSettingsStatus = $i18n.t('superadmin.states.networkUnavailable');
            telegramSettingsStatusType = 'error';
        } finally {
            telegramSettingsSaving = false;
        }
    }

    function formatFileSize(sizeBytes: number): string {
        if (sizeBytes < 1024) {
            return `${sizeBytes} B`;
        }
        if (sizeBytes < 1024 * 1024) {
            return `${(sizeBytes / 1024).toFixed(1)} KB`;
        }
        return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
    }

    async function restoreBackupFromHistory(filename: string) {
        if (!confirm($i18n.t('superadmin.database.backupRestoreConfirm', { filename }))) {
            return;
        }

        dbStatus = $i18n.t('superadmin.database.restoreInProgress');
        dbStatusType = 'info';

        try {
            const res = await fetchWithCsrf(`/api/super/db-restore/history/${encodeURIComponent(filename)}`, {
                method: 'POST',
            });
            const payload = await res.json().catch(() => ({})) as ResponsePayload;
            if (res.ok && payload.success) {
                dbStatus = $i18n.t('superadmin.database.backupRestoreSuccess');
                dbStatusType = 'success';
                setTimeout(() => location.reload(), 2000);
                return;
            }
            dbStatus = $i18n.t('superadmin.states.errorPrefix', {
                message: messageFromPayload(payload, $i18n.t('superadmin.states.unknown')),
            });
            dbStatusType = 'error';
        } catch {
            dbStatus = $i18n.t('superadmin.database.connectionError');
            dbStatusType = 'error';
        }
    }

    async function sendBackupToTelegram() {
        telegramBackupSending = true;
        telegramSettingsStatus = $i18n.t('superadmin.database.backupSending');
        telegramSettingsStatusType = 'info';

        try {
            const res = await fetchWithCsrf('/api/super/db-backup/send-telegram', {
                method: 'POST',
            });
            const payload = await res.json().catch(() => null);

            if (res.ok) {
                await loadTelegramBackupSettings();
                await loadBackupHistory();
                telegramSettingsStatus = $i18n.t('superadmin.database.backupSent');
                telegramSettingsStatusType = 'success';
                return;
            }

            telegramSettingsStatus = messageFromPayload(payload, $i18n.t('superadmin.database.backupSendError'));
            telegramSettingsStatusType = 'error';
        } catch {
            telegramSettingsStatus = $i18n.t('superadmin.states.networkUnavailable');
            telegramSettingsStatusType = 'error';
        } finally {
            telegramBackupSending = false;
        }
    }

    function triggerBackup() {
        window.location.href = '/api/super/db-backup';
    }

    function triggerRestoreClick() {
        document.getElementById('pg-restore-input')?.click();
    }

    async function handleRestoreChange(event: Event) {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file) {
            return;
        }

        input.value = '';
        if (!confirm($i18n.t('superadmin.database.restoreConfirm'))) {
            return;
        }

        dbStatus = $i18n.t('superadmin.database.restoreInProgress');
        dbStatusType = 'info';

        try {
            const res = await fetchWithCsrf('/api/super/db-restore', {
                method: 'POST',
                body: file,
                headers: { 'Content-Type': 'application/octet-stream' },
            });
            const payload = await res.json().catch(() => ({})) as ResponsePayload;

            if (res.ok && payload.success) {
                dbStatus = $i18n.t('superadmin.database.restoreSuccess');
                dbStatusType = 'success';
                setTimeout(() => location.reload(), 2000);
                return;
            }

            dbStatus = $i18n.t('superadmin.states.errorPrefix', {
                message: messageFromPayload(payload, $i18n.t('superadmin.states.unknown')),
            });
            dbStatusType = 'error';
        } catch {
            dbStatus = $i18n.t('superadmin.database.connectionError');
            dbStatusType = 'error';
        }
    }

    async function updateFamilyPassword() {
        if (!familyDetail) {
            return;
        }

        if (familyPassword.length < 6) {
            familyPasswordStatus = $i18n.t('superadmin.families.passwordTooShort');
            familyPasswordStatusType = 'error';
            return;
        }

        if (familyPassword !== familyPasswordConfirm) {
            familyPasswordStatus = $i18n.t('superadmin.families.passwordMismatch');
            familyPasswordStatusType = 'error';
            return;
        }

        familyPasswordSaving = true;
        familyPasswordStatus = $i18n.t('superadmin.families.passwordSaving');
        familyPasswordStatusType = 'info';

        try {
            const res = await fetchWithCsrf(`/api/super/family/${familyDetail.familyId}/password`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ password: familyPassword }),
            });
            const payload = await res.json().catch(() => null);

            if (res.ok) {
                familyPasswordStatus = $i18n.t('superadmin.families.passwordSaved');
                familyPasswordStatusType = 'success';
                familyPassword = '';
                familyPasswordConfirm = '';
                return;
            }

            familyPasswordStatus = messageFromPayload(payload, $i18n.t('superadmin.families.passwordSaveError'));
            familyPasswordStatusType = 'error';
        } catch {
            familyPasswordStatus = $i18n.t('superadmin.states.networkUnavailable');
            familyPasswordStatusType = 'error';
        } finally {
            familyPasswordSaving = false;
        }
    }

    async function blockFamily(familyId: unknown) {
        if (!confirm($i18n.t('superadmin.families.confirmBlock', { familyId: String(familyId) }))) {
            return;
        }

        const res = await fetchWithCsrf(`/api/super/family/${familyId}/block`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ isBlocked: true }),
        });

        if (res.ok) {
            families = families.map((family) => family.id === familyId ? { ...family, isBlocked: true } : family);
        }
    }

    async function unblockFamily(familyId: unknown) {
        if (!confirm($i18n.t('superadmin.families.confirmUnblock', { familyId: String(familyId) }))) {
            return;
        }

        const res = await fetchWithCsrf(`/api/super/family/${familyId}/block`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ isBlocked: false }),
        });

        if (res.ok) {
            families = families.map((family) => family.id === familyId ? { ...family, isBlocked: false } : family);
        }
    }

    async function openFamilyDetail(familyId: unknown) {
        familyDetailLoading = true;
        familyDetailError = '';
        familyDetail = null;
        resetFamilyPasswordState();

        try {
            const res = await fetchWithCsrf(`/api/super/family/${familyId}/data`);
            if (!res.ok) {
                familyDetailError = $i18n.t('superadmin.families.detailLoadError');
                return;
            }

            familyDetail = await res.json() as FamilyDetail;
        } catch {
            familyDetailError = $i18n.t('superadmin.states.networkUnavailable');
        } finally {
            familyDetailLoading = false;
        }
    }

    function closeFamilyDetail() {
        familyDetail = null;
        familyDetailError = '';
        resetFamilyPasswordState();
    }

    $: blockedFamiliesCount = families.filter((family) => (family.isBlocked ?? family.blocked) === true).length;

    $: dashboardStats = (() => {
        if (families.length === 0) {
            return null;
        }

        const now = Date.now();
        const todayMidnightMs = startOfTodayTimestamp(now);
        const weekAgoMs = now - 7 * 86400000;
        const monthAgoMs = now - 30 * 86400000;
        const activeAt = (family: Record<string, unknown>) => toDate(family.lastActive ?? family.last_activity)?.getTime() ?? 0;
        const createdAt = (family: Record<string, unknown>) => toDate(family.createdAt ?? family.created_at)?.getTime() ?? 0;

        const activeToday = families.filter((family) => activeAt(family) >= todayMidnightMs).length;
        const activeWeek = families.filter((family) => activeAt(family) >= weekAgoMs).length;
        const newWeek = families.filter((family) => createdAt(family) >= weekAgoMs).length;
        const newMonth = families.filter((family) => createdAt(family) >= monthAgoMs).length;
        const totalChildren = families.reduce((sum, family) => sum + parseNumber(family.childrenCount), 0);
        const totalTasks = families.reduce((sum, family) => sum + parseNumber(family.tasksCount), 0);
        const totalShop = families.reduce((sum, family) => sum + parseNumber(family.shopCount), 0);
        const withTasks = families.filter((family) => parseNumber(family.tasksCount) > 0).length;
        const withShop = families.filter((family) => parseNumber(family.shopCount) > 0).length;
        const avgTasks = (totalTasks / families.length).toFixed(1);
        const avgShop = (totalShop / families.length).toFixed(1);
        const recent = [...families]
            .filter((family) => createdAt(family) > 0)
            .sort((left, right) => createdAt(right) - createdAt(left))
            .slice(0, 8);
        const topEngaged = [...families]
            .sort((left, right) => (parseNumber(right.tasksCount) + parseNumber(right.shopCount)) - (parseNumber(left.tasksCount) + parseNumber(left.shopCount)))
            .slice(0, 8);

        return {
            total: families.length,
            activeToday,
            activeWeek,
            newWeek,
            newMonth,
            blocked: blockedFamiliesCount,
            totalChildren,
            totalTasks,
            totalShop,
            withTasks,
            withShop,
            avgTasks,
            avgShop,
            recent,
            topEngaged,
        };
    })();

    $: sortedFamilies = [...families].sort((left, right) => {
        if (familiesSort === 'created') {
            return new Date(String(right.createdAt ?? right.created_at ?? 0)).getTime() - new Date(String(left.createdAt ?? left.created_at ?? 0)).getTime();
        }

        return new Date(String(right.lastActive ?? right.last_activity ?? 0)).getTime() - new Date(String(left.lastActive ?? left.last_activity ?? 0)).getTime();
    });

    $: filteredFamilies = sortedFamilies.filter((family) => {
        const query = familiesSearch.toLowerCase();
        const matchesSearch = !query || String(family.email ?? '').toLowerCase().includes(query) || String(family.id ?? '').includes(query);
        const isBlocked = (family.isBlocked ?? family.blocked) === true;
        const matchesStatus = familiesStatus === 'all'
            || (familiesStatus === 'blocked' && isBlocked)
            || (familiesStatus === 'active' && !isBlocked);

        return matchesSearch && matchesStatus;
    });

    async function logout() {
        await fetchWithCsrf('/api/logout', { method: 'POST' });
        location.href = $i18n.href('/login');
    }

    function switchTab(tabId: TabId) {
        activeTab = tabId;
        sessionStorage.setItem('superAdminActiveTab', tabId);

        if (tabId === 'catalog-tasks' || tabId === 'catalog-products') {
            if (catalogTasks.length === 0 && catalogProducts.length === 0 && !catalogLoading) {
                void loadCatalog();
            }
        }
    }
</script>

<svelte:head>
    <title>{$i18n.t('superadmin.meta.title')} | {$i18n.t('common.brand.name')}</title>
    <meta name="robots" content="noindex, nofollow" />
    <link rel="alternate" hreflang="en" href={alternates.en} />
    <link rel="alternate" hreflang="ru" href={alternates.ru} />
    <link rel="alternate" hreflang="x-default" href={alternates['x-default']} />
</svelte:head>

<div class="super-admin-shell">
    <header class="super-admin-header">
        <div class="super-admin-header__brand">
            <span class="super-admin-header__wordmark">{$i18n.t('common.brand.name')}</span>
            <span class="super-admin-header__badge">{$i18n.t('superadmin.meta.badge')}</span>
        </div>
        <div class="super-admin-header__actions">
            {#if data.session.email}
            <span class="super-admin-header__identity">{data.session.email}</span>
            {/if}
            <a class="back-to-app-btn" href={$i18n.href('/app')}>{$i18n.t('superadmin.actions.backToApp')}</a>
            <button class="logout-btn" type="button" on:click={logout}>{$i18n.t('superadmin.actions.logout')}</button>
        </div>
    </header>

    <div class="tabs" role="tablist" aria-label={$i18n.t('superadmin.tabs.ariaLabel')}>
        {#each tabItems as [id, label] (id)}
        <button class="tab-btn" class:active={activeTab === id}
            id="tab-btn-{id}" data-tab={id} type="button" role="tab"
            aria-controls="tab-{id}" aria-selected={activeTab === id}
            on:click={() => switchTab(id)}>
            {label}
        </button>
        {/each}
    </div>

    <main class="super-admin-panels">
        {#if activeTab === 'dashboard'}
        <div id="tab-dashboard" class="tab-content active" role="tabpanel" aria-labelledby="tab-btn-dashboard">
            {#if familiesLoading}
            <div class="panel-state panel-state--loading">{$i18n.t('superadmin.states.loadingData')}</div>
            {:else if dashboardStats}
            <div class="sa-dashboard">
                <section class="sa-kpi-row">
                    <article class="sa-kpi-card">
                        <span class="sa-kpi-value">{dashboardStats.total}</span>
                        <span class="sa-kpi-label">{$i18n.t('superadmin.dashboard.totalFamilies')}</span>
                    </article>
                    <article class="sa-kpi-card sa-kpi-card--active">
                        <span class="sa-kpi-value">{dashboardStats.activeToday}</span>
                        <span class="sa-kpi-label">{$i18n.t('superadmin.dashboard.activeToday')}</span>
                    </article>
                    <article class="sa-kpi-card sa-kpi-card--week">
                        <span class="sa-kpi-value">{dashboardStats.activeWeek}</span>
                        <span class="sa-kpi-label">{$i18n.t('superadmin.dashboard.activeWeek')}</span>
                    </article>
                    <article class="sa-kpi-card sa-kpi-card--new">
                        <span class="sa-kpi-value">+{dashboardStats.newWeek}</span>
                        <span class="sa-kpi-label">{$i18n.t('superadmin.dashboard.newWeek')}</span>
                        {#if dashboardStats.newMonth > dashboardStats.newWeek}
                        <span class="sa-kpi-delta">{$i18n.t('superadmin.dashboard.newMonthDelta', { count: dashboardStats.newMonth })}</span>
                        {/if}
                    </article>
                    <article class="sa-kpi-card" class:sa-kpi-card--danger={dashboardStats.blocked > 0}>
                        <span class="sa-kpi-value">{dashboardStats.blocked}</span>
                        <span class="sa-kpi-label">{$i18n.t('superadmin.dashboard.blocked')}</span>
                    </article>
                    <article class="sa-kpi-card sa-kpi-card--children">
                        <span class="sa-kpi-value">{dashboardStats.totalChildren}</span>
                        <span class="sa-kpi-label">{$i18n.t('superadmin.dashboard.childProfiles')}</span>
                    </article>
                </section>

                <div class="sa-activity-grid">
                    <section class="sa-section">
                        <h3 class="sa-section__title">{$i18n.t('superadmin.dashboard.recentRegistrations')}</h3>
                        <ul class="sa-reg-list">
                            {#each dashboardStats.recent as family (family.id)}
                            <li class="sa-reg-item">
                                <div class="sa-reg-item__info">
                                    <strong>{familyLabel(family)}</strong>
                                    <span>{previewChildren(family)}</span>
                                </div>
                                <span class="sa-reg-item__date">{formatShortDate(family.createdAt ?? family.created_at)}</span>
                            </li>
                            {/each}
                        </ul>
                    </section>
                    <section class="sa-section">
                        <h3 class="sa-section__title">{$i18n.t('superadmin.dashboard.topByContent')}</h3>
                        <ul class="sa-reg-list">
                            {#each dashboardStats.topEngaged as family (family.id)}
                            <li class="sa-reg-item">
                                <div class="sa-reg-item__info">
                                    <strong>{familyLabel(family)}</strong>
                                    <span>{$i18n.t('superadmin.dashboard.engagedSummary', {
                                        children: parseNumber(family.childrenCount),
                                        date: formatShortDate(family.lastActive ?? family.last_activity),
                                    })}</span>
                                </div>
                                <span class="sa-reg-item__stats">
                                    <span class="sa-stat-chip sa-stat-chip--tasks">📋 {parseNumber(family.tasksCount)}</span>
                                    <span class="sa-stat-chip sa-stat-chip--shop">🛒 {parseNumber(family.shopCount)}</span>
                                </span>
                            </li>
                            {/each}
                        </ul>
                    </section>
                </div>

                <section class="sa-section">
                    <h3 class="sa-section__title">{$i18n.t('superadmin.dashboard.platformUsage')}</h3>
                    <div class="sa-adoption-grid">
                        <div class="sa-adoption-row">
                            <span class="sa-adoption-label">{$i18n.t('superadmin.dashboard.useTasks')}</span>
                            <div class="sa-adoption-bar-wrap">
                                <div class="sa-adoption-bar" style="width: {families.length > 0 ? (dashboardStats.withTasks / families.length * 100).toFixed(0) : 0}%"></div>
                            </div>
                            <span class="sa-adoption-pct">{families.length > 0 ? (dashboardStats.withTasks / families.length * 100).toFixed(0) : 0}% <span class="sa-adoption-count">({dashboardStats.withTasks}/{dashboardStats.total})</span></span>
                        </div>
                        <div class="sa-adoption-row">
                            <span class="sa-adoption-label">{$i18n.t('superadmin.dashboard.useShop')}</span>
                            <div class="sa-adoption-bar-wrap">
                                <div class="sa-adoption-bar sa-adoption-bar--shop" style="width: {families.length > 0 ? (dashboardStats.withShop / families.length * 100).toFixed(0) : 0}%"></div>
                            </div>
                            <span class="sa-adoption-pct">{families.length > 0 ? (dashboardStats.withShop / families.length * 100).toFixed(0) : 0}% <span class="sa-adoption-count">({dashboardStats.withShop}/{dashboardStats.total})</span></span>
                        </div>
                        <div class="sa-adoption-meta">
                            <span>{$i18n.t('superadmin.dashboard.averageTasksPerFamily')}: <strong>{dashboardStats.avgTasks}</strong></span>
                            <span>{$i18n.t('superadmin.dashboard.averageRewardsPerFamily')}: <strong>{dashboardStats.avgShop}</strong></span>
                            <span>{$i18n.t('superadmin.dashboard.totalTasks')}: <strong>{dashboardStats.totalTasks}</strong></span>
                            <span>{$i18n.t('superadmin.dashboard.totalRewards')}: <strong>{dashboardStats.totalShop}</strong></span>
                        </div>
                    </div>
                </section>
            </div>
            {:else}
            <div class="panel-state">{$i18n.t('superadmin.states.noData')} <button class="btn btn--ghost btn--small" type="button" on:click={loadFamilies}>{$i18n.t('superadmin.states.refresh')}</button></div>
            {/if}
        </div>
        {:else if activeTab === 'families'}
        <div id="tab-families" class="tab-content active" role="tabpanel" aria-labelledby="tab-btn-families">
            <div class="ft-toolbar">
                <div class="ft-toolbar__search">
                    <input id="families-search" class="ft-search" type="search"
                        placeholder={$i18n.t('superadmin.families.searchPlaceholder')}
                        bind:value={familiesSearch} />
                </div>
                <div class="ft-toolbar__filters">
                    <select id="families-status-select" class="ft-select" bind:value={familiesStatus}>
                        <option value="all">{$i18n.t('superadmin.families.filterAll', { count: families.length })}</option>
                        <option value="active">{$i18n.t('superadmin.families.filterActive', { count: families.length - blockedFamiliesCount })}</option>
                        <option value="blocked">{$i18n.t('superadmin.families.filterBlocked', { count: blockedFamiliesCount })}</option>
                    </select>
                    <button class="ft-sort-btn" class:ft-sort-btn--active={familiesSort === 'created'} type="button" on:click={() => familiesSort = 'created'}>{$i18n.t('superadmin.families.sortCreated')}</button>
                    <button class="ft-sort-btn" class:ft-sort-btn--active={familiesSort === 'active'} type="button" on:click={() => familiesSort = 'active'}>{$i18n.t('superadmin.families.sortActivity')}</button>
                </div>
                <span class="ft-toolbar__count">{$i18n.t('superadmin.families.countSummary', { visible: filteredFamilies.length, total: families.length })}</span>
            </div>

            {#if familiesLoading}
            <div class="panel-state panel-state--loading">{$i18n.t('superadmin.states.loading')}</div>
            {:else if familiesError}
            <div class="panel-state panel-state--error" aria-live="polite">{familiesError}</div>
            {:else}
            <div class="ft-wrap">
                <table class="ft" aria-label={$i18n.t('superadmin.families.tableAria')}>
                    <thead>
                        <tr>
                            <th class="ft__th ft__th--email">{$i18n.t('superadmin.families.emailId')}</th>
                            <th class="ft__th ft__th--status">{$i18n.t('superadmin.families.status')}</th>
                            <th class="ft__th ft__th--num">{$i18n.t('superadmin.families.children')}</th>
                            <th class="ft__th ft__th--num">{$i18n.t('superadmin.families.tasks')}</th>
                            <th class="ft__th ft__th--num">{$i18n.t('superadmin.families.rewards')}</th>
                            <th class="ft__th ft__th--profiles">{$i18n.t('superadmin.families.profiles')}</th>
                            <th class="ft__th ft__th--date">{$i18n.t('superadmin.families.created')}</th>
                            <th class="ft__th ft__th--date">{$i18n.t('superadmin.families.activity')}</th>
                            <th class="ft__th ft__th--actions">{$i18n.t('superadmin.families.actions')}</th>
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
                                <span class="ft__badge ft__badge--blocked">{familyStatusLabel(true)}</span>
                                {:else}
                                <span class="ft__badge ft__badge--active">{familyStatusLabel(false)}</span>
                                {/if}
                            </td>
                            <td class="ft__td ft__td--num ft__td--center">{parseNumber(family.childrenCount)}</td>
                            <td class="ft__td ft__td--num ft__td--center"><span class:ft__num--zero={parseNumber(family.tasksCount) === 0}>{parseNumber(family.tasksCount)}</span></td>
                            <td class="ft__td ft__td--num ft__td--center"><span class:ft__num--zero={parseNumber(family.shopCount) === 0}>{parseNumber(family.shopCount)}</span></td>
                            <td class="ft__td ft__td--profiles">{previewChildren(family)}</td>
                            <td class="ft__td ft__td--date">{formatShortDate(family.createdAt ?? family.created_at)}</td>
                            <td class="ft__td ft__td--date">{formatShortDate(family.lastActive ?? family.last_activity)}</td>
                            <td class="ft__td ft__td--actions">
                                <button class="ft__action-btn ft__action-btn--open" type="button" on:click={() => openFamilyDetail(family.id)}>{$i18n.t('superadmin.actions.open')}</button>
                                {#if isFamilyBlocked}
                                <button class="ft__action-btn ft__action-btn--unblock" type="button" on:click={() => unblockFamily(family.id)}>{$i18n.t('superadmin.actions.unblock')}</button>
                                {:else}
                                <button class="ft__action-btn ft__action-btn--block" type="button" on:click={() => blockFamily(family.id)}>{$i18n.t('superadmin.actions.block')}</button>
                                {/if}
                            </td>
                        </tr>
                        {/each}
                        {#if filteredFamilies.length === 0}
                        <tr><td colspan="9" class="ft__empty">{$i18n.t('superadmin.states.nothingFound')}</td></tr>
                        {/if}
                    </tbody>
                </table>
            </div>
            {/if}
        </div>
        {:else if activeTab === 'catalog-tasks' || activeTab === 'catalog-products'}
        {@const type = activeTab === 'catalog-tasks' ? 'tasks' : 'products'}
        {@const items = activeTab === 'catalog-tasks' ? catalogTasks : catalogProducts}
        <div id="tab-{activeTab}" class="tab-content active" role="tabpanel" aria-labelledby={`tab-btn-${activeTab}`}>
            <article class="panel catalog-panel">
                <header class="panel__header">
                    <div>
                        <p class="panel__eyebrow">{$i18n.t('superadmin.catalog.eyebrow')}</p>
                        <h2>{catalogTitle(type)}</h2>
                    </div>
                    <button class="btn btn--ghost" type="button" on:click={() => openEditModal(type, -1)}>{catalogAddLabel(type)}</button>
                </header>

                {#if catalogLoading}
                <div class="panel-state panel-state--loading" aria-live="polite">{catalogLoadingLabel(type)}</div>
                {:else if catalogError}
                <div class="panel-state panel-state--error" aria-live="polite">{catalogError}</div>
                {:else if items.length === 0}
                <div class="panel-state panel-state--empty" aria-live="polite">{catalogEmptyLabel(type)}</div>
                {:else}
                <div id={activeTab === 'catalog-tasks' ? 'base-tasks-list' : 'base-products-list'} class="items-grid" aria-live="polite">
                    {#each items as item, index (item.id ?? index)}
                    <div class="item-card">
                        <div class="item-header"><span>{item.name}</span></div>
                        <div class="item-meta" style="color: #6366f1; font-weight: 600;">
                            {item.coins ?? item.price ?? 0} 🪙
                            {#if item.frequency?.limit}
                            <span> • {item.frequency.limit} {formatCatalogPeriod(item.frequency.period).toLowerCase()}</span>
                            {/if}
                            {#if item.money_limit}
                            <span> | {$i18n.t('superadmin.catalog.moneyLimitValue', { amount: item.money_limit })}</span>
                            {/if}
                        </div>
                        <div class="item-meta">{$i18n.t('superadmin.catalog.ageValue', { min: item.age_min ?? 0, max: item.age_max ?? 18 })}</div>
                        <div class="item-actions">
                            <button class="btn-sm btn-edit" type="button" on:click={() => openEditModal(type, index)}>{$i18n.t('superadmin.actions.edit')}</button>
                            <button class="btn-sm btn-del" type="button" on:click={() => deleteCatalogItem(type, index)}>{$i18n.t('superadmin.actions.delete')}</button>
                        </div>
                    </div>
                    {/each}
                </div>
                {/if}

                {#if catalogSaveStatus === 'saving'}
                <p class="panel-state panel-state--loading" aria-live="polite">{$i18n.t('superadmin.catalog.savingStatus')}</p>
                {:else if catalogSaveStatus === 'saved'}
                <p class="panel-state" aria-live="polite" style="color: #10b981;">{$i18n.t('superadmin.catalog.savedStatus')}</p>
                {:else if catalogSaveStatus === 'error'}
                <p class="panel-state panel-state--error" aria-live="polite">{$i18n.t('superadmin.catalog.saveError')}</p>
                {/if}
            </article>
        </div>
        {:else if activeTab === 'database'}
        <div id="tab-database" class="tab-content active" role="tabpanel" aria-labelledby="tab-btn-database">
            <!-- Compact Backup Blocks -->
            <div class="db-compact">
                <!-- Row 1: Download + Restore -->
                <div class="db-compact__row">
                    <div class="db-compact__cell">
                        <div class="db-compact__cell-head">
                            <span class="db-compact__cell-label">{$i18n.t('superadmin.database.backupLabel')}</span>
                            <span class="db-compact__cell-title">{$i18n.t('superadmin.database.backupValue')}</span>
                        </div>
                        <p class="db-compact__desc">{$i18n.t('superadmin.database.backupDescription')}</p>
                        <button id="pg-backup-btn" class="btn btn--primary btn--small" type="button" on:click={triggerBackup}>{$i18n.t('superadmin.actions.downloadBackup')}</button>
                    </div>
                    <div class="db-compact__cell">
                        <div class="db-compact__cell-head">
                            <span class="db-compact__cell-label">{$i18n.t('superadmin.database.restoreLabel')}</span>
                            <span class="db-compact__cell-title">{$i18n.t('superadmin.database.restoreValue')}</span>
                        </div>
                        <p class="db-compact__desc">{$i18n.t('superadmin.database.restoreDescription')}</p>
                        <button id="pg-restore-btn" class="btn btn--success btn--small" type="button" on:click={triggerRestoreClick}>{$i18n.t('superadmin.actions.uploadFile')}</button>
                        <input type="file" id="pg-restore-input" hidden accept=".dump" on:change={handleRestoreChange} />
                    </div>
                </div>

                <!-- Row 2: Telegram -->
                {#if telegramSettingsLoading}
                <div class="db-compact__section-loading">{$i18n.t('superadmin.database.loadingTelegram')}</div>
                {:else}
                <div class="db-compact__section">
                    <div class="db-compact__section-head">
                        <span class="db-compact__section-label">{$i18n.t('superadmin.database.telegramLabel')}</span>
                        <span class="db-compact__section-title">{$i18n.t('superadmin.database.telegramHeading')}</span>
                    </div>
                    <div class="db-compact__telegram-form">
                        <div class="db-compact__tg-field">
                            <label for="backup-telegram-enabled">{$i18n.t('superadmin.database.schedule')}</label>
                            <select id="backup-telegram-enabled" bind:value={telegramScheduleMode}>
                                <option value="off">{$i18n.t('superadmin.database.scheduleOff')}</option>
                                <option value="on">{$i18n.t('superadmin.database.scheduleOn')}</option>
                            </select>
                        </div>
                        <div class="db-compact__tg-field">
                            <label for="backup-telegram-chat-id">Chat ID</label>
                            <input id="backup-telegram-chat-id" type="text" bind:value={telegramChatId} placeholder="-1001234567890" autocomplete="off" />
                        </div>
                        <div class="db-compact__tg-field db-compact__tg-field--narrow">
                            <label for="backup-telegram-interval">{$i18n.t('superadmin.database.intervalHours')}</label>
                            <input id="backup-telegram-interval" type="number" min="1" max="720" bind:value={telegramIntervalHours} />
                        </div>
                        <div class="db-compact__tg-field db-compact__tg-field--narrow">
                            <label for="backup-retention-count">{$i18n.t('superadmin.database.retentionCount')}</label>
                            <input id="backup-retention-count" type="number" min="1" max="500" bind:value={telegramBackupRetentionCount} />
                        </div>
                        <div class="db-compact__tg-field db-compact__tg-field--wide">
                            <label for="backup-telegram-token">Bot token</label>
                            <input id="backup-telegram-token" type="password" bind:value={telegramBotToken}
                                placeholder={telegramHasBotToken ? $i18n.t('superadmin.database.botTokenPlaceholderSaved') : $i18n.t('superadmin.database.botTokenPlaceholderNew')}
                                autocomplete="new-password" />
                        </div>
                    </div>
                    <div class="db-compact__tg-status">
                        <span class="db-compact__tg-badge" class:db-compact__tg-badge--ok={telegramConfigured} class:db-compact__tg-badge--missing={!telegramConfigured}>
                            {telegramConfigured ? '●' : '○'}
                        </span>
                        {#if telegramHasBotToken}
                        <span>{$i18n.t('superadmin.database.tokenSaved')}</span>
                        {:else}
                        <span>{$i18n.t('superadmin.database.tokenMissing')}</span>
                        {/if}
                        {#if telegramConfigured}
                        <span>{$i18n.t('superadmin.database.sendingAvailable')}</span>
                        {:else}
                        <span>{$i18n.t('superadmin.database.sendingNeedsCredentials')}</span>
                        {/if}
                        <span class="db-compact__tg-divider"></span>
                        <span>{$i18n.t('superadmin.database.state')}: {telegramScheduleMode === 'on' ? $i18n.t('superadmin.database.stateOn') : $i18n.t('superadmin.database.stateOff')}</span>
                        {#if telegramLastSentAt}
                        <span class="db-compact__tg-divider"></span>
                        <span>{$i18n.t('superadmin.database.lastSent')}: {formatDateTime(telegramLastSentAt)}</span>
                        {/if}
                        {#if telegramLastError}
                        <span class="db-compact__tg-divider"></span>
                        <span style="color: var(--color-danger-dark, #dc2626);">{$i18n.t('superadmin.database.lastError')}: {telegramLastError}</span>
                        {/if}
                    </div>
                    <div class="db-compact__tg-actions">
                        <button class="btn btn--ghost btn--small" type="button" disabled={telegramSettingsSaving || telegramBackupSending} on:click={loadTelegramBackupSettings}>{$i18n.t('superadmin.states.refresh')}</button>
                        <button class="btn btn--primary btn--small" type="button" disabled={telegramSettingsSaving || telegramBackupSending} on:click={saveTelegramBackupSettings}>
                            {telegramSettingsSaving ? $i18n.t('superadmin.actions.saving') : $i18n.t('superadmin.actions.save')}
                        </button>
                        <button class="btn btn--success btn--small" type="button" disabled={telegramSettingsSaving || telegramBackupSending || !telegramConfigured} on:click={sendBackupToTelegram}>
                            {telegramBackupSending ? $i18n.t('superadmin.actions.sending') : $i18n.t('superadmin.actions.sendNow')}
                        </button>
                    </div>
                    {#if telegramSettingsStatus}
                    <div class="status-callout"
                        class:status-callout--success={telegramSettingsStatusType === 'success'}
                        class:status-callout--error={telegramSettingsStatusType === 'error'}
                        class:status-callout--info={telegramSettingsStatusType === 'info'}
                        role="status">
                        {telegramSettingsStatus}
                    </div>
                    {/if}
                </div>
                {/if}

                <!-- Row 3: Backup History -->
                <div class="db-compact__section">
                    <div class="db-compact__section-head">
                        <span class="db-compact__section-label">{$i18n.t('superadmin.database.backupHistoryLabel')}</span>
                        <span class="db-compact__section-title">{$i18n.t('superadmin.database.backupHistoryHeading')}</span>
                    </div>
                    {#if backupHistoryLoading}
                    <div class="db-compact__section-loading">{$i18n.t('superadmin.database.loadingBackups')}</div>
                    {:else if backupHistory.length === 0}
                    <p class="db-compact__empty">{$i18n.t('superadmin.database.backupHistoryEmpty')}</p>
                    {:else}
                    <div class="db-compact__history-list">
                        {#each backupHistory as item (item.filename)}
                        <div class="db-compact__history-item">
                            <div class="db-compact__history-info">
                                <span class="db-compact__history-filename">{item.filename}</span>
                                <span class="db-compact__history-meta">{formatDateTime(item.createdAt)} · {formatFileSize(item.sizeBytes)}</span>
                            </div>
                            <div class="db-compact__history-actions">
                                <a class="btn btn--ghost btn--small" download href={`/api/super/db-backup/history/${encodeURIComponent(item.filename)}`}>
                                    {$i18n.t('superadmin.database.backupDownloadAction')}
                                </a>
                                <button class="btn btn--danger btn--small" type="button" on:click={() => restoreBackupFromHistory(item.filename)}>
                                    {$i18n.t('superadmin.database.backupRestoreAction')}
                                </button>
                            </div>
                        </div>
                        {/each}
                    </div>
                    {/if}
                </div>

                <!-- DB Status callout -->
                {#if dbChecking}
                <div class="db-compact__section-loading" id="db-panel-state">{$i18n.t('superadmin.database.checkingStatus')}</div>
                {/if}
                {#if dbStatus}
                <div class="status-callout"
                    class:status-callout--success={dbStatusType === 'success'}
                    class:status-callout--error={dbStatusType === 'error'}
                    class:status-callout--info={dbStatusType === 'info'}
                    role="status">
                    {dbStatus}
                </div>
                {/if}
            </div>
        </div>
        {:else if activeTab === 'system'}
        <div id="tab-system" class="tab-content active" role="tabpanel" aria-labelledby="tab-btn-system">
            <article class="panel system-panel" id="system-panel">
                <header class="panel__header">
                    <div>
                        <p class="panel__eyebrow">{$i18n.t('superadmin.system.eyebrow')}</p>
                        <h2>{$i18n.t('superadmin.system.title')}</h2>
                    </div>
                </header>
                <div class="system-panel__grid" id="system-kpi-grid">
                    <article class="system-card">
                        <p class="system-card__label">{$i18n.t('superadmin.system.memory')}</p>
                        <p class="system-card__value" id="system-memory-value">{systemInfo.memoryMB != null ? `${systemInfo.memoryMB} MB` : EMPTY_VALUE}</p>
                        <p class="system-card__helper">{$i18n.t('superadmin.system.rssHeap')}</p>
                    </article>
                    <article class="system-card">
                        <p class="system-card__label">{$i18n.t('superadmin.system.uptime')}</p>
                        <p class="system-card__value" id="system-uptime-value">{systemInfo.uptime ?? EMPTY_VALUE}</p>
                        <p class="system-card__helper">{$i18n.t('superadmin.system.running')}</p>
                    </article>
                    <article class="system-card">
                        <p class="system-card__label">{$i18n.t('superadmin.system.database')}</p>
                        <p class="system-card__value" id="system-db-value">{systemInfo.dbStatus ?? EMPTY_VALUE}</p>
                        <p class="system-card__helper">{$i18n.t('superadmin.system.latency')}</p>
                    </article>
                    <article class="system-card">
                        <p class="system-card__label">{$i18n.t('superadmin.system.version')}</p>
                        <p class="system-card__value">{systemInfo.version ?? EMPTY_VALUE}</p>
                        <p class="system-card__helper">Node: {systemInfo.nodeVersion ?? EMPTY_VALUE}</p>
                    </article>
                </div>
                <div class="system-panel__details">
                    <article class="system-card system-card--details">
                        <p class="system-card__label">{$i18n.t('superadmin.system.connection')}</p>
                        <h3 class="system-card__heading">{$i18n.t('superadmin.system.integrationHeading')}</h3>
                        <dl class="system-detail-list">
                            <div>
                                <dt>{$i18n.t('superadmin.system.wsPath')}</dt>
                                <dd>{data.appConfig.wsPath}</dd>
                            </div>
                            <div>
                                <dt>{$i18n.t('superadmin.system.backend')}</dt>
                                <dd>{data.appConfig.backendOrigin}</dd>
                            </div>
                            <div>
                                <dt>{$i18n.t('superadmin.system.server')}</dt>
                                <dd>{systemInfo.version ?? EMPTY_VALUE}</dd>
                            </div>
                            <div>
                                <dt>{$i18n.t('superadmin.system.updated')}</dt>
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

{#if familyDetailLoading || familyDetail || familyDetailError}
<div class="modal" style="display:flex;" role="dialog" aria-modal="true" aria-label={$i18n.t('superadmin.meta.detailDialog')}>
    <div class="modal-content family-detail-modal">
        <button class="modal-close" type="button" on:click={closeFamilyDetail}>&times;</button>
        {#if familyDetailLoading}
        <div class="panel-state panel-state--loading">{$i18n.t('superadmin.families.loadingDetail')}</div>
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
                        {familyStatusLabel(info.isBlocked === true)}
                    </span>
                    <span class="fdc__chip">{$i18n.t('superadmin.families.createdChip', { date: formatShortDate(info.created_at) })}</span>
                    <span class="fdc__chip">{$i18n.t('superadmin.families.activityChip', { date: formatShortDate(info.last_activity) })}</span>
                    <span class="fdc__chip">{$i18n.t('superadmin.families.balanceChip', { balance: parseNumber(familyDetail.data.balance) })}</span>
                    <span class="fdc__chip">{$i18n.t('superadmin.families.tasksChip', { count: tasks.length })}</span>
                    <span class="fdc__chip">{$i18n.t('superadmin.families.rewardsChip', { count: shopItems.length })}</span>
                </div>
            </div>
        </header>

        <section class="family-detail-section password-panel">
            <div class="fdc__section-head">
                <span class="fdc__section-label">{$i18n.t('superadmin.families.passwordSection')}</span>
                <span class="fdc__section-hint">{$i18n.t('superadmin.families.passwordHint')}</span>
            </div>
            <div class="password-form-grid">
                <div class="input-group">
                    <label for="family-password">{$i18n.t('superadmin.families.newPassword')}</label>
                    <input id="family-password" type="password" bind:value={familyPassword} autocomplete="new-password" />
                </div>
                <div class="input-group">
                    <label for="family-password-confirm">{$i18n.t('superadmin.families.confirmPassword')}</label>
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
                <button class="btn btn--primary" type="button" disabled={familyPasswordSaving} on:click={updateFamilyPassword}>
                    {familyPasswordSaving ? $i18n.t('superadmin.actions.saving') : $i18n.t('superadmin.actions.setPassword')}
                </button>
            </div>
        </section>

        <div class="family-detail-columns">
            <section class="family-detail-section">
                <div class="fdc__section-head">
                    <span class="fdc__section-label">{$i18n.t('superadmin.families.childProfilesHeading', { count: children.length })}</span>
                </div>
                {#if children.length > 0}
                <div class="fdc__children">
                    {#each children as child ((child as Record<string, unknown>).id)}
                    {@const currentChild = child as Record<string, unknown>}
                    <div class="fdc__child-row">
                        <strong class="fdc__child-name">{String(currentChild.name ?? EMPTY_VALUE)}</strong>
                        <span class="fdc__child-stat">{$i18n.t('superadmin.families.childBalance', { amount: parseNumber(currentChild.balance) })}</span>
                        <span class="fdc__child-stat">{$i18n.t('superadmin.families.childMonthlyLimit', { amount: parseNumber(currentChild.monthly_limit) })}</span>
                        <span class="fdc__child-stat">{$i18n.t('superadmin.families.childDailyLimit', { amount: parseNumber(currentChild.daily_coin_limit) })}</span>
                    </div>
                    {/each}
                </div>
                {:else}
                <p class="panel-state">{$i18n.t('superadmin.families.profilesEmpty')}</p>
                {/if}
            </section>

            <section class="family-detail-section">
                <div class="fdc__section-head">
                    <span class="fdc__section-label">{$i18n.t('superadmin.families.tasksAndRewards')}</span>
                </div>
                <div class="family-detail-collections">
                    <div class="family-detail-collection">
                        <h4>{$i18n.t('superadmin.families.tasksHeading')}</h4>
                        {#if tasks.length > 0}
                        <ul class="family-detail-list">
                            {#each tasks.slice(0, 6) as task ((task as Record<string, unknown>).id)}
                            {@const currentTask = task as Record<string, unknown>}
                            <li class="family-detail-list__item">
                                <div>
                                    <strong>{String(currentTask.name ?? EMPTY_VALUE)}</strong>
                                    <span>{String(currentTask.group ?? $i18n.t('superadmin.families.noGroup'))}</span>
                                </div>
                                <span>{$i18n.t('superadmin.families.childBalance', { amount: parseNumber(currentTask.coins) })}</span>
                            </li>
                            {/each}
                        </ul>
                        {:else}
                        <p class="panel-state">{$i18n.t('superadmin.families.tasksEmpty')}</p>
                        {/if}
                    </div>
                    <div class="family-detail-collection">
                        <h4>{$i18n.t('superadmin.families.rewardsHeading')}</h4>
                        {#if shopItems.length > 0}
                        <ul class="family-detail-list">
                            {#each shopItems.slice(0, 6) as item ((item as Record<string, unknown>).id)}
                            {@const shopItem = item as Record<string, unknown>}
                            <li class="family-detail-list__item">
                                <div>
                                    <strong>{String(shopItem.name ?? EMPTY_VALUE)}</strong>
                                    <span>{String(shopItem.group ?? $i18n.t('superadmin.families.noGroup'))}</span>
                                </div>
                                <span>{$i18n.t('superadmin.families.childBalance', { amount: parseNumber(shopItem.price) })}</span>
                            </li>
                            {/each}
                        </ul>
                        {:else}
                        <p class="panel-state">{$i18n.t('superadmin.families.rewardsEmpty')}</p>
                        {/if}
                    </div>
                </div>
            </section>
        </div>

        <div class="family-detail-columns">
            <section class="family-detail-section">
                <div class="fdc__section-head">
                    <span class="fdc__section-label">{$i18n.t('superadmin.families.transactions')}</span>
                </div>
                {#if historyItems.length > 0}
                <ul class="family-detail-list">
                    {#each historyItems.slice(0, 8) as entry ((entry as Record<string, unknown>).id)}
                    {@const historyEntry = entry as Record<string, unknown>}
                    <li class="family-detail-list__item">
                        <div>
                            <strong>{String(historyEntry.action ?? historyEntry.type ?? EMPTY_VALUE)}</strong>
                            <span>{formatDateTime(historyEntry.timestamp ?? historyEntry.createdAt)}</span>
                        </div>
                        <span>{$i18n.t('superadmin.families.childBalance', { amount: parseNumber(historyEntry.amount) })}</span>
                    </li>
                    {/each}
                </ul>
                {:else}
                <p class="panel-state">{$i18n.t('superadmin.families.transactionsEmpty')}</p>
                {/if}
            </section>

            <section class="family-detail-section">
                <div class="fdc__section-head">
                    <span class="fdc__section-label">{$i18n.t('superadmin.families.requests')}</span>
                </div>
                {#if requestItems.length > 0}
                <ul class="family-detail-list">
                    {#each requestItems.slice(0, 8) as request ((request as Record<string, unknown>).id)}
                    {@const requestItem = request as Record<string, unknown>}
                    <li class="family-detail-list__item">
                        <div>
                            <strong>{String(requestItem.taskName ?? requestItem.requestType ?? EMPTY_VALUE)}</strong>
                            <span>{String(requestItem.status ?? EMPTY_VALUE)}</span>
                        </div>
                        <span>{$i18n.t('superadmin.families.childBalance', { amount: parseNumber(requestItem.coins) })}</span>
                    </li>
                    {/each}
                </ul>
                {:else}
                <p class="panel-state">{$i18n.t('superadmin.families.requestsEmpty')}</p>
                {/if}
            </section>
        </div>
        {/if}
    </div>
</div>
<div class="modal-backdrop" on:click={closeFamilyDetail} role="presentation"></div>
{/if}

{#if editModalOpen}
<div class="modal" id="edit-modal" style="display:flex;" role="dialog" aria-modal="true" aria-label={$i18n.t('superadmin.meta.editDialog')}>
    <div class="modal-content modal-content--narrow">
        <button class="modal-close" type="button" on:click={() => editModalOpen = false}>&times;</button>
        <h2 id="edit-modal-title">{catalogModalTitle()}</h2>
        <div class="input-group">
            <label for="edit-name">{$i18n.t('superadmin.catalog.name')}</label>
            <input type="text" id="edit-name" bind:value={editName} />
        </div>
        <div class="input-group">
            <label for="edit-group">{$i18n.t('superadmin.catalog.group')}</label>
            <input type="text" id="edit-group" bind:value={editGroup} />
        </div>
        <div class="input-group">
            <label for="edit-cost">{catalogCostLabel(editType)}</label>
            <input type="number" id="edit-cost" bind:value={editCost} />
        </div>
        <div class="input-group">
            <label for="edit-min">{$i18n.t('superadmin.catalog.ageMin')}</label>
            <input type="number" id="edit-min" bind:value={editAgeMin} />
        </div>
        <div class="input-group">
            <label for="edit-max">{$i18n.t('superadmin.catalog.ageMax')}</label>
            <input type="number" id="edit-max" bind:value={editAgeMax} />
        </div>
        <div style="display: flex; gap: 1rem; border-top: 1px solid #eee; padding-top: 1rem; margin-top: 1rem;">
            <div class="input-group" style="flex: 1">
                <label for="edit-limit">{$i18n.t('superadmin.catalog.limit')}</label>
                <input type="number" id="edit-limit" bind:value={editFreqLimit} />
            </div>
            <div class="input-group" style="flex: 1">
                <label for="edit-period">{$i18n.t('superadmin.catalog.period')}</label>
                <select id="edit-period" bind:value={editFreqPeriod}>
                    <option value="day">{$i18n.t('superadmin.catalog.periodDay')}</option>
                    <option value="week">{$i18n.t('superadmin.catalog.periodWeek')}</option>
                    <option value="month">{$i18n.t('superadmin.catalog.periodMonth')}</option>
                    <option value="year">{$i18n.t('superadmin.catalog.periodYear')}</option>
                </select>
            </div>
        </div>
        <div class="input-group">
            <label for="edit-money-limit">{$i18n.t('superadmin.catalog.moneyLimitField')}</label>
            <input type="number" id="edit-money-limit" bind:value={editMoneyLimit} />
        </div>
        <div style="display: flex; gap: 0.75rem; margin-top: 1rem;">
            {#if editIndex >= 0}
            <button class="btn btn--danger" type="button" on:click={() => { deleteCatalogItem(editType, editIndex); editModalOpen = false; }}>{$i18n.t('superadmin.actions.delete')}</button>
            {/if}
            <button class="save-btn btn btn--primary" type="button" on:click={saveEditModal}>{$i18n.t('superadmin.actions.save')}</button>
        </div>
    </div>
</div>
<div class="modal-backdrop" on:click={() => editModalOpen = false} role="presentation"></div>
{/if}
