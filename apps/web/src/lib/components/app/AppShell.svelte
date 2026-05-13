<script lang="ts">
    import { onMount } from 'svelte';
    import AppHeader from './AppHeader.svelte';
    import AppNav from './AppNav.svelte';
    import Toast from './Toast.svelte';
    import AddChildModal from './modals/AddChildModal.svelte';
    import RequestNoteModal from './modals/RequestNoteModal.svelte';
    import TaskModal from './modals/TaskModal.svelte';
    import ShopModal from './modals/ShopModal.svelte';
    import type { AppSection } from '$lib/app/routes';
    import { useI18n } from '$lib/i18n/context';
    import type { SessionSnapshot } from '$lib/types/session';
    import { appStore, pendingRequestsCount } from '$lib/stores/app';
    import { initializeFromServer, refreshData } from '$lib/services/bootstrap';
    import { initializePwa } from '$lib/services/pwa';
    import { initializePushNotifications } from '$lib/services/push';
    import { startWebSocket, stopWebSocket } from '$lib/services/websocket';

    // __BUILD_TS__ is injected at build time by vite.config.ts define (declared in vite-env.d.ts)
    const buildTs: string = __BUILD_TS__;
    const i18n = useI18n();

    export let session: SessionSnapshot;
    export let activeSection: AppSection;

    const isAdmin = session.role === 'admin' || session.role === 'parent' || session.role === 'super_admin';
    const isSuperAdmin = session.role === 'super_admin';


    $: balance = $appStore.balance;
    $: childNickname = $appStore.childNickname ?? session.childName ?? '';
    $: reqCount = $pendingRequestsCount;

    onMount(() => {
        let mounted = true;
        let cleanupPwa: (() => void) | null = null;

        appStore.setState({
            permission: session.permission ?? null,
            familyId: (session.familyId as string | null | undefined) ?? null,
        });
        void initializeFromServer();
        void initializePwa(() => refreshData(true)).then((cleanup) => {
            if (!mounted) {
                cleanup();
                return null;
            }

            cleanupPwa = cleanup;
            return initializePushNotifications();
        });
        startWebSocket();
        return () => {
            mounted = false;
            cleanupPwa?.();
            stopWebSocket();
        };
    });
</script>

<div class="app" class:app--admin={isAdmin} class:app--child={!isAdmin} data-role={isAdmin ? 'admin' : 'child'} id="app">
    <div class="pull-refresh-indicator" id="pull-refresh-indicator" aria-hidden="true">
        <span class="pull-refresh-indicator__icon">↻</span>
        <span class="pull-refresh-indicator__text" id="pull-refresh-indicator-text">{$i18n.t('app.shell.pullToRefresh')}</span>
    </div>
    <div class="offline-banner hidden" id="offline-status-banner" role="status" aria-live="polite">
        {$i18n.t('app.shell.offlineBanner')}
    </div>

    <AppHeader {isAdmin} {balance} childNickname={String(childNickname)} />
    <AppNav {isAdmin} {isSuperAdmin} activeSection={activeSection} requestsCount={reqCount} />


    <main class="main" id="main-content">
        <slot />
    </main>

    <!-- Modal host -->
    <AddChildModal />
    <RequestNoteModal />
    <TaskModal />
    <ShopModal />

    <!-- Toast notifications -->
    <Toast />

    <footer class="app-footer">
        <span class="app-footer__meta">{buildTs}</span>
    </footer>
</div>
