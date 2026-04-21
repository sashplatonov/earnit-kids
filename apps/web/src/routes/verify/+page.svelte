<script lang="ts">
    import { onMount } from 'svelte';
    import { useI18n } from '$lib/i18n/context';

    type MessageKind = 'loading' | 'error' | 'success';

    const i18n = useI18n();

    let message = '';
    let messageKind: MessageKind = 'loading';
    let showLoginButton = false;

    $: alternates = $i18n.alternates('/verify');
    $: if (!message && messageKind === 'loading') {
        message = $i18n.t('auth.verify.verifying');
    }

    async function verifyEmail(token: string, email: string) {
        try {
            const response = await fetch('/api/verify', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ token, email }),
            });

            const body = (await response.json().catch(() => ({}))) as Record<string, unknown>;
            const success = body.success === true || response.ok;

            if (success) {
                message = $i18n.t('auth.verify.success');
                messageKind = 'success';
                showLoginButton = true;
                return;
            }

            message = typeof body.error === 'string' ? body.error : $i18n.t('auth.verify.fallbackError');
            messageKind = 'error';
        } catch {
            message = $i18n.t('auth.verify.serverError');
            messageKind = 'error';
        }
    }

    onMount(() => {
        const urlParams = new URLSearchParams(window.location.search);
        const token = urlParams.get('token');
        const email = urlParams.get('email');

        if (!token || !email) {
            message = $i18n.t('auth.verify.invalidLink');
            messageKind = 'error';
            return;
        }

        void verifyEmail(token, email);
    });
</script>

<svelte:head>
    <title>{$i18n.t('auth.verify.metaTitle')}</title>
    <meta name="description" content={$i18n.t('auth.verify.metaDescription')} />
    <link rel="canonical" href={$i18n.href('/verify')} />
    <link rel="alternate" hreflang="en" href={alternates.en} />
    <link rel="alternate" hreflang="ru" href={alternates.ru} />
    <link rel="alternate" hreflang="x-default" href={alternates['x-default']} />
    <meta name="robots" content="noindex, nofollow" />
    <meta name="theme-color" content="#fff3e0" />
</svelte:head>

<style>
    .verify-shell {
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

    .verify-card {
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

    .message {
        margin-top: 1rem;
        padding: 10px;
        border-radius: 5px;
        font-size: 1.1rem;
    }

    .message.loading {
        color: #2196f3;
    }

    .message.error {
        background: #ffebee;
        color: #c62828;
    }

    .message.success {
        background: #e8f5e9;
        color: #2e7d32;
    }

    .btn-primary {
        display: inline-block;
        margin-top: 20px;
        padding: 0.75rem 2rem;
        border: none;
        border-radius: 2rem;
        background: linear-gradient(135deg, #ffb65c, #ff8f70);
        color: white;
        cursor: pointer;
        text-decoration: none;
        font-size: 1.1rem;
        box-shadow: 0 16px 28px rgba(255, 143, 112, 0.24);
    }
</style>

<div class="verify-shell">
    <div class="verify-card">
        <div class="logo">{$i18n.t('common.brand.name')}</div>
        <h2>{$i18n.t('auth.verify.heading')}</h2>
        <div class="message {messageKind}" role={messageKind === 'error' ? 'alert' : 'status'}>{message}</div>
        {#if showLoginButton}
            <a href={$i18n.href('/login')} class="btn-primary">{$i18n.t('auth.verify.loginCta')}</a>
        {/if}
    </div>
</div>
