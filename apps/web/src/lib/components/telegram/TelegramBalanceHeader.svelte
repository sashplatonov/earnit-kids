<script lang="ts">
    export let nickname: string | null = null;
    export let balance = 0;
    export let headingId: string | undefined = undefined;
</script>

<header class="balance-header">
    <div><h1 id={headingId}>Hi{nickname ? `, ${nickname}` : ''}!</h1></div>
    <strong aria-label={`Balance: ${balance} coins`}>{balance} 🪙</strong>
</header>

<style>
    .balance-header { display:flex; align-items:center; justify-content:space-between; gap:1rem; margin-bottom:1rem; }
    h1 { margin:.2rem 0 0; color:#18243d; font-size:clamp(1.35rem, 6vw, 2rem); }
    strong { padding:.55rem .75rem; border-radius:999px; background:#fff4c2; color:#573d00; white-space:nowrap; }
</style>
