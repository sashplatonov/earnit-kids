<script lang="ts">
    import { onMount } from 'svelte';
    import AppHeader from './AppHeader.svelte';
    import AppNav from './AppNav.svelte';
    import Toast from './Toast.svelte';
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
    import { initializeFromServer } from '$lib/services/bootstrap';
    import { startWebSocket, stopWebSocket } from '$lib/services/websocket';

    export let session: SessionSnapshot;

    const isAdmin = session.role === 'admin' || session.role === 'parent';

    $: activeTab = $tabStore;
    $: balance = $appStore.balance;
    $: childNickname = $appStore.childNickname ?? session.childName ?? '';
    $: reqCount = $pendingRequestsCount;

    onMount(() => {
        void initializeFromServer();
        startWebSocket();
        return () => { stopWebSocket(); };
    });
</script>

<div class="app" id="app">
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
    <TaskModal />
    <ShopModal />

    <!-- Toast notifications -->
    <Toast />
</div>
