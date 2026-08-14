<script lang="ts">
    import TelegramCoin from './TelegramCoin.svelte';
    export let nickname: string | null = null;
    export let balance = 0;
    export let headingId: string | undefined = undefined;
    export let loading = false;
</script>

<header class="balance-header">
    {#if loading}
        <h1 id={headingId} class="loading-title">Loading…</h1>
        <span class="skeleton skeleton--pill" aria-hidden="true"></span>
    {:else}
        <div><h1 id={headingId}>Hi{nickname ? `, ${nickname}` : ''}!</h1></div>
        <strong class="balance-pill" aria-label={`Balance: ${balance} coins`}><TelegramCoin size={16} label="Coins" />{balance}</strong>
    {/if}
</header>

<style>
    .balance-header { display:flex; align-items:center; justify-content:space-between; gap:1rem; margin-bottom:1rem; }
    h1 { margin:.2rem 0 0; color:#18243d; font-size:clamp(1.35rem, 6vw, 2rem); }
    .loading-title { color:#9aa3b5; font-size:clamp(1.35rem, 6vw, 2rem); }
    .balance-pill { display:inline-flex; align-items:center; gap:.35rem; padding:.5rem .7rem; border-radius:999px; background:#fff4c2; color:#573d00; font-weight:700; white-space:nowrap; }
    .skeleton { display:block; background:#e8eaf1; border-radius:.55rem; animation: skeleton-pulse 1.3s ease-in-out infinite; }
    .skeleton--pill { width:4.2rem; height:2.1rem; border-radius:999px; }
    @keyframes skeleton-pulse { 0%,100% { opacity:.55; } 50% { opacity:1; } }
    @media (prefers-reduced-motion: reduce) { .skeleton { animation:none; } }
</style>
