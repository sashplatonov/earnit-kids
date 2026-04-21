<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
    import { useI18n } from '$lib/i18n/context';
    import type { PageData } from './$types';

    export let data: PageData;

    const i18n = useI18n();

    $: alternates = $i18n.alternates('/blog');

    function formatDate(isoDate: string): string {
        return $i18n.formatShortDate(isoDate);
    }

    function formatTags(tags: string[] | undefined): string {
        return tags && tags.length > 0 ? tags.join(', ') : $i18n.t('blog.list.defaultTags');
    }
</script>

<svelte:head>
    <title>{$i18n.t('blog.list.metaTitle')}</title>
    <meta name="description" content={$i18n.t('blog.list.metaDescription')} />
    <link rel="canonical" href={$i18n.href('/blog')} />
    <link rel="alternate" hreflang="en" href={alternates.en} />
    <link rel="alternate" hreflang="ru" href={alternates.ru} />
    <link rel="alternate" hreflang="x-default" href={alternates['x-default']} />
</svelte:head>

<div class="public-shell">
    <PublicTopNav />
    <main class="public-inner">
        <section class="blog-shell">
            <div class="blog-image-wrap">
                <img class="blog-image" src="/img/blog-reading.svg" alt={$i18n.t('blog.list.imageAlt')} loading="lazy" />
            </div>
            <header class="blog-header">
                <p class="section-label">{$i18n.t('blog.list.sectionLabel')}</p>
                <h1>{$i18n.t('blog.list.title')}</h1>
                <p>{$i18n.t('blog.list.intro')}</p>
            </header>
            <section class="blog-list">
                {#each data.posts as post (post.slug)}
                    <article class="blog-card">
                        <h2><a href={$i18n.href(`/blog/${post.slug}`)}>{post.title}</a></h2>
                        <small>{formatDate(post.isoDate)} · {formatTags(post.tags)}</small>
                        <p>{post.summary}</p>
                    </article>
                {:else}
                    <p>{$i18n.t('blog.list.empty')}</p>
                {/each}
            </section>
            <div class="feature-layout__actions">
                <a class="btn btn--primary" href={$i18n.href('/login')}>{$i18n.t('common.actions.tryWithFamily')}</a>
            </div>
        </section>
    </main>
</div>
