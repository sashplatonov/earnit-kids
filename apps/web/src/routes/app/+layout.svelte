<script lang="ts">
    import { browser } from '$app/environment';
    import { page } from '$app/stores';
    import { getAppSectionFromPath, getDefaultAppSection, LAST_APP_SECTION_COOKIE } from '$lib/app/routes';
    import AppShell from '$lib/components/app/AppShell.svelte';
    import type { LayoutData } from './$types';

    export let data: LayoutData;

    $: activeSection = getAppSectionFromPath($page.url.pathname) ?? getDefaultAppSection(data.session.role);
    $: if (browser && activeSection) {
        document.cookie = `${LAST_APP_SECTION_COOKIE}=${activeSection}; Path=/; Max-Age=31536000; SameSite=Lax`;
    }
</script>

<AppShell session={data.session} {activeSection} publicOrigin={data.appConfig.publicOrigin}>
    <slot />
</AppShell>
