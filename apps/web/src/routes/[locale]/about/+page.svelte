<script lang="ts">
    import PublicShell from '$lib/components/PublicShell.svelte';
    import { useI18n } from '$lib/i18n/context';
    const i18n = useI18n();
</script>

<PublicShell title={$i18n.t('public.about.metaTitle')} description={$i18n.t('public.about.metaDescription')}>
    <section class="landing-hero" aria-labelledby="about-title">
        <div class="landing-hero__copy"><span class="public-panel__badge">{$i18n.t('public.about.heroBadge')}</span><h1 id="about-title">{$i18n.t('public.about.heroTitle')}</h1><p>{$i18n.t('public.about.heroText')}</p></div>
        <div class="landing-hero__visual"><div class="landing-hero__panel"><img class="landing-hero__illustration" src="/img/about-family.svg" alt={$i18n.t('public.about.heroAlt')} /></div></div>
    </section>
    <section class="feature-points" aria-label={$i18n.t('public.about.mainAria')}>
        <article class="feature-point"><h2>{$i18n.t('public.about.childTitle')}</h2><p>{$i18n.t('public.about.childText')}</p></article>
        <article class="feature-point"><h2>{$i18n.t('public.about.parentTitle')}</h2><p>{$i18n.t('public.about.parentText')}</p></article>
        <article class="feature-point"><h2>{$i18n.t('public.about.familyTitle')}</h2><p>{$i18n.t('public.about.familyText')}</p></article>
    </section>
</PublicShell>
