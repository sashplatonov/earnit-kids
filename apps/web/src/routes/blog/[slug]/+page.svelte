<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
    import type { PageData } from './$types';

    export let data: PageData;

    function formatDate(isoDate: string): string {
        return new Date(isoDate).toLocaleDateString('ru-RU', { year: 'numeric', month: 'long', day: 'numeric' });
    }
</script>

<svelte:head>
    <title>{data.post.title} | EarnIt Kids</title>
    <meta name="description" content={data.post.summary} />
    <link rel="canonical" href="/blog/{data.post.slug}" />
    <meta property="og:title" content={data.post.title} />
    <meta property="og:description" content={data.post.summary} />
</svelte:head>

<div class="public-shell">
    <PublicTopNav />
    <main class="public-inner">
        <article class="article-shell">
            <div class="article-image-wrap">
                <img class="article-image" src="/img/blog-reading.svg" alt="Иллюстрация к статье блога" loading="lazy" />
            </div>
            <header class="article-header">
                <h1>{data.post.title}</h1>
                <p class="meta">{formatDate(data.post.isoDate)}</p>
            </header>
            <div class="article-body">
                {@html data.post.html}
            </div>
            <div class="feature-layout__actions">
                <a class="btn btn--primary" href="/login.html">Попробовать в своей семье</a>
                <a class="btn btn--ghost" href="/blog">← Все статьи</a>
            </div>
        </article>
    </main>
</div>
