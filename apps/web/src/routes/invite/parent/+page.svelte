
<script lang="ts">
    import { onMount } from 'svelte';
    import { requestGoogleLoginUrl } from '$lib/auth/googleOAuth';
    import type { PageData } from './$types';
    import { useI18n } from '$lib/i18n/context';

    export let data: PageData;
    const i18n = useI18n();

    let loading = true;
    let accepting = false;
    let error = '';
    let signedIn = data.authenticated;
    let state = '';

    onMount(() => {
        const params = new URLSearchParams(window.location.search);
        error = params.get('error') === 'expired'
            ? $i18n.t('auth.invitation.expired')
            : params.has('error') ? $i18n.t('auth.invitation.genericError') : '';
        state = params.get('state') ?? '';
        loading = false;
    });

    async function signIn() {
        loading = true;
        error = '';
        try {
            window.location.assign(await requestGoogleLoginUrl(fetch, '/invite/parent'));
        } catch {
            error = $i18n.t('auth.invitation.googleUnavailable');
            loading = false;
        }
    }

    async function accept() {
        accepting = true;
        error = '';
        const response = await fetch('/invite/parent/accept', {
            method: 'POST',
            credentials: 'same-origin',
            cache: 'no-store',
        });
        if (response.ok) {
            window.location.assign('/app');
            return;
        }
        error = response.status === 401
            ? $i18n.t('auth.invitation.signInInvitedAccount')
            : response.status === 404 || response.status === 410
                ? $i18n.t('auth.invitation.expired')
                : $i18n.t('auth.invitation.acceptError');
        accepting = false;
    }
</script>

<svelte:head>
    <title>{$i18n.t('auth.invitation.title')}</title>
    <meta name="robots" content="noindex, nofollow" />
</svelte:head>

<main class="invitation-page" data-testid="parent-invitation">
    <section class="invitation-card" aria-labelledby="invitation-title">
        <p class="eyebrow">{$i18n.t('auth.invitation.eyebrow')}</p>
        <h1 id="invitation-title">{$i18n.t('auth.invitation.heading')}</h1>
        {#if state === 'accepted'}<p class="success" role="status">{$i18n.t('auth.invitation.accepted')}</p>{/if}
        {#if error}
            <p class="error" role="alert">{error}</p>
        {/if}
        {#if loading}
            <p aria-live="polite">{$i18n.t('auth.invitation.checking')}</p>
        {:else if signedIn}
            <button type="button" disabled={accepting} onclick={accept}>
                {accepting ? $i18n.t('auth.invitation.accepting') : $i18n.t('auth.invitation.accept')}
            </button>
        {:else}
            <p>{$i18n.t('auth.invitation.signInHint')}</p>
            <button type="button" onclick={signIn}>{$i18n.t('auth.invitation.continueGoogle')}</button>
        {/if}
    </section>
</main>

<style>
    .invitation-page { min-height: 100vh; display: grid; place-items: center; padding: 1rem; background: #f5f1e8; }
    .invitation-card { width: min(100%, 28rem); padding: 2rem; border-radius: 1.25rem; background: white; box-shadow: 0 1rem 3rem #43351d1c; }
    .eyebrow { color: #8b5e34; font-size: .75rem; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
    h1 { margin: .5rem 0 1rem; font-size: clamp(1.75rem, 7vw, 2.5rem); line-height: 1.05; }
    button { min-height: 2.75rem; width: 100%; border: 0; border-radius: .75rem; background: #315c45; color: white; font: inherit; font-weight: 700; cursor: pointer; }
    button:disabled { opacity: .6; cursor: wait; }
    .error { color: #a33a2b; }
    .success { color: #17884b; }
</style>
