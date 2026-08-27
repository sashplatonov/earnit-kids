<script lang="ts">
    import { telegramUi } from './telegramUi';

    interface Props {
        label?: string | undefined;
        children?: import('svelte').Snippet;
    }

    let { label = undefined, children }: Props = $props();
</script>

<div class="list-surface" role="list" aria-label={label} style={`--telegram-border:${telegramUi.colors.border};--telegram-surface:${telegramUi.colors.surface};--telegram-divider:${telegramUi.colors.divider};`}>
    {@render children?.()}
</div>

<style>
    .list-surface { width:100%; box-sizing:border-box; overflow:hidden; border:1px solid var(--telegram-border); border-radius:.9rem; background:var(--telegram-surface); padding:0 .6rem; }
</style>
