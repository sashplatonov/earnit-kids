<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { getPublicSiteUrl } from '$lib/services/publicSiteUrl';
    import type { PageData } from './$types';
    import * as content from '$lib/content/public/ru/rewards';
    import PublicSection from '$lib/components/public/PublicSection.svelte';
    import PublicButton from '$lib/components/public/PublicButton.svelte';

    export let data: PageData;

    const i18n = useI18n();
    $: appConfig = data.appConfig;
    $: canonicalUrl = getPublicSiteUrl(appConfig.publicOrigin, '/rewards');
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

    <!-- Callout -->
    <PublicSection>
        <div class="callout">
            <h2>{content.callout.heading}</h2>
            <p>{content.callout.text}</p>
            <PublicButton href={telegramUrl} variant="primary">
                {content.callout.buttonText}
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

    .callout {
        max-width: 720px;
        margin: 0 auto;
        text-align: center;
        background: var(--public-bg-secondary);
        border: 1px solid var(--public-border);
        border-radius: 1.5rem;
        padding: 3rem 2rem;
    }

    .callout h2 {
        font-size: clamp(1.75rem, 5vw, 2.25rem);
        margin: 0 0 1rem 0;
        line-height: 1.2;
    }

    .callout p {
        font-size: 1.125rem;
        line-height: 1.6;
        color: var(--public-text-muted);
        max-width: 560px;
        margin: 0 auto 2rem auto;
    }

    @media (max-width: 768px) {
        .card-grid {
            grid-template-columns: 1fr;
        }

        .callout {
            padding: 2rem 1.5rem;
        }
    }
</style>