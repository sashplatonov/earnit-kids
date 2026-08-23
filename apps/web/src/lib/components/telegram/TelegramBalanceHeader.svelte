<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import TelegramCoin from './TelegramCoin.svelte';

    const i18n = useI18n();

    export let nickname: string | null = null;
    export let balance = 0;
    export let headingId: string | undefined = undefined;
    export let loading = false;
</script>

<header class="balance-header">
    {#if loading}
        <h1 id={headingId} class="loading-title">{$i18n.t('app.telegram.balanceHeader.loading')}</h1>
        <span class="skeleton skeleton--pill" aria-hidden="true"></span>
    {:else}
        <div><h1 id={headingId}>{nickname ? $i18n.t('app.telegram.balanceHeader.helloName', { nickname }) : $i18n.t('app.telegram.balanceHeader.hello')}</h1></div>
        <strong class="balance-pill" aria-label={$i18n.t('app.telegram.balanceHeader.balance', { balance })}><TelegramCoin size={16} label={$i18n.t('app.telegram.balanceHeader.coins')} />{balance}</strong>
    {/if}
</header>

<style>
    .balance-header { display:flex; align-items:center; justify-content:space-between; gap:1rem; min-height:3.4rem; margin-bottom:1rem; }
    h1 { margin:.2rem 0 0; color:#18243d; font-size:clamp(1.35rem, 6vw, 2rem); line-height:1.25; }
    .loading-title { color:#9aa3b5; font-size:clamp(1.35rem, 6vw, 2rem); line-height:1.25; }
    .balance-pill { display:inline-flex; align-items:center; gap:.35rem; box-sizing:border-box; height:2.25rem; padding:.5rem .7rem; border-radius:999px; background:#eef4ff; color:#2854ba; font-weight:700; white-space:nowrap; }
    .skeleton { display:block; background:#e8eaf1; border-radius:.55rem; animation: skeleton-pulse 1.3s ease-in-out infinite; }
    .skeleton--pill { width:4.2rem; height:2.25rem; border-radius:999px; }
    @keyframes skeleton-pulse { 0%,100% { opacity:.55; } 50% { opacity:1; } }
    @media (prefers-reduced-motion: reduce) { .skeleton { animation:none; } }
</style>
