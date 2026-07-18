<script lang="ts">
    export let id = '';
    export let groups: string[] = [];
    export let selected = '';
    export let allLabel = '';
    export let ariaLabel = '';
    export let onSelect: (group: string) => void = () => undefined;
</script>

{#if groups.length > 1}
<div class="catalog-group-nav" {id} role="group" aria-label={ariaLabel}>
    <div class="catalog-group-nav__scroll">
        <button type="button" class:catalog-group-nav__tab--active={selected === ''} class="catalog-group-nav__tab"
            aria-pressed={selected === ''} on:click={() => onSelect('')}>
            {allLabel}
        </button>
        {#each groups as group (group)}
        <button type="button" class:catalog-group-nav__tab--active={selected === group} class="catalog-group-nav__tab"
            aria-pressed={selected === group} on:click={() => onSelect(group)}>
            {group}
        </button>
        {/each}
    </div>
</div>
{/if}

<style>
    .catalog-group-nav {
        position: relative;
        margin: 0 0 var(--space-4);
        min-width: 0;
    }

    .catalog-group-nav::after {
        position: absolute;
        top: 0;
        right: 0;
        width: 2.25rem;
        height: 100%;
        pointer-events: none;
        background: linear-gradient(90deg, transparent, var(--color-bg));
        content: '';
    }

    .catalog-group-nav__scroll {
        display: flex;
        gap: var(--catalog-chip-gap);
        overflow-x: auto;
        padding: 0.15rem 2rem 0.4rem 0.1rem;
        scrollbar-width: thin;
        scroll-snap-type: x proximity;
        overscroll-behavior-inline: contain;
    }

    .catalog-group-nav__tab {
        display: inline-flex;
        flex: 0 0 auto;
        align-items: center;
        max-width: min(18rem, 78vw);
        min-height: var(--catalog-control-height);
        padding: 0.55rem 0.95rem;
        overflow: hidden;
        border: 1px solid var(--catalog-surface-border);
        border-radius: 999px;
        background: var(--color-bg-card);
        color: var(--color-text-muted);
        font: inherit;
        font-size: var(--text-sm);
        font-weight: 750;
        line-height: 1.2;
        text-overflow: ellipsis;
        white-space: nowrap;
        cursor: pointer;
        scroll-snap-align: start;
        touch-action: manipulation;
    }

    .catalog-group-nav__tab:hover {
        border-color: var(--color-border-strong);
        color: var(--color-text-high-contrast);
    }

    .catalog-group-nav__tab--active {
        border-color: color-mix(in oklch, var(--color-primary) 45%, transparent);
        background: var(--catalog-selected-surface);
        color: var(--color-primary-dark);
        box-shadow: inset 0 0 0 1px color-mix(in oklch, var(--color-primary) 12%, transparent);
    }

    .catalog-group-nav__tab:focus-visible {
        outline: 3px solid var(--ds-focus-ring, rgba(59, 130, 246, 0.6));
        outline-offset: 2px;
    }
</style>
