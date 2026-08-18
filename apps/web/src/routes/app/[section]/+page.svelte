<script lang="ts">
    import AnalyticsSection from '$lib/components/app/sections/AnalyticsSection.svelte';
    import CatalogSection from '$lib/components/app/sections/CatalogSection.svelte';
    import FriendsSection from '$lib/components/app/sections/FriendsSection.svelte';
    import HistorySection from '$lib/components/app/sections/HistorySection.svelte';
    import LimitsSection from '$lib/components/app/sections/LimitsSection.svelte';
    import RequestsSection from '$lib/components/app/sections/RequestsSection.svelte';
    import RulesSection from '$lib/components/app/sections/RulesSection.svelte';
    import SettingsSection from '$lib/components/app/sections/SettingsSection.svelte';
    import TasksSection from '$lib/components/app/sections/TasksSection.svelte';
    import type { PageData } from './$types';

    export let data: PageData;
</script>

<svelte:head>
    <title>{data.metaTitle} | EarnIt Kids</title>
    <link rel="canonical" href={data.canonicalPath} />
</svelte:head>

{#if data.section === 'analytics'}
    <AnalyticsSection />
{:else if data.section === 'tasks'}
    <TasksSection />
{:else if data.section === 'requests'}
    <RequestsSection />
{:else if data.section === 'history'}
    <HistorySection />
{:else if data.section === 'friends'}
    <FriendsSection />
{:else if data.section === 'rules'}
    <RulesSection />
{:else if data.section === 'settings'}
    <SettingsSection />
{:else if data.section === 'limits'}
    <LimitsSection />
{:else if data.section === 'catalog'}
    <CatalogSection />
{/if}