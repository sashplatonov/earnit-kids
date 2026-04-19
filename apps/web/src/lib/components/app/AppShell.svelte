<script lang="ts">
    import { onMount } from 'svelte';
    import AppHeader from './AppHeader.svelte';
    import AppNav from './AppNav.svelte';
    import Toast from './Toast.svelte';
    import AddChildModal from './modals/AddChildModal.svelte';
    import TaskModal from './modals/TaskModal.svelte';
    import ShopModal from './modals/ShopModal.svelte';
    import TasksSection from './sections/TasksSection.svelte';
    import ShopSection from './sections/ShopSection.svelte';
    import RequestsSection from './sections/RequestsSection.svelte';
    import HistorySection from './sections/HistorySection.svelte';
    import FriendsSection from './sections/FriendsSection.svelte';
    import AnalyticsSection from './sections/AnalyticsSection.svelte';
    import RulesSection from './sections/RulesSection.svelte';
    import LimitsSection from './sections/LimitsSection.svelte';
    import SettingsSection from './sections/SettingsSection.svelte';
    import CatalogSection from './sections/CatalogSection.svelte';
    import type { SessionSnapshot } from '$lib/types/session';
    import { tabStore } from '$lib/stores/tabs';
    import { appStore, pendingRequestsCount } from '$lib/stores/app';
    import { initializeFromServer, refreshData } from '$lib/services/bootstrap';
    import { initializePwa } from '$lib/services/pwa';
    import { initializePushNotifications } from '$lib/services/push';
    import { startWebSocket, stopWebSocket } from '$lib/services/websocket';

    export let session: SessionSnapshot;

    const isAdmin = session.role === 'admin' || session.role === 'parent';

    $: activeTab = $tabStore;
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

<div class="app" id="app">
    <div class="pull-refresh-indicator" id="pull-refresh-indicator" aria-hidden="true">
        <span class="pull-refresh-indicator__icon">↻</span>
        <span class="pull-refresh-indicator__text" id="pull-refresh-indicator-text">Потяните для обновления</span>
    </div>
    <div class="offline-banner hidden" id="offline-status-banner" role="status" aria-live="polite">
        Сейчас оффлайн: история доступна, новые действия отправятся после восстановления сети.
    </div>

    <AppHeader {isAdmin} {balance} childNickname={String(childNickname)} />
    <AppNav {isAdmin} {activeTab} requestsCount={reqCount}
        on:switch={e => tabStore.setTab(e.detail as import('$lib/stores/tabs').TabName)} />

    <main class="main" id="main-content">
        {#if activeTab === 'analytics'}
            <AnalyticsSection />
        {:else if activeTab === 'tasks'}
            <TasksSection />
        {:else if activeTab === 'shop'}
            <ShopSection />
        {:else if activeTab === 'requests'}
            <RequestsSection />
        {:else if activeTab === 'history'}
            <HistorySection />
        {:else if activeTab === 'friends'}
            <FriendsSection />
        {:else if activeTab === 'rules'}
            <RulesSection />
        {:else if activeTab === 'limits'}
            <LimitsSection />
        {:else if activeTab === 'settings'}
            <SettingsSection />
        {:else if activeTab === 'catalog'}
            <CatalogSection />
        {/if}
    </main>

    <!-- Modal host -->
    <AddChildModal />
    <TaskModal />
    <ShopModal />

    <!-- Toast notifications -->
    <Toast />
</div>
