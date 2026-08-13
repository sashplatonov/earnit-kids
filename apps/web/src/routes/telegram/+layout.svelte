<script lang="ts">
    import '../../app.css';

</script>

<svelte:head>
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
</svelte:head>

<div class="telegram-shell">
    <slot />
</div>

<style>
    .telegram-shell {
        min-height: 100dvh;
        padding: env(safe-area-inset-top) 1rem env(safe-area-inset-bottom);
        background: var(--color-background, #f7f8fb);
    }
</style>
