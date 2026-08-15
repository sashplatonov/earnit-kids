<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { getPublicSiteUrl } from '$lib/services/publicSiteUrl';
    import type { PageData } from './$types';
    import * as content from '$lib/content/public/ru/parents';
    import PublicSection from '$lib/components/public/PublicSection.svelte';
    import PublicButton from '$lib/components/public/PublicButton.svelte';

    export let data: PageData;

    const i18n = useI18n();
    $: appConfig = data.appConfig;
    $: canonicalUrl = getPublicSiteUrl(appConfig.publicOrigin, '/parents');
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

    <!-- Cards -->
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

    <!-- Settings Section -->
    <PublicSection muted>
        <div class="settings">
            <h2>{content.settingsSection.heading}</h2>
            <p class="lead">{content.settingsSection.lead}</p>
            <ul class="feature-list">
                {#each content.settingsSection.features as feature (feature)}
                    <li>{feature}</li>
                {/each}
            </ul>
        </div>
    </PublicSection>

    <!-- Trust Block (before CTA per backlog) -->
    <PublicSection>
        <div class="trust-block">
            <h2>{content.trustBlock.heading}</h2>
            <div class="trust-roles">
                <div class="role role--parent">
                    <span class="role-tag">Родитель</span>
                    <p>{content.trustBlock.parentLine}</p>
                </div>
                <div class="role role--child">
                    <span class="role-tag">Ребёнок</span>
                    <p>{content.trustBlock.childLine}</p>
                </div>
            </div>
            <p class="note">{content.trustBlock.note}</p>
            <p class="privacy-note">{content.trustBlock.privacyNote}</p>
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

    .settings {
        max-width: 800px;
        margin: 0 auto;
        text-align: center;
    }

    .settings h2 {
        font-size: clamp(1.75rem, 5vw, 2.25rem);
        margin-bottom: 1rem;
    }

    .settings .lead {
        font-size: 1.125rem;
        line-height: 1.6;
        color: var(--public-text-muted);
        max-width: 640px;
        margin: 0 auto 2rem auto;
    }

    .feature-list {
        list-style: none;
        padding: 0;
        margin: 0 auto;
        max-width: 640px;
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
        text-align: left;
    }

    .feature-list li {
        position: relative;
        padding-left: 1.75rem;
        font-size: 1rem;
        color: var(--public-text);
    }

    .feature-list li::before {
        content: '✓';
        position: absolute;
        left: 0;
        color: var(--public-accent);
        font-weight: bold;
    }

    .trust-block {
        max-width: 800px;
        margin: 0 auto;
    }

    .trust-block h2 {
        font-size: clamp(1.75rem, 5vw, 2.25rem);
        margin-bottom: 2rem;
        text-align: center;
    }

    .trust-roles {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 1.5rem;
        margin-bottom: 1.5rem;
    }

    .role {
        padding: 1.5rem;
        border-radius: 1rem;
        border: 1px solid var(--public-border);
    }

    .role--parent {
        background: var(--public-bg-secondary);
    }

    .role--child {
        background: transparent;
    }

    .role-tag {
        display: inline-block;
        font-weight: 700;
        font-size: 0.875rem;
        text-transform: uppercase;
        letter-spacing: 0.02em;
        padding: 0.25rem 0.75rem;
        border-radius: 999px;
        margin-bottom: 1rem;
    }

    .role--parent .role-tag {
        background: var(--public-accent);
        color: #fff;
    }

    .role--child .role-tag {
        background: var(--public-border);
        color: var(--public-text);
    }

    .role p {
        margin: 0;
        line-height: 1.6;
        color: var(--public-text-muted);
    }

    .note {
        text-align: center;
        font-weight: 600;
        color: var(--public-text);
        margin: 0 0 0.5rem 0;
    }

    .privacy-note {
        text-align: center;
        font-size: 0.9375rem;
        line-height: 1.6;
        color: var(--public-text-muted);
        max-width: 640px;
        margin: 0 auto;
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

        .trust-roles {
            grid-template-columns: 1fr;
        }
    }
</style>