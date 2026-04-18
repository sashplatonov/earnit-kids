<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
    import AppShell from '$lib/components/app/AppShell.svelte';
    import type { PageData } from './$types';

    export let data: PageData;
</script>

<svelte:head>
    <title>EarnIt Kids - Family tasks and rewards</title>
    <meta name="description" content="Helps children 7+ complete helpful tasks with engagement, and makes it easy for parents to maintain order without conflict." />
    <link rel="canonical" href="/" />
    <meta property="og:title" content="EarnIt Kids - Family tasks and rewards" />
    <meta property="og:description" content="Helps children 7+ complete helpful tasks with engagement, and makes it easy for parents to maintain order without conflict." />
    <meta property="og:image" content="/img/og-image.png" />
</svelte:head>

{#if !data.session.authenticated}
<div class="public-shell">
    <PublicTopNav />
    <main class="public-inner" aria-label="Страница earnit kids">
        <section class="landing-hero" aria-labelledby="landing-hero-title">
            <div class="landing-hero__copy">
                <p class="public-panel__badge">EarnIt Kids</p>
                <h1 id="landing-hero-title">Делаем полезные привычки понятными и веселыми</h1>
                <p>Ребенок получает понятный список заданий со следующим шагом, а родители видят заявки и мастер задач. Всего 3 шага до новой семейной привычки.</p>
                <div class="hero-actions">
                    <a class="btn btn--primary" href="/login.html">Начать вместе</a>
                    <a class="btn btn--ghost" href="/about">Узнать как</a>
                </div>
                <div class="hero-impact" aria-label="Ключевые показатели">
                    <div class="hero-impact__item">
                        <strong>7+</strong>
                        <span>возраст, для которого понятно</span>
                    </div>
                    <div class="hero-impact__item">
                        <strong>3 шага</strong>
                        <span>от цели до награды</span>
                    </div>
                    <div class="hero-impact__item">
                        <strong>0 ссор</strong>
                        <span>когда правила прозрачны</span>
                    </div>
                </div>
            </div>
            <div class="landing-hero__visual">
                <img class="landing-hero__illustration" src="/img/landing-family.svg" alt="Семья планирует задания и награды в светлом интерфейсе" loading="lazy" />
                <div class="landing-hero__visual-note">
                    <div>
                        <strong>Спокойная яркая палитра</strong>
                        <p>Светлый фон, теплые акценты и крупные карточки помогают детям быстрее находить действие и не уставать от экрана.</p>
                    </div>
                </div>
                <div class="landing-hero__panel">
                    <p class="value-card__hint">Ребенок видит, что нужно сделать и за что получит монетки. Родители подтверждают заявки, а магазин вдохновляет на копление.</p>
                    <div class="value-grid" style="margin-top: 1.5rem;">
                        <article class="value-card">
                            <h3>Задачи по шаблонам</h3>
                            <p>Подберите дом, учебу и эмпатичные дела. Есть шаблоны, которые сразу можно скопировать.</p>
                        </article>
                        <article class="value-card">
                            <h3>Магазин семьи</h3>
                            <p>Сделайте товары понятными: тип, лимит и состояние "можно купить/нужно запросить".</p>
                        </article>
                        <article class="value-card">
                            <h3>Прогресс и мотивация</h3>
                            <p>Уровни, награды и короткие истории по каждому действию — чтобы ребенок видел результат.</p>
                        </article>
                    </div>
                </div>
            </div>
        </section>

        <section class="value-grid">
            <article class="value-card">
                <h3>Для детей</h3>
                <p>4 фиксированные вкладки, список заданий с прогрессом и крупные зоны нажатия — детский интерфейс без лишнего.</p>
            </article>
            <article class="value-card">
                <h3>Для родителей</h3>
                <p>Раздел заявок, быстрые действия в карточках и мастер задач и наград помогают решать все в пару кликов.</p>
            </article>
            <article class="value-card">
                <h3>Для семьи</h3>
                <p>Родители и дети работают в одном приложении, где каждый видит только нужные действия и статусы.</p>
            </article>
        </section>

        <section class="public-panel">
            <div>
                <p class="public-panel__badge">Современный стиль</p>
                <h3>Структурированный дизайн по ролям</h3>
                <p>На экранах есть только нужные действия и понятные подсказки, чтобы дети и родители не терялись.</p>
            </div>
            <div class="feature-points">
                <div class="feature-point">
                    <h3>Мобильный фокус</h3>
                    <p>Нижняя навигация на 4 вкладки, крупные карточки и плавный отклик на действия.</p>
                </div>
                <div class="feature-point">
                    <h3>Разделы по ролям</h3>
                    <p>Список заданий для ребенка и раздел заявок для родителя сбалансированы по целям.</p>
                </div>
                <div class="feature-point">
                    <h3>Единый стиль</h3>
                    <p>Все экраны выглядят одинаково понятно, поэтому пользоваться сайтом проще и быстрее.</p>
                </div>
            </div>
            <div class="feature-layout__actions">
                <a class="btn btn--primary" href="/features/tasks">Смотреть сценарии задач</a>
                <a class="btn btn--ghost" href="/features/shop">Посмотреть магазин</a>
            </div>
        </section>

        <section class="public-cta">
            <div>
                <h3>Готовы собрать семью в одной игре?</h3>
                <p>Ребенок видит все задачи в одном месте, а родитель быстро обрабатывает заявки и управляет правилами семьи.</p>
            </div>
            <a class="btn btn--primary" href="/login.html">Присоединиться</a>
        </section>
    </main>
</div>
{:else}
<!-- W-005: Authenticated family shell -->
<AppShell session={data.session} />
{/if}
