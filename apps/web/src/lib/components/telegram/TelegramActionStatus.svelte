<script lang="ts">
    interface Props {
        state?: 'idle' | 'pending' | 'success' | 'error' | 'stale';
        message?: string;
    }

    let { state = 'idle', message = '' }: Props = $props();
</script>

{#if state !== 'idle'}
    <p class:status--error={state === 'error' || state === 'stale'} class="status" role={state === 'error' || state === 'stale' ? 'alert' : 'status'} aria-live="polite">
        {message}
    </p>
{/if}

<style>
    .status { margin:.75rem 0; padding:.7rem .8rem; border-radius:.7rem; background:#eef5ff; color:#254a8d; }
    .status--error { background:#fff0f0; color:#a33b3b; }
</style>
