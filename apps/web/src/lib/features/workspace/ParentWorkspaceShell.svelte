<script lang="ts">
    import TelegramParentShell from '$lib/components/telegram/TelegramParentShell.svelte';
    import ParentAccessPanel from './family/ParentAccessPanel.svelte';

    export let publicOrigin = '';
    export let onViewAsChild: () => void = () => {};
    export let showAccessPanel = false;
</script>

<div class="workspace-parent">
    {#if showAccessPanel}<ParentAccessPanel />{/if}
    <TelegramParentShell {publicOrigin} {onViewAsChild} />
</div>

<style>.workspace-parent{display:grid;gap:1rem;min-width:0}@media(max-width:520px){.workspace-parent{gap:.75rem}}</style>
