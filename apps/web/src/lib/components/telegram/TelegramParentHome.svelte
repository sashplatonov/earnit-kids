<script lang="ts">
    import { onMount } from 'svelte';
    import { appStore } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { adminAwardCoins } from '$lib/services/api';
    import { applyDataSnapshot } from '$lib/services/bootstrap';
    import { loadTelegramHistory } from '$lib/services/telegramActivity';
    import type { HistoryEntry } from '$lib/stores/app';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramCoinAdjust from './TelegramCoinAdjust.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramRequestList from './TelegramRequestList.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';
    import { formatLastUsedTime } from './telegramLastUsed';

    const i18n = useI18n();

    // EXPLAIN: Bot deep links (history/coins) open the exact home sub-context.
    export let initialContext = '';
    let showAll = false;
    let history: HistoryEntry[] = [];
    let historyLoading = false;
    let historyHasMore = false;
    let historyPage = 0;
    let historyError = '';
    let coinSheetOpen = false;
    let coinBusy = false;
    let coinError = '';

    $: pending = $appStore.requests.filter((request) => request.status === 'pending');
    $: visibleRequests = showAll ? pending : pending.slice(0, 2);

    async function loadHistory(reset = false) {
        if (historyLoading || $appStore.currentChildId == null) return;
        historyLoading = true;
        historyError = '';
        try {
            const page = await loadTelegramHistory($appStore.currentChildId, reset ? 1 : historyPage + 1, 10);
            history = reset ? page.items : [...history, ...page.items];
            historyPage = page.page;
            historyHasMore = page.items.length === page.limit;
        } catch {
            historyError = $i18n.t('app.telegram.childShell.activityError');
        }
        historyLoading = false;
    }

    async function adjustCoins(event: CustomEvent<{ amount: number; note: string | null }>) {
        const childId = $appStore.currentChildId;
        if (childId == null) {
            coinError = $i18n.t('app.telegram.family.chooseChildFirst');
            return;
        }
        coinBusy = true;
        coinError = '';
        const result = await adminAwardCoins(childId, event.detail.amount, event.detail.note ?? undefined);
        coinBusy = false;
        if (result) {
            applyDataSnapshot(result as Record<string, unknown>);
            coinSheetOpen = false;
        } else {
            coinError = $i18n.t('app.telegram.home.coinError');
        }
    }

    onMount(() => {
        if (initialContext === 'coins') {
            coinSheetOpen = true;
        }
        void loadHistory(true);
    });
</script>

<section class="home" aria-labelledby="parent-home-title">
    {#if pending.length}
        <div class="section-heading"><h2 id="parent-home-title">{$i18n.t('app.telegram.home.needsAttention')}</h2><span class="count">{pending.length}</span></div>
    {/if}
    <TelegramRequestList requests={visibleRequests} canDecide childId={$appStore.currentChildId} showHeading={false} emptyText={pending.length ? '' : $i18n.t('app.telegram.home.nothingNeedsAttention')} />
    {#if pending.length > 2}
        <button class="see-all" type="button" on:click={() => showAll = !showAll}><span>{showAll ? $i18n.t('app.telegram.home.showFewer') : $i18n.t('app.telegram.home.allRequests', { count: pending.length })}</span><TelegramIcon name="arrowRight" size={18} label={showAll ? $i18n.t('app.telegram.home.showFewerRequests') : $i18n.t('app.telegram.home.allRequestsAria')} /></button>
    {/if}

    <h2 class="section-title">{$i18n.t('app.telegram.home.quickActions')}</h2>
    <div class="quick-actions" aria-label={$i18n.t('app.telegram.home.quickActions')}>
        <button type="button" on:click={() => coinSheetOpen = true}><TelegramIcon name="coinAdjustment" size={20} label={$i18n.t('app.telegram.home.addCoins')} /><span>{$i18n.t('app.telegram.home.addCoins')}</span></button>
    </div>

    <div class="section-heading">
        <h2 class="section-title">{$i18n.t('app.telegram.home.recentActivity')}</h2>
    </div>

    {#if historyLoading && !history.length}
        <p class="muted" role="status">{$i18n.t('app.telegram.home.loadingActivity')}</p>
    {:else if historyError}
        <div class="state-error" role="alert"><TelegramIcon name="alert" size={18} label={$i18n.t('app.telegram.home.error')} /><p>{historyError}</p><button type="button" on:click={() => loadHistory(true)}><TelegramIcon name="refresh" size={18} label={$i18n.t('app.telegram.shell.retry')} />{$i18n.t('app.telegram.shell.retry')}</button></div>
    {:else if !history.length}
        <p class="muted">{$i18n.t('app.telegram.home.noActivity')}</p>
    {:else}
        <div class="activity" aria-label={$i18n.t('app.telegram.home.recentActivity')}>
            {#each history as entry (entry.id)}
                <div class="a"><span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: entry.type === 'purchase' || entry.type === 'spend' ? 'reward' : 'task', title: entry.description || entry.title || entry.taskName || entry.itemName || '', group: entry.groupName })} size={18} label={$i18n.t('app.telegram.home.activity')} /></span><span class="grow"><span class="title">{stripLeadingEmoji(entry.description || entry.title || entry.taskName || entry.itemName || $i18n.t('app.telegram.home.activity'))}</span><span class="meta">{entry.createdAt ? formatLastUsedTime(entry.createdAt, $i18n.locale) : $i18n.t('app.telegram.home.recently')}</span></span><strong class:spend={entry.amount < 0}><TelegramCoin size={13} />{entry.amount > 0 ? '+' : ''}{entry.amount}</strong></div>
            {/each}
        </div>
        {#if historyHasMore}
            <button class="load-more" type="button" on:click={() => loadHistory()} disabled={historyLoading}>{historyLoading ? $i18n.t('app.telegram.home.loading') : $i18n.t('app.telegram.home.loadMore')}</button>
        {/if}
    {/if}
</section>

<TelegramCoinAdjust open={coinSheetOpen} busy={coinBusy} error={coinError} on:adjust={adjustCoins} on:close={() => { coinSheetOpen = false; coinError = ''; }} />

<style>
    .home { display:grid; grid-template-columns:minmax(0,1fr); gap:.9rem; }
    .section-heading { display:flex; align-items:center; justify-content:space-between; gap:.5rem; }
    .section-title { margin:0; color:#18243d; font-size:1rem; }
    .count { display:inline-grid; place-items:center; min-width:1.6rem; height:1.6rem; padding:0 .45rem; border-radius:999px; background:#eef0ff; color:#5b63e9; font-size:.82rem; font-weight:800; }
    .see-all, .load-more, .quick-actions button { display:inline-flex; align-items:center; justify-content:center; gap:.4rem; border-radius:.7rem; font:inherit; cursor:pointer; }
    .see-all, .load-more { min-height:2.75rem; padding:.5rem .8rem; border:1px solid #dfe4ee; background:#fff; color:#33415f; }
    .quick-actions { display:grid; grid-template-columns:minmax(0,1fr); gap:.6rem; }
    .quick-actions button { min-height:3rem; padding:.55rem .7rem; border:1px solid #3867d6; background:#fff; color:#3867d6; font-weight:700; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .activity { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .75rem; }
    .activity .a { display:flex; align-items:center; gap:.6rem; padding:.6rem 0; border-bottom:1px solid #edf0f5; }
    .activity .a:last-child { border-bottom:0; }
    .activity .grow { flex:1; min-width:0; }
    .activity .title { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:#18243d; font-size:.9rem; font-weight:600; }
    .activity .meta { display:block; margin-top:.1rem; color:#66718a; font-size:.75rem; }
    .activity strong { display:inline-flex; align-items:center; gap:.25rem; color:#237b3c; font-size:.9rem; white-space:nowrap; }
    .activity strong.spend { color:#a33b3b; }
    .entity-icon { display:grid; place-items:center; width:2.1rem; height:2.1rem; flex:0 0 auto; border-radius:.6rem; background:#eef0ff; color:#5b63e9; }
    .state-error { display:flex; align-items:center; gap:.55rem; padding:.6rem .75rem; border-radius:.8rem; background:#fff0f0; color:#a33b3b; }
    .state-error p { margin:0; flex:1; font-size:.9rem; }
    .state-error button { display:inline-flex; align-items:center; gap:.35rem; min-height:2.75rem; padding:.4rem .7rem; border:1px solid #f3cfd2; border-radius:.6rem; background:#fff; color:#a33b3b; font:inherit; cursor:pointer; }
    .muted { color:#66718a; }
</style>
