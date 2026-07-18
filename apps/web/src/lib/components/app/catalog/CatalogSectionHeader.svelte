<script lang="ts">
    export let title = '';
    export let subtitle = '';
    export let iconClass = '';
</script>

<header class="catalog-section-header">
    <div class="catalog-section-header__copy">
        <div class="catalog-section-header__title-row">
            <span class={`gamified-icon ${iconClass}`.trim()} aria-hidden="true"></span>
            <div>
                <h2>{title}</h2>
                <p>{subtitle}</p>
            </div>
        </div>
    </div>
    <div class="catalog-section-header__controls">
        <slot />
    </div>
</header>

<style>
    .catalog-section-header {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        gap: var(--space-4);
        align-items: end;
        margin-bottom: var(--space-4);
        padding: clamp(0.9rem, 1.5vw, 1.25rem);
        border: 1px solid var(--catalog-surface-border);
        border-radius: var(--catalog-surface-radius);
        background: var(--catalog-header-surface);
        box-shadow: var(--catalog-surface-shadow);
    }

    .catalog-section-header__title-row {
        display: flex;
        min-width: 0;
        align-items: center;
        gap: 0.75rem;
    }

    .catalog-section-header__title-row > :global(.gamified-icon) {
        width: 2.65rem;
        height: 2.65rem;
        flex: 0 0 auto;
        border-radius: var(--radius-md);
        background-color: color-mix(in oklch, var(--color-primary) 9%, transparent);
    }

    h2,
    p {
        margin: 0;
    }

    h2 {
        color: var(--color-text-high-contrast);
        font-size: clamp(1.3rem, 2.5vw, 1.85rem);
        line-height: 1.1;
    }

    p {
        margin-top: 0.3rem;
        color: var(--color-text-muted);
        font-size: var(--text-sm);
        line-height: 1.35;
    }

    .catalog-section-header__controls {
        min-width: 0;
    }

    @media (max-width: 760px) {
        .catalog-section-header {
            grid-template-columns: minmax(0, 1fr);
            align-items: stretch;
        }

        .catalog-section-header__controls {
            width: 100%;
        }
    }
</style>
