<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';

    const faqItems = [
        { q: 'How does it work?', a: 'Parents assign a task, the child completes it and earns coins. Coins can then be exchanged for rewards.' },
        { q: 'Do we need to install an app?', a: 'No, it works directly in the browser on phone and computer.' },
        { q: 'Can coin spending be limited?', a: 'Yes, parents set limits and decide which rewards are available and how often they can be claimed.' },
    ];
</script>

<svelte:head>
    <title>EarnIt Kids - Frequently Asked Questions</title>
    <meta name="description" content="Short answers for parents and kids about tasks, coins, and rewards in EarnIt Kids." />
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
                    <article class="faq-card">
                        <details>
                            <summary>
                                <span>{item.q}</span>
                                <span class="faq-card__icon">❓</span>
                            </summary>
                            <p>{item.a}</p>
                        </details>
                    </article>
                {/each}
            </div>
        </section>
        <section class="value-grid" aria-label="Что нового">
            <article class="value-card">
                <h3>Ясные статусы заявок</h3>
                <p>Статусы “ожидает”, “одобрено” и “отклонено” легко читаются по цвету и понятным подсказкам.</p>
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
