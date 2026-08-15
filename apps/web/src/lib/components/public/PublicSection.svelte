<script lang="ts">
    export let muted = false;
    export let ariaLabel: string | undefined = undefined;
</script>

<section class="public-section" class:public-section--muted={muted} aria-label={ariaLabel}>
    <div class="public-section-inner">
        <slot />
    </div>
</section>

<style>
    .public-section {
        padding: 2.25rem 0;
    }

    .public-section--muted {
        background: #eef2f7;
        border-top: 1px solid #e2e7ee;
        border-bottom: 1px solid #e2e7ee;
    }

    .public-section-inner {
        width: min(calc(100% - 2rem), 70rem);
        margin: 0 auto;
    }

    @media (max-width: 640px) {
        .public-section {
            padding: 1.6875rem 0;
        }
    }
</style>