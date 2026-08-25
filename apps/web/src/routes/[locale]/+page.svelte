<script lang="ts">
    import PublicShell from '$lib/components/PublicShell.svelte';
    import { useI18n } from '$lib/i18n/context';

    const i18n = useI18n();
</script>

<PublicShell title={$i18n.t('public.home.metaTitle')} description={$i18n.t('public.home.metaDescription')}>
    <section class="landing-hero" aria-labelledby="home-title">
        <div class="landing-hero__copy">
            <span class="public-panel__badge">{$i18n.t('public.home.heroBadge')}</span>
            <h1 id="home-title">{$i18n.t('public.home.heroTitle')}</h1>
            <p>{$i18n.t('public.home.heroText')}</p>
            <div class="hero-impact">
                <div class="hero-impact__item"><strong>{$i18n.t('public.home.metricAgeTitle')}</strong><span>{$i18n.t('public.home.metricAgeText')}</span></div>
                <div class="hero-impact__item"><strong>{$i18n.t('public.home.metricStepsTitle')}</strong><span>{$i18n.t('public.home.metricStepsText')}</span></div>
                <div class="hero-impact__item"><strong>{$i18n.t('public.home.metricPeaceTitle')}</strong><span>{$i18n.t('public.home.metricPeaceText')}</span></div>
            </div>
            <div class="hero-actions">
                <a class="btn btn--primary" href="/telegram">{$i18n.t('public.cta.openTelegram')} →</a>
                <a class="btn btn--ghost" href={$i18n.href('/features/tasks')}>{$i18n.t('common.actions.learnHow')}</a>
            </div>
        </div>
        <div class="landing-hero__visual">
            <div class="landing-hero__panel"><img class="landing-hero__illustration" src="/img/landing-family.svg" alt={$i18n.t('public.home.visualAlt')} /></div>
            <div class="landing-hero__visual-note"><div><strong>{$i18n.t('public.home.visualNoteTitle')}</strong><p>{$i18n.t('public.home.visualNoteText')}</p></div></div>
        </div>
    </section>
    <section class="public-panel" aria-labelledby="home-values-title">
        <p class="value-card__hint">{$i18n.t('public.home.valueHint')}</p>
        <div class="value-grid" id="home-values-title">
            <article class="value-card"><h2>{$i18n.t('public.home.valueTasksTitle')}</h2><p>{$i18n.t('public.home.valueTasksText')}</p></article>
            <article class="value-card"><h2>{$i18n.t('public.home.valueShopTitle')}</h2><p>{$i18n.t('public.home.valueShopText')}</p></article>
            <article class="value-card"><h2>{$i18n.t('public.home.valueProgressTitle')}</h2><p>{$i18n.t('public.home.valueProgressText')}</p></article>
        </div>
    </section>
</PublicShell>
