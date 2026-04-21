<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
    import { useI18n } from '$lib/i18n/context';
    import type { PageData } from './$types';

    export let data: PageData;

    const i18n = useI18n();

    $: alternates = $i18n.alternates(`/blog/${data.post.slug}`);

    function formatDate(isoDate: string): string {
        return $i18n.formatDate(isoDate, { year: 'numeric', month: 'long', day: 'numeric' });
    }
</script>

<svelte:head>
    <title>{data.post.title} | EarnIt Kids</title>
    <meta name="description" content={data.post.summary} />
    <link rel="canonical" href={$i18n.href(`/blog/${data.post.slug}`)} />
    <link rel="alternate" hreflang="en" href={alternates.en} />
    <link rel="alternate" hreflang="ru" href={alternates.ru} />
    <link rel="alternate" hreflang="x-default" href={alternates['x-default']} />
    <meta property="og:title" content={data.post.title} />
    <meta property="og:description" content={data.post.summary} />
</svelte:head>

<div class="public-shell">
    <PublicTopNav />
    <main class="public-inner">
        <article class="article-shell">
            <div class="article-image-wrap">
                <img class="article-image" src="/img/blog-reading.svg" alt={$i18n.t('blog.article.imageAlt')} loading="lazy" />
            </div>
            <header class="article-header">
                <h1>{data.post.title}</h1>
                <p class="meta">{formatDate(data.post.isoDate)}</p>
            </header>
            <div class="article-body">
                {@html data.post.html}
            </div>
            <div class="feature-layout__actions">
                <a class="btn btn--primary" href={$i18n.href('/login')}>{$i18n.t('blog.article.ctaText')}</a>
                <a class="btn btn--ghost" href={$i18n.href('/blog')}>← {$i18n.t('common.actions.allArticles')}</a>
            </div>
        </article>
    </main>
</div>
