<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { getPublicSiteUrl } from '$lib/services/publicSiteUrl';
    import type { PageData } from './$types';
    import * as content from '$lib/content/public/ru/tasks';
    import PublicSection from '$lib/components/public/PublicSection.svelte';
    import PublicIcon from '$lib/components/public/PublicIcon.svelte';

    export let data: PageData;

    const i18n = useI18n();
    $: appConfig = data.appConfig;
    $: canonicalUrl = getPublicSiteUrl(appConfig.publicOrigin, '/tasks');
    $: ogImageUrl = getPublicSiteUrl(appConfig.publicOrigin, '/img/og-image.png');
</script>

<svelte:head>
    <title>{content.meta.title}</title>
    <meta name="description" content={content.meta.description} />
    <link rel="canonical" href={canonicalUrl} />
    <meta property="og:title" content={content.meta.title} />
    <meta property="og:description" content={content.meta.description} />
    <meta property="og:url" content={canonicalUrl} />
    <meta property="og:image" content={ogImageUrl} />
    <meta property="og:image:width" content="1200" />
    <meta property="og:image:height" content="630" />
</svelte:head>

<div class="public-page">
    <!-- Hero -->
    <PublicSection>
        <div class="page-hero">
            <div class="eyebrow">
                <PublicIcon name="list" />
                {content.hero.eyebrow}
            </div>
            <h1>{content.hero.title}</h1>
            <p>{content.hero.text}</p>
        </div>
    </PublicSection>

    <!-- Feature cards -->
    <PublicSection>
        <div class="grid-3">
            {#each content.cards as card (card.title)}
                <article class="card">
                    <div class="icon-box"><PublicIcon name={card.icon} /></div>
                    <h3>{card.title}</h3>
                    <p>{card.text}</p>
                </article>
            {/each}
        </div>
    </PublicSection>

    <!-- Example section -->
    <PublicSection muted>
        <div class="split">
            <div>
                <h2>{content.exampleSection.heading}</h2>
                <p class="body-lead">{content.exampleSection.lead}</p>
            </div>
            <div class="card">
                <h3>{content.exampleSection.cardTitle}</h3>
                <div class="feature-list">
                    {#each content.exampleSection.examples as example (example.title)}
                        <div class="feature">
                            <PublicIcon name="check" />
                            <span>{example.title} - {example.reward}</span>
                        </div>
                    {/each}
                </div>
            </div>
        </div>
    </PublicSection>
</div>

<style>
    .public-page {
        display: flex;
        flex-direction: column;
        gap: 0;
    }

    .page-hero {
        text-align: left;
        max-width: 100%;
    }
</style>