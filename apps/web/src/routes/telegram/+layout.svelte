<script lang="ts">
    import '../../app.css';

    interface Props {
        children?: import('svelte').Snippet;
    }

    let { children }: Props = $props();
</script>

<svelte:head>
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
</svelte:head>

<div class="telegram-shell">
    {@render children?.()}
</div>

<style>
    :global(body:has(.telegram-shell)) {
        padding-top: 0 !important;
        background: #f6f7fb;
    }

    .telegram-shell {
        min-height: 100dvh;
        padding: env(safe-area-inset-top) 0 env(safe-area-inset-bottom);
        background: var(--color-background, #f7f8fb);
    }
</style>
