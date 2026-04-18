<script lang="ts">
    import { onMount } from 'svelte';
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
    import type { PageData } from './$types';

    type ActivePanel = 'login' | 'register' | 'forgot';
    type ResponsePayload = Record<string, unknown>;

    interface AuthConfig {
        emailVerificationEnabled: boolean;
        passwordRecoveryEnabled: boolean;
    }

    const INVALID_TOKEN_MESSAGE = 'Эта ссылка для входа больше не работает. Попросите родителей прислать новую ссылку из настроек магазина.';

    export let data: PageData;

    let activePanel: ActivePanel = 'login';
    let loginEmail = '';
    let loginPassword = '';
    let regEmail = '';
    let regPassword = '';
    let forgotEmail = '';
    let errorMsg = '';
    let successMsg = '';
    let submitting: ActivePanel | null = null;
    let emailVerificationEnabled = true;
    let passwordRecoveryEnabled = true;

    let loginEmailInput: HTMLInputElement | null = null;
    let loginPasswordInput: HTMLInputElement | null = null;

    function clearMessages() {
        errorMsg = '';
        successMsg = '';
    }

    function activeTab(): 'login' | 'register' {
        return activePanel === 'register' ? 'register' : 'login';
    }

    function readMessage(body: ResponsePayload, fallback: string) {
        if (typeof body.error === 'string' && body.error) {
            return body.error;
        }

        if (typeof body.message === 'string' && body.message) {
            return body.message;
        }

        return fallback;
    }

    function showError(message: string) {
        errorMsg = `❌ ${message}`;
        successMsg = '';
    }

    function showSuccess(message: string) {
        successMsg = `✅ ${message}`;
        errorMsg = '';
    }

    function showLoginPanel() {
        activePanel = 'login';
        clearMessages();
    }

    function showRegisterPanel() {
        activePanel = 'register';
        clearMessages();
    }

    function showForgotPanel() {
        activePanel = 'forgot';
        clearMessages();
    }

    async function postJson(path: string, payload: Record<string, string>) {
        const response = await fetch(path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            cache: 'no-store',
            body: JSON.stringify(payload),
        });

        const body = (await response.json().catch(() => ({}))) as ResponsePayload;
        return { response, body };
    }

    async function handleLogin() {
        const email = loginEmail.trim();
        const password = loginPassword;

        if (!email) {
            showError('Введите Email');
            return;
        }

        if (password.length < 6) {
            showError('Пароль должен быть не менее 6 знаков');
            return;
        }

        submitting = 'login';
        clearMessages();

        try {
            const { response, body } = await postJson('/api/login', { email, password });

            if (response.ok) {
                location.assign('/');
                return;
            }

            showError(readMessage(body, 'Ошибка входа'));
            loginPassword = '';
        } catch {
            showError('Ошибка связи с сервером');
        } finally {
            submitting = null;
        }
    }

    async function handleRegister() {
        const email = regEmail.trim();
        const password = regPassword;

        if (!email || !email.includes('@')) {
            showError('Введите корректный Email');
            return;
        }

        if (password.length < 6) {
            showError('Пароль должен быть не менее 6 символов');
            return;
        }

        submitting = 'register';
        clearMessages();

        try {
            const { response, body } = await postJson('/api/register', { email, password });

            if (response.ok) {
                if (emailVerificationEnabled) {
                    showSuccess('Семья зарегистрирована! ПРОВЕРЬТЕ ПОЧТУ для подтверждения.');
                } else {
                    showSuccess('Семья зарегистрирована! Теперь войдите.');
                }

                submitting = null;

                window.setTimeout(() => {
                    showLoginPanel();
                    loginEmail = email;
                    loginPasswordInput?.focus();
                }, 3000);
                return;
            }

            showError(readMessage(body, 'Ошибка регистрации'));
        } catch {
            showError('Ошибка связи с сервером');
        } finally {
            submitting = null;
        }
    }

    async function handleRecover() {
        const email = forgotEmail.trim();

        if (!email || !email.includes('@')) {
            showError('Введите корректный Email');
            return;
        }

        submitting = 'forgot';
        clearMessages();

        try {
            const { response, body } = await postJson('/api/forgot-password', { email });

            if (response.ok) {
                showSuccess('Ссылка на восстановление пароля на ваш Email!');
                submitting = null;

                window.setTimeout(() => {
                    showLoginPanel();
                    loginEmail = email;
                    loginEmailInput?.focus();
                }, 3000);
                return;
            }

            showError(readMessage(body, 'Ошибка восстановления'));
        } catch {
            showError('Ошибка связи с сервером');
        } finally {
            submitting = null;
        }
    }

    onMount(() => {
        const searchParams = new URLSearchParams(window.location.search);

        if (searchParams.get('error') === 'invalid_token') {
            showError(INVALID_TOKEN_MESSAGE);
        }

        loginEmailInput?.focus();

        void fetch('/api/auth-config', { credentials: 'same-origin', cache: 'no-store' })
            .then(async (response) => {
                if (!response.ok) {
                    return;
                }

                const config = (await response.json()) as Partial<AuthConfig>;

                if (typeof config.emailVerificationEnabled === 'boolean') {
                    emailVerificationEnabled = config.emailVerificationEnabled;
                }

                if (typeof config.passwordRecoveryEnabled === 'boolean') {
                    passwordRecoveryEnabled = config.passwordRecoveryEnabled;
                }
            })
            .catch(() => undefined);
    });
</script>

<svelte:head>
    <title>Вход и первые шаги | EarnIt Kids</title>
    <meta name="description" content="Войдите в EarnIt Kids — систему мотивации для детей. Настройте роли, задания и магазин без долгого обучения." />
    <meta property="og:title" content="Вход и первые шаги | EarnIt Kids" />
    <link rel="canonical" href="/login.html" />
</svelte:head>

<style>
    :root {
        --page-surface: rgba(255, 255, 255, 0.82);
        --panel-border: rgba(125, 149, 187, 0.16);
        --highlight: #ffb65c;
        --highlight-strong: #ff8f70;
        --accent: #5cc7f3;
        --text-main: #26344f;
        --muted: #667892;
    }

    .login-bg-wrapper {
        min-height: 100vh;
        background:
            radial-gradient(780px 460px at 8% 0%, rgba(92, 199, 243, 0.24), transparent 58%),
            radial-gradient(760px 520px at 92% 4%, rgba(255, 214, 107, 0.28), transparent 52%),
            radial-gradient(720px 480px at 80% 82%, rgba(255, 156, 188, 0.18), transparent 48%),
            linear-gradient(180deg, #fffdf8 0%, #f4fbff 50%, #fff4e8 100%);
        color: var(--text-main);
        font-family: 'Nunito', system-ui, sans-serif;
    }

    .login-shell {
        max-width: 1180px;
        margin: 0 auto;
        padding: 1.5rem 1.25rem 2.5rem;
        display: grid;
        gap: 1.25rem;
    }

    .login-hero,
    .login-panel,
    .onboarding-steps {
        border-radius: 1.5rem;
        border: 1px solid var(--panel-border);
        background: var(--page-surface);
        padding: 2rem;
        box-shadow: 0 28px 60px -34px rgba(99, 128, 177, 0.35);
    }

    .login-hero {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
        gap: 1.5rem;
    }

    .eyebrow {
        display: inline-flex;
        font-size: 0.8rem;
        letter-spacing: 0.2em;
        text-transform: uppercase;
        padding: 0.4rem 0.9rem;
        border-radius: 999px;
        border: 1px solid rgba(92, 199, 243, 0.3);
        background: linear-gradient(135deg, rgba(92, 199, 243, 0.14), rgba(255, 214, 107, 0.18));
        color: #547198;
        margin-bottom: 1rem;
    }

    .hero-title {
        margin: 0;
        font-size: clamp(2.2rem, 4vw, 3.2rem);
        line-height: 1.1;
    }

    .hero-subtitle {
        margin-top: 0.9rem;
        color: var(--muted);
        max-width: 52ch;
        line-height: 1.5;
    }

    .hero-actions {
        margin-top: 1.4rem;
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
    }

    .btn-ghost,
    .btn-hero {
        border-radius: 999px;
        padding: 0.75rem 1.4rem;
        font-weight: 700;
        font-size: 0.92rem;
        border: 1px solid transparent;
        text-decoration: none;
        display: inline-flex;
        align-items: center;
        justify-content: center;
    }

    .btn-hero {
        background: linear-gradient(135deg, var(--highlight), var(--highlight-strong));
        color: #ffffff;
        box-shadow: 0 16px 28px rgba(255, 143, 112, 0.28);
    }

    .btn-ghost {
        border-color: rgba(125, 149, 187, 0.22);
        color: var(--text-main);
        background: rgba(255, 255, 255, 0.82);
    }

    .hero-stats {
        margin-top: 1.5rem;
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 0.9rem;
    }

    .hero-stat {
        border-radius: 1rem;
        border: 1px solid rgba(125, 149, 187, 0.16);
        padding: 0.85rem;
        background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(244, 249, 255, 0.92));
    }

    .hero-stat strong {
        display: block;
        font-size: 1.35rem;
    }

    .hero-stat span {
        color: var(--muted);
        font-size: 0.85rem;
    }

    .role-grid {
        margin-top: 0.5rem;
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 1rem;
    }

    .role-card {
        border-radius: 1.1rem;
        padding: 1rem;
        border: 1px solid rgba(125, 149, 187, 0.16);
        background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(247, 250, 255, 0.92));
        min-height: 155px;
    }

    .role-card h3 {
        margin: 0;
        font-size: 1.05rem;
    }

    .role-card p {
        margin: 0.6rem 0 0;
        color: var(--muted);
        font-size: 0.9rem;
        line-height: 1.4;
    }

    .role-card__badge {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.3rem 0.8rem;
        border-radius: 999px;
        font-size: 0.78rem;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        border: 1px solid rgba(92, 199, 243, 0.28);
        background: rgba(255, 255, 255, 0.82);
        color: #547198;
        margin-bottom: 0.8rem;
    }

    .hero-scenarios {
        margin-top: 1.25rem;
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: 0.75rem;
    }

    .scenario-card {
        padding: 1rem;
        border-radius: 1rem;
        border: 1px solid rgba(125, 149, 187, 0.16);
        background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(246, 249, 255, 0.93));
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
    }

    .scenario-card__header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 0.65rem;
    }

    .scenario-card__title {
        margin: 0;
        font-size: 1.1rem;
    }

    .role-chip {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        padding: 0.25rem 0.75rem;
        border-radius: 999px;
        font-size: 0.75rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.1em;
        border: 1px solid rgba(125, 149, 187, 0.24);
        background: rgba(255, 255, 255, 0.9);
        color: var(--text-main);
    }

    .role-chip--parent {
        border-color: rgba(92, 199, 243, 0.48);
    }

    .role-chip--child {
        border-color: rgba(141, 223, 183, 0.58);
    }

    .scenario-card__list {
        margin: 0;
        padding-left: 1.25rem;
        color: var(--muted);
        line-height: 1.4;
    }

    .scenario-card__list li {
        margin-bottom: 0.3rem;
    }

    .scenario-card__hint {
        font-size: 0.85rem;
        color: #72839a;
    }

    .login-panel {
        display: grid;
        gap: 1rem;
    }

    .panel-header h2 {
        margin: 0;
        font-size: 1.8rem;
    }

    .panel-header p {
        margin: 0.5rem 0 0;
        color: var(--muted);
        line-height: 1.5;
    }

    .form-switch {
        display: inline-flex;
        background: rgba(255, 255, 255, 0.78);
        border-radius: 999px;
        border: 1px solid rgba(125, 149, 187, 0.18);
        padding: 0.2rem;
    }

    .form-tab-btn {
        border: none;
        background: transparent;
        color: var(--muted);
        font-weight: 700;
        padding: 0.55rem 1.4rem;
        border-radius: 999px;
        cursor: pointer;
        transition: color 0.2s ease;
    }

    .form-tab-btn.active {
        color: var(--text-main);
        background: linear-gradient(135deg, rgba(92, 199, 243, 0.15), rgba(255, 214, 107, 0.22));
    }

    .auth-forms {
        max-width: 520px;
        margin: 0 auto;
        width: 100%;
    }

    .form-frame {
        background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(247, 250, 255, 0.94));
        border-radius: 1rem;
        border: 1px solid rgba(125, 149, 187, 0.14);
        padding: 1.4rem;
        min-height: 320px;
    }

    .form-grid {
        display: grid;
        gap: 0.9rem;
    }

    .input-field {
        width: 100%;
        padding: 0.85rem 1rem;
        font-size: 1rem;
        border-radius: 0.9rem;
        border: 1px solid rgba(125, 149, 187, 0.22);
        background: rgba(255, 255, 255, 0.98);
        color: var(--text-main);
        transition: border 0.2s ease, box-shadow 0.2s ease;
        font-family: inherit;
    }

    .input-field:focus {
        outline: none;
        border-color: var(--highlight);
        box-shadow: 0 0 0 3px rgba(255, 182, 92, 0.18);
    }

    .btn-login,
    .btn-secondary,
    .link-btn {
        border: none;
        border-radius: 1rem;
        font-weight: 700;
        font-size: 1rem;
        padding: 0.9rem;
        width: 100%;
        cursor: pointer;
        font-family: inherit;
    }

    .btn-login {
        background: linear-gradient(135deg, var(--highlight), var(--highlight-strong));
        color: #ffffff;
        margin-top: 0.8rem;
        box-shadow: 0 16px 32px rgba(255, 143, 112, 0.28);
    }

    .btn-secondary {
        background: rgba(255, 255, 255, 0.82);
        border: 1px solid rgba(125, 149, 187, 0.18);
        color: var(--text-main);
        margin-top: 0.9rem;
    }

    .link-btn {
        background: transparent;
        color: var(--accent);
        border: none;
        text-align: left;
        padding: 0;
        width: auto;
    }

    .form-links {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-top: 0.75rem;
        gap: 0.75rem;
        flex-wrap: wrap;
    }

    .form-links a {
        color: var(--accent);
        text-decoration: none;
        font-weight: 700;
    }

    .messages .error-message,
    .messages .success-message {
        border-radius: 0.9rem;
        padding: 0.85rem 1rem;
        font-weight: 700;
        margin-top: 0.2rem;
    }

    .messages .error-message {
        color: #ff6b6b;
        border: 1px solid rgba(255, 107, 107, 0.3);
        background: rgba(255, 107, 107, 0.08);
    }

    .messages .success-message {
        color: #34d399;
        border: 1px solid rgba(52, 211, 153, 0.3);
        background: rgba(52, 211, 153, 0.08);
    }

    .onboarding-steps {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 1rem;
    }

    .step-card {
        border-radius: 1rem;
        padding: 1.2rem;
        border: 1px solid rgba(125, 149, 187, 0.14);
        background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(245, 249, 255, 0.9));
        min-height: 170px;
    }

    .step-index {
        width: 38px;
        height: 38px;
        border-radius: 12px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        background: linear-gradient(135deg, rgba(92, 199, 243, 0.18), rgba(255, 214, 107, 0.26));
        color: #40628d;
        font-weight: 700;
        margin-bottom: 0.7rem;
    }

    .step-card h3 {
        margin: 0;
        font-size: 1.1rem;
    }

    .step-card p {
        margin-top: 0.6rem;
        color: var(--muted);
        font-size: 0.9rem;
        line-height: 1.4;
    }

    @media (min-width: 980px) {
        .login-shell {
            grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
            align-items: start;
        }

        .login-panel,
        .login-hero,
        .onboarding-steps {
            grid-column: 1 / -1;
        }
    }

    @media (max-width: 800px) {
        .login-shell {
            padding: 1rem 1rem 2rem;
        }

        .login-panel,
        .login-hero,
        .onboarding-steps {
            padding: 1.5rem;
        }
    }

    @media (max-width: 640px) {
        .hero-stats {
            grid-template-columns: repeat(2, minmax(0, 1fr));
        }

        .form-switch {
            width: 100%;
            justify-content: space-between;
        }
    }
</style>

<div class="login-bg-wrapper">
    <PublicTopNav />

    <main class="login-shell" data-authenticated={data.session.authenticated ? 'true' : 'false'}>
        <section class="login-panel" aria-labelledby="login-panel-title">
            <div class="panel-header">
                <p class="eyebrow" style="margin-bottom:0.4rem;">Доступ и проверка</p>
                <h2 id="login-panel-title">Вход для родителей и детей</h2>
                <p>Войдите по email и паролю родителя или отправьте ребенку ссылку для входа.</p>
            </div>

            <div class="form-switch" role="tablist">
                <button
                    type="button"
                    class="form-tab-btn"
                    class:active={activeTab() === 'login'}
                    on:click={showLoginPanel}
                >
                    Вход
                </button>
                <button
                    type="button"
                    class="form-tab-btn"
                    class:active={activeTab() === 'register'}
                    on:click={showRegisterPanel}
                >
                    Регистрация
                </button>
            </div>

            <div class="auth-forms">
                <div class="form-frame">
                    {#if activePanel === 'login'}
                        <div aria-label="Форма входа">
                            <p class="hero-subtitle" style="margin-bottom: 1rem;">Войдите в аккаунт или используйте ссылку для ребенка.</p>
                            <div class="form-grid">
                                <input
                                    bind:this={loginEmailInput}
                                    bind:value={loginEmail}
                                    type="email"
                                    class="input-field"
                                    placeholder="Email"
                                    autocomplete="username"
                                    autocapitalize="none"
                                    spellcheck="false"
                                />
                                <input
                                    bind:this={loginPasswordInput}
                                    bind:value={loginPassword}
                                    type="password"
                                    class="input-field"
                                    placeholder="Пароль"
                                    autocomplete="current-password"
                                    on:keydown={(event) => event.key === 'Enter' && handleLogin()}
                                />
                            </div>
                            <button class="btn-login" on:click={handleLogin} disabled={submitting === 'login'}>
                                {submitting === 'login' ? 'Вход...' : 'Войти'}
                            </button>
                            <div class="form-links">
                                {#if passwordRecoveryEnabled}
                                    <a href="/login.html#forgot-password" on:click|preventDefault={showForgotPanel}>Восстановить пароль</a>
                                {/if}
                                <span style="color: var(--muted); font-size: 0.85rem;">или</span>
                                <button type="button" class="link-btn" on:click={showRegisterPanel}>Собери семью</button>
                            </div>
                        </div>
                    {:else if activePanel === 'register'}
                        <div aria-label="Регистрация">
                            <p class="hero-subtitle" style="margin-bottom: 1rem;">Создайте родительский аккаунт и пригласите ребенка одним нажатием.</p>
                            <div class="form-grid">
                                <input
                                    bind:value={regEmail}
                                    type="email"
                                    class="input-field"
                                    placeholder="Email родителя"
                                    autocomplete="email"
                                    autocapitalize="none"
                                    spellcheck="false"
                                />
                                <input
                                    bind:value={regPassword}
                                    type="password"
                                    class="input-field"
                                    placeholder="Пароль (мин. 6)"
                                    minlength="6"
                                    autocomplete="new-password"
                                    on:keydown={(event) => event.key === 'Enter' && handleRegister()}
                                />
                            </div>
                            <button class="btn-login" on:click={handleRegister} disabled={submitting === 'register'}>
                                {submitting === 'register' ? 'Регистрация...' : 'Зарегистрировать'}
                            </button>
                            <button type="button" class="btn-secondary" on:click={showLoginPanel}>
                                Уже есть аккаунт? Войти
                            </button>
                        </div>
                    {:else}
                        <div aria-label="Восстановление">
                            <p class="hero-subtitle" style="margin-bottom: 1rem;">Восстановите доступ и отправьте ребенку новую ссылку.</p>
                            <div class="form-grid">
                                <input
                                    bind:value={forgotEmail}
                                    type="email"
                                    class="input-field"
                                    placeholder="Email для восстановления"
                                    autocomplete="email"
                                    autocapitalize="none"
                                    spellcheck="false"
                                    on:keydown={(event) => event.key === 'Enter' && handleRecover()}
                                />
                            </div>
                            <button class="btn-login" on:click={handleRecover} disabled={submitting === 'forgot'}>
                                {submitting === 'forgot' ? 'Отправка...' : 'Отправить'}
                            </button>
                            <button type="button" class="btn-secondary" on:click={showLoginPanel}>
                                Вернуться ко входу
                            </button>
                        </div>
                    {/if}
                </div>
            </div>

            <div class="messages">
                {#if errorMsg}
                    <div class="error-message" role="alert">{errorMsg}</div>
                {/if}
                {#if successMsg}
                    <div class="success-message" role="status">{successMsg}</div>
                {/if}
            </div>
        </section>

        <section class="login-hero" aria-labelledby="login-hero-title">
            <div>
                <p class="eyebrow">Семейный старт</p>
                <h1 id="login-hero-title" class="hero-title">EarnIt Kids запускается за пару минут</h1>
                <p class="hero-subtitle">Настройте задания и награды, отправьте ссылку ребенку и отслеживайте прогресс без сложных шагов.</p>
                <div class="hero-actions">
                    <a class="btn-hero" href="/">Посмотреть ресурсы</a>
                    <a class="btn-ghost" href="/faq">Часто задаваемые вопросы</a>
                </div>
                <div class="hero-stats">
                    <div class="hero-stat">
                        <strong>3 шага</strong>
                        <span>От роли к действию</span>
                    </div>
                    <div class="hero-stat">
                        <strong>1 минуту</strong>
                        <span>На стартовую настройку</span>
                    </div>
                    <div class="hero-stat">
                        <strong>Без лишнего</strong>
                        <span>Только нужные шаги</span>
                    </div>
                </div>
            </div>

            <div>
                <div class="role-grid">
                    <article class="role-card">
                        <div class="role-card__badge">Родитель</div>
                        <h3>Настройте семью</h3>
                        <p>Создайте задания, лимиты и магазин. Получайте заявки, подтверждайте и рассказывайте ребенку, что происходит.</p>
                    </article>
                    <article class="role-card">
                        <div class="role-card__badge">Ребенок</div>
                        <h3>Игровой вход</h3>
                        <p>Ссылка для входа позволяет зайти без пароля, сразу увидеть, что нужно сделать, и выбрать награду.</p>
                    </article>
                </div>

                <div class="hero-scenarios" aria-label="Как работают роли">
                    <article class="scenario-card">
                        <div class="scenario-card__header">
                            <h3 class="scenario-card__title">Ребенок — Задания</h3>
                            <span class="role-chip role-chip--child">Ребенок</span>
                        </div>
                        <ul class="scenario-card__list">
                            <li>Список заданий с фиксированными табами и крупной карточкой.</li>
                            <li>Яркие статусы заявки: ожидает, одобрено, отклонено.</li>
                            <li>Баланс монет, быстрые награды и мгновенный отклик после действия.</li>
                        </ul>
                        <p class="scenario-card__hint">Понятный следующий шаг, минимум текста и ощущение награды каждый раз.</p>
                    </article>
                    <article class="scenario-card">
                        <div class="scenario-card__header">
                            <h3 class="scenario-card__title">Родитель — Заявки</h3>
                            <span class="role-chip role-chip--parent">Родитель</span>
                        </div>
                        <ul class="scenario-card__list">
                            <li>Заявки собраны в одном месте, одобрение и отклонение проходят быстро.</li>
                            <li>Шаблоны и мастер задач и наград помогают запускать новые привычки.</li>
                            <li>Лимиты и частота на виду, чтобы покупки оставались справедливыми.</li>
                        </ul>
                        <p class="scenario-card__hint">Режим управления умеренный, без перегруженного интерфейса.</p>
                    </article>
                </div>
            </div>
        </section>

        <section class="onboarding-steps" aria-label="Первые шаги">
            <article class="step-card">
                <div class="step-index">1</div>
                <h3>Выберите роль</h3>
                <p>Родитель и ребенок получают понятные экраны и простые инструкции.</p>
            </article>
            <article class="step-card">
                <div class="step-index">2</div>
                <h3>Настройте задания и магазин</h3>
                <p>Задайте монетки, лимиты и любимые награды. Помогите ребенку увидеть, что делать прямо сейчас.</p>
            </article>
            <article class="step-card">
                <div class="step-index">3</div>
                <h3>Отправьте ссылку ребенку</h3>
                <p>Ребенок получает ссылку и сразу видит все доступные задания с понятным следующим шагом. Все действия и статусы прозрачны.</p>
            </article>
        </section>
    </main>
</div>
