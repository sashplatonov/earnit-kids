<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
    import { useI18n } from '$lib/i18n/context';
    import type { PageData } from './$types';

    export let data: PageData;

    const i18n = useI18n();

    $: alternates = $i18n.alternates('/');
</script>

<svelte:head>
    <title>{$i18n.t('public.home.metaTitle')}</title>
    <meta name="description" content={$i18n.t('public.home.metaDescription')} />
    <link rel="canonical" href={$i18n.href('/')} />
    <link rel="alternate" hreflang="en" href={alternates.en} />
    <link rel="alternate" hreflang="ru" href={alternates.ru} />
    <link rel="alternate" hreflang="x-default" href={alternates['x-default']} />
    <meta property="og:title" content={$i18n.t('public.home.metaTitle')} />
    <meta property="og:description" content={$i18n.t('public.home.metaDescription')} />
    <meta property="og:image" content="/img/og-image.png" />
</svelte:head>

{#if !data.session.authenticated}
<div class="public-shell">
    <PublicTopNav />
    <main class="public-inner" aria-label={$i18n.t('public.home.mainAria')}>
        <section class="landing-hero" aria-labelledby="landing-hero-title">
            <div class="landing-hero__copy">
                <p class="public-panel__badge">{$i18n.t('public.home.heroBadge')}</p>
                <h1 id="landing-hero-title">{$i18n.t('public.home.heroTitle')}</h1>
                <p>{$i18n.t('public.home.heroText')}</p>
                <div class="hero-actions">
                    <a class="btn btn--primary" href={$i18n.href('/login')}>{$i18n.t('common.actions.startTogether')}</a>
                    <a class="btn btn--ghost" href={$i18n.href('/about')}>{$i18n.t('common.actions.learnHow')}</a>
                </div>
                <div class="hero-impact" aria-label={$i18n.t('public.home.mainAria')}>
                    <div class="hero-impact__item">
                        <strong>{$i18n.t('public.home.metricAgeTitle')}</strong>
                        <span>{$i18n.t('public.home.metricAgeText')}</span>
                    </div>
                    <div class="hero-impact__item">
                        <strong>{$i18n.t('public.home.metricStepsTitle')}</strong>
                        <span>{$i18n.t('public.home.metricStepsText')}</span>
                    </div>
                    <div class="hero-impact__item">
                        <strong>{$i18n.t('public.home.metricPeaceTitle')}</strong>
                        <span>{$i18n.t('public.home.metricPeaceText')}</span>
                    </div>
                </div>
            </div>
            <div class="landing-hero__visual">
                <img class="landing-hero__illustration" src="/img/landing-family.svg" alt={$i18n.t('public.home.visualAlt')} loading="lazy" />
                <div class="landing-hero__visual-note">
                    <div>
                        <strong>{$i18n.t('public.home.visualNoteTitle')}</strong>
                        <p>{$i18n.t('public.home.visualNoteText')}</p>
                    </div>
                </div>
                <div class="landing-hero__panel">
                    <p class="value-card__hint">{$i18n.t('public.home.valueHint')}</p>
                    <div class="value-grid" style="margin-top: 1.5rem;">
                        <article class="value-card">
                            <h3>{$i18n.t('public.home.valueTasksTitle')}</h3>
                            <p>{$i18n.t('public.home.valueTasksText')}</p>
                        </article>
                        <article class="value-card">
                            <h3>{$i18n.t('public.home.valueShopTitle')}</h3>
                            <p>{$i18n.t('public.home.valueShopText')}</p>
                        </article>
                        <article class="value-card">
                            <h3>{$i18n.t('public.home.valueProgressTitle')}</h3>
                            <p>{$i18n.t('public.home.valueProgressText')}</p>
                        </article>
                    </div>
                </div>
            </div>
        </section>

        <section class="value-grid">
            <article class="value-card">
                <h3>{$i18n.t('public.home.childCardTitle')}</h3>
                <p>{$i18n.t('public.home.childCardText')}</p>
            </article>
            <article class="value-card">
                <h3>{$i18n.t('public.home.parentCardTitle')}</h3>
                <p>{$i18n.t('public.home.parentCardText')}</p>
            </article>
            <article class="value-card">
                <h3>{$i18n.t('public.home.familyCardTitle')}</h3>
                <p>{$i18n.t('public.home.familyCardText')}</p>
            </article>
        </section>

        <section class="public-panel">
            <div>
                <p class="public-panel__badge">{$i18n.t('public.home.styleBadge')}</p>
                <h3>{$i18n.t('public.home.styleTitle')}</h3>
                <p>{$i18n.t('public.home.styleText')}</p>
            </div>
            <div class="feature-points">
                <div class="feature-point">
                    <h3>{$i18n.t('public.home.mobileFocusTitle')}</h3>
                    <p>{$i18n.t('public.home.mobileFocusText')}</p>
                </div>
                <div class="feature-point">
                    <h3>{$i18n.t('public.home.roleSectionsTitle')}</h3>
                    <p>{$i18n.t('public.home.roleSectionsText')}</p>
                </div>
                <div class="feature-point">
                    <h3>{$i18n.t('public.home.unifiedStyleTitle')}</h3>
                    <p>{$i18n.t('public.home.unifiedStyleText')}</p>
                </div>
            </div>
            <div class="feature-layout__actions">
                <a class="btn btn--primary" href={$i18n.href('/features/tasks')}>{$i18n.t('common.navigation.tasks')}</a>
                <a class="btn btn--ghost" href={$i18n.href('/features/shop')}>{$i18n.t('common.navigation.shop')}</a>
            </div>
        </section>

        <section class="public-cta">
            <div>
                <h3>{$i18n.t('public.home.ctaTitle')}</h3>
                <p>{$i18n.t('public.home.ctaText')}</p>
            </div>
            <a class="btn btn--primary" href={$i18n.href('/login')}>{$i18n.t('common.actions.join')}</a>
        </section>
    </main>
</div>
{/if}
