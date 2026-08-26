<script lang="ts">
    import { onMount } from 'svelte';
    import { appStore, type Request } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { useWorkspaceActions } from '$lib/features/workspace/workspaceActions';
    import { deleteRequest } from '$lib/services/api';
    import { confirmAction } from '$lib/services/confirm';
    import TelegramBalanceHeader from './TelegramBalanceHeader.svelte';
    import TelegramChildTasks from './TelegramChildTasks.svelte';
    import TelegramChildRewards from './TelegramChildRewards.svelte';
    import TelegramActionStatus from './TelegramActionStatus.svelte';
    import TelegramHistoryList from './TelegramHistoryList.svelte';
    import TelegramChildRequestList from './TelegramChildRequestList.svelte';
    import TelegramConfirmModal from './TelegramConfirmModal.svelte';
    import type { HistoryEntry } from '$lib/stores/app';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramParentReturn from './TelegramParentReturn.svelte';
    import TelegramTabBar from './ui/TelegramTabBar.svelte';
    import TelegramAsyncState from './ui/TelegramAsyncState.svelte';
    import type { TelegramAsyncState as AsyncState } from './ui/telegramUi';
    import type { TelegramTab } from './ui/telegramTabBar';
    import { parseTelegramWorkspaceContext, type ActivityTab, type ChildTab } from './telegramWorkspaceContext';

    const i18n = useI18n();
    const workspaceActions = useWorkspaceActions();

    export let publicOrigin = '';
    export let onExitPreview: (() => void) | null = null;
    export let showSessionActions = false;

    // EXPLAIN: Bot deep links pass ?context= so the exact Mini App context opens.
    const context = typeof window === 'undefined'
        ? '' : new URLSearchParams(window.location.search).get('context') ?? '';
    const workspaceContext = parseTelegramWorkspaceContext(context);
    let loading = true;
    let error = '';
    let refreshing = false;
    let view: ChildTab = workspaceContext.childTab;
    let activityView: ActivityTab = workspaceContext.activityTab;
    let history: HistoryEntry[] = [];
    let historyPage = 0;
    let historyHasMore = false;
    let historyLoading = false;
    let historyError = '';
    $: tabs = [
        { id: 'tasks', icon: 'task', label: $i18n.t('app.telegram.childShell.tasks') },
        { id: 'rewards', icon: 'reward', label: $i18n.t('app.telegram.childShell.rewards') },
        { id: 'activity', icon: 'activity', label: $i18n.t('app.telegram.childShell.activity') },
    ] satisfies readonly TelegramTab[];
    $: activityTabs = [
        { id: 'history', icon: 'history', label: $i18n.t('app.telegram.history.history') },
        { id: 'requests', icon: 'request', label: $i18n.t('app.telegram.childShell.requests') },
    ] satisfies readonly TelegramTab[];

    onMount(async () => {
        const ok = await workspaceActions.initialize();
        loading = false;
        view = workspaceContext.childTab;
        activityView = workspaceContext.activityTab;
        if (!ok) error = $i18n.t('app.telegram.childShell.loadError');
    });
    async function retry() { refreshing = true; error = ''; const ok = await workspaceActions.refresh(); refreshing = false; if (!ok) error = $i18n.t('app.telegram.childShell.refreshError'); }
    function onVisibility() { if (document.visibilityState === 'visible' && !loading && !refreshing) void workspaceActions.refresh(); }
    async function loadHistory(reset = false) {
        if (historyLoading || $appStore.currentChildId == null) return;
        historyLoading = true; historyError = '';
        try {
            const page = await workspaceActions.loadHistory({ childId: $appStore.currentChildId, page: reset ? 1 : historyPage + 1, limit: 20 });
            history = reset ? page.items : [...history, ...page.items]; historyPage = page.page; historyHasMore = page.items.length === page.limit;
        } catch { historyError = $i18n.t('app.telegram.childShell.activityError'); }
        historyLoading = false;
    }
    function selectView(next: string) {
        view = next as ChildTab;
        if (next === 'activity') {
            activityView = workspaceContext.activityTab;
            if (activityView === 'history') void loadHistory(true);
        }
    }
    function selectActivityView(next: string) {
        activityView = next as ActivityTab;
        if (next === 'history') void loadHistory(true);
    }
    let cancellingIds: Array<string | number> = [];
    let cancelError = '';
    async function handleCancel(request: Request) {
        if (request.status !== 'pending') return;
        if (cancellingIds.some((id) => String(id) === String(request.id))) return;
        const confirmed = await confirmAction({
            title: $i18n.t('app.telegram.childRequests.cancelConfirmTitle'),
            description: $i18n.t('app.telegram.childRequests.cancelConfirmDescription'),
            confirmLabel: $i18n.t('app.telegram.childRequests.cancelConfirmAction'),
            cancelLabel: $i18n.t('app.telegram.childRequests.cancelConfirmCancel'),
            tone: 'danger',
        });
        if (!confirmed) return;
        cancelError = '';
        cancellingIds = [...cancellingIds, request.id];
        const ok = await deleteRequest(request.id, $appStore.currentChildId);
        cancellingIds = cancellingIds.filter((id) => String(id) !== String(request.id));
        if (ok) {
            await workspaceActions.refresh();
        } else {
            cancelError = $i18n.t('app.telegram.childRequests.cancelError');
        }
    }
</script>

<svelte:window on:visibilitychange={onVisibility} />
<main class="child-workspace" aria-labelledby="child-workspace-title">
    {#if onExitPreview}<TelegramParentReturn onClick={onExitPreview} />{/if}
    <TelegramBalanceHeader headingId="child-workspace-title" nickname={$appStore.childNickname} balance={$appStore.balance} loading={loading || Boolean(error)} />
    <TelegramTabBar tabs={tabs} selected={view} idPrefix="child" ariaLabel={$i18n.t('app.telegram.childShell.workspace')} onSelect={selectView} {showSessionActions} />
    <div aria-labelledby={`child-tab-${view}`} id={`child-panel-${view}`} class="tab-panel" role="tabpanel" tabindex="0">
        {#if loading || error}
            <TelegramAsyncState state={(loading ? 'loading' : 'error') as AsyncState} loadingLabel={$i18n.t('app.telegram.childShell.loading')} errorMessage={error} retryLabel={refreshing ? $i18n.t('app.telegram.childShell.refreshing') : $i18n.t('app.telegram.childShell.retry')} onRetry={retry} />
        {:else if view === 'tasks'}
            <TelegramChildTasks />
        {:else if view === 'rewards'}
            <TelegramChildRewards />
        {:else}
            <TelegramTabBar tabs={activityTabs} selected={activityView} idPrefix="child-activity" ariaLabel={$i18n.t('app.telegram.childShell.activity')} fixedOnMobile={false} onSelect={selectActivityView} />
            {#if activityView === 'requests'}
                <TelegramChildRequestList loading={$appStore.isLoading} cancellingIds={cancellingIds} cancelError={cancelError} onCancel={handleCancel} />
            {:else}
                <TelegramHistoryList entries={history} loading={historyLoading} error={historyError} hasMore={historyHasMore} onRetry={() => loadHistory(true)} onLoadMore={() => loadHistory()} />
            {/if}
        {/if}
    </div>
    <TelegramConfirmModal />
    {#if publicOrigin}
        <footer class="site-link" aria-label={$i18n.t('app.telegram.shell.publicSiteAria')}>
            <a href={publicOrigin} target="_blank" rel="noopener noreferrer"><TelegramIcon name="link" size={14} label={$i18n.t('app.telegram.shell.publicSiteAria')} />{$i18n.t('app.telegram.shell.publicSite')}</a>
        </footer>
    {/if}
</main>

<style>
    .child-workspace { box-sizing:border-box; display:flex; flex-direction:column; width:100%; max-width:48rem; min-height:100vh; margin:0 auto; padding:calc(.75rem + env(safe-area-inset-top)) 1rem calc(2rem + env(safe-area-inset-bottom)); } .tab-panel { flex:1 1 auto; min-height:0; } .site-link { display:flex; justify-content:center; flex-shrink:0; margin-top:1.25rem; } .site-link a { display:inline-flex; align-items:center; gap:.3rem; color:#8a93a8; font-size:.78rem; text-decoration:none; } .site-link a:hover { color:#3867d6; } .site-link a:focus-visible { outline:3px solid #80aaff; outline-offset:2px; border-radius:.3rem; }
    @media (max-width:700px) { .child-workspace { padding:.65rem .75rem calc(5.75rem + env(safe-area-inset-bottom)); } }
</style>
