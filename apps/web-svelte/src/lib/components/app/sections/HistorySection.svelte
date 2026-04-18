<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { deleteHistoryItem } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';
    import { scheduleSave } from '$lib/services/save';

    $: history = $appStore.history;
    $: isAdmin = $appStore.isAdmin;
    $: monthlyLimit = $appStore.monthlyLimit;
    $: dailyCoinLimit = $appStore.dailyCoinLimit;

    // Budget stats
    $: thisMonth = (() => {
        const now = new Date();
        return history.filter(h => {
            if (!h.createdAt) return false;
            const d = new Date(h.createdAt as string);
            return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
        });
    })();

    $: moneySpent = thisMonth.filter(h => h.type === 'spend').reduce((s, h) => s + (h.amount ?? 0), 0);
    $: spentPercent = monthlyLimit > 0 ? Math.min(100, Math.round((moneySpent / monthlyLimit) * 100)) : 0;
    $: moneyRemaining = Math.max(0, monthlyLimit - moneySpent);

    const today = new Date();
    $: coinsEarnedToday = history.filter(h => {
        if (h.type !== 'earn' || !h.createdAt) return false;
        const d = new Date(h.createdAt as string);
        return d.toDateString() === today.toDateString();
    }).reduce((s, h) => s + (h.amount ?? 0), 0);

    $: coinsPercent = dailyCoinLimit > 0 ? Math.min(100, Math.round((coinsEarnedToday / dailyCoinLimit) * 100)) : 0;

    $: largePurchase = (() => {
        const spends = thisMonth.filter(h => h.type === 'spend');
        if (!spends.length) return null;
        return spends.reduce((max, h) => (h.amount ?? 0) > (max.amount ?? 0) ? h : max);
    })();

    function formatDate(dateStr: string | null | undefined): string {
        if (!dateStr) return '';
        try { return new Date(dateStr as string).toLocaleDateString('ru-RU'); } catch { return ''; }
    }

    async function handleDelete(historyId: unknown) {
        const ok = await deleteHistoryItem(historyId);
        if (ok) {
            appStore.setState({ history: history.filter(h => h.id !== historyId) });
            scheduleSave();
            showToast('Запись удалена', 'info');
        }
    }

    async function clearAll() {
        if (!confirm('Очистить всю историю?')) return;
        appStore.setState({ history: [] });
        scheduleSave();
        showToast('История очищена', 'success');
    }

    function typeIcon(type: string): string {
        return type === 'earn' ? 'icon-coin' : type === 'spend' ? 'icon-shop' : 'icon-empty';
    }
</script>

<section class="section hidden" id="history-section">
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
                    {largePurchase ? `${largePurchase.amount} монет — ${largePurchase.title ?? '—'}` : 'Нет'}
                </div>
                <div class="stat-card__label">Крупная покупка месяца</div>
            </div>
        </div>
    </div>

    {#if history.length > 0}
    <div class="history-list" id="history-list">
        {#each history as entry (entry.id)}
        <article class="history-item history-item--{entry.type}">
            <div class="history-item__icon">
                <span class="gamified-icon {typeIcon(entry.type as string)}" aria-hidden="true"></span>
            </div>
            <div class="history-item__body">
                <p class="history-item__title">{entry.title ?? (entry.type === 'earn' ? 'Задание' : 'Покупка')}</p>
                <p class="history-item__meta">{formatDate(entry.createdAt as string)}</p>
            </div>
            <div class="history-item__amount history-item__amount--{entry.type}">
                {entry.type === 'earn' ? '+' : '-'}{entry.amount}
            </div>
            {#if isAdmin}
            <button class="btn btn--ghost btn--small" on:click={() => handleDelete(entry.id)} aria-label="Удалить запись">✕</button>
            {/if}
        </article>
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
