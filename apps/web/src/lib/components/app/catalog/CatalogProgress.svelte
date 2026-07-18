<script lang="ts">
    export let label = '';
    export let detail = '';
    export let hint = '';
    export let value = 0;
    export let max = 0;
    export let tone: 'available' | 'pending' | 'complete' | 'locked' = 'available';
    $: boundedValue = Math.max(0, Math.min(value, max));
    $: percent = max > 0 ? Math.round((boundedValue / max) * 100) : 0;
</script>

{#if max > 0}
<div class={`catalog-progress catalog-progress--${tone}`}>
    <div class="catalog-progress__copy">
        <span>{label}</span>
        <strong>{detail}</strong>
    </div>
    <div class="catalog-progress__track" role="progressbar" aria-label={label} aria-valuemin="0" aria-valuemax={max} aria-valuenow={boundedValue}>
        <span style={`width: ${percent}%`}></span>
    </div>
    {#if hint}<small>{hint}</small>{/if}
</div>
{/if}

<style>
    .catalog-progress {
        display: grid;
        gap: 0.4rem;
        min-width: 0;
        padding: 0.65rem 0.75rem;
        border: 1px solid var(--catalog-surface-border);
        border-radius: var(--radius-md);
        background: color-mix(in oklch, var(--color-bg-card) 88%, var(--color-primary) 4%);
    }

    .catalog-progress__copy {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: 0.75rem;
        color: var(--color-text-muted);
        font-size: var(--text-xs);
        line-height: 1.25;
    }

    .catalog-progress__copy strong {
        min-width: 0;
        color: var(--color-text-high-contrast);
        font-variant-numeric: tabular-nums;
        overflow-wrap: anywhere;
        text-align: right;
    }

    .catalog-progress__track {
        height: 0.55rem;
        overflow: hidden;
        border-radius: 999px;
        background: var(--catalog-progress-track);
    }

    .catalog-progress__track span {
        display: block;
        height: 100%;
        border-radius: inherit;
        background: var(--catalog-progress-fill);
        transition: width var(--transition-normal);
    }

    .catalog-progress small {
        color: var(--color-text-muted);
        font-size: var(--text-xs);
        line-height: 1.3;
        overflow-wrap: anywhere;
    }

    .catalog-progress--pending .catalog-progress__track span {
        background: var(--catalog-status-pending-text);
    }

    .catalog-progress--locked {
        opacity: 0.74;
    }

    @media (prefers-reduced-motion: reduce) {
        .catalog-progress__track span { transition: none; }
    }
</style>
