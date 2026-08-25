<script lang="ts">
    import TelegramIcon from '../TelegramIcon.svelte';
    import WorkspaceSessionActions from '$lib/features/workspace/WorkspaceSessionActions.svelte';
    import type { TelegramTab } from './telegramTabBar';
    export let tabs: readonly TelegramTab[] = [];
    export let selected = '';
    export let idPrefix = '';
    export let ariaLabel = '';
    export let onSelect: (id: string) => void = () => {};
    export let fixedOnMobile = true;
    export let showSessionActions = false;

    function handleKeydown(event: KeyboardEvent) {
        const index = tabs.findIndex((tab) => tab.id === selected);
        if (index < 0) return;
        let next: number;
        if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
        else if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
        else if (event.key === 'Home') next = 0;
        else if (event.key === 'End') next = tabs.length - 1;
        else return;
        event.preventDefault();
        const nextTab = tabs[next];
        onSelect(nextTab.id);
        document.getElementById(`${idPrefix}-tab-${nextTab.id}`)?.focus();
    }
</script>

<div class:tabs-shell={showSessionActions}>
    <div class:tabs--inline={!fixedOnMobile} class:tabs--fixed={fixedOnMobile} class="tabs" style={`--telegram-tab-count:${tabs.length};`} aria-label={ariaLabel} role="tablist" tabindex="-1" on:keydown={handleKeydown}>
        {#each tabs as tab (tab.id)}
            <button aria-controls={`${idPrefix}-panel-${tab.id}`} aria-selected={selected === tab.id} class:active={selected === tab.id} id={`${idPrefix}-tab-${tab.id}`} role="tab" tabindex={selected === tab.id ? 0 : -1} type="button" on:click={() => onSelect(tab.id)}>
                <TelegramIcon name={tab.icon} size={20} label={tab.label} />
                <span>{tab.label}{tab.count ? ` (${tab.count})` : ''}</span>
            </button>
        {/each}
    </div>
    {#if showSessionActions}<WorkspaceSessionActions inline />{/if}
</div>

<style>
    .tabs-shell { position:relative; display:flex; align-items:center; gap:.25rem; margin-bottom:.75rem; padding:.25rem; border:1px solid #dfe4ee; border-radius:2rem; background:#fff; box-shadow:0 .5rem 1.25rem rgb(24 36 61 / 6%); }
    .tabs-shell .tabs { flex:1 1 auto; margin-bottom:0; }
    .tabs-shell :global(.session-actions-inline) { flex:0 0 auto; }
    .tabs { display:grid; grid-template-columns:repeat(var(--telegram-tab-count), minmax(0, 1fr)); gap:.25rem; flex-shrink:0; margin-bottom:.75rem; }
    .tabs button { min-height:2.75rem; border:1px solid #dfe4ee; border-radius:.75rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; display:inline-flex; align-items:center; justify-content:center; gap:.2rem; padding-inline:.25rem; white-space:nowrap; }
    .tabs button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .tabs button.active { border-color:#b9c0ff; background:#fff; color:#2854ba; font-weight:750; }
    @media (max-width:700px) {
        .tabs-shell { display:block; padding:0; border:0; border-radius:0; background:transparent; box-shadow:none; }
        .tabs--inline { position:static; z-index:auto; }
        .tabs--fixed { position:fixed; z-index:20; right:0; bottom:0; left:0; gap:0; margin:0; padding:.3rem .35rem calc(.3rem + env(safe-area-inset-bottom)); border-top:1px solid #dfe4ee; background:rgb(255 255 255 / 96%); box-shadow:0 -8px 24px rgb(24 36 61 / 8%); }
        .tabs--fixed button { min-height:3rem; flex-direction:column; gap:.2rem; border:0; border-radius:.65rem; font-size:.7rem; }
        .tabs-shell :global(.session-actions-inline) { display:none; }
    }
    @media (max-width:340px) { .tabs--fixed button { font-size:.61rem; } }
</style>
