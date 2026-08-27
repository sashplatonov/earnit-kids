<script lang="ts">
    import TelegramParentShell from '$lib/components/telegram/TelegramParentShell.svelte';
    import ParentAccessPanel from './family/ParentAccessPanel.svelte';
    import type { MembershipPermission } from '$lib/types/auth';

    interface Props {
        publicOrigin?: string;
        onViewAsChild?: () => void;
        showAccessPanel?: boolean;
        showSessionActions?: boolean;
        permission?: MembershipPermission | null;
    }

    let {
        publicOrigin = '',
        onViewAsChild = () => {},
        showAccessPanel = false,
        showSessionActions = false,
        permission = null
    }: Props = $props();
</script>

<div class="workspace-parent">
    {#if showAccessPanel}<ParentAccessPanel />{/if}
    <TelegramParentShell {publicOrigin} {onViewAsChild} {showSessionActions} {permission} />
</div>

<style>
    .workspace-parent{display:grid;gap:1rem;min-width:0}
</style>
