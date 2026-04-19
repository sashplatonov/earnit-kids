<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
    import { page } from '$app/stores';

    const featureData: Record<string, {
        title: string; description: string; heading: string; subheading: string;
        bullets: string[]; ctaText: string; ctaLink: string; image: string;
    }> = {
        tasks: {
            title: 'EarnIt Kids - Family-friendly tasks',
            description: 'Simple tasks for kids, clear steps for parents, and fair coins for effort.',
            heading: 'Tasks kids enjoy completing',
            subheading: 'Kids see a clear goal and parents can easily track progress.',
            bullets: ['Add chores in a couple of clicks: tidy toys, read for 10 minutes, help in the kitchen.', 'The child marks completion and parents confirm the result.', 'Coins are awarded for each completed task.'],
            ctaText: 'Try the tasks',
            ctaLink: '/login.html',
            image: '/img/feature-tasks.svg',
        },
        shop: {
            title: 'EarnIt Kids - Family rewards shop',
            description: 'Exchange coins for treats: a movie, a walk, a board game, or a small prize.',
            heading: 'Rewards shop',
            subheading: 'Kids learn to save and choose, parents keep control and budget.',
            bullets: ['Create rewards: from 20 minutes of play to a family trip to the park.', 'Set limits so spending stays reasonable.', 'View exchange history and discuss choices with your child.'],
            ctaText: 'Open rewards shop',
            ctaLink: '/login.html',
            image: '/img/feature-shop.svg',
        },
    };

    $: slug = $page.params.slug;
    $: feature = featureData[slug] ?? featureData.tasks;
</script>

<svelte:head>
    <title>{feature.title}</title>
    <meta name="description" content={feature.description} />
    <link rel="canonical" href="/features/{slug}" />
</svelte:head>

<div class="public-shell">
    <PublicTopNav />
    <main class="public-inner" aria-label="Сторонняя страница">
        <section class="feature-layout" aria-labelledby="feature-title">
            <div class="feature-layout__content">
                <p class="public-panel__badge">{feature.ctaText}</p>
                <h1 id="feature-title">{feature.heading}</h1>
                <p>{feature.subheading}</p>
                <p>Ребенок и родитель получают понятные шаги и не тратят время на лишние действия.</p>
                <div class="feature-layout__actions">
                    <a class="btn btn--primary" href={feature.ctaLink}>{feature.ctaText}</a>
                    <a class="btn btn--ghost" href="/login.html">Войти</a>
                </div>
            </div>
            <div class="feature-layout__panel">
                <img src={feature.image} alt={feature.heading} loading="lazy" />
                <p>{feature.description}</p>
                <ul class="feature-bullets">
                    {#each feature.bullets as bullet (bullet)}
                        <li>{bullet}</li>
                    {/each}
                </ul>
            </div>
        </section>
        <section class="value-grid" aria-label="Сценарии для семьи">
            <article class="value-card">
                <h3>Ребенок: Задания</h3>
                <p>4 вкладки, прогресс, крупные карточки задач и анимации наград без перегрузки.</p>
            </article>
            <article class="value-card">
                <h3>Родитель: Заявки</h3>
                <p>Заявки, быстрые действия и мастер задач/награды — всё ровно в два клика.</p>
            </article>
            <article class="value-card">
                <h3>Семья</h3>
                <p>Родитель быстро управляет правилами, а ребенок видит понятные цели и шаги.</p>
            </article>
        </section>
    </main>
</div>
