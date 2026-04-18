<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';

    const faqItems = [
        { q: 'Как это работает?', a: 'Родитель назначает задачу, ребенок выполняет ее и зарабатывает монетки. Монетки можно обменять на награды.' },
        { q: 'Нужно ли устанавливать приложение?', a: 'Нет, работает прямо в браузере на телефоне и компьютере.' },
        { q: 'Можно ли ограничить траты монет?', a: 'Да, родители устанавливают лимиты и решают, какие награды доступны и как часто их можно получить.' },
        { q: 'Как ребенок видит свои задания?', a: 'На отдельном экране в 4 простых вкладках. Ребенок видит только свои задачи, баланс и магазин.' },
        { q: 'Что если ребенок забудет отметить задачу?', a: 'Родитель может подтвердить выполненное задание вручную в разделе заявок.' },
    ];
</script>

<svelte:head>
    <title>EarnIt Kids — Частые вопросы</title>
    <meta name="description" content="Короткие ответы для родителей и детей о задачах, монетках и наградах в EarnIt Kids." />
    <link rel="canonical" href="/faq" />
</svelte:head>

<div class="public-shell">
    <PublicTopNav />
    <main class="public-inner" aria-label="Частые вопросы">
        <section class="faq-shell" aria-labelledby="faq-title">
            <h1 id="faq-title">Вопросы и ответы</h1>
            <p class="value-card__hint">Собрали ответы на самые частые ситуации по задачам, заявкам и магазину.</p>
            <div class="faq-grid">
                {#each faqItems as item (item.q)}
                    <div class="faq-card">
                        <h3>{item.q}</h3>
                        <p>{item.a}</p>
                    </div>
                {/each}
            </div>
        </section>
        <section class="value-grid">
            <article class="value-card">
                <h3>Ясные статусы заявок</h3>
                <p>Статусы "ожидает", "одобрено" и "отклонено" легко читаются по цвету и понятным подсказкам.</p>
            </article>
            <article class="value-card">
                <h3>Заявки для родителей</h3>
                <p>Заявки по всем детям видны сразу, а одобрение или отклонение происходит в одном разделе, без поиска по меню.</p>
            </article>
            <article class="value-card">
                <h3>Мобильная навигация</h3>
                <p>4 вкладки в нижнем меню и крупные кнопки помогают детям не промахиваться и быстрее находить нужный раздел.</p>
            </article>
        </section>
        <section class="public-cta">
            <div>
                <h3>Нужна поддержка?</h3>
                <p>Пишите support@earnit-kids.com, когда нужна помощь с настройкой семьи.</p>
            </div>
            <a class="btn btn--ghost" href="/login.html">Войти и спросить</a>
        </section>
    </main>
</div>
