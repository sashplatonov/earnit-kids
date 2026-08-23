<script lang="ts">
    import ParentWorkspaceShell from './ParentWorkspaceShell.svelte';
    import ChildWorkspaceShell from './ChildWorkspaceShell.svelte';
    import { resolveWorkspaceRole } from './workspaceRoleResolver';
    import BrowserPushControls from './notifications/BrowserPushControls.svelte';
    import WorkspaceSessionActions from './WorkspaceSessionActions.svelte';

    export let role = '';
    export let publicOrigin = '';
    export let allowPreview = false;
    export let showBrowserPush = true;
    export let showSessionActions = false;
    export let showAccessPanel = false;
    export let browserPushEnabled = true;

    let viewAsChild = false;

    function showChildPreview() {
        if (allowPreview) viewAsChild = true;
    }
</script>

{#if showSessionActions}<WorkspaceSessionActions />{/if}
{#if resolveWorkspaceRole(role) === 'parent' && !viewAsChild}
    <ParentWorkspaceShell {publicOrigin} {showAccessPanel} {browserPushEnabled} onViewAsChild={showChildPreview} />
{:else}
    <ChildWorkspaceShell {publicOrigin} onExitPreview={() => viewAsChild = false} />
{/if}
{#if showBrowserPush}<BrowserPushControls />{/if}
