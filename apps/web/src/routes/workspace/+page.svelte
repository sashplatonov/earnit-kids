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

<svelte:head>
    <title>EarnIt Kids · {data.role === 'child' ? 'Мой маршрут' : 'Моя семья'}</title>
</svelte:head>

<WorkspaceRoleResolver role={data.role} publicOrigin={data.publicOrigin} allowPreview showBrowserPush={false} showSessionActions />

<style>
    :global(body.workspace-page) {
        --color-bg: #f8fafc;
        --color-bg-light: #f1f5fd;
        --color-bg-card: #ffffff;
        --color-bg-hover: #eef4ff;
        --color-text-high-contrast: #0f172a;
        --color-text-strong: #18243d;
        --color-text-soft: #33415f;
        --color-text-muted: #66718a;
        --color-border-strong: #dfe7f5;
        background: #f8fafc;
        padding-top: 0 !important;
    }
</style>
