<script lang="ts">
    import { browser } from '$app/environment';
    import CardHeader from '$lib/components/app/CardHeader.svelte';
    import SectionHeaderControls from '$lib/components/app/SectionHeaderControls.svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { deleteHistoryItem } from '$lib/services/api';
    import { loadCardViewMode, saveCardViewMode, type CardViewMode, type CardViewRole } from '$lib/services/cardViewMode';
    import { appStore } from '$lib/stores/app';
    import type { HistoryEntry } from '$lib/stores/app';
    import { showToast } from '$lib/stores/toasts';
    import { buildHistoryCatalog, resolveHistoryCard } from './historyDetails';
    import type { HistoryCardDetails, HistoryDetailsI18n } from './historyDetails';
    import { groupHistoryEntries, type HistoryGroup } from './historyGroups';
    import { buildHistoryStats, type HistoryStatsRange } from './historyStats';

    type HistoryViewEntry = HistoryEntry & { ui: HistoryCardDetails };
    type HistoryRangeOption = { value: HistoryStatsRange; labelKey: string };

    const i18n = useI18n();
    const historyRangeOptions: HistoryRangeOption[] = [
        { value: '3m', labelKey: 'history.period3m' },
        { value: '6m', labelKey: 'history.period6m' },
        { value: 'all', labelKey: 'history.periodAll' },
    ];

    let viewMode: CardViewMode = 'list';
    let statsRange: HistoryStatsRange = '6m';
    const loadedViewRole: { value: CardViewRole | null } = { value: null };

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

    $: historyStats = buildHistoryStats({
        entries: historyEntries,
        range: statsRange,
        getAmount: entry => entry.amount ?? 0,
        getCreatedAt: entry => entry.createdAt as string,
        getKind: entry => historyKind(entry.type),
    });
    $: averageMonthlySpent = historyStats.activeMonths > 0 ? historyStats.totalSpent / historyStats.activeMonths : 0;
    $: spentPercent = monthlyLimit > 0 ? Math.min(100, Math.round((averageMonthlySpent / monthlyLimit) * 100)) : 0;
    $: moneyRemaining = monthlyLimit > 0 ? Math.max(0, monthlyLimit - averageMonthlySpent) : 0;
    $: trendMax = Math.max(...historyStats.monthly.map(month => Math.max(month.earned, month.spent)), 1);

    function formatDate(dateStr: string | null | undefined): string {
        if (!dateStr) return '';
        try {
            return $i18n.formatDateTime(new Date(dateStr as string));
        } catch {
            return '';
        }
    }

    function monthLabel(key: string): string {
        if (!key) return '';
        const [y, m] = key.split('-');
        return $i18n.formatDate(new Date(Number(y), Number(m) - 1, 1), { month: 'short', year: 'numeric' });
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
        return hasMoneyAmount(value)
            ? tHistory('history.moneyAmountLabel', { amount: $i18n.formatNumber(value) })
            : '';
    }

    function historyTypeLabel(entry: HistoryViewEntry): string {
        return cssType(entry.type as string) === 'earn'
            ? tHistory('model.requestTypeTask')
            : tHistory('model.requestTypePurchase');
    }

    function historyTypeShortLabel(entry: HistoryViewEntry): string {
        return cssType(entry.type as string) === 'earn' ? 'TASK' : 'SHOP';
    }

    function historyCompactChips(entry: HistoryViewEntry) {
        const chips: Array<{ label: string; className?: string }> = [{ label: historyTypeLabel(entry) }];
        if (entry.ui.group) {
            chips.push({ label: entry.ui.group, className: 'card__compact-chip--group' });
        }
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

    function setStatsRange(nextRange: HistoryStatsRange) {
        statsRange = nextRange;
    }

    function historyRangeLabel(range: HistoryStatsRange): string {
        const option = historyRangeOptions.find(item => item.value === range);
        return option ? tHistory(option.labelKey) : '';
    }

    function trendBarHeight(value: number): number {
        return Math.max(8, Math.round((value / trendMax) * 100));
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

    <div class="history-overview">
        <div class="history-overview__copy">
            <p class="history-overview__eyebrow">{tHistory('history.overviewTitle')}</p>
            <p class="history-overview__hint">{tHistory('history.overviewHint')}</p>
        </div>
        <div class="history-range" aria-label={tHistory('history.periodLabel')}>
            {#each historyRangeOptions as option (option.value)}
            <button
                type="button"
                class:history-range__button={true}
                class:history-range__button--active={statsRange === option.value}
                on:click={() => setStatsRange(option.value)}
            >
                {tHistory(option.labelKey)}
            </button>
            {/each}
        </div>
    </div>

    <div class="budget-stats history-stats-grid" id="budget-stats">
        <div class="stat-card stat-card--spend">
            <div class="stat-card__icon">
                <span class="gamified-icon icon-wallet" aria-hidden="true"></span>
            </div>
            <div class="stat-card__content">
                <div class="stat-card__value">
                    {$i18n.formatNumber(historyStats.totalSpent)}
                </div>
                <div class="stat-card__label">{tHistory('history.spentInPeriod')}</div>
                <div class="stat-card__value-note">
                    {monthlyLimit > 0
                        ? tHistory('history.limitLabel', { amount: $i18n.formatNumber(monthlyLimit) })
                        : tHistory('history.limitUnlimited')}
                </div>
            </div>
            <div class="stat-card__progress">
                <div class="progress-bar warning" id="money-progress" style={`width:${spentPercent}%`}></div>
            </div>
            {#if monthlyLimit > 0}
            <div class="stat-card__meta">
                {tHistory('history.remaining', { amount: $i18n.formatNumber(moneyRemaining) })}
            </div>
            {/if}
        </div>

        <div class="stat-card stat-card--earn">
            <div class="stat-card__icon">
                <span class="gamified-icon icon-coin-stack" aria-hidden="true"></span>
            </div>
            <div class="stat-card__content">
                <div class="stat-card__value">
                    {$i18n.formatNumber(historyStats.totalEarned)}
                </div>
                <div class="stat-card__label">{tHistory('history.earnedInPeriod')}</div>
                <div class="stat-card__value-note">
                    {tHistory('history.earnedVsSpent', {
                        earned: $i18n.formatNumber(historyStats.totalEarned),
                        spent: $i18n.formatNumber(historyStats.totalSpent),
                    })}
                </div>
            </div>
        </div>

        <div class="stat-card stat-card--task">
            <div class="stat-card__icon">
                <span class="history-stat-glyph" aria-hidden="true">TK</span>
            </div>
            <div class="stat-card__content">
                <div class="stat-card__value">
                    {$i18n.formatNumber(historyStats.taskCount)}
                </div>
                <div class="stat-card__label">{tHistory('history.taskCountTitle')}</div>
                <div class="stat-card__value-note">
                    {tHistory('history.taskCountValue', { amount: $i18n.formatNumber(historyStats.taskCount) })}
                </div>
            </div>
        </div>

        <div class="stat-card stat-card--purchase">
            <div class="stat-card__icon">
                <span class="history-stat-glyph" aria-hidden="true">SP</span>
            </div>
            <div class="stat-card__content">
                <div class="stat-card__value stat-card__value--title" id="large-purchase">
                    {historyStats.largestPurchase
                        ? tHistory('history.largePurchaseValue', {
                            amount: $i18n.formatNumber(Math.abs(historyStats.largestPurchase.amount ?? 0)),
                            title: historyStats.largestPurchase.ui.title,
                        })
                        : tHistory('history.largePurchaseNone')}
                </div>
                <div class="stat-card__label">{tHistory('history.largePurchaseTitle')}</div>
                <div class="stat-card__value-note">
                    {historyStats.purchaseCount > 0
                        ? `${tHistory('history.purchaseCountValue', { amount: $i18n.formatNumber(historyStats.purchaseCount) })} · ${tHistory('history.averagePurchaseTitle')}: ${$i18n.formatNumber(Math.round(historyStats.averageSpentPerPurchase))}`
                        : tHistory('history.averagePurchaseNone')}
                </div>
            </div>
        </div>
    </div>

    {#if historyStats.monthly.length > 0}
    <div class="history-trend">
        <div class="history-trend__header">
            <div>
                <h3>{tHistory('history.trendTitle')}</h3>
                <p>{tHistory('history.trendHint')}</p>
            </div>
            <span class="history-trend__range">{historyRangeLabel(statsRange)}</span>
        </div>
        <div class="history-trend__chart">
            {#each historyStats.monthly as month (month.monthKey)}
            <div class="history-trend__month">
                <div class="history-trend__bars">
                    <div
                        class="history-trend__bar history-trend__bar--earned"
                        style={`height:${trendBarHeight(month.earned)}%`}
                        title={`${tHistory('history.trendEarned')}: ${$i18n.formatNumber(month.earned)}`}
                    ></div>
                    <div
                        class="history-trend__bar history-trend__bar--spent"
                        style={`height:${trendBarHeight(month.spent)}%`}
                        title={`${tHistory('history.trendSpent')}: ${$i18n.formatNumber(month.spent)}`}
                    ></div>
                </div>
                <div class="history-trend__month-label">{monthLabel(month.monthKey)}</div>
                <div class="history-trend__month-meta">
                    <span class="history-trend__month-chip history-trend__month-chip--earned">
                        +{$i18n.formatNumber(month.earned)}
                    </span>
                    <span class="history-trend__month-chip history-trend__month-chip--spent">
                        -{$i18n.formatNumber(month.spent)}
                    </span>
                </div>
                <div class="history-trend__month-foot">
                    {tHistory('history.activityCount', { amount: month.totalCount })}
                </div>
            </div>
            {/each}
        </div>
    </div>
    {/if}

    {#if historyGroups.length > 0}
    <div class="history-list history-list--transactions" id="history-list">
        {#each historyGroups as group (group.key)}
        <details class="history-month" open={!group.collapsedByDefault}>
            <summary class="history-month-header history-month-header--summary">
                <span class="month-title">{historyGroupLabel(group)}</span>
                <span class="month-stats">
                    <span class="month-stats__count">
                        {tHistory('history.groupEntryCount', { amount: group.entries.length })}
                    </span>
                    {#if group.earned > 0}<span class="earn">{tHistory('history.monthEarned', { amount: $i18n.formatNumber(group.earned) })}</span>{/if}
                    {#if group.spent > 0}<span class="spend">{tHistory('history.monthSpent', { amount: $i18n.formatNumber(group.spent) })}</span>{/if}
                </span>
            </summary>
            <div class="cards history-transaction-list" class:cards--list={viewMode === 'list'}>
            {#each group.entries as entry (entry.id)}
            <article
                class="card history-transaction-card history-transaction-card--{cssType(entry.type as string)}"
                class:history-transaction-card--list={viewMode === 'list'}
            >
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
                    <div class={`history-transaction-card__marker history-transaction-card__marker--${cssType(entry.type as string)}`}>
                        <span>{historyTypeShortLabel(entry)}</span>
                    </div>
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
                        <div class="history-transaction-card__metrics">
                            {#if historyMoneyLabel(entry.ui.moneyAmount)}
                            <span class="history-transaction-card__money-price">{historyMoneyLabel(entry.ui.moneyAmount)}</span>
                            {/if}
                            <span class={`history-transaction-card__kind history-transaction-card__kind--${cssType(entry.type as string)}`}>
                                {historyTypeLabel(entry)}
                            </span>
                        </div>
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
        </details>
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

    .history-section__actions :global(.section-controls) {
        width: auto;
        flex: 0 0 auto;
    }

    .history-overview {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 1rem;
        margin-bottom: 1rem;
        padding: 1rem 1.1rem;
        border: 1px solid rgba(15, 23, 42, 0.08);
        border-radius: 1.25rem;
        background:
            radial-gradient(circle at top left, rgba(56, 189, 248, 0.16), transparent 42%),
            linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(246, 249, 252, 0.94));
        box-shadow: 0 18px 40px rgba(148, 163, 184, 0.14);
    }

    .history-overview__copy {
        display: grid;
        gap: 0.3rem;
        max-width: 42rem;
    }

    .history-overview__eyebrow {
        margin: 0;
        color: #0f766e;
        font-size: 0.8rem;
        font-weight: 900;
        letter-spacing: 0.08em;
        text-transform: uppercase;
    }

    .history-overview__hint {
        margin: 0;
        color: #475569;
        font-size: 0.95rem;
        line-height: 1.45;
    }

    .history-range {
        display: inline-flex;
        flex-wrap: wrap;
        gap: 0.45rem;
        padding: 0.3rem;
        border-radius: 999px;
        background: rgba(226, 232, 240, 0.76);
    }

    .history-range__button {
        border: 0;
        border-radius: 999px;
        background: transparent;
        color: #475569;
        font-size: 0.83rem;
        font-weight: 800;
        line-height: 1;
        padding: 0.62rem 0.88rem;
        cursor: pointer;
        transition: background-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
    }

    .history-range__button:hover,
    .history-range__button:focus-visible {
        background: rgba(255, 255, 255, 0.82);
        color: #0f172a;
        outline: none;
    }

    .history-range__button--active {
        background: linear-gradient(135deg, #0f766e, #2563eb);
        color: #fff;
        box-shadow: 0 10px 22px rgba(37, 99, 235, 0.22);
    }

    .history-stats-grid {
        margin-bottom: 1rem;
    }

    .history-stat-glyph {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 1.7rem;
        min-height: 1.7rem;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.28);
        color: inherit;
        font-size: 0.72rem;
        font-weight: 900;
        letter-spacing: 0.08em;
    }

    .stat-card--spend {
        background: linear-gradient(135deg, rgba(255, 251, 235, 0.98), rgba(255, 237, 213, 0.96));
    }

    .stat-card--earn {
        background: linear-gradient(135deg, rgba(236, 253, 245, 0.98), rgba(209, 250, 229, 0.96));
    }

    .stat-card--task {
        background: linear-gradient(135deg, rgba(239, 246, 255, 0.98), rgba(219, 234, 254, 0.96));
    }

    .stat-card--purchase {
        background: linear-gradient(135deg, rgba(250, 245, 255, 0.98), rgba(243, 232, 255, 0.96));
    }

    .stat-card__value--title {
        font-size: 1.02rem;
        line-height: 1.35;
    }

    .history-trend {
        margin-bottom: 1rem;
        padding: 1rem 1.1rem 1.1rem;
        border-radius: 1.25rem;
        border: 1px solid rgba(15, 23, 42, 0.08);
        background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96));
        box-shadow: 0 16px 34px rgba(148, 163, 184, 0.12);
    }

    .history-trend__header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 0.8rem;
        margin-bottom: 0.9rem;
    }

    .history-trend__header h3,
    .history-trend__header p {
        margin: 0;
    }

    .history-trend__header h3 {
        font-size: 1rem;
    }

    .history-trend__header p {
        color: #64748b;
        font-size: 0.88rem;
        line-height: 1.4;
    }

    .history-trend__range {
        display: inline-flex;
        align-items: center;
        padding: 0.34rem 0.65rem;
        border-radius: 999px;
        background: rgba(37, 99, 235, 0.1);
        color: #1d4ed8;
        font-size: 0.76rem;
        font-weight: 800;
    }

    .history-trend__chart {
        display: grid;
        grid-auto-flow: column;
        grid-auto-columns: minmax(5.5rem, 1fr);
        gap: 0.8rem;
        overflow-x: auto;
        padding-bottom: 0.2rem;
    }

    .history-trend__month {
        display: grid;
        gap: 0.45rem;
        min-width: 0;
        padding: 0.85rem 0.7rem 0.75rem;
        border-radius: 1rem;
        background: linear-gradient(180deg, rgba(241, 245, 249, 0.9), rgba(255, 255, 255, 0.94));
        border: 1px solid rgba(148, 163, 184, 0.12);
    }

    .history-trend__bars {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 0.45rem;
        align-items: end;
        height: 8rem;
    }

    .history-trend__bar {
        border-radius: 0.8rem 0.8rem 0.3rem 0.3rem;
        min-height: 0.5rem;
    }

    .history-trend__bar--earned {
        background: linear-gradient(180deg, #10b981, #047857);
    }

    .history-trend__bar--spent {
        background: linear-gradient(180deg, #fb7185, #ea580c);
    }

    .history-trend__month-label {
        color: #0f172a;
        font-size: 0.8rem;
        font-weight: 800;
    }

    .history-trend__month-meta {
        display: flex;
        flex-wrap: wrap;
        gap: 0.28rem;
    }

    .history-trend__month-chip {
        display: inline-flex;
        align-items: center;
        padding: 0.18rem 0.42rem;
        border-radius: 999px;
        font-size: 0.67rem;
        font-weight: 800;
    }

    .history-trend__month-chip--earned {
        background: rgba(16, 185, 129, 0.12);
        color: #047857;
    }

    .history-trend__month-chip--spent {
        background: rgba(244, 63, 94, 0.12);
        color: #c2410c;
    }

    .history-trend__month-foot {
        color: #64748b;
        font-size: 0.72rem;
        font-weight: 700;
    }

    .history-list--transactions {
        gap: 1rem;
    }

    .history-transaction-list {
        margin-top: 0.6rem;
    }

    .history-month-header--summary {
        cursor: pointer;
        list-style: none;
        user-select: none;
    }

    .history-month-header--summary::-webkit-details-marker {
        display: none;
    }

    .history-month-header--summary:hover,
    .history-month-header--summary:focus-visible {
        filter: brightness(0.98);
    }

    .history-month-header--summary .month-title::before {
        content: '▸';
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 1rem;
        margin-right: 0.25rem;
    }

    .history-month[open] .history-month-header--summary .month-title::before {
        content: '▾';
    }

    .month-stats {
        display: inline-flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: flex-end;
        gap: 0.55rem;
    }

    .month-stats__count {
        color: #64748b;
        font-size: 0.76rem;
        font-weight: 700;
    }

    .cards--list {
        grid-template-columns: minmax(0, 1fr);
        gap: 0.35rem;
    }

    .history-transaction-card {
        min-height: 0;
        border: 1px solid rgba(148, 163, 184, 0.14);
        box-shadow: 0 14px 34px rgba(148, 163, 184, 0.1);
    }

    .history-transaction-card--spend {
        background: linear-gradient(145deg, rgba(255, 251, 235, 0.96), rgba(255, 255, 255, 0.98));
    }

    .history-transaction-card--spend .card__coins {
        background: linear-gradient(135deg, rgba(239, 68, 68, 0.9), rgba(190, 70, 52, 0.9));
        color: white;
    }

    .history-transaction-card--earn {
        background: linear-gradient(145deg, rgba(236, 253, 245, 0.96), rgba(255, 255, 255, 0.98));
    }

    .history-transaction-card--earn .card__coins {
        background: var(--gradient-success);
        color: white;
    }

    .history-transaction-card__layout {
        display: grid;
        grid-template-columns: auto minmax(0, 1fr) auto;
        gap: 0.9rem;
        align-items: stretch;
        height: 100%;
    }

    .history-transaction-card__marker {
        display: inline-flex;
        align-items: flex-start;
        justify-content: center;
        min-width: 3rem;
    }

    .history-transaction-card__marker span {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 2.7rem;
        min-height: 2.7rem;
        padding: 0.35rem;
        border-radius: 1rem;
        font-size: 0.68rem;
        font-weight: 900;
        letter-spacing: 0.08em;
        writing-mode: vertical-rl;
        transform: rotate(180deg);
    }

    .history-transaction-card__marker--earn span {
        background: linear-gradient(180deg, rgba(16, 185, 129, 0.14), rgba(5, 150, 105, 0.22));
        color: #047857;
    }

    .history-transaction-card__marker--spend span {
        background: linear-gradient(180deg, rgba(251, 191, 36, 0.16), rgba(249, 115, 22, 0.2));
        color: #c2410c;
    }

    .history-transaction-card__main,
    .history-transaction-card__side {
        display: flex;
        flex-direction: column;
        gap: 0.8rem;
    }

    .history-transaction-card__side {
        min-width: 10rem;
        margin-top: auto;
        align-items: flex-end;
    }

    .history-transaction-card__metrics {
        display: flex;
        flex-wrap: wrap;
        justify-content: flex-end;
        gap: 0.4rem;
    }

    .history-transaction-card__money-price,
    .history-transaction-card__kind {
        display: inline-flex;
        align-items: center;
        width: fit-content;
        padding: 0.18rem 0.46rem;
        border-radius: 999px;
        font-size: 0.76rem;
        font-weight: 800;
        line-height: 1;
        white-space: nowrap;
    }

    .history-transaction-card__money-price {
        background: rgba(245, 158, 11, 0.12);
        color: #8a6118;
    }

    .history-transaction-card__kind--earn {
        background: rgba(16, 185, 129, 0.12);
        color: #047857;
    }

    .history-transaction-card__kind--spend {
        background: rgba(249, 115, 22, 0.12);
        color: #c2410c;
    }

    .history-transaction-card--list {
        min-height: 0;
        height: auto;
        padding: 0.45rem 0.75rem;
    }

    .history-transaction-card--list .card__badge-row,
    .history-transaction-card--list .card__comment,
    .history-transaction-card--list .card__meta,
    .history-transaction-card--list .history-transaction-card__marker,
    .history-transaction-card--list .history-transaction-card__kind {
        display: none;
    }

    .history-transaction-card--list .history-transaction-card__layout {
        grid-template-columns: minmax(0, 1fr) auto;
        gap: 0.5rem 0.75rem;
        align-items: center;
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
        min-width: 0;
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

    @media (max-width: 900px) {
        .history-overview,
        .history-trend__header,
        .history-transaction-card__layout {
            grid-template-columns: none;
            flex-direction: column;
        }

        .history-overview,
        .history-trend__header {
            align-items: stretch;
        }

        .history-transaction-card__layout {
            display: flex;
        }

        .history-transaction-card__side {
            min-width: 0;
            align-items: flex-start;
        }

        .history-transaction-card__metrics {
            justify-content: flex-start;
        }
    }

    @media (max-width: 640px) {
        .history-section__actions {
            display: grid;
            grid-template-columns: minmax(0, 1fr) auto;
            width: 100%;
            align-items: center;
            gap: 0.5rem;
            flex-wrap: nowrap;
        }

        .history-section__actions :global(.section-controls) {
            width: auto;
            min-width: 0;
        }

        .history-section__actions :global(.section-controls__tools) {
            justify-self: start;
        }

        #clear-history-btn {
            justify-self: end;
            white-space: nowrap;
        }

        .history-overview {
            padding: 0.9rem;
        }

        .history-range {
            width: 100%;
            justify-content: space-between;
        }

        .history-range__button {
            flex: 1 1 0;
            text-align: center;
        }

        .history-trend {
            padding: 0.9rem;
        }

        .history-trend__chart {
            grid-auto-columns: minmax(5rem, 1fr);
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
