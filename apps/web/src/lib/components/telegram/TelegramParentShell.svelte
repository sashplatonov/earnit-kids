<script lang="ts">
    import { onMount } from 'svelte';
    import { appStore } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { initializeFromServer, refreshData } from '$lib/services/bootstrap';
    import TelegramParentHome from './TelegramParentHome.svelte';
    import TelegramParentTasks from './TelegramParentTasks.svelte';
    import TelegramParentRewards from './TelegramParentRewards.svelte';
    import TelegramParentFamily from './TelegramParentFamily.svelte';
    import TelegramParentHeader from './TelegramParentHeader.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramConfirmModal from './TelegramConfirmModal.svelte';
    import TelegramTabBar from './ui/TelegramTabBar.svelte';
    import TelegramAsyncState from './ui/TelegramAsyncState.svelte';
    import type { TelegramAsyncState as AsyncState } from './ui/telegramUi';
    import type { TelegramTab } from './ui/telegramTabBar';
    import { parseTelegramWorkspaceContext, type ParentTab } from './telegramWorkspaceContext';

    const i18n = useI18n();

    export let publicOrigin = '';
    export let onViewAsChild: () => void = () => {};

    // EXPLAIN: Bot deep links pass ?context= so the exact Mini App context opens.
    const context = typeof window === 'undefined'
        ? '' : new URLSearchParams(window.location.search).get('context') ?? '';
    const workspaceContext = parseTelegramWorkspaceContext(context);
    let view: ParentTab = workspaceContext.parentTab;
    let loading = true;
    let error = '';
    $: pending = $appStore.requests.filter((request) => request.status === 'pending');
    $: tabs = [
        { id: 'home', icon: 'home', label: $i18n.t('app.telegram.shell.home'), count: pending.length },
        { id: 'tasks', icon: 'task', label: $i18n.t('app.telegram.shell.tasks') },
        { id: 'rewards', icon: 'reward', label: $i18n.t('app.telegram.shell.rewards') },
        { id: 'family', icon: 'family', label: $i18n.t('app.telegram.shell.family') },
    ] satisfies readonly TelegramTab[];

    onMount(async () => {
        loading = true;
        error = '';
        const ok = await initializeFromServer();
        if (!ok) error = $i18n.t('app.telegram.shell.loadError');
        else view = workspaceContext.parentTab;
        loading = false;
    });

    async function retry() {
        const ok = await refreshData();
        if (ok) { error = ''; view = 'home'; }
        else error = $i18n.t('app.telegram.shell.refreshError');
    }
    function selectView(next: string) { view = next as ParentTab; }
</script>

<main class="parent-workspace" aria-label={$i18n.t('app.telegram.shell.workspace')}>
    <TelegramParentHeader onViewAsChild={onViewAsChild} />

    <TelegramTabBar tabs={tabs} selected={view} idPrefix="parent" ariaLabel={$i18n.t('app.telegram.shell.workspace')} onSelect={selectView} />
    <div aria-labelledby={`parent-tab-${view}`} id={`parent-panel-${view}`} role="tabpanel" tabindex="0">
        {#if loading || error}
            <TelegramAsyncState state={(loading ? 'loading' : 'error') as AsyncState} loadingLabel={$i18n.t('app.telegram.shell.loading')} errorMessage={error} retryLabel={$i18n.t('app.telegram.shell.retry')} onRetry={retry} />
        {:else if view === 'home'}
            <TelegramParentHome initialContext={context} />
        {:else if view === 'tasks'}
            <TelegramParentTasks />
        {:else if view === 'rewards'}
            <TelegramParentRewards />
        {:else}
            <TelegramParentFamily />
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
    .parent-workspace { display: flex; flex-direction: column; width: 100%; max-width: 48rem; min-height: 100vh; margin: 0 auto; padding: calc(.75rem + env(safe-area-inset-top)) 1rem 2rem; }
    [role="tabpanel"] { flex: 1 1 auto; min-height: 0; }
    .site-link { display: flex; justify-content: center; margin-top: 1.25rem; }
    .site-link a { display: inline-flex; align-items: center; gap: .3rem; color: #8a93a8; font-size: .78rem; text-decoration: none; }
    .site-link a:hover { color: #3867d6; }
    .site-link a:focus-visible { outline: 3px solid #80aaff; outline-offset: 2px; border-radius: .3rem; }
    @media (max-width: 700px) {
        .parent-workspace { padding: calc(.65rem + env(safe-area-inset-top)) .75rem calc(5.75rem + env(safe-area-inset-bottom)); }
    }
</style>
