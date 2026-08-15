<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { getPublicSiteUrl } from '$lib/services/publicSiteUrl';
    import type { PageData } from './$types';
    import * as content from '$lib/content/public/ru/how';
    import PublicSection from '$lib/components/public/PublicSection.svelte';
    import PublicButton from '$lib/components/public/PublicButton.svelte';
    import PublicIcon from '$lib/components/public/PublicIcon.svelte';
    import { fade } from 'svelte/transition';

    export let data: PageData;

    const i18n = useI18n();
    $: appConfig = data.appConfig;
    $: canonicalUrl = getPublicSiteUrl(appConfig.publicOrigin, '/how');
    $: ogImageUrl = getPublicSiteUrl(appConfig.publicOrigin, '/img/og-image.png');

    // Simple carousel state
    let activeIndex = 0;
    const nextSlide = () => {
        activeIndex = (activeIndex + 1) % content.carousel.items.length;
    };
    const prevSlide = () => {
        activeIndex = (activeIndex - 1 + content.carousel.items.length) % content.carousel.items.length;
    };
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
    <!-- Hero Section -->
    <PublicSection>
        <div class="page-hero">
            <div class="eyebrow">
                <PublicIcon name="check" />
                {content.hero.eyebrow}
            </div>
            <h1>{content.hero.title}</h1>
            <p>{content.hero.text}</p>
        </div>
    </PublicSection>

    <!-- How It Works Section -->
    <PublicSection>
        <div class="how-it-works split">
            <div class="steps">
                {#each content.steps as step, i (step.title)}
                    <div class="step">
                        <div class="step-number">{i + 1}</div>
                        <div class="step-content">
                            <h3>{step.title}</h3>
                            <p>{step.text}</p>
                        </div>
                    </div>
                {/each}
            </div>

            <aside class="card advantage-card">
                <div class="icon-box">
                    <PublicIcon name="telegram" />
                </div>
                <h3>{content.telegramAdvantage.title}</h3>
                <p>{content.telegramAdvantage.text}</p>
                <div class="feature-list">
                    {#each content.telegramAdvantage.features as feature (feature)}
                        <div class="feature">
                            <PublicIcon name="check" />
                            <span>{feature}</span>
                        </div>
                    {/each}
                </div>
            </aside>
        </div>
    </PublicSection>

    <!-- Carousel Section -->
    <PublicSection>
        <div class="preview-carousel">
            <h2>{content.carousel.title}</h2>

            <div class="carousel-viewport">
                {#each content.carousel.items as item, i (item.image)}
                    {#if i === activeIndex}
                        <div class="carousel-slide" transition:fade={{ duration: 300 }}>
                            <img src={item.image} alt={item.caption} />
                            <p class="caption">{item.caption}</p>
                        </div>
                    {/if}
                {/each}
            </div>

            <div class="carousel-controls">
                <button on:click={prevSlide} aria-label="Previous slide">&larr;</button>
                <div class="indicators">
                    {#each content.carousel.items as _, i (i)}
                        <button
                            class:active={i === activeIndex}
                            on:click={() => activeIndex = i}
                            aria-label="Go to slide {i + 1}"
                        ></button>
                    {/each}
                </div>
                <button on:click={nextSlide} aria-label="Next slide">&rarr;</button>
            </div>
        </div>
    </PublicSection>

    <!-- Final CTA -->
    <PublicSection>
        <div class="final-cta">
            <h2>Готовы начать?</h2>
            <PublicButton
                href={getPublicSiteUrl(appConfig.publicOrigin, '/telegram')}
                variant="primary"
            >
                <PublicIcon name="telegram" />
                Открыть в Telegram
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

    .how-it-works {
        grid-template-columns: 1fr 350px;
        gap: 4rem;
    }

    .steps {
        display: flex;
        flex-direction: column;
        gap: 2.5rem;
    }

    .step {
        display: flex;
        gap: 1.5rem;
        align-items: flex-start;
    }

    .step-number {
        background: var(--public-accent);
        color: white;
        font-weight: 700;
        font-size: 1.25rem;
        width: 2.5rem;
        height: 2.5rem;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
    }

    .step-content h3 {
        margin: 0 0 0.5rem 0;
        font-size: 1.5rem;
    }

    .step-content p {
        margin: 0;
        line-height: 1.6;
        color: var(--public-text-muted);
    }

    .advantage-card h3 {
        margin: 0 0 0.5rem 0;
    }

    .advantage-card p {
        margin: 0 0 1.25rem 0;
    }

    .preview-carousel {
        text-align: center;
        max-width: 1000px;
        margin: 0 auto;
    }

    .preview-carousel h2 {
        margin-bottom: 3rem;
        font-size: clamp(2rem, 5vw, 2.5rem);
    }

    .carousel-viewport {
        position: relative;
        width: 100%;
        aspect-ratio: 16 / 9;
        max-width: 800px;
        margin: 0 auto 2rem auto;
        background: var(--public-bg-secondary);
        border-radius: 2rem;
        overflow: hidden;
        box-shadow: 0 20px 40px rgba(0,0,0,0.1);
    }

    .carousel-slide {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 2rem;
        box-sizing: border-box;
    }

    .carousel-slide img {
        width: 100%;
        max-width: 600px;
        height: auto;
        object-fit: contain;
        border-radius: 1rem;
        box-shadow: 0 10px 20px rgba(0,0,0,0.1);
    }

    .carousel-slide .caption {
        margin-top: 1.5rem;
        text-align: center;
        font-size: 1.125rem;
        font-weight: 500;
        color: var(--public-text-muted);
    }

    .carousel-controls {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 1.5rem;
    }

    .carousel-controls button {
        background: var(--public-bg-secondary);
        border: 1px solid var(--public-border);
        color: var(--public-text);
        width: 3rem;
        height: 3rem;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        transition: background 0.2s;
    }

    .carousel-controls button:hover {
        background: var(--public-border);
    }

    .carousel-controls .indicators {
        display: flex;
        gap: 0.5rem;
    }

    .carousel-controls .indicators button {
        width: 0.75rem;
        height: 0.75rem;
        border-radius: 50%;
        background: var(--public-border);
        border: none;
        padding: 0;
        cursor: pointer;
        transition: background 0.2s;
    }

    .carousel-controls .indicators button.active {
        background: var(--public-accent);
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
        .how-it-works {
            grid-template-columns: 1fr;
            gap: 3rem;
        }

        .advantage-card {
            margin-top: 0;
        }

        .steps {
            gap: 2rem;
        }
    }
</style>