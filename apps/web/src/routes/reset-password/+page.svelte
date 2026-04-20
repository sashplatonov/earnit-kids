<script lang="ts">
    import { onMount } from 'svelte';

    type MessageKind = 'error' | 'success' | 'neutral';

    let token = '';
    let email = '';
    let password = '';
    let confirmPassword = '';
    let formVisible = true;
    let submitting = false;
    let message = '';
    let messageKind: MessageKind = 'neutral';

    function showMessage(text: string, kind: MessageKind) {
        message = text;
        messageKind = kind;
    }

    async function handleSubmit(event: SubmitEvent) {
        event.preventDefault();

        if (!token || !email) {
            formVisible = false;
            showMessage('Неверная ссылка для сброса пароля.', 'error');
            return;
        }

        if (password !== confirmPassword) {
            showMessage('Пароли не совпадают', 'error');
            return;
        }

        submitting = true;

        try {
            const response = await fetch('/api/reset-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ token, email, password }),
            });

            const body = (await response.json().catch(() => ({}))) as Record<string, unknown>;
            const success = body.success === true || response.ok;

            if (success) {
                formVisible = false;
                showMessage('Пароль успешно изменен! Перенаправление...', 'success');
                window.setTimeout(() => {
                    window.location.href = '/login.html';
                }, 2000);
                return;
            }

            showMessage(typeof body.error === 'string' ? body.error : 'Ошибка смены пароля', 'error');
        } catch {
            showMessage('Ошибка сервера', 'error');
        } finally {
            submitting = false;
        }
    }

    onMount(() => {
        const urlParams = new URLSearchParams(window.location.search);
        token = urlParams.get('token') ?? '';
        email = urlParams.get('email') ?? '';

        if (!token || !email) {
            formVisible = false;
            showMessage('Неверная ссылка для сброса пароля.', 'error');
        }
    });
</script>

<svelte:head>
    <title>Сброс пароля - EarnIt Kids</title>
    <meta name="description" content="Сброс пароля для аккаунта в EarnIt Kids." />
    <link rel="canonical" href="/reset-password" />
    <meta name="robots" content="noindex, nofollow" />
    <meta name="theme-color" content="#fff3e0" />
</svelte:head>

<style>
    .reset-shell {
        min-height: 100vh;
        display: flex;
        justify-content: center;
        align-items: center;
        padding: 1rem;
        font-family: 'Open Sans', sans-serif;
        background:
            radial-gradient(620px 360px at 10% 0%, rgba(92, 199, 243, 0.18), transparent 58%),
            radial-gradient(560px 320px at 100% 0%, rgba(255, 214, 107, 0.24), transparent 52%),
            linear-gradient(180deg, #fffdf8 0%, #f5fbff 52%, #fff4e8 100%);
    }

    .login-card {
        width: 100%;
        max-width: 400px;
        padding: 2rem;
        text-align: center;
        border-radius: 1.5rem;
        border: 1px solid rgba(125, 149, 187, 0.16);
        background: rgba(255, 255, 255, 0.94);
        box-shadow: 0 24px 48px rgba(110, 136, 184, 0.14);
    }

    .logo {
        margin-bottom: 2rem;
        color: #ffb65c;
        font-size: 2rem;
        font-family: 'Fredoka One', cursive;
        text-shadow: 2px 2px #ffd86f;
    }

    .input-group {
        margin-bottom: 1.5rem;
        text-align: left;
    }

    .input-group label {
        display: block;
        margin-bottom: 0.5rem;
        color: #4f6280;
    }

    .input-group input {
        width: 100%;
        padding: 0.75rem;
        border: 1px solid rgba(125, 149, 187, 0.22);
        border-radius: 0.85rem;
        background: #ffffff;
        color: #26344f;
        font-size: 1rem;
        box-sizing: border-box;
        font-family: inherit;
    }

    .btn-primary {
        width: 100%;
        padding: 0.75rem 2rem;
        border: none;
        border-radius: 2rem;
        background: linear-gradient(135deg, #ffb65c, #ff8f70);
        color: white;
        cursor: pointer;
        font-size: 1.1rem;
        box-shadow: 0 16px 28px rgba(255, 143, 112, 0.24);
    }

    .btn-primary:active {
        transform: scale(0.98);
    }

    .message {
        margin-top: 1rem;
        padding: 10px;
        border-radius: 5px;
    }

    .message.error {
        background: #ffebee;
        color: #c62828;
    }

    .message.success {
        background: #e8f5e9;
        color: #2e7d32;
    }

    .message.neutral {
        display: none;
    }

    .back-link {
        margin-top: 1rem;
    }

    .back-link a {
        color: #4a5568;
        text-decoration: none;
    }
</style>

<div class="reset-shell">
    <div class="login-card">
        <div class="logo">🪙 Coins Shop</div>
        <h2>Новый пароль</h2>

        {#if formVisible}
            <form on:submit={handleSubmit}>
                <div class="input-group">
                    <label for="password">Новый пароль</label>
                    <input
                        id="password"
                        bind:value={password}
                        type="password"
                        required
                        minlength="6"
                        placeholder="Минимум 6 символов"
                    />
                </div>

                <div class="input-group">
                    <label for="confirmPassword">Подтвердите пароль</label>
                    <input
                        id="confirmPassword"
                        bind:value={confirmPassword}
                        type="password"
                        required
                        minlength="6"
                        placeholder="Повторите пароль"
                    />
                </div>

                <button type="submit" class="btn-primary" disabled={submitting}>
                    {submitting ? 'Сохранение...' : 'Сохранить'}
                </button>
            </form>
        {/if}

        <div class="message {messageKind}" role={messageKind === 'error' ? 'alert' : 'status'}>{message}</div>

        <p class="back-link">
            <a href="/login.html">Вернуться ко входу</a>
        </p>
    </div>
</div>
