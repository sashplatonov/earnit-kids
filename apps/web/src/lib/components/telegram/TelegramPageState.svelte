<script lang="ts">
    import TelegramActionButton from './TelegramActionButton.svelte';

    export let kind: 'loading' | 'empty' | 'error' = 'loading';
    export let message = '';
    export let retry: (() => void) | undefined = undefined;
</script>

<section class:state--error={kind === 'error'} class:state--empty={kind === 'empty'} class="state" role={kind === 'error' ? 'alert' : 'status'} aria-live="polite">
    {#if kind === 'loading'}
        <div class="skeleton" aria-hidden="true"></div>
        <p>{message || 'Loading…'}</p>
    {:else}
        <p>{message}</p>
        {#if kind === 'error' && retry}
            <TelegramActionButton icon="refresh" label="Retry" on:click={retry} />
        {/if}
    {/if}
</section>

<style>
    .state { display:grid; justify-items:center; gap:.75rem; padding:2rem 1rem; color:#66718a; text-align:center; }
    .state--error { color:#a33b3b; }
    .skeleton { width:min(100%, 20rem); height:3.5rem; border-radius:.7rem; background:linear-gradient(90deg,#eef1f7 25%,#f8f9fc 50%,#eef1f7 75%); background-size:200% 100%; animation:shimmer 1.3s infinite; }
    @keyframes shimmer { to { background-position:-200% 0; } }
</style>
