<script lang="ts">
    import TelegramIcon from './TelegramIcon.svelte';
    import type { TelegramIconName } from './telegramIconMap';

    export let icon: TelegramIconName;
    export let label: string;
    export let type: 'button' | 'submit' = 'button';
    export let disabled = false;
</script>

<button {type} {disabled} aria-label={label} title={label} on:click>
    <TelegramIcon name={icon} size={20} label={label} />
</button>

<style>
    button { display:inline-grid; place-items:center; width:44px; height:44px; padding:0; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; cursor:pointer; }
    button:disabled { cursor:wait; opacity:.6; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
