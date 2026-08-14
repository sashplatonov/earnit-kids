<script lang="ts">
    import TelegramCoin from './TelegramCoin.svelte';
    export let nickname: string | null = null;
    export let balance = 0;
    export let headingId: string | undefined = undefined;
</script>

<header class="balance-header">
    <div><h1 id={headingId}>Hi{nickname ? `, ${nickname}` : ''}!</h1></div>
    <strong class="balance-pill" aria-label={`Balance: ${balance} coins`}><TelegramCoin size={16} label="Coins" />{balance}</strong>
</header>

<style>
    .balance-header { display:flex; align-items:center; justify-content:space-between; gap:1rem; margin-bottom:1rem; }
    h1 { margin:.2rem 0 0; color:#18243d; font-size:clamp(1.35rem, 6vw, 2rem); }
    .balance-pill { display:inline-flex; align-items:center; gap:.35rem; padding:.5rem .7rem; border-radius:999px; background:#fff4c2; color:#573d00; font-weight:700; white-space:nowrap; }
</style>
