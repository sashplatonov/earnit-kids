<script lang="ts">
    import TelegramIcon from '../TelegramIcon.svelte';
    import WorkspaceSessionActions from '$lib/features/workspace/WorkspaceSessionActions.svelte';
    import type { TelegramTab } from './telegramTabBar';
    interface Props {
        tabs?: readonly TelegramTab[];
        selected?: string;
        idPrefix?: string;
        ariaLabel?: string;
        onSelect?: (id: string) => void;
        fixedOnMobile?: boolean;
        showSessionActions?: boolean;
    }

    let {
        tabs = [],
        selected = '',
        idPrefix = '',
        ariaLabel = '',
        onSelect = () => {},
        fixedOnMobile = true,
        showSessionActions = false
    }: Props = $props();

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

<nav class="tabs-navigation" class:has-session-actions={showSessionActions} aria-label={showSessionActions ? ariaLabel : undefined}>
    <div class:tabs--inline={!fixedOnMobile} class:tabs--fixed={fixedOnMobile} class="tabs" style={`--telegram-tab-count:${tabs.length};`} aria-label={ariaLabel} role="tablist" tabindex="-1" onkeydown={handleKeydown}>
        {#each tabs as tab (tab.id)}
            <button aria-controls={`${idPrefix}-panel-${tab.id}`} aria-selected={selected === tab.id} class:active={selected === tab.id} id={`${idPrefix}-tab-${tab.id}`} role="tab" tabindex={selected === tab.id ? 0 : -1} type="button" onclick={() => onSelect(tab.id)}>
                <TelegramIcon name={tab.icon} size={20} label={tab.label} />
                <span>{tab.label}{tab.count ? ` (${tab.count})` : ''}</span>
            </button>
        {/each}
    </div>
    {#if showSessionActions}<WorkspaceSessionActions inline />{/if}
</nav>

<style>
    .tabs-navigation { display:contents; }
    .tabs-navigation.has-session-actions { display:grid; grid-template-columns:minmax(0, 1fr) auto; align-items:center; gap:.5rem; margin-bottom:.75rem; min-width:0; }
    .tabs-navigation .tabs { margin-bottom:0; }
    .tabs-navigation :global(.session-actions-inline) { flex:0 0 auto; }
    .tabs { display:grid; grid-template-columns:repeat(var(--telegram-tab-count), minmax(0, 1fr)); gap:.25rem; min-width:0; flex-shrink:0; margin-bottom:.75rem; }
    .tabs button { min-width:0; min-height:2.75rem; border:1px solid #dfe4ee; border-radius:.75rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; display:inline-flex; align-items:center; justify-content:center; gap:.2rem; padding-inline:.25rem; white-space:nowrap; }
    .tabs button span { min-width:0; overflow:hidden; text-overflow:ellipsis; }
    .tabs button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .tabs button.active { border-color:#b9c0ff; background:#fff; color:#2854ba; font-weight:750; }
    @media (max-width:700px) {
        .tabs-navigation.has-session-actions { display:contents; }
        .tabs--inline { position:static; z-index:auto; }
        .tabs--inline { margin-bottom:.25rem; }
        .tabs--fixed { position:fixed; z-index:20; right:0; bottom:0; left:0; gap:0; margin:0; padding:.3rem .35rem calc(.3rem + env(safe-area-inset-bottom)); border-top:1px solid #dfe4ee; background:rgb(255 255 255 / 96%); box-shadow:0 -8px 24px rgb(24 36 61 / 8%); }
        .tabs--fixed button { min-height:3rem; flex-direction:column; gap:.2rem; border:0; border-radius:.65rem; font-size:.7rem; }
        .tabs-navigation :global(.session-actions-inline) { display:none; }
    }
    @media (max-width:340px) { .tabs--fixed button { font-size:.61rem; } }
</style>
