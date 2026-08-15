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

    const i18n = useI18n();

    export let publicOrigin = '';

    // EXPLAIN: Bot deep links pass ?context= so the exact Mini App context opens.
    const context = new URLSearchParams(window.location.search).get('context') ?? '';
    let view: 'home' | 'tasks' | 'rewards' | 'family' = tabForContext(context);
    let loading = true;
    let error = '';
    $: pending = $appStore.requests.filter((request) => request.status === 'pending');
    const tabs = ['home', 'tasks', 'rewards', 'family'] as const;

    function tabForContext(value: string): 'home' | 'tasks' | 'rewards' | 'family' {
        if (value === 'tasks') return 'tasks';
        if (value === 'rewards') return 'rewards';
        if (value === 'family') return 'family';
        return 'home';
    }

    onMount(async () => {
        loading = true;
        error = '';
        const ok = await initializeFromServer();
        if (!ok) error = $i18n.t('app.telegram.shell.loadError');
        else view = tabForContext(context);
        loading = false;
    });

    async function retry() {
        const ok = await refreshData();
        if (ok) { error = ''; view = 'home'; }
        else error = $i18n.t('app.telegram.shell.refreshError');
    }
    function selectView(next: typeof tabs[number]) {
        view = next;
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

<main class="parent-workspace" aria-label={$i18n.t('app.telegram.shell.workspace')}>
    <TelegramParentHeader />

    <div class="tabs" aria-label={$i18n.t('app.telegram.shell.workspace')} role="tablist" tabindex="-1" on:keydown={handleTabKeydown}>
        <button aria-controls="parent-panel-home" aria-selected={view === 'home'} class:active={view === 'home'} id="parent-tab-home" role="tab" tabindex={view === 'home' ? 0 : -1} type="button" on:click={() => selectView('home')}><TelegramIcon name="home" size={20} label={$i18n.t('app.telegram.shell.home')} /><span>{$i18n.t('app.telegram.shell.home')}{pending.length ? ` (${pending.length})` : ''}</span></button>
        <button aria-controls="parent-panel-tasks" aria-selected={view === 'tasks'} class:active={view === 'tasks'} id="parent-tab-tasks" role="tab" tabindex={view === 'tasks' ? 0 : -1} type="button" on:click={() => selectView('tasks')}><TelegramIcon name="task" size={20} label={$i18n.t('app.telegram.shell.tasks')} /><span>{$i18n.t('app.telegram.shell.tasks')}</span></button>
        <button aria-controls="parent-panel-rewards" aria-selected={view === 'rewards'} class:active={view === 'rewards'} id="parent-tab-rewards" role="tab" tabindex={view === 'rewards' ? 0 : -1} type="button" on:click={() => selectView('rewards')}><TelegramIcon name="reward" size={20} label={$i18n.t('app.telegram.shell.rewards')} /><span>{$i18n.t('app.telegram.shell.rewards')}</span></button>
        <button aria-controls="parent-panel-family" aria-selected={view === 'family'} class:active={view === 'family'} id="parent-tab-family" role="tab" tabindex={view === 'family' ? 0 : -1} type="button" on:click={() => selectView('family')}><TelegramIcon name="family" size={20} label={$i18n.t('app.telegram.shell.family')} /><span>{$i18n.t('app.telegram.shell.family')}</span></button>
    </div>
    <div aria-labelledby={`parent-tab-${view}`} id={`parent-panel-${view}`} role="tabpanel" tabindex="0">
        {#if loading}
            <p class="state" role="status">{$i18n.t('app.telegram.shell.loading')}</p>
        {:else if error}
            <section class="state state--error" role="alert"><p>{error}</p><button type="button" on:click={retry}><TelegramIcon name="refresh" size={18} label={$i18n.t('app.telegram.shell.retry')} />{$i18n.t('app.telegram.shell.retry')}</button></section>
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
    {#if publicOrigin}
        <footer class="site-link" aria-label={$i18n.t('app.telegram.shell.publicSiteAria')}>
            <a href={publicOrigin} target="_blank" rel="noopener noreferrer"><TelegramIcon name="link" size={14} label={$i18n.t('app.telegram.shell.publicSiteAria')} />{$i18n.t('app.telegram.shell.publicSite')}</a>
        </footer>
    {/if}
</main>

<style>
    .parent-workspace { width: 100%; max-width: 48rem; margin: 0 auto; padding: calc(.75rem + env(safe-area-inset-top)) 1rem 2rem; }
    .tabs { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .25rem; margin-bottom: .75rem; }
    button { min-height: 2.75rem; border: 1px solid #dfe4ee; border-radius: .75rem; background: #fff; color: #33415f; font: inherit; cursor: pointer; }
    button:focus-visible { outline: 3px solid #80aaff; outline-offset: 2px; }
    .tabs button.active { border-color: #b9c0ff; background: #fff; color: #2854ba; font-weight: 750; }
    .tabs button { display: inline-flex; align-items: center; justify-content: center; gap: .2rem; padding-inline: .25rem; white-space: nowrap; }
    .state { padding: 2rem 1rem; text-align: center; color: #5c6780; }
    .state--error { color: #a33b3b; }
    .site-link { display: flex; justify-content: center; margin-top: 1.25rem; }
    .site-link a { display: inline-flex; align-items: center; gap: .3rem; color: #8a93a8; font-size: .78rem; text-decoration: none; }
    .site-link a:hover { color: #3867d6; }
    .site-link a:focus-visible { outline: 3px solid #80aaff; outline-offset: 2px; border-radius: .3rem; }
    @media (max-width: 700px) {
        .parent-workspace { padding: calc(.65rem + env(safe-area-inset-top)) .75rem calc(5.75rem + env(safe-area-inset-bottom)); }
        .tabs { position: fixed; z-index: 20; right: 0; bottom: 0; left: 0; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0; margin: 0; padding: .3rem .35rem calc(.3rem + env(safe-area-inset-bottom)); border-top: 1px solid #dfe4ee; background: rgb(255 255 255 / 96%); box-shadow: 0 -8px 24px rgb(24 36 61 / 8%); }
        .tabs button { min-height: 3rem; flex-direction: column; gap: .2rem; border: 0; border-radius: .65rem; font-size: .68rem; }
        .tabs button.active { color: #2854ba; font-weight: 750; }
    }
    @media (max-width: 340px) { .tabs button { font-size: .61rem; } }
</style>
