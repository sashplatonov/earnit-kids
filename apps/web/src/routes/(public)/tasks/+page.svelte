<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { getPublicSiteUrl } from '$lib/services/publicSiteUrl';
    import type { PageData } from './$types';
    import * as content from '$lib/content/public/ru/tasks';
    import PublicSection from '$lib/components/public/PublicSection.svelte';
    import PublicButton from '$lib/components/public/PublicButton.svelte';

    export let data: PageData;

    const i18n = useI18n();
    $: appConfig = data.appConfig;
    $: canonicalUrl = getPublicSiteUrl(appConfig.publicOrigin, '/tasks');
    $: telegramUrl = getPublicSiteUrl(appConfig.publicOrigin, '/telegram');
</script>

<svelte:head>
    <title>{content.meta.title}</title>
    <meta name="description" content={content.meta.description} />
    <link rel="canonical" href={canonicalUrl} />
</svelte:head>

<div class="public-page">
    <!-- Hero -->
    <PublicSection>
        <div class="hero">
            <span class="eyebrow">{content.hero.eyebrow}</span>
            <h1>{content.hero.title}</h1>
            <p>{content.hero.text}</p>
        </div>
    </PublicSection>

    <!-- Feature cards -->
    <PublicSection>
        <div class="card-grid">
            {#each content.cards as card (card.title)}
                <div class="card">
                    <h3>{card.title}</h3>
                    <p>{card.text}</p>
                </div>
            {/each}
        </div>
    </PublicSection>

    <!-- Example section -->
    <PublicSection muted>
        <div class="examples">
            <h2>{content.exampleSection.heading}</h2>
            <p class="lead">{content.exampleSection.lead}</p>

            <div class="example-card">
                <h3>{content.exampleSection.cardTitle}</h3>
                <ul>
                    {#each content.exampleSection.examples as example (example.title)}
                        <li>
                            <span class="example-title">{example.title}</span>
                            <span class="example-reward">{example.reward}</span>
                        </li>
                    {/each}
                </ul>
            </div>
        </div>
    </PublicSection>

    <!-- Final CTA -->
    <PublicSection>
        <div class="final-cta">
            <h2>{content.cta.title}</h2>
            <PublicButton href={telegramUrl} variant="primary">
                {content.cta.buttonText}
            </PublicButton>
        </div>
    </PublicSection>
</div>

<style>
    .public-page {
        display: flex;
        flex-direction: column;
        gap: 0;
    }

    .hero {
        text-align: center;
        max-width: 800px;
        margin: 0 auto;
    }

    .hero .eyebrow {
        display: block;
        color: var(--public-accent);
        font-weight: 600;
        text-transform: uppercase;
        font-size: 0.875rem;
        margin-bottom: 1rem;
    }

    .hero h1 {
        font-size: clamp(2rem, 8vw, 3rem);
        margin-bottom: 1.5rem;
        line-height: 1.1;
    }

    .hero p {
        font-size: clamp(1.125rem, 3vw, 1.25rem);
        line-height: 1.6;
        color: var(--public-text-muted);
    }

    .card-grid {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 1.5rem;
    }

    .card {
        background: var(--public-bg-secondary);
        padding: 2rem;
        border-radius: 1.5rem;
        border: 1px solid var(--public-border);
    }

    .card h3 {
        margin: 0 0 0.75rem 0;
        font-size: 1.375rem;
    }

    .card p {
        margin: 0;
        line-height: 1.6;
        color: var(--public-text-muted);
        font-size: 0.9375rem;
    }

    .examples {
        max-width: 800px;
        margin: 0 auto;
        text-align: center;
    }

    .examples h2 {
        font-size: clamp(1.75rem, 5vw, 2.25rem);
        margin-bottom: 1rem;
    }

    .examples .lead {
        font-size: 1.125rem;
        line-height: 1.6;
        color: var(--public-text-muted);
        max-width: 640px;
        margin: 0 auto 2rem auto;
    }

    .example-card {
        background: var(--public-bg-secondary);
        border: 1px solid var(--public-border);
        border-radius: 1.5rem;
        padding: 2rem;
        max-width: 480px;
        margin: 0 auto;
        text-align: left;
    }

    .example-card h3 {
        margin: 0 0 1.25rem 0;
        font-size: 1.25rem;
    }

    .example-card ul {
        list-style: none;
        padding: 0;
        margin: 0;
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
    }

    .example-card li {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
        padding-bottom: 0.75rem;
        border-bottom: 1px solid var(--public-border);
    }

    .example-card li:last-child {
        border-bottom: none;
        padding-bottom: 0;
    }

    .example-title {
        font-weight: 500;
    }

    .example-reward {
        color: var(--public-accent);
        font-weight: 600;
        white-space: nowrap;
    }

    .final-cta {
        text-align: center;
        padding: 4rem 0;
    }

    .final-cta h2 {
        margin-bottom: 2rem;
        font-size: clamp(2rem, 5vw, 2.5rem);
    }

    @media (max-width: 768px) {
        .card-grid {
            grid-template-columns: 1fr;
        }
    }
</style>