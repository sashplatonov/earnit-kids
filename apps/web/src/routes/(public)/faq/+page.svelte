<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { getPublicSiteUrl } from '$lib/services/publicSiteUrl';
    import type { PageData } from './$types';
    import * as content from '$lib/content/public/ru/faq';
    import PublicSection from '$lib/components/public/PublicSection.svelte';

    export let data: PageData;

    const i18n = useI18n();
    $: appConfig = data.appConfig;
    $: canonicalUrl = getPublicSiteUrl(appConfig.publicOrigin, '/faq');
</script>

<svelte:head>
    <title>{content.meta.title}</title>
    <meta name="description" content={content.meta.description} />
    <link rel="canonical" href={canonicalUrl} />
</svelte:head>

<div class="public-page">
    <PublicSection>
        <div class="hero">
            <h1>Вопросы, которые обычно возникают сначала</h1>
            <p>{content.intro}</p>
        </div>
    </PublicSection>

    <PublicSection>
        <div class="faq-list">
            {#each content.items as item, i (item.question)}
                <details class="faq-item" open={i === 0}>
                    <summary>{item.question}</summary>
                    <p>{item.answer}</p>
                </details>
            {/each}
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

    .hero h1 {
        font-size: clamp(2rem, 8vw, 3rem);
        margin: 0 0 1rem 0;
        line-height: 1.1;
    }

    .hero p {
        font-size: clamp(1.125rem, 3vw, 1.25rem);
        line-height: 1.6;
        color: var(--public-text-muted);
    }

    .faq-list {
        max-width: 720px;
        margin: 0 auto;
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
    }

    .faq-item {
        background: var(--public-bg-secondary);
        border: 1px solid var(--public-border);
        border-radius: 1rem;
        padding: 0.5rem 1.25rem;
    }

    .faq-item summary {
        cursor: pointer;
        padding: 1rem 0;
        font-weight: 600;
        font-size: 1.0625rem;
        list-style: none;
        position: relative;
        padding-right: 2rem;
    }

    .faq-item summary::-webkit-details-marker {
        display: none;
    }

    .faq-item summary::after {
        content: '+';
        position: absolute;
        right: 0;
        top: 50%;
        transform: translateY(-50%);
        font-size: 1.5rem;
        font-weight: 400;
        color: var(--public-accent);
        transition: transform 0.2s;
    }

    .faq-item[open] summary::after {
        transform: translateY(-50%) rotate(45deg);
    }

    .faq-item p {
        margin: 0;
        padding: 0 0 1rem 0;
        line-height: 1.6;
        color: var(--public-text-muted);
    }
</style>