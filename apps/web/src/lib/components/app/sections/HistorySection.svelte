<script lang="ts">
    import { SvelteMap } from 'svelte/reactivity';
    import { appStore } from '$lib/stores/app';
    import type { HistoryEntry } from '$lib/stores/app';
    import { deleteHistoryItem } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';
    import { buildHistoryCatalog, resolveHistoryCard } from './historyDetails';
    import type { HistoryCardDetails } from './historyDetails';

    type HistoryViewEntry = HistoryEntry & { ui: HistoryCardDetails };

    $: history = $appStore.history;
    $: isAdmin = $appStore.isAdmin;
    $: monthlyLimit = $appStore.monthlyLimit;
    $: dailyCoinLimit = $appStore.dailyCoinLimit;
    $: historyCatalog = buildHistoryCatalog({
        tasks: $appStore.tasks,
        shopItems: $appStore.shopItems,
        baseTasks: $appStore.baseData.tasks,
        baseProducts: $appStore.baseData.products,
    });
    $: historyEntries = history.map(entry => ({ ...entry, ui: resolveHistoryCard(entry, historyCatalog) })) as HistoryViewEntry[];

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
            return new Date(dateStr as string).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
        } catch { return ''; }
    }

    function monthKey(dateStr: string | null | undefined): string {
        if (!dateStr) return '';
        try {
            const d = new Date(dateStr as string);
            return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        } catch { return ''; }
    }

    function monthLabel(key: string): string {
        if (!key) return '';
        const [y, m] = key.split('-');
        return new Date(Number(y), Number(m) - 1, 1).toLocaleDateString('ru-RU', { month: 'long', year: 'numeric' });
    }

    // Group history by month
    $: monthGroups = (() => {
        const map = new SvelteMap<string, { entries: HistoryViewEntry[]; earned: number; spent: number }>();
        for (const h of historyEntries) {
            const key = monthKey(h.createdAt as string);
            if (!map.has(key)) map.set(key, { entries: [], earned: 0, spent: 0 });
            const g = map.get(key)!;
            g.entries.push(h);
            if (historyKind(h.type) === 'earn') g.earned += (h.amount ?? 0);
            else if (historyKind(h.type) === 'spend') g.spent += Math.abs(h.amount ?? 0);
        }
        return [...map.entries()].sort((a, b) => b[0].localeCompare(a[0]));
    })();

    async function handleDelete(historyId: unknown) {
        const ok = await deleteHistoryItem(historyId, $appStore.currentChildId);
        if (ok) {
            appStore.setState({ history: history.filter(h => h.id !== historyId) });
            showToast('Запись удалена', 'info');
        }
    }

    async function clearAll() {
        if (!confirm('Очистить всю историю?')) return;
        appStore.setState({ history: [] });
        showToast('История очищена', 'success');
    }

    function cssType(type: string): string {
        const kind = historyKind(type);
        return kind === 'other' ? type : kind;
    }

    function historyMeta(entry: HistoryViewEntry): string {
        const parts: string[] = [];
        if (entry.ui.group) parts.push(entry.ui.group);
        const createdAt = formatDate(entry.createdAt as string);
        if (createdAt) parts.push(createdAt);
        return parts.join(' · ');
    }
</script>

<section class="section" id="history-section">
    <h2>История операций</h2>
    {#if isAdmin}
    <div class="section__buttons admin-only" style="margin-bottom: 1rem;">
        <button class="btn btn--danger btn--small" id="clear-history-btn" on:click={clearAll}>
            Очистить всё
        </button>
    </div>
    {/if}

    <!-- Budget stats -->
    <div class="budget-stats" id="budget-stats">
        <div class="stat-card">
            <div class="stat-card__icon">
                <span class="gamified-icon icon-wallet" aria-hidden="true"></span>
            </div>
            <div class="stat-card__content">
                <div class="stat-card__value">
                    <span id="money-spent">{moneySpent.toLocaleString('ru-RU')}</span>
                    /
                    <span id="money-limit">{monthlyLimit.toLocaleString('ru-RU')}</span>
                    {#if moneyRemaining > 0}
                    <span id="money-remaining" style="font-size:0.8em;font-weight:400;margin-left:4px;">
                        (ещё {moneyRemaining.toLocaleString('ru-RU')})
                    </span>
                    {/if}
                </div>
                <div class="stat-card__label">Потрачено в этом месяце</div>
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
                        <span class="stat-card__value-number" id="coins-earned-today">{coinsEarnedToday}</span>
                        <span class="stat-card__value-unit gamified-icon icon-coin-stack" aria-hidden="true"></span>
                    </div>
                    <div class="stat-card__value-note" id="coins-daily-limit">
                        {dailyCoinLimit > 0 ? `Лимит: ${dailyCoinLimit}` : 'Лимит: ∞'}
                    </div>
                </div>
                <div class="stat-card__label">Заработано сегодня</div>
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
                    {largePurchase ? `${Math.abs(largePurchase.amount)} монет — ${largePurchase.ui.title}` : 'Нет'}
                </div>
                <div class="stat-card__label">Крупная покупка месяца</div>
            </div>
        </div>
    </div>

    {#if monthGroups.length > 0}
    <div class="history-list" id="history-list">
        {#each monthGroups as [key, group] (key)}
        <div class="history-month">
            <div class="history-month-header">
                <span class="month-title">{monthLabel(key)}</span>
                <span class="month-stats">
                    {#if group.earned > 0}<span class="earn">+{group.earned} монет</span>{/if}
                    {#if group.spent > 0}&nbsp;<span class="spend">−{group.spent} монет</span>{/if}
                </span>
            </div>
            {#each group.entries as entry (entry.id)}
            <article class="history-item history-item--{cssType(entry.type as string)}">
                <div class="history-item__icon">
                    <span class="gamified-icon {cssType(entry.type as string) === 'earn' ? 'icon-coin-stack' : 'icon-shop'}" aria-hidden="true"></span>
                </div>
                <div class="history-item__body">
                    <p class="history-item__title">{entry.ui.title}</p>
                    {#if entry.ui.description}
                    <p class="history-item__note">{entry.ui.description}</p>
                    {/if}
                    <p class="history-item__meta">{historyMeta(entry)}</p>
                </div>
                <div class="history-item__actions">
                    <div class="history-item__amount history-item__amount--{cssType(entry.type as string)}">
                        <span>
                            {cssType(entry.type as string) === 'earn' ? '+' : '−'}{Math.abs(entry.amount ?? 0)}
                            <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width:0.9em;height:0.9em;vertical-align:middle;"></span>
                        </span>
                        <span class="history-item__money">{entry.ui.moneyAmount} 💶</span>
                    </div>
                    {#if isAdmin}
                    <button class="history-item__delete-btn" on:click={() => handleDelete(entry.id)} aria-label="Удалить запись">✕</button>
                    {/if}
                </div>
            </article>
            {/each}
        </div>
        {/each}
    </div>
    {:else}
    <div class="empty-state" id="history-empty">
        <span class="empty-state__icon">
            <span class="gamified-icon icon-empty" aria-hidden="true"></span>
        </span>
        <p class="empty-state__title">История пока пуста</p>
        <p class="empty-state__hint">Выполните или подтвердите первое действие — и запись появится тут.</p>
    </div>
    {/if}
</section>
