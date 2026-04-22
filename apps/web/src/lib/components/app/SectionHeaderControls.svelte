<script lang="ts">
    import { createEventDispatcher } from 'svelte';

    type CardViewMode = 'grid' | 'list';

    export let isAdmin = false;
    export let addLabel = '';
    export let addId = '';
    export let viewMode: CardViewMode = 'grid';
    export let viewAriaLabel = '';
    export let gridLabel = '';
    export let listLabel = '';
    export let orderLabel = '';
    export let hasGroups = false;
    export let isEditingGroupOrder = false;
    export let isSavingGroupOrder = false;

    const dispatch = createEventDispatcher<{
        add: void;
        editOrder: void;
        viewMode: CardViewMode;
    }>();

    function setViewMode(nextMode: CardViewMode) {
        dispatch('viewMode', nextMode);
    }
</script>

<div class="section-controls" class:section-controls--without-add={!isAdmin}>
    {#if isAdmin}
    <button class="btn btn--add section-controls__add" id={addId} type="button" on:click={() => dispatch('add')}>
        {addLabel}
    </button>
    {/if}

    <div class="section-controls__tools">
        <div class="section-controls__segmented" role="group" aria-label={viewAriaLabel}>
            <button
                type="button"
                class="section-controls__icon-btn"
                class:section-controls__icon-btn--active={viewMode === 'grid'}
                aria-label={gridLabel}
                aria-pressed={viewMode === 'grid'}
                on:click={() => setViewMode('grid')}
            >
                <svg viewBox="0 0 24 24" aria-hidden="true" class="section-controls__icon">
                    <rect x="4" y="4" width="6" height="6" rx="1.6"></rect>
                    <rect x="14" y="4" width="6" height="6" rx="1.6"></rect>
                    <rect x="4" y="14" width="6" height="6" rx="1.6"></rect>
                    <rect x="14" y="14" width="6" height="6" rx="1.6"></rect>
                </svg>
                <span class="section-controls__label">{gridLabel}</span>
            </button>
            <button
                type="button"
                class="section-controls__icon-btn"
                class:section-controls__icon-btn--active={viewMode === 'list'}
                aria-label={listLabel}
                aria-pressed={viewMode === 'list'}
                on:click={() => setViewMode('list')}
            >
                <svg viewBox="0 0 24 24" aria-hidden="true" class="section-controls__icon">
                    <path d="M6 7h12"></path>
                    <path d="M6 12h12"></path>
                    <path d="M6 17h12"></path>
                    <circle cx="3.75" cy="7" r="0.75"></circle>
                    <circle cx="3.75" cy="12" r="0.75"></circle>
                    <circle cx="3.75" cy="17" r="0.75"></circle>
                </svg>
                <span class="section-controls__label">{listLabel}</span>
            </button>
        </div>

        {#if hasGroups && !isEditingGroupOrder}
        <button
            class="section-controls__icon-btn section-controls__order-btn"
            type="button"
            aria-label={orderLabel}
            on:click={() => dispatch('editOrder')}
            disabled={isSavingGroupOrder}
        >
            <svg viewBox="0 0 24 24" aria-hidden="true" class="section-controls__icon">
                <path d="M8 6h11"></path>
                <path d="M8 12h11"></path>
                <path d="M8 18h11"></path>
                <path d="M4 5v14"></path>
                <path d="m2.75 7.25 1.25-1.5 1.25 1.5"></path>
                <path d="m2.75 16.75 1.25 1.5 1.25-1.5"></path>
            </svg>
            <span class="section-controls__label">{orderLabel}</span>
        </button>
        {/if}
    </div>
</div>

<style>
    .section-controls {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 0.75rem;
        flex-wrap: wrap;
    }

    .section-controls__tools {
        order: 1;
        display: inline-flex;
        align-items: center;
        gap: 0.45rem;
    }

    .section-controls__add {
        order: 2;
    }

    .section-controls__segmented {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.25rem;
        border-radius: 999px;
        border: 1px solid rgba(120, 140, 175, 0.2);
        background: rgba(246, 248, 252, 0.94);
        box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.85);
    }

    .section-controls__icon-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 0.38rem;
        border: 0;
        min-height: 2.3rem;
        padding: 0.56rem 0.86rem;
        border-radius: 999px;
        background: transparent;
        color: rgba(54, 68, 96, 0.72);
        font: inherit;
        font-size: 0.84rem;
        font-weight: 750;
        line-height: 1;
        cursor: pointer;
        transition: background-color 140ms ease, color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
    }

    .section-controls__icon-btn:hover:not(:disabled) {
        color: #243653;
        background: rgba(255, 255, 255, 0.72);
        transform: translateY(-1px);
    }

    .section-controls__icon-btn--active {
        background: linear-gradient(135deg, rgba(87, 121, 206, 0.18), rgba(84, 179, 160, 0.2));
        color: #20304e;
        box-shadow: inset 0 0 0 1px rgba(87, 121, 206, 0.16), 0 4px 12px rgba(65, 89, 140, 0.08);
    }

    .section-controls__icon-btn:disabled {
        cursor: not-allowed;
        opacity: 0.55;
    }

    .section-controls__order-btn {
        border: 1px solid rgba(120, 140, 175, 0.2);
        background: rgba(246, 248, 252, 0.94);
        box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.85);
    }

    .section-controls__icon {
        width: 1.12rem;
        height: 1.12rem;
        flex: none;
        fill: none;
        stroke: currentColor;
        stroke-width: 1.85;
        stroke-linecap: round;
        stroke-linejoin: round;
    }

    .section-controls__icon circle,
    .section-controls__icon rect {
        fill: none;
        stroke: currentColor;
    }

    @media (max-width: 640px) {
        .section-controls {
            width: 100%;
            display: grid;
            grid-template-columns: minmax(4.25rem, auto) minmax(0, 1fr);
            align-items: center;
            gap: 0.5rem;
        }

        .section-controls__tools {
            order: 2;
            justify-self: end;
            max-width: 100%;
            gap: 0.35rem;
            min-width: 0;
        }

        .section-controls__add {
            order: 1;
            min-height: 2.55rem;
            padding-inline: 0.72rem;
            white-space: nowrap;
        }

        .section-controls--without-add {
            grid-template-columns: minmax(0, 1fr);
        }

        .section-controls--without-add .section-controls__tools {
            justify-self: start;
        }

        .section-controls__icon-btn {
            width: 2.22rem;
            height: 2.22rem;
            min-height: 2.22rem;
            padding: 0;
        }

        .section-controls__label {
            position: absolute;
            width: 1px;
            height: 1px;
            overflow: hidden;
            clip: rect(0 0 0 0);
            white-space: nowrap;
        }
    }
</style>
