<script lang="ts">
    import { onMount } from 'svelte';
    import PublicTopNav from '$lib/components/PublicTopNav.svelte';
    import { GOOGLE_LOGIN_NETWORK_ERROR, GOOGLE_LOGIN_URL_UNAVAILABLE, requestGoogleLoginUrl } from '$lib/auth/googleOAuth';
    import { useI18n } from '$lib/i18n/context';
    import { fetchWithCsrf, loginWithEmail, selectFamily } from '$lib/services/api';
    import type { FamilyChoice } from '$lib/types/auth';
    import type { PageData } from './$types';

    type ActivePanel = 'login' | 'register' | 'forgot' | 'choose-family';
    type ResponsePayload = Record<string, unknown>;

    interface AuthConfig {
        emailVerificationEnabled: boolean;
        passwordRecoveryEnabled: boolean;
        googleEnabled?: boolean;
        googleClientId?: string | null;
    }

    export let data: PageData;

    const i18n = useI18n();

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
    let googleAuthEnabled = false;
    let showLoginPassword = false;
    let showRegisterPassword = false;
    let pendingLoginEmail = '';
    let pendingFamilyChoices: FamilyChoice[] = [];
    let chooserError = '';
    let chooserSubmittingFamilyId: string | null = null;

    $: alternates = $i18n.alternates('/login');

    let loginEmailInput: HTMLInputElement | null = null;
    let loginPasswordInput: HTMLInputElement | null = null;

    function clearMessages() {
        errorMsg = '';
        successMsg = '';
    }

    function activeTab(): 'login' | 'register' | null {
        if (activePanel === 'register') {
            return 'register';
        }

        if (activePanel === 'login') {
            return 'login';
        }

        return null;
    }

    function permissionLabel(permission: FamilyChoice['permission']) {
        switch (permission) {
            case 'viewer':
                return $i18n.t('auth.login.permissionViewer');
            case 'editor':
                return $i18n.t('auth.login.permissionEditor');
            case 'family_admin':
                return $i18n.t('auth.login.permissionFamilyAdmin');
            default:
                return permission;
        }
    }

    function clearChooserState() {
        pendingLoginEmail = '';
        pendingFamilyChoices = [];
        chooserError = '';
        chooserSubmittingFamilyId = null;
    }

    function showFamilyChooser(email: string, choices: FamilyChoice[]) {
        pendingLoginEmail = email;
        pendingFamilyChoices = choices;
        chooserError = '';
        chooserSubmittingFamilyId = null;
        activePanel = 'choose-family';
        errorMsg = '';
        successMsg = '';
        loginPassword = '';
    }

    function showLoginPanel() {
        activePanel = 'login';
        showLoginPassword = false;
        clearMessages();
        clearChooserState();
    }

    function showRegisterPanel() {
        activePanel = 'register';
        showRegisterPassword = false;
        clearMessages();
        clearChooserState();
    }

    function showForgotPanel() {
        activePanel = 'forgot';
        clearMessages();
        clearChooserState();
    }

    function showChooserBackToLogin() {
        showLoginPanel();
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

    async function postJson(path: string, payload: Record<string, string>) {
        const response = await fetchWithCsrf(path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
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
            showError($i18n.t('auth.login.emailRequired'));
            return;
        }

        if (password.length < 6) {
            showError($i18n.t('auth.login.passwordTooShort'));
            return;
        }

        submitting = 'login';
        clearMessages();

        try {
            const result = await loginWithEmail(email, password);

            if (result.ok) {
                const payload = result.data;
                if (payload?.selectionRequired && Array.isArray(payload.familyChoices) && payload.familyChoices.length > 0) {
                    showFamilyChooser(email, payload.familyChoices);
                    return;
                }

                if (payload?.selectionRequired) {
                    showError($i18n.t('auth.login.chooserError'));
                    return;
                }

                location.assign($i18n.href('/app'));
                return;
            }

            showError(result.error || $i18n.t('auth.login.loginError'));
            loginPassword = '';
        } catch {
            showError($i18n.t('auth.login.loginNetworkError'));
        } finally {
            submitting = null;
        }
    }

    async function handleFamilyChoice(choice: FamilyChoice) {
        chooserError = '';
        chooserSubmittingFamilyId = choice.familyId;

        try {
            const result = await selectFamily(pendingLoginEmail, choice.familyId);

            if (result.ok) {
                location.assign($i18n.href('/app'));
                return;
            }

            chooserError = result.error || $i18n.t('auth.login.chooserError');
        } catch {
            chooserError = $i18n.t('auth.login.chooserNetworkError');
        } finally {
            chooserSubmittingFamilyId = null;
        }
    }

    async function handleGoogleLogin() {
        if (!googleAuthEnabled) {
            showError($i18n.t('auth.login.googleUnavailable'));
            return;
        }

        submitting = 'login';
        clearMessages();

        try {
            const redirectTo = $i18n.href('/app');
            const loginUrl = await requestGoogleLoginUrl(fetch, redirectTo);
            location.assign(loginUrl);
            return;
        } catch (error) {
            if (error instanceof Error) {
                if (error.message === GOOGLE_LOGIN_NETWORK_ERROR) {
                    showError($i18n.t('auth.login.loginNetworkError'));
                    return;
                }

                if (error.message === GOOGLE_LOGIN_URL_UNAVAILABLE) {
                    showError($i18n.t('auth.login.googleError'));
                    return;
                }

                showError(error.message);
                return;
            }

            showError($i18n.t('auth.login.googleError'));
        } finally {
            submitting = null;
        }
    }

    async function handleRegister() {
        const email = regEmail.trim();
        const password = regPassword;

        if (!email || !email.includes('@')) {
            showError($i18n.t('auth.login.registerEmailInvalid'));
            return;
        }

        if (password.length < 6) {
            showError($i18n.t('auth.login.registerPasswordTooShort'));
            return;
        }

        submitting = 'register';
        clearMessages();

        try {
            const { response, body } = await postJson('/api/register', { email, password });

            if (response.ok) {
                if (emailVerificationEnabled) {
                    showSuccess($i18n.t('auth.login.registerSuccessVerify'));
                } else {
                    showSuccess($i18n.t('auth.login.registerSuccessDirect'));
                }

                submitting = null;

                window.setTimeout(() => {
                    showLoginPanel();
                    loginEmail = email;
                    loginPasswordInput?.focus();
                }, 3000);
                return;
            }

            showError(readMessage(body, $i18n.t('auth.login.registerError')));
        } catch {
            showError($i18n.t('auth.login.loginNetworkError'));
        } finally {
            submitting = null;
        }
    }

    async function handleRecover() {
        const email = forgotEmail.trim();

        if (!email || !email.includes('@')) {
            showError($i18n.t('auth.login.recoverEmailInvalid'));
            return;
        }

        submitting = 'forgot';
        clearMessages();

        try {
            const { response, body } = await postJson('/api/forgot-password', { email });

            if (response.ok) {
                showSuccess($i18n.t('auth.login.recoverSuccess'));
                submitting = null;

                window.setTimeout(() => {
                    showLoginPanel();
                    loginEmail = email;
                    loginEmailInput?.focus();
                }, 3000);
                return;
            }

            showError(readMessage(body, $i18n.t('auth.login.recoverError')));
        } catch {
            showError($i18n.t('auth.login.loginNetworkError'));
        } finally {
            submitting = null;
        }
    }

    onMount(() => {
        const searchParams = new URLSearchParams(window.location.search);

        switch (searchParams.get('error')) {
            case 'invalid_token':
                showError($i18n.t('auth.login.invalidToken'));
                break;
            case 'oauth_state_mismatch':
                showError($i18n.t('auth.login.googleStateError'));
                break;
            case 'google_exchange_failed':
                showError($i18n.t('auth.login.googleExchangeError'));
                break;
            case 'authentication_failed':
                showError($i18n.t('auth.login.googleError'));
                break;
            default:
                break;
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

                if (config.googleEnabled === true) {
                    googleAuthEnabled = true;
                }
            })
            .catch(() => undefined);
    });
</script>

<svelte:head>
    <title>{$i18n.t('auth.login.metaTitle')}</title>
    <meta name="description" content={$i18n.t('auth.login.metaDescription')} />
    <meta property="og:title" content={$i18n.t('auth.login.metaTitle')} />
    <link rel="canonical" href={$i18n.href('/login')} />
    <link rel="alternate" hreflang="en" href={alternates.en} />
    <link rel="alternate" hreflang="ru" href={alternates.ru} />
    <link rel="alternate" hreflang="x-default" href={alternates['x-default']} />
</svelte:head>

<style>
    :root {
        --page-surface: rgba(255, 250, 244, 0.86);
        --panel-border: rgba(142, 111, 82, 0.14);
        --highlight: #d59b57;
        --highlight-strong: #b96d56;
        --accent: #5f8a9d;
        --text-main: #2b3550;
        --muted: #6b778d;
    }

    .login-bg-wrapper {
        min-height: 100vh;
        background:
            radial-gradient(780px 460px at 8% 0%, rgba(194, 166, 126, 0.16), transparent 58%),
            radial-gradient(760px 520px at 92% 4%, rgba(214, 151, 111, 0.14), transparent 52%),
            radial-gradient(720px 480px at 80% 82%, rgba(129, 165, 152, 0.12), transparent 48%),
            linear-gradient(180deg, #fffdf8 0%, #f8f1e7 50%, #fcf6ef 100%);
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
        border-radius: 1.75rem;
        border: 1px solid var(--panel-border);
        background: var(--page-surface);
        padding: 2rem;
        box-shadow: 0 30px 70px -42px rgba(77, 62, 49, 0.32);
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
        border: 1px solid rgba(142, 111, 82, 0.12);
        padding: 0.95rem;
        background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(251, 245, 238, 0.92));
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
        border: 1px solid rgba(142, 111, 82, 0.12);
        background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(250, 244, 236, 0.92));
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
        border: 1px solid rgba(142, 111, 82, 0.12);
        background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(250, 244, 236, 0.93));
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
        background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(250, 244, 236, 0.94));
        border-radius: 1.2rem;
        border: 1px solid rgba(142, 111, 82, 0.12);
        padding: 1.4rem;
        min-height: 320px;
    }

    .form-grid {
        display: grid;
        gap: 0.9rem;
    }

    .google-auth {
        display: grid;
        gap: 0.85rem;
        margin-bottom: 1rem;
    }

    .google-auth__cta {
        margin-top: 0;
    }

    .google-auth__hint {
        margin: 0;
        color: var(--muted);
        font-size: 0.88rem;
        line-height: 1.45;
    }

    .chooser-shell {
        display: grid;
        gap: 0.9rem;
    }

    .chooser-shell__header {
        display: grid;
        gap: 0.45rem;
    }

    .chooser-shell__eyebrow {
        margin: 0;
        font-size: 0.78rem;
        text-transform: uppercase;
        letter-spacing: 0.14em;
        color: var(--accent);
        font-weight: 700;
    }

    .chooser-shell__title {
        margin: 0;
        font-size: 1.35rem;
        line-height: 1.2;
    }

    .chooser-shell__text {
        margin: 0;
        color: var(--muted);
        line-height: 1.5;
    }

    .chooser-shell__error {
        border-radius: 0.9rem;
        padding: 0.85rem 1rem;
        font-weight: 700;
        color: #ff6b6b;
        border: 1px solid rgba(255, 107, 107, 0.3);
        background: rgba(255, 107, 107, 0.08);
    }

    .chooser-shell__list {
        display: grid;
        gap: 0.75rem;
    }

    .chooser-item {
        border: 1px solid rgba(142, 111, 82, 0.14);
        background: rgba(255, 255, 255, 0.92);
        border-radius: 1rem;
        padding: 1rem 1.1rem;
        width: 100%;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
        text-align: left;
        cursor: pointer;
        color: var(--text-main);
        transition: transform 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
    }

    .chooser-item:hover,
    .chooser-item:focus-visible {
        transform: translateY(-1px);
        border-color: rgba(185, 109, 86, 0.34);
        box-shadow: 0 14px 28px -22px rgba(77, 62, 49, 0.32);
        outline: none;
    }

    .chooser-item--loading {
        opacity: 0.72;
        cursor: progress;
    }

    .chooser-item__copy {
        display: grid;
        gap: 0.2rem;
    }

    .chooser-item__copy strong {
        font-size: 1rem;
    }

    .chooser-item__copy span {
        color: var(--muted);
        font-size: 0.86rem;
    }

    .chooser-item__meta {
        display: grid;
        justify-items: end;
        gap: 0.25rem;
        text-align: right;
    }

    .chooser-item__permission {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        padding: 0.3rem 0.7rem;
        border-radius: 999px;
        font-size: 0.74rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        background: rgba(92, 199, 243, 0.12);
        color: #547198;
    }

    .chooser-item__action {
        color: var(--accent);
        font-size: 0.82rem;
        font-weight: 700;
    }

    .google-auth__divider {
        display: flex;
        align-items: center;
        gap: 0.8rem;
        color: var(--muted);
        font-size: 0.82rem;
        font-weight: 700;
        letter-spacing: 0.04em;
        text-transform: uppercase;
    }

    .google-auth__divider::before,
    .google-auth__divider::after {
        content: '';
        flex: 1 1 auto;
        height: 1px;
        background: rgba(125, 149, 187, 0.18);
    }

    .input-field {
        width: 100%;
        padding: 0.85rem 1rem;
        font-size: 1rem;
        border-radius: 0.9rem;
        border: 1px solid rgba(142, 111, 82, 0.18);
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
        box-shadow: 0 16px 32px rgba(185, 109, 86, 0.24);
    }

    .btn-secondary {
        background: rgba(255, 255, 255, 0.82);
        border: 1px solid rgba(142, 111, 82, 0.16);
        color: var(--text-main);
        margin-top: 0.9rem;
    }

    .password-field {
        position: relative;
        display: flex;
        align-items: center;
    }

    .password-field .input-field {
        padding-right: 5.4rem;
    }

    .password-toggle {
        position: absolute;
        right: 0.45rem;
        border: none;
        background: rgba(245, 238, 228, 0.92);
        color: var(--text-main);
        border-radius: 999px;
        padding: 0.4rem 0.7rem;
        font-size: 0.82rem;
        font-weight: 700;
        cursor: pointer;
    }

    .password-toggle:hover,
    .password-toggle:focus-visible {
        background: rgba(232, 221, 206, 0.96);
        outline: none;
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
                <p class="eyebrow" style="margin-bottom:0.4rem;">{$i18n.t('auth.login.accessBadge')}</p>
                <h2 id="login-panel-title">{$i18n.t('auth.login.accessTitle')}</h2>
                <p>{$i18n.t('auth.login.accessText')}</p>
            </div>

            <div class="form-switch" role="tablist">
                <button
                    type="button"
                    class="form-tab-btn"
                    class:active={activeTab() === 'login'}
                    on:click={showLoginPanel}
                >
                    {$i18n.t('auth.login.tabLogin')}
                </button>
                <button
                    type="button"
                    class="form-tab-btn"
                    class:active={activeTab() === 'register'}
                    on:click={showRegisterPanel}
                >
                    {$i18n.t('auth.login.tabRegister')}
                </button>
            </div>

            <div class="auth-forms">
                <div class="form-frame">
                    {#if activePanel === 'choose-family'}
                        <div aria-label={$i18n.t('auth.login.chooseFamilyAria')}>
                            <p class="hero-subtitle" style="margin-bottom: 1rem;">{$i18n.t('auth.login.chooseFamilyIntro')}</p>
                            <div class="chooser-shell">
                                <div class="chooser-shell__header">
                                    <p class="chooser-shell__eyebrow">{$i18n.t('auth.login.chooseFamilyBadge')}</p>
                                    <h3 class="chooser-shell__title">{$i18n.t('auth.login.chooseFamilyTitle')}</h3>
                                    <p class="chooser-shell__text">
                                        {$i18n.t('auth.login.chooseFamilyText', { email: pendingLoginEmail })}
                                    </p>
                                </div>

                                {#if chooserError}
                                    <div class="chooser-shell__error" role="alert">{chooserError}</div>
                                {/if}

                                <div class="chooser-shell__list" role="list" aria-label={$i18n.t('auth.login.chooseFamilyListAria')}>
                                    {#each pendingFamilyChoices as choice (choice.familyId)}
                                        <button
                                            type="button"
                                            class="chooser-item"
                                            class:chooser-item--loading={chooserSubmittingFamilyId === choice.familyId}
                                            on:click={() => handleFamilyChoice(choice)}
                                            disabled={chooserSubmittingFamilyId !== null}
                                        >
                                            <div class="chooser-item__copy">
                                                <strong>{choice.familyName}</strong>
                                                <span>{choice.familyId}</span>
                                            </div>
                                            <div class="chooser-item__meta">
                                                <span class="chooser-item__permission">{permissionLabel(choice.permission)}</span>
                                                <span class="chooser-item__action">
                                                    {chooserSubmittingFamilyId === choice.familyId
                                                        ? $i18n.t('auth.login.chooseFamilySubmitting')
                                                        : $i18n.t('auth.login.chooseFamilySelect')}
                                                </span>
                                            </div>
                                        </button>
                                    {/each}
                                </div>

                                <button type="button" class="btn-secondary" on:click={showChooserBackToLogin}>
                                    {$i18n.t('auth.login.chooseFamilyUseAnother')}
                                </button>
                            </div>
                        </div>
                    {:else if activePanel === 'login'}
                        <div aria-label={$i18n.t('auth.login.loginFormAria')}>
                            <p class="hero-subtitle" style="margin-bottom: 1rem;">{$i18n.t('auth.login.loginIntro')}</p>
                            {#if googleAuthEnabled}
                                <div class="google-auth">
                                    <div class="google-auth__divider">{$i18n.t('auth.login.googleDivider')}</div>
                                    <button
                                        type="button"
                                        class="btn-secondary google-auth__cta"
                                        on:click={handleGoogleLogin}
                                        disabled={submitting === 'login'}
                                    >
                                        {submitting === 'login' ? $i18n.t('auth.login.googleSubmitting') : $i18n.t('auth.login.googleSubmit')}
                                    </button>
                                    <p class="google-auth__hint">{$i18n.t('auth.login.googleHint')}</p>
                                </div>
                            {/if}
                            <div class="form-grid">
                                <input
                                    bind:this={loginEmailInput}
                                    bind:value={loginEmail}
                                    type="email"
                                    class="input-field"
                                    placeholder={$i18n.t('auth.login.loginEmailPlaceholder')}
                                    autocomplete="username"
                                    autocapitalize="none"
                                    spellcheck="false"
                                />
                                <div class="password-field">
                                    <input
                                        bind:this={loginPasswordInput}
                                        bind:value={loginPassword}
                                        type={showLoginPassword ? 'text' : 'password'}
                                        class="input-field"
                                        placeholder={$i18n.t('auth.login.loginPasswordPlaceholder')}
                                        autocomplete="current-password"
                                        on:keydown={(event) => event.key === 'Enter' && handleLogin()}
                                    />
                                    <button class="password-toggle" type="button" on:click={() => showLoginPassword = !showLoginPassword}>
                                        {showLoginPassword ? $i18n.t('auth.login.hidePassword') : $i18n.t('auth.login.showPassword')}
                                    </button>
                                </div>
                            </div>
                            <button class="btn-login" on:click={handleLogin} disabled={submitting === 'login'}>
                                {submitting === 'login' ? $i18n.t('auth.login.loginSubmitting') : $i18n.t('auth.login.loginSubmit')}
                            </button>
                            <div class="form-links">
                                <a href={$i18n.href('/login')} on:click|preventDefault={showForgotPanel}>{$i18n.t('auth.login.forgotLink')}</a>
                                <span style="color: var(--muted); font-size: 0.85rem;">или</span>
                                <button type="button" class="link-btn" on:click={showRegisterPanel}>{$i18n.t('auth.login.familyCta')}</button>
                            </div>
                        </div>
                    {:else if activePanel === 'register'}
                        <div aria-label={$i18n.t('auth.login.registerAria')}>
                            <p class="hero-subtitle" style="margin-bottom: 1rem;">{$i18n.t('auth.login.registerIntro')}</p>
                            <div class="form-grid">
                                <input
                                    bind:value={regEmail}
                                    type="email"
                                    class="input-field"
                                    placeholder={$i18n.t('auth.login.registerEmailPlaceholder')}
                                    autocomplete="email"
                                    autocapitalize="none"
                                    spellcheck="false"
                                />
                                <div class="password-field">
                                    <input
                                        bind:value={regPassword}
                                        type={showRegisterPassword ? 'text' : 'password'}
                                        class="input-field"
                                        placeholder={$i18n.t('auth.login.registerPasswordPlaceholder')}
                                        minlength="6"
                                        autocomplete="new-password"
                                        on:keydown={(event) => event.key === 'Enter' && handleRegister()}
                                    />
                                    <button class="password-toggle" type="button" on:click={() => showRegisterPassword = !showRegisterPassword}>
                                        {showRegisterPassword ? $i18n.t('auth.login.hidePassword') : $i18n.t('auth.login.showPassword')}
                                    </button>
                                </div>
                            </div>
                            <button class="btn-login" on:click={handleRegister} disabled={submitting === 'register'}>
                                {submitting === 'register' ? $i18n.t('auth.login.registerSubmitting') : $i18n.t('auth.login.registerSubmit')}
                            </button>
                            <button type="button" class="btn-secondary" on:click={showLoginPanel}>
                                {$i18n.t('auth.login.registerBackToLogin')}
                            </button>
                        </div>
                    {:else}
                        <div aria-label={$i18n.t('auth.login.forgotAria')}>
                            <p class="hero-subtitle" style="margin-bottom: 1rem;">
                                {#if passwordRecoveryEnabled}
                                    {$i18n.t('auth.login.forgotIntroEnabled')}
                                {:else}
                                    {$i18n.t('auth.login.forgotIntroDisabled')}
                                {/if}
                            </p>
                            <div class="form-grid">
                                <input
                                    bind:value={forgotEmail}
                                    type="email"
                                    class="input-field"
                                    placeholder={$i18n.t('auth.login.forgotEmailPlaceholder')}
                                    autocomplete="email"
                                    autocapitalize="none"
                                    spellcheck="false"
                                    on:keydown={(event) => event.key === 'Enter' && handleRecover()}
                                />
                            </div>
                            <button class="btn-login" on:click={handleRecover} disabled={submitting === 'forgot'}>
                                {submitting === 'forgot' ? $i18n.t('auth.login.forgotSubmitting') : $i18n.t('auth.login.forgotSubmit')}
                            </button>
                            <button type="button" class="btn-secondary" on:click={showLoginPanel}>
                                {$i18n.t('auth.login.forgotBackToLogin')}
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
                <p class="eyebrow">{$i18n.t('auth.login.heroBadge')}</p>
                <h1 id="login-hero-title" class="hero-title">{$i18n.t('auth.login.heroTitle')}</h1>
                <p class="hero-subtitle">{$i18n.t('auth.login.heroSubtitle')}</p>
                <div class="hero-actions">
                    <a class="btn-hero" href={$i18n.href('/')}>{$i18n.t('auth.login.heroPrimaryCta')}</a>
                    <a class="btn-ghost" href={$i18n.href('/faq')}>{$i18n.t('auth.login.heroSecondaryCta')}</a>
                </div>
                <div class="hero-stats">
                    <div class="hero-stat">
                        <strong>{$i18n.t('auth.login.statOneTitle')}</strong>
                        <span>{$i18n.t('auth.login.statOneText')}</span>
                    </div>
                    <div class="hero-stat">
                        <strong>{$i18n.t('auth.login.statTwoTitle')}</strong>
                        <span>{$i18n.t('auth.login.statTwoText')}</span>
                    </div>
                    <div class="hero-stat">
                        <strong>{$i18n.t('auth.login.statThreeTitle')}</strong>
                        <span>{$i18n.t('auth.login.statThreeText')}</span>
                    </div>
                </div>
            </div>

            <div>
                <div class="role-grid">
                    <article class="role-card">
                        <div class="role-card__badge">{$i18n.t('auth.login.parentBadge')}</div>
                        <h3>{$i18n.t('auth.login.parentTitle')}</h3>
                        <p>{$i18n.t('auth.login.parentText')}</p>
                    </article>
                    <article class="role-card">
                        <div class="role-card__badge">{$i18n.t('auth.login.childBadge')}</div>
                        <h3>{$i18n.t('auth.login.childTitle')}</h3>
                        <p>{$i18n.t('auth.login.childText')}</p>
                    </article>
                </div>

                <div class="hero-scenarios" aria-label={$i18n.t('auth.login.scenariosAria')}>
                    <article class="scenario-card">
                        <div class="scenario-card__header">
                            <h3 class="scenario-card__title">{$i18n.t('auth.login.childScenarioTitle')}</h3>
                            <span class="role-chip role-chip--child">{$i18n.t('auth.login.childBadge')}</span>
                        </div>
                        <ul class="scenario-card__list">
                            <li>{$i18n.t('auth.login.childScenarioItemOne')}</li>
                            <li>{$i18n.t('auth.login.childScenarioItemTwo')}</li>
                            <li>{$i18n.t('auth.login.childScenarioItemThree')}</li>
                        </ul>
                        <p class="scenario-card__hint">{$i18n.t('auth.login.childScenarioHint')}</p>
                    </article>
                    <article class="scenario-card">
                        <div class="scenario-card__header">
                            <h3 class="scenario-card__title">{$i18n.t('auth.login.parentScenarioTitle')}</h3>
                            <span class="role-chip role-chip--parent">{$i18n.t('auth.login.parentBadge')}</span>
                        </div>
                        <ul class="scenario-card__list">
                            <li>{$i18n.t('auth.login.parentScenarioItemOne')}</li>
                            <li>{$i18n.t('auth.login.parentScenarioItemTwo')}</li>
                            <li>{$i18n.t('auth.login.parentScenarioItemThree')}</li>
                        </ul>
                        <p class="scenario-card__hint">{$i18n.t('auth.login.parentScenarioHint')}</p>
                    </article>
                </div>
            </div>
        </section>

        <section class="onboarding-steps" aria-label={$i18n.t('auth.login.stepsAria')}>
            <article class="step-card">
                <div class="step-index">1</div>
                <h3>{$i18n.t('auth.login.stepOneTitle')}</h3>
                <p>{$i18n.t('auth.login.stepOneText')}</p>
            </article>
            <article class="step-card">
                <div class="step-index">2</div>
                <h3>{$i18n.t('auth.login.stepTwoTitle')}</h3>
                <p>{$i18n.t('auth.login.stepTwoText')}</p>
            </article>
            <article class="step-card">
                <div class="step-index">3</div>
                <h3>{$i18n.t('auth.login.stepThreeTitle')}</h3>
                <p>{$i18n.t('auth.login.stepThreeText')}</p>
            </article>
        </section>
    </main>
</div>
