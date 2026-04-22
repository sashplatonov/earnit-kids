<script lang="ts">
    import { browser } from '$app/environment';
    import CardHeader from '$lib/components/app/CardHeader.svelte';
    import SectionHeaderControls from '$lib/components/app/SectionHeaderControls.svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import type { HistoryEntry } from '$lib/stores/app';
    import { deleteHistoryItem } from '$lib/services/api';
    import { loadCardViewMode, saveCardViewMode, type CardViewMode, type CardViewRole } from '$lib/services/cardViewMode';
    import { showToast } from '$lib/stores/toasts';
    import { buildHistoryCatalog, resolveHistoryCard } from './historyDetails';
    import type { HistoryCardDetails, HistoryDetailsI18n } from './historyDetails';
    import { groupHistoryEntries, type HistoryGroup } from './historyGroups';

    type HistoryViewEntry = HistoryEntry & { ui: HistoryCardDetails };

    const i18n = useI18n();
    let viewMode: CardViewMode = 'grid';
    const loadedViewRole: { value: CardViewRole | null } = { value: null };
    let historyGroupCollapseOverrides: Record<string, boolean> = {};

    function tHistory(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`history.${key}` as MessageKey, variables);
    }

    function createHistoryDetailsI18n(): HistoryDetailsI18n {
        return {
            t(key) {
                return tHistory(`model.${key}`);
            },
        };
    }

    $: history = $appStore.history;
    $: isAdmin = $appStore.isAdmin;
    $: viewRole = (isAdmin ? 'admin' : 'child') as CardViewRole;
    $: monthlyLimit = $appStore.monthlyLimit;
    $: dailyCoinLimit = $appStore.dailyCoinLimit;
    $: if (browser && loadedViewRole.value !== viewRole) {
        viewMode = loadCardViewMode('history', viewRole);
        loadedViewRole.value = viewRole;
    }
    $: historyDetailsI18n = ($i18n.locale, createHistoryDetailsI18n());
    $: historyCatalog = buildHistoryCatalog({
        tasks: $appStore.tasks,
        shopItems: $appStore.shopItems,
        baseTasks: $appStore.baseData.tasks,
        baseProducts: $appStore.baseData.products,
    });
    $: historyEntries = history.map(entry => ({ ...entry, ui: resolveHistoryCard(entry, historyCatalog, historyDetailsI18n) })) as HistoryViewEntry[];

    function historyKind(type: unknown): 'earn' | 'spend' | 'other' {
        if (type === 'task_completed' || type === 'earn') return 'earn';
        if (type === 'purchase' || type === 'spend') return 'spend';
        return 'other';
    }

    // Budget stats
    $: thisMonth = (() => {
        const now = new Date();
        return historyEntries.filter(h => {
            if (!h.createdAt) return false;
            const d = new Date(h.createdAt as string);
            return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
        });
    })();

    $: moneySpent = thisMonth
        .filter(h => historyKind(h.type) === 'spend')
        .reduce((sum, entry) => sum + Math.abs((entry.moneyAmount as number) ?? 0), 0);
    $: spentPercent = monthlyLimit > 0 ? Math.min(100, Math.round((moneySpent / monthlyLimit) * 100)) : 0;
    $: moneyRemaining = Math.max(0, monthlyLimit - moneySpent);

    const today = new Date();
    $: coinsEarnedToday = historyEntries.filter(h => {
        if (historyKind(h.type) !== 'earn' || !h.createdAt) return false;
        const d = new Date(h.createdAt as string);
        return d.toDateString() === today.toDateString();
    }).reduce((s, h) => s + (h.amount ?? 0), 0);

    $: coinsPercent = dailyCoinLimit > 0 ? Math.min(100, Math.round((coinsEarnedToday / dailyCoinLimit) * 100)) : 0;

    $: largePurchase = (() => {
        const spends = thisMonth.filter(h => historyKind(h.type) === 'spend');
        if (!spends.length) return null;
        return spends.reduce((max, h) => Math.abs(h.amount ?? 0) > Math.abs(max.amount ?? 0) ? h : max);
    })();

    function formatDate(dateStr: string | null | undefined): string {
        if (!dateStr) return '';
        try {
            return $i18n.formatDateTime(new Date(dateStr as string));
        } catch { return ''; }
    }

    function monthLabel(key: string): string {
        if (!key) return '';
        const [y, m] = key.split('-');
        return $i18n.formatDate(new Date(Number(y), Number(m) - 1, 1), { month: 'long', year: 'numeric' });
    }

    $: historyGroups = groupHistoryEntries({
        entries: historyEntries,
        getAmount: entry => entry.amount ?? 0,
        getCreatedAt: entry => entry.createdAt as string,
        getKind: entry => historyKind(entry.type),
    });

    function historyGroupLabel(group: HistoryGroup<HistoryViewEntry>): string {
        if (group.kind === 'today') return tHistory('history.groupToday');
        if (group.kind === 'thisWeek') return tHistory('history.groupThisWeek');
        if (group.kind === 'lastWeek') return tHistory('history.groupLastWeek');
        if (group.kind === 'noDate') return tHistory('history.groupNoDate');
        return monthLabel(group.monthKey);
    }

    function historyGroupPanelId(key: string): string {
        return `history-group-${key.replace(/[^a-z0-9_-]/gi, '-')}`;
    }

    function isHistoryGroupCollapsed(group: HistoryGroup<HistoryViewEntry>): boolean {
        if (Object.hasOwn(historyGroupCollapseOverrides, group.key)) {
            return historyGroupCollapseOverrides[group.key];
        }
        return group.collapsedByDefault;
    }

    function toggleHistoryGroup(group: HistoryGroup<HistoryViewEntry>) {
        historyGroupCollapseOverrides = {
            ...historyGroupCollapseOverrides,
            [group.key]: !isHistoryGroupCollapsed(group),
        };
    }

    async function handleDelete(historyId: unknown) {
        const ok = await deleteHistoryItem(historyId, $appStore.currentChildId);
        if (ok) {
            appStore.setState({ history: history.filter(h => h.id !== historyId) });
            showToast(tHistory('history.itemDeleted'), 'info');
        }
    }

    async function clearAll() {
        if (!confirm(tHistory('history.clearAllConfirm'))) return;
        appStore.setState({ history: [] });
        showToast(tHistory('history.clearedAll'), 'success');
    }

    function cssType(type: string): string {
        const kind = historyKind(type);
        return kind === 'other' ? type : kind;
    }

    function hasMoneyAmount(value: number): boolean {
        return Number(value ?? 0) > 0;
    }

    function historyMeta(entry: HistoryViewEntry): string {
        const parts: string[] = [];
        if (entry.ui.group) parts.push(entry.ui.group);
        const createdAt = formatDate(entry.createdAt as string);
        if (createdAt) parts.push(createdAt);
        return parts.join(' · ');
    }

    function historyAmount(entry: HistoryViewEntry): string {
        const prefix = cssType(entry.type as string) === 'earn' ? '+' : '−';
        return `${prefix}${$i18n.formatNumber(Math.abs(entry.amount ?? 0))}`;
    }

    function historyMoneyLabel(value: number): string {
        return hasMoneyAmount(value) ? `${$i18n.formatNumber(value)} 💶` : '';
    }

    function historyTypeLabel(entry: HistoryViewEntry): string {
        return cssType(entry.type as string) === 'earn'
            ? tHistory('model.requestTypeTask')
            : tHistory('model.requestTypePurchase');
    }

    function historyCompactChips(entry: HistoryViewEntry) {
        const chips = [];
        if (entry.ui.group) {
            chips.push({ label: entry.ui.group, className: 'card__compact-chip--group' });
        }
        chips.push({ label: historyTypeLabel(entry) });
        const createdAt = formatDate(entry.createdAt as string);
        if (createdAt) {
            chips.push({ label: createdAt });
        }
        return chips;
    }

    function setViewMode(nextMode: CardViewMode) {
        viewMode = nextMode;
        saveCardViewMode('history', viewRole, nextMode);
    }
</script>

<section class="section" id="history-section">
    <div class="section__header history-section__header">
        <div class="section__header-titles">
            <h2>{tHistory('history.title')}</h2>
        </div>
        {#if isAdmin}
        <div class="section__buttons admin-only history-section__actions">
            <SectionHeaderControls
                isAdmin={false}
                {viewMode}
                viewAriaLabel={tHistory('history.viewAria')}
                gridLabel={tHistory('history.viewGrid')}
                listLabel={tHistory('history.viewList')}
                on:viewMode={(event) => setViewMode(event.detail)}
            />
            <button class="btn btn--danger btn--small" id="clear-history-btn" on:click={clearAll}>
                {tHistory('history.clearAll')}
            </button>
        </div>
        {:else}
        <SectionHeaderControls
            isAdmin={false}
            {viewMode}
            viewAriaLabel={tHistory('history.viewAria')}
            gridLabel={tHistory('history.viewGrid')}
            listLabel={tHistory('history.viewList')}
            on:viewMode={(event) => setViewMode(event.detail)}
        />
        {/if}
    </div>

    <!-- Budget stats -->
    <div class="budget-stats" id="budget-stats">
        <div class="stat-card">
            <div class="stat-card__icon">
                <span class="gamified-icon icon-wallet" aria-hidden="true"></span>
            </div>
            <div class="stat-card__content">
                <div class="stat-card__value">
                    <span id="money-spent">{$i18n.formatNumber(moneySpent)}</span>
                    /
                    <span id="money-limit">{$i18n.formatNumber(monthlyLimit)}</span>
                    {#if moneyRemaining > 0}
                    <span id="money-remaining" style="font-size:0.8em;font-weight:400;margin-left:4px;">
                        ({tHistory('history.remaining', { amount: $i18n.formatNumber(moneyRemaining) })})
                    </span>
                    {/if}
                </div>
                <div class="stat-card__label">{tHistory('history.spentThisMonth')}</div>
            </div>
            <div class="stat-card__progress">
                <div class="progress-bar" id="money-progress" style="width:{spentPercent}%"></div>
            </div>
        </div>

        <div class="stat-card">
            <div class="stat-card__icon">
                <span class="gamified-icon icon-coin-stack" aria-hidden="true"></span>
            </div>
            <div class="stat-card__content">
                <div class="stat-card__value">
                    <div class="stat-card__value-head">
                        <span class="stat-card__value-number" id="coins-earned-today">{$i18n.formatNumber(coinsEarnedToday)}</span>
                        <span class="stat-card__value-unit gamified-icon icon-coin-stack" aria-hidden="true"></span>
                    </div>
                    <div class="stat-card__value-note" id="coins-daily-limit">
                        {dailyCoinLimit > 0
                            ? tHistory('history.dailyLimitValue', { amount: $i18n.formatNumber(dailyCoinLimit) })
                            : tHistory('history.dailyLimitUnlimited')}
                    </div>
                </div>
                <div class="stat-card__label">{tHistory('history.earnedToday')}</div>
                <div class="stat-card__progress stat-card__progress--coins">
                    <div class="progress-bar" id="coins-daily-progress" style="width:{coinsPercent}%"></div>
                </div>
            </div>
        </div>

        <div class="stat-card">
            <div class="stat-card__icon" id="large-icon">
                <span class="gamified-icon icon-empty" aria-hidden="true"></span>
            </div>
            <div class="stat-card__content">
                <div class="stat-card__value" id="large-purchase">
                    {largePurchase
                        ? tHistory('history.largePurchaseValue', {
                            amount: $i18n.formatNumber(Math.abs(largePurchase.amount ?? 0)),
                            title: largePurchase.ui.title,
                        })
                        : tHistory('history.largePurchaseNone')}
                </div>
                <div class="stat-card__label">{tHistory('history.largePurchaseTitle')}</div>
            </div>
        </div>
    </div>

    {#if historyGroups.length > 0}
    <div class="history-list history-list--transactions" id="history-list">
        {#each historyGroups as group (group.key)}
        {@const isCollapsed = isHistoryGroupCollapsed(group)}
        <div class="history-month">
            <button
                class="history-month-header history-month-header--button"
                type="button"
                aria-expanded={!isCollapsed}
                aria-controls={historyGroupPanelId(group.key)}
                aria-label={tHistory('history.groupToggleAria', { title: historyGroupLabel(group) })}
                on:click={() => toggleHistoryGroup(group)}>
                <span class="month-title">
                    <span class="history-month-header__chevron" aria-hidden="true">{isCollapsed ? '▸' : '▾'}</span>
                    {historyGroupLabel(group)}
                </span>
                <span class="month-stats">
                    {#if group.earned > 0}<span class="earn">{tHistory('history.monthEarned', { amount: $i18n.formatNumber(group.earned) })}</span>{/if}
                    {#if group.spent > 0}&nbsp;<span class="spend">{tHistory('history.monthSpent', { amount: $i18n.formatNumber(group.spent) })}</span>{/if}
                </span>
            </button>
            {#if !isCollapsed}
            <div id={historyGroupPanelId(group.key)} class="cards history-transaction-list" class:cards--list={viewMode === 'list'}>
            {#each group.entries as entry (entry.id)}
            <article
                class="card history-transaction-card history-transaction-card--{cssType(entry.type as string)}"
                class:history-transaction-card--list={viewMode === 'list'}>
                <div class="card__badge-row">
                    <span class={`card__badge ${cssType(entry.type as string) === 'earn' ? 'request-chip--type-task' : 'request-chip--type-purchase'}`}>{historyTypeLabel(entry)}</span>
                    {#if entry.ui.group}
                    <span class="card__badge card__badge--group">{entry.ui.group}</span>
                    {/if}
                    {#if formatDate(entry.createdAt as string)}
                    <span class="card__badge request-chip--muted">{formatDate(entry.createdAt as string)}</span>
                    {/if}
                </div>
                <div class="history-transaction-card__layout">
                    <div class="history-transaction-card__main">
                    <CardHeader
                        title={entry.ui.title}
                        amount={historyAmount(entry)}
                        amountClass={cssType(entry.type as string) === 'earn' ? 'task-coins' : 'item-coins'}
                        amountNote={historyMoneyLabel(entry.ui.moneyAmount)}
                        compactChips={historyCompactChips(entry)}
                    />
                    {#if entry.ui.description}
                    <p class="card__comment">{entry.ui.description}</p>
                    {/if}
                    </div>
                    <div class="history-transaction-card__side">
                    <div class="card__meta">
                        {#if historyMeta(entry)}
                        <span class="card__meta-item">{historyMeta(entry)}</span>
                        {/if}
                    </div>
                    {#if historyMoneyLabel(entry.ui.moneyAmount)}
                    <span class="history-transaction-card__money-price">{historyMoneyLabel(entry.ui.moneyAmount)}</span>
                    {/if}
                    {#if isAdmin}
                    <div class="card__actions history-transaction-card__actions">
                        <button class="history-item__delete-btn" on:click={() => handleDelete(entry.id)} aria-label={tHistory('history.deleteAria')}>✕</button>
                    </div>
                    {/if}
                    </div>
                </div>
            </article>
            {/each}
            </div>
            {/if}
        </div>
        {/each}
    </div>
    {:else}
    <div class="empty-state" id="history-empty">
        <span class="empty-state__icon">
            <span class="gamified-icon icon-empty" aria-hidden="true"></span>
        </span>
        <p class="empty-state__title">{tHistory('history.emptyTitle')}</p>
        <p class="empty-state__hint">{tHistory('history.emptyHint')}</p>
    </div>
    {/if}
</section>

<style>
    .history-section__actions {
        display: inline-flex;
        align-items: center;
        gap: 0.55rem;
        flex-wrap: wrap;
    }

    .history-list--transactions {
        gap: 1rem;
    }

    .history-transaction-list {
        margin-top: 0.6rem;
    }

    .history-month-header--button {
        width: 100%;
        border: 0;
        border-left: 4px solid var(--color-primary);
        background: rgba(255, 255, 255, 0.05);
        color: inherit;
        cursor: pointer;
        font: inherit;
        text-align: left;
    }

    .history-month-header--button:hover,
    .history-month-header--button:focus-visible {
        filter: brightness(0.98);
    }

    .history-month-header__chevron {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 1rem;
        margin-right: 0.25rem;
    }

    .cards--list {
        grid-template-columns: minmax(0, 1fr);
        gap: 0.35rem;
    }

    .history-transaction-card {
        min-height: 312px;
    }

    .history-transaction-card--spend .card__coins {
        background: linear-gradient(135deg, rgba(239, 68, 68, 0.9), rgba(190, 70, 52, 0.9));
        color: white;
    }

    .history-transaction-card--earn .card__coins {
        background: var(--gradient-success);
        color: white;
    }

    .history-transaction-card__layout {
        display: flex;
        flex-direction: column;
        gap: 0.9rem;
        height: 100%;
    }

    .history-transaction-card__main,
    .history-transaction-card__side {
        display: flex;
        flex-direction: column;
        gap: 0.8rem;
    }

    .history-transaction-card__side {
        margin-top: auto;
    }

    .history-transaction-card__money-price {
        display: inline-flex;
        align-items: center;
        width: fit-content;
        padding: 0.18rem 0.46rem;
        border-radius: 999px;
        background: rgba(245, 158, 11, 0.12);
        color: #8a6118;
        font-size: 0.76rem;
        font-weight: 800;
        line-height: 1;
        white-space: nowrap;
    }

    .history-transaction-card--list {
        min-height: 0;
        height: auto;
        padding: 0.4rem 0.75rem;
    }

    .history-transaction-card--list .card__badge-row,
    .history-transaction-card--list .card__comment,
    .history-transaction-card--list .card__meta {
        display: none;
    }

    .history-transaction-card--list .history-transaction-card__layout {
        flex-direction: row;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.5rem 0.75rem;
    }

    .history-transaction-card--list .history-transaction-card__main {
        flex: 1 1 0;
        min-width: 0;
    }

    .history-transaction-card--list .history-transaction-card__side {
        flex-direction: row;
        align-items: center;
        gap: 0.4rem;
        flex-shrink: 0;
        margin-top: 0;
    }

    .history-transaction-card--list .history-transaction-card__actions {
        flex-wrap: nowrap;
        gap: 0.4rem;
        justify-content: flex-end;
        margin-top: 0;
    }

    .history-transaction-card--list .history-item__delete-btn {
        width: 2.2rem;
        height: 2.2rem;
    }

    @media (max-width: 640px) {
        .history-section__actions {
            width: 100%;
        }

        .history-transaction-card {
            min-height: 0;
        }

        .history-transaction-card--list {
            padding: 0.38rem 0.46rem 0.38rem 0.56rem;
        }

        .history-transaction-card--list .history-transaction-card__layout {
            display: grid;
            grid-template-columns: minmax(0, 1fr) auto;
            align-items: stretch;
            gap: 0.48rem;
        }

        .history-transaction-card--list .history-transaction-card__side {
            align-self: stretch;
            align-items: stretch;
            gap: 0;
        }

        .history-transaction-card--list .history-transaction-card__money-price {
            display: none;
        }
    }
</style>
