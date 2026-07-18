<script lang="ts">
    export let status: 'idle' | 'pending' | 'success' | 'error' = 'idle';
    export let message = '';
    export let retryLabel = '';
    export let onRetry: (() => void) | null = null;
</script>

{#if status !== 'idle' && message}
<div class:catalog-feedback--error={status === 'error'} class:catalog-feedback--success={status === 'success'}
    class="catalog-feedback" role={status === 'error' ? 'alert' : 'status'} aria-live="polite">
    <span aria-hidden="true">{status === 'pending' ? '…' : status === 'success' ? '✓' : '!'}</span>
    <span>{message}</span>
    {#if status === 'error' && onRetry}
    <button type="button" class="catalog-feedback__retry" on:click={onRetry}>{retryLabel}</button>
    {/if}
</div>
{/if}

<style>
    .catalog-feedback {
        display: flex;
        align-items: center;
        gap: 0.4rem;
        min-width: 0;
        margin-top: 0.45rem;
        overflow-wrap: anywhere;
        color: var(--color-text-muted);
        font-size: var(--text-xs);
        font-weight: 700;
    }

    .catalog-feedback--success {
        color: var(--color-success, #16805c);
    }

    .catalog-feedback--error {
        color: var(--color-danger, #b42318);
    }

    .catalog-feedback__retry {
        min-height: var(--touch-target-height);
        padding: 0.35rem 0.5rem;
        border: 0;
        border-radius: var(--radius-sm);
        background: transparent;
        color: inherit;
        font: inherit;
        text-decoration: underline;
        cursor: pointer;
        touch-action: manipulation;
    }

    .catalog-feedback__retry:hover {
        background: color-mix(in oklch, currentColor 8%, transparent);
    }

    .catalog-feedback__retry:focus-visible {
        outline: 3px solid var(--ds-focus-ring, rgba(59, 130, 246, 0.6));
        outline-offset: 2px;
    }

    @media (prefers-reduced-motion: no-preference) {
        .catalog-feedback--success span:first-child {
            animation: catalog-feedback-pop 220ms ease-out;
        }
    }

    @keyframes catalog-feedback-pop {
        from { transform: scale(0.7); opacity: 0.4; }
        to { transform: scale(1); opacity: 1; }
    }
</style>
