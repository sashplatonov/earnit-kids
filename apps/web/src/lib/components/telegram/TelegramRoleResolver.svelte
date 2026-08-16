<script lang="ts">
    import TelegramParentShell from './TelegramParentShell.svelte';
    import TelegramChildShell from './TelegramChildShell.svelte';

    export let role: string;
    export let publicOrigin = '';

    // EXPLAIN: A parent can preview the child experience. When viewAsChild is
    // EXPLAIN: true, the child shell is rendered instead of the parent shell.
    let viewAsChild = false;
</script>

{#if (role === 'parent' || role === 'admin' || role === 'super_admin') && !viewAsChild}
    <TelegramParentShell {publicOrigin} onViewAsChild={() => viewAsChild = true} />
{:else}
    <TelegramChildShell {publicOrigin} onExitPreview={() => viewAsChild = false} />
{/if}
