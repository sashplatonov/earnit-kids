<script lang="ts">
    import TelegramParentShell from '$lib/components/telegram/TelegramParentShell.svelte';
    import ParentAccessPanel from './family/ParentAccessPanel.svelte';
    import type { MembershipPermission } from '$lib/types/auth';

    export let publicOrigin = '';
    export let onViewAsChild: () => void = () => {};
    export let showAccessPanel = false;
    export let showSessionActions = false;
    export let permission: MembershipPermission | null = null;
</script>

<div class="workspace-parent">
    {#if showAccessPanel}<ParentAccessPanel />{/if}
    <TelegramParentShell {publicOrigin} {onViewAsChild} {showSessionActions} {permission} />
</div>

<style>
    .workspace-parent{display:grid;gap:1rem;min-width:0}
</style>
