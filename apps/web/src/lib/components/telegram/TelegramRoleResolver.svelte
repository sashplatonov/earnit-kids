<script lang="ts">
    import TelegramParentShell from './TelegramParentShell.svelte';
    import TelegramChildShell from './TelegramChildShell.svelte';

    export let role: string;
</script>

{#if role === 'parent' || role === 'admin' || role === 'super_admin'}
    <TelegramParentShell />
{:else}
    <TelegramChildShell />
{/if}
