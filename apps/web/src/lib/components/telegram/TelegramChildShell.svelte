<script lang="ts">
    import { onMount } from 'svelte';
    import { appStore } from '$lib/stores/app';
    import { initializeFromServer, refreshData } from '$lib/services/bootstrap';
    import TelegramBalanceHeader from './TelegramBalanceHeader.svelte';
    import TelegramChildTasks from './TelegramChildTasks.svelte';
    import TelegramChildRewards from './TelegramChildRewards.svelte';
    import TelegramActionStatus from './TelegramActionStatus.svelte';
    import TelegramHistoryList from './TelegramHistoryList.svelte';
    import { loadTelegramHistory } from '$lib/services/telegramActivity';
    import type { HistoryEntry } from '$lib/stores/app';
    import TelegramIcon from './TelegramIcon.svelte';
    // EXPLAIN: Bot deep links pass ?context= so the exact Mini App context opens.
    const context = new URLSearchParams(window.location.search).get('context') ?? '';
    let loading = true;
    let error = '';
    let refreshing = false;
    let view: 'today' | 'rewards' | 'activity' = tabForContext(context);
    let history: HistoryEntry[] = [];
    let historyPage = 0;
    let historyHasMore = false;
    let historyLoading = false;
    let historyError = '';
    const tabs = ['today', 'rewards', 'activity'] as const;

    function tabForContext(value: string): 'today' | 'rewards' | 'activity' {
        if (value === 'rewards') return 'rewards';
        if (value === 'activity' || value === 'history') return 'activity';
        return 'today';
    }

    onMount(async () => {
        const ok = await initializeFromServer();
        loading = false;
        view = tabForContext(context);
        if (!ok) error = 'Could not load your workspace. Try again.';
    });
    async function retry() { refreshing = true; error = ''; const ok = await refreshData(); refreshing = false; if (!ok) error = 'Could not refresh your workspace.'; }
    function onVisibility() { if (document.visibilityState === 'visible' && !loading && !refreshing) void refreshData(); }
    async function loadHistory(reset = false) {
        if (historyLoading || $appStore.currentChildId == null) return;
        historyLoading = true; historyError = '';
        try {
            const page = await loadTelegramHistory($appStore.currentChildId, reset ? 1 : historyPage + 1, 20);
            history = reset ? page.items : [...history, ...page.items]; historyPage = page.page; historyHasMore = page.items.length === page.limit;
        } catch { historyError = 'Activity could not be loaded.'; }
        historyLoading = false;
    }
    function selectView(next: typeof tabs[number]) {
        view = next;
        if (next === 'activity') void loadHistory(true);
    }
    function handleTabKeydown(event: KeyboardEvent) {
        const index = tabs.indexOf(view);
        let next: number;
        if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
        else if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
        else if (event.key === 'Home') next = 0;
        else if (event.key === 'End') next = tabs.length - 1;
        else return;
        event.preventDefault();
        selectView(tabs[next]);
        document.getElementById(`child-tab-${tabs[next]}`)?.focus();
    }
</script>

<svelte:window on:visibilitychange={onVisibility} />
<main class="child-workspace" aria-labelledby="child-workspace-title">
    <TelegramBalanceHeader headingId="child-workspace-title" nickname={$appStore.childNickname} balance={$appStore.balance} loading={loading || Boolean(error)} />
    <div class="tabs" aria-label="Child workspace" role="tablist" tabindex="-1" on:keydown={handleTabKeydown}>
        <button aria-controls="child-panel-today" aria-selected={view === 'today'} class:active={view === 'today'} id="child-tab-today" role="tab" tabindex={view === 'today' ? 0 : -1} type="button" on:click={() => selectView('today')}><TelegramIcon name="task" size={20} label="Today" /><span>Today</span></button>
        <button aria-controls="child-panel-rewards" aria-selected={view === 'rewards'} class:active={view === 'rewards'} id="child-tab-rewards" role="tab" tabindex={view === 'rewards' ? 0 : -1} type="button" on:click={() => selectView('rewards')}><TelegramIcon name="reward" size={20} label="Rewards" /><span>Rewards</span></button>
        <button aria-controls="child-panel-activity" aria-selected={view === 'activity'} class:active={view === 'activity'} id="child-tab-activity" role="tab" tabindex={view === 'activity' ? 0 : -1} type="button" on:click={() => selectView('activity')}><TelegramIcon name="activity" size={20} label="Activity" /><span>Activity</span></button>
    </div>
    <div aria-labelledby={`child-tab-${view}`} id={`child-panel-${view}`} role="tabpanel" tabindex="0">
        {#if loading}
            <p class="state" role="status">Loading your tasks…</p>
        {:else if error}
            <section class="state" role="alert"><p>{error}</p><button type="button" on:click={retry} disabled={refreshing}><TelegramIcon name="refresh" size={18} label={refreshing ? 'Refreshing workspace' : 'Retry'} />{refreshing ? 'Refreshing…' : 'Retry'}</button></section>
        {:else if view === 'today'}
            <TelegramChildTasks />
        {:else if view === 'rewards'}
            <TelegramChildRewards />
        {:else}
            <TelegramHistoryList entries={history} loading={historyLoading} error={historyError} hasMore={historyHasMore} onRetry={() => loadHistory(true)} onLoadMore={() => loadHistory()} />
        {/if}
    </div>
</main>

<style>
    .child-workspace { box-sizing:border-box; width:100%; max-width:48rem; min-height:100%; margin:0 auto; padding:calc(.75rem + env(safe-area-inset-top)) 1rem calc(2rem + env(safe-area-inset-bottom)); } .state { padding:2rem 1rem; color:#66718a; text-align:center; } button { min-height:2.75rem; margin-top:1rem; padding:.6rem .85rem; border:1px solid #3867d6; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; } .tabs { display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:.45rem; margin-bottom:.75rem; } .tabs button { margin-top:0; background:#fff; color:#33415f; } .tabs button.active { background:#3867d6; color:#fff; }
    @media (max-width:700px) { .child-workspace { padding:.65rem .75rem calc(5.75rem + env(safe-area-inset-bottom)); } .tabs { position:fixed; z-index:20; right:0; bottom:0; left:0; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:0; margin:0; padding:.3rem .35rem calc(.3rem + env(safe-area-inset-bottom)); border-top:1px solid #dfe4ee; background:rgb(255 255 255 / 96%); box-shadow:0 -8px 24px rgb(24 36 61 / 8%); } .tabs button { min-height:3rem; flex-direction:column; gap:.2rem; border:0; border-radius:.65rem; font-size:.7rem; } .tabs button.active { color:#2854ba; font-weight:750; } }
</style>
