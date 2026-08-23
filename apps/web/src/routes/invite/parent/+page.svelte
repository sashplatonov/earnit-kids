<script lang="ts">
    import { onMount } from 'svelte';
    import { requestGoogleLoginUrl } from '$lib/auth/googleOAuth';
    import type { PageData } from './$types';

    export let data: PageData;

    let loading = true;
    let accepting = false;
    let error = '';
    let signedIn = data.authenticated;
    let state = '';

    onMount(() => {
        const params = new URLSearchParams(window.location.search);
        error = params.get('error') ?? '';
        state = params.get('state') ?? '';
        loading = false;
    });

    async function signIn() {
        loading = true;
        error = '';
        try {
            window.location.assign(await requestGoogleLoginUrl(fetch, '/invite/parent'));
        } catch {
            error = 'Google sign-in is currently unavailable.';
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
            window.location.assign('/workspace');
            return;
        }
        error = response.status === 401
            ? 'Sign in with the invited Google account before accepting this invitation.'
            : response.status === 404 || response.status === 410
                ? 'This invitation has expired or was revoked. Ask the family admin for a new link.'
                : 'This invitation could not be accepted. Ask the family admin for a new link.';
        accepting = false;
    }
</script>

<svelte:head>
    <title>Accept parent invitation</title>
    <meta name="robots" content="noindex, nofollow" />
</svelte:head>

<main class="invitation-page" data-testid="parent-invitation">
    <section class="invitation-card" aria-labelledby="invitation-title">
        <p class="eyebrow">Family workspace</p>
        <h1 id="invitation-title">You’ve been invited to join a family</h1>
        {#if state === 'accepted'}<p class="success" role="status">Invitation accepted. Opening your workspace…</p>{/if}
        {#if error}
            <p class="error" role="alert">{error}</p>
        {/if}
        {#if loading}
            <p aria-live="polite">Checking invitation…</p>
        {:else if signedIn}
            <button type="button" disabled={accepting} on:click={accept}>
                {accepting ? 'Accepting…' : 'Accept invitation'}
            </button>
        {:else}
            <p>Sign in with the Google account that received this invitation.</p>
            <button type="button" on:click={signIn}>Continue with Google</button>
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
