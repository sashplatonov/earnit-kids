<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
    import { useI18n } from '$lib/i18n/context';

    const i18n = useI18n();

    $: alternates = $i18n.alternates('/faq');
    $: faqItems = [
        { q: $i18n.t('public.faq.q1Question'), a: $i18n.t('public.faq.q1Answer') },
        { q: $i18n.t('public.faq.q2Question'), a: $i18n.t('public.faq.q2Answer') },
        { q: $i18n.t('public.faq.q3Question'), a: $i18n.t('public.faq.q3Answer') },
    ];
</script>

<svelte:head>
    <title>{$i18n.t('public.faq.metaTitle')}</title>
    <meta name="description" content={$i18n.t('public.faq.metaDescription')} />
    <link rel="canonical" href={$i18n.href('/faq')} />
    <link rel="alternate" hreflang="en" href={alternates.en} />
    <link rel="alternate" hreflang="ru" href={alternates.ru} />
    <link rel="alternate" hreflang="x-default" href={alternates['x-default']} />
</svelte:head>

<div class="public-shell">
    <PublicTopNav />
    <main class="public-inner" aria-label={$i18n.t('public.faq.mainAria')}>
        <section class="faq-shell" aria-labelledby="faq-title">
            <h1 id="faq-title">{$i18n.t('public.faq.title')}</h1>
            <p class="value-card__hint">{$i18n.t('public.faq.intro')}</p>
            <div class="faq-grid">
                {#each faqItems as item (item.q)}
                    <article class="faq-card">
                        <details>
                            <summary>
                                <span>{item.q}</span>
                                <span class="faq-card__icon">❓</span>
                            </summary>
                            <p>{item.a}</p>
                        </details>
                    </article>
                {/each}
            </div>
        </section>
        <section class="value-grid" aria-label={$i18n.t('public.faq.updatesAria')}>
            <article class="value-card">
                <h3>{$i18n.t('public.faq.requestsStatusTitle')}</h3>
                <p>{$i18n.t('public.faq.requestsStatusText')}</p>
            </article>
            <article class="value-card">
                <h3>{$i18n.t('public.faq.parentRequestsTitle')}</h3>
                <p>{$i18n.t('public.faq.parentRequestsText')}</p>
            </article>
            <article class="value-card">
                <h3>{$i18n.t('public.faq.mobileNavTitle')}</h3>
                <p>{$i18n.t('public.faq.mobileNavText')}</p>
            </article>
        </section>
        <section class="public-cta">
            <div>
                <h3>{$i18n.t('public.faq.supportTitle')}</h3>
                <p>{$i18n.t('public.faq.supportText')}</p>
            </div>
            <a class="btn btn--ghost" href={$i18n.href('/login')}>{$i18n.t('public.faq.supportCta')}</a>
        </section>
    </main>
</div>
