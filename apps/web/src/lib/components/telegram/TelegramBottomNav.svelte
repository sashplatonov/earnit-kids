<script lang="ts">
    import TelegramIcon from './TelegramIcon.svelte';
    import type { TelegramIconName } from './telegramIconMap';
    import { createEventDispatcher } from 'svelte';

    export let items: ReadonlyArray<{ id: string; label: string; icon: TelegramIconName }> = [];
    export let active = '';
    const dispatch = createEventDispatcher<{ select: string }>();
</script>

<nav class="bottom-nav" aria-label="Telegram workspace navigation">
    {#each items as item (item.id)}
        <button type="button" class:active={item.id === active} aria-current={item.id === active ? 'page' : undefined} aria-label={item.label} on:click={() => dispatch('select', item.id)}>
            <TelegramIcon name={item.icon} size={20} label={item.label} />
            <span>{item.label}</span>
        </button>
    {/each}
</nav>

<style>
    .bottom-nav { position:fixed; z-index:20; right:0; bottom:0; left:0; display:grid; grid-auto-columns:1fr; grid-auto-flow:column; gap:.25rem; padding:.4rem .35rem calc(.4rem + env(safe-area-inset-bottom)); border-top:1px solid #dfe4ee; background:rgb(255 255 255 / 96%); box-shadow:0 -8px 24px rgb(24 36 61 / 8%); }
    button { display:flex; min-width:44px; min-height:44px; align-items:center; justify-content:center; gap:.2rem; border:0; border-radius:.65rem; background:transparent; color:#33415f; font:inherit; font-size:.7rem; cursor:pointer; }
    button.active { background:#edf2ff; color:#2854ba; font-weight:700; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
