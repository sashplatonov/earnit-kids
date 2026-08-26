<script lang="ts">
    import { onMount } from 'svelte';
    import TelegramParentShell from '$lib/components/telegram/TelegramParentShell.svelte';
    import TelegramChildShell from '$lib/components/telegram/TelegramChildShell.svelte';
    import TelegramIcon from '$lib/components/telegram/TelegramIcon.svelte';
    import { useI18n } from '$lib/i18n/context';
    import { provideWorkspaceActions } from '$lib/features/workspace/workspaceActions';
    import { provideTaskActions } from '$lib/telegram/services/taskActions';
    import { provideRewardActions } from '$lib/telegram/services/rewardActions';
    import { provideRequestActions } from '$lib/telegram/services/requestActions';
    import { provideFamilyActions } from '$lib/telegram/services/familyActions';
    import { provideHistoryActions } from '$lib/telegram/services/historyActions';
    import { provideRewardRequestActions } from '$lib/telegram/services/rewardRequestActions';
    import { createLiveCoinShopDemoSession } from './liveCoinShopDemoSession';
    import { createLiveCoinShopDemoWorkspace } from './liveCoinShopDemoWorkspace';

    const i18n = useI18n();
    export let publicOrigin = '';
    const session = createLiveCoinShopDemoSession($i18n.locale);
    const workspace = createLiveCoinShopDemoWorkspace(session);
    provideWorkspaceActions(workspace.workspace);
    provideTaskActions(workspace.tasks);
    provideRewardActions(workspace.rewards);
    provideRequestActions(workspace.requests);
    provideFamilyActions(workspace.family);
    provideHistoryActions(workspace.history);
    // EXPLAIN: Context must be installed during component initialisation so
    // TelegramChildRewards receives the demo action before it is created.
    provideRewardRequestActions(session.actions);

    let mounted = false;
    let demoView: 'parent' | 'child' = 'parent';
    let shellRevision = 0;
    let announcement = '';

    onMount(() => {
        session.initialize();
        mounted = true;

        return () => {
            session.teardown();
        };
    });

    function resetDemo(): void {
        session.reset();
        demoView = 'parent';
        shellRevision += 1;
        announcement = $i18n.t('app.liveDemo.resetDone');
    }
</script>

<svelte:head>
    <title>{$i18n.t('app.liveDemo.title')} · EarnIt Kids</title>
</svelte:head>

<main class="demo-page">
    <div class="demo-toolbar">
        <p class="notice" role="note">{$i18n.t('app.liveDemo.temporaryData')}</p>
        <a class="public-link" href={publicOrigin || '/'}><TelegramIcon name="link" size={16} label={$i18n.t('app.liveDemo.publicSite')} />{$i18n.t('app.liveDemo.publicSite')}</a>
        <button class="reset" type="button" on:click={resetDemo}><TelegramIcon name="refresh" size={16} label={$i18n.t('app.liveDemo.reset')} />{$i18n.t('app.liveDemo.reset')}</button>
    </div>
    <p class="announcement" aria-live="polite">{announcement}</p>
    {#if mounted}
        {#key shellRevision}
            {#if demoView === 'parent'}
                <TelegramParentShell {publicOrigin} permission="family_admin" demoMode onViewAsChild={() => demoView = 'child'} />
            {:else}
                <TelegramChildShell {publicOrigin} onExitPreview={() => demoView = 'parent'} />
            {/if}
        {/key}
    {/if}
</main>

<style>
    .demo-page { min-height:100vh; box-sizing:border-box; padding:0 1rem 2rem; background:#f8fafc; color:#18243d; }
    .demo-toolbar { display:flex; align-items:center; gap:.65rem; width:min(100%, 48rem); margin:0 auto; padding:.35rem 0; }
    .public-link, .reset { display:inline-flex; align-items:center; justify-content:center; gap:.35rem; min-height:2.25rem; padding:.35rem .65rem; border-radius:.6rem; font:inherit; font-size:.86rem; font-weight:650; white-space:nowrap; }
    .public-link { color:#2454bb; text-decoration:none; }
    .reset { flex:none; border:1px solid #3867d6; background:#fff; color:#2454bb; cursor:pointer; }
    .reset:focus-visible { outline:3px solid #93b4ff; outline-offset:2px; }
    .notice { flex:1; margin:0; color:#52627d; font-size:.74rem; line-height:1.25; }
    .announcement { width:min(100%, 48rem); margin:0 auto; color:#26734d; font-size:.8rem; }
    .announcement:empty { display:none; }
    @media (max-width: 420px) { .demo-toolbar { align-items:stretch; flex-direction:column; gap:.4rem; } .reset { width:100%; } }
</style>
