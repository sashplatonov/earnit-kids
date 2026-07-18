<script lang="ts">
    import type { ShopItem } from '$lib/stores/app';
    export let item: ShopItem | null = null;
    export let balance = 0;
    export let label = '';
    export let clearLabel = '';
    export let staleLabel = '';
    export let emptyLabel = '';
    export let stale = false;
    export let readyLabel = '';
    export let missingLabel: (amount: number) => string = (amount) => String(amount);
    export let onClear: (() => void) | null = null;
    export let compact = false;
    export let disabled = false;
    export let formatNumber: (value: number) => string = (value) => String(value);
    $: price = item?.price ?? 0;
    $: progressValue = Math.max(0, Math.min(balance, price));
    $: percent = price > 0 ? Math.max(0, Math.min(100, Math.round((balance / price) * 100))) : 0;
    $: missing = Math.max(0, price - balance);
</script>

{#if item}
<section class:reward-goal--compact={compact} class="reward-goal" aria-label={label} aria-live="polite">
    <div class="reward-goal__copy">
        <span>{label}</span>
        <strong>{item.name}</strong>
    </div>
    <div class="reward-goal__progress" role="progressbar" aria-valuemin="0" aria-valuemax={price}
        aria-valuenow={progressValue} aria-label={item.name}>
        <span style={`width: ${percent}%`}></span>
    </div>
    <small>{formatNumber(balance)} / {formatNumber(price)} · {missing > 0 ? missingLabel(missing) : readyLabel}</small>
    {#if onClear}
    <button type="button" class="reward-goal__clear" {disabled} on:click={onClear}>{clearLabel}</button>
    {/if}
</section>
{:else}
<section class="reward-goal reward-goal--stale" aria-label={stale ? staleLabel : emptyLabel}>
    <span>{stale ? staleLabel : emptyLabel}</span>
    {#if stale && onClear}
    <button type="button" class="reward-goal__clear" {disabled} on:click={onClear}>{clearLabel}</button>
    {/if}
</section>
{/if}

<style>
    .reward-goal {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        gap: 0.45rem 0.8rem;
        align-items: center;
        min-width: 0;
        margin: 0 0 1rem;
        padding: 0.8rem 1rem;
        border: 1px solid color-mix(in oklch, var(--color-secondary) 30%, transparent);
        border-radius: var(--radius-lg);
        background: color-mix(in oklch, var(--color-secondary) 9%, var(--color-bg-card));
    }

    .reward-goal__copy {
        display: grid;
        min-width: 0;
        gap: 0.15rem;
    }

    .reward-goal__copy span,
    .reward-goal small {
        color: var(--color-text-muted);
        font-size: var(--text-xs);
        font-variant-numeric: tabular-nums;
        font-weight: 700;
    }

    .reward-goal__copy strong {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .reward-goal__progress {
        grid-column: 1 / -1;
        height: 0.5rem;
        overflow: hidden;
        border-radius: 99px;
        background: var(--catalog-progress-track);
    }

    .reward-goal__progress span {
        display: block;
        height: 100%;
        border-radius: inherit;
        background: var(--catalog-progress-fill);
        transition: width var(--transition-normal);
    }

    .reward-goal__clear {
        min-height: var(--touch-target-height);
        padding: 0.35rem 0.5rem;
        border: 0;
        border-radius: var(--radius-sm);
        background: transparent;
        color: var(--color-primary-dark);
        font: inherit;
        font-size: var(--text-xs);
        font-weight: 800;
        text-decoration: underline;
        cursor: pointer;
        touch-action: manipulation;
    }

    .reward-goal__clear:hover {
        background: color-mix(in oklch, var(--color-primary) 8%, transparent);
    }

    .reward-goal__clear:focus-visible {
        outline: 3px solid var(--ds-focus-ring, rgba(59, 130, 246, 0.6));
        outline-offset: 2px;
    }

    .reward-goal__clear:disabled {
        cursor: wait;
        opacity: 0.6;
    }

    .reward-goal--compact {
        grid-column: 1 / -1;
        width: 100%;
        margin-top: 0.8rem;
        margin-bottom: 0;
        padding: 0.65rem 0.8rem;
    }

    .reward-goal--stale {
        display: flex;
        justify-content: space-between;
        min-height: var(--catalog-control-height);
        color: var(--color-text-muted);
        font-size: var(--text-xs);
    }

    @media (prefers-reduced-motion: reduce) {
        .reward-goal__progress span {
            transition: none;
        }
    }
</style>
