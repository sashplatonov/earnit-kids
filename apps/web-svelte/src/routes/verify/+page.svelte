<script lang="ts">
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
</script>

<svelte:head>
    <title>Подтверждение | EarnIt Kids</title>
    <meta name="description" content="Подтверждение аккаунта EarnIt Kids." />
</svelte:head>

<div class="public-shell">
    <PublicTopNav />
    <main class="public-inner" aria-label="Подтверждение">
        <section class="public-panel">
            <p class="public-panel__badge">EarnIt Kids</p>
            <h1>Подтверждение аккаунта</h1>
            <p>Ссылка проверяется... Если ничего не происходит — попробуйте войти заново.</p>
            <div style="margin-top:1.5rem">
                <a class="btn btn--primary" href="/login.html">Перейти к входу</a>
            </div>
        </section>
    </main>
</div>
