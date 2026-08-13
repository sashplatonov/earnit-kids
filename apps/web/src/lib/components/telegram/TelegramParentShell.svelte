<script lang="ts">
    import { onMount } from 'svelte';
    import { appStore } from '$lib/stores/app';
    import { initializeFromServer, refreshData } from '$lib/services/bootstrap';
    import { initialParentView, type TelegramParentView } from '$lib/services/telegramViewState';
    import TelegramChildPicker from './TelegramChildPicker.svelte';
    import TelegramParentTasks from './TelegramParentTasks.svelte';
    import TelegramParentRewards from './TelegramParentRewards.svelte';
    import TelegramCatalogEditor from './TelegramCatalogEditor.svelte';
    import TelegramChildInvite from './TelegramChildInvite.svelte';
    import TelegramLinkSettings from './TelegramLinkSettings.svelte';
    import TelegramRequestList from './TelegramRequestList.svelte';
    import TelegramHistoryList from './TelegramHistoryList.svelte';
    import { loadTelegramHistory, loadTelegramRequests, type TelegramPage } from '$lib/services/telegramActivity';
    import type { HistoryEntry, Request } from '$lib/stores/app';

    let view: TelegramParentView | 'activity' = 'tasks';
    let loading = true;
    let error = '';
    $: pending = $appStore.requests.filter((request) => request.status === 'pending');
    let activityRequests: Request[] = [];
    let activityHistory: HistoryEntry[] = [];
    let activityPage = 0;
    let activityHasMore = false;
    let activityLoading = false;
    let activityError = '';
    const tabs: Array<TelegramParentView | 'activity'> = ['requests', 'tasks', 'rewards', 'activity', 'child'];

    onMount(async () => {
        loading = true;
        error = '';
        const ok = await initializeFromServer();
        if (!ok) error = 'Could not load your family. Try again.';
        else view = initialParentView($appStore);
        loading = false;
    });

    async function retry() {
        const ok = await refreshData();
        if (ok) { error = ''; view = initialParentView($appStore); }
        else error = 'Could not refresh your family. Try again.';
    }

    async function loadActivity(reset = false) {
        if (activityLoading) return;
        activityLoading = true; activityError = '';
        try {
            const requestPage = await loadTelegramRequests(reset ? 1 : 1, 20);
            activityRequests = requestPage.items;
            const childId = $appStore.currentChildId;
            if (childId != null) {
                const historyPage: TelegramPage<HistoryEntry> = await loadTelegramHistory(childId, reset ? 1 : activityPage + 1, 20);
                activityHistory = reset ? historyPage.items : [...activityHistory, ...historyPage.items];
                activityPage = historyPage.page;
                activityHasMore = historyPage.items.length === historyPage.limit;
            }
        } catch { activityError = 'Activity could not be loaded.'; }
        activityLoading = false;
    }
    function selectView(next: TelegramParentView | 'activity') {
        view = next;
        if (next === 'activity') void loadActivity(true);
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
        document.getElementById(`parent-tab-${tabs[next]}`)?.focus();
    }
</script>

<main class="parent-workspace" aria-labelledby="telegram-parent-title">
    <header class="workspace-header">
        <div><p class="eyebrow">Telegram Mini App</p><h1 id="telegram-parent-title">Family space</h1></div>
        <span class="balance" aria-label="Selected child balance">{$appStore.balance} 🪙</span>
    </header>

    {#if loading}
        <p class="state" role="status">Loading your family…</p>
    {:else if error}
        <section class="state state--error" role="alert"><p>{error}</p><button type="button" on:click={retry}>Retry</button></section>
    {:else}
        <div class="tabs" aria-label="Parent workspace" role="tablist" tabindex="-1" on:keydown={handleTabKeydown}>
            <button aria-controls="parent-panel-requests" aria-selected={view === 'requests'} class:active={view === 'requests'} id="parent-tab-requests" role="tab" tabindex={view === 'requests' ? 0 : -1} type="button" on:click={() => selectView('requests')}>Requests {pending.length ? `(${pending.length})` : ''}</button>
            <button aria-controls="parent-panel-tasks" aria-selected={view === 'tasks'} class:active={view === 'tasks'} id="parent-tab-tasks" role="tab" tabindex={view === 'tasks' ? 0 : -1} type="button" on:click={() => selectView('tasks')}>Tasks</button>
            <button aria-controls="parent-panel-rewards" aria-selected={view === 'rewards'} class:active={view === 'rewards'} id="parent-tab-rewards" role="tab" tabindex={view === 'rewards' ? 0 : -1} type="button" on:click={() => selectView('rewards')}>Rewards</button>
            <button aria-controls="parent-panel-activity" aria-selected={view === 'activity'} class:active={view === 'activity'} id="parent-tab-activity" role="tab" tabindex={view === 'activity' ? 0 : -1} type="button" on:click={() => selectView('activity')}>Activity</button>
            <button aria-controls="parent-panel-child" aria-selected={view === 'child'} class:active={view === 'child'} id="parent-tab-child" role="tab" tabindex={view === 'child' ? 0 : -1} type="button" on:click={() => selectView('child')}>Child</button>
        </div>
        <div aria-labelledby={`parent-tab-${view}`} id={`parent-panel-${view}`} role="tabpanel" tabindex="0">
            {#if view === 'requests'}
                <TelegramCatalogEditor />
            {:else if view === 'tasks'}
                <TelegramParentTasks />
            {:else if view === 'rewards'}
                <TelegramParentRewards />
            {:else if view === 'activity'}
                <TelegramRequestList requests={activityRequests} canDecide childId={$appStore.currentChildId} loading={activityLoading} error={activityError} onRetry={() => loadActivity(true)} />
                <TelegramHistoryList entries={activityHistory} loading={activityLoading} error={activityError} hasMore={activityHasMore} onRetry={() => loadActivity(true)} onLoadMore={() => loadActivity()} />
            {:else}
                <TelegramChildPicker />
                <TelegramChildInvite />
                <TelegramLinkSettings />
            {/if}
        </div>
    {/if}
</main>

<style>
    .parent-workspace { max-width: 42rem; margin: 0 auto; padding: .5rem 0 2rem; }
    .workspace-header { display: flex; justify-content: space-between; align-items: center; gap: 1rem; margin-bottom: 1rem; }
    .eyebrow { margin: 0; color: #66718a; font-size: .75rem; text-transform: uppercase; letter-spacing: .08em; }
    h1 { margin: .2rem 0 0; font-size: clamp(1.35rem, 6vw, 2rem); color: #18243d; }
    .balance { padding: .55rem .75rem; border-radius: 999px; background: #fff4c2; color: #573d00; font-weight: 700; white-space: nowrap; }
    .tabs { display: grid; grid-template-columns: repeat(4, 1fr); gap: .4rem; margin-bottom: 1rem; }
    button { min-height: 2.75rem; border: 1px solid #dfe4ee; border-radius: .75rem; background: #fff; color: #33415f; font: inherit; cursor: pointer; }
    button:focus-visible { outline: 3px solid #80aaff; outline-offset: 2px; }
    .tabs button.active { border-color: #3867d6; background: #3867d6; color: #fff; }
    .state { padding: 2rem 1rem; text-align: center; color: #5c6780; }
    .state--error { color: #a33b3b; }
    @media (max-width: 340px) { .tabs { grid-template-columns: repeat(2, 1fr); } }
</style>
