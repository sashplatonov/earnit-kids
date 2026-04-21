<script lang="ts">
    import { onMount } from 'svelte';
    import AppHeader from './AppHeader.svelte';
    import AppNav from './AppNav.svelte';
    import Toast from './Toast.svelte';
    import AddChildModal from './modals/AddChildModal.svelte';
    import TaskModal from './modals/TaskModal.svelte';
    import ShopModal from './modals/ShopModal.svelte';
    import type { AppSection } from '$lib/app/routes';
    import type { SessionSnapshot } from '$lib/types/session';
    import { appStore, pendingRequestsCount } from '$lib/stores/app';
    import { initializeFromServer, refreshData } from '$lib/services/bootstrap';
    import { initializePwa } from '$lib/services/pwa';
    import { initializePushNotifications } from '$lib/services/push';
    import { startWebSocket, stopWebSocket } from '$lib/services/websocket';

    // __BUILD_TS__ is injected at build time by vite.config.ts define (declared in vite-env.d.ts)
    const buildTs: string = __BUILD_TS__;

    export let session: SessionSnapshot;
    export let activeSection: AppSection;

    const isAdmin = session.role === 'admin' || session.role === 'parent';

    $: balance = $appStore.balance;
    $: childNickname = $appStore.childNickname ?? session.childName ?? '';
    $: reqCount = $pendingRequestsCount;

    onMount(() => {
        void initializeFromServer();
        void initializePwa(() => refreshData(true)).then(() => initializePushNotifications());
        startWebSocket();
        return () => { stopWebSocket(); };
    });
</script>

<div class="app" class:app--admin={isAdmin} class:app--child={!isAdmin} data-role={isAdmin ? 'admin' : 'child'} id="app">
    <div class="pull-refresh-indicator" id="pull-refresh-indicator" aria-hidden="true">
        <span class="pull-refresh-indicator__icon">↻</span>
        <span class="pull-refresh-indicator__text" id="pull-refresh-indicator-text">Потяните для обновления</span>
    </div>
    <div class="offline-banner hidden" id="offline-status-banner" role="status" aria-live="polite">
        Сейчас оффлайн: история доступна, новые действия отправятся после восстановления сети.
    </div>

    <AppHeader {isAdmin} {balance} childNickname={String(childNickname)} />
    <AppNav {isAdmin} activeSection={activeSection} requestsCount={reqCount} />

    <main class="main" id="main-content">
        <slot />
    </main>

    <!-- Modal host -->
    <AddChildModal />
    <TaskModal />
    <ShopModal />

    <!-- Toast notifications -->
    <Toast />

    <footer class="app-footer">
        <span class="app-footer__meta">{buildTs}</span>
    </footer>
</div>
