<script lang="ts">
    import { page } from '$app/stores';
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
    import { useI18n } from '$lib/i18n/context';

    export let title: string;
    export let description: string;
    export let image = '/img/og-image.png';

    const i18n = useI18n();
    $: alternates = $i18n.alternates($page.url.pathname);
</script>

<svelte:head>
    <title>{title}</title>
    <meta name="description" content={description} />
    <meta property="og:type" content="website" />
    <meta property="og:title" content={title} />
    <meta property="og:description" content={description} />
    <meta property="og:image" content={image} />
    <meta property="og:locale" content={$i18n.locale === 'ru' ? 'ru_RU' : 'en_US'} />
    <link rel="canonical" href={alternates[$i18n.locale]} />
    <link rel="alternate" hreflang="en" href={alternates.en} />
    <link rel="alternate" hreflang="ru" href={alternates.ru} />
    <link rel="alternate" hreflang="x-default" href={alternates['x-default']} />
</svelte:head>

<div class="public-shell" data-locale={$i18n.locale}>
    <PublicTopNav />
    <main class="public-inner" id="main" aria-label={$i18n.t('public.shell.mainAria')}>
        <slot />
        <section class="public-cta" aria-labelledby="public-cta-title">
            <div>
                <h2 id="public-cta-title">{$i18n.t('public.home.ctaTitle')}</h2>
                <p>{$i18n.t('public.home.ctaText')}</p>
            </div>
            <a class="btn btn--primary" href="/telegram">{$i18n.t('public.cta.openTelegram')} →</a>
        </section>
        <footer class="footer" aria-label={$i18n.t('common.brand.name')}>
            <div class="footer-row">
                <strong>{$i18n.t('common.brand.name')}</strong>
                <span>{$i18n.t('public.shell.footerTagline')}</span>
            </div>
        </footer>
    </main>
</div>
