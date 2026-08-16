<script lang="ts">
    import { onMount } from 'svelte';
    import { appStore } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { initializeFromServer, refreshData } from '$lib/services/bootstrap';
    import TelegramBalanceHeader from './TelegramBalanceHeader.svelte';
    import TelegramChildTasks from './TelegramChildTasks.svelte';
    import TelegramChildRewards from './TelegramChildRewards.svelte';
    import TelegramActionStatus from './TelegramActionStatus.svelte';
    import TelegramHistoryList from './TelegramHistoryList.svelte';
    import { loadTelegramHistory } from '$lib/services/telegramActivity';
    import type { HistoryEntry } from '$lib/stores/app';
    import TelegramIcon from './TelegramIcon.svelte';

    const i18n = useI18n();

    export let publicOrigin = '';
    export let onExitPreview: () => void = () => {};

    // EXPLAIN: Bot deep links pass ?context= so the exact Mini App context opens.
    const context = new URLSearchParams(window.location.search).get('context') ?? '';
    let loading = true;
    let error = '';
    let refreshing = false;
    let view: 'tasks' | 'rewards' | 'activity' = tabForContext(context);
    let history: HistoryEntry[] = [];
    let historyPage = 0;
    let historyHasMore = false;
    let historyLoading = false;
    let historyError = '';
    const tabs = ['tasks', 'rewards', 'activity'] as const;

    function tabForContext(value: string): 'tasks' | 'rewards' | 'activity' {
        if (value === 'rewards') return 'rewards';
        if (value === 'activity' || value === 'history') return 'activity';
        return 'tasks';
    }

    onMount(async () => {
        const ok = await initializeFromServer();
        loading = false;
        view = tabForContext(context);
        if (!ok) error = $i18n.t('app.telegram.childShell.loadError');
    });
    async function retry() { refreshing = true; error = ''; const ok = await refreshData(); refreshing = false; if (!ok) error = $i18n.t('app.telegram.childShell.refreshError'); }
    function onVisibility() { if (document.visibilityState === 'visible' && !loading && !refreshing) void refreshData(); }
    async function loadHistory(reset = false) {
        if (historyLoading || $appStore.currentChildId == null) return;
        historyLoading = true; historyError = '';
        try {
            const page = await loadTelegramHistory($appStore.currentChildId, reset ? 1 : historyPage + 1, 20);
            history = reset ? page.items : [...history, ...page.items]; historyPage = page.page; historyHasMore = page.items.length === page.limit;
        } catch { historyError = $i18n.t('app.telegram.childShell.activityError'); }
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
    {#if onExitPreview}
        <button class="exit-preview" type="button" on:click={onExitPreview}><TelegramIcon name="back" size={16} label={$i18n.t('app.telegram.childShell.exitPreview')} /><span>{$i18n.t('app.telegram.childShell.exitPreview')}</span></button>
    {/if}
    <TelegramBalanceHeader headingId="child-workspace-title" nickname={$appStore.childNickname} balance={$appStore.balance} loading={loading || Boolean(error)} />
    <div class="tabs" aria-label={$i18n.t('app.telegram.childShell.workspace')} role="tablist" tabindex="-1" on:keydown={handleTabKeydown}>
        <button aria-controls="child-panel-tasks" aria-selected={view === 'tasks'} class:active={view === 'tasks'} id="child-tab-tasks" role="tab" tabindex={view === 'tasks' ? 0 : -1} type="button" on:click={() => selectView('tasks')}><TelegramIcon name="task" size={20} label={$i18n.t('app.telegram.childShell.tasks')} /><span>{$i18n.t('app.telegram.childShell.tasks')}</span></button>
        <button aria-controls="child-panel-rewards" aria-selected={view === 'rewards'} class:active={view === 'rewards'} id="child-tab-rewards" role="tab" tabindex={view === 'rewards' ? 0 : -1} type="button" on:click={() => selectView('rewards')}><TelegramIcon name="reward" size={20} label={$i18n.t('app.telegram.childShell.rewards')} /><span>{$i18n.t('app.telegram.childShell.rewards')}</span></button>
        <button aria-controls="child-panel-activity" aria-selected={view === 'activity'} class:active={view === 'activity'} id="child-tab-activity" role="tab" tabindex={view === 'activity' ? 0 : -1} type="button" on:click={() => selectView('activity')}><TelegramIcon name="activity" size={20} label={$i18n.t('app.telegram.childShell.activity')} /><span>{$i18n.t('app.telegram.childShell.activity')}</span></button>
    </div>
    <div aria-labelledby={`child-tab-${view}`} id={`child-panel-${view}`} class="tab-panel" role="tabpanel" tabindex="0">
        {#if loading}
            <div class="panel-skeleton" aria-hidden="true"><div class="skeleton-line"></div><div class="skeleton-line"></div><div class="skeleton-line"></div></div>
        {:else if error}
            <section class="state" role="alert"><p>{error}</p><button type="button" on:click={retry} disabled={refreshing}><TelegramIcon name="refresh" size={18} label={refreshing ? $i18n.t('app.telegram.childShell.refreshingWorkspace') : $i18n.t('app.telegram.childShell.retry')} />{refreshing ? $i18n.t('app.telegram.childShell.refreshing') : $i18n.t('app.telegram.childShell.retry')}</button></section>
        {:else if view === 'tasks'}
            <TelegramChildTasks />
        {:else if view === 'rewards'}
            <TelegramChildRewards />
        {:else}
            <TelegramHistoryList entries={history} loading={historyLoading} error={historyError} hasMore={historyHasMore} onRetry={() => loadHistory(true)} onLoadMore={() => loadHistory()} />
        {/if}
    </div>
    {#if publicOrigin}
        <footer class="site-link" aria-label={$i18n.t('app.telegram.shell.publicSiteAria')}>
            <a href={publicOrigin} target="_blank" rel="noopener noreferrer"><TelegramIcon name="link" size={14} label={$i18n.t('app.telegram.shell.publicSiteAria')} />{$i18n.t('app.telegram.shell.publicSite')}</a>
        </footer>
    {/if}
</main>

<style>
    .child-workspace { box-sizing:border-box; display:flex; flex-direction:column; width:100%; max-width:48rem; min-height:100vh; margin:0 auto; padding:calc(.75rem + env(safe-area-inset-top)) 1rem calc(2rem + env(safe-area-inset-bottom)); } .state { padding:2rem 1rem; color:#66718a; text-align:center; } button { min-height:2.75rem; margin-top:1rem; padding:.6rem .85rem; border:1px solid #3867d6; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; } .exit-preview { display:inline-flex; align-items:center; gap:.35rem; min-height:2.25rem; margin:0 0 .5rem; padding:.3rem .6rem; border:1px solid #dfe4ee; border-radius:.6rem; background:#fff; color:#3867d6; font:inherit; font-weight:700; cursor:pointer; } @keyframes skeleton-pulse { 0%,100% { opacity:.55; } 50% { opacity:1; } } @media (prefers-reduced-motion: reduce) { .panel-skeleton .skeleton-line { animation:none; } } .tabs { display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:.25rem; flex-shrink:0; margin-bottom:.75rem; } .tabs button { margin-top:0; background:#fff; color:#33415f; border:1px solid #dfe4ee; border-radius:.75rem; display:inline-flex; align-items:center; justify-content:center; gap:.2rem; padding-inline:.25rem; white-space:nowrap; } .tabs button.active { background:#fff; color:#2854ba; font-weight:750; border-color:#b9c0ff; } .tab-panel { flex:1 1 auto; min-height:0; } .panel-skeleton { display:flex; flex-direction:column; gap:.75rem; padding:1rem 0; } .panel-skeleton .skeleton-line { height:4.5rem; border-radius:.8rem; background:#e8eaf1; animation:skeleton-pulse 1.3s ease-in-out infinite; } .site-link { display:flex; justify-content:center; flex-shrink:0; margin-top:1.25rem; } .site-link a { display:inline-flex; align-items:center; gap:.3rem; color:#8a93a8; font-size:.78rem; text-decoration:none; } .site-link a:hover { color:#3867d6; } .site-link a:focus-visible { outline:3px solid #80aaff; outline-offset:2px; border-radius:.3rem; }
    @media (max-width:700px) { .child-workspace { padding:.65rem .75rem calc(5.75rem + env(safe-area-inset-bottom)); } .tabs { position:fixed; z-index:20; right:0; bottom:0; left:0; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:0; margin:0; padding:.3rem .35rem calc(.3rem + env(safe-area-inset-bottom)); border-top:1px solid #dfe4ee; background:rgb(255 255 255 / 96%); box-shadow:0 -8px 24px rgb(24 36 61 / 8%); } .tabs button { min-height:3rem; flex-direction:column; gap:.2rem; border:0; border-radius:.65rem; font-size:.7rem; } .tabs button.active { color:#2854ba; font-weight:750; } }
</style>
