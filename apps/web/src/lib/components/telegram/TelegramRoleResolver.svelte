<script lang="ts">
    import TelegramParentShell from './TelegramParentShell.svelte';
    import TelegramChildShell from './TelegramChildShell.svelte';

    export let role: string;
    export let publicOrigin = '';
</script>

{#if role === 'parent' || role === 'admin' || role === 'super_admin'}
    <TelegramParentShell {publicOrigin} />
{:else}
    <TelegramChildShell {publicOrigin} />
{/if}
