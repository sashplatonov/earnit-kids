<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
    import { page } from '$app/stores';
    import { useI18n } from '$lib/i18n/context';

    type FeatureConfig = {
        titleKey: 'public.features.tasksTitle' | 'public.features.shopTitle';
        descriptionKey: 'public.features.tasksDescription' | 'public.features.shopDescription';
        badgeKey: 'public.features.tasksBadge' | 'public.features.shopBadge';
        headingKey: 'public.features.tasksHeading' | 'public.features.shopHeading';
        subheadingKey: 'public.features.tasksSubheading' | 'public.features.shopSubheading';
        bodyKey: 'public.features.tasksBody' | 'public.features.shopBody';
        imageAltKey: 'public.features.tasksImageAlt' | 'public.features.shopImageAlt';
        bullets: Array<
            | 'public.features.tasksBulletOne'
            | 'public.features.tasksBulletTwo'
            | 'public.features.tasksBulletThree'
            | 'public.features.shopBulletOne'
            | 'public.features.shopBulletTwo'
            | 'public.features.shopBulletThree'
        >;
        image: string;
    };

    const i18n = useI18n();

    const featureData: Record<string, FeatureConfig> = {
        tasks: {
            titleKey: 'public.features.tasksTitle',
            descriptionKey: 'public.features.tasksDescription',
            badgeKey: 'public.features.tasksBadge',
            headingKey: 'public.features.tasksHeading',
            subheadingKey: 'public.features.tasksSubheading',
            bodyKey: 'public.features.tasksBody',
            bullets: [
                'public.features.tasksBulletOne',
                'public.features.tasksBulletTwo',
                'public.features.tasksBulletThree',
            ],
            image: '/img/feature-tasks.svg',
            imageAltKey: 'public.features.tasksImageAlt',
        },
        shop: {
            titleKey: 'public.features.shopTitle',
            descriptionKey: 'public.features.shopDescription',
            badgeKey: 'public.features.shopBadge',
            headingKey: 'public.features.shopHeading',
            subheadingKey: 'public.features.shopSubheading',
            bodyKey: 'public.features.shopBody',
            bullets: [
                'public.features.shopBulletOne',
                'public.features.shopBulletTwo',
                'public.features.shopBulletThree',
            ],
            image: '/img/feature-shop.svg',
            imageAltKey: 'public.features.shopImageAlt',
        },
    };

    $: slug = $page.params.slug;
    $: feature = featureData[slug] ?? featureData.tasks;
    $: alternates = $i18n.alternates(`/features/${slug}`);
    $: featureTitle = $i18n.t(feature.titleKey);
    $: featureDescription = $i18n.t(feature.descriptionKey);
    $: featureHeading = $i18n.t(feature.headingKey);
    $: featureSubheading = $i18n.t(feature.subheadingKey);
    $: featureBody = $i18n.t(feature.bodyKey);
    $: featureBadge = $i18n.t(feature.badgeKey);
    $: featureImageAlt = $i18n.t(feature.imageAltKey);
    $: featureBullets = feature.bullets.map((key) => $i18n.t(key));
</script>

<svelte:head>
    <title>{featureTitle}</title>
    <meta name="description" content={featureDescription} />
    <link rel="canonical" href={$i18n.href(`/features/${slug}`)} />
    <link rel="alternate" hreflang="en" href={alternates.en} />
    <link rel="alternate" hreflang="ru" href={alternates.ru} />
    <link rel="alternate" hreflang="x-default" href={alternates['x-default']} />
</svelte:head>

<div class="public-shell">
    <PublicTopNav />
    <main class="public-inner" aria-label={$i18n.t('public.features.pageAria')}>
        <section class="feature-layout" aria-labelledby="feature-title">
            <div class="feature-layout__content">
                <p class="public-panel__badge">{featureBadge}</p>
                <h1 id="feature-title">{featureHeading}</h1>
                <p>{featureSubheading}</p>
                <p>{featureBody}</p>
                <div class="feature-layout__actions">
                    <a class="btn btn--primary" href={$i18n.href('/login')}>{featureBadge}</a>
                    <a class="btn btn--ghost" href={$i18n.href('/login')}>{$i18n.t('common.actions.login')}</a>
                </div>
            </div>
            <div class="feature-layout__panel">
                <img src={feature.image} alt={featureImageAlt} loading="lazy" />
                <p>{featureDescription}</p>
                <ul class="feature-bullets">
                    {#each featureBullets as bullet (bullet)}
                        <li>{bullet}</li>
                    {/each}
                </ul>
            </div>
        </section>
        <section class="value-grid" aria-label="Сценарии для семьи">
            <article class="value-card">
                <h3>{$i18n.t('public.features.childCardTitle')}</h3>
                <p>{$i18n.t('public.features.childCardText')}</p>
            </article>
            <article class="value-card">
                <h3>{$i18n.t('public.features.parentCardTitle')}</h3>
                <p>{$i18n.t('public.features.parentCardText')}</p>
            </article>
            <article class="value-card">
                <h3>{$i18n.t('public.features.familyCardTitle')}</h3>
                <p>{$i18n.t('public.features.familyCardText')}</p>
            </article>
        </section>
    </main>
</div>
