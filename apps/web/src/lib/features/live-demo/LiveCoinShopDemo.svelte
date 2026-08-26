<script lang="ts">
    import { onMount } from 'svelte';
    import TelegramChildRewards from '$lib/components/telegram/TelegramChildRewards.svelte';
    import { useI18n } from '$lib/i18n/context';
    import { provideRewardRequestActions } from '$lib/telegram/services/rewardRequestActions';
    import { createLiveCoinShopDemoSession } from './liveCoinShopDemoSession';

    const i18n = useI18n();
    const session = createLiveCoinShopDemoSession();
    // EXPLAIN: Context must be installed during component initialisation so
    // TelegramChildRewards receives the demo action before it is created.
    provideRewardRequestActions(session.actions);

    let mounted = false;
    let announcement = '';

    onMount(() => {
        session.initialize();
        mounted = true;

        return () => {
            session.teardown();
        };
    });

    function resetDemo(): void {
        session.reset();
        announcement = $i18n.t('app.liveDemo.resetDone');
    }
</script>

<svelte:head>
    <title>{$i18n.t('app.liveDemo.title')} · EarnIt Kids</title>
</svelte:head>

<main class="demo-page" aria-labelledby="live-demo-title">
    <div class="demo-shell">
        <header class="demo-header">
            <div>
                <p class="eyebrow">EarnIt Kids</p>
                <h1 id="live-demo-title">{$i18n.t('app.liveDemo.title')}</h1>
                <p class="description">{$i18n.t('app.liveDemo.description')}</p>
            </div>
            <button class="reset" type="button" on:click={resetDemo}>{$i18n.t('app.liveDemo.reset')}</button>
        </header>

        <p class="notice" role="note">{$i18n.t('app.liveDemo.temporaryData')}</p>
        <p class="announcement" aria-live="polite">{announcement}</p>

        {#if mounted}
            <TelegramChildRewards />
        {/if}
    </div>
</main>

<style>
    .demo-page { min-height:100vh; box-sizing:border-box; padding:clamp(1rem, 4vw, 3rem) 1rem; background:#f8fafc; color:#18243d; }
    .demo-shell { width:min(100%, 52rem); margin:0 auto; }
    .demo-header { display:flex; align-items:flex-start; justify-content:space-between; gap:1rem; margin-bottom:1rem; }
    .eyebrow { margin:0 0 .3rem; color:#3867d6; font-size:.75rem; font-weight:700; letter-spacing:.08em; text-transform:uppercase; }
    h1 { margin:0; font-size:clamp(1.5rem, 5vw, 2.2rem); line-height:1.1; }
    .description { margin:.5rem 0 0; color:#5c6780; line-height:1.5; }
    .reset { flex:none; min-height:2.75rem; padding:.6rem .9rem; border:1px solid #3867d6; border-radius:.7rem; background:#fff; color:#2454bb; font:inherit; font-weight:600; cursor:pointer; }
    .reset:focus-visible { outline:3px solid #93b4ff; outline-offset:2px; }
    .notice { margin:0 0 .25rem; padding:.7rem .8rem; border-left:3px solid #f0a51a; background:#fff8e8; color:#5c4b21; font-size:.9rem; line-height:1.45; }
    .announcement { min-height:1.4rem; margin:.35rem 0; color:#26734d; font-size:.85rem; }
    @media (max-width: 420px) { .demo-header { flex-direction:column; } .reset { width:100%; } }
</style>
