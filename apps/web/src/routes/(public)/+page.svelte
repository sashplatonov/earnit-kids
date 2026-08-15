<script lang="ts">
    import { onMount } from 'svelte';
    import { useI18n } from '$lib/i18n/context';
    import { initializeTelegramWebApp } from '$lib/services/telegram';

    const i18n = useI18n();

    onMount(() => {
        const telegram = initializeTelegramWebApp();
        if (!telegram?.initData) {
            return;
        }

        // EXPLAIN: Telegram opens the BotFather Main Mini App URL for `startapp`
        // EXPLAIN: links. Keep the launch parameters while routing every Telegram
        // EXPLAIN: session through the dedicated gate that completes pairing.
        window.location.replace(`/telegram${window.location.search}`);
    });
</script>

<svelte:head>
    <title>{$i18n.t('public.home.metaTitle')}</title>
    <meta name="description" content={$i18n.t('public.home.metaDescription')} />
</svelte:head>

<section aria-label={$i18n.t('public.home.mainAria')}>
    <h1>{$i18n.t('public.home.heroTitle')}</h1>
    <p>{$i18n.t('public.home.heroText')}</p>
</section>