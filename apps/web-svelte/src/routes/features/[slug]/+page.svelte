<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
    import { page } from '$app/stores';

    const featureData: Record<string, {
        title: string; description: string; heading: string; subheading: string;
        bullets: string[]; ctaText: string; ctaLink: string; image: string;
    }> = {
        tasks: {
            title: 'EarnIt Kids — Задания для детей',
            description: 'Простые задания для детей, четкие шаги для родителей и справедливые монетки за старание.',
            heading: 'Задания, которые дети выполняют с удовольствием',
            subheading: 'Ребенок видит понятную цель, а родители легко отслеживают прогресс.',
            bullets: ['Добавляйте домашние дела за пару кликов: убрать игрушки, почитать 10 минут, помочь на кухне.', 'Ребенок отмечает выполнение, а родители подтверждают результат.', 'Монетки начисляются за каждое выполненное задание.'],
            ctaText: 'Попробовать задания',
            ctaLink: '/login.html',
            image: '/img/feature-tasks.svg',
        },
        shop: {
            title: 'EarnIt Kids — Магазин наград',
            description: 'Обменивайте монетки на приятности: кино, прогулку, настолку или небольшой подарок.',
            heading: 'Магазин наград',
            subheading: 'Дети учатся копить и выбирать, а родители сохраняют контроль и бюджет.',
            bullets: ['Создавайте награды: от 20 минут игры до семейного похода в парк.', 'Устанавливайте лимиты, чтобы траты оставались разумными.', 'Просматривайте историю обменов и обсуждайте выборы с ребенком.'],
            ctaText: 'Открыть магазин наград',
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
    <main class="public-inner" aria-label="Страница функции">
        <section class="feature-layout" aria-labelledby="feature-title">
            <div class="feature-layout__content">
                <p class="public-panel__badge">EarnIt Kids</p>
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
