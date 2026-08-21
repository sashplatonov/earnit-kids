<script lang="ts">
    import { telegramUi } from './telegramUi';

    export let interactive = false;
    export let archived = false;
    export let compact = false;
    export let hasSelection = false;
    export let hasTrailingActions = false;
</script>

<div class:archived class:compact class:has-selection={hasSelection} class:has-trailing-actions={hasTrailingActions} class="entity-row row" role="listitem" style={`--telegram-divider:${telegramUi.colors.divider};--telegram-focus:${telegramUi.colors.focus};`}>
    {#if $$slots.selection}<div class="entity-selection"><slot name="selection" /></div>{/if}
    <div class="entity-icon"><slot name="icon" /></div>
    <div class="entity-content">
        <div class="entity-heading">
            <div class="entity-title"><slot name="title" /></div>
            <div class="entity-trailing"><slot name="trailing" /></div>
        </div>
        <div class="entity-meta"><slot name="metadata" /></div>
        <div class="entity-actions"><slot name="actions" /></div>
    </div>
    {#if interactive}<div class="entity-interactive"><slot name="interactive" /></div>{/if}
</div>

<style>
    .entity-row { display:grid; grid-template-columns:1.75rem minmax(0,1fr) auto; align-items:center; gap:.35rem; width:100%; box-sizing:border-box; min-width:0; min-height:2.5rem; padding:.2rem 0; border-bottom:1px solid var(--telegram-divider); background:transparent; }
    .entity-row.has-selection { grid-template-columns:2.75rem 1.75rem minmax(0,1fr) auto; }
    .entity-row:last-child { border-bottom:0; }
    .entity-icon { display:grid; place-items:center; width:1.75rem; height:1.75rem; flex:0 0 auto; margin-top:.2rem; border-radius:.5rem; background:#eef0ff; color:#5b63e9; }
    .entity-selection { display:flex; align-items:center; justify-content:center; width:2.75rem; min-width:0; min-height:2.75rem; }
    .entity-content { display:flex; min-width:0; flex-direction:column; gap:0; }
    .entity-heading { display:flex; align-items:flex-start; gap:.35rem; min-width:0; }
    .entity-title { display:flex; flex:1; min-width:0; color:#18243d; font-size:.8rem; font-weight:600; line-height:1.2; overflow:hidden; overflow-wrap:anywhere; }
    .entity-title :global(h3), .entity-title :global(p) { margin:0; }
    .entity-trailing { display:flex; flex:0 0 auto; min-width:0; flex-direction:column; align-items:flex-end; gap:.15rem; }
    .entity-meta { display:flex; min-width:0; flex-direction:column; gap:0; color:#8a93a8; font-size:.7rem; line-height:1.2; overflow-wrap:anywhere; }
    .compact { padding-top:0; padding-bottom:0; }
    .compact .entity-meta { line-height:1.1; }
    .entity-actions { display:flex; align-items:center; justify-content:space-between; gap:.5rem; min-width:0; }
    .entity-actions:empty, .entity-meta:empty, .entity-trailing:empty { display:none; }
    .has-trailing-actions .entity-meta { padding-right:7rem; }
    .entity-interactive { display:flex; min-width:0; flex:0 0 auto; align-items:center; gap:.15rem; }
    .archived { opacity:.6; }
    :global(.entity-row .row-main) { display:flex; flex-direction:column; align-items:flex-start; width:100%; min-width:0; box-sizing:border-box; padding:0; border:0; background:transparent; color:inherit; text-align:left; cursor:pointer; }
    :global(.entity-row .entity-title .title) { display:-webkit-box; overflow:hidden; min-width:0; overflow-wrap:anywhere; -webkit-box-orient:vertical; -webkit-line-clamp:2; line-clamp:2; color:#18243d; font-size:.8rem; font-weight:600; line-height:1.2; }
    :global(.entity-row .entity-title h3) { display:-webkit-box; overflow:hidden; min-width:0; overflow-wrap:anywhere; -webkit-box-orient:vertical; -webkit-line-clamp:2; line-clamp:2; color:#18243d; font-size:.8rem; font-weight:700; line-height:1.2; }
    :global(.entity-row .entity-meta .meta) { display:flex; align-items:center; gap:.3rem; min-width:0; margin-top:.05rem; color:#66718a; font-size:.7rem; }
    :global(.entity-row .entity-meta .meta--last) { color:#8a93a8; font-size:.7rem; }
    :global(.entity-row .row-metadata) { display:flex; flex-direction:column; gap:0; width:100%; min-width:0; margin-top:.05rem; color:#8a93a8; font-size:.7rem; line-height:1.2; }
    :global(.entity-row .row-metadata .meta) { display:flex; align-items:center; gap:.3rem; min-width:0; color:#66718a; font-size:.7rem; }
    :global(.entity-row .row-metadata .meta--last) { color:#8a93a8; }
    :global(.entity-row.compact .row-metadata) { margin-top:0; line-height:1.1; }
    :global(.entity-row.compact .entity-meta .meta) { margin-top:0; }
    :global(.entity-row .row-action) { display:grid; place-items:center; width:2.75rem; height:2.75rem; min-width:2.75rem; min-height:2.75rem; box-sizing:border-box; border:0; border-radius:.5rem; background:transparent; cursor:pointer; }
    :global(.entity-row .row-action:disabled) { opacity:.5; cursor:not-allowed; }
    :global(.entity-row button:focus-visible), :global(.entity-row a:focus-visible) { outline:3px solid var(--telegram-focus); outline-offset:2px; }
    @media (max-width:370px) {
        .entity-row { grid-template-columns:1.75rem minmax(0,1fr) auto; gap:.3rem; }
        .entity-row.has-selection { grid-template-columns:2.75rem 1.75rem minmax(0,1fr) auto; }
        .entity-interactive { gap:0; }
    }
</style>
