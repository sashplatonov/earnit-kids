<script lang="ts">
    import ParentWorkspaceShell from './ParentWorkspaceShell.svelte';
    import ChildWorkspaceShell from './ChildWorkspaceShell.svelte';
    import { resolveWorkspaceRole } from './workspaceRoleResolver';
    import BrowserPushControls from './notifications/BrowserPushControls.svelte';
    import type { MembershipPermission } from '$lib/types/auth';

    interface Props {
        role?: string;
        publicOrigin?: string;
        allowPreview?: boolean;
        showBrowserPush?: boolean;
        showSessionActions?: boolean;
        showAccessPanel?: boolean;
        permission?: MembershipPermission | null;
    }

    let {
        role = '',
        publicOrigin = '',
        allowPreview = false,
        showBrowserPush = true,
        showSessionActions = false,
        showAccessPanel = false,
        permission = null
    }: Props = $props();
    let viewAsChild = $state(false);

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
