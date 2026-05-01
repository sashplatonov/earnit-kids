<script lang="ts">
    type CardHeaderChip = {
        label: string;
        className?: string;
    };

    export let title = '';
	// Optional compact inline note/suffix shown after the title (used in requests row view).
	export let titleSuffix = '';
    export let amount: string | number = '';
    export let amountClass = '';
    export let amountNote = '';
    export let compactChips: CardHeaderChip[] = [];
</script>

<div class="card__header">
    <div class="card__header-main">
		<h3 class="card__title">
			<span class="card__title-text">{title}</span>
			{#if titleSuffix}
				<span class="card__title-suffix"> — {titleSuffix}</span>
			{/if}
		</h3>
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
    {#if amountNote}
    <span class="card__amount-note">{amountNote}</span>
    {/if}
</div>

<style>
    .card__header-main {
        min-width: 0;
        flex: 1 1 auto;
    }

    .card__compact-meta {
        display: none;
    }

    .card__amount-note {
        display: none;
    }

	.card__title-suffix {
		opacity: 0.78;
		font-weight: 600;
		font-size: 0.92em;
	}

    :global(.task-card--list) .card__header,
    :global(.shop-card--list) .card__header,
    :global(.request-card--list) .card__header,
    :global(.history-transaction-card--list) .card__header {
        min-height: 0;
        align-items: flex-start;
        gap: 0.65rem;
    }

    :global(.task-card--list) .card__title,
    :global(.shop-card--list) .card__title,
    :global(.request-card--list) .card__title,
    :global(.history-transaction-card--list) .card__title {
        min-height: 0;
        display: block;
        overflow: visible;
        white-space: normal;
        text-overflow: clip;
        line-height: 1.2;
        word-break: break-word;
    }

    :global(.task-card--list) .card__compact-meta,
    :global(.shop-card--list) .card__compact-meta,
    :global(.request-card--list) .card__compact-meta,
    :global(.history-transaction-card--list) .card__compact-meta {
        display: flex;
        flex-wrap: wrap;
        gap: 0.28rem;
        margin-top: 0.3rem;
    }

    :global(.task-card--list) .card__compact-chip,
    :global(.shop-card--list) .card__compact-chip,
    :global(.request-card--list) .card__compact-chip,
    :global(.history-transaction-card--list) .card__compact-chip {
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

    /* Highlighted child name chip for requests list (row) view */
    :global(.request-card--list) .card__compact-chip--child {
        background: linear-gradient(135deg, rgba(99, 102, 241, 0.22), rgba(168, 85, 247, 0.22));
        color: #2d1b5a;
        border: 1px solid rgba(99, 102, 241, 0.18);
        font-weight: 900;
    }

    :global(.task-card--list) .card__compact-chip--group,
    :global(.shop-card--list) .card__compact-chip--group,
    :global(.request-card--list) .card__compact-chip--group,
    :global(.history-transaction-card--list) .card__compact-chip--group {
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
        :global(.task-card--list) .card__header,
        :global(.shop-card--list) .card__header,
        :global(.request-card--list) .card__header,
        :global(.history-transaction-card--list) .card__header {
            display: grid;
            grid-template-columns: minmax(0, 1fr) auto;
            grid-template-areas:
                'title coins'
                'meta amount-note';
            align-items: center;
            min-width: 0;
            margin-bottom: 0;
            gap: 0.22rem 0.4rem;
        }

        :global(.task-card--list) .card__header-main,
        :global(.shop-card--list) .card__header-main,
        :global(.request-card--list) .card__header-main,
        :global(.history-transaction-card--list) .card__header-main {
            display: contents;
        }

        :global(.task-card--list) .card__title,
        :global(.shop-card--list) .card__title,
        :global(.request-card--list) .card__title,
        :global(.history-transaction-card--list) .card__title {
            grid-area: title;
            min-width: 0;
            min-height: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            font-size: 0.84rem;
            line-height: 1.12;
            word-break: normal;
        }

		/* Keep the suffix on the same line, but make it ellipsis-friendly. */
		:global(.request-card--list) .card__title-text,
		:global(.request-card--list) .card__title-suffix {
			white-space: nowrap;
		}
		:global(.request-card--list) .card__title-suffix {
			overflow: hidden;
			text-overflow: ellipsis;
		}

        :global(.task-card--list) .card__compact-meta,
        :global(.shop-card--list) .card__compact-meta,
        :global(.request-card--list) .card__compact-meta,
        :global(.history-transaction-card--list) .card__compact-meta {
            grid-area: meta;
            min-width: 0;
            flex-wrap: nowrap;
            overflow: hidden;
            gap: 0.22rem;
            margin-top: 0;
        }

        :global(.task-card--list) .card__compact-chip,
        :global(.shop-card--list) .card__compact-chip,
        :global(.request-card--list) .card__compact-chip,
        :global(.history-transaction-card--list) .card__compact-chip {
            flex: 0 1 auto;
            max-width: 5.8rem;
            overflow: hidden;
            padding: 0.12rem 0.34rem;
            font-size: 0.62rem;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        :global(.task-card--list) .card__compact-chip:not(.card__compact-chip--group):not(:nth-child(2)),
        :global(.shop-card--list) .card__compact-chip:not(.card__compact-chip--group):not(:nth-child(2)),
        :global(.request-card--list) .card__compact-chip:not(.card__compact-chip--group):not(:nth-child(2)),
        :global(.history-transaction-card--list) .card__compact-chip:not(.card__compact-chip--group):not(:nth-child(2)) {
            display: none;
        }

        :global(.task-card--list) .card__compact-chip--group,
        :global(.shop-card--list) .card__compact-chip--group,
        :global(.request-card--list) .card__compact-chip--group,
        :global(.history-transaction-card--list) .card__compact-chip--group {
            max-width: 4.8rem;
        }

        :global(.task-card--list) .card__coins,
        :global(.shop-card--list) .card__coins,
        :global(.request-card--list) .card__coins,
        :global(.history-transaction-card--list) .card__coins {
            grid-area: coins;
            flex: 0 0 auto;
            padding: 0.16rem 0.34rem;
            font-size: 0.68rem;
            line-height: 1;
            white-space: nowrap;
        }

        :global(.task-card--list) .card__amount-note,
        :global(.shop-card--list) .card__amount-note,
        :global(.request-card--list) .card__amount-note,
        :global(.history-transaction-card--list) .card__amount-note {
            grid-area: amount-note;
            justify-self: end;
            display: inline-flex;
            align-items: center;
            max-width: 4.8rem;
            overflow: hidden;
            padding: 0.12rem 0.34rem;
            border-radius: 999px;
            background: rgba(245, 158, 11, 0.12);
            color: #8a6118;
            font-size: 0.62rem;
            font-weight: 800;
            line-height: 1.05;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        :global(.task-card--list) .card__coins .gamified-icon,
        :global(.shop-card--list) .card__coins .gamified-icon,
        :global(.request-card--list) .card__coins .gamified-icon,
        :global(.history-transaction-card--list) .card__coins .gamified-icon {
            width: 0.78rem;
            height: 0.78rem;
        }
    }
</style>
