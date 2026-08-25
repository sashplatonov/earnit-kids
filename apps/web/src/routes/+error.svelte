<script lang="ts">
    import { page } from '$app/state';
    import { useI18n } from '$lib/i18n/context';

    const i18n = useI18n();
</script>

<svelte:head>
    <title>{page.status} | {$i18n.t('common.brand.name')}</title>
</svelte:head>

<div class="error-shell">
    <section class="panel">
        <p class="eyebrow">{page.status}</p>
        <h1>{$i18n.t('errors.page.notFound')}</h1>
        <p class="error-text">{$i18n.t('errors.page.description')}</p>

        <a class="cta-link" href={$i18n.href('/')}>{$i18n.t('common.errors.backToHome')}</a>

        <nav class="error-links" aria-label={$i18n.t('errors.page.linksAria')}>
            <a href={$i18n.href('/how')}>{$i18n.t('common.navigation.how')}</a>
            <a href={$i18n.href('/tasks')}>{$i18n.t('common.navigation.tasks')}</a>
            <a href={$i18n.href('/rewards')}>{$i18n.t('common.navigation.rewards')}</a>
        </nav>
    </section>
</div>

<style>
    .error-shell {
        min-height: 100dvh;
        display: grid;
        place-items: center;
        padding: 2rem 1.5rem;
        background: #f7f8fb;
        color: #1c2534;
        font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
        line-height: 1.52;
        box-sizing: border-box;
    }

    .panel {
        max-width: 30rem;
        width: 100%;
        background: #fff;
        border: 1px solid #dde3eb;
        border-radius: 1.25rem;
        padding: 2.5rem 2rem;
        text-align: center;
        box-shadow: 0 14px 38px rgb(32 46 76 / 9%);
        box-sizing: border-box;
    }

    .eyebrow {
        margin: 0 0 0.5rem;
        font-weight: 700;
        color: #275fd6;
        font-size: 0.875rem;
        text-transform: uppercase;
        letter-spacing: 0.03em;
    }

    h1 {
        margin: 0 0 1rem;
        font-size: 2rem;
        line-height: 1.1;
        letter-spacing: -0.03em;
    }

    .error-text {
        margin: 0 0 1.5rem;
        color: #687386;
    }

    .cta-link {
        display: inline-block;
        margin-bottom: 1.5rem;
        padding: 0.625rem 1.25rem;
        border-radius: 0.75rem;
        background: #275fd6;
        color: #fff;
        font-weight: 700;
        text-decoration: none;
    }

    .cta-link:hover {
        background: #1d4fb9;
    }

    .error-links {
        display: flex;
        flex-wrap: wrap;
        justify-content: center;
        gap: 0.5rem 1rem;
        padding-top: 1rem;
        border-top: 1px solid #dde3eb;
    }

    .error-links a {
        color: #275fd6;
        text-decoration: none;
        font-weight: 500;
    }

    .error-links a:hover {
        text-decoration: underline;
    }
</style>
