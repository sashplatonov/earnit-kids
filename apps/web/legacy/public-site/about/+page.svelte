<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
    import { useI18n } from '$lib/i18n/context';

    const i18n = useI18n();

    $: alternates = $i18n.alternates('/about');
</script>

<svelte:head>
    <title>{$i18n.t('public.about.metaTitle')}</title>
    <meta name="description" content={$i18n.t('public.about.metaDescription')} />
    <link rel="canonical" href={$i18n.href('/about')} />
    <link rel="alternate" hreflang="en" href={alternates.en} />
    <link rel="alternate" hreflang="ru" href={alternates.ru} />
    <link rel="alternate" hreflang="x-default" href={alternates['x-default']} />
</svelte:head>

<div class="public-shell">
    <PublicTopNav />
    <main class="public-inner" aria-label={$i18n.t('public.about.mainAria')}>
        <section class="landing-hero" aria-labelledby="about-hero-title">
            <div>
                <p class="public-panel__badge">{$i18n.t('public.about.heroBadge')}</p>
                <h1 id="about-hero-title">{$i18n.t('public.about.heroTitle')}</h1>
                <p>{$i18n.t('public.about.heroText')}</p>
                <div class="hero-actions">
                    <a class="btn btn--primary" href={$i18n.href('/login')}>{$i18n.t('common.actions.login')}</a>
                    <a class="btn btn--ghost" href={$i18n.href('/faq')}>{$i18n.t('public.about.faqLink')}</a>
                </div>
            </div>
            <div>
                <img src="/img/about-family.svg" alt={$i18n.t('public.about.heroAlt')} loading="lazy" />
            </div>
        </section>

        <section class="value-grid" aria-label="Основные принципы">
            <article class="value-card">
                <h3>{$i18n.t('public.about.childTitle')}</h3>
                <p>{$i18n.t('public.about.childText')}</p>
            </article>
            <article class="value-card">
                <h3>{$i18n.t('public.about.parentTitle')}</h3>
                <p>{$i18n.t('public.about.parentText')}</p>
            </article>
            <article class="value-card">
                <h3>{$i18n.t('public.about.familyTitle')}</h3>
                <p>{$i18n.t('public.about.familyText')}</p>
            </article>
        </section>

        <section class="public-cta">
            <div>
                <h3>{$i18n.t('public.about.ctaTitle')}</h3>
                <p>{$i18n.t('public.about.ctaText')}</p>
            </div>
            <a class="btn btn--primary" href={$i18n.href('/login')}>{$i18n.t('common.actions.tryIt')}</a>
        </section>
    </main>
</div>
