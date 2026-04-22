<script lang="ts">
    type CardHeaderChip = {
        label: string;
        className?: string;
    };

    export let title = '';
    export let amount: string | number = '';
    export let amountClass = '';
    export let compactChips: CardHeaderChip[] = [];
</script>

<div class="card__header">
    <div class="card__header-main">
        <h3 class="card__title">{title}</h3>
        {#if compactChips.length > 0}
        <div class="card__compact-meta">
            {#each compactChips as chip (`${chip.label}:${chip.className ?? ''}`)}
            <span class={`card__compact-chip ${chip.className ?? ''}`.trim()}>{chip.label}</span>
            {/each}
        </div>
        {/if}
    </div>
    <div class={`card__coins ${amountClass}`.trim()}>
        <span class="gamified-icon icon-coin" aria-hidden="true"></span>
        <span>{amount}</span>
    </div>
</div>

<style>
    .card__header-main {
        min-width: 0;
        flex: 1 1 auto;
    }

    .card__compact-meta {
        display: none;
    }

    :global(.task-card--list) .card__header,
    :global(.shop-card--list) .card__header {
        min-height: 0;
        align-items: flex-start;
        gap: 0.65rem;
    }

    :global(.task-card--list) .card__title,
    :global(.shop-card--list) .card__title {
        min-height: 0;
        display: block;
        overflow: visible;
        white-space: normal;
        text-overflow: clip;
        line-height: 1.2;
        word-break: break-word;
    }

    :global(.task-card--list) .card__compact-meta,
    :global(.shop-card--list) .card__compact-meta {
        display: flex;
        flex-wrap: wrap;
        gap: 0.28rem;
        margin-top: 0.3rem;
    }

    :global(.task-card--list) .card__compact-chip,
    :global(.shop-card--list) .card__compact-chip {
        display: inline-flex;
        align-items: center;
        min-width: 0;
        max-width: 100%;
        padding: 0.14rem 0.45rem;
        border-radius: 999px;
        background: rgba(116, 134, 170, 0.12);
        color: #44526b;
        font-size: 0.68rem;
        font-weight: 700;
        line-height: 1.15;
    }

    :global(.task-card--list) .card__compact-chip--group,
    :global(.shop-card--list) .card__compact-chip--group {
        background: rgba(87, 121, 206, 0.16);
        color: #20304e;
    }

    :global(.shop-card--list) .card__compact-chip--status-available {
        background: rgba(54, 166, 110, 0.16);
        color: #1f6a45;
    }

    :global(.shop-card--list) .card__compact-chip--status-locked {
        background: rgba(214, 110, 89, 0.14);
        color: #8a3f2f;
    }

    @media (max-width: 640px) {
        :global(.shop-card--list) .card__header {
            display: grid;
            grid-template-columns: minmax(0, 1fr) auto;
            align-items: start;
        }

        :global(.shop-card--list) .card__header-main {
            min-width: 0;
        }

        :global(.shop-card--list) .card__title {
            font-size: 0.95rem;
            min-height: 0;
        }

        :global(.shop-card--list) .card__compact-meta {
            gap: 0.22rem;
            margin-top: 0.24rem;
        }
    }
</style>
