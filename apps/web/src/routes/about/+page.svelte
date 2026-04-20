<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
</script>

<svelte:head>
    <title>EarnIt Kids - About</title>
    <meta name="description" content="Learn how EarnIt Kids helps families turn routine into a clear and positive experience." />
    <link rel="canonical" href="/about" />
</svelte:head>

<div class="public-shell">
    <PublicTopNav />
    <main class="public-inner" aria-label="О проекте EarnIt Kids">
        <section class="landing-hero" aria-labelledby="about-hero-title">
            <div>
                <p class="public-panel__badge">EarnIt Kids</p>
                <h1 id="about-hero-title">Мы делаем повседневные задачи позитивным маршрутом</h1>
                <p>Создаем систему, где ребенок видит список заданий с понятными действиями, а родитель управляет заявками и правилами семьи.</p>
                <div class="hero-actions">
                    <a class="btn btn--primary" href="/login.html">Войти</a>
                    <a class="btn btn--ghost" href="/faq">FAQ</a>
                </div>
            </div>
            <div>
                <img src="/img/about-family.svg" alt="Семья работает над задачами вместе" loading="lazy" />
            </div>
        </section>

        <section class="value-grid" aria-label="Основные принципы">
            <article class="value-card">
                <h3>Для ребенка</h3>
                <p>Ребенок получает прозрачный список заданий, понятные статусы заявок и заметные акценты на наградах.</p>
            </article>
            <article class="value-card">
                <h3>Контроль родителей</h3>
                <p>Раздел заявок, мастер задач и лимиты помогают спокойно управлять семейными правилами без споров.</p>
            </article>
            <article class="value-card">
                <h3>Семейный контроль</h3>
                <p>Родитель видит заявки, настраивает задания и награды, а ребенок сразу понимает, что делать дальше.</p>
            </article>
        </section>

        <section class="public-cta">
            <div>
                <h3>Готовы перевести задачи в игру?</h3>
                <p>Ребенок и родитель получают ясный маршрут: задания, раздел заявок и прозрачные статусы.</p>
            </div>
            <a class="btn btn--primary" href="/login.html">Попробовать</a>
        </section>
    </main>
</div>
