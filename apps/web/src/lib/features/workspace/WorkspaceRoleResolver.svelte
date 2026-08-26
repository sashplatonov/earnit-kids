<script lang="ts">
    import ParentWorkspaceShell from './ParentWorkspaceShell.svelte';
    import ChildWorkspaceShell from './ChildWorkspaceShell.svelte';
    import { resolveWorkspaceRole } from './workspaceRoleResolver';
    import BrowserPushControls from './notifications/BrowserPushControls.svelte';
    import type { MembershipPermission } from '$lib/types/auth';

    export let role = '';
    export let publicOrigin = '';
    export let allowPreview = false;
    export let showBrowserPush = true;
    export let showSessionActions = false;
    export let showAccessPanel = false;
    export let permission: MembershipPermission | null = null;
    let viewAsChild = false;

    function showChildPreview() {
        if (allowPreview) viewAsChild = true;
    }
</script>

{#if resolveWorkspaceRole(role) === 'parent' && !viewAsChild}
    <ParentWorkspaceShell {publicOrigin} {showAccessPanel} {showSessionActions} {permission} onViewAsChild={showChildPreview} />
{:else if viewAsChild}
    <ChildWorkspaceShell {publicOrigin} {showSessionActions} onExitPreview={() => viewAsChild = false} />
{:else}
    <ChildWorkspaceShell {publicOrigin} {showSessionActions} />
{/if}
{#if showBrowserPush}<BrowserPushControls />{/if}
