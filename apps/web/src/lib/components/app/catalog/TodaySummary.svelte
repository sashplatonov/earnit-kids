<script lang="ts">
    import type { ShopItem, Task } from '$lib/stores/app';
    import type { TodayTaskSummary } from '$lib/services/todayTaskViewModel';
    import RewardGoalProgress from './RewardGoalProgress.svelte';

    export let summary: TodayTaskSummary;
    export let title = '';
    export let progressLabel = '';
    export let emptyProgressLabel = '';
    export let availableLabel = '';
    export let nextLabel = '';
    export let nextActionLabel = '';
    export let onNext: ((task: Task) => void) | null = null;
    export let rewardGoal: ShopItem | null = null;
    export let rewardGoalSelected = false;
    export let balance = 0;
    export let goalLabel = '';
    export let goalReadyLabel = '';
    export let goalMissingLabel: (amount: number) => string = (amount) => String(amount);
    export let goalEmptyLabel = '';
    export let goalStaleLabel = '';
    export let formatNumber: (value: number) => string = (value) => String(value);

    $: progressPercent = summary.limitCount > 0
        ? Math.min(100, Math.round((summary.completedCount / summary.limitCount) * 100))
        : 0;
</script>

<section class="today-summary" aria-label={title}>
    <div class="today-summary__intro">
        <p class="today-summary__eyebrow">{title}</p>
        <p class="today-summary__progress">{summary.trackedCount > 0 ? progressLabel : emptyProgressLabel}</p>
        {#if summary.trackedCount > 0}
        <div class="today-summary__track" role="progressbar" aria-valuemin="0" aria-valuemax={summary.limitCount} aria-valuenow={summary.completedCount}>
            <span class="today-summary__fill" style={`width: ${progressPercent}%`}></span>
        </div>
        {/if}
    </div>
    {#if summary.trackedCount > 0}
    <div class="today-summary__stats">
        <span>{availableLabel}</span>
        <strong>{summary.availableCount}</strong>
    </div>
    {#if summary.nextTask && onNext}
    <button class="today-summary__next" type="button" on:click={() => summary.nextTask && onNext?.(summary.nextTask)}>
        <span>
            <small>{nextLabel}</small>
            <strong>{summary.nextTask.name}</strong>
        </span>
        <span>{nextActionLabel}</span>
    </button>
    {/if}
    {/if}
    <RewardGoalProgress item={rewardGoal} {balance} label={goalLabel} readyLabel={goalReadyLabel}
        missingLabel={goalMissingLabel} emptyLabel={goalEmptyLabel} staleLabel={goalStaleLabel}
        stale={rewardGoalSelected && !rewardGoal} {formatNumber} compact />
</section>

<style>
    .today-summary {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        gap: 0.8rem;
        align-items: center;
        margin: 0 0 1rem;
        padding: 1rem;
        border: 1px solid color-mix(in oklch, var(--color-primary) 22%, transparent);
        border-radius: var(--radius-lg);
        background: linear-gradient(135deg, color-mix(in oklch, var(--color-primary) 10%, white), color-mix(in oklch, var(--color-secondary) 10%, white));
        box-shadow: var(--shadow-warm);
    }

    .today-summary__eyebrow,
    .today-summary__progress,
    .today-summary__stats,
    .today-summary__next small,
    .today-summary__next strong {
        margin: 0;
    }

    .today-summary__eyebrow,
    .today-summary__next small {
        color: var(--color-text-muted);
        font-size: var(--text-xs);
        font-weight: 800;
        text-transform: uppercase;
        letter-spacing: 0.06em;
    }

    .today-summary__progress {
        margin-top: 0.2rem;
        color: var(--color-text-high-contrast);
        font-size: var(--text-lg);
        font-weight: 800;
    }

    .today-summary__track {
        height: 0.65rem;
        margin-top: 0.6rem;
        overflow: hidden;
        border-radius: 999px;
        background: var(--catalog-progress-track);
    }

    .today-summary__fill {
        display: block;
        height: 100%;
        border-radius: inherit;
        background: var(--catalog-progress-fill);
        transition: width var(--transition-normal);
    }

    .today-summary__stats {
        display: grid;
        gap: 0.15rem;
        min-width: 5.5rem;
        padding: 0.65rem 0.8rem;
        border-radius: var(--radius-md);
        background: rgba(255, 255, 255, 0.58);
        color: var(--color-text-muted);
        font-size: var(--text-xs);
        text-align: center;
    }

    .today-summary__stats strong {
        color: var(--color-text-high-contrast);
        font-size: var(--text-xl);
    }

    .today-summary__next {
        display: flex;
        grid-column: 1 / -1;
        align-items: center;
        justify-content: space-between;
        gap: 0.8rem;
        min-width: 0;
        min-height: var(--catalog-control-height);
        padding: 0.7rem 0.9rem;
        border: 1px solid var(--color-border-strong);
        border-radius: var(--radius-md);
        background: var(--color-bg-card);
        color: var(--color-text-high-contrast);
        font: inherit;
        text-align: left;
        cursor: pointer;
    }

    .today-summary__next span:first-child {
        display: grid;
        min-width: 0;
        gap: 0.15rem;
    }

    .today-summary__next strong {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .today-summary__next > span:last-child {
        flex: 0 0 auto;
        color: var(--color-primary-dark);
        font-weight: 800;
    }

    .today-summary__next:focus-visible {
        outline: 3px solid var(--ds-focus-ring, rgba(59, 130, 246, 0.6));
        outline-offset: 2px;
    }

    @media (max-width: 520px) {
        .today-summary {
            grid-template-columns: minmax(0, 1fr);
        }

        .today-summary__stats {
            grid-template-columns: auto 1fr;
            align-items: center;
            text-align: left;
        }

        .today-summary__stats strong {
            justify-self: end;
        }
    }

    @media (prefers-reduced-motion: reduce) {
        .today-summary__fill {
            transition: none;
        }
    }
</style>
