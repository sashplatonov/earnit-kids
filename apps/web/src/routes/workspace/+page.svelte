<script lang="ts">
    import WorkspaceRoleResolver from '$lib/features/workspace/WorkspaceRoleResolver.svelte';
    import type { PageData } from './$types';

    export let data: PageData;
</script>

<svelte:head><title>EarnIt Kids · Workspace</title></svelte:head>

<WorkspaceRoleResolver role={data.role} publicOrigin={data.publicOrigin} showSessionActions showAccessPanel />
