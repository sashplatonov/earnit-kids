<script lang="ts">
    import { onMount } from 'svelte';
    import { useI18n } from '$lib/i18n/context';
    import { initializeTelegramWebApp } from '$lib/services/telegram';
    import { getPublicSiteUrl } from '$lib/services/publicSiteUrl';
    import type { PageData } from './$types';
    import * as content from '$lib/content/public/ru/home';
    import PublicSection from '$lib/components/public/PublicSection.svelte';
    import PublicButton from '$lib/components/public/PublicButton.svelte';

    export let data: PageData;

    const i18n = useI18n();

    $: appConfig = data.appConfig;
    $: telegramUrl = appConfig.telegramMiniAppUrl;
    $: canonicalUrl = getPublicSiteUrl(appConfig.publicOrigin, '/');
    $: ogImageUrl = getPublicSiteUrl(appConfig.publicOrigin, '/img/og-image.png');

    onMount(() => {
        const telegram = initializeTelegramWebApp();
        if (!telegram?.initData) {
            return;
        }

        // EXPLAIN: Telegram opens the BotFather Main Mini App URL for `startapp`
        // EXPLAIN: links. Keep the launch parameters while routing every Telegram
        // EXPLAIN: session through the dedicated gate that completes pairing.
        window.location.replace(`/telegram${window.location.search}`);
    });
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

<section class="public-hero">
    <div class="container public-hero-grid">
        <div class="public-hero-copy">
            <p class="public-eyebrow">{content.hero.eyebrow}</p>
            <h1>{content.hero.title}</h1>
            <p class="public-lead">{content.hero.text}</p>
            <div class="public-hero-actions">
                {#if telegramUrl}
                    <PublicButton href={telegramUrl} variant="primary" rel="external noopener">
                        {$i18n.t('public.cta.openTelegram')}
                    </PublicButton>
                {/if}
                <PublicButton href="/how" variant="secondary">
                    {content.hero.secondaryCta}
                </PublicButton>
            </div>
            <p class="public-note">{content.hero.note}</p>
        </div>
        <aside class="public-hero-card" aria-label="Пример дня ребёнка">
            <h3>{content.exampleCard.title}</h3>
            <p class="public-muted-copy">{content.exampleCard.text}</p>
            <div class="public-metric-row">
                {#each content.exampleCard.metrics as metric (metric.label)}
                    <div class="public-metric">
                        <b>{metric.value}</b>
                        <span>{metric.label}</span>
                    </div>
                {/each}
            </div>
        </aside>
    </div>
</section>

<PublicSection muted>
    <div class="public-section-head">
        <div>
            <h2>{content.changesSection.heading}</h2>
        </div>
        <p>{content.changesSection.intro}</p>
    </div>
    <div class="public-grid-3">
        {#each content.changesSection.cards as card (card.title)}
            <article class="public-card">
                <h3>{card.title}</h3>
                <p>{card.description}</p>
            </article>
        {/each}
    </div>
</PublicSection>

<PublicSection>
    <div class="public-split">
        <div>
            <h2>{content.quickSection.heading}</h2>
            <p class="public-body-lead">{content.quickSection.text}</p>
            <div class="public-feature-list">
                {#each content.quickSection.features as feature (feature.text)}
                    <div class="public-feature">
                        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4 10-10"/></svg>
                        <span>{feature.text}</span>
                    </div>
                {/each}
            </div>
        </div>
        <div class="public-step-stack">
            {#each content.quickSection.steps as step (step.number)}
                <div class="public-step">
                    <div class="public-step-num">{step.number}</div>
                    <div>
                        <h3>{step.title}</h3>
                        <p>{step.description}</p>
                    </div>
                </div>
            {/each}
        </div>
    </div>
</PublicSection>

<PublicSection>
    <div class="public-trust-block">
        <h2>{content.trustBlock.heading}</h2>
        <p>{content.trustBlock.parentLine}</p>
        <p>{content.trustBlock.childLine}</p>
    </div>
</PublicSection>

<PublicSection>
    <div class="public-callout">
        <div>
            <h2>{content.callout.title}</h2>
            <p>{content.callout.text}</p>
        </div>
        {#if telegramUrl}
            <PublicButton href={telegramUrl} variant="primary" rel="external noopener">
                {$i18n.t('public.cta.openTelegram')}
            </PublicButton>
        {/if}
    </div>
</PublicSection>

<style>
    .public-hero {
        padding: 2.875rem 0 2.125rem;
    }

    .public-hero-grid {
        display: grid;
        grid-template-columns: minmax(0, 1.08fr) minmax(320px, .92fr);
        gap: 1.875rem;
        align-items: center;
    }

    .public-eyebrow {
        display: inline-flex;
        align-items: center;
        gap: .4375rem;
        padding: .375rem .5625rem;
        border-radius: 999px;
        background: var(--public-blue-soft);
        color: var(--public-blue);
        font-size: .75rem;
        font-weight: 800;
        margin-bottom: .8125rem;
        white-space: nowrap;
    }

    .public-lead {
        font-size: clamp(1.125rem, 2vw, 1.3125rem);
        color: var(--public-muted);
        max-width: var(--public-content-max);
        margin: 1.125rem 0 0;
    }

    .public-note {
        font-size: .75rem;
        color: var(--public-muted);
        margin: .5625rem 0 0;
    }

    .public-hero-actions {
        display: flex;
        gap: .5625rem;
        flex-wrap: wrap;
        margin-top: 1.375rem;
    }

    .public-hero-card {
        background: linear-gradient(145deg, #fff, #f4f7ff);
        border: 1px solid #dfe6f1;
        border-radius: 1.4375rem;
        padding: 1.25rem;
        box-shadow: var(--public-shadow);
    }

    .public-hero-card h3 {
        margin-bottom: .4375rem;
    }

    .public-muted-copy {
        color: var(--public-muted);
        margin: .4375rem 0 .9375rem;
    }

    .public-metric-row {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: .5rem;
    }

    .public-metric {
        background: #fff;
        border: 1px solid var(--public-line);
        border-radius: .8125rem;
        padding: .6875rem;
    }

    .public-metric b {
        display: block;
        font-size: 1.25rem;
    }

    .public-metric span {
        font-size: .6875rem;
        color: var(--public-muted);
    }

    .public-section-head {
        display: flex;
        justify-content: space-between;
        gap: 1.25rem;
        align-items: end;
        margin-bottom: 1rem;
    }

    .public-section-head p {
        max-width: 610px;
        margin: 0;
        color: var(--public-muted);
    }

    .public-grid-3 {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: .6875rem;
    }

    .public-card {
        background: var(--public-surface);
        border: 1px solid var(--public-line);
        border-radius: var(--public-radius);
        padding: 1.0625rem;
        box-shadow: 0 4px 13px rgb(39 54 82 / 4.5%);
    }

    .public-card p {
        color: var(--public-muted);
        margin: .5rem 0 0;
    }

    .public-split {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 1.125rem;
        align-items: start;
    }

    .public-body-lead {
        font-size: 1.0625rem;
        color: var(--public-muted);
        margin: .75rem 0 0;
        max-width: 690px;
    }

    .public-feature-list {
        display: grid;
        gap: .5rem;
        margin-top: .875rem;
    }

    .public-feature {
        display: flex;
        gap: .5625rem;
        align-items: flex-start;
    }

    .public-feature svg {
        width: 1.125rem;
        height: 1.125rem;
        color: var(--public-green);
        fill: none;
        stroke: currentColor;
        stroke-width: 2.2;
        flex: 0 0 auto;
        margin-top: 2px;
    }

    .public-feature span {
        color: #4f5d72;
    }

    .public-step-stack {
        display: grid;
        gap: .5rem;
    }

    .public-step {
        display: flex;
        gap: .75rem;
        align-items: flex-start;
        background: #fff;
        border: 1px solid var(--public-line);
        border-radius: .875rem;
        padding: .75rem;
    }

    .public-step-num {
        width: 1.8125rem;
        height: 1.8125rem;
        border-radius: .5rem;
        background: var(--public-blue);
        color: #fff;
        display: grid;
        place-items: center;
        font-size: .8125rem;
        font-weight: 850;
        flex: 0 0 auto;
    }

    .public-step p {
        margin: .1875rem 0 0;
        color: var(--public-muted);
        font-size: .875rem;
    }

    .public-trust-block {
        background: var(--public-surface);
        border: 1px solid var(--public-line);
        border-radius: var(--public-radius);
        padding: 1.25rem;
    }

    .public-trust-block p {
        color: var(--public-muted);
        margin: .5rem 0 0;
    }

    .public-callout {
        background: #15294d;
        color: #fff;
        border-radius: 1.3125rem;
        padding: 1.25rem;
        display: grid;
        grid-template-columns: 1fr auto;
        gap: 1.25rem;
        align-items: center;
    }

    .public-callout p {
        color: #cbd6e8;
        margin: .4375rem 0 0;
    }

    @media (max-width: 900px) {
        .public-hero-grid,
        .public-split {
            grid-template-columns: 1fr;
        }
        .public-grid-3 {
            grid-template-columns: repeat(2, minmax(0, 1fr));
        }
        .public-callout {
            grid-template-columns: 1fr;
        }
        .public-section-head {
            display: block;
        }
        .public-section-head p {
            margin-top: .4375rem;
        }
    }

    @media (max-width: 640px) {
        .public-hero {
            padding: 1.4375rem 0 1.625rem;
        }
        .public-hero-actions {
            display: grid;
            grid-template-columns: 1fr;
        }
        .public-grid-3 {
            grid-template-columns: 1fr;
        }
        .public-hero-card {
            padding: .9375rem;
        }
    }
</style>