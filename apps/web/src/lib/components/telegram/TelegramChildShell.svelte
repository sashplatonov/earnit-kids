<script lang="ts">
    import { onMount } from 'svelte';
    import { appStore } from '$lib/stores/app';
    import { initializeFromServer, refreshData } from '$lib/services/bootstrap';
    import TelegramBalanceHeader from './TelegramBalanceHeader.svelte';
    import TelegramChildTasks from './TelegramChildTasks.svelte';
    import TelegramChildRewards from './TelegramChildRewards.svelte';
    import TelegramActionStatus from './TelegramActionStatus.svelte';
    import TelegramRequestList from './TelegramRequestList.svelte';
    import TelegramHistoryList from './TelegramHistoryList.svelte';
    import { loadTelegramHistory } from '$lib/services/telegramActivity';
    import type { HistoryEntry } from '$lib/stores/app';
    let loading = true;
    let error = '';
    let refreshing = false;
    let view: 'tasks' | 'activity' = 'tasks';
    let history: HistoryEntry[] = [];
    let historyPage = 0;
    let historyHasMore = false;
    let historyLoading = false;
    let historyError = '';
    const tabs = ['tasks', 'activity'] as const;
    onMount(async () => { const ok = await initializeFromServer(); loading = false; if (!ok) error = 'Could not load your workspace. Try again.'; });
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
    {#if loading}<p class="state" role="status">Loading your tasks…</p>
    {:else if error}<section class="state" role="alert"><p>{error}</p><button type="button" on:click={retry} disabled={refreshing}>{refreshing ? 'Refreshing…' : 'Retry'}</button></section>
    {:else}
        <TelegramBalanceHeader headingId="child-workspace-title" nickname={$appStore.childNickname} balance={$appStore.balance} />
        <div class="tabs" aria-label="Child workspace" role="tablist" tabindex="-1" on:keydown={handleTabKeydown}>
            <button aria-controls="child-panel-tasks" aria-selected={view === 'tasks'} class:active={view === 'tasks'} id="child-tab-tasks" role="tab" tabindex={view === 'tasks' ? 0 : -1} type="button" on:click={() => selectView('tasks')}>Tasks</button>
            <button aria-controls="child-panel-activity" aria-selected={view === 'activity'} class:active={view === 'activity'} id="child-tab-activity" role="tab" tabindex={view === 'activity' ? 0 : -1} type="button" on:click={() => selectView('activity')}>Activity</button>
        </div>
        {#if view === 'tasks'}
            <div aria-labelledby="child-tab-tasks" id="child-panel-tasks" role="tabpanel" tabindex="0"><TelegramChildTasks /><TelegramChildRewards /></div>
        {:else}
            <div aria-labelledby="child-tab-activity" id="child-panel-activity" role="tabpanel" tabindex="0"><TelegramRequestList requests={$appStore.requests} /><TelegramHistoryList entries={history} loading={historyLoading} error={historyError} hasMore={historyHasMore} onRetry={() => loadHistory(true)} onLoadMore={() => loadHistory()} /></div>
        {/if}
    {/if}
</main>

<style>
    .child-workspace { box-sizing:border-box; max-width:42rem; min-height:100%; margin:0 auto; padding:.5rem 0 calc(2rem + env(safe-area-inset-bottom)); } .state { padding:2rem 1rem; color:#66718a; text-align:center; } button { min-height:2.75rem; margin-top:1rem; padding:.6rem .85rem; border:1px solid #3867d6; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; } .tabs { display:grid; grid-template-columns:repeat(2, 1fr); gap:.45rem; margin-bottom:1rem; } .tabs button { margin-top:0; background:#fff; color:#33415f; } .tabs button.active { background:#3867d6; color:#fff; }
</style>
