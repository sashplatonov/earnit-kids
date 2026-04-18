<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
</script>

<svelte:head>
    <title>Сброс пароля | EarnIt Kids</title>
    <meta name="description" content="Восстановите доступ к вашему аккаунту EarnIt Kids." />
</svelte:head>

<div class="public-shell">
    <PublicTopNav />
    <main class="public-inner" aria-label="Сброс пароля">
        <section class="public-panel">
            <p class="public-panel__badge">EarnIt Kids</p>
            <h1>Сброс пароля</h1>
            <p>Введите адрес электронной почты, и мы пришлем ссылку для сброса пароля.</p>
            <form method="POST" action="/api/auth/reset-password" style="margin-top:1.5rem;display:flex;flex-direction:column;gap:1rem;max-width:28rem">
                <label>
                    <span style="display:block;font-weight:700;margin-bottom:0.35rem">Email</span>
                    <input class="input-field" type="email" name="email" required autocomplete="email"
                        placeholder="your@email.com"
                        style="width:100%;padding:0.75rem 1rem;border-radius:12px;border:1px solid var(--public-border);background:rgba(255,255,255,0.8);font-size:1rem;font-family:inherit" />
                </label>
                <button class="btn btn--primary" type="submit">Отправить ссылку</button>
            </form>
            <p style="margin-top:1rem"><a href="/login.html">← Назад к входу</a></p>
        </section>
    </main>
</div>
