<script lang="ts">
    import { page } from '$app/stores';
    import { getAppSectionFromPath, getDefaultAppSection } from '$lib/app/routes';
    import AppShell from '$lib/components/app/AppShell.svelte';
    import type { LayoutData } from './$types';

    export let data: LayoutData;

    $: activeSection = getAppSectionFromPath($page.url.pathname) ?? getDefaultAppSection(data.session.role);
</script>

<AppShell session={data.session} {activeSection}>
    <slot />
</AppShell>