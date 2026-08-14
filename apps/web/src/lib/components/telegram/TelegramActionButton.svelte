<script lang="ts">
    import TelegramIcon from './TelegramIcon.svelte';
    import type { TelegramIconName } from './telegramIconMap';

    export let icon: TelegramIconName;
    export let label: string;
    export let type: 'button' | 'submit' = 'button';
    export let disabled = false;
    export let busy = false;
</script>

<button {type} {disabled} aria-busy={busy} aria-label={label} on:click>
    <TelegramIcon name={icon} size={20} label={label} />
    <span>{label}</span>
</button>

<style>
    button { display:inline-flex; align-items:center; justify-content:center; gap:.45rem; min-width:44px; min-height:44px; padding:.6rem .85rem; border:1px solid #3867d6; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; cursor:pointer; }
    button:disabled { cursor:wait; opacity:.6; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
