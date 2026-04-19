<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
    import type { PageData } from './$types';

    export let data: PageData;

    function formatDate(isoDate: string): string {
        return new Date(isoDate).toLocaleDateString('ru-RU');
    }

    function formatTags(tags: string[] | undefined): string {
        return tags && tags.length > 0 ? tags.join(', ') : 'семейные советы';
    }
</script>

<svelte:head>
    <title>EarnIt Kids - Блог для родителей и детей</title>
    <meta name="description" content="Простые советы о семейных заданиях, монетках и добрых наградах для детей 7+." />
    <link rel="canonical" href="/blog" />
</svelte:head>

<div class="public-shell">
    <PublicTopNav />
    <main class="public-inner">
        <section class="blog-shell">
            <div class="blog-image-wrap">
                <img class="blog-image" src="/img/blog-reading.svg" alt="Родитель и ребенок читают семейные советы" loading="lazy" />
            </div>
            <header class="blog-header">
                <p class="section-label">Блог EarnIt Kids</p>
                <h1>Полезные советы для родителей и детей</h1>
                <p>Короткие статьи о том, как договориться дома, поддержать ребенка и вырастить хорошие привычки без давления.</p>
            </header>
            <section class="blog-list">
                {#each data.posts as post (post.slug)}
                    <article class="blog-card">
                        <h2><a href="/blog/{post.slug}">{post.title}</a></h2>
                        <small>{formatDate(post.isoDate)} · {formatTags(post.tags)}</small>
                        <p>{post.summary}</p>
                    </article>
                {:else}
                    <p>Статьи скоро появятся.</p>
                {/each}
            </section>
            <div class="feature-layout__actions">
                <a class="btn btn--primary" href="/login.html">Попробовать с семьей</a>
            </div>
        </section>
    </main>
</div>
