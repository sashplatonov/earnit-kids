<script lang="ts">
    let activeTab: 'login' | 'register' = 'login';
    let loginEmail = '';
    let loginPassword = '';
    let regEmail = '';
    let regPassword = '';
    let errorMsg = '';
    let loading = false;

    async function handleLogin() {
        loading = true; errorMsg = '';
        try {
            const resp = await fetch('/api/auth/login', {
                method: 'POST', credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: loginEmail, password: loginPassword }),
            });
            if (resp.ok) { window.location.href = '/'; return; }
            const body = await resp.json().catch(() => ({}));
            errorMsg = body.message || 'Неверный логин или пароль';
        } catch { errorMsg = 'Ошибка соединения'; }
        loading = false;
    }

    async function handleRegister() {
        loading = true; errorMsg = '';
        try {
            const resp = await fetch('/api/auth/register', {
                method: 'POST', credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: regEmail, password: regPassword }),
            });
            if (resp.ok) { window.location.href = '/'; return; }
            const body = await resp.json().catch(() => ({}));
            errorMsg = body.message || 'Ошибка регистрации';
        } catch { errorMsg = 'Ошибка соединения'; }
        loading = false;
    }
</script>

<svelte:head>
    <title>Вход и первые шаги | EarnIt Kids</title>
    <meta name="description" content="Войдите в EarnIt Kids — систему мотивации для детей. Настройте роли, задания и магазин без долгого обучения." />
    <meta property="og:title" content="Вход и первые шаги | EarnIt Kids" />
    <link rel="canonical" href="/login.html" />
</svelte:head>

<style>
    :root {
        --page-bg: #fffaf1;
        --page-surface: rgba(255,255,255,0.82);
        --panel-border: rgba(125,149,187,0.16);
        --highlight: #ffb65c;
        --highlight-strong: #ff8f70;
        --accent: #5cc7f3;
        --text-main: #26344f;
        --muted: #667892;
    }
    .login-bg-wrapper { min-height:100vh;background: radial-gradient(780px 460px at 8% 0%,rgba(92,199,243,.24),transparent 58%),radial-gradient(760px 520px at 92% 4%,rgba(255,214,107,.28),transparent 52%),radial-gradient(720px 480px at 80% 82%,rgba(255,156,188,.18),transparent 48%),linear-gradient(180deg,#fffdf8 0%,#f4fbff 50%,#fff4e8 100%);font-family:'Nunito',system-ui,sans-serif; }
    .login-shell { max-width:1180px;margin:0 auto;padding:1.5rem 1.25rem 2.5rem;display:grid;gap:1.25rem; }
    .eyebrow { display:inline-flex;font-size:.8rem;letter-spacing:.2em;text-transform:uppercase;padding:.4rem .9rem;border-radius:999px;border:1px solid rgba(92,199,243,.3);background:linear-gradient(135deg,rgba(92,199,243,.14),rgba(255,214,107,.18));color:#547198;margin-bottom:1rem; }
    .login-panel { border-radius:1.5rem;border:1px solid var(--panel-border);background:var(--page-surface);padding:2rem;box-shadow:0 28px 60px -34px rgba(99,128,177,.35);display:grid;gap:1rem; }
    .panel-header h2 { margin:0;font-size:1.8rem;color:var(--text-main); }
    .panel-header p { margin:.5rem 0 0;color:var(--muted);line-height:1.5; }
    .form-switch { display:inline-flex;background:rgba(255,255,255,.78);border-radius:999px;border:1px solid rgba(125,149,187,.18);padding:.2rem;gap:.2rem; }
    .form-tab-btn { border:none;background:transparent;color:var(--muted);font-weight:700;padding:.55rem 1.4rem;border-radius:999px;cursor:pointer;font-family:inherit;font-size:.95rem; }
    .form-tab-btn.active { color:var(--text-main);background:linear-gradient(135deg,rgba(92,199,243,.15),rgba(255,214,107,.22)); }
    .auth-forms { max-width:520px;margin:0 auto;width:100%; }
    .form-frame { background:linear-gradient(180deg,rgba(255,255,255,.96),rgba(247,250,255,.94));border-radius:1rem;border:1px solid rgba(125,149,187,.14);padding:1.4rem; }
    .form-grid { display:grid;gap:.9rem;margin-bottom:.5rem; }
    .input-field { width:100%;padding:.85rem 1rem;font-size:1rem;border-radius:.9rem;border:1px solid rgba(125,149,187,.22);background:rgba(255,255,255,.98);color:var(--text-main);font-family:inherit;box-sizing:border-box; }
    .input-field:focus { outline:none;border-color:var(--highlight);box-shadow:0 0 0 3px rgba(255,182,92,.18); }
    .btn-login { background:linear-gradient(135deg,var(--highlight),var(--highlight-strong));color:#fff;border:none;border-radius:1rem;font-weight:700;font-size:1rem;padding:.9rem;width:100%;cursor:pointer;margin-top:.8rem;box-shadow:0 16px 32px rgba(255,143,112,.28);font-family:inherit; }
    .btn-secondary { background:rgba(255,255,255,.82);border:1px solid rgba(125,149,187,.18);color:var(--text-main);border-radius:1rem;font-weight:700;font-size:1rem;padding:.9rem;width:100%;cursor:pointer;margin-top:.9rem;font-family:inherit; }
    .form-links { display:flex;justify-content:space-between;align-items:center;margin-top:.75rem; }
    .form-links a,.link-btn { color:var(--accent);text-decoration:none;font-weight:700;background:none;border:none;cursor:pointer;font-family:inherit;font-size:inherit; }
    .error-msg { color:#ff6b6b;border:1px solid rgba(255,107,107,.3);background:rgba(255,107,107,.08);border-radius:.9rem;padding:.85rem 1rem;font-weight:700;margin-top:.5rem; }
    .onboarding-steps { border-radius:1.5rem;border:1px solid var(--panel-border);background:var(--page-surface);padding:2rem;box-shadow:0 28px 60px -34px rgba(99,128,177,.35);display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:1rem; }
    .step-card { border-radius:1rem;padding:1.2rem;border:1px solid rgba(125,149,187,.14);background:linear-gradient(180deg,rgba(255,255,255,.96),rgba(245,249,255,.9)); }
    .step-index { width:38px;height:38px;border-radius:12px;display:inline-flex;align-items:center;justify-content:center;background:linear-gradient(135deg,rgba(92,199,243,.18),rgba(255,214,107,.26));color:#40628d;font-weight:700;margin-bottom:.7rem; }
    .step-card h3 { margin:0;font-size:1.1rem;color:var(--text-main); }
    .step-card p { margin:.6rem 0 0;color:var(--muted);font-size:.9rem;line-height:1.4; }
</style>

<div class="login-bg-wrapper">
<main class="login-shell">
    <section class="login-panel" aria-labelledby="login-panel-title">
        <div class="panel-header">
            <p class="eyebrow">Доступ и проверка</p>
            <h2 id="login-panel-title">Вход для родителей и детей</h2>
            <p>Войдите по email и паролю родителя или отправьте ребенку ссылку для входа.</p>
        </div>
        <div class="form-switch" role="tablist">
            <button type="button" class="form-tab-btn" class:active={activeTab === 'login'}
                on:click={() => { activeTab = 'login'; errorMsg = ''; }}>Вход</button>
            <button type="button" class="form-tab-btn" class:active={activeTab === 'register'}
                on:click={() => { activeTab = 'register'; errorMsg = ''; }}>Регистрация</button>
        </div>
        <div class="auth-forms">
            <div class="form-frame">
                {#if activeTab === 'login'}
                    <p style="color:var(--muted);margin-bottom:1rem">Войдите в аккаунт или используйте ссылку для ребенка.</p>
                    <div class="form-grid">
                        <input type="email" class="input-field" placeholder="Email" autocomplete="username"
                            bind:value={loginEmail} />
                        <input type="password" class="input-field" placeholder="Пароль" autocomplete="current-password"
                            bind:value={loginPassword} on:keydown={(e) => e.key === 'Enter' && handleLogin()} />
                    </div>
                    <button class="btn-login" on:click={handleLogin} disabled={loading}>
                        {loading ? 'Вхожу...' : 'Войти'}
                    </button>
                    <div class="form-links">
                        <a href="/reset-password">Восстановить пароль</a>
                        <span style="color:var(--muted);font-size:.85rem">или</span>
                        <button type="button" class="link-btn"
                            on:click={() => { activeTab = 'register'; errorMsg = ''; }}>Собери семью</button>
                    </div>
                {:else}
                    <p style="color:var(--muted);margin-bottom:1rem">Создайте родительский аккаунт и пригласите ребенка одним нажатием.</p>
                    <div class="form-grid">
                        <input type="email" class="input-field" placeholder="Email родителя" autocomplete="email"
                            bind:value={regEmail} />
                        <input type="password" class="input-field" placeholder="Пароль (мин. 6)" minlength="6"
                            autocomplete="new-password" bind:value={regPassword}
                            on:keydown={(e) => e.key === 'Enter' && handleRegister()} />
                    </div>
                    <button class="btn-login" on:click={handleRegister} disabled={loading}>
                        {loading ? 'Регистрирую...' : 'Зарегистрировать'}
                    </button>
                    <button type="button" class="btn-secondary"
                        on:click={() => { activeTab = 'login'; errorMsg = ''; }}>Уже есть аккаунт? Войти</button>
                {/if}
                {#if errorMsg}
                    <p class="error-msg" role="alert">{errorMsg}</p>
                {/if}
            </div>
        </div>
    </section>

    <section class="onboarding-steps" aria-label="Как начать">
        <div class="step-card">
            <div class="step-index">1</div>
            <h3>Зарегистрируйтесь</h3>
            <p>Создайте аккаунт родителя за 30 секунд — только email и пароль.</p>
        </div>
        <div class="step-card">
            <div class="step-index">2</div>
            <h3>Добавьте ребенка</h3>
            <p>Создайте профиль ребенка и отправьте ему ссылку для входа.</p>
        </div>
        <div class="step-card">
            <div class="step-index">3</div>
            <h3>Назначьте задания</h3>
            <p>Выберите задания из каталога или создайте свои с монетками-наградой.</p>
        </div>
    </section>
</main>
</div>
