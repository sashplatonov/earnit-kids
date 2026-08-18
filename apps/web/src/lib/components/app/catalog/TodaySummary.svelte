<script lang="ts">
    import type { Task } from '$lib/stores/app';
    import type { TodayTaskSummary } from '$lib/services/todayTaskViewModel';

    export let summary: TodayTaskSummary;
    export let title = '';
    export let progressLabel = '';
    export let emptyProgressLabel = '';
    export let availableLabel = '';
    export let nextLabel = '';
    export let nextActionLabel = '';
    export let onNext: ((task: Task) => void) | null = null;

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
</section>

<style>
    .today-summary {
        display: grid;
        grid-template-columns: minmax(0, 1.15fr) auto minmax(10rem, 0.85fr) minmax(13rem, 1fr);
        gap: 0.5rem;
        border: 1px solid color-mix(in oklch, var(--color-primary) 22%, transparent);
        border-radius: var(--radius-md);
        background: linear-gradient(135deg, color-mix(in oklch, var(--color-primary) 10%, white), color-mix(in oklch, var(--color-secondary) 10%, white));
        box-shadow: var(--shadow-sm);
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
        margin-top: 0.1rem;
        color: var(--color-text-high-contrast);
        font-size: var(--text-sm);
        font-weight: 800;
        line-height: 1.25;
    }

    .today-summary__track {
        height: 0.38rem;
        margin-top: 0.32rem;
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
        display: flex;
        gap: 0.4rem;
        min-width: 0;
        align-items: center;
        padding: 0.4rem 0.55rem;
        border-radius: var(--radius-md);
        background: rgba(255, 255, 255, 0.58);
        color: var(--color-text-muted);
        font-size: var(--text-xs);
        text-align: center;
    }

    .today-summary__stats strong {
        color: var(--color-text-high-contrast);
        font-size: var(--text-lg);
    }

    .today-summary__next {
        display: flex;
        grid-column: auto;
        align-items: center;
        justify-content: space-between;
        gap: 0.8rem;
        min-width: 0;
        min-height: 2.5rem;
        padding: 0.42rem 0.6rem;
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
        font-size: var(--text-xs);
    }

    .today-summary__next:focus-visible {
        outline: 3px solid var(--ds-focus-ring, rgba(59, 130, 246, 0.6));
        outline-offset: 2px;
    }

    @media (max-width: 900px) {
        .today-summary {
            grid-template-columns: minmax(0, 1fr) auto;
        }

        .today-summary__next {
            grid-column: 1;
        }
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

        .today-summary__next {
            grid-column: 1;
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
