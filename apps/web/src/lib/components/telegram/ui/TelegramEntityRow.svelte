<script lang="ts">
    import { telegramUi } from './telegramUi';

    export let interactive = false;
</script>

<div class="entity-row row" role="listitem" style={`--telegram-divider:${telegramUi.colors.divider};--telegram-focus:${telegramUi.colors.focus};`}>
    <div class="entity-icon"><slot name="icon" /></div>
    <div class="entity-content">
        <div class="entity-heading">
            <div class="entity-title"><slot name="title" /></div>
            <div class="entity-trailing"><slot name="trailing" /></div>
        </div>
        <div class="entity-meta"><slot name="metadata" /></div>
        <div class="entity-actions"><slot name="actions" /></div>
    </div>
    {#if interactive}<slot name="interactive" />{/if}
</div>

<style>
    .entity-row { display:flex; align-items:stretch; gap:.6rem; width:100%; box-sizing:border-box; min-width:0; min-height:4rem; padding:.5rem 0; border-bottom:1px solid var(--telegram-divider); background:transparent; }
    .entity-row:last-child { border-bottom:0; }
    .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; margin-top:.2rem; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .entity-content { display:flex; flex:1; min-width:0; flex-direction:column; gap:.25rem; }
    .entity-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:.5rem; min-width:0; }
    .entity-title { flex:1; min-width:0; color:#18243d; font-size:.95rem; font-weight:600; line-height:1.3; overflow:hidden; overflow-wrap:anywhere; display:-webkit-box; -webkit-line-clamp:2; line-clamp:2; -webkit-box-orient:vertical; }
    .entity-title :global(h3), .entity-title :global(p) { margin:0; }
    .entity-trailing { display:flex; flex:0 0 auto; min-width:0; flex-direction:column; align-items:flex-end; gap:.15rem; }
    .entity-meta { min-width:0; color:#8a93a8; font-size:.75rem; line-height:1.3; overflow-wrap:anywhere; }
    .entity-actions { display:flex; align-items:center; justify-content:space-between; gap:.5rem; min-width:0; }
    .entity-actions:empty, .entity-meta:empty, .entity-trailing:empty { display:none; }
    :global(.entity-row button:focus-visible), :global(.entity-row a:focus-visible) { outline:3px solid var(--telegram-focus); outline-offset:2px; }
    @media (max-width:370px) {
        .entity-heading { flex-direction:column; }
        .entity-trailing { width:100%; flex-direction:row; align-items:flex-start; flex-wrap:wrap; }
    }
</style>
