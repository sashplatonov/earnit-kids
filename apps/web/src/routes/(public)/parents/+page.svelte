<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { getPublicSiteUrl } from '$lib/services/publicSiteUrl';
    import type { PageData } from './$types';
    import * as content from '$lib/content/public/ru/parents';
    import PublicSection from '$lib/components/public/PublicSection.svelte';
    import PublicButton from '$lib/components/public/PublicButton.svelte';
    import PublicIcon from '$lib/components/public/PublicIcon.svelte';

    export let data: PageData;

    const i18n = useI18n();
    $: appConfig = data.appConfig;
    $: canonicalUrl = getPublicSiteUrl(appConfig.publicOrigin, '/parents');
    $: telegramUrl = getPublicSiteUrl(appConfig.publicOrigin, '/telegram');
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
                <PublicIcon name="family" />
                {content.hero.eyebrow}
            </div>
            <h1>{content.hero.title}</h1>
            <p>{content.hero.text}</p>
        </div>
    </PublicSection>

    <!-- Cards -->
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

    <!-- Settings Section -->
    <PublicSection muted>
        <div class="split">
            <div>
                <h2>{content.settingsSection.heading}</h2>
                <p class="body-lead">{content.settingsSection.lead}</p>
            </div>
            <div class="feature-list">
                {#each content.settingsSection.features as feature (feature)}
                    <div class="feature">
                        <PublicIcon name="check" />
                        <span>{feature}</span>
                    </div>
                {/each}
            </div>
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
                <PublicIcon name="telegram" />
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

    .page-hero {
        text-align: left;
        max-width: 100%;
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
        .trust-roles {
            grid-template-columns: 1fr;
        }
    }
</style>