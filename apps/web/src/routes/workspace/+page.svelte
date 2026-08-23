<script lang="ts">
    import { onMount } from 'svelte';
    import WorkspaceRoleResolver from '$lib/features/workspace/WorkspaceRoleResolver.svelte';
    import type { PageData } from './$types';

    export let data: PageData;

    onMount(() => {
        document.body.classList.add('workspace-page');
        return () => document.body.classList.remove('workspace-page');
    });
</script>

<svelte:head><title>EarnIt Kids · Рабочее пространство</title></svelte:head>

<WorkspaceRoleResolver role={data.role} publicOrigin={data.publicOrigin} showBrowserPush={false} />

<style>
    :global(body.workspace-page) {
        padding-top: 0 !important;
    }
</style>
