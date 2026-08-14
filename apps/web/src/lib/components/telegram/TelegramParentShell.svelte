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
    import TelegramRequestList from './TelegramRequestList.svelte';
    import TelegramHistoryList from './TelegramHistoryList.svelte';
    import { loadTelegramHistory, loadTelegramRequests, type TelegramPage } from '$lib/services/telegramActivity';
    import type { HistoryEntry, Request } from '$lib/stores/app';
    import TelegramIcon from './TelegramIcon.svelte';

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
        <div><h1 id="telegram-parent-title">Family space</h1></div>
        <span class="balance" aria-label="Selected child balance">{$appStore.balance} 🪙</span>
    </header>

    {#if loading}
        <p class="state" role="status">Loading your family…</p>
    {:else if error}
        <section class="state state--error" role="alert"><p>{error}</p><button type="button" on:click={retry}>Retry</button></section>
    {:else}
        <div class="tabs" aria-label="Parent workspace" role="tablist" tabindex="-1" on:keydown={handleTabKeydown}>
            <button aria-controls="parent-panel-requests" aria-selected={view === 'requests'} class:active={view === 'requests'} id="parent-tab-requests" role="tab" tabindex={view === 'requests' ? 0 : -1} type="button" on:click={() => selectView('requests')}><TelegramIcon name="request" size={20} label="Requests" /><span>Requests</span>{pending.length ? ` (${pending.length})` : ''}</button>
            <button aria-controls="parent-panel-tasks" aria-selected={view === 'tasks'} class:active={view === 'tasks'} id="parent-tab-tasks" role="tab" tabindex={view === 'tasks' ? 0 : -1} type="button" on:click={() => selectView('tasks')}><TelegramIcon name="task" size={20} label="Tasks" /><span>Tasks</span></button>
            <button aria-controls="parent-panel-rewards" aria-selected={view === 'rewards'} class:active={view === 'rewards'} id="parent-tab-rewards" role="tab" tabindex={view === 'rewards' ? 0 : -1} type="button" on:click={() => selectView('rewards')}><TelegramIcon name="reward" size={20} label="Rewards" /><span>Rewards</span></button>
            <button aria-controls="parent-panel-activity" aria-selected={view === 'activity'} class:active={view === 'activity'} id="parent-tab-activity" role="tab" tabindex={view === 'activity' ? 0 : -1} type="button" on:click={() => selectView('activity')}><TelegramIcon name="activity" size={20} label="Activity" /><span>Activity</span></button>
            <button aria-controls="parent-panel-child" aria-selected={view === 'child'} class:active={view === 'child'} id="parent-tab-child" role="tab" tabindex={view === 'child' ? 0 : -1} type="button" on:click={() => selectView('child')}><TelegramIcon name="child" size={20} label="Child" /><span>Child</span></button>
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
            {/if}
        </div>
    {/if}
</main>

<style>
    .parent-workspace { width: 100%; max-width: 48rem; margin: 0 auto; padding: calc(.75rem + env(safe-area-inset-top)) 1rem 2rem; }
    .workspace-header { display: flex; justify-content: space-between; align-items: center; gap: .75rem; margin-bottom: .75rem; }
    h1 { margin: .15rem 0 0; font-size: clamp(1.25rem, 5vw, 1.75rem); color: #18243d; }
    .balance { padding: .55rem .75rem; border-radius: 999px; background: #fff4c2; color: #573d00; font-weight: 700; white-space: nowrap; }
    .tabs { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: .25rem; margin-bottom: .75rem; }
    button { min-height: 2.75rem; border: 1px solid #dfe4ee; border-radius: .75rem; background: #fff; color: #33415f; font: inherit; cursor: pointer; }
    button:focus-visible { outline: 3px solid #80aaff; outline-offset: 2px; }
    .tabs button.active { border-color: #3867d6; background: #3867d6; color: #fff; }
    .tabs button { display: inline-flex; align-items: center; justify-content: center; gap: .2rem; padding-inline: .25rem; white-space: nowrap; }
    .state { padding: 2rem 1rem; text-align: center; color: #5c6780; }
    .state--error { color: #a33b3b; }
    @media (max-width: 700px) {
        .parent-workspace { padding: calc(.65rem + env(safe-area-inset-top)) .75rem calc(5.75rem + env(safe-area-inset-bottom)); }
        .tabs { position: fixed; z-index: 20; right: 0; bottom: 0; left: 0; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 0; margin: 0; padding: .4rem .35rem calc(.4rem + env(safe-area-inset-bottom)); border-top: 1px solid #dfe4ee; background: rgb(255 255 255 / 96%); box-shadow: 0 -8px 24px rgb(24 36 61 / 8%); }
        .tabs button { min-height: 3.25rem; flex-direction: column; gap: .2rem; border: 0; border-radius: .65rem; font-size: .68rem; }
        .tabs button.active { background: #edf2ff; color: #2854ba; }
    }
    @media (max-width: 340px) { .tabs button { font-size: .61rem; } }
</style>
