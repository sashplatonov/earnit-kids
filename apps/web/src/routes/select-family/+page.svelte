<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import type { PageData } from './$types';

    export let data: PageData;

    const i18n = useI18n();
    let submitting = '';
    let error = '';

    async function selectFamily(familyId: string) {
        submitting = familyId;
        error = '';
        try {
            const response = await fetch('/api/select-family', {
                method: 'POST',
                headers: { 'content-type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({ familyId }),
            });
            if (response.ok) {
                window.location.assign('/app');
                return;
            }
            error = $i18n.t('auth.login.chooserError');
        } catch {
            error = $i18n.t('auth.login.chooserNetworkError');
        } finally {
            submitting = '';
        }
    }
</script>

<svelte:head>
    <title>{$i18n.t('auth.login.chooseFamilyTitle')}</title>
    <meta name="robots" content="noindex, nofollow" />
</svelte:head>

<main class="chooser" aria-labelledby="chooser-title">
    <section class="surface">
        <p class="eyebrow">{$i18n.t('auth.login.chooseFamilyBadge')}</p>
        <h1 id="chooser-title">{$i18n.t('auth.login.chooseFamilyTitle')}</h1>
        {#if data.expired}
            <p class="message" role="alert">{$i18n.t('auth.login.chooserNetworkError')}</p>
            <a href="/">{$i18n.t('auth.login.chooseFamilyUseAnother')}</a>
        {:else}
            <p class="intro">{$i18n.t('auth.login.chooseFamilyIntro')}</p>
            {#if error}<p class="message" role="alert">{error}</p>{/if}
            <div class="choices" role="list" aria-label={$i18n.t('auth.login.chooseFamilyListAria')}>
                {#each data.choices as choice (choice.familyId)}
                    <button
                        type="button"
                        class:blocked={choice.blocked}
                        disabled={choice.blocked || !!submitting}
                        on:click={() => selectFamily(choice.familyId)}
                    >
                        <span>{choice.familyName}</span>
                        <small>{choice.blocked ? $i18n.t('auth.login.chooseFamilyBlockedBadge') : $i18n.t('auth.login.chooseFamilySelect')}</small>
                    </button>
                {/each}
            </div>
        {/if}
    </section>
</main>

<style>
    .chooser { min-height: 100vh; display: grid; place-items: center; padding: 1rem; background: #f4f7fb; }
    .surface { width: min(100%, 30rem); max-width: 30rem; padding: clamp(1.5rem, 5vw, 2.5rem); border: 1px solid #dce5f0; border-radius: 1.25rem; background: white; box-shadow: 0 1.25rem 3rem #17324d18; }
    .eyebrow { margin: 0 0 .5rem; color: #32658d; font-size: .75rem; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
    h1 { margin: 0 0 .75rem; color: #17324d; font-size: clamp(1.7rem, 7vw, 2.35rem); line-height: 1.05; }
    .intro, .message { color: #52677b; line-height: 1.5; }
    .message { color: #a13f3f; }
    a { color: #245b85; font-weight: 700; }
    .choices { display: grid; gap: .75rem; margin-top: 1.5rem; }
    button { display: flex; align-items: center; justify-content: space-between; gap: 1rem; width: 100%; min-height: 3rem; padding: .75rem 1rem; border: 1px solid #bfd2e4; border-radius: .8rem; background: #f9fbfd; color: #17324d; font: inherit; font-weight: 750; text-align: left; cursor: pointer; }
    button:hover:not(:disabled), button:focus-visible { border-color: #32658d; background: #eef6fc; }
    button:disabled { cursor: not-allowed; opacity: .58; }
    button small { color: #32658d; font-size: .75rem; font-weight: 700; text-align: right; }
    button.blocked small { color: #8a5c41; }
</style>
